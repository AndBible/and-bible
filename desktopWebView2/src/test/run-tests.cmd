@echo off
rem Runs the module tests on Windows (W2): java on PATH or JAVA set.
cd /d %~dp0
if "%JAVA%"=="" set JAVA=java
"%JAVA%" -Djna.library.path=%~dp0lib -cp "classes;lib\*" org.junit.runner.JUnitCore net.bible.desktop.webview2.WebView2EngineLogicTest net.bible.desktop.webview2.WebView2WindowsTest
