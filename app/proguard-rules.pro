# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============ Glide ============
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ============ MediaStore / ContentProvider ============
-keep class android.provider.MediaStore { *; }
-keep class android.provider.MediaStore$Audio { *; }
-keep class android.provider.MediaStore$Audio$Media { *; }

# ============ Custom Views ============
-keep class com.codetrio.spatialflow.ui.custom.** { *; }

# ============ Palette ============
-keep class androidx.palette.graphics.** { *; }

# ============ Material Components ============
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============ Navigation ============
-keep class androidx.navigation.** { *; }

# ============ Lifecycle ============
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }