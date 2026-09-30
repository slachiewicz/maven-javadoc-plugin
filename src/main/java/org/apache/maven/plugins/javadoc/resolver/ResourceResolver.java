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
package org.apache.maven.plugins.javadoc.resolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.MavenException;
import org.apache.maven.plugins.javadoc.AbstractJavadocMojo;
import org.apache.maven.plugins.javadoc.JavadocModule;
import org.apache.maven.plugins.javadoc.JavadocUtil;
import org.apache.maven.plugins.javadoc.ResourcesBundleMojo;
import org.apache.maven.plugins.javadoc.options.JavadocOptions;
import org.apache.maven.plugins.javadoc.options.io.xpp3.JavadocOptionsXpp3Reader;
import org.apache.maven.shared.artifact.filter.resolve.transform.ArtifactIncludeFilterTransformer;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the source and resource artifacts of the dependencies of a project.
 *
 * @since 3.1.0
 */
public final class ResourceResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceResolver.class);

    public static final String SOURCES_CLASSIFIER = "sources";

    public static final String TEST_SOURCES_CLASSIFIER = "test-sources";

    private static final List<String> SOURCE_VALID_CLASSIFIERS =
            Arrays.asList(SOURCES_CLASSIFIER, TEST_SOURCES_CLASSIFIER);

    private static final List<String> RESOURCE_VALID_CLASSIFIERS = Arrays.asList(
            AbstractJavadocMojo.JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER,
            AbstractJavadocMojo.TEST_JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER);

    /**
     * Resolves the dependency javadoc bundles: the resources that dependencies contribute to the javadoc.
     *
     * @param config the resolution configuration
     * @return the bundles, never {@code null}
     * @throws IOException if a bundle cannot be read
     */
    public List<JavadocBundle> resolveDependencyJavadocBundles(final SourceResolverConfig config) throws IOException {
        final List<JavadocBundle> bundles = new ArrayList<>();

        final Map<String, Project> projectMap = reactorProjectMap(config);

        final Map<Dependency, Path> artifacts =
                ArtifactSupport.resolveAllDependencies(config.session(), config.project());

        final List<Dependency> forResourceResolution = new ArrayList<>(artifacts.size());
        for (final Dependency artifact : artifacts.keySet()) {
            final Project p = projectMap.get(
                    artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion());
            if (p != null) {
                bundles.addAll(resolveBundleFromProject(config, p));
            } else {
                forResourceResolution.add(artifact);
            }
        }

        bundles.addAll(resolveBundlesFromArtifacts(config, forResourceResolution));

        return bundles;
    }

    /**
     * Resolves the dependency sources.
     *
     * @param config the resolution configuration
     * @return the source paths of the dependencies, never {@code null}
     * @throws MavenException if a dependency cannot be resolved or a source archive cannot be unpacked
     */
    public Collection<JavadocModule> resolveDependencySourcePaths(final SourceResolverConfig config) {
        final Collection<JavadocModule> mappedDirs = new ArrayList<>();

        final Map<String, Project> projectMap = reactorProjectMap(config);

        final Map<Dependency, Path> artifacts =
                ArtifactSupport.resolveAllDependencies(config.session(), config.project());

        for (final Map.Entry<Dependency, Path> entry : artifacts.entrySet()) {
            final Dependency artifact = entry.getKey();
            final String key = artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion();
            final Project p = projectMap.get(key);
            if (p != null) {
                mappedDirs.add(
                        new JavadocModule(key, entry.getValue().toFile(), resolveFromProject(config, p, artifact)));
            } else {
                JavadocModule m = resolveFromArtifact(config, artifact, entry.getValue());
                if (m != null) {
                    mappedDirs.add(m);
                }
            }
        }

        return mappedDirs;
    }

    private static Map<String, Project> reactorProjectMap(SourceResolverConfig config) {
        final Map<String, Project> projectMap = new HashMap<>();
        if (config.reactorProjects() != null) {
            for (final Project p : config.reactorProjects()) {
                projectMap.put(p.getGroupId() + ':' + p.getArtifactId() + ':' + p.getVersion(), p);
            }
        }
        return projectMap;
    }

    private static Predicate<Dependency> filter(SourceResolverConfig config) {
        if (config.filter() == null) {
            return d -> true;
        }
        return new ArtifactIncludeFilterTransformer().transform(config.filter());
    }

    private static List<JavadocBundle> resolveBundleFromProject(SourceResolverConfig config, Project project)
            throws IOException {
        List<JavadocBundle> bundles = new ArrayList<>();

        List<String> classifiers = new ArrayList<>();
        if (config.includeCompileSources()) {
            classifiers.add(AbstractJavadocMojo.JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER);
        }

        if (config.includeTestSources()) {
            classifiers.add(AbstractJavadocMojo.TEST_JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER);
        }

        for (String classifier : classifiers) {
            Path optionsFile = Path.of(project.getBuild().getDirectory())
                    .resolve("javadoc-bundle-options/javadoc-options-" + classifier + ".xml");
            if (!Files.exists(optionsFile)) {
                continue;
            }

            try (InputStream stream = Files.newInputStream(optionsFile)) {
                JavadocOptions options = new JavadocOptionsXpp3Reader().read(stream);
                bundles.add(new JavadocBundle(
                        options,
                        project.getBasedir()
                                .resolve(options.getJavadocResourcesDirectory())
                                .toFile()));
            } catch (XmlPullParserException e) {
                throw new IOException(
                        "Failed to read javadoc options from: " + optionsFile + "\nReason: " + e.getMessage(), e);
            }
        }

        return bundles;
    }

    private List<JavadocBundle> resolveBundlesFromArtifacts(
            final SourceResolverConfig config, final List<Dependency> artifacts) throws IOException {
        final Predicate<Dependency> filter = filter(config);
        final Set<Coordinates> toResolve = new LinkedHashSet<>();

        for (final Dependency artifact : artifacts) {
            if (!filter.test(artifact)) {
                continue;
            }

            if (config.includeCompileSources()) {
                toResolve.add(new Coordinates(artifact, AbstractJavadocMojo.JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER));
            }

            if (config.includeTestSources()) {
                toResolve.add(
                        new Coordinates(artifact, AbstractJavadocMojo.TEST_JAVADOC_RESOURCES_ATTACHMENT_CLASSIFIER));
            }
        }

        Collection<Path> dirs = resolveAndUnpack(toResolve, config, RESOURCE_VALID_CLASSIFIERS, false);

        List<JavadocBundle> result = new ArrayList<>();

        for (Path d : dirs) {
            File dir = d.toFile();
            File resources = new File(dir, ResourcesBundleMojo.RESOURCES_DIR_PATH);
            JavadocOptions options = null;

            File javadocOptions = new File(dir, ResourcesBundleMojo.BUNDLE_OPTIONS_PATH);
            if (javadocOptions.exists()) {
                try (InputStream reader = Files.newInputStream(javadocOptions.toPath())) {
                    options = new JavadocOptionsXpp3Reader().read(reader);
                } catch (XmlPullParserException e) {
                    throw new IOException("Failed to parse javadoc options: " + e.getMessage(), e);
                }
            }

            result.add(new JavadocBundle(options, resources));
        }

        return result;
    }

    private JavadocModule resolveFromArtifact(final SourceResolverConfig config, final Dependency artifact, Path file) {
        if (!filter(config).test(artifact)) {
            return null;
        }

        final Set<Coordinates> toResolve = new LinkedHashSet<>();

        if (config.includeCompileSources()) {
            toResolve.add(new Coordinates(artifact, SOURCES_CLASSIFIER));
        }

        if (config.includeTestSources()) {
            toResolve.add(new Coordinates(artifact, TEST_SOURCES_CLASSIFIER));
        }

        Collection<Path> sourcePaths = resolveAndUnpack(toResolve, config, SOURCE_VALID_CLASSIFIERS, true);

        return new JavadocModule(
                artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion(),
                file.toFile(),
                sourcePaths);
    }

    private Collection<Path> resolveAndUnpack(
            final Set<Coordinates> artifacts,
            final SourceResolverConfig config,
            final List<String> validClassifiers,
            final boolean propagateErrors) {
        final Session session = config.session();

        final List<Path> result = new ArrayList<>(artifacts.size());
        for (final Coordinates a : artifacts) {
            if (!validClassifiers.contains(a.classifier)) {
                continue;
            }

            Path resolvedArtifact;
            try {
                resolvedArtifact = ArtifactSupport.resolveArtifact(
                        session, config.project(), a.groupId, a.artifactId, a.version, a.classifier, "jar");
            } catch (ArtifactResolverException e) {
                LOGGER.debug("Unable to resolve {}: {}", a, e.getMessage());
                continue;
            }

            final File d = new File(config.outputBasedir(), a.artifactId + "-" + a.version + "-" + a.classifier);

            if (!d.exists()) {
                d.mkdirs();
            }

            try {
                final UnArchiver unArchiver = new ZipUnArchiver();

                unArchiver.setDestDirectory(d);
                unArchiver.setSourceFile(resolvedArtifact.toFile());

                unArchiver.extract();

                result.add(d.toPath().toAbsolutePath());
            } catch (final ArchiverException e) {
                if (propagateErrors) {
                    throw new MavenException("Failed to unpack: " + a, e);
                }
            }
        }

        return result;
    }

    private static Collection<Path> resolveFromProject(
            final SourceResolverConfig config, final Project reactorProject, final Dependency artifact) {
        final List<String> dirs = new ArrayList<>();

        if (filter(config).test(artifact)) {
            if (config.includeCompileSources()) {
                dirs.addAll(ArtifactSupport.sourceRoots(config.session(), reactorProject, ProjectScope.MAIN));
            }

            if (config.includeTestSources()) {
                dirs.addAll(ArtifactSupport.sourceRoots(config.session(), reactorProject, ProjectScope.TEST));
            }
        }

        return JavadocUtil.pruneDirs(reactorProject, dirs);
    }

    private static final class Coordinates {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;

        private Coordinates(Dependency dependency, String classifier) {
            this.groupId = dependency.getGroupId();
            this.artifactId = dependency.getArtifactId();
            this.version = dependency.getVersion().toString();
            this.classifier = classifier;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Coordinates)) {
                return false;
            }
            Coordinates c = (Coordinates) o;
            return groupId.equals(c.groupId)
                    && artifactId.equals(c.artifactId)
                    && version.equals(c.version)
                    && classifier.equals(c.classifier);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(groupId, artifactId, version, classifier);
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":jar:" + classifier + ":" + version;
        }
    }
}
