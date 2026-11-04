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
The first actual compilation error was C2244 at
`test/hotspot/gtest/metaprogramming/test_enableIf.cpp:73`, at 17:06:28 CST.
The job finished FAILURE at 17:07:06 without JDK packages. This reproduces the
MSVC 14.42 failure from the earlier container trial.

A derived image upgrading to VS2022 17.14 / MSVC 14.44 is being built without
changing Dragonwell source. Its recipe is in
`pipelines/build/dragonwell/windows-vs-repair`; the Microsoft installer signature
is checked, and installation must finish with the expected VS and MSVC versions.
The upgrade retains the current Perl-repaired base image. Image construction
completed successfully with VS 17.14.37806.7 / MSVC 14.44.35207, installer exit 0,
and a passing Windows SDK compile smoke test.

New local image: `dragonwell/windows2022-build:synced-vs1714-20261104`.
Image ID: `sha256:a62faecf7d6be291ee454c0991e5a5f0764acb9b30534c32452b34e1dc368369`.
Default user: `ContainerUser`.

## JDK17 rebuild verification

[#6](http://ci.dragonwell-jdk.io/job/build-scripts-new/job/windows-synced-image-jdk17/6/console)
uses pipeline commit `68847141`, build-script commit `68a13a2e`, and the image
above, with the Dragonwell source ref unchanged. Configure selected compiler
19.44.35229. The previously failing `test_enableIf.cpp` compiled successfully.
All requested make targets (`product-images legacy-jre-image test-image
static-libs-image`) completed, followed by `Build complete` at 19:23 CST on
2026-11-04. The generated JVM printed Dragonwell 17.0.20.0.21+8-GA / 17.0.20+8.

The build log confirms JDK, JRE, testimage, debugimage, and static-libs ZIP files
were moved into `workspace/target`, with suffix
`_x64_windows_dragonwell_2026-11-04-09-39.zip`. SBOM generation completed and
`build.sh` printed `All done!` at 19:27:11 CST.

Jenkins archived the artifacts successfully between 19:39 and 19:41 CST.
The build page lists the JDK ZIP (189,684,921 bytes / 180.90 MiB) and JRE ZIP
(43,418,399 bytes / 41.41 MiB), plus debugimage, testimage, static-libs and SBOM.
The JDK SHA-256 is
`f502c0ac97c90b7875daa7bdbc72c48bfe6d84d246ec51047d4f773e704a549a`.

The overall job finished FAILURE at 19:41:40 solely after the subsequent
`sbom_validator_job #50` failed validation. The requested build/package goal
passed; SBOM validation was explicitly outside the acceptance requirement.
Artifacts remain available on the
[build page](http://ci.dragonwell-jdk.io/job/build-scripts-new/job/windows-synced-image-jdk17/6/).
The post-build workspace disk-usage scan (`du -sk "$WORKSPACE"` in
`build-farm/make-adopt-build-farm.sh`) took approximately 12 minutes before
metadata generation and archiving; it was not a compilation hang.
This result establishes JDK17 compilation and packaging with unchanged source;
it does not validate other JDK versions, test-suite execution, or publishing the
local image to a registry.
