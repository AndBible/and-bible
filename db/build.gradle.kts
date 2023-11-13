/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34

    /** these config values override those in AndroidManifest.xml.  Can also set versionCode and versionName */
    defaultConfig {
        minSdk = 26
        targetSdk = 34
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
        val sourceCompatibilityVersion: JavaVersion by rootProject.extra
        val targetCompatibilityVersion: JavaVersion by rootProject.extra
        val jvmTargetVersion: String by rootProject.extra

        sourceCompatibility = sourceCompatibilityVersion
        targetCompatibility = targetCompatibilityVersion
        kotlinOptions {
            jvmTarget = jvmTargetVersion
        }
    }

    namespace = "net.bible.android.database"
}

val jvmToolChainVersion: Int by rootProject.extra

kotlin {
    jvmToolchain(jvmToolChainVersion)
}

dependencies {
    val coroutinesVersion: String by rootProject.extra
    val commonsTextVersion: String by rootProject.extra
    val jdomVersion: String by rootProject.extra
    val kotlinxSerializationVersion: String by rootProject.extra
    val roomVersion: String by rootProject.extra
    val coreKtxVersion: String by rootProject.extra
    val sqliteAndroidVersion: String by rootProject.extra

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation(project(":jsword")) {
        exclude("org.apache.httpcomponents")
    }

    implementation("org.jdom:jdom2:$jdomVersion")
    implementation("org.apache.commons:commons-text:$commonsTextVersion")
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("com.github.requery:sqlite-android:$sqliteAndroidVersion")
    testImplementation("junit:junit:4.12")
    testImplementation("org.hamcrest:hamcrest-library:2.2")

    kapt("androidx.room:room-compiler:$roomVersion")
}

repositories {
	mavenCentral()
}
