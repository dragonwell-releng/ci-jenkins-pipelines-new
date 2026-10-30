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

class DockerPullArgumentsTest {
    @Test void runOnlyOptionsAreExcludedFromPull() {
        assertEquals('', Build.dockerPullArguments(null))
        assertEquals('', Build.dockerPullArguments(''))
        assertEquals('', Build.dockerPullArguments('-e RISCV_MARCH='))
        assertEquals('', Build.dockerPullArguments('--privileged -e FOO=bar --network host'))
    }

    @Test void platformSelectionIsRetained() {
        assertEquals('--platform linux/arm/v7',
            Build.dockerPullArguments('--platform linux/arm/v7 -e RISCV_MARCH='))
        assertEquals('--platform=linux/arm64',
            Build.dockerPullArguments('-e FOO=bar --platform=linux/arm64 --privileged'))
    }

    @Test void missingPlatformValueIsRejected() {
        assertThrows(IllegalArgumentException) { Build.dockerPullArguments('--platform') }
        assertThrows(IllegalArgumentException) { Build.dockerPullArguments('--platform=') }
        assertThrows(IllegalArgumentException) { Build.dockerPullArguments('--platform -e FOO=bar') }
    }
}
