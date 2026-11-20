/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

/**
 * Add-on bookmark collections (#4162) are read-only content installed by an add-on module.
 * Kotlin marks them with a non-null `addonModule` (module initials, "" when the owner is
 * unknown), so any non-null value, including "", means add-on. Keep every read-only check here.
 */
type AddonOwned = { addonModule?: string | null } | null | undefined

/** True when the bookmark row belongs to an add-on collection (not editable, not deletable). */
export function isAddonBookmark(bookmark: AddonOwned): boolean {
    return bookmark?.addonModule != null
}

/** True when the label is an add-on collection label. */
export function isAddonLabel(label: AddonOwned): boolean {
    return label?.addonModule != null
}

/**
 * True when the StudyPad of this label is read-only: its text entries and layout/order cannot
 * change. The user's own bookmark copies in it stay editable and deletable.
 */
export function readOnlyStudyPad(label: AddonOwned): boolean {
    return isAddonLabel(label)
}
