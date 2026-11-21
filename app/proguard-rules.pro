# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk-linux/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Obfuscation is on (#4160; Play Console requires >= 25 % obfuscated DEX code). Scope, measurement and the keep
# rules it needs: docs/r8-obfuscation.md. Crash stack traces need the build's mapping.txt to be readable.

# JSword (org.crosswire.**, in-tree Kotlin) still derives names from classes (MsgBase resource bundles named after
# this::class.simpleName, ResourceUtil/CallContext lookups, Convert/ReflectionUtil): keep all its names.
# Members may still be shrunk away when unused.
-keepnames class org.crosswire.** { *; }

# Add any project specific keep options here:


-dontwarn java.lang.management.ManagementFactory

# do not use JDOM2 jaxen xpath facility
-dontwarn org.jaxen.**

# commons compress does not need classes for other platforms
-dontwarn org.tukaani.xz.**

-keep class org.apache.commons.compress.archivers.zip.** { *; }

# hopefully these JDOm dependencies aren't used because I don't think Android provides them
-dontwarn javax.xml.stream.**

-dontwarn javax.swing.text.**

# slf4j has a lot of potential dependencies, not all of which are required
-dontwarn org.slf4j.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class net.bible.android.view.activity.page.BibleJavascriptInterface {
   public *;
}



# Keep all the GreenRobot event handling onEvent functions
-keepclassmembers class ** {
    public void onEvent*(**);
}


-keep class net.bible.android.view.activity.settings.ColorSettingsFragment

# Prevent R8 class merging for core JSword classes
-keep class org.crosswire.jsword.book.Books { *; }
-keep class org.crosswire.jsword.book.BookCategory { *; }
-keep class org.crosswire.jsword.index.** { *; }
-keep class org.crosswire.common.util.** { *; }

# keep dynamically loaded Jsword classes (loaded via .plugin files using reflection)
-keep class org.crosswire.jsword.book.install.sword.HttpsSwordInstallerFactory { *; }
-keep class org.crosswire.jsword.book.install.sword.HttpSwordInstallerFactory { *; }
-keep class org.crosswire.jsword.book.sword.SwordBookDriver { *; }
-keep class org.crosswire.jsword.book.filter.** { *; }
-keep class org.crosswire.jsword.book.basic.DefaultBookmark { *; }
-keep class org.crosswire.common.config.** { *; }



-dontwarn javax.management.MBeanServerConnection
-dontwarn javax.management.MBeanServer

#We need these in order to support Kotlin reflection (used at least in SpeakWidgets.kt)
#-keepattributes *Annotation*
#-keep class kotlin.** { *; }
#-keep class org.jetbrains.** { *; }

# Kotlinx serialization rules

-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class net.bible.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class net.bible.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class net.bible.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations


-keep class net.bible.android.database.bookmarks.VerseRangeSerializer { *; }

# Google Drive: AndroidCloudAdapterFactory loads the provider by name (googledrive/ is absent from F-Droid builds).
-keep class net.bible.service.cloudsync.googledrive.GoogleDriveAdapterProvider { <init>(); }

-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable
#,RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn java.lang.invoke.StringConcatFactory

-keep class org.jaxen.saxpath.base.XPathReader { *; }

# Nextcloud
-keep,allowshrinking class com.owncloud.android.** { *; }
-keep,allowshrinking class org.apache.jackrabbit.webdav.** { *; }
-keep,allowshrinking class org.apache.commons.codec.** { *; }
-keep,allowshrinking class org.apache.commons.logging.** { *; }
-keep class org.apache.commons.httpclient.** { *; }
-keep,allowshrinking class org.parceler.** { *; }
-keep,allowshrinking class org.slf4j.** { *; }

#ignore nextcloud related warnings
-dontwarn com.owncloud.android.lib.**
-dontwarn org.apache.jackrabbit.webdav.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.slf4j.**

-dontskipnonpubliclibraryclasses

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}




-keepattributes InnerClasses
#end nextcloud

# SnakeYAML introspector requires java.beans classes
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Classes that libraries create by name with a no-argument constructor (#4175). R8 full mode (the AGP 8 default)
# keeps only the class, not its default constructor, for a member-less `-keep class * extends Base` rule, and such
# old consumer rules are still around: Glance 1.1.1 pulls in WorkManager 2.7.1, which brings Room 2.2.5
# (`-keep class * extends androidx.room.RoomDatabase`) and its own `-keep class * extends androidx.work.InputMerger`.
# Without the constructor `WorkDatabase_Impl` cannot be created and the app crash-loops at startup (6.0.1279), and
# WorkManager cannot run a OneTimeWorkRequest (Glance widget sessions). Glance's own
# `-keep public class * extends androidx.glance.appwidget.action.ActionCallback` has the same gap: every widget button
# (actionRunCallback<T>()) fails with NoSuchMethodException. scripts/check-r8-reflective-constructors.py checks every
# minified build for this (task checkR8ReflectiveConstructors<Variant>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Glance stores which widget ids belong to a widget under the GlanceAppWidget class name
# (GlanceAppWidgetManager: "provider:" + Class.getCanonicalName()) in a DataStore that survives an app update.
# R8 renames the class differently in every build, so after an update updateAll() found no widget ids and the old
# widgets showed "Can't show content". Keep the names stable (#4175).
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# Koin keys definitions by KClass. R8 vertical class merging folds a single-implementer interface into its
# implementation, so an interface key and its implementation (or two interfaces of one class) become the same
# key and an alias definition like `single<Port> { get<Impl>() }` resolves itself (StackOverflowError at startup).
-keep,allowshrinking,allowobfuscation interface net.bible.**
