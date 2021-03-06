/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project;

import static org.robotframework.ide.eclipse.main.plugin.RedPlugin.newCoreException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.rf.ide.core.execution.agent.RobotAgentEventListener;
import org.rf.ide.core.execution.agent.TestsMode;
import org.rf.ide.core.execution.server.AgentConnectionServer;
import org.rf.ide.core.execution.server.AgentServerKeepAlive;
import org.rf.ide.core.execution.server.AgentServerTestsStarter;
import org.rf.ide.core.executor.RobotRuntimeEnvironment;
import org.rf.ide.core.testdata.model.table.variables.names.VariableNamesSupport;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.launch.AgentConnectionServerJob;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;

/**
 * @author bembenek
 */
public abstract class AbstractAutoDiscoverer {

    private static final AtomicBoolean IS_DRY_RUN_RUNNING = new AtomicBoolean(false);

    private static final int VIRTUAL_ENV_SEARCH_DEPTH = 1;

    final RobotProject robotProject;

    private final List<? extends IResource> resources;

    private final LibrariesSourcesCollector librariesSourcesCollector;

    private final IDryRunTargetsCollector dryRunTargetsCollector;

    private AgentConnectionServerJob serverJob;

    AbstractAutoDiscoverer(final RobotProject robotProject, final List<? extends IResource> resources,
            final LibrariesSourcesCollector librariesSourcesCollector,
            final IDryRunTargetsCollector dryRunTargetsCollector) {
        this.robotProject = robotProject;
        this.resources = resources;
        this.librariesSourcesCollector = librariesSourcesCollector;
        this.dryRunTargetsCollector = dryRunTargetsCollector;
    }

    public Job start() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
        final Shell parent = workbenchWindow != null ? workbenchWindow.getShell() : null;
        return start(parent);
    }

    abstract Job start(Shell parent);

    final boolean lockDryRun() {
        return IS_DRY_RUN_RUNNING.compareAndSet(false, true);
    }

    final void unlockDryRun() {
        IS_DRY_RUN_RUNNING.set(false);
    }

    void stopDiscovering() {
        if (serverJob != null) {
            serverJob.stopServer();
        }
    }

    void startDiscovering(final IProgressMonitor monitor) throws CoreException, InterruptedException {
        final SubMonitor subMonitor = SubMonitor.convert(monitor);
        subMonitor.subTask("Preparing Robot dry run execution...");
        subMonitor.setWorkRemaining(3);

        try {
            final RobotRuntimeEnvironment runtimeEnvironment = robotProject.getRuntimeEnvironment();
            if (runtimeEnvironment == null) {
                throw newCoreException(
                        "There is no active runtime environment for project '" + robotProject.getName() + "'");
            }

            collectLibrarySources(runtimeEnvironment);
            subMonitor.worked(1);

            dryRunTargetsCollector.collectSuiteNamesAndAdditionalProjectsLocations(robotProject, resources);
            subMonitor.worked(1);

            subMonitor.subTask("Executing Robot dry run...");
            if (!subMonitor.isCanceled()) {
                executeDryRun(runtimeEnvironment,
                        suiteName -> subMonitor.subTask("Executing Robot dry run on suite: " + suiteName));
            }
            subMonitor.worked(1);
        } finally {
            subMonitor.done();
        }
    }

    private void collectLibrarySources(final RobotRuntimeEnvironment runtimeEnvironment) throws CoreException {
        if (!runtimeEnvironment.isVirtualenv()
                || RedPlugin.getDefault().getPreferences().isProjectModulesRecursiveAdditionOnVirtualenvEnabled()) {
            librariesSourcesCollector.collectPythonAndJavaLibrariesSources();
        } else {
            librariesSourcesCollector.collectPythonAndJavaLibrariesSources(VIRTUAL_ENV_SEARCH_DEPTH);
        }
    }

    private void executeDryRun(final RobotRuntimeEnvironment runtimeEnvironment,
            final Consumer<String> startSuiteHandler) throws InterruptedException {
        final String host = AgentConnectionServer.DEFAULT_CONNECTION_HOST;
        final int port = AgentConnectionServer.findFreePort();
        final int timeout = AgentConnectionServer.DEFAULT_CONNECTION_TIMEOUT;

        serverJob = startDryRunServer(host, port, timeout, startSuiteHandler);

        startDryRunClient(runtimeEnvironment, port, timeout);

        serverJob.join();
    }

    private AgentConnectionServerJob startDryRunServer(final String host, final int port, final int timeout,
            final Consumer<String> startSuiteHandler) throws InterruptedException {
        final AgentServerTestsStarter testsStarter = new AgentServerTestsStarter(TestsMode.RUN);
        final AgentConnectionServerJob serverJob = AgentConnectionServerJob.setupServerAt(host, port)
                .withConnectionTimeout(timeout, TimeUnit.SECONDS)
                .agentEventsListenedBy(testsStarter)
                .agentEventsListenedBy(createDryRunEventListener(startSuiteHandler))
                .agentEventsListenedBy(new AgentServerKeepAlive())
                .start()
                .waitForServer();

        testsStarter.allowClientTestsStart();

        return serverJob;
    }

    abstract RobotAgentEventListener createDryRunEventListener(final Consumer<String> startSuiteHandler);

    private void startDryRunClient(final RobotRuntimeEnvironment runtimeEnvironment, final int port,
            final int timeout) {
        runtimeEnvironment.startLibraryAutoDiscovering(port, timeout, dryRunTargetsCollector.getSuiteNames(),
                getVariableMappings(), getDataSourcePaths(), librariesSourcesCollector.getEnvironmentSearchPaths());
    }

    private List<String> getDataSourcePaths() {
        final List<String> dataSourcePaths = new ArrayList<>();
        final IPath projectLocation = robotProject.getProject().getLocation();
        if (projectLocation != null) {
            dataSourcePaths.add(projectLocation.toFile().getAbsolutePath());
        }
        for (final File additionalProjectsLocation : dryRunTargetsCollector.getAdditionalProjectsLocations()) {
            dataSourcePaths.add(additionalProjectsLocation.getAbsolutePath());
        }
        return dataSourcePaths;
    }

    private List<String> getVariableMappings() {
        return robotProject.getRobotProjectConfig()
                .getVariableMappings()
                .stream()
                .map(v -> VariableNamesSupport.extractUnifiedVariableNameWithoutBrackets(v.getName()) + ":"
                        + v.getValue())
                .collect(Collectors.toList());
    }

    public interface IDryRunTargetsCollector {

        void collectSuiteNamesAndAdditionalProjectsLocations(RobotProject robotProject,
                List<? extends IResource> resources);

        List<String> getSuiteNames();

        List<File> getAdditionalProjectsLocations();
    }

    public static class AutoDiscovererException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public AutoDiscovererException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

}
