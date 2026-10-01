# EventBus-generated implementations are loaded via Class.forName
# in EventBus.java:84, so they must not be renamed or removed.
-keep class ru.ytkab0bp.eventbus.impl.** { *; }
-keep class * implements ru.ytkab0bp.eventbus.EventBusListenerImpl { *; }

# Klippy/Moonraker per-slot services are instantiated via Class.forName
# in KlipperInstance.kt (KlippyService_<slot> / MoonrakerService_<slot>).
-keep class ru.ytkab0bp.beamklipper.service.KlippyService_* { *; }
-keep class ru.ytkab0bp.beamklipper.service.MoonrakerService_* { *; }

# Chaquopy runtime classes and the Python bridge.
-keep class com.chaquo.python.** { *; }
-keep class ru.ytkab0bp.beamklipper.KlipperApp { *; }

# SerialProxy#onDataReceived is looked up and invoked by exact name from native code
# (app/src/main/jni/serial.cpp: env->GetMethodID(mProxyClass, "onDataReceived", "([B)V"),
# called from the pty worker thread on whatever implements it, e.g. NativeSerialPort).
# It is a plain (non-native) interface method, so the default
# "-keepclasseswithmembernames class * { native <methods>; }" rule in
# proguard-android-optimize.txt does NOT protect it: R8 renames it on any minified
# build, and the very first byte read from an attached USB-serial device then crashes
# with NoSuchMethodError (observed on release builds, e.g. Poco F7 / Android 16, sdk=36).
-keep interface ru.ytkab0bp.beamklipper.serial.SerialProxy { *; }

# SerialNative's native methods are already preserved by the native-method rule above,
# but its class name is also referenced by the static JNI binding
# (Java_ru_ytkab0bp_beamklipper_serial_SerialNative_*), so keep it explicit.
-keep class ru.ytkab0bp.beamklipper.serial.SerialNative { *; }
