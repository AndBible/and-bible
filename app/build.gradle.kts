/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("app.accrescent.tools.bundletool")
}

val jsDir = "bibleview-js"

// Release signing secrets live ONLY inside keystore.properties.gpg, which is committed
// (encrypted to the developer's GPG key). We decrypt inline into an in-memory [Properties]
// object; the plaintext never touches disk. Decryption is skipped unless a task that
// actually needs release signing has been requested — this avoids spurious YubiKey prompts
// on routine debug builds. F-Droid builds are also skipped because the F-Droid server
// signs its own releases.
val keystoreGpgFile = rootProject.file("keystore.properties.gpg")

val wantsReleaseSigning: Boolean = gradle.startParameter.taskNames.any { name ->
    val n = name.lowercase()
    n.contains("release") && !n.contains("unittest") && !n.contains("fdroid")
}

val keystoreProperties: Properties = Properties().apply {
    if (wantsReleaseSigning && keystoreGpgFile.exists()) {
        val process = ProcessBuilder(
            "gpg", "--decrypt", "--quiet", keystoreGpgFile.absolutePath
        ).redirectErrorStream(false).start()
        val decrypted = process.inputStream.readBytes()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            load(ByteArrayInputStream(decrypted))
        } else {
            // GPG failure is expected on CI (no secret key available). Leave
            // [keystoreProperties] empty so signingConfig stays unset and the
            // build produces *-unsigned.apk to be signed externally (apksigner
            // with GitHub Secrets in build-apk.yml).
            val stderr = process.errorStream.bufferedReader().readText()
            logger.warn("gpg --decrypt of keystore.properties.gpg failed (exit $exitCode): $stderr")
            logger.warn("Release signing will be skipped; APKs will be produced unsigned.")
        }
    }
}

fun expandUserHome(path: String): String =
    if (path.startsWith("~/")) System.getProperty("user.home") + path.substring(1) else path

// The flavor dimension for the appearance of the app
val dimAppearanceName = "appearance"
val discreteFlavorName = "discrete"
// This is the "standard" applicationId.
// This value must remain the same as it has been since the original
// release in 2010 for continuity of updates for existing users.
val applicationIdStandard = "net.bible.android.activity"
// An alternative applicationId, to be used for the "discrete" flavor.
val applicationIdDiscrete = "com.app.calculator"
// An alternative applicationId, to be used for the "accrescent" flavor.
val applicationIdAccrescent = "org.andbible.andbible"

// The flavor dimension for the app's distribution channel
val dimDistributionChannelName = "distchannel"


fun getGitHash(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()

fun getGitDescribe(): String =
    providers.exec {
        commandLine("git", "describe", "--always")
    }.standardOutput.asText.get().trim()

fun getGitCommitDate(): String =
    providers.exec {
        commandLine("git", "log", "-1", "--format=%ad", "--date=format:%d/%m/%y %H:%M:%S")
    }.standardOutput.asText.get().trim()

val npmVersion = "11"
val npmUpgrade by tasks.registering(Exec::class) {
    inputs.file("$jsDir/package.json")
    outputs.file("$jsDir/node_modules/.bin/npm")
    workingDir = file(jsDir)
    // Workaround for F-droid, which has buggy npm version 5.8, that always fails when installing packages.
    if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")) {
        commandLine("npx.cmd", "npm@${npmVersion}", "ci", "--save-dev", "npm@${npmVersion}")
    }
    else {
        commandLine("npx", "npm@${npmVersion}", "ci", "--save-dev", "npm@${npmVersion}")
    }
}

val npmInstall by tasks.registering(Exec::class) {
    dependsOn(npmUpgrade)
    inputs.file("$jsDir/package.json")
    outputs.dir("$jsDir/node_modules")

    workingDir = file(jsDir)
    if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")) {
        commandLine("$rootDir/app/$jsDir/node_modules/.bin/npm.cmd", "ci")
    }
    else {
        commandLine("node_modules/.bin/npm", "ci")
    }
}

val jsBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    inputs.file("$jsDir/package.json")
    inputs.file("$jsDir/vite.config.mts")
    inputs.file("$jsDir/index.html")
    inputs.file("$jsDir/tsconfig.json")
    inputs.dir("$jsDir/src")
    outputs.dir("$jsDir/dist")
    println("Task names "+gradle.startParameter.taskNames)
    val taskNames = gradle.startParameter.taskNames
    println(taskNames)
    val isDebug = taskNames.any { it.endsWith("Debug") }

    val buildCmd: String = if(!isDebug) {
        println("Building js for production")
        "build-production"
    } else {
        println("Building js for debug")
        "build-debug"
    }
    workingDir = file(jsDir)
    if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")) {
        commandLine("$rootDir/app/$jsDir/node_modules/.bin/npm.cmd", "run", buildCmd)
    }
    else {
        commandLine("node_modules/.bin/npm", "run", buildCmd)
    }
}

