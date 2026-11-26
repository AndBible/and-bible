param([string]$PolicyScript = (Join-Path $PSScriptRoot 'test-msi-ui.ps1'))
$ErrorActionPreference = 'Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
# Execute the real compiled-MSI validation block against minimal Windows Installer databases.
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $PolicyScript).Path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw $errors[0] }
foreach ($name in @('Rows', 'Assert')) {
    $function = $ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $true)
    . ([scriptblock]::Create($function.Extent.Text))
}
$statements = $ast.EndBlock.Statements
$first = @($statements | Where-Object { $_.Extent.Text -match '^\$(tables|genericDefaults)\s*=' })[0]
$last = @($statements | Where-Object { $_.Extent.Text -match '^\$session\s*=' })[0]
$text = [IO.File]::ReadAllText((Resolve-Path $PolicyScript).Path)
$validation = [scriptblock]::Create($text.Substring($first.Extent.StartOffset, $last.Extent.StartOffset - $first.Extent.StartOffset))
$directory = Join-Path ([IO.Path]::GetTempPath()) ('andbible-msi-policy-' + [guid]::NewGuid())
$null = New-Item -ItemType Directory $directory
function Execute([string]$Sql) {
    $view = $database.OpenView($Sql)
    $null = $view.Execute()
    $null = $view.Close()
    $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)
}
try {
    foreach ($case in @('no-optional-tables', 'open-with', 'generic-default', 'advertised-extension')) {
        $database = $installer.OpenDatabase((Join-Path $directory ($case + '.msi')), 3)
        # Keep a real table even when both optional association tables are absent.
        Execute 'CREATE TABLE `Property` (`Property` CHAR(72) NOT NULL, `Value` CHAR(255) LOCALIZABLE PRIMARY KEY `Property`)'
        if ($case -in @('open-with', 'generic-default')) {
            Execute 'CREATE TABLE `Registry` (`Registry` CHAR(72) NOT NULL, `Root` SHORT NOT NULL, `Key` CHAR(255) NOT NULL, `Name` CHAR(255), `Value` CHAR(255), `Component_` CHAR(72) NOT NULL PRIMARY KEY `Registry`)'
            if ($case -eq 'open-with') {
                Execute 'INSERT INTO `Registry` (`Registry`, `Root`, `Key`, `Name`, `Value`, `Component_`) VALUES (''candidate'', 1, ''Software\Classes\.zip\OpenWithProgids'', ''AndBible.Document'', '''', ''candidate'')'
            } else {
                Execute 'INSERT INTO `Registry` (`Registry`, `Root`, `Key`, `Value`, `Component_`) VALUES (''takeover'', 0, ''.zip'', ''AndBible.Document'', ''takeover'')'
            }
        }
        if ($case -eq 'advertised-extension') {
            Execute 'CREATE TABLE `Extension` (`Extension` CHAR(255) NOT NULL PRIMARY KEY `Extension`)'
            Execute 'INSERT INTO `Extension` (`Extension`) VALUES (''zip'')'
        }
        $rejection = $null
        try { . $validation } catch { $rejection = $_.Exception.Message }
        switch ($case) {
            'generic-default' { Assert ($rejection -eq 'MSI must not replace default handlers for generic files') "$case must reject takeover: $rejection" }
            'advertised-extension' { Assert ($rejection -eq 'MSI must not advertise generic file handlers') "$case must reject takeover: $rejection" }
            default { Assert ($null -eq $rejection) "$case must be accepted: $rejection" }
        }
        Write-Output "MSI association fixture ${case}: PASS"
        $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($database)
        $database = $null
    }
} finally {
    if ($database) { $null = [Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
    Remove-Item -Recurse -Force $directory
}
