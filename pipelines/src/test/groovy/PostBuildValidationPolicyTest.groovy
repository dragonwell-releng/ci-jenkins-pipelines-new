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

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class PostBuildValidationPolicyTest {
    private Map config = [JAVA_TO_BUILD: 'jdk25u', VARIANT: 'dragonwell', TARGET_OS: 'linux',
                          ARCHITECTURE: 'riscv64', RELEASE: false]
    private Map defaults = [postBuildValidation: [skip: ['jdk25u/dragonwell/linux/riscv64']]]

    @Test void skipsOnlyExplicitBringupTarget() {
        assertTrue(Build.shouldSkipPostBuildValidation(config, defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config + [ARCHITECTURE: 'x64'], defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config + [JAVA_TO_BUILD: 'jdk21u'], defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config + [VARIANT: 'temurin'], defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config + [TARGET_OS: 'windows'], defaults))
    }

    @Test void globalBypassCoversAllBuildsIncludingReleases() {
        Map policy = [postBuildValidation: [skipAll: true]]
        [
            [JAVA_TO_BUILD: 'jdk8u', TARGET_OS: 'windows', ARCHITECTURE: 'x64', RELEASE: true],
            [JAVA_TO_BUILD: 'jdk11u', TARGET_OS: 'mac', ARCHITECTURE: 'aarch64', RELEASE: 'true'],
            [JAVA_TO_BUILD: 'jdk17u', TARGET_OS: 'aix', ARCHITECTURE: 'ppc64', VARIANT: 'openj9'],
            [JAVA_TO_BUILD: 'jdk21u', ARCHITECTURE: 's390x', VARIANT: 'temurin'],
            [JAVA_TO_BUILD: 'jdk25u', RELEASE: false],
            [JAVA_TO_BUILD: 'jdk', ARCHITECTURE: 'aarch64', RELEASE: true]
        ].each { overrides ->
            assertTrue(Build.shouldSkipPostBuildValidation(config + overrides, policy))
        }
    }

    @Test void globalBypassCanBeDisabled() {
        assertTrue(Build.shouldSkipPostBuildValidation(config, [postBuildValidation: [skipAll: 'true']]))
        assertFalse(Build.shouldSkipPostBuildValidation(config, [postBuildValidation: [skipAll: false]]))
        assertFalse(Build.shouldSkipPostBuildValidation(config, [postBuildValidation: [skipAll: 'false']]))
    }

    @Test void dragonwellDefaultsEnableGlobalBypass() {
        Map repositoryDefaults = new JsonSlurper().parse(new File('defaults-build-scripts-new.json')) as Map
        assertTrue(Build.shouldSkipPostBuildValidation(config, repositoryDefaults))
        assertTrue(Build.shouldSkipPostBuildValidation(config + [JAVA_TO_BUILD: 'jdk8u',
            TARGET_OS: 'windows', ARCHITECTURE: 'x64', RELEASE: true], repositoryDefaults))
    }

    @Test void releasesAndMissingPolicyStillValidate() {
        assertFalse(Build.shouldSkipPostBuildValidation(config + [RELEASE: true], defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config + [RELEASE: 'true'], defaults))
        assertFalse(Build.shouldSkipPostBuildValidation(config, [:]))
        assertFalse(Build.shouldSkipPostBuildValidation(config, [postBuildValidation: [skip: []]]))
    }
}
