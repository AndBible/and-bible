# Run outside the package. Capture only after the app is closed (SQLite WAL must be checkpointed).
param(
    [Parameter(Mandatory=$true)][ValidateSet('Data','Capture','Compare','Instances')][string]$Action,
    [string]$PackageFamily = '',
    [string]$Database = '',
    [string]$Module = '',
    [string]$Snapshot = '',
    [string[]]$InstanceExe = @()
)
$ErrorActionPreference = 'Stop'
$realData = Join-Path ([Environment]::GetFolderPath('ApplicationData')) 'AndBible'
if ($env:APPDATA -ine [Environment]::GetFolderPath('ApplicationData')) { throw 'Run from an unpackaged observer with the real APPDATA.' }
function CheckedFile([string]$path) {
    if (-not $path) { throw 'Both -Database and -Module are required.' }
    $item = Get-Item -LiteralPath $path
    if ($item.PSIsContainer) { throw "Expected a file: $path" }
    $root = (Resolve-Path -LiteralPath $realData).Path.TrimEnd('\') + '\'
    if (-not $item.FullName.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw "File is outside real AndBible APPDATA: $path" }
    return $item
}
function Manifest {
    foreach ($path in @($Database, $Module)) {
        $item = CheckedFile $path
        [pscustomobject]@{ Path = $item.FullName; Hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash }
    }
}
switch ($Action) {
    'Data' {
        if (-not $PackageFamily) { throw '-PackageFamily is required.' }
        $files = @(Manifest)
        if ($files[0].Path -ieq $files[1].Path) { throw 'Database and module must be different files.' }
        $cache = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "Packages\$PackageFamily\LocalCache"
        $redirected = @(if (Test-Path -LiteralPath $cache) { Get-ChildItem -LiteralPath $cache -Recurse -File -ErrorAction Stop | Where-Object { $_.FullName -match '[\\/]AndBible[\\/]' } })
        if ($redirected.Count) { throw "AndBible data found in package LocalCache: $($redirected.FullName -join ', ')" }
        Write-Output "OK: database and module exist under real APPDATA; no AndBible files in $cache"
    }
    'Capture' {
        if (-not $Snapshot) { throw '-Snapshot is required.' }
        if (Test-Path -LiteralPath $Snapshot) { throw 'Snapshot already exists; choose a new path.' }
        $files = @(Manifest)
        if ($files[0].Path -ieq $files[1].Path) { throw 'Database and module must be different files.' }
        $files | ConvertTo-Json | Set-Content -LiteralPath $Snapshot -Encoding UTF8
        Write-Output "Captured SHA256 hashes: $Snapshot"
    }
    'Compare' {
        if (-not $Snapshot) { throw '-Snapshot is required.' }
        $before = @(Get-Content -LiteralPath $Snapshot -Raw | ConvertFrom-Json)
        $after = @(Manifest)
        if ($before.Count -ne 2 -or $after.Count -ne 2) { throw 'Expected exactly two snapshot entries.' }
        $difference = @(Compare-Object $before $after -Property Path,Hash)
        if ($difference.Count) { throw "Persistence comparison failed: $($difference | Out-String)" }
        Write-Output 'OK: database and module paths and SHA256 hashes unchanged'
    }
    'Instances' {
        if ($InstanceExe.Count -lt 2) { throw 'Supply at least two distinct executable paths in -InstanceExe.' }
        $paths = @($InstanceExe | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
        if (@($paths | Select-Object -Unique).Count -ne $paths.Count) { throw 'Executable paths must be distinct.' }
        $processes = @(Get-CimInstance Win32_Process)
        $running = @($paths | Where-Object { $path = $_; @($processes | Where-Object { $_.ExecutablePath -ieq $path }).Count -gt 0 })
        if ($running.Count -ne 1) { throw "Expected exactly one installation running; found $($running.Count): $($running -join ', ')" }
        Write-Output "OK: exactly one installation running: $($running[0]); manually verify the other launch exits successfully or hands off."
    }
}
