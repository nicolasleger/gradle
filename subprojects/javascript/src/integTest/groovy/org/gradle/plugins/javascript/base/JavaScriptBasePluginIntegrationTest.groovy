/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.javascript.base

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Unroll

import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGoogleRepoScript
import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGradlePublicJsRepoScript

class JavaScriptBasePluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginName() {
        "javascript-base"
    }

    def setup() {
        applyPlugin()
    }

    @Unroll
    def "can download from googles repo (gradleMetadata=#gradleMetadata)"() {
        given:
        if (gradleMetadata) {
            FeaturePreviewsFixture.enableGradleMetadata(propertiesFile)
        }
        addGoogleRepoScript(buildFile)

        when:
        buildFile << """
            configurations {
                jquery
            }
            dependencies {
                jquery "jquery:jquery.min:1.7.2@js"
            }
            task resolve(type: Copy) {
                from configurations.jquery
                into "jquery"
            }
        """

        then:
        succeeds "resolve"

        and:
        def jquery = file("jquery/jquery.min-1.7.2.js")
        jquery.exists()
        jquery.text.contains("jQuery v1.7.2")

        where:
        gradleMetadata | _
        true           | _
        false          | _
    }

    @Unroll
    def "can download from gradleJs repo (gradleMetadata=#gradleMetadata)"() {
        given:
        if (gradleMetadata) {
            FeaturePreviewsFixture.enableGradleMetadata(propertiesFile)
        }
        addGradlePublicJsRepoScript(buildFile)

        when:
        buildFile << """
            configurations {
                jshint
            }
            dependencies {
                jshint "com.jshint:jshint:r07@js"
            }
            task resolve(type: Copy) {
                from configurations.jshint
                into "jshint"
            }
        """

        then:
        succeeds "resolve"

        and:
        def jshint = file("jshint/jshint-r07.js")
        jshint.exists()
        jshint.text.contains("JSHint")

        where:
        gradleMetadata | _
        true           | _
        false          | _
    }

}
