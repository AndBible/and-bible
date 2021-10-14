plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
}

android {
    compileSdk = 30

    /** these config values override those in AndroidManifest.xml.  Can also set versionCode and versionName */
    defaultConfig {
        minSdk =19
        targetSdk = 30
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas".toString())
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val commonsTextVersion: String by rootProject.extra
    val jdomVersion: String by rootProject.extra
    val jswordVersion: String by rootProject.extra
    val kotlinxSerializationVersion: String by rootProject.extra
    val roomVersion: String by rootProject.extra

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("com.github.AndBible:jsword:$jswordVersion")
    implementation("org.jdom:jdom2:$jdomVersion")
    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    kapt("androidx.room:room-compiler:$roomVersion")
}

repositories {
	mavenCentral()
}
