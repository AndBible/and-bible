rem tx status // to view
rem tx pull --force // force download of translations ignoring file dates
rem tx push -s // push the English source file to Transifex 
tx pull
rem Java uses some legacy language codes for Hebrew and Indonesian 
copy res\values-he\strings.xml res\values-iw
copy res\values-id\strings.xml res\values-in