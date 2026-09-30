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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.Language;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.archiver.MavenArchiveConfiguration;
import org.apache.maven.shared.archiver.MavenArchiver;
import org.apache.maven.shared.archiver.MavenArchiverException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;

/**
 * Bundles the Javadoc documentation for <code>main Java code</code> in an <b>NON aggregator</b> project into
 * a jar using the standard <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">
 * Javadoc Tool</a>.
 *
 * @since 2.0
 */
@Mojo(name = "jar", defaultPhase = "package")
public class JavadocJarMojo extends AbstractJavadocMojo {
    /**
     * Includes all generated Javadoc files
     */
    private static final String[] DEFAULT_INCLUDES = new String[] {"**/**"};

    /**
     * Excludes all processing files.
     *
     * @see AbstractJavadocMojo#DEBUG_JAVADOC_SCRIPT_NAME
     * @see AbstractJavadocMojo#OPTIONS_FILE_NAME
     * @see AbstractJavadocMojo#PACKAGES_FILE_NAME
     * @see AbstractJavadocMojo#ARGFILE_FILE_NAME
     * @see AbstractJavadocMojo#FILES_FILE_NAME
     */
    private static final String[] DEFAULT_EXCLUDES = new String[] {
        DEBUG_JAVADOC_SCRIPT_NAME, OPTIONS_FILE_NAME, PACKAGES_FILE_NAME, ARGFILE_FILE_NAME, FILES_FILE_NAME
    };

    // ----------------------------------------------------------------------
    // Mojo Parameters
    // ----------------------------------------------------------------------

    /**
     * Specifies the directory where the generated jar file will be put.
     */
    @Parameter(defaultValue = "${project.build.directory}")
    private String jarOutputDirectory;

    /**
     * Specifies the filename that will be used for the generated jar file. Please note that <code>-javadoc</code>
     * or <code>-test-javadoc</code> will be appended to the file name.
     */
    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    /**
     * Specifies whether to attach the generated artifact to the project helper.
     * <br/>
     */
    @Parameter(property = "attach", defaultValue = "true")
    private boolean attach;

    /**
     * The archive configuration to use.
     * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
     *
     * @since 2.5
     */
    @Parameter
    private MavenArchiveConfiguration archive = new JavadocArchiveConfiguration();

    /**
     * Path to the default MANIFEST file to use. It will be used if
     * <code>useDefaultManifestFile</code> is set to <code>true</code>.
     *
     * @since 2.5
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/MANIFEST.MF", required = true, readonly = true)
    private Path defaultManifestFile;

    /**
     * Set this to <code>true</code> to enable the use of the <code>defaultManifestFile</code>.
     * <br/>
     *
     * @since 2.5
     */
    @Parameter(defaultValue = "false")
    private boolean useDefaultManifestFile;

    /**
     * @since 2.10
     */
    @Parameter(property = "maven.javadoc.classifier", defaultValue = "javadoc", required = true)
    private String classifier;

    /**
     * Detect the links for all modules defined in the project.
     * <br/>
     * If {@code reactorProjects} is defined in a non-aggregator way, it generates default offline links
     * between modules based on the defined project's URLs. For instance, if a parent project has two projects
     * <code>module1</code> and <code>module2</code>, the <code>-linkoffline</code> will be:
     * <br/>
     * The added Javadoc <code>-linkoffline</code> parameter for <b>module1</b> will be
     * <code>/absolute/path/to/</code><b>module2</b><code>/target/site/apidocs</code>
     * <br/>
     * The added Javadoc <code>-linkoffline</code> parameter for <b>module2</b> will be
     * <code>/absolute/path/to/</code><b>module1</b><code>/target/site/apidocs</code>
     *
     * @see #offlineLinks
     * @since 2.6
     */
    @Parameter(property = "detectOfflineLinks", defaultValue = "false")
    private boolean detectOfflineLinks;

