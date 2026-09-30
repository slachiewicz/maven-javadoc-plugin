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

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.Language;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.VersionConstraint;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.impl.DefaultSourceRoot;
import org.apache.maven.impl.InternalSession;
import org.apache.maven.model.v4.MavenStaxReader;
import org.apache.maven.testing.plugin.stubs.ProjectStub;
import org.apache.maven.testing.plugin.stubs.SessionMock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the mock session for the mojo tests: the source roots come from the POM of the project under test, the
 * dependency resolution yields no dependencies and there is no toolchain.
 */
final class TestSessions {
    private TestSessions() {}

    static InternalSession create() {
        return create(Paths.get("target/local-repo"));
    }

    /**
     * @param localRepo the directory that artifacts are resolved from, using the Maven repository layout
     * @return the mock session
     */
    static InternalSession create(Path localRepo) {
        InternalSession session = SessionMock.getMockSession(localRepo.toString());

        ProjectManager projectManager = mock(ProjectManager.class);
        when(session.getService(ProjectManager.class)).thenReturn(projectManager);
        when(projectManager.getEnabledSourceRoots(any(), eq(ProjectScope.MAIN), eq(Language.JAVA_FAMILY)))
                .thenAnswer(iom -> sourceRoots(iom.getArgument(0, Project.class), ProjectScope.MAIN));
        when(projectManager.getEnabledSourceRoots(any(), eq(ProjectScope.TEST), eq(Language.JAVA_FAMILY)))
                .thenAnswer(iom -> sourceRoots(iom.getArgument(0, Project.class), ProjectScope.TEST));
        when(projectManager.getEnabledSourceRoots(any(), any(), eq(Language.RESOURCES)))
                .thenAnswer(iom ->
                        resourceRoots(iom.getArgument(0, Project.class), iom.getArgument(1, ProjectScope.class)));
        when(projectManager.getExecutionProject(any())).thenReturn(Optional.empty());
        when(projectManager.getPath(any())).thenReturn(Optional.empty());
        when(projectManager.getRemoteProjectRepositories(any())).thenReturn(Collections.emptyList());

        DependencyResolverResult noDependencies = mock(DependencyResolverResult.class);
        when(noDependencies.getDependencies()).thenReturn(Collections.emptyMap());
        when(noDependencies.getPaths()).thenReturn(Collections.emptyList());
        DependencyResolver dependencyResolver = mock(DependencyResolver.class);
        when(dependencyResolver.resolve(any(), any(Project.class), any(PathScope.class)))
                .thenReturn(noDependencies);
        when(session.getService(DependencyResolver.class)).thenReturn(dependencyResolver);

        ToolchainManager toolchainManager = mock(ToolchainManager.class);
        when(toolchainManager.getToolchainFromBuildContext(any(), any())).thenReturn(Optional.empty());
        when(session.getService(ToolchainManager.class)).thenReturn(toolchainManager);

        when(dependencyResolver.resolve(any(), nullable(DependencyCoordinates.class), any(PathScope.class)))
                .thenReturn(noDependencies);

        when(session.withRemoteRepositories(any())).thenReturn(session);
        when(session.createArtifactCoordinates(any(), any(), any(), any(), any(), any()))
                .thenAnswer(iom -> new Coordinates(
                        iom.getArgument(0),
                        iom.getArgument(1),
                        iom.getArgument(2),
                        iom.getArgument(3),
                        iom.getArgument(4)));
        when(session.resolveArtifact(any(ArtifactCoordinates.class), any())).thenAnswer(iom -> {
            Coordinates coordinates = (Coordinates) iom.getArgument(0);
            Path path = coordinates.path(localRepo);
            if (!Files.isRegularFile(path)) {
                throw new ArtifactResolverException("Could not find " + path, null, null);
            }
            DownloadedArtifact artifact = mock(DownloadedArtifact.class);
            when(artifact.getPath()).thenReturn(path);
            return artifact;
        });

        when(session.getSettings()).thenReturn(Settings.newInstance());
        when(session.getEffectiveProperties()).thenReturn(Map.of());
        return session;
    }

