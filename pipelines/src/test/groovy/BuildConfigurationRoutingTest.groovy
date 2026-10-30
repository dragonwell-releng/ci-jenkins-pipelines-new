/*
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class BuildConfigurationRoutingTest {
    private Builder builder = new Builder(DEFAULTS_JSON: [
        repository: [build_url: 'https://github.com/dragonwell-releng/openjdk-build-new.git', build_branch: 'master'],
        configDirectories: [platform: 'build-farm/platform-specific-configurations']
    ])

    @Test void riscvCrossBuildUsesHostNodes() {
        Map platform = [arch: 'riscv64', crossCompile: 'x64']
        assertEquals('build', builder.formAdditionalBuildNodeLabels(platform, 'dragonwell').toString())
        assertEquals('x64', builder.getArchLabel(platform, 'dragonwell'))
    }

    @Test void nativeRiscvKeepsDedicatedNodes() {
        Map platform = [arch: 'riscv64']
        assertEquals('build&&riscv64build', builder.formAdditionalBuildNodeLabels(platform, 'dragonwell').toString())
        assertEquals('riscv64', builder.getArchLabel(platform, 'dragonwell'))
        assertEquals('custom&&build', builder.formAdditionalBuildNodeLabels(
            [arch: 'x64', additionalNodeLabels: 'custom'], 'dragonwell').toString())
    }

    @Test void variantSpecificCrossBuildSelectsHostNodes() {
        Map platform = [arch: 'riscv64', crossCompile: [dragonwell: 'x64']]
        assertEquals('build', builder.formAdditionalBuildNodeLabels(platform, 'dragonwell').toString())
        assertEquals('build&&riscv64build', builder.formAdditionalBuildNodeLabels(platform, 'openj9').toString())
    }

    @Test void platformPathUsesRepositoryOwnerAndName() {
        assertEquals('dragonwell-releng/openjdk-build-new/master/build-farm/platform-specific-configurations',
            builder.getPlatformSpecificConfigPath([:]).toString())
    }

    @Test void platformPathHonorsBuildReferenceOverride() {
        builder.buildReference = 'codex/debug-riscv'
        assertEquals('dragonwell-releng/openjdk-build-new/codex/debug-riscv/build-farm/platform-specific-configurations',
            builder.getPlatformSpecificConfigPath([buildRef: 'platform-branch']).toString())
    }

    @Test void platformPathRetainsPlatformOverrides() {
        assertEquals('dragonwell-releng/openjdk-build-new/platform-branch/custom/platform',
            builder.getPlatformSpecificConfigPath([buildRef: 'platform-branch', platformSpecificConfigPath: 'custom/platform']).toString())
    }
    @Test void generatedPlatformPathsMatchRuntimeRepositoryPaths() {
        Regeneration generator = new Regeneration('25', [:], [:], builder.DEFAULTS_JSON, [:], [:],
            0, null, null, '', [:], '', '', '', '', '', '', '', '')
        assertEquals('dragonwell-releng/openjdk-build-new/master/build-farm/platform-specific-configurations',
            generator.getPlatformSpecificConfigPath([:]).toString())
        assertEquals('dragonwell-releng/openjdk-build-new/platform-branch/custom/platform',
            generator.getPlatformSpecificConfigPath([buildRef: 'platform-branch',
                platformSpecificConfigPath: 'custom/platform']).toString())
    }

    @Test void perRunNodeRestrictionPreservesRequiredLabels() {
        builder.DEFAULTS_JSON.buildNodeLabels = ['alpine-linux-x64-dragonwell': 'worker-a || worker-b']
        assertEquals('custom&&build&&(worker-a || worker-b)', builder.formAdditionalBuildNodeLabels(
            [os: 'alpine-linux', arch: 'x64', additionalNodeLabels: 'custom'], 'dragonwell').toString())
    }

    @Test void perRunNodeRestrictionIsScopedToPlatformAndVariant() {
        builder.DEFAULTS_JSON.buildNodeLabels = ['alpine-linux-x64-dragonwell': 'worker-a']
        assertEquals('build', builder.formAdditionalBuildNodeLabels(
            [os: 'linux', arch: 'x64'], 'dragonwell').toString())
        assertEquals('build', builder.formAdditionalBuildNodeLabels(
            [os: 'alpine-linux', arch: 'x64'], 'openj9').toString())
    }

    @Test void perRunNodeRestrictionRetainsCrossBuildHostSelection() {
        builder.DEFAULTS_JSON.buildNodeLabels = ['linux-riscv64-dragonwell': 'worker-a']
        assertEquals('build&&(worker-a)', builder.formAdditionalBuildNodeLabels(
            [os: 'linux', arch: 'riscv64', crossCompile: 'x64'], 'dragonwell').toString())
        assertEquals('x64', builder.getArchLabel([arch: 'riscv64', crossCompile: 'x64'], 'dragonwell'))
    }

}
