/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.os;

import static android.system.OsConstants.S_IRWXG;
import static android.system.OsConstants.S_IRWXO;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.icu.impl.CacheValue;
import android.icu.text.DecimalFormatSymbols;
import android.icu.util.ULocale;
import android.opengl.EGL14;
import android.os.Build;
import android.os.IInstalld;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.Seccomp;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.ZygoteProcess;
import android.os.storage.StorageManager;
import android.security.keystore.AndroidKeyStoreProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.Hyphenator;
import android.util.TimingsTraceLog;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.webkit.WebViewFactory;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;

import com.android.internal.util.Preconditions;
import dalvik.system.DexFile;
import dalvik.system.VMRuntime;
import dalvik.system.ZygoteHooks;

import libcore.io.IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Security;
import java.security.Provider;

/* mobiledui: start */
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import dalvik.system.DexClassLoader;
import libcore.reflect.Types;
import android.view.View;
/* mobiledui: end */

/**
 * Startup class for the zygote process.
 *
 * Pre-initializes some classes, and then waits for commands on a UNIX domain
 * socket. Based on these commands, forks off child processes that inherit
 * the initial state of the VM.
 *
 * Please see {@link ZygoteConnection.Arguments} for documentation on the
 * client protocol.
 *
 * @hide
 */
public class ZygoteInit {
    private static final String TAG = "Zygote";

	/* mobiledui: start */
	private static final int RPC_INSTALLED = 0x00100000;
	/* mobiledui: end */
    private static final String PROPERTY_DISABLE_OPENGL_PRELOADING = "ro.zygote.disable_gl_preload";
    private static final String PROPERTY_GFX_DRIVER = "ro.gfx.driver.0";
    private static final String PROPERTY_RUNNING_IN_CONTAINER = "ro.boot.container";

    private static final int LOG_BOOT_PROGRESS_PRELOAD_START = 3020;
    private static final int LOG_BOOT_PROGRESS_PRELOAD_END = 3030;

    /** when preloading, GC after allocating this many bytes */
    private static final int PRELOAD_GC_THRESHOLD = 50000;

    private static final String ABI_LIST_ARG = "--abi-list=";

    private static final String SOCKET_NAME_ARG = "--socket-name=";

    /**
     * Used to pre-load resources.
     */
    private static Resources mResources;

    /**
     * The path of a file that contains classes to preload.
     */
    private static final String PRELOADED_CLASSES = "/system/etc/preloaded-classes";

    /** Controls whether we should preload resources during zygote init. */
    public static final boolean PRELOAD_RESOURCES = true;

    private static final int UNPRIVILEGED_UID = 9999;
    private static final int UNPRIVILEGED_GID = 9999;

    private static final int ROOT_UID = 0;
    private static final int ROOT_GID = 0;

    private static boolean sPreloadComplete;

    static void preload(TimingsTraceLog bootTimingsTraceLog) {
        Log.d(TAG, "begin preload");
        bootTimingsTraceLog.traceBegin("BeginIcuCachePinning");
        beginIcuCachePinning();
        bootTimingsTraceLog.traceEnd(); // BeginIcuCachePinning
        bootTimingsTraceLog.traceBegin("PreloadClasses");
        preloadClasses();
        bootTimingsTraceLog.traceEnd(); // PreloadClasses
        bootTimingsTraceLog.traceBegin("PreloadResources");
        preloadResources();
        bootTimingsTraceLog.traceEnd(); // PreloadResources
        Trace.traceBegin(Trace.TRACE_TAG_DALVIK, "PreloadAppProcessHALs");
        nativePreloadAppProcessHALs();
        Trace.traceEnd(Trace.TRACE_TAG_DALVIK);
        Trace.traceBegin(Trace.TRACE_TAG_DALVIK, "PreloadOpenGL");
        preloadOpenGL();
        Trace.traceEnd(Trace.TRACE_TAG_DALVIK);
        preloadSharedLibraries();
        preloadTextResources();
        // Ask the WebViewFactory to do any initialization that must run in the zygote process,
        // for memory sharing purposes.
        WebViewFactory.prepareWebViewInZygote();
        endIcuCachePinning();
        warmUpJcaProviders();
        Log.d(TAG, "end preload");

        sPreloadComplete = true;
    }

