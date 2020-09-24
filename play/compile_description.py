#  Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
#
#  This file is part of And Bible (http://github.com/AndBible/and-bible).
#
#  And Bible is free software: you can redistribute it and/or modify it under the
#  terms of the GNU General Public License as published by the Free Software Foundation,
#  either version 3 of the License, or (at your option) any later version.
#
#  And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
#  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
#  See the GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License along with And Bible.
#  If not, see http://www.gnu.org/licenses/.
#

import yaml
import jinja2

description = yaml.load(open("playstore-description.yml").read(), yaml.SafeLoader)
constants = yaml.load(open("constants.yml").read(), yaml.SafeLoader)

template = jinja2.Template(open("playstore-description-template.txt").read())

variables = dict(**description, **constants)
variables = {key: str(value).strip() for key, value in variables.items()}
variables = {key: jinja2.Template(value).render(**variables) for key, value in variables.items()}

rendered = template.render(**variables)

with open("playstore-description-en.txt", "w") as f:
    f.write(rendered)
print(rendered)
