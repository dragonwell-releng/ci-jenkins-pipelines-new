$ErrorActionPreference = 'Stop'
$perl = 'C:\cygwin64\bin\perl.exe'
$modules = @('-MEncode', '-MMIME::Base64', '-MList::Util', '-MText::Wrap', '-MTime::Local', '-MDigest::SHA', '-MGetopt::Std', '-e', '1')
& $perl @modules
if ($LASTEXITCODE -eq 0) {
    Write-Output 'Cygwin Perl modules already available'
    exit 0
}
# Repair the version already used by the validated image. Do not upgrade the toolchain.
$installed = Get-Content 'C:\cygwin64\etc\setup\installed.db'
$installed | Where-Object { $_ -match '^perl' } | Write-Output
if (-not ($installed -match '^perl perl-5\.44\.0-1\.tar')) {
    throw 'Unexpected Cygwin Perl version; review package selection before repairing'
}
$setup = 'C:\temp\cygwin.exe'
if ((Get-FileHash $setup -Algorithm SHA256).Hash.ToLower() -ne '2c9f2fb56e1fb687b5d9680afa8f8b06e6214f0e483096af0eae1946431226c5') {
    throw 'Cygwin installer differs from the pinned upstream installer'
}
$setupArgs = '--packages perl_base=5.44.0-1 --quiet-mode --download --local-install --site https://mirror-hk.koddos.net/cygwin/ --local-package-dir C:\cygwin_packages --root C:\cygwin64'
$process = Start-Process -Wait -PassThru -FilePath $setup -ArgumentList $setupArgs
if ($process.ExitCode -ne 0) { throw "Cygwin setup failed: $($process.ExitCode)" }
& $perl @modules
if ($LASTEXITCODE -ne 0) { throw 'Required Cygwin Perl modules remain unavailable' }
Write-Output 'CYGWIN_CACERTS_PERL_MODULES_OK'
