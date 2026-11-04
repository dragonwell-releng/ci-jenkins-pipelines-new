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

## Validation in progress (2026-11-04)

Upstream pipeline revision `4619c4d1530c638b5e2f6d9d614984e76afce9d7` retains
the JDK17 Windows image/devkit arguments used here. The companion `windows.sh`
matches temurin-build `d60bae438ce7e28831ba454d4e094851655d61ac`.

Isolated job: `build-scripts-new/windows-synced-image-jdk17`.
- #1 was cancelled in queue: quote the exact node name in label expressions.
- #2 entered the validated container but failed before compilation because HOME
  resolved to `/` and Git could not write `//.gitconfig`.
- #3 sets `-e HOME=/cygdrive/c/Users/ContainerUser`. Git initialization and boot JDK
  discovery passed; Dragonwell source cloning is in progress. No package is verified yet.

The source ref is unchanged from the prior experiment:
`dragonwell-standard-17.0.20.0.21+8_jdk-17.0.20-ga`.
Build output is retained for diagnosis (`CLEAN_WORKSPACE_AFTER=false`).
