/*
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE in the repository root.
 *
 * Standalone Dragonwell image-maintenance job, independent of JDK job generation.
 */
def publishImage = params.PUBLISH_WINDOWS_IMAGE != false
def registryCredential = params.DOCKERHUB_CREDENTIAL_ID ?: 'dockerhub-alibabadragonwelljdk'
def checkFrequency = params.IMAGE_CHECK_FREQUENCY ?: 'daily'
def schedules = [daily: 'TZ=Asia/Shanghai\nH 3 * * *', weekly: 'TZ=Asia/Shanghai\nH 3 * * 0']
if (!schedules.containsKey(checkFrequency)) {
    error('IMAGE_CHECK_FREQUENCY must be daily or weekly')
}
def scheduleEnabled = params.ENABLE_IMAGE_SCHEDULE == null || params.ENABLE_IMAGE_SCHEDULE
def imageNode = params.WINDOWS_IMAGE_NODE ?: '"windows_2022rc_build4(47.76.90.84)"'
if (imageNode == 'windows_2022rc_build4(47.76.90.84)') {
    imageNode = '"windows_2022rc_build4(47.76.90.84)"'
}

properties([
    disableConcurrentBuilds(),
    buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30')),
    pipelineTriggers(scheduleEnabled ? [cron(schedules[checkFrequency])] : []),
    parameters([
        booleanParam(name: 'ENABLE_IMAGE_SCHEDULE', defaultValue: scheduleEnabled,
                     description: 'Check upstream automatically using the selected frequency'),
        choice(name: 'IMAGE_CHECK_FREQUENCY', choices: [checkFrequency] + ['daily', 'weekly'].findAll { it != checkFrequency },
               description: 'Check around 03:00 Asia/Shanghai daily or on Sunday'),
        string(name: 'DOWNLOAD_NODE', defaultValue: params.DOWNLOAD_NODE ?: 'linux_dockerBuild',
               description: 'Linux agent with Git, curl and Python 3 for verified upstream downloads'),
        string(name: 'WINDOWS_IMAGE_NODE', defaultValue: imageNode,
               description: 'Windows Server 2022 agent with Windows Docker Engine and Git in the agent PATH'),
        string(name: 'WINDOWS_IMAGE_REPOSITORY',
               defaultValue: params.WINDOWS_IMAGE_REPOSITORY ?: 'docker.io/alibabadragonwelljdk/windows2022_build_image',
               description: 'Image repository in the alibabadragonwelljdk namespace'),
        booleanParam(name: 'PUBLISH_WINDOWS_IMAGE', defaultValue: publishImage,
                     description: 'Push validated versioned and latest tags using the configured credential'),
        string(name: 'DOCKERHUB_CREDENTIAL_ID', defaultValue: registryCredential,
               description: 'Jenkins username/password credential holding a Docker Hub write token'),
        booleanParam(name: 'FORCE_IMAGE_BUILD', defaultValue: false,
                     description: 'Rebuild even when a validated local image already exists'),
        string(name: 'CYGWIN_PACKAGE_CACHE', defaultValue: params.CYGWIN_PACKAGE_CACHE ?: '',
               description: 'Optional agent-local package ZIP; otherwise downloaded on the host and verified against upstream'),
        string(name: 'CYGWIN_MIRROR', defaultValue: params.CYGWIN_MIRROR ?: '',
               description: 'Optional HTTPS mirror from cygwin.com/mirrors.html; empty preserves upstream'),
        string(name: 'NASM_PACKAGE_CACHE', defaultValue: params.NASM_PACKAGE_CACHE ?: '',
               description: 'Optional agent-local NASM ZIP; must match the upstream role SHA-256'),
        string(name: 'CLANG_PACKAGE_CACHE', defaultValue: params.CLANG_PACKAGE_CACHE ?: '',
               description: 'Optional agent-local directory containing upstream Clang win32/win64 ZIPs, verified by SHA-256'),
        string(name: 'SEVENZIP_INSTALLER_CACHE', defaultValue: params.SEVENZIP_INSTALLER_CACHE ?: '',
               description: 'Optional agent-local 7-Zip installer, verified against the upstream SHA-256')
    ])
])

def imageRepository = params.WINDOWS_IMAGE_REPOSITORY ?: 'docker.io/alibabadragonwelljdk/windows2022_build_image'
if (!(imageRepository ==~ /docker\.io\/alibabadragonwelljdk\/[a-z0-9]+(?:[._-][a-z0-9]+)*/)) {
    error('WINDOWS_IMAGE_REPOSITORY must be a repository under docker.io/alibabadragonwelljdk, without tag/digest')
}
if (publishImage && !registryCredential.trim()) {
    error('DOCKERHUB_CREDENTIAL_ID is required for publishing')
}

