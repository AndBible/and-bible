param([Parameter(Mandatory=$true)][string]$Msi)
$ErrorActionPreference = 'Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase((Resolve-Path $Msi).Path, 0)
function Rows([string]$Sql, [int]$Columns = 2) {
    $view = $database.OpenView($Sql)
    $null = $view.Execute()
    while ($record = $view.Fetch()) {
        $fields = @()
        for ($i = 1; $i -le $Columns; $i++) { $fields += $record.StringData($i) }
        [pscustomobject]@{ Fields = $fields }
    }
    $null = $view.Close()
}
function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
# Inspect the compiled registry table: jpackage's non-advertised associations can silently replace generic defaults.
$genericDefaults = @(Rows 'SELECT `Key`, `Name`, `Value` FROM `Registry`' 3 | Where-Object {
    $_.Fields[0] -match '^(Software\\Classes\\)?\.(zip|csv|ttf|epub|jpg|jpeg|png|webp|sqlite3)$' -and
        ($_.Fields[1] -eq '' -or $_.Fields[1] -eq '@')
})
Assert ($genericDefaults.Count -eq 0) 'MSI must not replace default handlers for generic files'
# Advertised associations use MSI's Extension table instead of explicit Registry rows.
$tables = @(Rows 'SELECT `Name` FROM `_Tables`' 1 | ForEach-Object { $_.Fields[0] })
if ('Extension' -in $tables) {
    $genericAdvertised = @(Rows 'SELECT `Extension` FROM `Extension`' 1 | Where-Object {
        $_.Fields[0] -match '^(zip|csv|ttf|epub|jpg|jpeg|png|webp|sqlite3)$'
    })
    Assert ($genericAdvertised.Count -eq 0) 'MSI must not advertise generic file handlers'
}
# Evaluate the compiled MSI conditions, not a second implementation of the UI routing.
$session = $installer.OpenPackage((Resolve-Path $Msi).Path, 1)
$upgrades = @(Rows 'SELECT `ActionProperty`, `VersionMax` FROM `Upgrade`')
Assert (@($upgrades | Where-Object { $_.Fields[0] -eq 'JP_UPGRADABLE_FOUND' -and $_.Fields[1] -eq $session.Property('ProductVersion') }).Count -eq 1) 'The UI must use the actual jpackage upgrade-detection property'
# Exercise AppSearch against a legacy custom-location install without installing a fixture MSI.
$legacyCode = '{' + [guid]::NewGuid().ToString().ToUpperInvariant() + '}'
$legacyKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\' + $legacyCode
try {
    $null = New-Item -Force $legacyKey
    $null = New-ItemProperty $legacyKey -Name InstallLocation -Value 'C:\Custom AndBible\' -PropertyType String
    $session.Property('JP_UPGRADABLE_FOUND') = $legacyCode
    $null = $session.DoAction('AppSearch')
    Assert ($session.Property('ANDBIBLE_PREVIOUS_DIR') -eq 'C:\Custom AndBible\') 'AppSearch must recover the legacy custom installation location'
    $null = $session.DoAction('SetINSTALLDIR')
    Assert ($session.Property('INSTALLDIR') -eq 'C:\Custom AndBible\') 'Update must keep the old custom folder'
} finally {
    Remove-Item -Force $legacyKey -ErrorAction SilentlyContinue
}
$shows = @(Rows 'SELECT `Action`, `Condition` FROM `InstallUISequence`')
function Shown([string]$Dialog) {
    $row = @($shows | Where-Object { $_.Fields[0] -eq $Dialog })
    return $row.Count -eq 1 -and $session.EvaluateCondition($row[0].Fields[1]) -eq 1
}
$session.Property('Installed') = ''
$session.Property('PATCH') = ''
$session.Property('JP_UPGRADABLE_FOUND') = '{11111111-1111-1111-1111-111111111111}'
$session.Property('ANDBIBLE_PREVIOUS_DIR') = 'C:\Custom AndBible\'
Assert (Shown 'AndBibleUpdateDlg') 'An upgrade must start at the one-click update dialog'
Assert (-not (Shown 'WelcomeDlg')) 'An upgrade must not start the first-install wizard'
$session.Property('ANDBIBLE_PREVIOUS_DIR') = ''
Assert (Shown 'WelcomeDlg') 'An upgrade with an unknown location must fall back to the folder wizard'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'An unknown old location must never silently move the installation'
$session.Property('JP_UPGRADABLE_FOUND') = ''
Assert (Shown 'WelcomeDlg') 'A first install must retain the standard welcome wizard'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'A first install must not show the upgrade dialog'
$session.Property('JP_DOWNGRADABLE_FOUND') = '{22222222-2222-2222-2222-222222222222}'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'A newer installed version must not be described as an upgrade'
$session.Property('JP_DOWNGRADABLE_FOUND') = ''
$session.Property('Installed') = '1'
$session.Property('JP_UPGRADABLE_FOUND') = '{11111111-1111-1111-1111-111111111111}'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'Maintenance must not be treated as a major upgrade'
$controls = @(Rows 'SELECT `Control`, `Type`, `Text` FROM `Control` WHERE `Dialog_` = ''AndBibleUpdateDlg''' 3)
$buttons = @($controls | Where-Object { $_.Fields[1] -eq 'PushButton' })
Assert ($buttons.Count -eq 2) 'Upgrade must have only Install and Cancel (no folder/next/back buttons)'
Assert (@($controls | Where-Object { $_.Fields[2].Contains('will be updated to version [ProductVersion]') }).Count -eq 1) 'Upgrade must display the target MSI version'
$events = @(Rows 'SELECT `Control_`, `Event`, `Argument` FROM `ControlEvent` WHERE `Dialog_` = ''AndBibleUpdateDlg''' 3)
Assert (@($events | Where-Object { $_.Fields[0] -eq 'Install' -and $_.Fields[1] -eq 'EndDialog' -and $_.Fields[2] -eq 'Return' }).Count -ge 1) 'Install must start the MSI execute sequence directly'
Assert (@($events | Where-Object { $_.Fields[1] -eq 'NewDialog' }).Count -eq 0) 'Upgrade must not navigate to a folder or confirmation dialog'
$folder = @(Rows 'SELECT `Dialog` FROM `Dialog` WHERE `Dialog` = ''InstallDirDlg''' 1)
Assert ($folder.Count -eq 1) 'First install must keep the folder dialog'
Write-Output 'MSI UI: upgrade/first-install/maintenance routing, version text, direct Install, folder dialog: PASS'