val buildLoaderJs by tasks.registering(Sync::class) {
    dependsOn(jsBuild)
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
    compileSdk = 36

    /** these config values override those in AndroidManifest.xml.  Can also set versionCode and versionName */
    defaultConfig {
        applicationId = applicationIdStandard
        minSdk = 23
        targetSdk = 36
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GitHash", "\"${getGitHash()}\"")
        buildConfigField("String", "GitDescribe", "\"${getGitDescribe()}\"")
        buildConfigField("String", "CommitDate", "\"${getGitCommitDate()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "org.andbible.tests"
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = file(expandUserHome(storeFilePath))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                val props = Properties()
                FileInputStream(propsFile).use { props.load(it) }

                val appSuffix: String? = props["PROD_APP_SUFFIX"] as String?
                println("Prod app suffix: $appSuffix")

                if (appSuffix != null) {
                    applicationIdSuffix = appSuffix
                }
            }
        }
        debug {
            // Debug builds default to a ".debug" applicationId suffix so they can be
            // installed alongside the production app. local.properties is gitignored, so
            // relying on APP_SUFFIX there is not portable; APP_SUFFIX (when present) still
            // overrides this default for setups that need a different suffix.
            var appSuffix = ".debug"
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                val props = Properties()
                FileInputStream(propsFile).use { props.load(it) }

                (props["APP_SUFFIX"] as String?)?.let { appSuffix = it }
            }
            println("App suffix: $appSuffix")
            applicationIdSuffix = appSuffix
