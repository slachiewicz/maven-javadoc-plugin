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
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.shared.artifact.filter.resolve.AndFilter;

/**
 *
 */
public class SourceResolverConfig {
    private final Session session;

    private final Project project;

    private AndFilter filter;

    private List<Project> reactorProjects;

    private final File outputBasedir;

    private boolean compileSourceIncluded;

    private boolean testSourceIncluded;

    /**
     * @param session {@link Session}
     * @param project {@link Project}
     * @param outputBasedir The output base directory.
     */
    public SourceResolverConfig(final Session session, final Project project, final File outputBasedir) {
        this.session = session;
        this.project = project;
        this.outputBasedir = outputBasedir;
    }

    /**
     * @param filter {@link AndFilter}
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withFilter(final AndFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * @param reactorProjects The list of reactor projects.
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withReactorProjects(final List<Project> reactorProjects) {
        this.reactorProjects = reactorProjects;
        return this;
    }

    /**
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withCompileSources() {
        compileSourceIncluded = true;
        return this;
    }

    /**
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withoutCompileSources() {
        compileSourceIncluded = false;
        return this;
    }

    /**
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withTestSources() {
        testSourceIncluded = true;
        return this;
    }

    /**
     * @return {@link SourceResolverConfig}
     */
    public SourceResolverConfig withoutTestSources() {
        testSourceIncluded = false;
        return this;
    }

    /**
     * @return {@link Project}
     */
    public Project project() {
        return project;
    }

    /**
     * @return {@link Session}
     */
    public Session session() {
        return session;
    }

    /**
     * @return {@link AndFilter}
     */
    public AndFilter filter() {
        return filter;
    }

    /**
     * @return list of {@link Project}
     */
    public List<Project> reactorProjects() {
        return reactorProjects;
    }

    /**
     * @return {@link #outputBasedir}
     */
    public File outputBasedir() {
        return outputBasedir;
    }

    /**
     * @return {@link #compileSourceIncluded}
     */
    public boolean includeCompileSources() {
        return compileSourceIncluded;
    }

    /**
     * @return {@link #testSourceIncluded}
     */
    public boolean includeTestSources() {
        return testSourceIncluded;
    }
}
