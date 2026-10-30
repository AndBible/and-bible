# Run from an unpackaged PowerShell session. For MSIX supply -AppId (family!application id)
# and -Exe; shell activation is required to preserve package identity.
param(
    [Parameter(Mandatory=$true)][string]$Exe,
    [string]$AppId = '',
    [switch]$TempProfile,
    [int]$TimeoutSec = 120
)
$ErrorActionPreference = 'Stop'
if ($AppId -and $TempProfile) { throw 'Shell activation cannot inherit a temporary profile; use the real profile.' }
$exePath = (Resolve-Path -LiteralPath $Exe).Path
$imageDir = (Split-Path $exePath).TrimEnd('\') + '\'
$existing = @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($imageDir, [StringComparison]::OrdinalIgnoreCase) })
if ($existing.Count) { throw 'Image already has running processes; stop them before this smoke.' }
$started = Get-Date
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('ab-installed-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $tmp | Out-Null
$oldAppData = $env:APPDATA; $oldLocal = $env:LOCALAPPDATA
if ($TempProfile) {
    $env:APPDATA = Join-Path $tmp 'Roaming'; $env:LOCALAPPDATA = Join-Path $tmp 'Local'
    New-Item -ItemType Directory $env:APPDATA, $env:LOCALAPPDATA | Out-Null
}
$log = Join-Path $env:APPDATA 'AndBible\logs\andbible.log'
$length = if (Test-Path $log) { (Get-Item $log).Length } else { 0 }
function NewLog {
    if (-not (Test-Path $log)) { return '' }
    $f = [IO.File]::Open($log, 'Open', 'Read', 'ReadWrite')
    try {
        if ($f.Length -lt $length) { $f.Position = 0 } else { $f.Position = $length }
        $reader = New-Object IO.StreamReader($f)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $f.Dispose() }
}
function ImageProcesses {
    @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($imageDir, [StringComparison]::OrdinalIgnoreCase) })
}
Add-Type -Namespace AbInstalled -Name Win -MemberDefinition @'
[DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr p);
[DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
[DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
[DllImport("user32.dll")] static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
delegate bool EnumProc(IntPtr h, IntPtr p);
public static int CloseAll(uint target) {
    int n = 0; EnumWindows((h, p) => { uint pid; GetWindowThreadProcessId(h, out pid); if (pid == target && IsWindowVisible(h) && PostMessage(h, 0x10, IntPtr.Zero, IntPtr.Zero)) n++; return true; }, IntPtr.Zero); return n;
}
'@
try {
    if ($AppId) { Start-Process explorer.exe -ArgumentList "shell:AppsFolder\$AppId" | Out-Null }
    else { $launched = Start-Process -FilePath $exePath -PassThru; $null = $launched.Handle }
    $proc = $null; $readyAt = $null
    for ($i = 0; $i -lt $TimeoutSec; $i++) {
        if (-not $proc) {
            $candidate = @(ImageProcesses | Where-Object { $_.ExecutablePath -ieq $exePath } | Select-Object -First 1)
            if ($candidate.Count) { $proc = Get-Process -Id $candidate[0].ProcessId -ErrorAction Stop; $null = $proc.Handle }
        }
        $text = NewLog
        if ($text -match 'already running') { throw 'App reports already running.' }
        if ($text -match 'I/DesktopAppStartup: Databases open') { if (-not $readyAt) { $readyAt = Get-Date } }
        if ($proc -and $proc.HasExited) { throw 'App exited before readiness.' }
        if ($proc -and $readyAt -and ((Get-Date) - $readyAt).TotalSeconds -ge 5) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $proc -or -not $readyAt -or ((Get-Date) - $readyAt).TotalSeconds -lt 5) { throw 'Timed out waiting for a live process and new readiness log.' }
    $closed = 0
    ImageProcesses | ForEach-Object { $closed += [AbInstalled.Win]::CloseAll([uint32]$_.ProcessId) }
    if (-not $closed) { throw 'No visible window accepted WM_CLOSE.' }
    if (-not $proc.WaitForExit(20000)) { throw 'App did not exit within 20 seconds.' }
    if ($null -eq $proc.ExitCode -or $proc.ExitCode -ne 0) { throw "Exit code unavailable or nonzero: $($proc.ExitCode)" }
    if ($launched) {
        if (-not $launched.WaitForExit(20000) -or $null -eq $launched.ExitCode -or $launched.ExitCode -ne 0) { throw 'Launcher did not exit cleanly.' }
    }
    Start-Sleep -Seconds 3
    $left = @(ImageProcesses)
    if ($left.Count) { throw "Image processes left: $($left.ProcessId -join ', ')" }
    $wer = @(Get-Process -Name WerFault -ErrorAction SilentlyContinue)
    $events = @(Get-WinEvent -FilterHashtable @{LogName='Application'; StartTime=$started} -ErrorAction SilentlyContinue | Where-Object { $_.ProviderName -in 'Application Error','Windows Error Reporting' -and $_.Message -match 'AndBible|jcef_helper|java\.exe' -and $_.Message -notmatch 'RADAR_PRE_LEAK' })
    if ($wer.Count -or $events.Count) { throw 'Windows Error Reporting process or crash event found.' }
    Write-Output 'OK: readiness, WM_CLOSE, exit 0, no image processes or WER'
} finally {
    $env:APPDATA = $oldAppData; $env:LOCALAPPDATA = $oldLocal
    if (Test-Path $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
}
