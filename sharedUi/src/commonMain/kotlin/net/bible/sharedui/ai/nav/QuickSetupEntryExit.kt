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


package net.bible.sharedui.ai.nav

/**
 * F86 (fix batch 3 §2.3.2): Connection settings' "Quick Setup" row pushes a Providers entry only to
 * host the wizard (`NavRoutes.aiProviders(startEasySetup = true)`). Every way out of the wizard --
 * cancelling its disclaimer, cancelling a step, or confirming DONE -- must leave that entry too, or
 * BACK lands on a Providers list the user never asked for while Up (from the list) goes where they
 * came from. A Providers entry opened normally is left alone.
 */
internal class QuickSetupEntryExit(
    private val enteredForQuickSetup: Boolean,
    private val leaveEntry: () -> Unit,
) {
    /** The "Accept AI disclaimer" dialog was cancelled; [wasQuickSetupStart] = it was guarding the wizard's start. */
    fun disclaimerDismissed(wasQuickSetupStart: Boolean) {
        if (enteredForQuickSetup && wasQuickSetupStart) leaveEntry()
    }

    /** The wizard closed, at any step (DONE's OK included -- `EasySetupWizard` routes it to `onDismiss`). */
    fun wizardClosed() {
        if (enteredForQuickSetup) leaveEntry()
    }
}