    /** {@inheritDoc} */
    @Override
    protected void doExecute() throws MojoException {
        if (skip) {
            getLog().info("Skipping javadoc generation");
            return;
        }

        if (!isAggregator() || !isPomPackaging(project)) {
            if (!isJavaProject(project)) {
                getLog().info("Not executing Javadoc as the project is not a Java classpath-capable package");
                return;
            }
        }

        try {
            executeReport(DEFAULT_LOCALE);
        } catch (MavenReportException e) {
            failOnError("MavenReportException: Error while generating Javadoc", e);
        } catch (RuntimeException e) {
            failOnError("RuntimeException: Error while generating Javadoc", e);
        }

        File javadocOutputDirectory = new File(getPluginReportOutputDirectory());
        if (javadocOutputDirectory.exists()) {
            try {
                File outputFile = generateArchive(javadocOutputDirectory, finalName + "-" + getClassifier() + ".jar");

                if (!attach) {
                    getLog().info("NOT adding javadoc to attached artifacts list.");
                } else {
                    // TODO: these introduced dependencies on the project are going to become problematic - can we
                    // export it
                    //  through metadata instead?
                    ProducedArtifact artifact = session.createProducedArtifact(
                            project.getGroupId(),
                            project.getArtifactId(),
                            project.getVersion(),
                            getClassifier(),
                            null,
                            "javadoc");
                    getProjectManager().attachArtifact(project, artifact, outputFile.toPath());
                }
            } catch (ArchiverException e) {
                failOnError("ArchiverException: Error while creating archive", e);
            } catch (IOException e) {
                failOnError("IOException: Error while creating archive", e);
            } catch (RuntimeException e) {
                failOnError("RuntimeException: Error while creating archive", e);
            }
        } else {
            getLog().info("No Javadoc in project. Archive not created.");
        }
    }

    // ----------------------------------------------------------------------
    // Protected methods
    // ----------------------------------------------------------------------

    /**
     * @return the wanted classifier, i.e. <code>javadoc</code> or <code>test-javadoc</code>
     */
    protected String getClassifier() {
        return classifier;
    }

    // ----------------------------------------------------------------------
    // private methods
    // ----------------------------------------------------------------------

    /**
     * Method that creates the jar file
     *
     * @param javadocFiles the directory where the generated jar file will be put
     * @param jarFileName the filename of the generated jar file
     * @return a File object that contains the generated jar file
     * @throws ArchiverException {@link ArchiverException}
     * @throws IOException {@link IOException}
     */
    private File generateArchive(File javadocFiles, String jarFileName) throws ArchiverException, IOException {
        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(new JarArchiver());
        archiver.setCreatedBy("Maven Javadoc Plugin", "org.apache.maven.plugins", "maven-javadoc-plugin");

        // configure for Reproducible Builds based on outputTimestamp value
        archiver.configureReproducibleBuild(outputTimestamp);

        if (javadocFiles.exists()) {
            archiver.getArchiver().addDirectory(javadocFiles, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
        }

        getProjectManager()
                .getEnabledSourceRoots(project, ProjectScope.MAIN, Language.RESOURCES)
                .map(root -> project.getBasedir().resolve(root.directory()))
                .filter(directory -> directory.endsWith("maven-shared-archive-resources"))
                .forEach(directory -> archiver.getArchiver().addDirectory(directory.toFile()));

        if (useDefaultManifestFile && Files.exists(defaultManifestFile) && archive.getManifestFile() == null) {
            getLog().debug("Adding existing MANIFEST to archive. Found under: " + defaultManifestFile);
            archive.setManifestFile(defaultManifestFile);
        }

        File outputFile = new File(jarOutputDirectory, jarFileName);

        // Why do we do this?
        if (outputFile.exists()) {
            outputFile.delete();
        }
        archiver.setOutputFile(outputFile);

        try {
            archiver.createArchive(session, project, archive);
        } catch (MavenArchiverException e) {
            throw new ArchiverException("Error creating Javadoc archive: " + e.getMessage(), e);
        }

        return outputFile;
    }

    @Override
    public boolean isDetectOfflineLinks() {
        return detectOfflineLinks;
    }
}
