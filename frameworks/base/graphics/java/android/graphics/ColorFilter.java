/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.graphics;

import libcore.util.NativeAllocationRegistry;

/**
 * A color filter can be used with a {@link Paint} to modify the color of
 * each pixel drawn with that paint. This is an abstract class that should
 * never be used directly.
 */
public class ColorFilter {
	/* mobiledui: start */
    private static final String DUI_TAG = "MOBILEDUI(ColorFilter)";
    private static final boolean DUI_DEBUG = false;

	/** @hide */
	public void unflattenForFLUID() {
		if (DUI_DEBUG)
			android.util.Log.d(DUI_TAG, "unflattenForFLUID()");
		mNativeInstance = 0;
		getNativeInstance();
	}
	/* mobiledui: end */

    private static class NoImagePreloadHolder {
        public static final NativeAllocationRegistry sRegistry = new NativeAllocationRegistry(
                ColorFilter.class.getClassLoader(), nativeGetFinalizer(), 50);
    }

    /**
     * @deprecated Use subclass constructors directly instead.
     */
    @Deprecated
    public ColorFilter() {}

    /**
     * Current native SkColorFilter instance.
     */
    private long mNativeInstance;
    // Runnable to do immediate destruction
    //private Runnable mCleaner;
	/* mobiledui: start */
    transient private Runnable mCleaner;
	/* mobiledui: end */

    long createNativeInstance() {
        return 0;
    }

    void discardNativeInstance() {
        if (mNativeInstance != 0) {
            mCleaner.run();
            mCleaner = null;
            mNativeInstance = 0;
        }
    }

    /** @hide */
    public long getNativeInstance() {
        if (mNativeInstance == 0) {
            mNativeInstance = createNativeInstance();

            if (mNativeInstance != 0) {
                // Note: we must check for null here, since it's possible for createNativeInstance()
                // to return nullptr if the native SkColorFilter would be a no-op at draw time.
                // See native implementations of subclass create methods for more info.
                mCleaner = NoImagePreloadHolder.sRegistry.registerNativeAllocation(
                        this, mNativeInstance);
            }
        }
        return mNativeInstance;

    }

    private static native long nativeGetFinalizer();
}
