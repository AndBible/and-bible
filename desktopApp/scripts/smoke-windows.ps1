# Smoke test for the Windows app image zip (the Windows counterpart of smoke-installed.sh).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File smoke-windows.ps1 -Zip <andbible-...-windows-x64.zip>
#        [-TimeoutSec 120] [-SwordDir <dir with mods.d and modules>] [-Keep]
#
# Unzips the app image into a fresh temp folder and starts AndBible.exe with a fresh temporary profile (APPDATA and
# LOCALAPPDATA point into the temp folder, so the user's real data is never touched). Readiness, as in
# smoke-installed.sh: "[desktop] reading ready " with -SwordDir, else "I/DesktopAppStartup: Databases open" and the
# process still alive 5 s later. The app is then closed like a user closes it (WM_CLOSE to its main window); a kill
# only follows a failed close and fails the smoke.
#
# Fails on: no readiness, an "already running" line, a close that does not end the process within 20 s, any
# AndBible.exe / java.exe / jcef_helper.exe from the unzipped image left afterwards, and a Windows Error Reporting
# crash (WerFault.exe, or an Application Error / Windows Error Reporting event for these executables since the start;
# not RADAR_PRE_LEAK, a memory heuristic).
# Exit 0 only if all of that held. Needs an interactive or SSH session of the test user; no Java on the PATH.
param(
    [Parameter(Mandatory = $true)][string]$Zip,
    [int]$TimeoutSec = 120,
    [string]$SwordDir = '',
    [switch]$Keep
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Fail([string]$message) { Write-Output "FAIL: $message"; $script:rc = 1 }

$rc = 0
$started = Get-Date
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("absmoke-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force $tmp | Out-Null
# The system bsdtar unpacks the 370 MB image in seconds; Expand-Archive takes minutes on a fresh VM.
New-Item -ItemType Directory -Force (Join-Path $tmp 'image') | Out-Null
& "$env:SystemRoot\System32\tar.exe" -xf $Zip -C (Join-Path $tmp 'image')
if ($LASTEXITCODE -ne 0) { Write-Output "FAIL: could not unpack $Zip"; exit 1 }
$exe = Join-Path $tmp 'image\AndBible\AndBible.exe'
if (-not (Test-Path $exe)) { Write-Output "FAIL: no AndBible\AndBible.exe in $Zip"; exit 1 }
$imageDir = Split-Path $exe

$appData = Join-Path $tmp 'Roaming'
$localAppData = Join-Path $tmp 'Local'
$dataDir = Join-Path $appData 'AndBible'
New-Item -ItemType Directory -Force $appData, $localAppData | Out-Null
if ($SwordDir) {
    if (-not ((Test-Path (Join-Path $SwordDir 'mods.d')) -and (Test-Path (Join-Path $SwordDir 'modules')))) {
        Write-Output "FAIL: -SwordDir needs mods.d and modules: $SwordDir"; exit 1
    }
    New-Item -ItemType Directory -Force (Join-Path $dataDir 'sword') | Out-Null
    Copy-Item -Recurse (Join-Path $SwordDir '*') (Join-Path $dataDir 'sword')
    $pattern = '\[desktop\] reading ready '
} else {
    $pattern = 'I/DesktopAppStartup: Databases open'
}
$log = Join-Path $dataDir 'logs\andbible.log'
$stdout = Join-Path $tmp 'stdout.txt'
$stderr = Join-Path $tmp 'stderr.txt'

# The child inherits this process's environment: point its profile into the temp folder.
$env:APPDATA = $appData
$env:LOCALAPPDATA = $localAppData
$proc = Start-Process -FilePath $exe -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
# Without a cached handle, Process.ExitCode stays empty after Start-Process -PassThru.
$null = $proc.Handle
function Read-Logs { (@($log, $stdout, $stderr) | Where-Object { Test-Path $_ } | ForEach-Object { Get-Content -Raw $_ }) -join "`n" }

$ready = $false
$seen = -1
for ($i = 0; $i -lt $TimeoutSec; $i++) {
    if ($proc.HasExited) { break }
    $text = Read-Logs
    if ($text -match 'already running') { break }
    if ($text -match $pattern) {
        if ($seen -lt 0) { $seen = $i }
        if ($SwordDir -or ($i - $seen) -ge 5) { $ready = $true; break }
    }
    Start-Sleep -Seconds 1
}
$text = Read-Logs
if ($text -match 'already running') { Fail "app reports 'already running'" }
elseif ($ready) { Write-Output ("OK: " + (($text -split "`n") | Where-Object { $_ -match $pattern } | Select-Object -First 1)) }
else { Fail "no readiness line ('$pattern') within $TimeoutSec s, or the app exited first (exit $(if ($proc.HasExited) { $proc.ExitCode } else { 'n/a' }))" }

# Close it the way a user does: WM_CLOSE to the main window.
if (-not $proc.HasExited) {
    # Process.CloseMainWindow needs a visible main window of the current desktop; an SSH session's desktop is not the
    # interactive one, so WM_CLOSE goes to every visible top-level window of the process instead (not to AWT's hidden
    # toolkit windows).
    Add-Type -Namespace AbSmoke -Name Win -MemberDefinition @'
[DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr p);
[DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
[DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
[DllImport("user32.dll")] static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
delegate bool EnumProc(IntPtr h, IntPtr p);
public static int CloseAll(uint target) {
    int n = 0;
    EnumWindows((h, p) => { uint pid; GetWindowThreadProcessId(h, out pid); if (pid == target && IsWindowVisible(h) && PostMessage(h, 0x0010, IntPtr.Zero, IntPtr.Zero)) n++; return true; }, IntPtr.Zero);
    return n;
}
'@
    # The jpackage launcher re-starts itself: the window belongs to a child AndBible.exe, not to $proc.
    $closed = 0
    Get-CimInstance Win32_Process | Where-Object {
        $_.ExecutablePath -and $_.ExecutablePath.StartsWith($imageDir, [StringComparison]::OrdinalIgnoreCase)
    } | ForEach-Object { $closed += [AbSmoke.Win]::CloseAll([uint32]$_.ProcessId) }
    if ($closed -eq 0) { Fail "could not send WM_CLOSE (the process has no visible top-level window)" } else { Write-Output "sent WM_CLOSE to $closed window(s)" }
    if (-not $proc.WaitForExit(20000)) {
        Fail 'the app did not exit within 20 s of WM_CLOSE; killing it'
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        $proc.WaitForExit(10000) | Out-Null
    } elseif ($proc.ExitCode -ne 0) {
        Fail "the app exited with $($proc.ExitCode) after WM_CLOSE"
    } else {
        Write-Output 'OK: closed with exit 0'
    }
}

# Nothing of this image may be left running, and no crash may have been reported.
Start-Sleep -Seconds 3
$left = Get-CimInstance Win32_Process | Where-Object {
    $_.ExecutablePath -and $_.ExecutablePath.StartsWith($imageDir, [StringComparison]::OrdinalIgnoreCase)
}
if ($left) {
    Fail ("processes left: " + (($left | ForEach-Object { "$($_.Name) ($($_.ProcessId))" }) -join ', '))
    $left | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
} else { Write-Output 'OK: no AndBible.exe / java.exe / jcef_helper.exe left' }
$wer = Get-Process -Name WerFault -ErrorAction SilentlyContinue
# RADAR_PRE_LEAK_64 is Windows' memory-growth heuristic (seen for AndBible.exe and the Gradle JVM in W1): no crash, no dialog.
$events = Get-WinEvent -FilterHashtable @{ LogName = 'Application'; StartTime = $started } -ErrorAction SilentlyContinue |
    Where-Object { ($_.ProviderName -in 'Application Error', 'Windows Error Reporting') -and ($_.Message -match 'AndBible|jcef_helper|java\.exe') } |
    Where-Object { $_.Message -notmatch 'RADAR_PRE_LEAK' }
if ($wer -or $events) {
    Fail ("Windows Error Reporting: " + (@($events | ForEach-Object { $_.Message.Split("`n")[0] }) + @($wer | ForEach-Object { "WerFault.exe ($($_.Id))" }) -join ' | '))
} else { Write-Output 'OK: no Windows Error Reporting crash' }

if ($rc -ne 0) {
    Write-Output '--- last log lines ---'
    (Read-Logs) -split "`n" | Select-Object -Last 40
}
if ($Keep) { Write-Output "kept: $tmp" } else { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
exit $rc
