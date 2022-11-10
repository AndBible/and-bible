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
package net.bible.service.db

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SQLHelper {
    /**
     * Get a comma separated list of columns preceded by the table name for use in a query
     *
     * @return column list
     */
    fun getColumnsForQuery(table: String, columns: Array<String>): String {
        val returnColumns = StringBuilder()
        for (column in columns) {
            returnColumns.append(table).append(".").append(column).append(",")
        }
        // remove the extra final comma
        returnColumns.deleteCharAt(returnColumns.lastIndexOf(","))
        return returnColumns.toString()
    }
}
