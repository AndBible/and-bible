/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.readingplan

import net.bible.sharedcore.log.Log

import net.bible.android.SharedConstants
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.AndRuntimeException
import net.bible.service.common.ProvidedReadingPlan
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.readingplan.ReadingPlanSource

import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJV
import org.crosswire.jsword.versification.system.SystemNRSVA
import org.crosswire.jsword.versification.system.Versifications

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Properties
import kotlin.math.max

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
/** Name and description of a plan the app distributes, localized (shown instead of the file's header comments). */
class DistributedPlanDetails(val planCode: String, val planName: String, val planDescription: String)

/**
 * Reads reading plans: the bundled ones through [source], user-installed ones from [userPlanFolder]
 * and from add-on modules ([providedPlans]).
 *
 * @param distributedPlans evaluated lazily at each lookup, so localized names resolve when first needed.
 */
class ReadingPlanTextFileDao(
    private val source: ReadingPlanSource,
    private val repository: ReadingPlanRepository,
    private val coreStrings: CoreStrings,
    private val userPlanFolder: () -> File = { SharedConstants.manualReadingPlanDir },
    private val providedPlans: () -> Map<String, ProvidedReadingPlan> = { AndBibleAddons.providedReadingPlans },
    private val distributedPlans: () -> List<DistributedPlanDetails> = { emptyList() },
) {
    private var cachedPlanProperties: ReadingPlanProperties? = null
    private var cachedReadingList: List<OneDaysReadingsDto>? = null

    /** All plans (bundled, user, add-on) the user can choose from. */
    suspend fun readingPlanList(): List<ReadingPlanInfoDto> {
            try {
                val codes = allReadingPlanCodes

                val planInfoList = ArrayList<ReadingPlanInfoDto>()
                for (code in codes) {
                    planInfoList.add(getReadingPlanInfoDto(code))
                }

                return planInfoList

            } catch (e: Exception) {
                Log.e(TAG, "Error getting reading plans", e)
                throw AndRuntimeException("Error getting reading plans", e)
            }
    }

    /** look in assets/readingplan and sdcard/jsword/readingplan for reading plans and return a list of all codes
     */
    private val allReadingPlanCodes: List<String>
        @Throws(IOException::class)
        get() {

            val allCodes = ArrayList<String>()

            allCodes.addAll(internalPlanCodes)

            val userPlans = userPlanCodes()
            if(userPlans != null) {
                allCodes.addAll(userPlans.filter { s -> !allCodes.contains(s) })
            }

            val userPlanModules = providedPlans().keys
            allCodes.addAll(userPlanModules.filter { s -> !allCodes.contains(s) })

            return allCodes
        }

    val internalPlanCodes: List<String>
        @Throws(IOException::class)
        get() = source.builtInPlanCodes()

    fun userPlanCodes(filterDuplicates: Boolean = true): List<String>? {
            val userPlans = userPlanFolder().list()
            return if (userPlans != null) {
                if (filterDuplicates) {
                    getReadingPlanCodes(userPlans).filter { userPlan ->
                        userPlan != internalPlanCodes.find { internalPlan -> internalPlan == userPlan }
                    }
                } else {
                    getReadingPlanCodes(userPlans)
                }
            } else {
                null
            }
        }

    /** get a list of all days readings in a plan
     */
    suspend fun getReadingList(planCode: String): List<OneDaysReadingsDto> {
        var list: ArrayList<OneDaysReadingsDto>? = null
        val cachedReadingList = cachedReadingList
        if (cachedReadingList == null || planCode != cachedReadingList[0].readingPlanInfo.planCode) {
            Log.i(TAG,"Getting List of days readings for plan $planCode")
            list = ArrayList()
            val planInfo = getReadingPlanInfoDto(planCode)
            val properties = getPlanProperties(planCode)

            for ((key1, value1) in properties) {
                val dayNumber = (key1 as String).toIntOrNull() ?: continue
                val readingString = value1 as String

                list.add(OneDaysReadingsDto(dayNumber, readingString, planInfo, coreStrings))
            }
            list.sort()
            this.cachedReadingList = list
        }

        return list ?: cachedReadingList!!
    }

    /** get readings for one day
     */
    suspend fun getReading(planName: String, dayNo: Int): OneDaysReadingsDto {
        val properties = getPlanProperties(planName)

        val readings = properties[dayNo.toString()] as String?
        Log.i(TAG, "Readings for day:$readings")
        return OneDaysReadingsDto(dayNo, readings, getReadingPlanInfoDto(planName), coreStrings)
    }

    /** get last day number - there may be missed days so cannot simply do props.size()
     */
    fun getNumberOfPlanDays(planCode: String): Int {
        if (cachedPlanProperties?.planCode == planCode)
            return cachedPlanProperties?.numberOfPlanDays ?: 0

        return getNumberOfPlanDays(getPlanProperties(planCode))
    }

    private fun getNumberOfPlanDays(properties: ReadingPlanProperties): Int {
        var maxDayNo = 0

        for (oDayNo in properties.keys) {
            val dayNo = (oDayNo as String).toIntOrNull()
            if (dayNo != null) {
                maxDayNo = max(maxDayNo, dayNo)
            } else {
                if (!VERSIFICATION.equals(dayNo, ignoreCase = true)) {
                    Log.e(TAG, "Invalid day number:$dayNo")
                }
            }
        }

        return maxDayNo
    }

    /**
     * Get versification specified in properties file e.g. 'Versification=Vulg'
     * Default to KJV.
     * If specified Versification is not found then use NRSVA because it includes most books possible
     */
    private fun getReadingPlanVersification(planCode: String): Versification {
        if (cachedPlanProperties?.planCode == planCode)
            return cachedPlanProperties?.versification!!

        return getReadingPlanVersification(getPlanProperties(planCode))
    }

    private fun getReadingPlanVersification(properties: ReadingPlanProperties, versificationString: String? = null): Versification =
        try {
            val versificationName = versificationString ?: properties.getProperty(VERSIFICATION, DEFAULT_VERSIFICATION)
            Versifications.instance().getVersification(versificationName)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading versification from Reading plan:${properties.planCode}")
            Versifications.instance().getVersification(INCLUSIVE_VERSIFICATION)
        }

    suspend fun getReadingPlanInfoDto(planCode: String): ReadingPlanInfoDto {
        Log.i(TAG, "Get reading plan info:$planCode")
        val info = ReadingPlanInfoDto(planCode)

        info.planName = getPlanName(planCode)
        info.planDescription = getPlanDescription(planCode)
        info.numberOfPlanDays = getNumberOfPlanDays(planCode)
        info.versification = getReadingPlanVersification(planCode)
        info.isDateBasedPlan = getPlanProperties(planCode).isDateBasedPlan
        info.startDate = repository.getStartDate(planCode)

        return info
    }

    private fun getPlanName(planCode: String): String {
        return distributedPlans().find { it.planCode == planCode }?.planName
            ?: getPlanProperties(planCode).planName ?: planCode
    }

    private fun getPlanDescription(planCode: String): String {
        return distributedPlans().find { it.planCode == planCode } ?.planDescription
            ?: getPlanProperties(planCode).planDescription ?: ""
    }

    private fun getReadingPlanCodes(files: Array<String>): List<String> {
        val codes = ArrayList<String>()
        for (file in files) {
			// this if statement ensures we only deal with .properties files - not folders or anything else
			if (file.endsWith(DOT_PROPERTIES)) {
				// remove the file extension to get the code
				codes.add(file.replace(DOT_PROPERTIES, ""))
			}
		}
        return codes
    }

    /* either load reading plan info from assets/readingplan or sdcard/jsword/readingplan
	 */
    @Synchronized
    private fun getPlanProperties(planCode: String): ReadingPlanProperties {
        if (planCode != cachedPlanProperties?.planCode) {
            val filename = planCode + DOT_PROPERTIES

            // Read from the /assets directory
            val properties = ReadingPlanProperties()
            try {
                // check to see if a user has created his own reading plan with this name
                val userReadingPlanFile = File(userPlanFolder(), filename)
                val userReadingPlanModule = providedPlans()[planCode]
                val isUserPlan = userReadingPlanFile.exists() || userReadingPlanModule?.file?.exists() == true

                val planBytes: ByteArray = if (!isUserPlan) {
                    // see ReadingPlanSource: Latin-1 text is the file's bytes one to one
                    (source.openBuiltInPlan(planCode) ?: throw IOException("No bundled reading plan $planCode"))
                        .toByteArray(Charsets.ISO_8859_1)
                } else {
                    if (userReadingPlanModule?.file?.exists() == true)
                        FileInputStream(userReadingPlanModule.file).use { it.readBytes() }
                    else
                        FileInputStream(userReadingPlanFile).use { it.readBytes() }
                }

                properties.load(ByteArrayInputStream(planBytes))
                properties.planCode = planCode
                properties.numberOfPlanDays = getNumberOfPlanDays(properties)
                properties.versification = getReadingPlanVersification(properties, userReadingPlanModule?.book?.getProperty(VERSIFICATION))
                properties.isDateBasedPlan = userReadingPlanModule?.isDateBased ?: properties["1"].toString().contains("^([a-z]|[A-Z]){3}-([0-9]{1,2});".toRegex())
                if (userReadingPlanModule != null) {
                    properties.planName = userReadingPlanModule.book.name
                    properties.planDescription = userReadingPlanModule.book.getProperty(SwordBookMetaData.KEY_SHORT_PROMO)
                } else {
                    getNameAndDescFromProperties(ByteArrayInputStream(planBytes), properties)
                }

                Log.i(TAG, "The properties are now loaded")
                Log.i(TAG, "properties: $properties")

                // cache it so we don't constantly reload the properties
                cachedPlanProperties = properties

            } catch (e: IOException) {
                Log.e(TAG, "Failed to open reading plan property file", e)
            }
        }
        return cachedPlanProperties!!
    }

    private fun getNameAndDescFromProperties(inputStream: InputStream, properties: ReadingPlanProperties) {
        var lineCount = 0
        var loopCount = 0
        // Get first commented lines from file for Plan Name (first line)
        // and Description (following commented lines) up to line 5: otherwise any
        // commented lines further down in the file will also get added to description
        inputStream.bufferedReader().forEachLine {
            if (it.startsWith("#") && loopCount < 5) {
                val lineWithoutCommentMarks: String = it.trim().replaceFirst("^(\\s*#*\\s*)".toRegex(), "")
                Log.i(TAG, lineWithoutCommentMarks)
                if (lineCount == 0) {
                    properties.planName = lineWithoutCommentMarks.trim()
                } else {
                    properties.planDescription = "${properties.planDescription ?: ""} $lineWithoutCommentMarks ".trim()
                }
                lineCount++
            }
            loopCount++
        }
    }

    private class ReadingPlanProperties : Properties() {
        var planCode = ""
        var planName: String? = null
        var planDescription: String? = null
        var versification: Versification? = null
        var numberOfPlanDays = 0
        var isDateBasedPlan = false
    }

    companion object {

        private const val DOT_PROPERTIES = ".properties"
        private const val VERSIFICATION = "Versification"
        private const val DEFAULT_VERSIFICATION = SystemKJV.V11N_NAME
        private const val INCLUSIVE_VERSIFICATION = SystemNRSVA.V11N_NAME

        private const val TAG = "ReadingPlanDao"
    }
}
