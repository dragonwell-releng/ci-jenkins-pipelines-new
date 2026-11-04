$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$url = 'https://download.visualstudio.microsoft.com/download/pr/c95450be-0a45-4779-b142-33f567ccfd6e/68978bb28916156802e60e04c5ee50b94348d802fe151a3ca24433f27984187b/vs_BuildTools.exe'
$exe = 'C:\temp\vs1714_buildtools.exe'
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $exe
$signature = Get-AuthenticodeSignature $exe
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'Microsoft installer signature verification failed' }
Write-Output "INSTALLER_SHA256=$((Get-FileHash $exe -Algorithm SHA256).Hash)"
$vs = 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools'
if (!(Test-Path "$vs\VC\Auxiliary\Build\vcvars64.bat")) { throw 'Expected VS instance missing' }
$arguments = 'update --installPath "' + $vs + '" --channelUri https://aka.ms/vs/17/release/channel --quiet --wait --norestart --nocache'
$p = Start-Process -FilePath $exe -ArgumentList $arguments -PassThru -Wait
Write-Output "VS_UPDATE_EXIT=$($p.ExitCode)"
if ($p.ExitCode -notin @(0,3010)) { throw 'VS update failed' }
$where = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
$instances = & $where -products Microsoft.VisualStudio.Product.BuildTools -format json | ConvertFrom-Json
$instance = @($instances | Where-Object { $_.installationPath -eq $vs })
if ($instance.Count -ne 1 -or $instance[0].installationVersion -notmatch '^17\.14\.') { throw 'VS version is not 17.14' }
Write-Output "VS_VERSION=$($instance[0].installationVersion)"
$tool = (Get-Content "$vs\VC\Auxiliary\Build\Microsoft.VCToolsVersion.default.txt" -Raw).Trim()
Write-Output "DEFAULT_MSVC=$tool"
if ($tool -notmatch '^14\.44\.') { throw 'Default MSVC is not 14.44' }
'#include <windows.h>', 'int main() { return 0; }' | Set-Content C:\temp\vs1714-smoke.c -Encoding Ascii
$command = '"' + $vs + '\VC\Auxiliary\Build\vcvars64.bat" && cl.exe /nologo C:\temp\vs1714-smoke.c /Fe:C:\temp\vs1714-smoke.exe && C:\temp\vs1714-smoke.exe'
& cmd.exe /d /s /c $command
if ($LASTEXITCODE -ne 0) { throw 'MSVC Windows SDK compile smoke failed' }
Write-Output 'VS1714_MSVC1444_COMPILE_OK'
