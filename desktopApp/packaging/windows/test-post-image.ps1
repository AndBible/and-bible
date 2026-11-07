$ErrorActionPreference = 'Stop'
$dir = Join-Path ([IO.Path]::GetTempPath()) ('andbible-wsf-' + [guid]::NewGuid())
New-Item -ItemType Directory $dir | Out-Null
try {
    Copy-Item (Join-Path $PSScriptRoot 'jpackage-resources/AndBible-post-image.wsf') $dir
    $fixture = @'
<?xml version="1.0"?><Wix xmlns="http://schemas.microsoft.com/wix/2006/wi"><Fragment><Shortcut Name="AndBible"/><Shortcut Name="AndBible"/><Shortcut Name="Other"/></Fragment></Wix>
'@
    [IO.File]::WriteAllText((Join-Path $dir 'bundle.wxf'), $fixture)
    & cscript.exe //Nologo (Join-Path $dir 'AndBible-post-image.wsf')
    if ($LASTEXITCODE -ne 0) { throw "cscript failed: $LASTEXITCODE" }
    [xml]$xml = Get-Content (Join-Path $dir 'bundle.wxf')
    $ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $ns.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
    $nodes = $xml.SelectNodes('//w:Shortcut[@Name="AndBible"]/w:ShortcutProperty[@Key="System.AppUserModel.ID" and @Value="org.andbible.AndBible"]', $ns)
    $other = $xml.SelectNodes('//w:Shortcut[@Name="Other"]/*', $ns)
    if ($nodes.Count -ne 2 -or $other.Count -ne 0) { throw 'Unexpected shortcut transformation' }
    Write-Output 'Two shortcuts patched; unrelated shortcut untouched.'
} finally {
    Remove-Item -Recurse -Force $dir
}
