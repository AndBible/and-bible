param([Parameter(Mandatory=$true)][string]$Msi)
$ErrorActionPreference = 'Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase((Resolve-Path $Msi).Path, 0)
function Rows([string]$Sql) {
    $view = $database.OpenView($Sql)
    $null = $view.Execute()
    while ($record = $view.Fetch()) {
        $fields = @()
        for ($i = 1; $i -le $record.FieldCount; $i++) { $fields += $record.StringData($i) }
        [pscustomobject]@{ Fields = $fields }
    }
    $null = $view.Close()
}
function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
# Evaluate the compiled MSI conditions, not a second implementation of the UI routing.
$session = $installer.OpenPackage((Resolve-Path $Msi).Path, 1)
$shows = @(Rows 'SELECT `Action`, `Condition` FROM `InstallUISequence`')
function Shown([string]$Dialog) {
    $row = @($shows | Where-Object { $_.Fields[0] -eq $Dialog })
    return $row.Count -eq 1 -and $session.EvaluateCondition($row[0].Fields[1]) -eq 1
}
$session.Property('Installed') = ''
$session.Property('PATCH') = ''
$session.Property('JP_UPGRADABLE_FOUND') = '{11111111-1111-1111-1111-111111111111}'
Assert (Shown 'AndBibleUpdateDlg') 'An upgrade must start at the one-click update dialog'
Assert (-not (Shown 'WelcomeDlg')) 'An upgrade must not start the first-install wizard'
$session.Property('JP_UPGRADABLE_FOUND') = ''
Assert (Shown 'WelcomeDlg') 'A first install must retain the standard welcome wizard'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'A first install must not show the upgrade dialog'
$session.Property('Installed') = '1'
Assert (-not (Shown 'AndBibleUpdateDlg')) 'Maintenance must not be treated as a major upgrade'
$controls = @(Rows 'SELECT `Control`, `Type`, `Text` FROM `Control` WHERE `Dialog_` = ''AndBibleUpdateDlg''')
$buttons = @($controls | Where-Object { $_.Fields[1] -eq 'PushButton' })
Assert ($buttons.Count -eq 2) 'Upgrade must have only Install and Cancel (no folder/next/back buttons)'
Assert (@($controls | Where-Object { $_.Fields[2].Contains('will be updated to version [ProductVersion]') }).Count -eq 1) 'Upgrade must display the target MSI version'
$events = @(Rows 'SELECT `Control_`, `Event`, `Argument` FROM `ControlEvent` WHERE `Dialog_` = ''AndBibleUpdateDlg''')
Assert (@($events | Where-Object { $_.Fields[0] -eq 'Install' -and $_.Fields[1] -eq 'EndDialog' -and $_.Fields[2] -eq 'Return' }).Count -ge 1) 'Install must start the MSI execute sequence directly'
Assert (@($events | Where-Object { $_.Fields[1] -eq 'NewDialog' }).Count -eq 0) 'Upgrade must not navigate to a folder or confirmation dialog'
$folder = @(Rows 'SELECT `Dialog` FROM `Dialog` WHERE `Dialog` = ''InstallDirDlg''')
Assert ($folder.Count -eq 1) 'First install must keep the folder dialog'
Write-Output 'MSI UI: upgrade/first-install/maintenance routing, version text, direct Install, folder dialog: PASS'
