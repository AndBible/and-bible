/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

export function exec(command: string, value: string | null = null): boolean

type EditorElement = HTMLElement & { content: HTMLElement }
type Action = string |
    { divider: boolean } |
    {
        icon: string[]
        title: string
        state?: () => boolean
        result: () => boolean | Promise
    }

type PellSettings = {
    element: HTMLElement,
    onChange: (html: string) => void
    actions: Action[]
}

export function init(settings: PellSettings): EditorElement

export function queryCommandState(state: string): boolean