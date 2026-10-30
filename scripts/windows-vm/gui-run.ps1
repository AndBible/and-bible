# Starts a command in the interactive desktop session of the logged-on test user (windows-vm.sh gui-run).
# A process started over SSH lives on a non-interactive window station: its windows are invisible, get no input, and
# Skiko finds no Direct3D/OpenGL device there. This runs the command through a one-shot scheduled task with /IT instead,
# so the user must be logged on at the console (see docs/windows-vm.md). windows-vm.sh writes the command into
# C:\abtest\gui\<id>.cmd (cmd.exe syntax, UTF-8 without a BOM, code page 65001), so `set "NAME=value" && ...` prefixes
# set environment variables for it. Returns at once and prints <id>; the command's output goes to
# C:\abtest\gui\<id>.log and, when it ends, its exit code to C:\abtest\gui\<id>.exit.
param([Parameter(Mandatory = $true)][string]$Id)
$ErrorActionPreference = 'Stop'
# Earlier one-shot tasks that have finished are removed; running ones (an app still open) are left alone.
Get-ScheduledTask -TaskName 'abtest-gui-*' -ErrorAction SilentlyContinue | Where-Object State -ne 'Running' |
    Unregister-ScheduledTask -Confirm:$false
schtasks.exe /Create /F /TN "abtest-gui-$Id" /TR "cmd.exe /c C:\abtest\gui\$Id.cmd" /SC ONCE /ST 23:59 /IT | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks /Create failed ($LASTEXITCODE)" }
schtasks.exe /Run /TN "abtest-gui-$Id" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "schtasks /Run failed ($LASTEXITCODE)" }
$Id
