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
-keep class com.find.gang.app.*
# 保留 DataBinding 相关的核心类和 Mapper 类
-keep class androidx.databinding.** { *; }
-keep class * extends androidx.databinding.DataBinderMapper { *; }
-dontwarn androidx.databinding.**

# 保留你项目中特定模块的 DataBinding 生成的类，将 com.example.yourapp 替换为你的包名
-keep class com.find.gang.app.databinding.** { *; }
-keep class com.find.gang.app.DataBinderMapperImpl { *; }