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
import os
import re

import yaml
import jinja2

dir_path = os.path.dirname(os.path.realpath(__file__))

constants = yaml.load(open(os.path.join(dir_path, "constants.yml")).read(), yaml.SafeLoader)
template = jinja2.Template(open(os.path.join(dir_path, "playstore-description-template.txt")).read())


def render(filename):
    description = yaml.load(open(filename).read(), yaml.SafeLoader)

    variables = dict(**description, **constants)
    variables = {key: str(value).strip() for key, value in variables.items()}
    variables = {key: jinja2.Template(value).render(**variables) for key, value in variables.items()}

    return template.render(**variables)


def give_path(lang):
    return os.path.join(dir_path, f"../fastlane/metadata/android/{lang}/full_description.txt")


with open(give_path("en-US"), "w") as f:
    f.write(render(os.path.join(dir_path,"playstore-description.yml")))

translation_folder = os.path.join(dir_path, "description-translations")
matcher = re.compile(r"^([a-zA-Z-]+)\.yml$")
for ymlfile in os.listdir(translation_folder):
    lang = matcher.match(ymlfile).group(1)
    with open(give_path(lang), "w") as f:
        f.write(render(os.path.join(translation_folder, ymlfile)))

