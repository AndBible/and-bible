// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val kotlinVersion by extra("1.6.10")
    val roomVersion by extra("2.4.1")
    val jswordVersion by extra("2.3.63")
    val jdomVersion by extra("2.0.6") // make sure this is same version as in jsword!
    val commonsTextVersion by extra("1.9")
    val kotlinxSerializationVersion by extra("1.3.2")

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.1.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
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
    delete(rootProject.buildDir)
}
