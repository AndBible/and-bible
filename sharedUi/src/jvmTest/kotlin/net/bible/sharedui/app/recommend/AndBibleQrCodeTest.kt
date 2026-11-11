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

package net.bible.sharedui.app.recommend

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The checked-in QR drawable must really decode to the homepage. */
class AndBibleQrCodeTest {
    private val xml = File("src/commonMain/composeResources/drawable/andbible_qr.xml").readText()

    private fun attr(name: String): String =
        Regex("android:$name=\"([^\"]*)\"").findAll(xml).first().groupValues[1]

    @Test
    fun vectorDecodesToHomepage() {
        val size = attr("viewportWidth").toInt()
        val darkPath = Regex("android:fillColor=\"#000000\"\\s+android:pathData=\"([^\"]*)\"")
            .find(xml)!!.groupValues[1]
        val scale = 8
        val image = BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, image.width, image.height)
            color = Color.BLACK
            Regex("M(\\d+),(\\d+)h1v1h-1z").findAll(darkPath).forEach {
                val (x, y) = it.destructured
                fillRect(x.toInt() * scale, y.toInt() * scale, scale, scale)
            }
            dispose()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))))
        assertEquals("https://andbible.org", QRCodeReader().decode(bitmap).text)
    }

    @Test
    fun hasFourModuleQuietZone() {
        val darkPath = Regex("android:fillColor=\"#000000\"\\s+android:pathData=\"([^\"]*)\"")
            .find(xml)!!.groupValues[1]
        val size = attr("viewportWidth").toInt()
        val cells = Regex("M(\\d+),(\\d+)h1v1h-1z").findAll(darkPath).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
        assertTrue(cells.all { (x, y) -> x in 4 until size - 4 && y in 4 until size - 4 })
    }
}
