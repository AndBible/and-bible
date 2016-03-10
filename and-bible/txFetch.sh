#!/bin/bash
echo tx status // to view
echo tx pull --force // force download of translations ignoring file dates
echo tx push -s // push the English source file to Transifex 
tx pull
echo Java uses some legacy language codes for Hebrew and Indonesian 
cp res/values-he/strings.xml res/values-iw
cp res/values-id/strings.xml res/values-in