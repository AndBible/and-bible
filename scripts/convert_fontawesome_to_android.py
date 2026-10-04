import os
import re
import argparse

# Hardcoded mapping from icon names to source JS filenames
js_mapping = {
    "star": "faStar.js",
    "book": "faBook.js",
    "flag": "faFlag.js",
    "user": "faUser.js",
    "info": "faCircleInfo.js",
    "question": "faCircleQuestion.js",
    "lightbulb": "faLightbulb.js",
    "bell": "faBell.js",
    "globe": "faGlobe.js",
    "clock": "faClock.js",
    "envelope": "faEnvelope.js",
    "map-marker": "faLocationDot.js",
    "link": "faLink.js",
    "cross": "faCross.js",
    "exclamation": "faCircleExclamation.js",
    "church": "faChurch.js",
    "heart": "faHeart.js",
    "heart-crack": "faHeartCrack.js",
    "crown": "faCrown.js",
    "comment": "faComment.js",
    "key": "faKey.js",
    "calendar": "faCalendar.js",
    "book-bible": "faBookBible.js",
    "share-nodes": "faShareNodes.js",
    "tag": "faTag.js",
    "star-of-david": "faStarOfDavid.js",
    "handshake": "faHandshake.js",
    "person-praying": "faPersonPraying.js",
    "music": "faMusic.js",
    "microphone": "faMicrophone.js",
    "landmark": "faLandmark.js"
}

# Replace the previous list with the mapping keys.
icons_to_convert = list(js_mapping.keys())

def process_file(filepath, output_dir):
    # Read the icon file (TS/JS)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Extract properties from the file content
    iconName_match = re.search(r"var\s+iconName\s*=\s*'([^']+)'", content)
    width_match = re.search(r"var\s+width\s*=\s*(\d+)", content)
    height_match = re.search(r"var\s+height\s*=\s*(\d+)", content)
    svgPathData_match = re.search(r"var\s+svgPathData\s*=\s*'([^']+)'", content)

    if not (iconName_match and width_match and height_match and svgPathData_match):
        print(f"Skipping file {filepath}: missing required icon properties.")
        return

    iconName = iconName_match.group(1)
    #if iconName not in icons_to_convert:
    #    print(f"Skipping file {filepath}: icon '{iconName}' not in conversion list.")
    #    return

    width = width_match.group(1)
    height = height_match.group(1)
    svgPathData = svgPathData_match.group(1)

    # Create XML content using the FontAwesome values
    xml_template = f'''<?xml version="1.0" encoding="utf-8"?>
<vector
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{width}"
    android:viewportHeight="{height}"
    xmlns:android="http://schemas.android.com/apk/res/android">
    <path
        android:fillColor="#000000"
        android:pathData="{svgPathData}"/>
</vector>
'''

    # Convert hyphens to underscores for Android filename compatibility
    safe_iconName = iconName.replace("-", "_")
    # Write the XML to the output directory with file name icon_<safe_iconName>.xml
    out_filepath = os.path.join(output_dir, f"icon_{safe_iconName}.xml")
    with open(out_filepath, 'w', encoding='utf-8') as f:
        f.write(xml_template)
    print(f"Converted {filepath} to {out_filepath}")

def main():
    parser = argparse.ArgumentParser(
        description="Convert FontAwesome TS/JS icons to Android XML drawable files")
    parser.add_argument('--source', required=True,
                        help="Source directory containing FontAwesome TS/JS files")
    parser.add_argument('--output', required=True,
                        help="Output directory for Android XML drawable files")
    args = parser.parse_args()

    source_dir = args.source
    output_dir = args.output

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    # Process each icon based on our hardcoded mapping.
    for iconName, jsFilename in js_mapping.items():
        file_path = os.path.join(source_dir, jsFilename)
        if os.path.exists(file_path):
            process_file(file_path, output_dir)
        else:
            print(f"Source file not found: {file_path}")

if __name__ == '__main__':
    main()
