LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)
LOCAL_MODULE := reflectasm-prebuilt
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_SRC_FILES := reflectasm-1.10.1-shaded.jar
LOCAL_JACK_FLAGS := -D jack.import.jar.debug-info=false
LOCAL_UNINSTALLABLE_MODULE := true
include $(BUILD_PREBUILT)
