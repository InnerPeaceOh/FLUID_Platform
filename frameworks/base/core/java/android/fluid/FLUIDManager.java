package android.fluid;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import libcore.reflect.Types;
import dalvik.system.DexClassLoader;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.IBinder;
import android.os.Environment;
import android.os.Message;
import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.view.ViewGroup;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.app.Activity;
import android.app.ResourcesManager;
import android.content.res.Resources;
import android.content.res.CompatibilityInfo;
import android.graphics.drawable.Drawable;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.util.Log;
import android.util.AttributeSet;
import android.fluid.kryo.Kryo;
import android.fluid.kryo.io.Output;
import android.fluid.kryo.io.Input;
import android.fluid.objenesis.strategy.StdInstantiatorStrategy;
import java.io.FileWriter;
import java.io.PrintWriter;

/** @hide */
public class FLUIDManager 
{
    public static final String DUI_TAG = "MOBILEDUI(FLUIDManager)";
    static final boolean DUI_DEBUG = false;
	static public final int MIGRATED = 0x00010000;
	static public final int IN_REMOTE = 0x00001111;
	static public final int RPC_INSTALLED = 0x00100000;
	static public final int DUMMY_OBJECT = 0x01000000;
	static public final int RETURN_CACHE = 1;
	static public final int RPC_CACHE = 2;
	static public final int NULL = 0;
	static public final int NOT_NULL = 1;

	private static FLUIDManager sInstance;
	private final IFLUIDService mService;
	public final Kryo mKryo;
	private HashMap<String, DexClassLoader> mClassLoaders;
	public HashMap<String, Resources> mResourceMap;
	public HashMap<Integer, Object> mIdToObj;
	public boolean mIsWaiting = false;
	public LinkedList<ByteArrayInputStream> mRecvCaches = null;
	public boolean mIsHostDevice;
	public boolean mUiSelectionMode = false;
	public int mTouchPointNums = 0;
	public LinkedList<View> mOverlayList = new LinkedList<View>();
	private ViewGroup.LayoutParams mRootParams = null;
	private View[] mPrevRemoteViews = null;
	public DexClassLoader mDexLoader;
	public ArrayList<View> mTargetViews;
	public boolean mIsReplicationMode = false;

	public final boolean mPreCachingMode = true;
	private LinkedList<ByteArrayOutputStream> mNaiveCaches = null;
	public boolean isWaiting = false;

	public static FLUIDManager getInstance() {
		synchronized (FLUIDManager.class) {
			if (sInstance == null) {
				IBinder b = ServiceManager.getService("fluid_service");
				if (b != null) {
					sInstance = new FLUIDManager(IFLUIDService.Stub.asInterface(b));
				}
			}
			return sInstance;
		}
	}

	public FLUIDManager(IFLUIDService service) {
		mService = service;
		mKryo = new Kryo();
        mKryo.setInstantiatorStrategy(
				new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));

		mIdToObj = new HashMap<Integer, Object>();
		mKryo.mIdToObj = mIdToObj;