stage('Inspect upstream and download bootstrap') {
    timeout(time: 15, unit: 'MINUTES') {
        node(params.DOWNLOAD_NODE ?: 'linux_dockerBuild') {
            def installerCache = "${env.WORKSPACE}@downloads"
            ws("${env.WORKSPACE}@inspect-${env.BUILD_NUMBER}") {
                try {
                    withEnv(["SYNC_INSTALLER_CACHE=${installerCache}"]) {
                        sh(script: '''
set -eu
revision=$(git ls-remote https://github.com/adoptium/infrastructure.git refs/heads/master | cut -f1)
export INSPECTED_REVISION="$revision"
python3 - <<'PYTHON'
import os, re
assert re.fullmatch(r'[0-9a-f]{40}', os.environ['INSPECTED_REVISION']), 'Invalid upstream revision'
PYTHON
curl --fail --location --silent --show-error --retry 3 --connect-timeout 15 --max-time 120 \\
    --output Dockerfile.upstream "https://raw.githubusercontent.com/adoptium/infrastructure/$revision/ansible/docker/Dockerfile.win2022"
python3 - <<'PYTHON'
import json, os, re
from pathlib import Path
text = Path('Dockerfile.upstream').read_text()
match = re.search(r"# Download Cygwin Bootstrapper & Verify Its Checksum.*?[$]expectedChecksum = '([0-9a-f]{64})';", text, re.S)
assert match, 'Missing upstream Cygwin checksum'
Path('inspection.json').write_text(json.dumps({'revision': os.environ['INSPECTED_REVISION'], 'cygwinInstallerSha256': match.group(1)}))
PYTHON
checksum=$(python3 -c 'import json; print(json.load(open("inspection.json"))["cygwinInstallerSha256"])')
mkdir -p "$SYNC_INSTALLER_CACHE"
cache="$SYNC_INSTALLER_CACHE/cygwin-$checksum.exe"
if [ -f "$cache" ] && printf '%s  %s\\n' "$checksum" "$cache" | sha256sum --check --status; then
    echo "Reuse verified Cygwin bootstrap $checksum"
else
    curl --fail --location --silent --show-error --retry 3 --connect-timeout 15 --max-time 120 \\
        --output "$cache.part" https://cygwin.com/setup-x86_64.exe
    printf '%s  %s\\n' "$checksum" "$cache.part" | sha256sum --check --status
    mv "$cache.part" "$cache"
fi
cp "$cache" setup-x86_64.exe
''')
                    }
                    stash name: 'windows-image-bootstrap', includes: 'inspection.json,setup-x86_64.exe'
                } finally { deleteDir() }
            }
        }
    }
}

