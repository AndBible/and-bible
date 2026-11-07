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
package net.bible.sharedcore.platform

/**
 * Whether an iOS run is a beta build. The same binary goes from TestFlight to the App Store, so this is decided at
 * run time: a TestFlight install has the sandbox receipt (`appStoreReceiptURL.lastPathComponent == "sandboxReceipt"`),
 * an App Store install has `receipt`.
 *
 * Simulator and debug (Xcode) runs are never beta by default (debug device builds carry the sandbox receipt name
 * too, and the XCUITests must not get the beta notice); [FORCE_BETA_ARG] turns a simulator or debug run into a beta
 * one, to check the notice. Release device builds ignore the argument.
 */
object IosBetaBuild {
    const val TESTFLIGHT_RECEIPT = "sandboxReceipt"

    /** Launch argument: treat a simulator or debug run as a beta build. */
    const val FORCE_BETA_ARG = "-betaBuild"

    fun isBeta(receiptName: String?, isSimulator: Boolean, isDebugBinary: Boolean, arguments: List<String>): Boolean {
        if (isSimulator || isDebugBinary) return FORCE_BETA_ARG in arguments
        return receiptName == TESTFLIGHT_RECEIPT
    }
}