    public static void lazyPreload() {
        Preconditions.checkState(!sPreloadComplete);
        Log.i(TAG, "Lazily preloading resources.");

        preload(new TimingsTraceLog("ZygoteInitTiming_lazy", Trace.TRACE_TAG_DALVIK));
    }

    private static void beginIcuCachePinning() {
        // Pin ICU data in memory from this point that would normally be held by soft references.
        // Without this, any references created immediately below or during class preloading
        // would be collected when the Zygote GC runs in gcAndFinalize().
        Log.i(TAG, "Installing ICU cache reference pinning...");

        CacheValue.setStrength(CacheValue.Strength.STRONG);

        Log.i(TAG, "Preloading ICU data...");
        // Explicitly exercise code to cache data apps are likely to need.
        ULocale[] localesToPin = { ULocale.ROOT, ULocale.US, ULocale.getDefault() };
        for (ULocale uLocale : localesToPin) {
            new DecimalFormatSymbols(uLocale);
        }
    }

    private static void endIcuCachePinning() {
        // All cache references created by ICU from this point will be soft.
        CacheValue.setStrength(CacheValue.Strength.SOFT);

        Log.i(TAG, "Uninstalled ICU cache reference pinning...");
    }

    private static void preloadSharedLibraries() {
        Log.i(TAG, "Preloading shared libraries...");
        System.loadLibrary("android");
        System.loadLibrary("compiler_rt");
        System.loadLibrary("jnigraphics");
    }

    native private static void nativePreloadAppProcessHALs();

    private static void preloadOpenGL() {
        String driverPackageName = SystemProperties.get(PROPERTY_GFX_DRIVER);
        if (!SystemProperties.getBoolean(PROPERTY_DISABLE_OPENGL_PRELOADING, false) &&
                (driverPackageName == null || driverPackageName.isEmpty())) {
            EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        }
    }

    private static void preloadTextResources() {
        Hyphenator.init();
        TextView.preloadFontCache();
    }

    /**
     * Register AndroidKeyStoreProvider and warm up the providers that are already registered.
     *
     * By doing it here we avoid that each app does it when requesting a service from the
     * provider for the first time.
     */
    private static void warmUpJcaProviders() {
        long startTime = SystemClock.uptimeMillis();
        Trace.traceBegin(
                Trace.TRACE_TAG_DALVIK, "Starting installation of AndroidKeyStoreProvider");
        // AndroidKeyStoreProvider.install() manipulates the list of JCA providers to insert
        // preferred providers. Note this is not done via security.properties as the JCA providers
        // are not on the classpath in the case of, for example, raw dalvikvm runtimes.
        AndroidKeyStoreProvider.install();
        Log.i(TAG, "Installed AndroidKeyStoreProvider in "
                + (SystemClock.uptimeMillis() - startTime) + "ms.");
        Trace.traceEnd(Trace.TRACE_TAG_DALVIK);

        startTime = SystemClock.uptimeMillis();
        Trace.traceBegin(
                Trace.TRACE_TAG_DALVIK, "Starting warm up of JCA providers");
        for (Provider p : Security.getProviders()) {
            p.warmUpServiceProvision();
        }
        Log.i(TAG, "Warmed up JCA providers in "
                + (SystemClock.uptimeMillis() - startTime) + "ms.");
        Trace.traceEnd(Trace.TRACE_TAG_DALVIK);
    }

