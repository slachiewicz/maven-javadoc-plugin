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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.Language;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.ProjectManager;

/**
 * Artifact and dependency resolution on top of the Maven 4 session services.
 */
public final class ArtifactSupport {
    private ArtifactSupport() {}

    /**
     * The remote repositories of the project, which {@link Session#resolveArtifact(ArtifactCoordinates)} does not
     * use (it resolves against the session repositories).
     */
    public static List<RemoteRepository> remoteRepositories(Session session, Project project) {
        return session.getService(ProjectManager.class).getRemoteProjectRepositories(project);
    }

    /**
     * Resolves the dependencies of the given project for all scopes, like {@code MavenProject#getTestArtifacts()}.
     * The Maven 4 path scopes have no single scope with compile, provided, runtime and test dependencies, so the
     * {@link PathScope#TEST_COMPILE} and {@link PathScope#TEST_RUNTIME} results are merged.
     *
     * @return the resolved dependencies with their files, never {@code null}
     */
    public static Map<Dependency, Path> resolveAllDependencies(Session session, Project project) {
        DependencyResolver resolver = session.getService(DependencyResolver.class);
        Map<Dependency, Path> result = new LinkedHashMap<>();
        result.putAll(resolver.resolve(session, project, PathScope.TEST_COMPILE).getDependencies());
        Set<String> keys = new LinkedHashSet<>();
        result.keySet().forEach(d -> keys.add(d.key()));
        resolver.resolve(session, project, PathScope.TEST_RUNTIME)
                .getDependencies()
                .forEach((d, p) -> {
                    if (!keys.contains(d.key())) {
                        result.put(d, p);
                    }
                });
        return result;
    }

    /**
     * Resolves one artifact from the repositories of the project.
     */
    public static Path resolveArtifact(
            Session session,
            Project project,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String extension) {
        ArtifactCoordinates coordinates =
                session.createArtifactCoordinates(groupId, artifactId, version, classifier, extension, null);
        DownloadedArtifact artifact = session.resolveArtifact(coordinates, remoteRepositories(session, project));
        return artifact.getPath();
    }

    /**
     * Resolves an artifact and its transitive dependencies in the {@link PathScope#MAIN_COMPILE} scope, the
     * artifact itself first.
     */
    public static List<Path> resolveWithDependencies(
            Session session,
            Project project,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String extension) {
        ArtifactCoordinates coordinates =
                session.createArtifactCoordinates(groupId, artifactId, version, classifier, extension, null);
        DependencyCoordinates root = session.createDependencyCoordinates(coordinates);
        Set<Path> paths = new LinkedHashSet<>();
        paths.add(resolveArtifact(session, project, groupId, artifactId, version, classifier, extension));
        paths.addAll(session.getService(DependencyResolver.class)
                .resolve(
                        session.withRemoteRepositories(remoteRepositories(session, project)),
                        root,
                        PathScope.MAIN_COMPILE)
                .getPaths());
        return new ArrayList<>(paths);
    }

    /**
     * The enabled Java source roots of the project for the given scope, as absolute paths.
     */
    public static List<String> sourceRoots(Session session, Project project, ProjectScope scope) {
        List<String> roots = new ArrayList<>();
        session.getService(ProjectManager.class)
                .getEnabledSourceRoots(project, scope, Language.JAVA_FAMILY)
                .map(root -> project.getBasedir().resolve(root.directory()).toString())
                .forEach(roots::add);
        return roots;
    }
}
