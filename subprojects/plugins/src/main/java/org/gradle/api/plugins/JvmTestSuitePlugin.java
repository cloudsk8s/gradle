/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;

import java.util.concurrent.Callable;

import static org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME;

/**
 * <p>A {@link org.gradle.api.Plugin} which allows for defining, compiling and running groups of Java tests against (potentially)
 * various different target environments.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/test_suite_plugin.html">Test Suite plugin reference</a>
 *
 * @since 7.3
 */
@Incubating
public class JvmTestSuitePlugin implements Plugin<Project> {
    public static final String DEFAULT_TEST_SUITE_NAME = SourceSet.TEST_SOURCE_SET_NAME;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.test-suite-base");
        project.getPluginManager().apply("org.gradle.java-base");
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
        testSuites.registerBinding(JvmTestSuite.class, DefaultJvmTestSuite.class);

        // TODO: Deprecate this behavior?
        // Why would any Test task created need to use the test source set's classes?
        project.getTasks().withType(Test.class).configureEach(test -> {
            SourceSet testSourceSet = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
            test.getConventionMapping().map("testClassesDirs", () -> testSourceSet.getOutput().getClassesDirs());
            test.getConventionMapping().map("classpath", () -> testSourceSet.getRuntimeClasspath());
            test.getModularity().getInferModulePath().convention(java.getModularity().getInferModulePath());
        });

        testSuites.withType(JvmTestSuite.class).all(testSuite -> {
            testSuite.getTargets().all(target -> {
                target.getTestTask().configure(test -> {
                    test.getConventionMapping().map("testClassesDirs", () -> testSuite.getSources().getOutput().getClassesDirs());
                    test.getConventionMapping().map("classpath", () -> testSuite.getSources().getRuntimeClasspath());
                });
            });
        });

        configureBuiltInTest(project, testing, java);
    }

    private void configureBuiltInTest(Project project, TestingExtension testing, JavaPluginExtension java) {
        final NamedDomainObjectProvider<JvmTestSuite> testSuite = testing.getSuites().register(DEFAULT_TEST_SUITE_NAME, JvmTestSuite.class, suite -> {
            final Callable<FileCollection> mainSourceSetOutput = () -> java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
            final Callable<FileCollection> testSourceSetOutput = () -> java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput();

            suite.getSources().setCompileClasspath(project.getObjects().fileCollection().from(mainSourceSetOutput, project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
            suite.getSources().setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSetOutput, mainSourceSetOutput, project.getConfigurations().getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));
        });

        // Force the realization of this test suite, targets and task
        testSuite.get();

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(testSuite));
    }
}
