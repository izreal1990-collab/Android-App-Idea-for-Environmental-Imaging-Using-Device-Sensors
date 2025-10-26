# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep SLAM and sensor classes
-keep class com.environmentalimaging.app.slam.** { *; }
-keep class com.environmentalimaging.app.sensors.** { *; }
-keep class com.environmentalimaging.app.data.** { *; }

# Keep Apache Commons Math classes
-keep class org.apache.commons.math3.** { *; }
-dontwarn org.apache.commons.math3.**

# Keep OpenGL related classes
-keep class android.opengl.** { *; }
-keep class javax.microedition.khronos.** { *; }

# Keep Gson classes for JSON serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer