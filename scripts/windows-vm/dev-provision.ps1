# Provisions the persistent dev overlay (windows-vm.sh dev-provision): the build tools for the W1 Windows build.
# Idempotent. Never run on a throwaway overlay meant for clean test runs: the app must work without a JDK on the PATH,
# so the JDK is installed WITHOUT its PATH and JAVA_HOME features (dev-gradle sets JAVA_HOME per command).
#   - Temurin JDK 17 (MSI, silent), pinned version and SHA-256.
#   - Git for Windows (silent), pinned version and SHA-256, with its Unix tools on the PATH: :app's configuration
#     runs `git`, and :desktopApp's prepareBibleViewBundle runs `sh scripts/copy-bibleview.sh`.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$jdkVersion = '17.0.20.1+1'
$jdkUrl = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.msi'
$jdkSha256 = 'a6013774c5e4a34951e11c1997d8b4641d2de7736d3eac839231f4266bd1116f'
$jdkHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1-hotspot'
$gitUrl = 'https://github.com/git-for-windows/git/releases/download/v2.56.0.windows.4/Git-2.56.0.4-64-bit.exe'
$gitSha256 = '4961a4a8cc778d8b6e00a5819e5fe4d078b754555aff7aedc2d77a5d74c2de80'

function Get-Verified([string]$url, [string]$sha256, [string]$out) {
    curl.exe -fsSL --retry 2 -o $out $url
    if ($LASTEXITCODE -ne 0) { throw "download failed: $url" }
    $actual = (Get-FileHash $out -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $sha256) { Remove-Item $out; throw "SHA-256 mismatch for $url ($actual)" }
}

New-Item -ItemType Directory -Force C:\abtest\dl | Out-Null
if (-not (Test-Path "$jdkHome\bin\jpackage.exe")) {
    Get-Verified $jdkUrl $jdkSha256 C:\abtest\dl\jdk.msi
    $p = Start-Process msiexec.exe -Wait -PassThru -ArgumentList "/i C:\abtest\dl\jdk.msi /qn /norestart ADDLOCAL=FeatureMain INSTALLDIR=`"$jdkHome`""
    if ($p.ExitCode -ne 0) { throw "JDK MSI exit $($p.ExitCode)" }
}
& "$jdkHome\bin\java.exe" -version
if (-not (Test-Path 'C:\Program Files\Git\usr\bin\sh.exe')) {
    Get-Verified $gitUrl $gitSha256 C:\abtest\dl\git.exe
    $p = Start-Process C:\abtest\dl\git.exe -Wait -PassThru -ArgumentList '/VERYSILENT /NORESTART /NOCANCEL /SP- /SUPPRESSMSGBOXES /o:PathOption=CmdTools'
    if ($p.ExitCode -ne 0) { throw "Git installer exit $($p.ExitCode)" }
}
& 'C:\Program Files\Git\cmd\git.exe' --version
Remove-Item -Recurse -Force C:\abtest\dl
"JDK $jdkVersion at $jdkHome; Git at C:\Program Files\Git"
