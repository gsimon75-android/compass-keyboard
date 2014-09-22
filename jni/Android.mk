LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE            := symlink
LOCAL_SRC_FILES         := symlink.c
#LOCAL_C_INCLUDES        := include
#LOCAL_STATIC_LIBRARIES  := something
#LOCAL_LDLIBS            := -L$(SYSROOT)/usr/lib -llog
include $(BUILD_SHARED_LIBRARY)

# http://stackoverflow.com/questions/4765465/android-ndk-two-static-libraries-and-linking
# http://stackoverflow.com/questions/4563928/linking-thirdparty-libs-libs-a-with-ndk

