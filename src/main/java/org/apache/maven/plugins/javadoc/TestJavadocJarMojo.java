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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.javadoc.resolver.SourceResolverConfig;
import org.codehaus.plexus.util.StringUtils;

/**
 * Bundles the Javadoc documentation for <code>test Java code</code> in a <b>NON aggregator</b> project into
 * a jar using the standard <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">
 * Javadoc Tool</a>.
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @since 2.5
 */
@Mojo(name = "test-jar", defaultPhase = "package")
public class TestJavadocJarMojo extends JavadocJarMojo {

    // ----------------------------------------------------------------------
    // Javadoc Options (should be inline with Javadoc options defined in TestJavadocReport)
    // ----------------------------------------------------------------------

    /**
     * Specifies the Test title to be placed near the top of the overview summary file.
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html#standard-doclet-options">Doclet option doctitle</a>.
     * @since 2.5
     */
    @Parameter(
            property = "testDoctitle",
            alias = "doctitle",
            defaultValue = "${project.model.name} ${project.version} Test API")
    private String testDoctitle;

    /**
     * Specifies that Javadoc should retrieve the text for the Test overview documentation from the "source" file
     * specified by path/filename and place it on the Overview page (overview-summary.html).
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html#standard-doclet-options">Doclet option overview</a>.
     * @since 2.5
     */
    @Parameter(
            property = "testOverview",
            alias = "overview",
            defaultValue = "${project.basedir}/src/test/javadoc/overview.html")
    private File testOverview;

    /**
     * Specifies the Test title to be placed in the HTML title tag.
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html#standard-doclet-options">Doclet option windowtitle</a>.
     * @since 2.5
     */
    @Parameter(
            property = "testWindowtitle",
            alias = "windowtitle",
            defaultValue = "${project.model.name} ${project.version} Test API")
    private String testWindowtitle;

    // ----------------------------------------------------------------------
    // Mojo Parameters (should be inline with options defined in TestJavadocReport)
    // ----------------------------------------------------------------------

    /**
     * Specifies the Test Javadoc resources directory to be included in the Javadoc (i.e. package.html, images...).
     *
     * @since 2.5
     */
    @Parameter(alias = "javadocDirectory", defaultValue = "${project.basedir}/src/test/javadoc")
    private File testJavadocDirectory;

    /**
     * @since 2.10
     */
    @Parameter(property = "maven.javadoc.testClassifier", defaultValue = "test-javadoc", required = true)
    private String testClassifier;

    // ----------------------------------------------------------------------
    // Protected methods
    // ----------------------------------------------------------------------

    @Override
    protected String getClassifier() {
        return testClassifier;
    }

    // ----------------------------------------------------------------------
    // Important Note: should be inline with methods defined in TestJavadocReport
    // ----------------------------------------------------------------------

    @Override
    protected File getJavadocDirectory() {
        return testJavadocDirectory;
    }

    @Override
    protected String getDoctitle() {
        return testDoctitle;
    }

    @Override
    protected File getOverview() {
        return testOverview;
    }

    @Override
    protected String getWindowtitle() {
        return testWindowtitle;
    }

    @Override
    protected List<File> getProjectBuildOutputDirs(Project p) {
        List<File> dirs = new ArrayList<>();
        if (StringUtils.isNotEmpty(p.getBuild().getOutputDirectory())) {
            dirs.add(new File(p.getBuild().getOutputDirectory()));
        }
        if (StringUtils.isNotEmpty(p.getBuild().getTestOutputDirectory())) {
            dirs.add(new File(p.getBuild().getTestOutputDirectory()));
        }

        return dirs;
    }

    /**
     * Overridden to enable the resolution of -test-sources jar files.
     *
     * {@inheritDoc}
     */
    @Override
    protected SourceResolverConfig configureDependencySourceResolution(final SourceResolverConfig config) {
        return super.configureDependencySourceResolution(config)
                .withoutCompileSources()
                .withTestSources();
    }

    @Override
    protected boolean isTest() {
        return true;
    }
}
