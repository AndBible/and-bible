#!/bin/bash
echo tx status // to view
echo tx pull --force // force download of translations ignoring file dates
echo tx push -s // push the English source file to Transifex 
tx pull
echo Java uses some legacy language codes for Hebrew and Indonesian 
cp app/src/main/res/values-he/strings.xml app/src/main/res/values-iw
cp app/src/main/res/values-id/strings.xml app/src/main/res/values-in