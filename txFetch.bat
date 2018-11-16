rem tx status // to view
rem tx pull --force // force download of translations ignoring file dates
rem tx push -s // push the English source file to Transifex 
tx-64 pull
rem Java uses some legacy language codes for Hebrew and Indonesian 
copy app\src\main\res\values-he\strings.xml app\src\main\res\values-iw
copy app\src\main\res\values-id\strings.xml app\src\main\res\values-in