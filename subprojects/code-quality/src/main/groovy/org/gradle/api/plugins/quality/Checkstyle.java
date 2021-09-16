/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.plugins.quality;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.internal.CheckstyleAntInvoker;
import org.gradle.api.plugins.quality.internal.CheckstyleReportsImpl;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Runs Checkstyle against some source files.
 */
@CacheableTask
public class Checkstyle extends SourceTask implements VerificationTask, Reporting<CheckstyleReports> {

    private FileCollection checkstyleClasspath;
    private FileCollection classpath;
    private TextResource config;
    private Map<String, Object> configProperties = new LinkedHashMap<String, Object>();
    private final CheckstyleReports reports;
    private boolean ignoreFailures;
    private int maxErrors;
    private int maxWarnings = Integer.MAX_VALUE;
    private boolean showViolations = true;
    private final DirectoryProperty configDirectory;

    /**
     * The Checkstyle configuration file to use.
     */
    @Internal
    public File getConfigFile() {
        return getConfig() == null ? null : getConfig().asFile();
    }

    /**
     * The Checkstyle configuration file to use.
     */
    public void setConfigFile(File configFile) {
        setConfig(getProject().getResources().getText().fromFile(configFile));
    }

    public Checkstyle() {
        configDirectory = getObjectFactory().directoryProperty();
        reports = getObjectFactory().newInstance(CheckstyleReportsImpl.class, this);
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     *   reports {
     *     html {
     *       destination "build/checkstyle.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    @SuppressWarnings("rawtypes")
    public CheckstyleReports reports(@DelegatesTo(value = CheckstyleReports.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     *   reports {
     *     html {
     *       destination "build/checkstyle.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     * @since 3.0
     */
    @Override
    public CheckstyleReports reports(Action<? super CheckstyleReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @TaskAction
    public void run() {
        WorkQueue workQueue = getWorkerExecutor().processIsolation(worker -> {
            worker.getClasspath().setFrom(getCheckstyleClasspath().getFiles());
        });

        workQueue.submit(CheckstyleAction.class, parameters -> {
            parameters.getConfig().set(getConfigFile());
            parameters.getMaxErrors().set(getMaxErrors());
            parameters.getMaxWarnings().set(getMaxWarnings());
            parameters.getIgnoreFailures().set(getIgnoreFailures());
            parameters.getConfigDirectory().set(getConfigDirectory());
            parameters.getShowViolations().set(isShowViolations());
        });
    }

    @Inject
    public WorkerExecutor getWorkerExecutor() {
        throw new UnsupportedOperationException();
    }

    public interface CheckstyleActionParameters extends WorkParameters {
        RegularFileProperty getConfig();
        Property<Integer> getMaxErrors();
        Property<Integer> getMaxWarnings();
        Property<Boolean> getIgnoreFailures();
        DirectoryProperty getConfigDirectory();
        Property<Boolean> getShowViolations();


    }

    public static abstract class CheckstyleAction implements WorkAction<CheckstyleActionParameters> {

        @Override
        public void execute() {
            Object antBuilder = newInstanceOf("org.gradle.api.internal.project.ant.BasicAntBuilder");
            Object antLogger = newInstanceOf("org.gradle.api.internal.project.ant.AntLoggingAdapter");
            configureAntBuilder(antBuilder, antLogger);

            // Ideally, we'd delegate directly to the AntBuilder, but its Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof its Closure class.
            Object delegate = new AntBuilderDelegate(antBuilder, Thread.currentThread().getContextClassLoader());
            ClosureBackedAction.execute(delegate, new CheckstyleAntInvoker(this, this, getParameters()));
        }

    }

    protected static void configureAntBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method addBuildListener = projectClass.getDeclaredMethod("addBuildListener", buildListenerClass);
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            Method getBuildListeners = projectClass.getDeclaredMethod("getBuildListeners");
            Vector listeners = (Vector) getBuildListeners.invoke(project);
            removeBuildListener.invoke(project, listeners.get(0));
            addBuildListener.invoke(project, antLogger);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    private static Object getProject(Object antBuilder) throws Exception {
        return antBuilder.getClass().getMethod("getProject").invoke(antBuilder);
    }

    private static Object newInstanceOf(String className) {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        try {
            return Class.forName(className).getConstructor().newInstance();
        } catch (Exception e) {
            // should never happen
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The sources for this task are relatively relocatable even though it produces output that
     * includes absolute paths. This is a compromise made to ensure that results can be reused
     * between different builds. The downside is that up-to-date results, or results loaded
     * from cache can show different absolute paths than would be produced if the task was
     * executed.</p>
     */
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The class path containing the Checkstyle library to be used.
     */
    @Classpath
    public FileCollection getCheckstyleClasspath() {
        return checkstyleClasspath;
    }

    /**
     * The class path containing the Checkstyle library to be used.
     */
    public void setCheckstyleClasspath(FileCollection checkstyleClasspath) {
        this.checkstyleClasspath = checkstyleClasspath;
    }

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Nested
    public TextResource getConfig() {
        return config;
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    public void setConfig(TextResource config) {
        this.config = config;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @Nullable
    @Optional
    @Input
    public Map<String, Object> getConfigProperties() {
        return configProperties;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    public void setConfigProperties(@Nullable Map<String, Object> configProperties) {
        this.configProperties = configProperties;
    }

    /**
     * Path to other Checkstyle configuration files.
     * <p>
     * This path will be exposed as the variable {@code config_loc} in Checkstyle's configuration files.
     * </p>
     *
     * @return path to other Checkstyle configuration files
     * @since 6.0
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public DirectoryProperty getConfigDirectory() {
        return configDirectory;
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final CheckstyleReports getReports() {
        return reports;
    }

    /**
     * Whether or not this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Internal
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether this task will ignore failures and continue running the build.
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    @Input
    public int getMaxErrors() {
        return maxErrors;
    }

    /**
     * Set the maximum number of errors that are tolerated before breaking the build.
     *
     * @param maxErrors number of errors allowed
     * @since 3.4
     */
    public void setMaxErrors(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    @Input
    public int getMaxWarnings() {
        return maxWarnings;
    }

    /**
     * Set the maximum number of warnings that are tolerated before breaking the build.
     *
     * @param maxWarnings number of warnings allowed
     * @since 3.4
     */
    public void setMaxWarnings(int maxWarnings) {
        this.maxWarnings = maxWarnings;
    }

    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @return true if violations should be displayed on console
     */
    @Console
    public boolean isShowViolations() {
        return showViolations;
    }

    /**
     * Whether rule violations are to be displayed on the console.
     */
    public void setShowViolations(boolean showViolations) {
        this.showViolations = showViolations;
    }
}
