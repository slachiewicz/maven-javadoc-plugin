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

import org.apache.maven.api.plugin.annotations.Execute;
import org.apache.maven.api.plugin.annotations.Mojo;

/**
 * Generates documentation for the <code>Java code</code> in a <b>NON aggregator</b> project using the standard
 * <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @since 2.0
 * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/man/javadoc.html">Javadoc Tool</a>
 */
@Mojo(name = "javadoc")
@Execute(phase = "generate-sources")
public class JavadocReport extends JavadocNoForkReport {}
