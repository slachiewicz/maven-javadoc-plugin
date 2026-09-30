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
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.javadoc.resolver.SourceResolverConfig;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.StringUtils;

/**
 * Generates documentation for the <code>Java Test code</code> in an <b>NON aggregator</b> project using the standard
 * <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>. Note that this
 * goal does require generation of test sources before site generation, e.g. by invoking
 * {@code mvn clean deploy site}.
 *
 * @author <a href="mailto:mfriedenhagen@apache.org">Mirko Friedenhagen</a>
 * @since 2.10
 * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>
 * @see <a href="http://docs.oracle.com/javase/1.4.2/docs/tooldocs/windows/javadoc.html#options">Javadoc Options </a>
 */
@Mojo(name = "test-javadoc-no-fork")
public class TestJavadocNoForkReport extends JavadocNoForkReport {
    // ----------------------------------------------------------------------
    // Javadoc Options (should be inline with options defined in TestJavadocJar)
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
    // Mojo Parameters (should be inline with options defined in TestJavadocJar)
    // ----------------------------------------------------------------------

    /**
     * Specifies the Test Javadoc resources directory to be included in the Javadoc (i.e. package.html, images...).
     * <br/>
     * Could be used in addition of <code>docfilessubdirs</code> parameter.
     * <br/>
     * See <a href="#docfilessubdirs">docfilessubdirs</a>.
     *
     * @since 2.5
     */
    @Parameter(alias = "javadocDirectory", defaultValue = "${project.basedir}/src/test/javadoc")
    private File testJavadocDirectory;

    // ----------------------------------------------------------------------
    // Report Mojo Parameters
    // ----------------------------------------------------------------------

    /**
     * The name of the Test Javadoc report to be displayed in the Maven Generated Reports page
     * (i.e. <code>project-reports.html</code>).
     *
     * @since 2.5
     */
    @Parameter(property = "testName", alias = "name")
    private String testName;

    /**
     * The description of the Test Javadoc report to be displayed in the Maven Generated Reports page
     * (i.e. <code>project-reports.html</code>).
     *
     * @since 2.5
     */
    @Parameter(property = "testDescription", alias = "description")
    private String testDescription;

    // ----------------------------------------------------------------------
    // Report public methods
    // ----------------------------------------------------------------------

    @Override
    protected void executeReport(Locale unusedLocale) throws MavenReportException {
        addMainJavadocLink();

        super.executeReport(unusedLocale);
    }

    @Override
    public String getName(Locale locale) {
        if (testName == null || testName.isEmpty()) {
            return getBundle(locale).getString("report.test-javadoc.name");
        }

        return testName;
    }

    @Override
    public String getDescription(Locale locale) {
        if (testDescription == null || testDescription.isEmpty()) {
            return getBundle(locale).getString("report.test-javadoc.description");
        }

        return testDescription;
    }

    // ----------------------------------------------------------------------
    // Protected methods
    // Important Note: should be inline with methods defined in TestJavadocJar
    // ----------------------------------------------------------------------

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

    /**
     * Gets the resource bundle for the specified locale.
     *
     * @param locale The locale of the currently generated report.
     * @return The resource bundle for the requested locale.
     */
    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(
                "test-javadoc-report", locale, getClass().getClassLoader());
    }

    /**
     * Add the <code>../apidocs</code> to the links parameter so Test report could be linked to the Main report.
     */
    private void addMainJavadocLink() {
        if (links == null) {
            links = new ArrayList<>();
        }

        File apidocs = new File(getReportOutputDirectory(), "apidocs");
        if (apidocs.isDirectory() && !links.contains("../apidocs")) {
            links.add("../apidocs");
        }
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
