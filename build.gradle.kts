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

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // KSP version must match AGP 9.0's built-in Kotlin version (2.2.10)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    id("app.accrescent.tools.bundletool") version "0.2.4" apply false
}

buildscript {
    val kotlinVersion by extra("2.2.10") // Must match AGP 9.0's built-in Kotlin version
    val coroutinesVersion by extra("1.10.2")
    val roomVersion by extra("2.7.2")
    val jdomVersion by extra("2.0.6.1") // make sure this is same version as in jsword!
    val commonsTextVersion by extra("1.9") // 1.10.0 crashes on Android 5.1
    val kotlinxSerializationVersion by extra("1.8.1")
    val sourceCompatibilityVersion by extra(JavaVersion.VERSION_17)
    val targetCompatibilityVersion by extra(JavaVersion.VERSION_17)
    val jvmTargetVersion by extra("17")
    val jvmToolChainVersion by extra(17)
    val coreKtxVersion by extra("1.16.0")
    val sqliteAndroidVersion by extra("3.49.0")


    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.0.0")
        // kotlin-gradle-plugin not needed - AGP 9.0 has built-in Kotlin support
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile)
}
