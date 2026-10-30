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

class ContainerHostRoutingTest {
    @Test void alpineContainersUseLinuxHostsInBothEntryPoints() {
        [Builder, Regeneration].each { entry ->
            assertEquals('linux', entry.getHostOsLabel('alpine-linux', 'adoptopenjdk/alpine3_build_image'))
        }
    }

    @Test void nativeAndOtherContainerTargetsKeepTheirOsLabels() {
        [Builder, Regeneration].each { entry ->
            assertEquals('alpine-linux', entry.getHostOsLabel('alpine-linux', ''))
            assertEquals('linux', entry.getHostOsLabel('linux', 'build-image'))
            assertEquals('windows', entry.getHostOsLabel('windows', 'windows-image'))
            assertEquals('mac', entry.getHostOsLabel('mac', null))
        }
    }
}
