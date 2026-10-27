# Module Creation

Custom modules can be created for *AndBible* versions 4.0+

Examples: <https://github.com/AndBible/special-modules/>

## General Book Modules

A SWORD module using `ModDrv=RawGenBook` has a tree of entries rather than
Bible verses. Use `Category=Generic Books` for general books, or `Category=Maps`
for maps. If a RawGenBook module incorrectly declares `Category=Biblical Texts`
or `Category=Commentaries`, AndBible treats it as a general book instead of
listing it as a Bible or commentary.

## Custom Font Modules

It is possible to offer new fonts to users with custom font modules.

Define category as “And Bible” and your fonts with `AndBibleProvidesFont` keys,
like in the following example:

```
[HebrewFonts]
DataPath=./modules/texts/ztext/HebrewFonts/
Version=1.0
Description=Provides Hebrew fonts "Sileo TSR" and "SBL Hebrew" for And Bible
DistributionLicense=Free to distribute
Category=And Bible
ModDrv=RawGenBook
InstallSize=501314
AndBibleMinimumVersion=506
AndBibleProvidesFont=Sileo TSR;and-bible/SILEOTSR.ttf
AndBibleProvidesFont=SBL Hebrew;and-bible/SBL_Hbrw.ttf
```

Put your fonts in the `and-bible/` subfolder of your module folder, i.e.
`./modules/texts/ztext/HebrewFonts/and-bible`.

`AndBibleMinimumVersion` refers to the build number (the last three digits
in the version, i.e. 3.3.506 -> 506).