//			minifyEnabled true
//			useProguard true
//			proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
//			zipAlignEnabled true
        }
    }

    flavorDimensions += listOf(dimAppearanceName, dimDistributionChannelName)

    productFlavors {
        create("standard") {
            dimension = dimAppearanceName
            isDefault = true
        }

        create(discreteFlavorName) {
            dimension = dimAppearanceName
        }

        create("googleplay") {
            dimension = dimDistributionChannelName
            isDefault = true
        }

        create("fdroid") {
            dimension = dimDistributionChannelName
        }

        create("samsung") {
            dimension = dimDistributionChannelName
        }

        create("huawei") {
            dimension = dimDistributionChannelName
        }

        create("amazon") {
            dimension = dimDistributionChannelName
        }

        create("github") {
            dimension = dimDistributionChannelName
            minSdk = 21
        }

        create("accrescent") {
            dimension = dimDistributionChannelName
        }
    }

    lint {
        disable +="MissingTranslation"
        disable += "ExtraTranslation"
        disable +="InvalidPackage"
    }

    compileOptions {
        val sourceCompatibilityVersion: JavaVersion by rootProject.extra
        val targetCompatibilityVersion: JavaVersion by rootProject.extra

        sourceCompatibility = sourceCompatibilityVersion
        targetCompatibility = targetCompatibilityVersion
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
        managedDevices {
            localDevices {
                create("emulator") {
                    device = "Pixel 3"
                    apiLevel = 31
                    systemImageSource = "aosp"
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

    packaging {
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    namespace = "net.bible.android.activity"
}

val jvmToolChainVersion: Int by rootProject.extra

kotlin {
    jvmToolchain(jvmToolChainVersion)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

if(gradle.startParameter.taskNames.any { it.contains("Fdroid") }) {
    println("Fdroid build: excluding Google Drive stuff")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        println("Excluding ${name}")
        exclude("**/googledrive/*")
    }
}

androidComponents {
    val discreteSelector = selector().withFlavor(dimAppearanceName to discreteFlavorName )
    // Set the applicationId to a more discrete alternative.
    // Replace only the "standard" prefix, in order to preserve any
    // suffixes that are contributed by the build types or product flavors.
    onVariants(discreteSelector) { variant ->
        val originalAppId = variant.applicationId.get()
        val alternateAppId = originalAppId.replace(applicationIdStandard, applicationIdDiscrete)
        variant.applicationId.set(alternateAppId)
        println("Reconfigured variant ${variant.name} with applicationId '${alternateAppId}' (was ${originalAppId})")
    }
    val accrescentSelector = selector().withFlavor(dimDistributionChannelName to "accrescent")
    // Set the applicationId for Accrescent variant.
    // Replace only the "standard" prefix, in order to preserve any
    // suffixes that are contributed by the build types or product flavors.
    onVariants(accrescentSelector) { variant ->
        val originalAppId = variant.applicationId.get()
        val alternateAppId = originalAppId.replace(applicationIdStandard, applicationIdAccrescent)
        variant.applicationId.set(alternateAppId)
        println("Reconfigured variant ${variant.name} with applicationId '${alternateAppId}' (was ${originalAppId})")
    }
    beforeVariants(selector()
        .withFlavor(dimAppearanceName to "discrete")
    ) { variant ->
        for((dimension, value) in variant.productFlavors) {
            if(dimension == dimDistributionChannelName && !listOf("github").contains(value)) {
                variant.enable = false
            }
        }
    }
}


dependencies {
    val commonsTextVersion: String by rootProject.extra
    val jdomVersion: String by rootProject.extra
    val kotlinVersion: String by rootProject.extra
    val coroutinesVersion: String by rootProject.extra
    val kotlinxSerializationVersion: String by rootProject.extra
    val roomVersion: String by rootProject.extra
    val coreKtxVersion: String by rootProject.extra
    val sqliteAndroidVersion: String by rootProject.extra

    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("net.objecthunter:exp4j:0.4.8")
    implementation("com.github.requery:sqlite-android:$sqliteAndroidVersion")
    implementation("org.yaml:snakeyaml:2.2")

    for(variantImplementation in listOf("googleplay", "github", "amazon", "samsung", "huawei", "accrescent").map { "${it}Implementation" }) {
        // Google Drive API
        variantImplementation("com.google.android.gms:play-services-auth:20.7.0")
        variantImplementation("com.google.apis:google-api-services-drive:v3-rev20230212-2.0.0") {
            exclude("org.apache.httpcomponents")
            exclude("com.google.guava.guava")
        }
        variantImplementation("com.google.guava:guava:32.0.1-android")
        variantImplementation("com.google.api-client:google-api-client-android:2.2.0") {
            exclude("org.apache.httpcomponents")
        }
    }
    //implementation("androidx.recyclerview:recyclerview-selection:1.0.0")

    //implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.AndBible:ColorPicker:ab-fix-1")

    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.room:room-runtime:$roomVersion")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${coroutinesVersion}")

    implementation("com.madgag.spongycastle:core:1.58.0.0")
    //implementation("com.madgag.spongycastle:prov:1.58.0.0")
    //implementation("com.madgag.spongycastle:pkix:1.58.0.0")
    //implementation("com.madgag.spongycastle:pg:1.58.0.0")

    val daggerVersion = "2.56.2"
    implementation("com.google.dagger:dagger:$daggerVersion")
    annotationProcessor("com.google.dagger:dagger-compiler:$daggerVersion")
    ksp("com.google.dagger:dagger-compiler:$daggerVersion")

    implementation("de.greenrobot:eventbus:2.4.1")

    implementation("org.apache.commons:commons-lang3:3.12.0") // make sure this is the same version that commons-text depends on
    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation(project(":jsword")) {
        exclude("org.apache.httpcomponents")
    }

    implementation("de.psdev.slf4j-android-logger:slf4j-android-logger:1.0.5")

    implementation("org.jdom:jdom2:$jdomVersion")
    implementation("jaxen:jaxen:2.0.0")

    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")

    // Next cloud related dependencies
    implementation("com.github.nextcloud:android-library:2.20.0") {
        exclude(group = "org.ogce", module = "xpp3") // unused in Android and brings wrong Junit version
    }
    implementation("commons-httpclient:commons-httpclient:3.1@jar")  // Make sure this is same version as in NextCloud lib
    implementation("org.apache.jackrabbit:jackrabbit-webdav:2.13.5") // Make sure this is same version as in NextCloud lib


    debugImplementation("com.facebook.stetho:stetho:1.6.0")

    // TESTS
    //testImplementation("com.github.AndBible:robolectric:4.3.1-andbible3")
    testImplementation("org.robolectric:robolectric:4.9")
    //testImplementation("org.robolectric:shadows-multidex:4.3.1")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("junit:junit:4.13.2")

    // Android instrumentation testing

    // Core library
    androidTestImplementation("androidx.test:core:1.5.0")

    // AndroidJUnitRunner and JUnit Rules
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")

    // Assertions
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:truth:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.1.4")

    // Espresso dependencies
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1") {
        // https://github.com/android/android-test/issues/861#issuecomment-1067448610
        exclude(group="org.checkerframework", module="checker")
    }
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1") {
        // https://github.com/android/android-test/issues/861#issuecomment-872582819
        exclude(group="org.checkerframework", module="checker")
    }
    
    androidTestImplementation("androidx.test.espresso:espresso-web:3.5.1")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.5.1")

    // The following Espresso dependency can be either "implementation"
    // or "androidTestImplementation", depending on whether you want the
    // dependency to appear on your APK's compile classpath or the test APK
    // classpath.
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.5.1")
}

// Bundletool configuration for Accrescent APK set building.
// Reads the same credentials as the release signingConfig (keystore.properties.gpg).
bundletool {
    val storeFilePath = keystoreProperties.getProperty("storeFile")
    if (storeFilePath != null) {
        signingConfig {
            storeFile.set(file(expandUserHome(storeFilePath)))
            storePassword.set(keystoreProperties.getProperty("storePassword"))
            keyAlias.set(keystoreProperties.getProperty("keyAlias"))
            keyPassword.set(keystoreProperties.getProperty("keyPassword"))
        }
        println("✓ Accrescent signing configuration loaded from keystore.properties.gpg")
    } else if (wantsReleaseSigning) {
        // No local keystore.properties.gpg — e.g. CI builds that sign externally
        // (GitHub Actions uses apksigner with GitHub Secrets on *-unsigned.apk).
        println("ℹ keystore.properties.gpg not found — release artifacts will be unsigned (expected on CI)")
    }
}

configurations {
    testImplementation {
        exclude(group = "com.github.requery", module = "sqlite-android")
    }
}

