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

import common.IndividualBuildConfig
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class CrossBuildReleaseMetadataTest {
    private Map config = [JAVA_TO_BUILD: 'jdk25u', VARIANT: 'dragonwell', TARGET_OS: 'linux',
        ARCHITECTURE: 'riscv64', BUILD_ARGS: '--create-sbom --cross-compile', RELEASE: false]
    private Map policy = [postBuildValidation: [skipAll: true]]

    @Test void buildOnlyCrossCompilationUsesReleaseMetadata() {
        assertTrue(Build.shouldUseReleaseMetadata(config, policy))
        assertTrue(Build.shouldUseReleaseMetadata(config + [RELEASE: true], policy))
    }

    @Test void restoringValidationRestoresRuntimeVersionReader() {
        assertFalse(Build.shouldUseReleaseMetadata(config, [:]))
        assertFalse(Build.shouldUseReleaseMetadata(config, [postBuildValidation: [skipAll: false]]))
        assertFalse(Build.shouldUseReleaseMetadata(config + [ARCHITECTURE: 'x64'], policy))
        assertFalse(Build.shouldUseReleaseMetadata(config + [BUILD_ARGS: '--create-sbom'], policy))
    }

    @Test void runtimeVersionIsPreferredAndQuotesAreRemoved() {
        String text = 'JAVA_VERSION="25.0.4"\r\nJAVA_RUNTIME_VERSION="25.0.4+7"\r\nOS_ARCH="riscv64"\r\n'
        assertEquals('25.0.4+7', Build.releaseRuntimeVersion(text))
        assertEquals('riscv64', Build.releaseProperties(text)['OS_ARCH'])
    }

    @Test void javaVersionIsUsedWhenRuntimePropertyIsMissing() {
        assertEquals('25.0.4.0.4', Build.releaseRuntimeVersion('JAVA_VERSION="25.0.4.0.4"\n'))
    }

    @Test void missingReleaseVersionFails() {
        assertThrows(IllegalArgumentException) { Build.releaseRuntimeVersion('OS_ARCH="riscv64"\n') }
    }

    @Test void versionMetadataIsParsedWithoutInventingRuntimeOutput() {
        Build build = new Build(new IndividualBuildConfig(config), [:], policy, [:], this, [:], [:])
        String text = Build.RELEASE_METADATA_PREFIX + 'JAVA_VERSION="25.0.4"\nJAVA_RUNTIME_VERSION="25.0.4+7"\nOS_ARCH="riscv64"\n'
        def parsed = build.parseVersionOutput(text)
        assertEquals(25, parsed.major)
        assertEquals(7, parsed.build)
        assertEquals('25.0.4+7', parsed.version)
    }
    @Test void wrongFeatureVersionIsRejected() {
        Build build = new Build(new IndividualBuildConfig(config), [:], policy, [:], this, [:], [:])
        String text = Build.RELEASE_METADATA_PREFIX + 'JAVA_VERSION="24.0.2"\nOS_ARCH="riscv64"\n'
        assertThrows(IllegalArgumentException) { build.parseVersionOutput(text) }
    }

}
