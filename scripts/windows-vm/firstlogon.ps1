$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force C:\abtest | Out-Null
Start-Transcript -Path C:\abtest\firstlogon.log -Force
try {
    $volume = Get-Volume -FileSystemLabel ABANSWER
    $key = "$($volume.DriveLetter):\authorized_key.pub"
    $cap = Get-WindowsCapability -Online -Name OpenSSH.Server*
    if ($cap.State -ne 'Installed') { Add-WindowsCapability -Online -Name $cap.Name | Out-Null }
    Set-Service sshd -StartupType Automatic
    New-Item -Path 'HKLM:\SOFTWARE\OpenSSH' -Force | Out-Null
    New-ItemProperty -Path 'HKLM:\SOFTWARE\OpenSSH' -Name DefaultShell -Value "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -PropertyType String -Force | Out-Null
    $authorized = "$env:ProgramData\ssh\administrators_authorized_keys"
    New-Item -ItemType Directory -Force (Split-Path $authorized) | Out-Null
    Copy-Item $key $authorized -Force
    & icacls.exe $authorized /inheritance:r /grant '*S-1-5-32-544:F' /grant '*S-1-5-18:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to set SSH key ACL' }
    if (-not (Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue)) { New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null }
    Start-Service sshd
    & powercfg.exe /change standby-timeout-ac 0
    & powercfg.exe /change hibernate-timeout-ac 0
    $wu = 'HKLM:\SOFTWARE\Microsoft\WindowsUpdate\UX\Settings'
    New-Item -Path $wu -Force | Out-Null
    $now = [DateTime]::UtcNow
    foreach ($name in @('PauseUpdatesStartTime','PauseFeatureUpdatesStartTime','PauseQualityUpdatesStartTime')) { Set-ItemProperty $wu $name ($now.ToString('yyyy-MM-ddTHH:mm:ssZ')) }
    foreach ($name in @('PauseUpdatesExpiryTime','PauseFeatureUpdatesEndTime','PauseQualityUpdatesEndTime')) { Set-ItemProperty $wu $name ($now.AddDays(35).ToString('yyyy-MM-ddTHH:mm:ssZ')) }
    New-ItemProperty $wu -Name ActiveHoursStart -Value 8 -PropertyType DWord -Force | Out-Null
    New-ItemProperty $wu -Name ActiveHoursEnd -Value 23 -PropertyType DWord -Force | Out-Null
    $policy = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate'
    New-Item -Path $policy -Force | Out-Null
    New-ItemProperty $policy -Name DeferFeatureUpdates -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty $policy -Name DeferFeatureUpdatesPeriodInDays -Value 365 -PropertyType DWord -Force | Out-Null
    $winlogon = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon'
    New-ItemProperty $winlogon -Name AutoLogonCount -Value 0 -PropertyType DWord -Force | Out-Null
    Set-ItemProperty $winlogon -Name AutoAdminLogon -Value '0'
    Remove-ItemProperty $winlogon -Name DefaultPassword -ErrorAction SilentlyContinue
    Set-Content -Path C:\abtest\ready.txt -Value 'ready'
} finally { Stop-Transcript }
