# Starts a command in the interactive desktop session of the logged-on test user (windows-vm.sh gui-run).
# A process started over SSH lives on a non-interactive window station: its windows are invisible and get no input.
# This runs the command through a one-shot scheduled task with /IT instead, so the user must be logged on at the
# console (see docs/windows-vm.md). windows-vm.sh writes the command into C:\abtest\gui-run.cmd (cmd.exe syntax, UTF-8
# without a BOM, code page 65001), so `set "NAME=value" && ...` prefixes set environment variables for it. Returns at
# once; the command's output goes to C:\abtest\gui-run.log and, when it ends, its exit code to C:\abtest\gui-run.exit.
$ErrorActionPreference = 'Stop'
schtasks.exe /Create /F /TN abtest-gui-run /TR 'cmd.exe /c C:\abtest\gui-run.cmd' /SC ONCE /ST 23:59 /IT | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks /Create failed ($LASTEXITCODE)" }
Remove-Item C:\abtest\gui-run.exit -ErrorAction SilentlyContinue
schtasks.exe /Run /TN abtest-gui-run | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks /Run failed ($LASTEXITCODE)" }
'started'
