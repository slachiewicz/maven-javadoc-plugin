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
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkFactory;
import org.apache.maven.doxia.siterenderer.DocumentRenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.reporting.MavenReportException;

/**
 * Generates documentation for the <code>Java code</code> in a <b>NON aggregator</b> project using the standard
 * <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>. Note that this
 * goal does require generation of sources before site generation, e.g. by invoking {@code mvn clean deploy site}.
 *
 * @author <a href="mailto:mfriedenhagen@apache.org">Mirko Friedenhagen</a>
 * @since 2.10
 * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>
 * @see <a href="http://docs.oracle.com/javase/1.4.2/docs/tooldocs/windows/javadoc.html#options">Javadoc Options</a>
 */
@Mojo(name = "javadoc-no-fork")
public class JavadocNoForkReport extends AbstractJavadocMojo implements MultiPageReportCompat {
    // ----------------------------------------------------------------------
    // Report Mojo Parameters
    // ----------------------------------------------------------------------

    /** The current shared report output directory to use */
    private File reportOutputDirectory;

    /**
     * The name of the Javadoc report to be displayed in the Maven Generated Reports page
     * (e.g. <code>project-reports.html</code>).
     *
     * @since 2.1
     */
    @Parameter(property = "name")
    private String name;

    /**
     * The description of the Javadoc report to be displayed in the Maven Generated Reports page
     * (i.e. <code>project-reports.html</code>).
     *
     * @since 2.1
     */
    @Parameter(property = "description")
    private String description;

    // ----------------------------------------------------------------------
    // Report public methods
    // ----------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public String getName(Locale locale) {
        if (name == null || name.isEmpty()) {
            return getBundle(locale).getString("report.javadoc.name");
        }

        return name;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription(Locale locale) {
        if (description == null || description.isEmpty()) {
            return getBundle(locale).getString("report.javadoc.description");
        }

        return description;
    }

    /** {@inheritDoc} */
    @Override
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        generate(sink, null, locale);
    }

    /** {@inheritDoc} */
    @Override
    public void generate(Sink sink, SinkFactory sinkFactory, Locale locale) throws MavenReportException {
        try {
            executeReport(locale);
        } catch (MavenReportException | RuntimeException e) {
            if (failOnError) {
                throw e;
            }
            getLog().error("Error while creating javadoc report: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated use getOutputPath() instead
     */
    @Override
    @Deprecated
    public String getOutputName() {
        return getOutputPath();
    }

    /** {@inheritDoc} */
    @Override
    public String getOutputPath() {
        return (isTest() ? "test" : "") + "apidocs" + "/index";
    }

    /** {@inheritDoc} */
    @Override
    public boolean isExternalReport() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <br>
     * The logic is the following:
     * <table><caption>Can-generate-report Matrix</caption>
     *   <tbody>
     *     <tr>
     *       <th> isAggregator </th>
     *       <th> hasSourceFiles </th>
     *       <th> isRootProject </th>
     *       <th> Generate Report </th>
     *     </tr>
     *     <tr>
     *       <td>True</td>
     *       <td>True</td>
     *       <td>True</td>
     *       <td>True</td>
     *     </tr>
     *     <tr>
     *       <td>True</td>
     *       <td>True</td>
     *       <td>False</td>
     *       <td>False</td>
     *     </tr>
     *     <tr>
     *       <td>True</td>
     *       <td>False</td>
     *       <td>True</td>
     *       <td>False</td>
     *     </tr>
     *     <tr>
     *       <td>True</td>
     *       <td>False</td>
     *       <td>False</td>
     *       <td>False</td>
     *     </tr>
     *     <tr>
     *       <td>False</td>
     *       <td>True</td>
     *       <td>True</td>
     *       <td>True</td>
     *     </tr>
     *     <tr>
     *       <td>False</td>
     *       <td>True</td>
     *       <td>False</td>
     *       <td>True</td>
     *     </tr>
     *     <tr>
     *        <td>False</td>
     *        <td>False</td>
     *        <td>True</td>
     *        <td>False</td>
     *      </tr>
     *      <tr>
     *        <td>False</td>
     *        <td>False</td>
     *        <td>False</td>
     *        <td>False</td>
     *      </tr>
     *    </tbody>
     *  </table>
     */
    @Override
    public boolean canGenerateReport() throws MavenReportException {
        if (skip) {
            return false;
        }

        Collection<JavadocModule> sourcePaths = getSourcePaths();

        Collection<Path> collectedSourcePaths =
                sourcePaths.stream().flatMap(e -> e.getSourcePaths().stream()).collect(Collectors.toList());

        Map<Path, Collection<String>> files = getFiles(collectedSourcePaths);

        return canGenerateReport(files);
    }

    /** {@inheritDoc} */
    @Override
    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    /** {@inheritDoc} */
    @Override
    public File getReportOutputDirectory() {
        if (reportOutputDirectory == null) {
            reportOutputDirectory = new File(getOutputDirectory());
        }

        return reportOutputDirectory;
    }

    /** {@inheritDoc} */
    @Override
    public void setReportOutputDirectory(File reportOutputDirectory) {
        this.reportOutputDirectory = reportOutputDirectory;
        this.outputDirectory = reportOutputDirectory;
    }

    /** {@inheritDoc} */
    @Override
    protected String getPluginReportOutputDirectory() {
        return getReportOutputDirectory().getAbsolutePath() + "/" + (isTest() ? "test" : "") + "apidocs";
    }

    /** {@inheritDoc} */
    @Override
    protected void doExecute() throws MojoException {
        try {
            if (!canGenerateReport()) {
                String reportMojoInfo = pluginId() + ":" + mojoExecution.getGoal();
                getLog().info("Skipping " + reportMojoInfo + " report goal");
                return;
            }
        } catch (MavenReportException e) {
            throw new MojoException("Failed to determine whether report can be generated", e);
        }

        File outputDirectory = new File(getOutputDirectory());

        String filename = getOutputPath() + ".html";

        Locale locale = DEFAULT_LOCALE;

        try {
            String reportMojoInfo = pluginId() + ":" + mojoExecution.getGoal();
            DocumentRenderingContext docRenderingContext =
                    new DocumentRenderingContext(outputDirectory, filename, reportMojoInfo);

            SiteRendererSink sink = new SiteRendererSink(docRenderingContext);

            generate(sink, null, locale);
        } catch (MavenReportException | RuntimeException e) {
            failOnError("An error has occurred in " + getName(Locale.ENGLISH) + " report generation", e);
        }
    }

    /**
     * Gets the resource bundle for the specified locale.
     *
     * @param locale the locale of the currently generated report
     * @return the resource bundle for the requested locale
     */
    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("javadoc-report", locale, getClass().getClassLoader());
    }
}
