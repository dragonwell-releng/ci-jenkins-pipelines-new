# Dragonwell Windows synchronized-image experiment

Both repositories use branch `experiment/windows-synced-image`, based on their
current master. Existing Windows x64 targets (8, 11, 17, 21, 25) use the upstream
Windows container execution path, VS2022 devkit arguments and boot JDK discovery.
Dragonwell source is unchanged. No new target architectures are enabled.

The image is built from infrastructure revision
`1394a2fc82ef37d71baec6ee4bf98b5890f8c843`, validated by image-sync builds #26/#27.
It contains VS 17.12.12 / MSVC 14.42.34433. It is not the old VS17.14 trial image.
The Dockerfile has verified download-cache adaptations and a pinned source checkout;
it is not byte-for-byte upstream. For JDK8/11 this new build is also not the older
image digest pinned in upstream's configurations, so compatibility needs testing.

The image exists only on `windows_2022rc_build4(47.76.90.84)`.
The experimental pull bypass matches only the exact versioned image tag and checks
its full image ID before using the normal upstream Windows Docker execution path.
Remove this local-only exception after publishing the image to the registry.

Use an isolated Jenkins job, with CI_REF and BUILD_REF pointing to this branch,
USE_ADOPT_SHELL_SCRIPTS=false and ENABLE_SIGNER=false. Run versions sequentially
because upstream uses a shared Windows workspace. Verify JDK17 first; record
compile/package results separately from SBOM and other post-build gates.

## Actual build result (2026-11-04)

Upstream pipeline revision `4619c4d1530c638b5e2f6d9d614984e76afce9d7` retains
the JDK17 Windows image/devkit arguments used here. The companion `windows.sh`
matches temurin-build `d60bae438ce7e28831ba454d4e094851655d61ac`.

Isolated job: `build-scripts-new/windows-synced-image-jdk17`.
- #1 was cancelled in queue: quote the exact node name in label expressions.
- #2 entered the validated container but failed before compilation because HOME
  resolved to `/` and Git could not write `//.gitconfig`.
- [#3](http://ci.dragonwell-jdk.io/job/build-scripts-new/job/windows-synced-image-jdk17/3/console)
  sets `-e HOME=/cygdrive/c/Users/ContainerUser`. Git initialization, boot JDK
  discovery, source checkout (`a471c10eebf`) and matching devkit selection passed.
  The first actual failure is `Can't locate Encode.pm in @INC` from
  `mk-ca-bundle.pl` while generating Mozilla cacerts, at 15:58:22 CST.
  Cygwin Perl is 5.44. No C++ compilation or JDK packaging was reached.
  `No artifacts found ... workspace/target/*` is a secondary archive failure.

Conclusion: the container execution path works, but this image cannot yet build
Dragonwell17 unchanged. Its Cygwin Perl runtime needs the required modules (starting
with Encode, supplied by perl_base) before another full build can establish compiler
compatibility. Toolchain compatibility and JDK8/11/21/25 builds remain unverified.
Do not interpret successful image-sync validation as a successful JDK build.

The source ref is unchanged from the prior experiment:
`dragonwell-standard-17.0.20.0.21+8_jdk-17.0.20-ga`.
Build output is retained for diagnosis (`CLEAN_WORKSPACE_AFTER=false`).

## Perl repair

Live inspection found `perl 5.44.0-1` installed but no `perl_base` entry and no
`Encode.pm` file. Installing `perl_base=5.44.0-1` in a derived image restored all
cacerts modules. The repair Dockerfile/script are under
`pipelines/build/dragonwell/windows-perl-repair`. The base image and VS toolchain
are unchanged. Validation passed both during build and in a fresh ContainerUser run.

Repaired local image: `dragonwell/windows2022-build:synced-perlbase-20261104`

Image ID: `sha256:3e5b3168e0ac30194b8e84d31bf16c76d1f79cbfe4bc5271c43e9db4a7b58513`.

The original Cygwin setup log selected and downloaded `perl_base 5.44.0-1`,
then failed to create paths while extracting it after removing 5.40.3. This was
an incomplete upgrade, not a missing dependency declaration. The exact cause of
those path-creation errors is not yet established.

[#4](http://ci.dragonwell-jdk.io/job/build-scripts-new/job/windows-synced-image-jdk17/4/console)
passed cacerts generation with the repaired image, then failed at configure with
`Cannot locate a valid Visual Studio installation`. A fresh container showed the
VS installation and vcvars64.bat existed, but `cygpath -m -s` still returned paths
containing spaces.

The isolated pipeline now initializes short names for Program Files (x86),
Microsoft Visual Studio and Windows Kits in the running container. The host
checks the container image and default user before using ContainerAdministrator
for this setup only; the actual build remains ContainerUser. This workaround is
scoped to the exact experimental image tag and should not be generalized without
review. It does not modify Dragonwell source or the VS toolchain.

[#5](http://ci.dragonwell-jdk.io/job/build-scripts-new/job/windows-synced-image-jdk17/5/console)
uses pipeline commit `03721293`. Runtime initialization and ContainerUser short
path validation passed. Configure completed at 17:02:54 CST with MSVC
14.42.34433 / compiler 19.42.34444. Compilation started at 17:03:12.
Final compilation and packaging results are still pending.
