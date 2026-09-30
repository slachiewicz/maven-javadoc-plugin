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
import java.util.Locale;

import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.plugins.javadoc.options.OfflineLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.testing.plugin.MojoExtension.setVariableValueToObject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractJavadocMojoTest {
    AbstractJavadocMojo mojo;

    @BeforeEach
    void setUp() {
        mojo = new AbstractJavadocMojo() {
            @Override
            public void doExecute() {}
        };
        mojo.log = mock(Log.class);
        Session session = mock(Session.class);
        when(session.getSettings()).thenReturn(Settings.newInstance());
        mojo.session = session;
    }

    @Test
    void testMJAVADOC432DetectLinksMessages() {
        Log log = mock(Log.class);
        when(log.isErrorEnabled()).thenReturn(true);
        mojo.log = log;
        mojo.outputDirectory = new File("target/test-classes");

        // first continues after warning, next exits with warning
        assertThat(mojo.isValidJavadocLink(new File("pom.xml").getPath(), true)).isFalse();
        assertThat(mojo.isValidJavadocLink("file://%%", true)).isFalse();
        assertThat(mojo.isValidJavadocLink(new File("pom.xml").toURI().toString(), true))
                .isFalse();
        verify(log, times(4)).warn(anyString());
        verify(log, never()).error(anyString());

        // first continues after error, next exits with error
        assertThat(mojo.isValidJavadocLink(new File("pom.xml").getPath(), false))
                .isFalse();
        assertThat(mojo.isValidJavadocLink("file://%%", false)).isFalse();
        assertThat(mojo.isValidJavadocLink(new File("pom.xml").toURI().toString(), false))
                .isFalse();
        verify(log, times(4)).error(anyString());
        verify(log, times(4)).warn(anyString()); // no extra warnings
    }

    @Test
    void testMJAVADOC527DetectLinksRecursion() {
        Log log = mock(Log.class);
        when(log.isErrorEnabled()).thenReturn(true);
        mojo.log = log;
        mojo.outputDirectory = new File("target/test-classes");

        assertThat(mojo.isValidJavadocLink("http://javamail.java.net/mailapi/apidocs", false))
                .isFalse();
        assertThat(mojo.isValidJavadocLink("http://commons.apache.org/proper/commons-lang/apidocs", false))
                .isTrue();
    }

    @Test
    void toRelativeStripsTheBaseDirectory() {
        File basedir = new File("/home/project").getAbsoluteFile();

        assertThat(AbstractJavadocMojo.toRelative(basedir, basedir.getPath() + "/src/site"))
                .isEqualTo("src/site");
    }

    @Test
    void toRelativeAnswersDotForTheBaseDirectoryItself() {
        File basedir = new File("/home/project").getAbsoluteFile();

        assertThat(AbstractJavadocMojo.toRelative(basedir, basedir.getPath())).isEqualTo(".");
    }

    @Test
    void toRelativeLeavesAPathOutsideTheBaseDirectoryAlone() {
        File basedir = new File("/home/project").getAbsoluteFile();
        String elsewhere = new File("/elsewhere/docs").getAbsolutePath().replace('\\', '/');

        // deliberately not a chain of ".." segments, which is what Path.relativize would give
        assertThat(AbstractJavadocMojo.toRelative(basedir, elsewhere)).isEqualTo(elsewhere);
    }

    @Test
    void toRelativeNormalisesBackslashes() {
        File basedir = new File("/home/project").getAbsoluteFile();

        assertThat(AbstractJavadocMojo.toRelative(basedir, basedir.getPath() + "\\src\\site"))
                .isEqualTo("src/site");
    }

    @Test
    void emptyReleaseFallsBackToSourceForTheApiLink(@TempDir Path optionsDir) throws Exception {
        setVariableValueToObject(mojo, "detectJavaApiLink", true);
        setVariableValueToObject(mojo, "javadocOptionsDir", optionsDir.toFile());
        // what Maven injects for a declared but blank maven.compiler.release
        setVariableValueToObject(mojo, "release", "");
        setVariableValueToObject(mojo, "source", "11");

        OfflineLink link = mojo.getDefaultJavadocApiLink();

        assertThat(link).isNotNull();
        assertThat(link.getUrl()).isEqualTo("https://docs.oracle.com/en/java/javase/11/docs/api/");
    }

    @Test
    void releaseWinsOverSourceForTheApiLink(@TempDir Path optionsDir) throws Exception {
        setVariableValueToObject(mojo, "detectJavaApiLink", true);
        setVariableValueToObject(mojo, "javadocOptionsDir", optionsDir.toFile());
        setVariableValueToObject(mojo, "release", "11");
        setVariableValueToObject(mojo, "source", "17");

        OfflineLink link = mojo.getDefaultJavadocApiLink();

        assertThat(link).isNotNull();
        assertThat(link.getUrl()).isEqualTo("https://docs.oracle.com/en/java/javase/11/docs/api/");
    }

    @Test
    void siteLocaleFollowsTheSiteToolRules() {
        assertThat(AbstractJavadocMojo.getSiteLocale(null)).isEqualTo(Locale.ENGLISH);
        assertThat(AbstractJavadocMojo.getSiteLocale("default")).isEqualTo(Locale.ENGLISH);
        assertThat(AbstractJavadocMojo.getSiteLocale("de")).isEqualTo(Locale.GERMAN);
        assertThat(AbstractJavadocMojo.getSiteLocale("de_CH")).isEqualTo(new Locale("de", "CH"));
        // the first locale of a list that the JVM knows wins
        assertThat(AbstractJavadocMojo.getSiteLocale("xx_YY_ZZ_AA,fr")).isEqualTo(Locale.FRENCH);
        // nothing usable falls back to English
        assertThat(AbstractJavadocMojo.getSiteLocale("xx_YY_ZZ_AA")).isEqualTo(Locale.ENGLISH);
    }
}
