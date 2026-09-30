/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.javadoc;

import java.io.File;
import java.nio.file.Paths;

import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.impl.InternalSession;
import org.apache.maven.testing.plugin.Basedir;
import org.apache.maven.testing.plugin.InjectMojo;
import org.apache.maven.testing.plugin.MojoTest;
import org.junit.jupiter.api.Test;

import static org.apache.maven.testing.plugin.MojoExtension.getBasedir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 */
@MojoTest
public class TestJavadocReportTest {
    @Provides
    @Singleton
    InternalSession createSession() {
        InternalSession session = TestSessions.create();
        TestSessions.addDependency(
                session,
                PathScope.TEST_COMPILE,
                DependencyScope.TEST,
                "artifactId",
                Paths.get("test/test-dependency-1.2.3.jar"));
        // Maven 3 also put the runtime scope on the class path of the test goals, which TEST_COMPILE lacks
        TestSessions.addDependency(
                session,
                PathScope.TEST_RUNTIME,
                DependencyScope.RUNTIME,
                "runtime",
                Paths.get("test/runtime-dependency-1.0.jar"));
        return session;
    }

    /**
     * Test the test-javadoc configuration for the plugin
     *
     * @throws Exception if any
     */
    @Test
    @InjectMojo(goal = "test-javadoc", pom = "test-javadoc-test-plugin-config.xml")
    @Basedir("${basedir}/target/test-classes/unit/test-javadoc-test")
    public void testTestJavadoc(TestJavadocReport mojo) throws Exception {
        mojo.execute();

        File generatedFile = new File(getBasedir(), "/target/site/testapidocs/maven/AppTest.html");
        assertThat(generatedFile).exists();

        // -classpath
        // '/Users/slawomir.jaranowski/repos/apache/maven-javadoc-plugin/target/test/unit/test-javadoc-test/target/classes:/Users/slawomir.jaranowski/repos/apache/maven-javadoc-plugin/target/test/unit/test-javadoc-test/target/test-classes:/Users/slawomir.jaranowski/repos/apache/maven-javadoc-plugin/src/test/resources/unit/test-javadoc-test/junit/junit/3.8.1/junit-3.8.1.jar'

        File options = new File(getBasedir(), "/target/site/testapidocs/options");
        assertThat(options).content().contains("test-dependency-1.2.3.jar").contains("runtime-dependency-1.0.jar");
    }
}