timeout(time: 12, unit: 'HOURS') {
    node(imageNode) {
        if (isUnix()) {
            error('This job requires a Windows Server 2022 Docker host')
        }
        def downloadCache = "${env.WORKSPACE}@downloads"
        // Dedicated workspace also isolates simultaneous invocations of this job.
        ws("${env.WORKSPACE}@image-${env.BUILD_NUMBER}") {
            unstash 'windows-image-bootstrap'
            withEnv(["SYNC_IMAGE_REPOSITORY=${imageRepository}",
                     "SYNC_DOWNLOAD_CACHE=${downloadCache}",
                     "SYNC_PACKAGE_CACHE=${params.CYGWIN_PACKAGE_CACHE ?: ''}",
                     "SYNC_CYGWIN_MIRROR=${params.CYGWIN_MIRROR ?: ''}",
                     "SYNC_NASM_CACHE=${params.NASM_PACKAGE_CACHE ?: ''}",
                     "SYNC_CLANG_CACHE=${params.CLANG_PACKAGE_CACHE ?: ''}",
                     "SYNC_SEVENZIP_CACHE=${params.SEVENZIP_INSTALLER_CACHE ?: ''}",
                     "SYNC_FORCE_BUILD=${params.FORCE_IMAGE_BUILD ?: false}"]) {
                try {
                    stage('Check upstream and build Windows image') {
                        powershell(script: '''
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Invoke-Native {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}

$upstream = 'https://github.com/adoptium/infrastructure.git'
$recipeVersion = 'windows-sync-v11' + $(if ($env:SYNC_NASM_CACHE) { '-nasm-cache' } else { '' }) + $(if ($env:SYNC_CLANG_CACHE) { '-clang-cache' } else { '' }) + $(if ($env:SYNC_SEVENZIP_CACHE) { '-7zip-cache' } else { '' })
$inspection = Get-Content -Raw inspection.json | ConvertFrom-Json
$revision = $inspection.revision
if ($revision -notmatch '^[0-9a-f]{40}$') { throw 'Invalid upstream revision' }
Invoke-Native git @('init', 'upstream')
Invoke-Native git @('-C', 'upstream', 'remote', 'add', 'origin', $upstream)
Invoke-Native git @('-C', 'upstream', 'fetch', '--depth', '1', 'origin', $revision)
Invoke-Native git @('-C', 'upstream', 'checkout', '--detach', 'FETCH_HEAD')
if ((Invoke-Native git @('-C', 'upstream', 'rev-parse', 'HEAD')).Trim() -ne $revision) {
    throw 'Windows checkout differs from inspected upstream revision'
}

# Include the Windows playbook and shared Ansible dependencies alongside its Dockerfile.
$trackedPaths = @('ansible/docker/Dockerfile.win2022',
    'ansible/playbooks/AdoptOpenJDK_Windows_Playbook', 'ansible/ansible.cfg',
    'ansible/group_vars', 'ansible/roles', 'ansible/plugins',
    'ansible/requirements.yml', 'collections', 'requirements.txt')
$inputs = @(Invoke-Native git (@('-C', 'upstream', 'ls-tree', '-r', 'HEAD', '--') + $trackedPaths))
if (-not ($inputs -match '\\tansible/docker/Dockerfile[.]win2022$')) { throw 'Upstream Windows Dockerfile missing' }
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes($recipeVersion + "`n" + $env:SYNC_CYGWIN_MIRROR + "`n" + ($inputs -join "`n"))
    $key = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLower()
} finally { $sha.Dispose() }
$image = $env:SYNC_IMAGE_REPOSITORY + ':inputs-' + $key
$latest = $env:SYNC_IMAGE_REPOSITORY + ':latest'
$result = [ordered]@{ upstream = $upstream; revision = $revision; inputs = $inputs;
    recipe = $recipeVersion; fingerprint = $key; image = $image; latest = $latest; status = 'checking' }
$result | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 result.json

$os = (Invoke-Native docker @('info', '--format', '{{.OSType}}')).Trim()
if ($os -ne 'windows') { throw 'Docker daemon is not in Windows-container mode' }

# Only promote this key after smoke validation. An existing validated local image
# is the success marker; losing local Docker state safely causes a rebuild.
$existingId = @(Invoke-Native docker @('image', 'ls', '--quiet', '--no-trunc', $image))
$found = $existingId.Count -gt 0
if ($found -and $env:SYNC_FORCE_BUILD -ne 'true') {
    $existing = Invoke-Native docker @('image', 'inspect', $image)
    $metadata = @($existing | ConvertFrom-Json)[0]
    if ($metadata.Config.Labels.'io.dragonwell.sync-inputs' -ne $key) {
        throw 'Existing image has unexpected provenance'
    }
    # Revalidate required Cygwin modules even for an existing image fingerprint.
    Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint',
        'C:/cygwin64/bin/perl.exe', $image, '-MEncode', '-MMIME::Base64',
        '-MList::Util', '-MText::Wrap', '-MTime::Local', '-MDigest::SHA', '-MGetopt::Std', '-e', '1')
    Invoke-Native docker @('tag', $image, $latest)
    $result.status = 'unchanged'
    $result.imageId = $metadata.Id
    $result.imageRevision = $metadata.Config.Labels.'org.opencontainers.image.revision'
    $result | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 result.json
    Write-Output "No input changes; reuse $image"
    exit 0
}

$drive = (Get-Item .).PSDrive
Write-Output ('WORKSPACE_DRIVE=' + $drive.Root + ' FREE_GIB=' + [math]::Round($drive.Free / 1GB, 2))
Invoke-Native docker @('info', '--format', 'DOCKER_ROOT={{.DockerRootDir}}')
if ($drive.Free -lt 100GB) { throw 'At least 100 GiB free workspace disk space is required' }
New-Item -ItemType Directory context | Out-Null
$dockerfile = [IO.File]::ReadAllText((Join-Path $PWD 'upstream/ansible/docker/Dockerfile.win2022'))
[IO.File]::WriteAllText((Join-Path $PWD 'Dockerfile.upstream'), $dockerfile)

# The upstream Dockerfile clones master INSIDE the image. Pin that second
# checkout to the exact revision we inspected; fail closed if its syntax changes.
$clone = 'C:\\cygwin64\\bin\\git clone https://github.com/adoptium/infrastructure -b master c:/infrastructure'
if ([regex]::Matches($dockerfile, [regex]::Escape($clone)).Count -ne 1) {
    throw 'Upstream clone instruction changed; review revision pinning before rebuilding'
}
$pinned = $clone + ' && C:\\cygwin64\\bin\\git -C c:/infrastructure checkout --detach ' + $revision
$dockerfile = $dockerfile.Replace($clone, $pinned)
# Download on the host: container transfers have repeatedly been truncated.
# Derive the URL and checksum from the inspected upstream Dockerfile.
$cacheSpec = [regex]::Matches($dockerfile, '(?s)[$]url = ''(https://ci[.]adoptium[.]net/userContent/cygwin/[a-zA-Z0-9_.-]+[.]zip)'';.*?[$]expectedChecksum = ''([0-9a-f]{64})'';')
if ($cacheSpec.Count -ne 1) { throw 'Upstream package-cache specification changed' }
$cacheUrl = $cacheSpec[0].Groups[1].Value
$cacheHash = $cacheSpec[0].Groups[2].Value
$cachePath = Join-Path $PWD 'context/cygwin_cache.zip'
if ($env:SYNC_PACKAGE_CACHE) {
    Copy-Item -LiteralPath $env:SYNC_PACKAGE_CACHE -Destination $cachePath
} else {
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        & curl.exe --fail --location --silent --show-error --connect-timeout 30 --max-time 900 --continue-at - --output $cachePath $cacheUrl
        $downloadExit = $LASTEXITCODE
        if ($downloadExit -eq 0) { break }
        if ($downloadExit -notin @(18, 28, 56) -or $attempt -eq 5) {
            throw "Package-cache download failed: curl exit $downloadExit"
        }
        Write-Output "Resuming incomplete package-cache download, attempt $($attempt + 1)"
    }
}
if ((Get-FileHash $cachePath -Algorithm SHA256).Hash.ToLower() -ne $cacheHash) {
    throw 'Package-cache checksum differs from upstream'
}
$result.cygwinPackageUrl = $cacheUrl
$result.cygwinPackageSha256 = $cacheHash
$cacheDownload = 'Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing; '
$cacheMarker = '# Download Cygwin Package Cache & Verify Checksum'
if ([regex]::Matches($dockerfile, [regex]::Escape($cacheDownload)).Count -ne 1 -or
    [regex]::Matches($dockerfile, [regex]::Escape($cacheMarker)).Count -ne 1) {
    throw 'Upstream package-cache download changed; review transport adaptation'
}
$dockerfile = $dockerfile.Replace($cacheDownload, '').Replace($cacheMarker,
    "COPY cygwin_cache.zip C:/temp/cygwin_cache.zip`n" + $cacheMarker)
# Keep installer bytes in a checksum-keyed host cache across build workspaces.
$bootstrap = [regex]::Matches($dockerfile, "(?s)# Download Cygwin Bootstrapper & Verify Its Checksum.*?[$]expectedChecksum = '([0-9a-f]{64})';")
if ($bootstrap.Count -ne 1) { throw 'Cannot identify upstream Cygwin installer checksum' }
$expected = $bootstrap[0].Groups[1].Value
$installer = Join-Path $env:SYNC_DOWNLOAD_CACHE ('cygwin-' + $expected + '.exe')
New-Item -ItemType Directory -Force -Path $env:SYNC_DOWNLOAD_CACHE | Out-Null
if ($inspection.cygwinInstallerSha256 -ne $expected) { throw 'Inspection checksum differs from Dockerfile' }
$cached = (Test-Path -LiteralPath $installer) -and ((Get-FileHash $installer -Algorithm SHA256).Hash.ToLower() -eq $expected)
if (!$cached) {
    if ((Get-FileHash setup-x86_64.exe -Algorithm SHA256).Hash.ToLower() -ne $expected) {
        throw 'Stashed Cygwin installer differs from upstream checksum'
    }
    Copy-Item -LiteralPath setup-x86_64.exe -Destination $installer -Force
}
Copy-Item -LiteralPath $installer -Destination context/setup-x86_64.exe
$download = 'wget -UseBasicParsing https://cygwin.com/setup-x86_64.exe -OutFile setup-x86_64.exe; '
$marker = '# Download Cygwin Bootstrapper & Verify Its Checksum'
foreach ($anchor in @($download, $marker, 'ENV TERM=dumb', 'sed -i -e')) {
    if ([regex]::Matches($dockerfile, [regex]::Escape($anchor)).Count -ne 1) {
        throw 'Unsupported upstream Cygwin installer instruction'
    }
}
$dockerfile = $dockerfile.Replace($download, '').Replace($marker,
    "COPY setup-x86_64.exe C:/setup-x86_64.exe`n" + $marker)
$dockerfile = $dockerfile.Replace('ENV TERM=dumb',
    "COPY setup-x86_64.exe C:/temp/cygwin.exe`nCOPY prepare-cygwin-cache.ps1 C:/prepare-cygwin-cache.ps1`nENV TERM=dumb")
$prepare = @'
$ErrorActionPreference = 'Stop'
$expected = '@@CYGWIN_CHECKSUM@@'
if ((Get-FileHash C:/temp/cygwin.exe -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw 'Cygwin cache hash mismatch' }
$path = 'C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/cygwin/tasks/main.yml'
$source = [IO.File]::ReadAllText($path)
$start = $source.IndexOf('- name: Retrieve Cygwin setup')
if ($start -lt 0) { throw 'Upstream Cygwin download task missing' }
$end = $source.IndexOf('- name:', $start + 1)
if ($end -lt 0) { throw 'Upstream Cygwin task boundary missing' }
$task = $source.Substring($start, $end - $start)
if (-not $task.Contains($expected) -or -not $task.Contains('https://cygwin.com/setup-x86_64.exe')) {
    throw 'Upstream Cygwin checksum or URL differs from inspected Dockerfile'
}
$condition = [regex]::Match($task, '(?m)^  when: (.+)$')
$cacheCondition = "(dragonwell_cygwin_cache.stat.checksum | default('')) != '$expected'"
if ($condition.Success) {
    $task = $task.Replace($condition.Value, '  when: (' + $condition.Groups[1].Value.Trim() + ') and ' + $cacheCondition)
} elseif ($task.Contains('when:')) {
    throw 'Unsupported multiline Cygwin download condition'
} else { $task = $task.TrimEnd() + "`n  when: $cacheCondition`n`n" }
$guard = @"
- name: Verify preseeded Cygwin installer cache
  win_stat:
    path: 'C:/temp/cygwin.exe'
    get_checksum: yes
    checksum_algorithm: sha256
  register: dragonwell_cygwin_cache
  tags: cygwin

"@
[IO.File]::WriteAllText($path, $source.Substring(0,$start)+$guard+"`n"+$task+$source.Substring($end), (New-Object Text.UTF8Encoding($false)))
Write-Output 'ANSIBLE_DOWNLOAD_CACHE_GUARD_APPLIED'
'@
$prepare = $prepare.Replace('@@CYGWIN_CHECKSUM@@', $expected)
[IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-cygwin-cache.ps1'), $prepare, (New-Object Text.UTF8Encoding($false)))
$dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-cygwin-cache.ps1 && sed -i -e')
$result.cygwinInstallerSha256 = $expected
if ($env:SYNC_NASM_CACHE) {
    $role = Get-Content -Raw 'upstream/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/nasm/tasks/main.yml'
    $expected = 'b3a1f896b53d07854884c2e0d6be7defba7ebd09b864bbb9e6d69ada1c3e989f'
    # Ansible 2.8 win_get_url still contacts the URL for preseeded files.
    # Skip only the download when an independent stat verifies upstream bytes.
    foreach ($anchor in @('https://www.nasm.us/pub/nasm/releasebuilds/2.13.03/win64/nasm-2.13.03-win64.zip',
                          'dest: C:\\temp\\nasm-2.13.03.zip', "checksum: $expected", 'checksum_algorithm: sha256')) {
        if (-not $role.Contains($anchor)) { throw 'Upstream NASM role changed; review cache adaptation' }
    }
    if ([regex]::Matches($dockerfile, 'ENV TERM=dumb').Count -ne 1) { throw 'Upstream NASM cache insertion point changed' }
    Copy-Item -LiteralPath $env:SYNC_NASM_CACHE -Destination context/nasm.zip
    if ((Get-FileHash context/nasm.zip -Algorithm SHA256).Hash.ToLower() -ne $expected) {
        throw 'NASM cache differs from upstream checksum'
    }
    $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY nasm.zip C:/temp/nasm-2.13.03.zip`nENV TERM=dumb")
    $prepareNasm = @'
$ErrorActionPreference = 'Stop'
$expected = 'b3a1f896b53d07854884c2e0d6be7defba7ebd09b864bbb9e6d69ada1c3e989f'
$path = 'C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/nasm/tasks/main.yml'
$source = [IO.File]::ReadAllText($path)
$start = $source.IndexOf('- name: Download nasm')
if ($start -lt 0) { throw 'Upstream NASM download task changed' }
$end = $source.IndexOf('- name:', $start + 1)
if ($end -lt 0) { throw 'Upstream NASM task boundary changed' }
$task = $source.Substring($start, $end - $start)
$condition = 'when: (not nasm_installed.stat.exists)'
if (-not $task.Contains($condition) -or -not $task.Contains($expected)) { throw 'Upstream NASM cache guard changed' }
$guard = @"
- name: Verify preseeded NASM package cache
  win_stat:
    path: 'C:/temp/nasm-2.13.03.zip'
    get_checksum: yes
    checksum_algorithm: sha256
  register: dragonwell_nasm_cache
  tags: nasm

"@
$replacement = $guard + "`n" + $task.Replace($condition, $condition + " and (dragonwell_nasm_cache.stat.checksum | default('')) != '$expected'")
[IO.File]::WriteAllText($path, $source.Substring(0,$start)+$replacement+$source.Substring($end), (New-Object Text.UTF8Encoding($false)))
'@
    [IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-nasm-cache.ps1'), $prepareNasm,
        (New-Object Text.UTF8Encoding($false)))
    if ([regex]::Matches($dockerfile, 'sed -i -e').Count -ne 1) { throw 'Upstream NASM provisioning anchor changed' }
    $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY prepare-nasm-cache.ps1 C:/prepare-nasm-cache.ps1`nENV TERM=dumb")
    $dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-nasm-cache.ps1 && sed -i -e')
    $result.nasmPackageSha256 = $expected
}
if ($env:SYNC_SEVENZIP_CACHE) {
    $role = Get-Content -Raw 'upstream/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/7-Zip/tasks/main.yml'
    $expected = 'b055fee85472921575071464a97a79540e489c1c3a14b9bdfbdbab60e17f36e4'
    foreach ($anchor in @('https://www.7-zip.org/a/7z2201-x64.exe', "dest: 'C:\\temp\\7z.exe'", "checksum: $expected", 'checksum_algorithm: sha256')) {
        if (-not $role.Contains($anchor)) { throw 'Upstream 7-Zip role changed; review cache adaptation' }
    }
    foreach ($anchor in @('ENV TERM=dumb', 'sed -i -e')) {
        if ([regex]::Matches($dockerfile, [regex]::Escape($anchor)).Count -ne 1) { throw 'Upstream 7-Zip insertion point changed' }
    }
    Copy-Item -LiteralPath $env:SYNC_SEVENZIP_CACHE -Destination context/7z.exe
    if ((Get-FileHash context/7z.exe -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw '7-Zip cache differs from upstream checksum' }
    $prepareZip = @'
$ErrorActionPreference = 'Stop'
$expected = 'b055fee85472921575071464a97a79540e489c1c3a14b9bdfbdbab60e17f36e4'
$path = 'C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/7-Zip/tasks/main.yml'
$source = [IO.File]::ReadAllText($path)
$matches = [regex]::Matches($source, '(?m)^([ ]*)- name: Download 7-Zip [(]required to unpack MinGW-W64[)]')
if ($matches.Count -ne 1) { throw 'Upstream 7-Zip download task changed' }
$indent = $matches[0].Groups[1].Value
$start = $matches[0].Index
$end = $source.IndexOf($indent + '- name: Install 7-Zip', $start + 1)
if ($end -lt 0) { throw 'Upstream 7-Zip task boundary changed' }
$task = $source.Substring($start, $end - $start)
$module = $indent + '  win_get_url:'
if ([regex]::Matches($task, [regex]::Escape($module)).Count -ne 1 -or $task.Contains('when:') -or -not $task.Contains("checksum: $expected")) { throw 'Upstream 7-Zip task structure changed' }
$guard = @('- name: Verify preseeded 7-Zip installer cache', '  win_stat:', "    path: 'C:/temp/7z.exe'", '    get_checksum: yes', '    checksum_algorithm: sha256', '  register: dragonwell_7zip_cache', '') | ForEach-Object { $indent + $_ }
$replacement = ($guard -join "`n") + "`n" + $task.Replace($module, $indent + "  when: (dragonwell_7zip_cache.stat.checksum | default('')) != '$expected'`n" + $module)
[IO.File]::WriteAllText($path, $source.Substring(0,$start)+$replacement+$source.Substring($end), (New-Object Text.UTF8Encoding($false)))
'@
    [IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-7zip-cache.ps1'), $prepareZip, (New-Object Text.UTF8Encoding($false)))
    $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY 7z.exe C:/temp/7z.exe`nCOPY prepare-7zip-cache.ps1 C:/prepare-7zip-cache.ps1`nENV TERM=dumb")
    $dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-7zip-cache.ps1 && sed -i -e')
    $result.sevenZipInstallerSha256 = $expected
}
if ($env:SYNC_CLANG_CACHE) {
    foreach ($anchor in @('ENV TERM=dumb', 'sed -i -e')) {
        if ([regex]::Matches($dockerfile, [regex]::Escape($anchor)).Count -ne 1) { throw 'Upstream Clang insertion point changed' }
    }
    foreach ($bit in @('64', '32')) {
        $role = Get-Content -Raw "upstream/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/Clang_${bit}bit/tasks/main.yml"
        $expected = if ($bit -eq '64') { 'e61c3d115778dbb54f91cec8ca7682cbad601d61e2c40a9a97e554aa1f1bb2b6' } else { '89dcd42d240b3e7ef3a5487855e224f2f90a23a2a194dbadd8c427c2dd59df79' }
        $file = "llvm-7.0.0-win$bit.zip"
        foreach ($anchor in @("https://ci.adoptium.net/userContent/winansible/$file", "checksum: $expected", 'checksum_algorithm: sha256', "dest: 'C:\\temp\\'")) {
            if (-not $role.Contains($anchor)) { throw 'Upstream Clang role changed; review cache adaptation' }
        }
        Copy-Item -LiteralPath (Join-Path $env:SYNC_CLANG_CACHE $file) -Destination (Join-Path 'context' $file)
        if ((Get-FileHash (Join-Path 'context' $file) -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw 'Clang cache differs from upstream checksum' }
        $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY $file C:/temp/$file`nENV TERM=dumb")
        $result["clang${bit}PackageSha256"] = $expected
    }
    $prepareClang = @'
$ErrorActionPreference = 'Stop'
foreach ($bit in @('64', '32')) {
    $expected = if ($bit -eq '64') { 'e61c3d115778dbb54f91cec8ca7682cbad601d61e2c40a9a97e554aa1f1bb2b6' } else { '89dcd42d240b3e7ef3a5487855e224f2f90a23a2a194dbadd8c427c2dd59df79' }
    $path = "C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/Clang_${bit}bit/tasks/main.yml"
    $source = [IO.File]::ReadAllText($path)
    $marker = "- name: Download Clang ${bit}bit"
    if ([regex]::Matches($source, [regex]::Escape($marker)).Count -ne 1) { throw 'Upstream Clang download task changed' }
    $start = $source.IndexOf($marker)
    $end = $source.IndexOf('- name:', $start + 1)
    if ($end -lt 0) { throw 'Upstream Clang task boundary changed' }
    $task = $source.Substring($start, $end - $start)
    $condition = "when: not clang_${bit}bit_installed.stat.exists"
    if ([regex]::Matches($task, [regex]::Escape($condition)).Count -ne 1 -or -not $task.Contains("checksum: $expected")) { throw 'Upstream Clang download condition changed' }
    $guard = @"
- name: Verify preseeded Clang ${bit}bit package cache
  win_stat:
    path: 'C:/temp/llvm-7.0.0-win$bit.zip'
    get_checksum: yes
    checksum_algorithm: sha256
  register: dragonwell_clang${bit}_cache
  tags: clang_${bit}bit

"@
    $replacement = $guard + "`n" + $task.Replace($condition, $condition + " and (dragonwell_clang${bit}_cache.stat.checksum | default('')) != '$expected'")
    [IO.File]::WriteAllText($path, $source.Substring(0,$start)+$replacement+$source.Substring($end), (New-Object Text.UTF8Encoding($false)))
}
'@
    [IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-clang-cache.ps1'), $prepareClang, (New-Object Text.UTF8Encoding($false)))
    $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY prepare-clang-cache.ps1 C:/prepare-clang-cache.ps1`nENV TERM=dumb")
    $dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-clang-cache.ps1 && sed -i -e')
}
if ($env:SYNC_CYGWIN_MIRROR) {
    if ($env:SYNC_CYGWIN_MIRROR -notmatch '^https://[a-zA-Z0-9.-]+/[a-zA-Z0-9/_-]+/$') {
        throw 'CYGWIN_MIRROR must be a simple HTTPS mirror directory URL with a trailing slash'
    }
    foreach ($anchor in @('ENV TERM=dumb', 'sed -i -e')) {
        if ([regex]::Matches($dockerfile, [regex]::Escape($anchor)).Count -ne 1) { throw 'Upstream Cygwin mirror insertion point changed' }
    }
    $prepareMirror = @'
$ErrorActionPreference = 'Stop'
$path = 'C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/cygwin/tasks/main.yml'
$source = [IO.File]::ReadAllText($path)
$original = '--site https://mirrors.kernel.org/sourceware/cygwin/'
if ([regex]::Matches($source, [regex]::Escape($original)).Count -lt 1) { throw 'Upstream Cygwin mirror command missing' }
[IO.File]::WriteAllText($path, $source.Replace($original, '--site @@MIRROR@@'), (New-Object Text.UTF8Encoding($false)))
'@
    $prepareMirror = $prepareMirror.Replace('@@MIRROR@@', $env:SYNC_CYGWIN_MIRROR)
    [IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-cygwin-mirror.ps1'), $prepareMirror,
        (New-Object Text.UTF8Encoding($false)))
    $dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY prepare-cygwin-mirror.ps1 C:/prepare-cygwin-mirror.ps1`nENV TERM=dumb")
    $dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-cygwin-mirror.ps1 && sed -i -e')
    $result.cygwinMirror = $env:SYNC_CYGWIN_MIRROR
}
$preparePerl = @'
$ErrorActionPreference = 'Stop'
$path = 'C:/infrastructure/ansible/playbooks/AdoptOpenJDK_Windows_Playbook/roles/Strawberry_Perl/tasks/main.yml'
$source = [IO.File]::ReadAllText($path)
$start = $source.IndexOf('- name: Install Text::CSV_XS')
if ($start -lt 0) { throw 'Upstream Strawberry task changed' }
$end = $source.IndexOf('- name:', $start + 1)
if ($end -lt 0) { throw 'Upstream Strawberry task boundary changed' }
$task = $source.Substring($start, $end - $start)
$condition = 'when: (not strawberry_perl_installed.stat.exists)'
if (-not $task.Contains($condition) -or -not $task.Contains('install Text::CSV_XS')) { throw 'Upstream module installation changed' }
$guard = @"
- name: Check whether bundled Text::CSV_XS can be loaded
  win_command: C:/Strawberry/perl/bin/perl.exe -MText::CSV_XS -e 1
  register: dragonwell_csv_module
  changed_when: false
  failed_when: false
  tags: Strawberry_Perl

"@
$replacement = $guard + "`n" + $task.Replace($condition, $condition + ' and dragonwell_csv_module.rc != 0')
[IO.File]::WriteAllText($path, $source.Substring(0,$start)+$replacement+$source.Substring($end), (New-Object Text.UTF8Encoding($false)))
'@
[IO.File]::WriteAllText((Join-Path $PWD 'context/prepare-strawberry.ps1'), $preparePerl,
    (New-Object Text.UTF8Encoding($false)))
foreach ($anchor in @('ENV TERM=dumb', 'sed -i -e')) {
    if ([regex]::Matches($dockerfile, [regex]::Escape($anchor)).Count -ne 1) { throw 'Upstream provisioning instruction changed' }
}
$dockerfile = $dockerfile.Replace('ENV TERM=dumb', "COPY prepare-strawberry.ps1 C:/prepare-strawberry.ps1`nENV TERM=dumb")
$dockerfile = $dockerfile.Replace('sed -i -e', 'powershell -NoProfile -File C:/prepare-strawberry.ps1 && sed -i -e')
$ensureVs = @'
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$currentTool = (Get-Content 'C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\VC\\Auxiliary\\Build\\Microsoft.VCToolsVersion.default.txt' -Raw).Trim()
if ([version]$currentTool -ge [version]'14.44') {
    Write-Output "UPSTREAM_MSVC_ALREADY_COMPATIBLE=$currentTool"
    exit 0
}
$url = 'https://download.visualstudio.microsoft.com/download/pr/c95450be-0a45-4779-b142-33f567ccfd6e/68978bb28916156802e60e04c5ee50b94348d802fe151a3ca24433f27984187b/vs_BuildTools.exe'
$exe = 'C:\\temp\\vs1714_buildtools.exe'
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $exe
$signature = Get-AuthenticodeSignature $exe
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'Microsoft installer signature verification failed' }
Write-Output "INSTALLER_SHA256=$((Get-FileHash $exe -Algorithm SHA256).Hash)"
$vs = 'C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools'
if (!(Test-Path "$vs\\VC\\Auxiliary\\Build\\vcvars64.bat")) { throw 'Expected VS instance missing' }
$arguments = 'update --installPath "' + $vs + '" --channelUri https://aka.ms/vs/17/release/channel --quiet --wait --norestart --nocache'
$p = Start-Process -FilePath $exe -ArgumentList $arguments -PassThru -Wait
Write-Output "VS_UPDATE_EXIT=$($p.ExitCode)"
if ($p.ExitCode -notin @(0,3010)) { throw 'VS update failed' }
$where = 'C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe'
$instances = & $where -products Microsoft.VisualStudio.Product.BuildTools -format json | ConvertFrom-Json
$instance = @($instances | Where-Object { $_.installationPath -eq $vs })
if ($instance.Count -ne 1 -or $instance[0].installationVersion -notmatch '^17\\.14\\.') { throw 'VS version is not 17.14' }
Write-Output "VS_VERSION=$($instance[0].installationVersion)"
$tool = (Get-Content "$vs\\VC\\Auxiliary\\Build\\Microsoft.VCToolsVersion.default.txt" -Raw).Trim()
Write-Output "DEFAULT_MSVC=$tool"
if ($tool -notmatch '^14\\.44\\.') { throw 'Default MSVC is not 14.44' }
'#include <windows.h>', 'int main() { return 0; }' | Set-Content C:\\temp\\vs1714-smoke.c -Encoding Ascii
$command = '"' + $vs + '\\VC\\Auxiliary\\Build\\vcvars64.bat" && cl.exe /nologo C:\\temp\\vs1714-smoke.c /Fe:C:\\temp\\vs1714-smoke.exe && C:\\temp\\vs1714-smoke.exe'
& cmd.exe /d /s /c $command
if ($LASTEXITCODE -ne 0) { throw 'MSVC Windows SDK compile smoke failed' }
Write-Output 'VS1714_MSVC1444_COMPILE_OK'
'@
[IO.File]::WriteAllText((Join-Path $PWD 'context/ensure-vs.ps1'), $ensureVs, (New-Object Text.UTF8Encoding($false)))
$dockerfile += "`nUSER ContainerAdministrator`nCOPY ensure-vs.ps1 C:/ensure-vs.ps1`nRUN powershell.exe -NoProfile -NonInteractive -File C:/ensure-vs.ps1`nUSER ContainerUser`n"
[IO.File]::WriteAllText((Join-Path $PWD 'context/Dockerfile'), $dockerfile,
    (New-Object Text.UTF8Encoding($false)))
$result.dockerfileSha256 = (Get-FileHash context/Dockerfile -Algorithm SHA256).Hash.ToLower()
$result.status = 'building'
$result | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 result.json

# Unique candidate: failed builds cannot overwrite the last validated image.
$candidate = $env:SYNC_IMAGE_REPOSITORY + ':candidate-' + $key + '-' + $env:BUILD_NUMBER
Invoke-Native docker @('build', '--pull', '--isolation=process',
    '--label', "org.opencontainers.image.revision=$revision",
    '--label', "org.opencontainers.image.source=$upstream",
    '--label', "io.dragonwell.sync-inputs=$key",
    '--tag', $candidate, '--file', 'context/Dockerfile', 'context') | Tee-Object -FilePath docker-build.log
if (Select-String -Path docker-build.log -Pattern 'localhost[ ]+:.*(?:failed|unreachable)=[1-9]' -Quiet) {
    throw 'Ansible provisioning failed; candidate image must not be promoted'
}

# Upstream provisioning contains cmd '&' chains: a zero docker-build exit code
# alone is not proof that Ansible succeeded. Check tools as the default user.
$smoke = 'call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\Common7\\Tools\\VsDevCmd.bat" && where cl.exe && where link.exe && C:\\cygwin64\\bin\\bash.exe --version && C:\\cygwin64\\bin\\make.exe --version && C:\\cygwin64\\bin\\perl.exe --version'
Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint', 'cmd.exe',
    $candidate, '/S', '/C', $smoke)
Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint',
    'C:/Strawberry/perl/bin/perl.exe', $candidate, '-MText::CSV_XS', '-e', '1')
# cacerts uses Cygwin Perl, not Strawberry Perl; a version check is insufficient.
Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint',
    'C:/cygwin64/bin/perl.exe', $candidate, '-MEncode', '-MMIME::Base64',
    '-MList::Util', '-MText::Wrap', '-MTime::Local', '-MDigest::SHA', '-MGetopt::Std', '-e', '1')
$bootCheck = @'
$ErrorActionPreference = 'Stop'
foreach ($java in @('C:/openjdk/dragonwell-bootstrap/jdk8u272-ga/bin/java.exe',
    'C:/openjdk/jdk-10/bin/java.exe', 'C:/openjdk/jdk-17/bin/java.exe',
    'C:/openjdk/jdk-20/bin/java.exe', 'C:/openjdk/jdk-24/bin/java.exe')) {
    if (!(Test-Path $java)) { throw "Boot JDK missing: $java" }
    Write-Output "BOOT_JDK=$java"
    & cmd.exe /d /c ($java + ' -version 2>&1')
    if ($LASTEXITCODE -ne 0) { throw "Boot JDK failed: $java" }
}
'@
$bootCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($bootCheck))
Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint', 'powershell.exe',
    $candidate, '-NoProfile', '-NonInteractive', '-EncodedCommand', $bootCommand)
$msvcCommand = "(Get-Content 'C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/Microsoft.VCToolsVersion.default.txt' -Raw).Trim()"
$msvc = (Invoke-Native docker @('run', '--rm', '--isolation=process', '--entrypoint', 'powershell.exe',
    $candidate, '-NoProfile', '-NonInteractive', '-Command', $msvcCommand)).Trim()
if ([version]$msvc -lt [version]'14.44') { throw 'Default MSVC is below the validated compatibility floor' }
$result.msvc = $msvc
$actualRevision = (Invoke-Native docker @('run', '--rm', '--isolation=process',
    '--entrypoint', 'C:\\cygwin64\\bin\\git.exe', $candidate,
    '-c', 'safe.directory=/cygdrive/c/infrastructure',
    '-C', 'C:/infrastructure', 'rev-parse', 'HEAD')).Trim()
if ($actualRevision -ne $revision) { throw 'Image Ansible revision differs from checked source' }
Invoke-Native docker @('tag', $candidate, $image)
Invoke-Native docker @('tag', $image, $latest)
$result.status = 'built'
$result.imageId = (Invoke-Native docker @('image', 'inspect', $image, '--format', '{{.Id}}')).Trim()
$result.imageRevision = $actualRevision
$result | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 result.json
Write-Output "Validated image: $image"
''')
                    }
                    if (publishImage) {
                        stage('Publish versioned and latest images to Docker Hub') {
                            // Credentials exist only during push, never during upstream build.
                            withCredentials([usernamePassword(credentialsId: registryCredential,
                                    usernameVariable: 'HUB_USER', passwordVariable: 'HUB_TOKEN')]) {
                                powershell(script: '''
$ErrorActionPreference = 'Stop'
$result = Get-Content -Raw result.json | ConvertFrom-Json
if ($result.status -notin @('built', 'unchanged')) { throw 'No validated image to publish' }
if ($result.image -notmatch '^docker\\.io/alibabadragonwelljdk/[a-z0-9._-]+:inputs-[0-9a-f]{64}$') {
    throw 'Unexpected publication target'
}
$imageId = & docker image inspect $result.image --format '{{.Id}}'
if ($LASTEXITCODE -ne 0 -or $imageId.Trim() -ne $result.imageId) {
    throw 'Local validated image changed before publication'
}
# Isolate login state; never overwrite the node user Docker configuration.
$env:DOCKER_CONFIG = Join-Path $PWD '.docker-publish'
New-Item -ItemType Directory -Path $env:DOCKER_CONFIG | Out-Null
try {
    $env:HUB_TOKEN | & docker login docker.io --username $env:HUB_USER --password-stdin
    if ($LASTEXITCODE -ne 0) { throw 'Docker Hub login failed' }
    & docker push $result.image
    if ($LASTEXITCODE -ne 0) { throw 'Docker Hub push failed; next run will retry' }
    & docker push $result.latest
    if ($LASTEXITCODE -ne 0) { throw 'Docker Hub latest push failed; next run will retry' }
    $digests = & docker image inspect $result.image --format '{{json .RepoDigests}}'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot record pushed digest' }
    $result | Add-Member -NotePropertyName publishedDigests -NotePropertyValue ($digests | ConvertFrom-Json) -Force
    $result | Add-Member -NotePropertyName published -NotePropertyValue $true -Force
    $result | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 result.json
} finally {
    # Exact task-created credential directory, not the user's shared Docker config.
    Remove-Item -LiteralPath $env:DOCKER_CONFIG -Recurse -Force
}
''')
                            }
                        }
                    }
                } finally {
                    archiveArtifacts artifacts: 'result.json,Dockerfile.upstream,docker-build.log,context/Dockerfile,context/prepare-*.ps1,context/ensure-vs.ps1', allowEmptyArchive: true
                    // Only this invocation's dedicated workspace, never Docker images/cache.
                    deleteDir()
                }
            }
        }
    }
}