    /**
     * Performs Zygote process initialization. Loads and initializes
     * commonly used classes.
     *
     * Most classes only cause a few hundred bytes to be allocated, but
     * a few will allocate a dozen Kbytes (in one case, 500+K).
     */
    private static void preloadClasses() {
        final VMRuntime runtime = VMRuntime.getRuntime();

        InputStream is;
        try {
            is = new FileInputStream(PRELOADED_CLASSES);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Couldn't find " + PRELOADED_CLASSES + ".");
            return;
        }

        Log.i(TAG, "Preloading classes...");
        long startTime = SystemClock.uptimeMillis();

        // Drop root perms while running static initializers.
        final int reuid = Os.getuid();
        final int regid = Os.getgid();

        // We need to drop root perms only if we're already root. In the case of "wrapped"
        // processes (see WrapperInit), this function is called from an unprivileged uid
        // and gid.
        boolean droppedPriviliges = false;
        if (reuid == ROOT_UID && regid == ROOT_GID) {
            try {
                Os.setregid(ROOT_GID, UNPRIVILEGED_GID);
                Os.setreuid(ROOT_UID, UNPRIVILEGED_UID);
            } catch (ErrnoException ex) {
                throw new RuntimeException("Failed to drop root", ex);
            }

            droppedPriviliges = true;
        }

        // Alter the target heap utilization.  With explicit GCs this
        // is not likely to have any effect.
        float defaultUtilization = runtime.getTargetHeapUtilization();
        runtime.setTargetHeapUtilization(0.8f);

        try {
            BufferedReader br
                = new BufferedReader(new InputStreamReader(is), 256);

            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                // Skip comments and blank lines.
                line = line.trim();
                if (line.startsWith("#") || line.equals("")) {
                    continue;
                }

                Trace.traceBegin(Trace.TRACE_TAG_DALVIK, line);
                try {
                    if (false) {
                        Log.v(TAG, "Preloading " + line + "...");
                    }
                    // Load and explicitly initialize the given class. Use
                    // Class.forName(String, boolean, ClassLoader) to avoid repeated stack lookups
                    // (to derive the caller's class-loader). Use true to force initialization, and
                    // null for the boot classpath class-loader (could as well cache the
                    // class-loader of this class in a variable).
                    Class.forName(line, true, null);
                    count++;
                } catch (ClassNotFoundException e) {
                    Log.w(TAG, "Class not found for preloading: " + line);
                } catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "Problem preloading " + line + ": " + e);
                } catch (Throwable t) {
                    Log.e(TAG, "Error preloading " + line + ".", t);
                    if (t instanceof Error) {
                        throw (Error) t;
                    }
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    throw new RuntimeException(t);
                }
                Trace.traceEnd(Trace.TRACE_TAG_DALVIK);
            }

            Log.i(TAG, "...preloaded " + count + " classes in "
                    + (SystemClock.uptimeMillis()-startTime) + "ms.");
        } catch (IOException e) {
            Log.e(TAG, "Error reading " + PRELOADED_CLASSES + ".", e);
        } finally {
            IoUtils.closeQuietly(is);
            // Restore default.
            runtime.setTargetHeapUtilization(defaultUtilization);

            // Fill in dex caches with classes, fields, and methods brought in by preloading.
            Trace.traceBegin(Trace.TRACE_TAG_DALVIK, "PreloadDexCaches");
            runtime.preloadDexCaches();
            Trace.traceEnd(Trace.TRACE_TAG_DALVIK);

            // Bring back root. We'll need it later if we're in the zygote.
            if (droppedPriviliges) {
                try {
                    Os.setreuid(ROOT_UID, ROOT_UID);
                    Os.setregid(ROOT_GID, ROOT_GID);
                } catch (ErrnoException ex) {
                    throw new RuntimeException("Failed to restore root", ex);
                }
            }
        }
    }

    /**
     * Load in commonly used resources, so they can be shared across
     * processes.
     *
     * These tend to be a few Kbytes, but are frequently in the 20-40K
     * range, and occasionally even larger.
     */
    private static void preloadResources() {
        final VMRuntime runtime = VMRuntime.getRuntime();

        try {
            mResources = Resources.getSystem();
            mResources.startPreloading();
            if (PRELOAD_RESOURCES) {
                Log.i(TAG, "Preloading resources...");

                long startTime = SystemClock.uptimeMillis();
                TypedArray ar = mResources.obtainTypedArray(
                        com.android.internal.R.array.preloaded_drawables);
                int N = preloadDrawables(ar);
                ar.recycle();
                Log.i(TAG, "...preloaded " + N + " resources in "
                        + (SystemClock.uptimeMillis()-startTime) + "ms.");

                startTime = SystemClock.uptimeMillis();
                ar = mResources.obtainTypedArray(
                        com.android.internal.R.array.preloaded_color_state_lists);
                N = preloadColorStateLists(ar);
                ar.recycle();
                Log.i(TAG, "...preloaded " + N + " resources in "
                        + (SystemClock.uptimeMillis()-startTime) + "ms.");

                if (mResources.getBoolean(
                        com.android.internal.R.bool.config_freeformWindowManagement)) {
                    startTime = SystemClock.uptimeMillis();
                    ar = mResources.obtainTypedArray(
                            com.android.internal.R.array.preloaded_freeform_multi_window_drawables);
                    N = preloadDrawables(ar);
                    ar.recycle();
                    Log.i(TAG, "...preloaded " + N + " resource in "
                            + (SystemClock.uptimeMillis() - startTime) + "ms.");
                }
            }
            mResources.finishPreloading();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failure preloading resources", e);
        }
    }

    private static int preloadColorStateLists(TypedArray ar) {
        int N = ar.length();
        for (int i=0; i<N; i++) {
            int id = ar.getResourceId(i, 0);
            if (false) {
                Log.v(TAG, "Preloading resource #" + Integer.toHexString(id));
            }
            if (id != 0) {
                if (mResources.getColorStateList(id, null) == null) {
                    throw new IllegalArgumentException(
                            "Unable to find preloaded color resource #0x"
                            + Integer.toHexString(id)
                            + " (" + ar.getString(i) + ")");
                }
            }
        }
        return N;
    }


    private static int preloadDrawables(TypedArray ar) {
        int N = ar.length();
        for (int i=0; i<N; i++) {
            int id = ar.getResourceId(i, 0);
            if (false) {
                Log.v(TAG, "Preloading resource #" + Integer.toHexString(id));
            }
            if (id != 0) {
                if (mResources.getDrawable(id, null) == null) {
                    throw new IllegalArgumentException(
                            "Unable to find preloaded drawable resource #0x"
                            + Integer.toHexString(id)
                            + " (" + ar.getString(i) + ")");
                }
            }
        }
        return N;
    }

    /**
     * Runs several special GCs to try to clean up a few generations of
     * softly- and final-reachable objects, along with any other garbage.
     * This is only useful just before a fork().
     */
    /*package*/ static void gcAndFinalize() {
        final VMRuntime runtime = VMRuntime.getRuntime();

        /* runFinalizationSync() lets finalizers be called in Zygote,
         * which doesn't have a HeapWorker thread.
         */
        System.gc();
        runtime.runFinalizationSync();
        System.gc();
    }

    /**
     * Finish remaining work for the newly forked system server process.
     */
    private static Runnable handleSystemServerProcess(ZygoteConnection.Arguments parsedArgs) {
        // set umask to 0077 so new files and directories will default to owner-only permissions.
        Os.umask(S_IRWXG | S_IRWXO);

        if (parsedArgs.niceName != null) {
            Process.setArgV0(parsedArgs.niceName);
        }

        final String systemServerClasspath = Os.getenv("SYSTEMSERVERCLASSPATH");
        if (systemServerClasspath != null) {
            performSystemServerDexOpt(systemServerClasspath);
            // Capturing profiles is only supported for debug or eng builds since selinux normally
            // prevents it.
            boolean profileSystemServer = SystemProperties.getBoolean(
                    "dalvik.vm.profilesystemserver", false);
            if (profileSystemServer && (Build.IS_USERDEBUG || Build.IS_ENG)) {
                try {
                    File profileDir = Environment.getDataProfilesDePackageDirectory(
                            Process.SYSTEM_UID, "system_server");
                    File profile = new File(profileDir, "primary.prof");
                    profile.getParentFile().mkdirs();
                    profile.createNewFile();
                    String[] codePaths = systemServerClasspath.split(":");
                    VMRuntime.registerAppInfo(profile.getPath(), codePaths);
                } catch (Exception e) {
                    Log.wtf(TAG, "Failed to set up system server profile", e);
                }
            }
        }

        if (parsedArgs.invokeWith != null) {
            String[] args = parsedArgs.remainingArgs;
            // If we have a non-null system server class path, we'll have to duplicate the
            // existing arguments and append the classpath to it. ART will handle the classpath
            // correctly when we exec a new process.
            if (systemServerClasspath != null) {
                String[] amendedArgs = new String[args.length + 2];
                amendedArgs[0] = "-cp";
                amendedArgs[1] = systemServerClasspath;
                System.arraycopy(args, 0, amendedArgs, 2, args.length);
                args = amendedArgs;
            }

            WrapperInit.execApplication(parsedArgs.invokeWith,
                    parsedArgs.niceName, parsedArgs.targetSdkVersion,
                    VMRuntime.getCurrentInstructionSet(), null, args);

            throw new IllegalStateException("Unexpected return from WrapperInit.execApplication");
        } else {
            ClassLoader cl = null;
            if (systemServerClasspath != null) {
                cl = createPathClassLoader(systemServerClasspath, parsedArgs.targetSdkVersion);

                Thread.currentThread().setContextClassLoader(cl);
            }

            /*
             * Pass the remaining arguments to SystemServer.
             */
            return ZygoteInit.zygoteInit(parsedArgs.targetSdkVersion, parsedArgs.remainingArgs, cl);
        }

        /* should never reach here */
    }

    /**
     * Creates a PathClassLoader for the given class path that is associated with a shared
     * namespace, i.e., this classloader can access platform-private native libraries. The
     * classloader will use java.library.path as the native library path.
     */
    static ClassLoader createPathClassLoader(String classPath, int targetSdkVersion) {
        String libraryPath = System.getProperty("java.library.path");

        return ClassLoaderFactory.createClassLoader(classPath, libraryPath, libraryPath,
                ClassLoader.getSystemClassLoader(), targetSdkVersion, true /* isNamespaceShared */,
                null /* classLoaderName */);
    }

    /**
     * Performs dex-opt on the elements of {@code classPath}, if needed. We
     * choose the instruction set of the current runtime.
     */
    private static void performSystemServerDexOpt(String classPath) {
        final String[] classPathElements = classPath.split(":");
        final IInstalld installd = IInstalld.Stub
                .asInterface(ServiceManager.getService("installd"));
        final String instructionSet = VMRuntime.getRuntime().vmInstructionSet();

        String classPathForElement = "";
        for (String classPathElement : classPathElements) {
            // System server is fully AOTed and never profiled
            // for profile guided compilation.
            String systemServerFilter = SystemProperties.get(
                    "dalvik.vm.systemservercompilerfilter", "speed");

            int dexoptNeeded;
            try {
                dexoptNeeded = DexFile.getDexOptNeeded(
                    classPathElement, instructionSet, systemServerFilter,
                    false /* newProfile */, false /* downgrade */);
            } catch (FileNotFoundException ignored) {
                // Do not add to the classpath.
                Log.w(TAG, "Missing classpath element for system server: " + classPathElement);
                continue;
            } catch (IOException e) {
                // Not fully clear what to do here as we don't know the cause of the
                // IO exception. Add to the classpath to be conservative, but don't
                // attempt to compile it.
                Log.w(TAG, "Error checking classpath element for system server: "
                        + classPathElement, e);
                dexoptNeeded = DexFile.NO_DEXOPT_NEEDED;
            }

            if (dexoptNeeded != DexFile.NO_DEXOPT_NEEDED) {
                final String packageName = "*";
                final String outputPath = null;
                final int dexFlags = 0;
                final String compilerFilter = systemServerFilter;
                final String uuid = StorageManager.UUID_PRIVATE_INTERNAL;
                final String seInfo = null;
                final String classLoaderContext =
                        getSystemServerClassLoaderContext(classPathForElement);
                try {
                    installd.dexopt(classPathElement, Process.SYSTEM_UID, packageName,
                            instructionSet, dexoptNeeded, outputPath, dexFlags, compilerFilter,
                            uuid, classLoaderContext, seInfo, false /* downgrade */);
                } catch (RemoteException | ServiceSpecificException e) {
                    // Ignore (but log), we need this on the classpath for fallback mode.
                    Log.w(TAG, "Failed compiling classpath element for system server: "
                            + classPathElement, e);
                }
            }

            classPathForElement = encodeSystemServerClassPath(
                    classPathForElement, classPathElement);
        }
    }

    /**
     * Encodes the system server class loader context in a format that is accepted by dexopt.
     * This assumes the system server is always loaded with a {@link dalvik.system.PathClassLoader}.
     *
     * Note that ideally we would use the {@code DexoptUtils} to compute this. However we have no
     * dependency here on the server so we hard code the logic again.
     */
    private static String getSystemServerClassLoaderContext(String classPath) {
        return classPath == null ? "PCL[]" : "PCL[" + classPath + "]";
    }

    /**
     * Encodes the class path in a format accepted by dexopt.
     * @param classPath the old class path (may be empty).
     * @param newElement the new class path elements
     * @return the class path encoding resulted from appending {@code newElement} to
     * {@code classPath}.
     */
    private static String encodeSystemServerClassPath(String classPath, String newElement) {
        return (classPath == null || classPath.isEmpty())
                ? newElement
                : classPath + ":" + newElement;
    }

    /**
     * Prepare the arguments and forks for the system server process.
     *
     * Returns an {@code Runnable} that provides an entrypoint into system_server code in the
     * child process, and {@code null} in the parent.
     */
    private static Runnable forkSystemServer(String abiList, String socketName,
            ZygoteServer zygoteServer) {
        long capabilities = posixCapabilitiesAsBits(
            OsConstants.CAP_IPC_LOCK,
            OsConstants.CAP_KILL,
            OsConstants.CAP_NET_ADMIN,
            OsConstants.CAP_NET_BIND_SERVICE,
            OsConstants.CAP_NET_BROADCAST,
            OsConstants.CAP_NET_RAW,
            OsConstants.CAP_SYS_MODULE,
            OsConstants.CAP_SYS_NICE,
            OsConstants.CAP_SYS_PTRACE,
            OsConstants.CAP_SYS_TIME,
            OsConstants.CAP_SYS_TTY_CONFIG,
            OsConstants.CAP_WAKE_ALARM
        );
        /* Containers run without this capability, so avoid setting it in that case */
        if (!SystemProperties.getBoolean(PROPERTY_RUNNING_IN_CONTAINER, false)) {
            capabilities |= posixCapabilitiesAsBits(OsConstants.CAP_BLOCK_SUSPEND);
        }
        /* Hardcoded command line to start the system server */
        String args[] = {
            "--setuid=1000",
            "--setgid=1000",
            "--setgroups=1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1018,1021,1023,1032,3001,3002,3003,3006,3007,3009,3010",
            "--capabilities=" + capabilities + "," + capabilities,
            "--nice-name=system_server",
            "--runtime-args",
            "com.android.server.SystemServer",
        };
        ZygoteConnection.Arguments parsedArgs = null;

        int pid;

        try {
            parsedArgs = new ZygoteConnection.Arguments(args);
            ZygoteConnection.applyDebuggerSystemProperty(parsedArgs);
            ZygoteConnection.applyInvokeWithSystemProperty(parsedArgs);

            /* Request to fork the system server process */
            pid = Zygote.forkSystemServer(
                    parsedArgs.uid, parsedArgs.gid,
                    parsedArgs.gids,
                    parsedArgs.debugFlags,
                    null,
                    parsedArgs.permittedCapabilities,
                    parsedArgs.effectiveCapabilities);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        }

        /* For child process */
        if (pid == 0) {
            if (hasSecondZygote(abiList)) {
                waitForSecondaryZygote(socketName);
            }

            zygoteServer.closeServerSocket();
            return handleSystemServerProcess(parsedArgs);
        }

        return null;
    }

    /**
     * Gets the bit array representation of the provided list of POSIX capabilities.
     */
    private static long posixCapabilitiesAsBits(int... capabilities) {
        long result = 0;
        for (int capability : capabilities) {
            if ((capability < 0) || (capability > OsConstants.CAP_LAST_CAP)) {
                throw new IllegalArgumentException(String.valueOf(capability));
            }
            result |= (1L << capability);
        }
        return result;
    }

    public static void main(String argv[]) {
        ZygoteServer zygoteServer = new ZygoteServer();

        // Mark zygote start. This ensures that thread creation will throw
        // an error.
        ZygoteHooks.startZygoteNoThreadCreation();

        // Zygote goes into its own process group.
        try {
            Os.setpgid(0, 0);
        } catch (ErrnoException ex) {
            throw new RuntimeException("Failed to setpgid(0,0)", ex);
        }

        final Runnable caller;
        try {
            // Report Zygote start time to tron unless it is a runtime restart
            if (!"1".equals(SystemProperties.get("sys.boot_completed"))) {
                MetricsLogger.histogram(null, "boot_zygote_init",
                        (int) SystemClock.elapsedRealtime());
            }

            String bootTimeTag = Process.is64Bit() ? "Zygote64Timing" : "Zygote32Timing";
            TimingsTraceLog bootTimingsTraceLog = new TimingsTraceLog(bootTimeTag,
                    Trace.TRACE_TAG_DALVIK);
            bootTimingsTraceLog.traceBegin("ZygoteInit");
            RuntimeInit.enableDdms();

            boolean startSystemServer = false;
            String socketName = "zygote";
            String abiList = null;
            boolean enableLazyPreload = false;
            for (int i = 1; i < argv.length; i++) {
                if ("start-system-server".equals(argv[i])) {
                    startSystemServer = true;
                } else if ("--enable-lazy-preload".equals(argv[i])) {
                    enableLazyPreload = true;
                } else if (argv[i].startsWith(ABI_LIST_ARG)) {
                    abiList = argv[i].substring(ABI_LIST_ARG.length());
                } else if (argv[i].startsWith(SOCKET_NAME_ARG)) {
                    socketName = argv[i].substring(SOCKET_NAME_ARG.length());
                } else {
                    throw new RuntimeException("Unknown command line argument: " + argv[i]);
                }
            }

            if (abiList == null) {
                throw new RuntimeException("No ABI list supplied.");
            }

            zygoteServer.registerServerSocket(socketName);
            // In some configurations, we avoid preloading resources and classes eagerly.
            // In such cases, we will preload things prior to our first fork.
            if (!enableLazyPreload) {
                bootTimingsTraceLog.traceBegin("ZygotePreload");
                EventLog.writeEvent(LOG_BOOT_PROGRESS_PRELOAD_START,
                    SystemClock.uptimeMillis());
                preload(bootTimingsTraceLog);
                EventLog.writeEvent(LOG_BOOT_PROGRESS_PRELOAD_END,
                    SystemClock.uptimeMillis());
                bootTimingsTraceLog.traceEnd(); // ZygotePreload
            } else {
                Zygote.resetNicePriority();
            }

            // Do an initial gc to clean up after startup
            bootTimingsTraceLog.traceBegin("PostZygoteInitGC");
            gcAndFinalize();
            bootTimingsTraceLog.traceEnd(); // PostZygoteInitGC

            bootTimingsTraceLog.traceEnd(); // ZygoteInit
            // Disable tracing so that forked processes do not inherit stale tracing tags from
            // Zygote.
            Trace.setTracingEnabled(false, 0);

            // Zygote process unmounts root storage spaces.
            Zygote.nativeUnmountStorageOnInit();

            // Set seccomp policy
            Seccomp.setPolicy();

            ZygoteHooks.stopZygoteNoThreadCreation();

            if (startSystemServer) {
                Runnable r = forkSystemServer(abiList, socketName, zygoteServer);

                // {@code r == null} in the parent (zygote) process, and {@code r != null} in the
                // child (system_server) process.
                if (r != null) {
                    r.run();
                    return;
                }
            }

			/* mobiledui: start */
			installRpcGadget();
			/* mobiledui: end */

            Log.i(TAG, "Accepting command socket connections");

            // The select loop returns early in the child process after a fork and
            // loops forever in the zygote.
            caller = zygoteServer.runSelectLoop(abiList);
        } catch (Throwable ex) {
            Log.e(TAG, "System zygote died with exception", ex);
            throw ex;
        } finally {
            zygoteServer.closeServerSocket();
        }

        // We're in the child process and have exited the select loop. Proceed to execute the
        // command.
        if (caller != null) {
            caller.run();
        }
    }

    /**
     * Return {@code true} if this device configuration has another zygote.
     *
     * We determine this by comparing the device ABI list with this zygotes
     * list. If this zygote supports all ABIs this device supports, there won't
     * be another zygote.
     */
    private static boolean hasSecondZygote(String abiList) {
        return !SystemProperties.get("ro.product.cpu.abilist").equals(abiList);
    }

    private static void waitForSecondaryZygote(String socketName) {
        String otherZygoteName = Process.ZYGOTE_SOCKET.equals(socketName) ?
                Process.SECONDARY_ZYGOTE_SOCKET : Process.ZYGOTE_SOCKET;
        ZygoteProcess.waitForConnectionToZygote(otherZygoteName);
    }

    static boolean isPreloadComplete() {
        return sPreloadComplete;
    }

    /**
     * Class not instantiable.
     */
    private ZygoteInit() {
    }

    /**
     * The main function called when started through the zygote process. This
     * could be unified with main(), if the native code in nativeFinishInit()
     * were rationalized with Zygote startup.<p>
     *
     * Current recognized args:
     * <ul>
     *   <li> <code> [--] &lt;start class name&gt;  &lt;args&gt;
     * </ul>
     *
     * @param targetSdkVersion target SDK version
     * @param argv arg strings
     */
    public static final Runnable zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) {
        if (RuntimeInit.DEBUG) {
            Slog.d(RuntimeInit.TAG, "RuntimeInit: Starting application from zygote");
        }

        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ZygoteInit");
        RuntimeInit.redirectLogStreams();

        RuntimeInit.commonInit();
        ZygoteInit.nativeZygoteInit();
        return RuntimeInit.applicationInit(targetSdkVersion, argv, classLoader);
    }

    private static final native void nativeZygoteInit();

	/* mobiledui: start */
    public static void installRpcGadget() {
        final VMRuntime runtime = VMRuntime.getRuntime();

        InputStream is;
        try {
            is = new FileInputStream(PRELOADED_CLASSES);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Couldn't find " + PRELOADED_CLASSES + ".");
            return;
        }
        float defaultUtilization = runtime.getTargetHeapUtilization();
        runtime.setTargetHeapUtilization(0.8f);
			
		Class.initRpcGadget();

        try {
            BufferedReader br
                = new BufferedReader(new InputStreamReader(is), 256);

            String line;
            while ((line = br.readLine()) != null) {
                // Skip comments and blank lines.
                line = line.trim();
                if (line.startsWith("#") || line.equals(""))
                    continue;

				try {
					Class clazz = Class.forName(line, true, null);
					if (line.equals("java.lang.Object") || line.equals("android.os.LocaleList")) 
						continue;
					while (clazz != null && clazz != Object.class) {
						if ((clazz.zFLUIDFlags & RPC_INSTALLED) == 0) {
							Method[] methods = clazz.getDeclaredMethods();
							for (Method method : methods) {
								if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers()))
									continue;
								Class.setRpcGadget(clazz, method.getName(), getSignature(method));
							}
							clazz.zFLUIDFlags |= RPC_INSTALLED;
						}
						clazz = clazz.getSuperclass();
					}

				} catch (ClassNotFoundException e) {
					Log.w(TAG, "Class not found for preloading: " + line);
				} catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "Problem preloading " + line + ": " + e);
                } catch (Throwable t) {
                    Log.e(TAG, "Error preloading " + line + ".", t);
                    if (t instanceof Error) {
                        throw (Error) t;
                    }
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    throw new RuntimeException(t);
                }
			}
        } catch (IOException e) {
            Log.e(TAG, "Error reading " + PRELOADED_CLASSES + ".", e);
        } finally {
            IoUtils.closeQuietly(is);
            runtime.setTargetHeapUtilization(defaultUtilization);
        }
    }

    private static String getSignature(Method method) {
        StringBuilder result = new StringBuilder();

        result.append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
            result.append(Types.getSignature(parameterType));
        }
        result.append(')');
        result.append(Types.getSignature(method.getReturnType()));

        return result.toString();
    }
	/* mobiledui: end */
}
