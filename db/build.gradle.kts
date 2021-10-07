plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
}


android {
    compileSdkVersion(30)

    /** these config values override those in AndroidManifest.xml.  Can also set versionCode and versionName */
    defaultConfig {
        minSdkVersion(19)
        targetSdkVersion(30)
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
    val commons_text_version: String by rootProject.extra
    val jdom_version: String by rootProject.extra
    val jsword_version: String by rootProject.extra
    val kotlinx_serialization_version: String by rootProject.extra
    val room_version: String by rootProject.extra

    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    implementation("com.github.AndBible:jsword:$jsword_version")
    implementation("org.jdom:jdom2:$jdom_version")
    implementation("org.apache.commons:commons-text:$commons_text_version")

    kapt("androidx.room:room-compiler:$room_version")
}

repositories {
	mavenCentral()
}
