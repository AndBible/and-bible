# Run with Windows PowerShell 5.1 from an unpackaged observer.
$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'live-checks.ps1'
$root = Join-Path ([Environment]::GetFolderPath('ApplicationData')) ('AndBible\live-checks-test-' + [guid]::NewGuid())
$snapshot = Join-Path ([IO.Path]::GetTempPath()) ('andbible-snapshot-' + [guid]::NewGuid() + '.json')
try {
    New-Item -ItemType Directory -Path $root | Out-Null
    $database = Join-Path $root 'test.sqlite3'
    $module = Join-Path $root 'test.module'
    Set-Content -LiteralPath $database -Value 'database'
    Set-Content -LiteralPath $module -Value 'module'
    $arguments = @{ Database = $database; Module = $module; Snapshot = $snapshot }

    & $script -Action Capture @arguments
    & $script -Action Compare @arguments

    Set-Content -LiteralPath $module -Value 'changed module'
    $failed = $false
    try { & $script -Action Compare @arguments } catch {
        if ($_.Exception.Message -notmatch 'Persistence comparison failed') { throw }
        $failed = $true
    }
    if (-not $failed) { throw 'Compare accepted a changed module.' }

    '[{"Path":"only-one","Hash":"hash"}]' | Set-Content -LiteralPath $snapshot -Encoding UTF8
    $failed = $false
    try { & $script -Action Compare @arguments } catch {
        if ($_.Exception.Message -notmatch 'Expected exactly two snapshot entries') { throw }
        $failed = $true
    }
    if (-not $failed) { throw 'Compare accepted a one-entry snapshot.' }
    Write-Output 'PASS: capture/compare detects unchanged, changed, and incomplete snapshots'
} finally {
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    if (Test-Path -LiteralPath $snapshot) { Remove-Item -LiteralPath $snapshot -Force }
}
