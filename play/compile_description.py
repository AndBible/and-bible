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
full_description_template = jinja2.Template(open(os.path.join(dir_path, "full_description_template.txt")).read())
full_description_template_plaintext = jinja2.Template(open(os.path.join(dir_path, "full_description_template_plaintext.txt")).read())
homepage_template = jinja2.Template(open(os.path.join(dir_path, "homepage_template.html")).read())
short_description_template = jinja2.Template("{{short_description}}")
title_template = jinja2.Template("{{title}}")

def render(filename, template=full_description_template, skip_issues=False):
    description = yaml.load(open(filename).read(), yaml.SafeLoader)

    variables = dict(**description, **constants)
    variables = {key: str(value).strip() for key, value in variables.items()}
    variables = {key: jinja2.Template(value).render(**variables) for key, value in variables.items()}

    rendered = template.render(**variables)
    if not skip_issues:
        for issue in ["{{", "}}", "_"]:
            if issue in rendered:
                raise RuntimeError(f"Issue with full_description_template render {filename}: {rendered}")
    return rendered


def give_path(lang, path="../fastlane/metadata/android/", txt_file="full_description.txt"):
    try:
        os.mkdir(os.path.join(dir_path, f"{path}{lang}"))
    except FileExistsError:
        pass
    return os.path.join(dir_path, f"{path}{lang}/{txt_file}")


with open(give_path("en-US"), "w") as f:
    f.write(render(os.path.join(dir_path,"playstore-description.yml")))

#with open(os.path.join(dir_path, "../../homepage/index.html"), "w") as f:
#    f.write(render(os.path.join(dir_path,"playstore-description.yml"), homepage_template, skip_issues=True))

translation_folder = os.path.join(dir_path, "description-translations")
matcher = re.compile(r"^([a-zA-Z-]+)\.yml$")
for ymlfile in os.listdir(translation_folder):
    lang = matcher.match(ymlfile).group(1)
    yml_file = os.path.join(translation_folder, ymlfile)
    with open(give_path(lang), "w") as f:
        f.write(render(yml_file, full_description_template))

    with open(os.path.join(dir_path, f"./plaintext-descriptions/{lang}.txt"), "w") as f:
        f.write(render(yml_file, full_description_template_plaintext))

    with open(give_path(lang, txt_file="short_description.txt"), "w") as f:
        f.write(render(yml_file, short_description_template))

    with open(give_path(lang, txt_file="title.txt"), "w") as f:
        f.write(render(yml_file, title_template))
