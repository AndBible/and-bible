import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
}

val jsDir = "bibleview-js"

fun getGitHash(): String =
    ByteArrayOutputStream().use { stdout ->
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
        }
        return stdout.toString().trim()
    }

fun getGitDescribe(): String  = ByteArrayOutputStream().use { stdout ->
    exec {
        commandLine("git", "describe", "--always")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}


val npmUpgrade by tasks.registering(Exec::class) {
    inputs.file("$jsDir/package.json")
    outputs.file("$jsDir/node_modules/.bin/npm")
    workingDir = file(jsDir)
    // Workaround for F-droid, which has buggy npm version 5.8, that always fails when installing packages.
    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        commandLine("npx.cmd", "npm@latest", "ci", "--save-dev", "npm@latest")
    }
    else {
        commandLine("npx", "npm@latest", "ci", "--save-dev", "npm@latest")
    }
}

val npmInstall by tasks.registering(Exec::class) {
    dependsOn(npmUpgrade)
    inputs.file("$jsDir/package.json")
    outputs.dir("$jsDir/node_modules")

    workingDir = file(jsDir)
    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        commandLine("$rootDir/app/$jsDir/node_modules/.bin/npm.cmd", "ci")
    }
    else {
        commandLine("node_modules/.bin/npm", "ci")
    }
}

val vueCli by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    inputs.file("$jsDir/package.json")
    inputs.file("$jsDir/vue.config.js")
    inputs.file("$jsDir/babel.config.js")
    inputs.dir("$jsDir/src")
    inputs.dir("$jsDir/public")
    outputs.dir("$jsDir/dist")
    println("Task names "+gradle.startParameter.taskNames)
    val taskNames = gradle.startParameter.taskNames
    println(taskNames)
    val isDebug = taskNames.contains(":app:packageDebug")

    val buildCmd: String = if(!isDebug) {
        println("Building js for production")
        "build-production"
    } else {
        println("Building js for debug")
        "build-debug"
    }
    workingDir = file(jsDir)
    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        commandLine("$rootDir/app/$jsDir/node_modules/.bin/npm.cmd", "run", buildCmd)
    }
    else {
        commandLine("node_modules/.bin/npm", "run", buildCmd)
    }
}

val buildLoaderJs by tasks.registering(Sync::class) {
    dependsOn(vueCli)
    from("$jsDir/dist")
    into("src/main/assets/bibleview-js")
}

val jsTests by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = file(jsDir)
    commandLine("node_modules/.bin/npm", "run", "test:unit")
}

tasks.named("preBuild").configure { dependsOn(buildLoaderJs) }
tasks.named("check").configure { dependsOn(jsTests) }

android {
    compileSdk = 30

    /** these config values override those in AndroidManifest.xml.  Can also set versionCode and versionName */
    defaultConfig {
        applicationId = "net.bible.android.activity"
        minSdk =21
        targetSdk = 30
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GitHash", "\"${getGitHash()}\"")
        buildConfigField("String", "GitDescribe", "\"${getGitDescribe()}\"")
        buildConfigField("String", "BuildDate", "\"${SimpleDateFormat("dd/MM/YY HH:mm:ss").format(Date())}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        debug {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                val props = Properties()
                FileInputStream(propsFile).use { props.load(it) }

                val appSuffix: String? = props["APP_SUFFIX"] as String?
                println("App suffix: $appSuffix")

                if (appSuffix != null) {
                    applicationIdSuffix = appSuffix
                }
            }
//			minifyEnabled true
//			useProguard true
//			proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
//			zipAlignEnabled true
        }
    }

    val dimAppearance = "appearance"

    flavorDimensions(dimAppearance)

    productFlavors {
        create("standard") {
            dimension = dimAppearance
            isDefault = true
        }

        create("discrete") {
            dimension = dimAppearance
        }

        create("fdroid") {
            dimension = dimAppearance
        }

        create("github") {
            dimension = dimAppearance
        }
    }


    lint {
        disable("MissingTranslation")
        disable("ExtraTranslation")
        warning("InvalidPackage")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        // prevent logger errors
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                test ->
                  test.testLogging {
                    events("passed", "skipped", "failed")
                    setExceptionFormat("full")
                }
            }
        }

    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = false
        }
        abi {
            enableSplit = false
        }
    }

    packagingOptions {
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/DEPENDENCIES")
    }

    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

}

dependencies {
    val commonsTextVersion: String by rootProject.extra
    val jdomVersion: String by rootProject.extra
    val jswordVersion: String by rootProject.extra
    val kotlinVersion: String by rootProject.extra
    val kotlinxSerializationVersion: String by rootProject.extra
    val roomVersion: String by rootProject.extra

    implementation(project(":db"))
    // Appcompat:
    // 1.2.0+ releases (until 1.3.0-alpha02 at least) have issue with translations
    // not showing up on MainBibleActivity. Thus reverting to 1.0.2 for now.
    // https://issuetracker.google.com/issues/141132133
    implementation("androidx.appcompat:appcompat:1.3.1")

    implementation("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation("androidx.media:media:1.4.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.1")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.webkit:webkit:1.4.0")

    //implementation("androidx.recyclerview:recyclerview-selection:1.0.0")

    //implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.AndBible:ColorPicker:ab-fix-1")

    implementation("com.google.android.material:material:1.4.0")

    // allow annotations like UIThread, StringRes see: https://developer.android.com/reference/android/support/annotation/package-summary.html
    implementation("androidx.annotation:annotation:1.2.0")

    implementation("androidx.room:room-runtime:$roomVersion")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")

    implementation("com.madgag.spongycastle:core:1.58.0.0")
    //implementation("com.madgag.spongycastle:prov:1.58.0.0")
    //implementation("com.madgag.spongycastle:pkix:1.58.0.0")
    //implementation("com.madgag.spongycastle:pg:1.58.0.0")

    implementation("com.google.dagger:dagger:2.38.1")
    annotationProcessor("com.google.dagger:dagger-compiler:2.38.1")
    kapt("com.google.dagger:dagger-compiler:2.38.1")

    implementation("de.greenrobot:eventbus:2.4.1")

    implementation("org.apache.commons:commons-lang3:3.12.0") // make sure this is the same version that commons-text depends on
    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.github.AndBible:jsword:$jswordVersion")

    implementation("org.jdom:jdom2:$jdomVersion")

    debugImplementation("com.facebook.stetho:stetho:1.6.0")

    testImplementation(project(":db"))

    // TESTS
    //testImplementation("com.github.AndBible:robolectric:4.3.1-andbible3")
    testImplementation("org.robolectric:robolectric:4.6.1")
    //testImplementation("org.robolectric:shadows-multidex:4.3.1")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("junit:junit:4.13.2")

    // Android UI testing

    // Core library
    androidTestImplementation("androidx.test:core:1.4.0")

    // AndroidJUnitRunner and JUnit Rules
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")

    // Assertions
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.ext:truth:1.4.0")
    androidTestImplementation("com.google.truth:truth:1.1.3")

    // Espresso dependencies
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.4.0")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.4.0")

    // The following Espresso dependency can be either "implementation"
    // or "androidTestImplementation", depending on whether you want the
    // dependency to appear on your APK's compile classpath or the test APK
    // classpath.
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.4.0")
}