    /**
     * @param project the project
     * @param scope main or test
     * @return the resource directories of the project, which is {@code src/main/resources} unless the POM declares
     *         resources
     */
    private static Stream<DefaultSourceRoot> resourceRoots(Project project, ProjectScope scope) {
        if (scope != ProjectScope.MAIN) {
            return Stream.empty();
        }
        Build build = project.getModel().getBuild();
        if (build != null && !build.getResources().isEmpty()) {
            return build.getResources().stream().map(r -> new DefaultSourceRoot(project.getBasedir(), scope, r));
        }
        return Stream.of(new DefaultSourceRoot(
                scope, Language.RESOURCES, project.getBasedir().resolve("src/main/resources")));
    }

    /**
     * @param project the project
     * @param scope main or test
     * @return the source directory of the project, which is {@code src/main/java} or {@code src/test/java} unless the
     *         POM says otherwise; the model no longer carries a default {@code <sourceDirectory>}
     */
    private static Stream<DefaultSourceRoot> sourceRoots(Project project, ProjectScope scope) {
        boolean main = scope == ProjectScope.MAIN;
        Build build = project.getModel().getBuild();
        String configured = build == null ? null : main ? build.getSourceDirectory() : build.getTestSourceDirectory();
        Path directory = project.getBasedir()
                .resolve(configured != null ? configured : main ? "src/main/java" : "src/test/java");
        return Stream.of(new DefaultSourceRoot(scope, Language.JAVA_FAMILY, directory));
    }

    /**
     * Makes the dependency resolution of the project for the given path scope yield the given file as a dependency.
     *
     * @param session the session created with {@link #create()}
     * @param pathScope the path scope that the dependency is resolved for
     * @param scope the scope of the dependency
     * @param artifactId the artifact id of the dependency
     * @param file the file of the dependency
     */
    static void addDependency(
            InternalSession session, PathScope pathScope, DependencyScope scope, String artifactId, Path file) {
        Dependency dependency = mock(Dependency.class);
        when(dependency.getGroupId()).thenReturn("groupId");
        when(dependency.getArtifactId()).thenReturn(artifactId);
        when(dependency.key()).thenReturn("groupId:" + artifactId + ":jar::1");
        when(dependency.getExtension()).thenReturn("jar");
        when(dependency.getClassifier()).thenReturn("");
        when(dependency.getScope()).thenReturn(scope);
        DependencyResolverResult result = mock(DependencyResolverResult.class);
        when(result.getDependencies()).thenReturn(Map.of(dependency, file));
        when(result.getPaths()).thenReturn(List.of(file));
        when(session.getService(DependencyResolver.class).resolve(any(), any(Project.class), eq(pathScope)))
                .thenReturn(result);
    }

    /**
     * Reads the project at the given base directory without a parent lookup, like the reactor does.
     *
     * @param basedir the directory of the project
     * @param pom the name of the POM file in the directory
     * @return a stub of the project
     * @throws Exception if the POM cannot be read
     */
    static ProjectStub project(Path basedir, String pom) throws Exception {
        Model model;
        try (Reader reader = Files.newBufferedReader(basedir.resolve(pom))) {
            model = new MavenStaxReader().read(reader);
        }
        String groupId = model.getGroupId() != null
                ? model.getGroupId()
                : model.getParent().getGroupId();
        String version = model.getVersion() != null
                ? model.getVersion()
                : model.getParent().getVersion();
        ProjectStub stub = new ProjectStub();
        stub.setGroupId(groupId);
        stub.setArtifactId(model.getArtifactId());
        stub.setVersion(version);
        stub.setPackaging(model.getPackaging());
        stub.setModel(model);
        stub.setBasedir(basedir);
        stub.setPomPath(basedir.resolve(pom));
        return stub;
    }

    /**
     * Coordinates that resolve to a file in a repository with the standard layout.
     */
    private static final class Coordinates implements ArtifactCoordinates {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;
        private final String extension;

        private Coordinates(String groupId, String artifactId, String version, String classifier, String extension) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier == null ? "" : classifier;
            this.extension = extension;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getExtension() {
            return extension;
        }

        Path path(Path localRepo) {
            return localRepo
                    .resolve(groupId.replace('.', '/'))
                    .resolve(artifactId)
                    .resolve(version)
                    .resolve(artifactId + "-" + version + (classifier.isEmpty() ? "" : "-" + classifier) + "."
                            + extension);
        }
    }
}
