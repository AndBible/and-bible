/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

const fs = require('fs')

fs.readFile('./refparser.js', 'utf8', (err, data) => {
    /*const dataUri = 'data:text/javascript;charset=utf-8,' + encodeURIComponent(data)
    import(dataUri).then((module) => {
        console.log(module)
    })*/
    let module = {}
    Function(data).call(module)
    console.log(new module.bcv_parser)
})

/*import('./refparser.js').then(module => {
    console.log(module)
})*/
