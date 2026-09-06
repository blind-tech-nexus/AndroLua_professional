APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_PLATFORM := android-26
APP_STL := c++_static
APP_CPPFLAGS := -std=c++17 -frtti -fexceptions
APP_CFLAGS += -Wno-error=deprecated-declarations -Wno-error=implicit-function-declaration -Wno-error=incompatible-pointer-types -Wno-error=implicit-int
APP_CPPFLAGS += -Wno-error=deprecated-declarations
