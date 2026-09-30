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

import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

/**
 * Bundle {@link AbstractJavadocMojo#javadocDirectory}, along with javadoc configuration options such
 * as taglet, doclet, and link information into a deployable artifact. This artifact can then be consumed
 * by the javadoc plugin mojos when used by the <code>includeDependencySources</code> option, to generate
 * javadocs that are somewhat consistent with those generated in the original project itself.
 *
 * @since 2.7
 */
@Mojo(name = "resource-bundle", defaultPhase = "package")
public class ResourcesBundleMojo extends AbstractJavadocMojo {

    /**
     * Bundle options path.
     */
    public static final String BUNDLE_OPTIONS_PATH = "META-INF/maven/javadoc-options.xml";

    /**
     * Resources directory path.
     */
    public static final String RESOURCES_DIR_PATH = "resources";

    /**
     * Base name of artifacts produced by this project. This will be combined with
     * {@link AbstractJavadocMojo#getAttachmentClassifier()} to produce the name for this bundle
     * jar.
     */
    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName;

    /**
     * Helper component to provide an easy mechanism for attaching an artifact to the project for
     * installation/deployment.
     */
    /**
     * Archiver manager, used to manage jar builder.
     */

    /**
     * Assemble a new {@link org.apache.maven.plugins.javadoc.options.JavadocOptions JavadocOptions} instance that
     * contains the configuration options in this
     * mojo, which are a subset of those provided in derivatives of the {@link AbstractJavadocMojo}
     * class (most of the javadoc mojos, in other words). Then, bundle the contents of the
     * <code>javadocDirectory</code> along with the assembled JavadocOptions instance (serialized to
     * META-INF/maven/javadoc-options.xml) into a project attachment for installation/deployment.
     *
     * {@inheritDoc}
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    protected void doExecute() throws MojoException {
        if (skip) {
            getLog().info("Skipping javadoc resource bundle generation");
            return;
        }

        try {
            buildJavadocOptions();
        } catch (IOException e) {
            throw new MojoException("Failed to generate javadoc-options file: " + e.getMessage(), e);
        }

        Archiver archiver = new JarArchiver();

        File optionsFile = getJavadocOptionsFile();
        File bundleFile =
                new File(getProject().getBuild().getDirectory(), finalName + "-" + getAttachmentClassifier() + ".jar");
        try {
            archiver.addFile(optionsFile, BUNDLE_OPTIONS_PATH);

            File javadocDir = getJavadocDirectory();
            if (javadocDir.isDirectory()) {
                DefaultFileSet fileSet = DefaultFileSet.fileSet(javadocDir).prefixed(RESOURCES_DIR_PATH + "/");
                archiver.addFileSet(fileSet);
            }

            archiver.setDestFile(bundleFile);
            archiver.createArchive();
        } catch (ArchiverException | IOException e) {
            throw new MojoException(
                    "Failed to assemble javadoc-resources bundle archive. Reason: " + e.getMessage(), e);
        }

        ProducedArtifact artifact = session.createProducedArtifact(
                getProject().getGroupId(),
                getProject().getArtifactId(),
                getProject().getVersion(),
                getAttachmentClassifier(),
                null,
                "jar");
        getProjectManager().attachArtifact(getProject(), artifact, bundleFile.toPath());
    }
}
