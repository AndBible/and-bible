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
    alias(libs.plugins.kover)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.accrescent.bundletool) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // Pre-staged for Spec 2 (KMP foundations) — declared apply-false, not yet used:
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.kotlinMultiplatformLibrary) apply false
}

// A gate must observe this run, not another checkout's cached test results.
// AbstractTestTask also covers Kotlin/Native tests, not only JVM Test tasks.
allprojects {
    tasks.withType<AbstractTestTask>().configureEach {
        outputs.doNotCacheIf("Tests must execute on every requested gate") { true }
        outputs.upToDateWhen { false }
    }
    tasks.matching {
        it.name.matches(Regex("(verify|record|compare).*Roborazzi.*", RegexOption.IGNORE_CASE))
    }.configureEach {
        outputs.doNotCacheIf("Screenshot gates must execute, not restore results") { true }
        outputs.upToDateWhen { false }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile)
}

// Only the JVM report variant is used; the total variant would include Android.
if (providers.gradleProperty("coverage").isPresent) {
    dependencies {
        kover(project(":sharedCore"))
        kover(project(":sharedData"))
        kover(project(":sharedLogic"))
    }
    kover {
        currentProject {
            createVariant("shared") {}
        }
    }
} else {
    kover { disable() }
}