		mRecvCaches = new LinkedList<ByteArrayInputStream>();
		mIsHostDevice = isHostDevice();
		mNaiveCaches = new LinkedList<ByteArrayOutputStream>();
	}

	public void clear() {
		for (Object obj : mKryo.mIdToObj.values()) {
			obj.zObjectId = 0;
			obj.zFLUIDFlags = 0;
		}
		mKryo.mIdToObj.clear();
		mKryo.mTargetObjIds.clear();
	}

	public void serializeUi(Context context, ArrayList<View> targetViews) {
		/* temporary code block: start */
		// LOG 1
		//{
		//	long time = System.nanoTime();
		//	Log.d("EXPR", "LOG 1: " + time);
		//	//try {
		//	//	File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		//	//	File f = new File(path, "dist_time"); 
		//	//	FileWriter write = new FileWriter(f, true);
		//	//	PrintWriter out = new PrintWriter(write);
		//	//	out.print(time);
		//	//	out.close();
		//	//} catch (Exception e) {
		//	//	e.printStackTrace();
		//	//}
		//}
		/* temporary code block: end */
        if (targetViews == null) 
			throw new AssertionError("LayoutInflater.targetViews not found.");

		try {
			mTargetViews = targetViews;
			String packageName = context.getPackageName();
			Log.d(DUI_TAG, "serializeUi(), packageName = " + packageName);
			mKryo.mRenderingObjs = mKryo.mAnalysisData.get(packageName);

			int length = targetViews.size();
			ByteArrayOutputStream totalBaos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(totalBaos);

			dos.writeInt(context.getThemeResId());
			String[] uiClassNames = new String[length];
			//for (int i = 0; i < length; i++) {
			for (int i = length - 1; i >= 0; i--) {
				final View view = targetViews.get(i);
				ViewGroup.LayoutParams params = null;
				Class clazz = ((Object)view).getClass();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Output output = new Output(baos);

				if (i == 0) {
					params =  view.clearLayoutParamsForFLUID();
					View parent = (View)view.getParent();
					Drawable background = parent.getBackground();
					while (background == null) {
						parent = (View)parent.getParent();
						if (parent == null)
							break;
						background = parent.getBackground();
					}

					if (params != null) {
						output.writeVarInt(Kryo.NOT_NULL, true);
						output.writeVarInt(params.width, true);
						output.writeVarInt(params.height, true);
					}
					else
						output.writeVarInt(Kryo.NULL, true);

					if (background != null) {
						Drawable.Callback cb = background.getCallback();
						background.setCallback(null);
						output.writeVarInt(Kryo.NOT_NULL, true);
						mKryo.writeClass(output, background.getClass());
						mKryo.writeObject(output, background);
						background.setCallback(cb);
					}
					else
						output.writeVarInt(Kryo.NULL, true);
				}
				view.mIsInScrollingContainer = view.isInScrollingContainer();
				view.mOrigWidth = view.getWidth();
				view.mOrigHeight = view.getHeight();

				mKryo.writeFLUIDObject(output, view);
				output.close();

				byte[] uiData = baos.toByteArray();
				baos.close();
				dos.writeInt(uiData.length);
				totalBaos.write(uiData, 0, uiData.length);
				uiClassNames[i] = clazz.getName();

				if (DUI_DEBUG) {
					Log.d(DUI_TAG, "serializeUi()"
							+ ", i = " + i
							+ ", clazz = " + clazz
							+ ", view = " + view
							+ ", zFLUIDFlags = " + view.zFLUIDFlags
							+ ", zObjectId = " + view.zObjectId
							+ ", uiData.length = " + uiData.length);
				}
				if (i == 0) {
					view.resetLayoutParamsForFLUID(params);
				}
				if (TextView.class.isAssignableFrom(clazz)) {
					TextView textView = (TextView)view;
					final InputMethodManager imm = InputMethodManager.peekInstance();
					if (textView.getShowSoftInputOnFocus()
							&& textView.getInputType() != EditorInfo.TYPE_NULL
							&& imm != null) {
						imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
					}
				}
			}

			/* temporary code block: start */
			// LOG 2
			//{
			//	long time = System.nanoTime();
			//	Log.d("EXPR", "LOG 2: " + time);
			//	//try {
			//	//	File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			//	//	File f = new File(path, "dist_time"); 
			//	//	FileWriter write = new FileWriter(f, true);
			//	//	PrintWriter out = new PrintWriter(write);
			//	//	out.println(" " + time);
			//	//	out.println();
			//	//	out.close();
			//	//} catch (Exception e) {
			//	//	e.printStackTrace();
			//	//}
			//}
			/* temporary code block: end */

			byte[] totalUiData = totalBaos.toByteArray();
			sendUi(packageName, totalUiData, uiClassNames);
			dos.close();
			totalBaos.close();

			View wrapperLayout = targetViews.get(0);
			wrapperLayout.zFLUIDFlags &= ~MIGRATED; 

			for (int i = length - 1; i >= 0; i--) {
				View view = targetViews.get(i);
				view.invalidate();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void restoreUi(Context context, ViewGroup contentParent, 
			String packageName, byte[] viewData, String[] classNames) {
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "restoreUi()"
					+ ", contentParent = " + contentParent
					+ ", packageName = " + packageName
					+ ", viewData = " + viewData 
					+ ", viewData.length = " + viewData.length
					+ ", classNames = " + classNames);
		}

		mDexLoader = getClassLoader(packageName);
		mKryo.mDexLoader = mDexLoader;
		mKryo.mAppContext = context;
		mKryo.mWindowManager = ((Activity)context).getWindowManager();
		mKryo.mWindow = ((Activity)context).getWindow();
		mKryo.mDecor = mKryo.mWindow.getDecorView();
		mKryo.mRenderingObjs = mKryo.mAnalysisData.get(packageName);
		mKryo.mResources = getResources(packageName);
		context.getResources().setImpl(mKryo.mResources.getImpl());

		try {
			if (mDexLoader == null) throw new Exception("no DexClassLoader for " + packageName);
			int length = classNames.length;
			View[] remoteViews = new View[length];
			ByteArrayInputStream totalBais = new ByteArrayInputStream(viewData);
			DataInputStream dis = new DataInputStream(totalBais);

			int themeResId = dis.readInt();
			context.setTheme(themeResId);
			ViewGroup duiContainer = (ViewGroup)contentParent.getChildAt(0);
			contentParent.removeView(duiContainer);
			FrameLayout.LayoutParams params = null;

			//for (int i = 0; i < length; i++) {
			for (int i = length - 1; i >= 0; i--) {
				Class clazz = null;
				try {
					clazz = Class.forName(classNames[i]);
				} catch (ClassNotFoundException e) {
					clazz = mDexLoader.loadClass(classNames[i]);
				}
				int uiSize = dis.readInt();
				byte[] uiData = new byte[uiSize];
				totalBais.read(uiData, 0, uiSize);
				
				Constructor<?> constructor = null;
				View view = null;
				try {
					constructor = clazz.getConstructor(Context.class);
					view = (View) constructor.newInstance(context);
				} catch (NoSuchMethodException e) {
					constructor = clazz.getConstructor(Context.class, AttributeSet.class);
					view = (View) constructor.newInstance(context, null);
				}
				ByteArrayInputStream bais = new ByteArrayInputStream(uiData);
				Input input = new Input(bais);

				if (i ==  0) {
					if (input.readVarInt(true) == Kryo.NOT_NULL) {
						int width = input.readVarInt(true);
						int height = input.readVarInt(true);
						if (width == 0)
							width = ViewGroup.LayoutParams.WRAP_CONTENT;
						if (height == 0)
							height = ViewGroup.LayoutParams.WRAP_CONTENT;

						params = new FrameLayout.LayoutParams(width, height);
					}
					if (input.readVarInt(true) == Kryo.NOT_NULL) {
						Class drawableClazz = mKryo.readClass(input).getType();
						Drawable background = (Drawable)mKryo.readObject(input, drawableClazz);
						contentParent.setBackground(background);
					}
				}

				view.mFLUIDManager = this;
				view.mKryo = mKryo;
				view = (View) mKryo.readFLUIDObject(input, clazz, view);
				input.close();
				bais.close();
				remoteViews[i] = view;
				if (DUI_DEBUG) {
					Log.d(DUI_TAG, "restoreUi()" + ", i = " + i + ", view = " + view
							+ ", clazz = " + clazz + ", zFLUIDFlags = " + view.zFLUIDFlags
							+ ", zObjectId = " + view.zObjectId
							+ ", parent id = " + view.mParentRemoteId
							+ ", uiData.length = " + uiData.length);
				}
			}

			if (mPrevRemoteViews != null) {
				contentParent.removeAllViewsInLayout();
				for (int i = 0; i < mPrevRemoteViews.length; i++) {
					View view = mPrevRemoteViews[i];
					if (view instanceof ViewGroup)
						((ViewGroup)view).removeAllViewsInLayout();
				}
			}

			if (params != null)
				remoteViews[0].setLayoutParams(params);
			contentParent.addView(remoteViews[0]);
			for (int i = 1; i < length; i++) {
				View view = remoteViews[i];
				if (DUI_DEBUG)
					Log.d(DUI_TAG, "restoreUi()" + ", i = " + i + ", view = " + view);
				view.dispatchDetachedFromWindowForFLUID();
				int parentId = view.mParentRemoteId;
				ViewGroup parent = (ViewGroup)remoteViews[parentId];
				parent.addView(view);
			}
			mPrevRemoteViews = remoteViews;
			
			params = (FrameLayout.LayoutParams)remoteViews[0].getLayoutParams();
			params.gravity = Gravity.CENTER;

			dis.close();
			totalBais.close();
			contentParent.requestLayout();
			contentParent.invalidate();
			remoteViews[0].zFLUIDFlags &= ~MIGRATED;
		} catch (Exception e) {
			e.printStackTrace();
		}
		/* temporary code block: start */
		// LOG 5
		//{
		//	long time = System.nanoTime();
		//	Log.d("EXPR", "LOG 5: " + time);
		//	//try {
		//	//	File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		//	//	File f = new File(path, "dist_time"); 
		//	//	FileWriter write = new FileWriter(f, true);
		//	//	PrintWriter out = new PrintWriter(write);
		//	//	out.println(time);
		//	//	//out.println();
		//	//	out.close();
		//	//} catch (Exception e) {
		//	//	e.printStackTrace();
		//	//}
		//}
		/* temporary code block: end */
	}

    public void rpcTranslation(Object object, 
			String methodName, String methodSig, Object[] args) {
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "rpcTranslation()"
					+ ", object = " + object.getClass()
					+ ", objectId = " + object.zObjectId
					+ ", methodName = " + methodName 
					+ ", methodSig = " + methodSig);
		}

		try {
			int argc = args.length, idx = 0;
			char[] argSig = methodSig.substring(1, methodSig.indexOf(')')).toCharArray();
			String[] types = (argc != 0)? new String[argc] : null;
			ByteArrayOutputStream totalBaos = new ByteArrayOutputStream();
			DataOutputStream totalDos = new DataOutputStream(totalBaos);

			for (int i = 0; i < argc; i++) {
				if (DUI_DEBUG)
					Log.d(DUI_TAG, "args[" + i + "] = " + ((args[i] != null)? args[i].getClass() : "null") + ", argSig = " + argSig[idx]);

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);

				if (argSig[idx] == 'Z') {
					dos.writeBoolean((Boolean)args[i]);
				}
				else if (argSig[idx] == 'B') {
					int value = ((Byte)args[i]).intValue();
					dos.writeByte(value);
				}
				else if (argSig[idx] == 'C') {
					int value = (int)((Character)args[i]).charValue();
					dos.writeChar(value);
				}
				else if (argSig[idx] == 'S') {
					int value = ((Short)args[i]).intValue();
					dos.writeShort(value);
				}
				else if (argSig[idx] == 'I') {
					dos.writeInt((Integer)args[i]);
				}
				else if (argSig[idx] == 'J') {
					dos.writeLong((Long)args[i]);
				}
				else if (argSig[idx] == 'F') {
					dos.writeFloat((Float)args[i]);
				}
				else if (argSig[idx] == 'D') {
					dos.writeDouble((Double)args[i]);
				}
				else if (args[i] != null) {
					Class clazz = args[i].getClass();
					types[i] = clazz.getName();
					Output output = new Output(baos);
					if ((object.zFLUIDFlags & DUMMY_OBJECT) == 0
							&& mKryo.needPartialMigration(clazz))
						mKryo.writeFLUIDObject(output, args[i]);
					else 
						mKryo.writeObject(output, args[i]);
					output.close();
				}
				byte[] arg = baos.toByteArray();
				Log.d(DUI_TAG, "argSize = " + arg.length);
				totalDos.writeInt(arg.length);
				totalBaos.write(arg, 0, arg.length);
				idx = nextIdx(argSig, idx);
				baos.close();
				dos.close();
			}
			byte[] argsData = (argc != 0)? totalBaos.toByteArray() : null;
			Parcel reply = sendRpc(object.zObjectId, methodName, methodSig, types, argsData);
			totalBaos.close();
			totalDos.close();
		} catch (Exception e) { e.printStackTrace(); }
    }

	public void executeRpc(int id, String methodName, String methodSig, 
			String[] types, byte[] args, Parcel reply) {
		Object object  = mIdToObj.get(id);
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "executeRpc(), object = " + object
					+ ", objectId = " + id
					+ ", methodName = " + methodName
					+ ", methodSig = " + methodSig
					+ ", types = " + types
					+ ", args = " + args
					+ ", reply = " + reply);
		}
		try {
			int argc = (types != null)? types.length : 0, idx = 0;
			char[] argSig = methodSig.substring(1, methodSig.indexOf(')')).toCharArray();
			Class clazz = object.getClass();
			Class[] paramClazz = (argc != 0)? new Class[argc] : null;
			Object[] paramObject = (argc != 0)? new Object[argc] : null;
			ByteArrayInputStream totalBais = null;
			DataInputStream totalDis = null;
			if (args != null) {
				totalBais = new ByteArrayInputStream(args);
				totalDis = new DataInputStream(totalBais);
			}

			for (int i = 0; i < argc; i++) {
				Log.d(DUI_TAG, "args[" + i + "] argSig = " + argSig[idx] + ", type = " + types[i]);
				int argSize = totalDis.readInt();
				byte[] arg = new byte[argSize];
				totalBais.read(arg, 0, argSize);
				ByteArrayInputStream bais = new ByteArrayInputStream(arg);
				DataInputStream dis = new DataInputStream(bais);

				if (argSig[idx] == 'Z') {
					paramObject[i] = dis.readBoolean();
					paramClazz[i] = boolean.class;
				}
				else if (argSig[idx] == 'B') {
					paramObject[i] = dis.readByte();
					paramClazz[i] = byte.class;
				}
				else if (argSig[idx] == 'C') {
					paramObject[i] = dis.readChar();
					paramClazz[i] = char.class;
				}
				else if (argSig[idx] == 'S') {
					paramObject[i] = dis.readShort();
					paramClazz[i] = short.class;
				}
				else if (argSig[idx] == 'I') {
					paramObject[i] = dis.readInt();
					paramClazz[i] = int.class;
				}
				else if (argSig[idx] == 'J') {
					paramObject[i] = dis.readLong();
					paramClazz[i] = long.class;
				}
				else if (argSig[idx] == 'F') {
					paramObject[i] = dis.readFloat();
					paramClazz[i] = float.class;
				}
				else if (argSig[idx] == 'D') {
					paramObject[i] = dis.readDouble();
					paramClazz[i] = double.class;
				}
				else {
					String className;
					Class argClazz = null;
					if (argSig[idx] == 'L') {
						int tmpIdx = idx;
						while (argSig[tmpIdx] != ';') tmpIdx++;
						className = new String(argSig, idx+1, tmpIdx-idx-1);
					}
					else if (argSig[idx] == '[' && argSig[idx+1] == 'L') {
						int tmpIdx = idx;
						while (argSig[tmpIdx] != ';') tmpIdx++;
						className = new String(argSig, idx, tmpIdx-idx+1);
					}
					else
						className = new String(argSig, idx, 2);

					try {
						argClazz = Class.forName(className);
					} catch (ClassNotFoundException e) {
						argClazz = mKryo.mDexLoader.loadClass(className);
					}
					paramClazz[i] = argClazz;
					Log.d(DUI_TAG, "executeRpc(), argClazz = " + argClazz);

					if (types[i] != null && !types[i].equals("")) {
						try {
							argClazz = Class.forName(types[i]);
						} catch (ClassNotFoundException e) {
							if (mKryo.mDexLoader != null)
								argClazz = mKryo.mDexLoader.loadClass(types[i]);
							else
								argClazz = mKryo.mAppContext.getClassLoader().loadClass(types[i]);
						}
						Input input = new Input(bais);
						if ((object.zFLUIDFlags & MIGRATED) == 0 
								|| !mKryo.needPartialMigration(argClazz))
							paramObject[i] = mKryo.readObject(input, argClazz);
						else
							paramObject[i] = mKryo.readFLUIDObject(input, argClazz);

						input.close();
					}
				}
				idx = nextIdx(argSig, idx);
				bais.close();
				dis.close();
			}
			if (totalBais != null) totalBais.close();
			if (totalDis != null) totalDis.close();

			Class tempClazz = clazz;
			Method method = null;
			while (tempClazz != Object.class) {
				try {
					method = tempClazz.getDeclaredMethod(methodName, paramClazz);
					method.setAccessible(true);
					break;
				}
				catch (Exception e) {
					tempClazz = tempClazz.getSuperclass();
				}
			}
			Log.d(DUI_TAG, "executeRpc(), Invoke the method = " + method);

			Object res = method.invoke(object, paramObject);
			/* temporary code block: start */
			// LOG 5
			{
				long time = System.nanoTime();
				Log.d("EXPRI", "LOG 5: " + time + ", " + methodName);
				//try {
				//	File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
				//	File f = new File(path, "dist_time"); 
				//	FileWriter write = new FileWriter(f, true);
				//	PrintWriter out = new PrintWriter(write);
				//	out.println(time);
				//	//out.println();
				//	out.close();
				//} catch (Exception e) {
				//	e.printStackTrace();
				//}
			}
			/* temporary code block: end */

			// Handling the return of RPC
			String retType = Types.getSignature(method.getReturnType());
			if (res != null && reply != null) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);
				if (retType.equals("Z")) {
					dos.writeBoolean((Boolean)res);
				}
				else if (retType.equals("B")) {
					int value = ((Byte)res).intValue();
					dos.writeByte(value);
				}
				else if (retType.equals("C")) {
					int value = (int)((Character)res).charValue();
					dos.writeChar(value);
				}
				else if (retType.equals("S")) {
					int value = ((Short)res).intValue();
					dos.writeShort(value);
				}
				else if (retType.equals("I")) {
					dos.writeInt((Integer)res);
				}
				else if (retType.equals("J")) {
					dos.writeLong((Long)res);
				}
				else if (retType.equals("F")) {
					dos.writeFloat((Float)res);
				}
				else if (retType.equals("D")) {
					dos.writeDouble((Double)res);
				}
				else if (retType != null) {
					retType = res.getClass().getName();
					Output output = new Output(baos);
					mKryo.writeObject(output, res);
					output.close();
				}
				byte[] buffer = baos.toByteArray();
				reply.writeNoException();
				reply.writeInt(1);
				reply.writeString(retType);
				reply.writeToAshmem(buffer);
				Log.d(DUI_TAG, "executeRpc() res = " + res + ", retType = " + retType 
						+ ", buffer = " + buffer + ", length = " + buffer.length);
				baos.close();
				dos.close();
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

    public void returnCaching(int objectId,
			String methodName, String methodSig, Object ret) {
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "returnCaching()"
					+ ", objectId = " + objectId
					+ ", methodName = " + methodName
					+ ", methodSig = " + methodSig
					+ ", ret = " + ((ret != null)? ret.getClass().getName() : "null"));
		}
		try {
			int startIdx = methodSig.indexOf(')') + 1;
			char[] retSig = methodSig.substring(startIdx, startIdx + 1).toCharArray();
			ByteArrayOutputStream totalBaos = new ByteArrayOutputStream();
			DataOutputStream totalDos = new DataOutputStream(totalBaos);
			totalDos.writeInt(RETURN_CACHE);
			totalDos.writeInt(objectId);
			totalDos.writeUTF(methodName);
			totalDos.writeUTF(methodSig);

			if (retSig[0] == 'Z') {
				totalDos.writeBoolean((Boolean)ret);
			}
			else if (retSig[0] == 'B') {
				int value = ((Byte)ret).intValue();
				totalDos.writeByte(value);
			}
			else if (retSig[0] == 'C') {
				int value = (int)((Character)ret).charValue();
				totalDos.writeChar(value);
			}
			else if (retSig[0] == 'S') {
				int value = ((Short)ret).intValue();
				totalDos.writeShort(value);
			}
			else if (retSig[0] == 'I') {
				totalDos.writeInt((Integer)ret);
			}
			else if (retSig[0] == 'J') {
				totalDos.writeLong((Long)ret);
			}
			else if (retSig[0] == 'F') {
				totalDos.writeFloat((Float)ret);
			}
			else if (retSig[0] == 'D') {
				totalDos.writeDouble((Double)ret);
			}
			else {
				if (ret != null) {
					totalDos.writeInt(NOT_NULL);
					Class clazz = ret.getClass();
					String clazzName = clazz.getName();
					totalDos.writeUTF(clazzName);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					Output output = new Output(baos);
					mKryo.writeObject(output, ret);
					output.close();
					byte[] data = baos.toByteArray();
					totalDos.writeInt(data.length);
					totalBaos.write(data, 0, data.length);
					baos.close();
				}
				else
					totalDos.writeInt(NULL);
			}
			byte[] cacheData = totalBaos.toByteArray();
			if (mPreCachingMode)
				sendCache(objectId, cacheData);
			else {
				synchronized (mNaiveCaches) {
					mNaiveCaches.offer(totalBaos);
					if (isWaiting)
						mNaiveCaches.notifyAll();
				}
			}


			totalBaos.close();
			totalDos.close();
		} catch (Exception e) { e.printStackTrace(); }
	}

    public void rpcCaching(Object object, 
			String methodName, String methodSig, Object[] args) {

		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "rpcCaching()"
					+ ", object = " + object.getClass()
					+ ", objectId = " + object.zObjectId
					+ ", methodName = " + methodName 
					+ ", methodSig = " + methodSig);
		}

		try {
			int argc = args.length, idx = 0;
			char[] argSig = methodSig.substring(1, methodSig.indexOf(')')).toCharArray();
			String[] types = (argc != 0)? new String[argc] : null;
			ByteArrayOutputStream totalBaos = new ByteArrayOutputStream();
			DataOutputStream totalDos = new DataOutputStream(totalBaos);
			totalDos.writeInt(RPC_CACHE);
			totalDos.writeInt(object.zObjectId);
			totalDos.writeUTF(methodName);
			totalDos.writeUTF(methodSig);

			ByteArrayOutputStream argsBaos = new ByteArrayOutputStream();
			DataOutputStream argsDos = new DataOutputStream(argsBaos);
			for (int i = 0; i < argc; i++) {
				if (DUI_DEBUG)
					Log.d(DUI_TAG, "args[" + i + "] = " + ((args[i] != null)? args[i].getClass() : "null") + ", argSig = " + argSig[idx]);

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);

				if (argSig[idx] == 'Z') {
					dos.writeBoolean((Boolean)args[i]);
				}
				else if (argSig[idx] == 'B') {
					int value = ((Byte)args[i]).intValue();
					dos.writeByte(value);
				}
				else if (argSig[idx] == 'C') {
					int value = (int)((Character)args[i]).charValue();
					dos.writeChar(value);
				}
				else if (argSig[idx] == 'S') {
					int value = ((Short)args[i]).intValue();
					dos.writeShort(value);
				}
				else if (argSig[idx] == 'I') {
					dos.writeInt((Integer)args[i]);
				}
				else if (argSig[idx] == 'J') {
					dos.writeLong((Long)args[i]);
				}
				else if (argSig[idx] == 'F') {
					dos.writeFloat((Float)args[i]);
				}
				else if (argSig[idx] == 'D') {
					dos.writeDouble((Double)args[i]);
				}
				else if (args[i] != null) {
					Class clazz = args[i].getClass();
					types[i] = clazz.getName();
					Output output = new Output(baos);
					if (mKryo.needPartialMigration(clazz))
						mKryo.writeFLUIDObject(output, args[i]);
					else
						mKryo.writeObject(output, args[i]);
					output.close();
				}
				byte[] arg = baos.toByteArray();
				Log.d(DUI_TAG, "argSize = " + arg.length);
				argsDos.writeInt(arg.length);
				argsBaos.write(arg, 0, arg.length);
				idx = nextIdx(argSig, idx);
				baos.close();
				dos.close();
			}
			byte[] argsData = (argc != 0)? argsBaos.toByteArray() : null;
			if (argsData != null) {
				totalDos.writeInt(argsData.length);
				totalBaos.write(argsData, 0, argsData.length);
			}
			else
				totalDos.writeInt(0);

			if (types != null) {
				totalDos.writeInt(types.length);
				for (int i = 0; i < types.length; i++) {
					if (types[i] == null) {
						totalDos.writeUTF("");
						continue;
					}
					totalDos.writeUTF(types[i]);
				}
			}
			else
				totalDos.writeInt(0);

			byte[] cacheData = totalBaos.toByteArray();
			if (mPreCachingMode)
				sendCache(-1, cacheData);
			else {
				synchronized (mNaiveCaches) {
					mNaiveCaches.offer(totalBaos);
					if (isWaiting)
						mNaiveCaches.notifyAll();
				}
			}

			totalBaos.close();
			totalDos.close();

		} catch (Exception e) { e.printStackTrace(); }
    }

	public void storeCache(int objectId, byte[] cacheData) {
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "storeCache(), objectId = " + objectId + ", cacheData = " + cacheData);

		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(cacheData);
			synchronized (mRecvCaches) {
				mRecvCaches.offer(bais);
				if (mIsWaiting)
					mRecvCaches.notify();
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

    public Object executeCache(int objectId, String methodName, String methodSig) {
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "executeCache() start, objectId = " + objectId + ", methodName = " + methodName + ", methodSig = " + methodSig);

		try {
			ByteArrayInputStream[] bais = new ByteArrayInputStream[2];
			if (!mPreCachingMode) {
				byte[][] caches = requestCache(objectId, methodName, methodSig);
				Log.d(DUI_TAG, "executeCache() bp 0, caches[0] = " + caches[0] + ", caches[1] = " + caches[1]);
				bais[0] = new ByteArrayInputStream(caches[0]);
				bais[1] = new ByteArrayInputStream(caches[1]);

				mRecvCaches.offer(bais[0]);
			}

			synchronized (mRecvCaches) {
				Log.d("FUCK", "executeCache() mRecvCaches = " + mRecvCaches);
				boolean isMine = false;
				if (!mRecvCaches.isEmpty()) {
					DataInputStream dis = new DataInputStream(mRecvCaches.getFirst());
					int type = dis.readInt();
					int recvObjId = dis.readInt();
					String recvMethodName = dis.readUTF();
					String recvMethodSig = dis.readUTF();
					isMine = (type == RETURN_CACHE && objectId == recvObjId 
							&& methodName.equals(recvMethodName) && methodSig.equals(recvMethodSig));
					Log.d("FUCK", "executeCache(), type = " + type + ", recvObjId = " + recvObjId);
				}
				Log.d("FUCK", "executeCache() bp 1 isMine = " + isMine);

				while (mRecvCaches.isEmpty() || !isMine) {
					if (mRecvCaches.isEmpty()) {
						mIsWaiting = true;
						mRecvCaches.wait();
						mIsWaiting = false;
						DataInputStream dis = new DataInputStream(mRecvCaches.getFirst());
						int type = dis.readInt();
						int recvObjId = dis.readInt();
						String recvMethodName = dis.readUTF();
						String recvMethodSig = dis.readUTF();
						isMine = (type == RETURN_CACHE && objectId == recvObjId 
							&& methodName.equals(recvMethodName) && methodSig.equals(recvMethodSig));
						Log.d("FUCK", "executeCache(), type = " + type + ", recvObjId = " + recvObjId);
						Log.d("FUCK", "executeCache() bp 2 isMine = " + isMine);
						if (isMine)
							break;
					}
					Log.d("FUCK", "executeCache() mRecvCaches = " + mRecvCaches);

					handleRpcCache(mRecvCaches.poll());

					if (!mPreCachingMode)
						mRecvCaches.offer(bais[1]);

					if (!mRecvCaches.isEmpty()) {
						DataInputStream dis = new DataInputStream(mRecvCaches.getFirst());
						int type = dis.readInt();
						int recvObjId = dis.readInt();
						String recvMethodName = dis.readUTF();
						String recvMethodSig = dis.readUTF();
						isMine = (type == RETURN_CACHE && objectId == recvObjId 
							&& methodName.equals(recvMethodName) && methodSig.equals(recvMethodSig));
					}
				}
				if (DUI_DEBUG) 
					Log.d(DUI_TAG, "executeCache() end, objectId = " + objectId + ", methodName = " + methodName + ", methodSig = " + methodSig);

				return handleReturnCache(mRecvCaches.poll(), objectId, methodName, methodSig);
			}
		} catch (Exception e) { e.printStackTrace(); }
		return null;
	}

	public void handleRpcCache(ByteArrayInputStream bais) 
			throws Exception {
		bais.reset();
		DataInputStream dis = new DataInputStream(bais);
		int type = dis.readInt();
		int recvObjId = dis.readInt();
		if (type == RETURN_CACHE) {
			Log.d(DUI_TAG, "handleRpcCache() start, type = " + type + ", recvObjId = " + recvObjId);
			Log.d(DUI_TAG, "handleRpcCache() cache ordering error! It's a return cache.");
			return;
			//throw new RuntimeException("handleRpcCache() cache ordering error! It's a return cache.");
		}
		String methodName = dis.readUTF();
		String methodSig = dis.readUTF();
		int argsSize = dis.readInt();
		byte[] args = (argsSize != 0)? new byte[argsSize] : null;
		if (args != null) bais.read(args, 0, argsSize);
		int typesSize = dis.readInt();
		String[] types = (typesSize != 0)? new String[typesSize] : null;
		if (types != null) {
			for (int i = 0; i < typesSize; i++)
				types[i] = dis.readUTF();
		}
		executeRpc(recvObjId, methodName, methodSig, types, args, null);
		dis.close();
		bais.close();
	}

	public Object handleReturnCache(ByteArrayInputStream bais, 
			int objectId, String methodName, String methodSig) throws Exception {
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "handleReturnCache() objectId = " + objectId + ", methodName = " + methodName + ", methodSig = " + methodSig);

		int startIdx = methodSig.indexOf(')') + 1;
		char[] retSig = methodSig.substring(startIdx, startIdx + 1).toCharArray();
		bais.reset();
		DataInputStream dis = new DataInputStream(bais);
		int type = dis.readInt();
		int recvObjId = dis.readInt();
		String recvMethodName = dis.readUTF();
		String recvMethodSig = dis.readUTF();

		if (objectId != recvObjId || !methodName.equals(recvMethodName) 
				|| !methodSig.equals(recvMethodSig)) {
			throw new RuntimeException("handleReturnCache() recvObjId = " + recvObjId 
					+ ", recvMethodName = " + recvMethodName
					+ ", recvMethodSig = " + recvMethodSig);
		}

		Object ret = null;
		if (retSig[0] == 'Z') {
			ret = dis.readBoolean();
		}
		else if (retSig[0] == 'B') {
			ret = dis.readByte();
		}
		else if (retSig[0] == 'C') {
			ret = dis.readChar();
		}
		else if (retSig[0] == 'S') {
			ret = dis.readShort();
		}
		else if (retSig[0] == 'I') {
			ret = dis.readInt();
		}
		else if (retSig[0] == 'J') {
			ret = dis.readLong();
		}
		else if (retSig[0] == 'F') {
			ret = dis.readFloat();
		}
		else if (retSig[0] == 'D') {
			ret = dis.readDouble();
		}
		else {
			int isNotNull = dis.readInt();
			if (isNotNull == 1) {
				String clazzName = dis.readUTF();
				int size = dis.readInt();
				byte[] data = new byte[size];
				bais.read(data, 0, size);
				ByteArrayInputStream argBais = new ByteArrayInputStream(data);

				Class clazz = null;
				try {
					clazz = Class.forName(clazzName);
				} catch (ClassNotFoundException e) {
					clazz = mKryo.mDexLoader.loadClass(clazzName);
				}

				Input input = new Input(argBais);
				ret = mKryo.readObject(input, clazz);
				input.close();
			}
		}
		dis.close();
		bais.close();
		return ret;
	}

	// For the naive approach
	// HARD CODING for experiment 1
	public void getCache(int objectId, String methodName, String methodSig, Parcel reply) {
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "getCache() start, objectId = " + objectId + ", methodName = " + methodName + ", methodSig = " + methodSig);

		try {
			synchronized (mNaiveCaches) {
				boolean isMine = false;
				boolean isRetCache = false;

				if (mNaiveCaches.isEmpty()) {
					isWaiting = true;
					mNaiveCaches.wait();
					isWaiting = false;
				}
				if (!mNaiveCaches.isEmpty()) {
					byte[] cacheData = mNaiveCaches.getFirst().toByteArray();
					ByteArrayInputStream bais = new ByteArrayInputStream(cacheData);
					DataInputStream dis = new DataInputStream(bais);
					int type = dis.readInt();
					int recvObjId = dis.readInt();
					String recvMethodName = dis.readUTF();
					String recvMethodSig = dis.readUTF();
					isMine = (type == RETURN_CACHE && objectId == recvObjId 
							&& methodName.equals(recvMethodName) && methodSig.equals(recvMethodSig));
					isRetCache = (type == RETURN_CACHE);
					Log.d(DUI_TAG, "getCache() bp 1, objectId = " + objectId + ", type = " + type + ", recvObjId = " + recvObjId + ", recvMethodName = " + recvMethodName + ", recvMethodSig = " + recvMethodSig + ", isMine = " + isMine + ", isRetCache = " + isRetCache);
				}

				if (!isMine && !isRetCache) {
					//sendCache(-1, mNaiveCaches.poll().toByteArray());
					//mNaiveCaches.notifyAll();
					reply.writeByteArray(mNaiveCaches.poll().toByteArray());
				}

				if (!mNaiveCaches.isEmpty()) {
					byte[] cacheData = mNaiveCaches.getLast().toByteArray();
					ByteArrayInputStream bais = new ByteArrayInputStream(cacheData);
					DataInputStream dis = new DataInputStream(bais);
					int type = dis.readInt();
					int recvObjId = dis.readInt();
					String recvMethodName = dis.readUTF();
					String recvMethodSig = dis.readUTF();
					isMine = (type == RETURN_CACHE && objectId == recvObjId 
							&& methodName.equals(recvMethodName) && methodSig.equals(recvMethodSig));
					isRetCache = (type == RETURN_CACHE);
					Log.d(DUI_TAG, "getCache() bp 2, objectId = " + objectId + ", type = " + type + ", recvObjId = " + recvObjId + ", recvMethodName = " + recvMethodName + ", recvMethodSig = " + recvMethodSig + ", isMine = " + isMine + ", isRetCache = " + isRetCache);
				}

				if (isMine && isRetCache) {
					reply.writeByteArray(mNaiveCaches.pollLast().toByteArray());
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
		Log.d(DUI_TAG, "getCache() end, objectId = " + objectId + ", methodName = " + methodName + ", methodSig = " + methodSig);
		return;
	}
	public void dispatchInput(View view, InputEvent event, boolean isMotionEvent) { 
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "dispatchInput(), view = " + view
					+ ", event = " + event
					+ ", isMotionEvent = " + isMotionEvent);
		}
		if (view == null)
			return;

		if (mIsHostDevice) {
			event.mIsFromHost = true;
			if (isMotionEvent) {
				MotionEvent me = (MotionEvent)event;
				float transformedX = me.getX() * (float)view.getWidth();
				event.mScale = view.setScaleForFLUID(transformedX);
				sendInput(view.zObjectId, event, isMotionEvent);
			}
			event.mIsFromHost = false;
		}
		
		if (isMotionEvent) {
			MotionEvent me = (MotionEvent)event;
			float transformedX = me.getX() * (float)view.getWidth();
			float transformedY = me.getY() * (float)view.getHeight();
			me.setLocation(transformedX, transformedY);
			view.dispatchTouchEvent(me);
		}
		else
			view.dispatchKeyEvent((KeyEvent)event);
	}

	public void dispatchIMEInput(View view, Message msg, 
			CharSequence text, String str, Bundle bundle) { 
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "dispatchIMEInput(), view = " + view
					+ ", msg = " + msg
					+ ", text = " + text
					+ ", str = " + str
					+ ", bundle = " + bundle);
		}
		if (view != null)
			view.dispatchIMEInput(msg, text, str, bundle);
	}

	private static int nextIdx(char[] argSig, int idx) {
		if (argSig[idx] == 'L') {
			while (argSig[idx] != ';')
				idx++;
		}
		else if (argSig[idx] == '[') {
			idx++;
			if (argSig[idx] == 'L') {
				while (argSig[idx] != ';')
					idx++;
			}
		}
		idx++;
		return idx;
	}

    public static String getSignature(Method method) throws Exception {
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

	public Kryo getKryo() {
		return mKryo;
	}

	public void registerApks(Context context) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "registerApks()");
		mClassLoaders = new HashMap<String, DexClassLoader>();
		mResourceMap = new HashMap<String, Resources>();

		try {
			File rootDir = context.getDir("fluid_apk", 0);
			Log.d(DUI_TAG, "rootDir = " + rootDir.getAbsolutePath() + ", " + rootDir.list() + ", " + rootDir.canRead());
			String[] subDirs = rootDir.list();
			
			for (int i = 0; i < subDirs.length; i++) {
				Log.d(DUI_TAG, "subDirs = " + subDirs[i]);
				File apkFile = new File(rootDir, subDirs[i] + "/base.apk");
				Log.d(DUI_TAG, "apkFile = " + apkFile.getAbsolutePath());
				File optimizedDexOutputPath = context.getDir("outdex", 0);
				Log.d(DUI_TAG, "optimizedDexOutputPath = " + optimizedDexOutputPath.getAbsolutePath());
				DexClassLoader dexLoader = new DexClassLoader(apkFile.getAbsolutePath(), 
						optimizedDexOutputPath.getAbsolutePath(), null, context.getClassLoader());
				mClassLoaders.put(subDirs[i], dexLoader);

				Resources res = ResourcesManager.getInstance().getResources(null,
						apkFile.getAbsolutePath(), null, null, null, 0, null,
						CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, dexLoader);
				mResourceMap.put(subDirs[i], res);
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

	public DexClassLoader getClassLoader(String packageName) {
		return mClassLoaders.get(packageName);
	}

	public Resources getResources(String packageName) {
		return mResourceMap.get(packageName);
	}

	public void registerAppBinders(IBinder appThread, IBinder token) { 
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "registerAppBinders(), appThread = " + appThread + ", token = " + token);
		try {
			mService.registerAppBinders(appThread, token);
		} catch (RemoteException e) {}
	}
	
	public int sendUi(String packageName, byte[] viewData, String[] className) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "sendUi()");
		int ret = -1; 
		try {
			ret = mService.sendUi(packageName, viewData, className);
		} catch (RemoteException e) {}
		return ret;
	}

	public Parcel sendRpc(int objectId, String methodName, 
			String methodSig, String[] types, byte[] args) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "sendRpc()");
		Parcel reply = null; 
		try {
			reply = mService.sendRpc(objectId, methodName, methodSig, types, args);
		} catch (RemoteException e) {}
		return reply;
	}

	public int sendInput(int id, InputEvent event, boolean isMotionEvent) {
		/* temporary code block: start */
		// LOG 1, 2
		{
			long time = System.nanoTime();
			Log.d("EXPRI", "LOG 1, 2: " + time);
			//try {
			//	File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			//	File f = new File(path, "dist_time"); 
			//	FileWriter write = new FileWriter(f, true);
			//	PrintWriter out = new PrintWriter(write);
			//	out.print(time);
			//	out.close();
			//} catch (Exception e) {
			//	e.printStackTrace();
			//}
		}
		/* temporary code block: end */
		if (DUI_DEBUG) Log.d(DUI_TAG, "sendInput(), id = " + id);
		int ret = -1; 
		try {
			mService.sendInput(id, event, isMotionEvent);
		} catch (RemoteException e) {}
		return ret;
	}

	public int sendIMEInput(int id, Message msg) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "sendIMEInput(), id = " + id);
		int ret = -1; 
		try {
			mService.sendIMEInput(id, msg);
		} catch (RemoteException e) {}
		return ret;
	}

	public int sendCache(int objectId, byte[] cacheData) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "sendCache()");
		int ret = -1; 
		try {
			ret = mService.sendCache(objectId, cacheData);
		} catch (RemoteException e) {}
		return ret;
	}

	public void onActivityLaunched(IBinder token) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "onActivityLaunched()");
		try {
			mService.onActivityLaunched(token);
		} catch (RemoteException e) {}
	}

	public boolean isHostDevice() {
		boolean res = false;
		try {
			res = mService.isHostDevice();
		} catch (RemoteException e) {}
		return res;
	}

	public void notifyAppCrash(IBinder appThread) {
		try {
			mService.notifyAppCrash(appThread);
		} catch (RemoteException e) {}
	}

	public byte[][] requestCache(int objectId, String methodName, String methodSig) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "requestCache()");
		byte[][] ret = null;
		try {
			ret = mService.requestCache(objectId, methodName, methodSig);
		} catch (RemoteException e) {}
		return ret;
	}

	public static boolean isMigrated(Object obj) {
		return (obj.zFLUIDFlags & MIGRATED) != 0;
	}

	public static boolean isInRemote(Object obj) {
		return (obj.zFLUIDFlags & IN_REMOTE) != 0;
	}

    public void executeCacheWithVoid(int objectId, String methodName, String methodSig) {
		executeCache(objectId, methodName, methodSig);
	}

    public boolean executeCacheWithBool(int objectId, String methodName, String methodSig) {
		Boolean res = (Boolean)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.booleanValue() : false;
	}

    public char executeCacheWithChar(int objectId, String methodName, String methodSig) {
		Character res = (Character)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.charValue() : 0;
	}

    public byte executeCacheWithByte(int objectId, String methodName, String methodSig) {
		Byte res = (Byte)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.byteValue() : 0;
	}

    public short executeCacheWithShort(int objectId, String methodName, String methodSig) {
		Short res = (Short)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.shortValue() : 0;
	}

    public int executeCacheWithInt(int objectId, String methodName, String methodSig) {
		Integer res = (Integer)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.intValue() : 0;
	}

    public long executeCacheWithLong(int objectId, String methodName, String methodSig) {
		Long res = (Long)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.longValue() : 0;
	}

    public float executeCacheWithFloat(int objectId, String methodName, String methodSig) {
		Float res = (Float)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.floatValue() : (float)0.0;
	}

    public double executeCacheWithDouble(int objectId, String methodName, String methodSig) {
		Double res = (Double)executeCache(objectId, methodName, methodSig);
		return (res != null)? res.doubleValue() : 0.0;
	}

    public Object executeCacheWithObject(int objectId, String methodName, String methodSig) {
		Object res = executeCache(objectId, methodName, methodSig);
		return (res != null)? res : null;
	}
}
