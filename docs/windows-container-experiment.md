# Dragonwell Windows container experiment

Branch in both repositories: `experiment/windows-upstream-containers`.
Base: `pr/dragonwell-cleanup`; existing PRs and normal Jenkins job defaults are unchanged.

## Scope and upstream provenance

Existing Dragonwell Windows x64 targets for 8, 11, 17, 21 and 25 only.
No new x86-32/ARM64 targets are enabled. Dragonwell source, vendor identity,
Linux/macOS configurations and existing tests/signing implementation are retained.

Environment fields are taken from Adoptium ci-jenkins-pipelines commit
`d2721a535ac361fcc6bf008cd67bc82ad79a5497`, `pipelines/jobs/configurations/jdk*_pipeline_config.groovy`:

- Windows Server 2022 image, registry `https://adoptium.azurecr.io`.
- JDK8/11: `windows2022_build_image@sha256:0d0a3afb998c34df0277d6a5ed97c2d8d705012e6a93d358af25af638963ef6f`.
- JDK17/21/25: `windows2022_build_image` (upstream floating tag; record the actual digest for every trial).
- `win2022&&vs2022`, with `dockerBuild` appended by the existing pipeline.
- `--use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742`.
- Version-specific build arguments match upstream Temurin; JDK8/11 disable ccache.
- Remove the old JDK17 Zulu path and JDK21 host-specific UCRT path.

The companion openjdk-build-new branch restores windows.sh byte-for-byte to
adoptium/temurin-build commit `2dd5226b8fbfc3d075a9d5a75a1a4f710f4e306c`.
This enables upstream Boot JDK lookup for Dragonwell 8/11 as well as later versions.
JDK8 needs an installed JDK7 Boot JDK in the image; upstream cannot download a pre-8 Boot JDK.
The upstream script itself retains the optional Dragonwell8 bootstrap override.

## Preconditions and isolated trial

1. Restore Jenkins login. The last node-inventory request returned HTTP 403;
   node capability and credentials have NOT been verified.
2. Verify an online Windows x64 build node satisfies
   `win2022&&vs2022&&build&&windows&&x64&&dockerBuild` and runs a Windows-container
   Docker daemon compatible with the image. Do not add a label as a substitute
   for installing/verifying Docker. Do not switch shared daemon modes automatically.
3. The copied upstream `dockerCredential` is a credential ID, NOT credentials.
   Confirm authorized local registry access or replace the trial's credential ID
   with the locally provisioned ID. Never embed a password/token. Pulling the exact
   upstream image must succeed before claiming parity; do not silently replace it.
4. Use isolated trial jobs or one-off parameter overrides, not broad job regeneration.
   Select this branch for BOTH `CI_REF` and `BUILD_REF`, use the user repositories
   (`USE_ADOPT_SHELL_SCRIPTS=false`), and apply the version-specific environment fields
   from these configs to the trial job. Merely changing CI_REF does not update a
   generated job's existing Docker parameters.
5. Retain the user's disabled signing setting (`ENABLE_SIGNER=false`) for the trial.
   Do not disable SBOM/test checks to manufacture success. Avoid concurrent trials on
   the same Windows node: the existing pipeline uses `C:/workspace/openjdk-build/`.
6. Start with one version, then validate all five sequentially. Record the agent,
   actual container digest, compiler/SDK/Boot JDK, source refs, first failure, and
   downloadable archived JDK ZIP separately from post-build SBOM/test status.

## Current validation boundary

Static field comparisons passed for all five versions, with no changes outside
their x64Windows blocks. The companion Windows script matches pinned upstream and
passes bash syntax checking. No container pull or Jenkins build has been executed
for this branch; successful container builds remain unproven.
