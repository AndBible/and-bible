# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk-linux/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# used during initial config debug
-dontobfuscate

# Add any project specific keep options here:

# do not use mmseg4j SOLR integration
-dontwarn org.apache.solr.**
-dontwarn com.chenlb.mmseg4j.solr.*

# we use mmseg4j instead of jsword's default SmartChineseAnalyzer
-dontwarn org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer

# do not use JDOM2 jaxen xpath facility
-dontwarn org.jaxen.**

# commons compress does not need classes for other platforms
-dontwarn org.tukaani.xz.**

# hopefully these JDOm dependencies aren't used because I don't think Android provides them
-dontwarn javax.xml.stream.**

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

# keep dynamically loaded Jsword classes
-keep class org.crosswire.jsword.book.install.sword.AndBibleHttpSwordInstallerFactory
-keep class org.crosswire.jsword.index.lucene.analysis.**
-keep class org.crosswire.jsword.book.sword.SwordBookDriver
-keep class org.crosswire.jsword.index.lucene.LuceneIndexManager
-keep class org.crosswire.jsword.index.lucene.LuceneQueryBuilder
-keep class org.crosswire.jsword.index.lucene.LuceneQueryDecorator
-keep class org.crosswire.jsword.index.lucene.LuceneSearcher
-keep class org.crosswire.jsword.book.filter.**

# This class has a number of dynamic invocation so let's not
# touch it
# DO WE NEED THESE 2 LINES
-keep class org.apache.lucene.util.Attribute* { *; }
-keep class org.apache.lucene.analysis.tokenattributes.TermAttribute
# Lucene classes
-keep class org.apache.lucene.codecs.Codec
-keep class * extends org.apache.lucene.codecs.Codec
-keep class org.apache.lucene.codecs.PostingsFormat
-keep class * extends org.apache.lucene.codecs.PostingsFormat
-keep class org.apache.lucene.codecs.DocValuesFormat
-keep class * extends org.apache.lucene.codecs.DocValuesFormat
-keep class org.apache.lucene.analysis.tokenattributes.**
-keep class org.apache.lucene.**Attribute
-keep class * implements org.apache.lucene.**Attribute
# required for non-English searches
-keep class * extends org.tartarus.snowball.SnowballProgram
