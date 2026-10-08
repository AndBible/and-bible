/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.android.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Modifier

/**
 * Every Room DAO function must be suspend (Room 3).
 *
 * Room's annotations (`@Dao`, `@Query`, ...) have CLASS retention, so neither Java nor Kotlin reflection
 * can see them; this test reads them from the compiled class files with ASM instead.
 *
 * ReadingPlanDao is already fully suspend and so is not listed.
 * [notYetConverted] is exactly the set of DAOs that still have at least one non-suspend Room function. It
 * shrinks as DAOs convert: BookmarkDao converted in Task 11; SyncDao follows in Task 15 (together with SyncUtilities); [notYetConvertedEntriesStillHaveOffenders] keeps it from going stale.
 */
class DaoSuspendGuardTest {
    private val notYetConverted = setOf(
        "SyncDao", // 17 non-suspend; stays until Task 15 (SyncUtilities calls it inside raw beginTransaction blocks)
    )

    private val roomAnnotations = setOf("Query", "Insert", "Update", "Delete", "Upsert", "Transaction", "RawQuery")
        .map { "Landroidx/room/$it;" }.toSet()

    private val databases = listOf(
        BookmarkDatabase::class.java, WorkspaceDatabase::class.java, ReadingPlanDatabase::class.java,
        TemporaryDatabase::class.java, DocumentSyncDatabase::class.java, RepoDatabase::class.java,
        SettingsDatabase::class.java, AiSettingsDatabase::class.java, EpubDatabase::class.java,
        net.bible.android.database.mydocument.MyDocumentDatabase::class.java,
        net.bible.android.database.progress.ProgressDatabase::class.java,
    )

    private class ClassInfo(
        val name: String, val superName: String?, val interfaces: List<String>,
        val isDao: Boolean, val roomFunctions: List<Pair<String, String>>, // name to descriptor
    )

    private fun read(internalName: String): ClassInfo? {
        val bytes = DaoSuspendGuardTest::class.java.classLoader
            .getResourceAsStream("$internalName.class")?.use { it.readBytes() } ?: return null
        var isDao = false
        val fns = mutableListOf<Pair<String, String>>()
        val reader = ClassReader(bytes)
        var superName: String? = null
        var ifaces = listOf<String>()
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(v: Int, a: Int, n: String, s: String?, sup: String?, i: Array<String>?) {
                superName = sup; ifaces = i?.toList().orEmpty()
            }
            override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                if (desc == "Landroidx/room/Dao;") isDao = true
                return null
            }
            override fun visitMethod(a: Int, n: String, d: String, sig: String?, ex: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                        if (desc in roomAnnotations) fns += n to d
                        return null
                    }
                }
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return ClassInfo(internalName, superName, ifaces, isDao, fns.distinct())
    }

    /** The class and every app supertype (Room functions may be declared on a base DAO). */
    private fun hierarchy(internalName: String): List<ClassInfo> {
        if (internalName.startsWith("java/") || internalName.startsWith("kotlin/")) return emptyList()
        val info = read(internalName) ?: return emptyList()
        return listOf(info) + listOfNotNull(info.superName).flatMap { hierarchy(it) } + info.interfaces.flatMap { hierarchy(it) }
    }

    private fun daoClasses(): List<Class<*>> = databases.flatMap { db ->
        db.methods.filter { Modifier.isAbstract(it.modifiers) && it.parameterCount == 0 }
            .map { it.returnType }
            .filter { read(it.name.replace('.', '/'))?.isDao == true }
    }.distinct()

    private fun isSuspend(descriptor: String): Boolean =
        Type.getArgumentTypes(descriptor).lastOrNull()?.internalName == "kotlin/coroutines/Continuation"

    /** `Dao.function` for each non-suspend Room function reachable from [dao]. */
    private fun offendersOf(dao: Class<*>): List<String> =
        hierarchy(dao.name.replace('.', '/')).flatMap { c ->
            c.roomFunctions.filterNot { isSuspend(it.second) }.map { "${dao.simpleName}.${it.first}" }
        }.distinct()

    @Test fun everyRoomDaoFunctionIsSuspend() {
        val daos = daoClasses()
        println("DAOs found (${daos.size}): ${daos.map { it.simpleName }.sorted()}")
        assertTrue("found only ${daos.size} DAOs", daos.size >= DAO_COUNT)
        val offenders = daos.filterNot { it.simpleName in notYetConverted }.flatMap { offendersOf(it) }
        assertEquals(offenders.joinToString(), emptyList<String>(), offenders)
    }

    @Test fun notYetConvertedEntriesStillHaveOffenders() {
        val daos = daoClasses().associateBy { it.simpleName }
        for (name in notYetConverted) {
            val dao = daos[name] ?: error("$name is not a DAO reachable from the databases; remove it from notYetConverted")
            assertFalse("$name is fully suspend now; remove it from notYetConverted", offendersOf(dao).isEmpty())
        }
    }

    @Test fun everyCheckedDaoHasDetectedRoomFunctions() {
        // An already-suspend DAO (ReadingPlanDao) must not pass vacuously because the detector saw nothing.
        val empty = daoClasses().filterNot { it.simpleName in notYetConverted }
            .filter { hierarchy(it.name.replace('.', '/')).sumOf { c -> c.roomFunctions.size } == 0 }
        assertEquals("checked DAOs with no detected Room function: $empty", emptyList<Class<*>>(), empty)
    }

    /** Test-only stand-in for a DAO; not processed by Room (KSP runs on main only), annotations are what we read. */
    @androidx.room.Dao
    interface FixtureDao {
        @androidx.room.Query("SELECT 1") fun blocking(): Int
        @androidx.room.Query("SELECT 2") suspend fun suspending(): Int
        fun plainHelper(): Int = 3
    }

    @Test fun detectionSeesSuspendAndNonSuspendFunctions() {
        // Drive the real ASM visitor on compiled classes, not on hand-written descriptors.
        val fixture = read(FixtureDao::class.java.name.replace('.', '/'))!!
        assertTrue(fixture.isDao)
        assertEquals(setOf("blocking", "suspending"), fixture.roomFunctions.map { it.first }.toSet())
        assertEquals(listOf("FixtureDao.blocking"), offendersOf(FixtureDao::class.java))

        // A converted production DAO: functions are found and every one is suspend.
        val epub = read("net/bible/android/database/EpubDao")!!
        assertTrue(epub.isDao)
        assertTrue("no Room functions detected on EpubDao", epub.roomFunctions.isNotEmpty())
        assertTrue(epub.roomFunctions.all { isSuspend(it.second) })
    }

    private companion object { const val DAO_COUNT = 28 }
}
