package org.robotframework.ide.eclipse.main.plugin.launch;

import static com.google.common.collect.Lists.newArrayList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.part.FileEditorInput;
import org.robotframework.ide.core.executor.ILineHandler;
import org.robotframework.ide.core.executor.RobotRuntimeEnvironment;
import org.robotframework.ide.core.executor.RobotRuntimeEnvironment.RunCommandLine;
import org.robotframework.ide.core.executor.SuiteExecutor;
import org.robotframework.ide.eclipse.main.plugin.RobotFramework;
import org.robotframework.ide.eclipse.main.plugin.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.debug.RobotPartListener;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugTarget;
import org.robotframework.viewers.Selections;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class RobotLaunchConfigurationDelegate extends LaunchConfigurationDelegate implements
        ILaunchConfigurationDelegate, ILaunchShortcut {

    private final ILaunchConfigurationType launchConfigurationType;

    private final ILaunchManager launchManager;
    
    private final RobotEventBroker robotEventBroker;
    
    private final AtomicBoolean isConfigurationRunning = new AtomicBoolean(false);
    
    public RobotLaunchConfigurationDelegate() {
        launchManager = DebugPlugin.getDefault().getLaunchManager();
        launchConfigurationType = launchManager.getLaunchConfigurationType(RobotLaunchConfiguration.TYPE_ID);
        robotEventBroker = new RobotEventBroker((IEventBroker) PlatformUI.getWorkbench().getService(IEventBroker.class));
    }

    @Override
    public void launch(final ISelection selection, final String mode) {
        if (selection instanceof IStructuredSelection) {
            final List<IResource> resources = Selections.getElements((IStructuredSelection) selection, IResource.class);
            if (!resources.isEmpty()) {
                launch(resources, mode);
            }
        }
    }

    @Override
    public void launch(final IEditorPart editor, final String mode) {
        final IEditorInput input = editor.getEditorInput();
        if (input instanceof FileEditorInput) {
            final IResource file = ((FileEditorInput) input).getFile();
            launch(newArrayList(file), mode);
        }
    }

    private void launch(final List<IResource> resources, final String mode) {
        if (resources.isEmpty()) {
            throw new IllegalStateException("There should be at least one suite selected for launching");
        }
        final WorkspaceJob job = new WorkspaceJob("Launching Robot Tests") {
            @Override
            public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
                robotEventBroker.sendClearEventToMessageLogView();

                ILaunchConfiguration config = findLaunchConfiguration(resources);
                if (config == null) {
                    config = RobotLaunchConfiguration.createDefault(launchConfigurationType, resources);
                }
                config.launch(mode, monitor);

                return Status.OK_STATUS;
            }
        };
        job.setUser(false);
        job.schedule();
    }

    private ILaunchConfiguration findLaunchConfiguration(final List<IResource> resources) throws CoreException {
        for (final ILaunchConfiguration configuration : launchManager.getLaunchConfigurations(launchConfigurationType)) {
            final RobotLaunchConfiguration robotConfig = new RobotLaunchConfiguration(configuration);
            if (robotConfig.isSuitableFor(resources)) {
                return configuration;
            }
        }
        return null;
    }

    @Override
    public void launch(final ILaunchConfiguration configuration, final String mode, final ILaunch launch,
            final IProgressMonitor monitor) throws CoreException {
        if (!ILaunchManager.RUN_MODE.equals(mode) && !ILaunchManager.DEBUG_MODE.equals(mode)) {
            throw newCoreException("Unrecognized launch mode: '" + mode + "'", null);
        }

        if (isConfigurationRunning.getAndSet(true)) {
            return;
        }
        try {
            doLaunch(configuration, mode, launch, monitor);
        } catch (final IOException e) {
            throw newCoreException("Unable to launch Robot", e);
        } finally {
            isConfigurationRunning.set(false);
        }
    }

    public void doLaunch(final ILaunchConfiguration configuration, final String mode, final ILaunch launch,
            final IProgressMonitor monitor) throws CoreException, IOException {
        // FIXME : use monitor for progress reporting and cancellation
        // possibility

        final RobotLaunchConfiguration robotConfig = new RobotLaunchConfiguration(configuration);
        final SuiteExecutor executor = robotConfig.getExecutor();
        final IProject project = getProject(robotConfig);
        final List<IResource> suiteResources = getSuiteResources(robotConfig, project);
        final RobotRuntimeEnvironment runtimeEnvironment = getRobotRuntimeEnvironment(project);

        final boolean isDebugging = ILaunchManager.DEBUG_MODE.equals(mode);

        final List<String> suites = getSuitesToRun(suiteResources);
        final String userArguments = robotConfig.getExecutorArguments();
        final RunCommandLine cmdLine = runtimeEnvironment.createCommandLineCall(executor, project.getLocation()
                .toFile(), suites, userArguments, isDebugging);
        final String executorVersion = runtimeEnvironment.getVersion(executor);
        if (cmdLine.getPort() < 0) {
            throw newCoreException("Unable to find free port", null);
        }

        if (!isDebugging) {
            runtimeEnvironment.startTestRunnerAgentHandler(cmdLine.getPort(), new ILineHandler() {
                @Override
                public void processLine(final String line) {
                    robotEventBroker.sendAppendLineEventToMessageLogView(line);
                }
            });
        }

        final Process process = DebugPlugin.exec(cmdLine.getCommandLine(), project.getLocation().toFile());
        final String description = runtimeEnvironment.getFile().getAbsolutePath();
        final IProcess eclipseProcess = DebugPlugin.newProcess(launch, process, description);
        printCommandOnConsole(cmdLine.getCommandLine(), executorVersion, configuration, description);

        RobotPartListener robotPartListener = null;
        if (isDebugging) {
            robotPartListener = new RobotPartListener(robotEventBroker);
            registerPartListener(robotPartListener);

            final IDebugTarget target = new RobotDebugTarget(launch, eclipseProcess, cmdLine.getPort(),
                    (IFile) suiteResources.get(0), robotPartListener, robotEventBroker);
            launch.addDebugTarget(target);
        }

        try {
            if (process != null) {
                process.waitFor();
            }
        } catch (final InterruptedException e) {
            throw newCoreException("Robot process was interrupted", e);
        } finally {
            if (robotPartListener != null) {
                unregisterPartListener(robotPartListener);
            }
        }
    }

    protected final RobotRuntimeEnvironment getRobotRuntimeEnvironment(final RobotLaunchConfiguration robotConfig)
            throws CoreException {
        return getRobotRuntimeEnvironment(getProject(robotConfig));
    }

    private RobotRuntimeEnvironment getRobotRuntimeEnvironment(final IProject project) throws CoreException {
        final RobotProject robotProject = RobotFramework.getModelManager().getModel().createRobotProject(project);
        final RobotRuntimeEnvironment runtimeEnvironment = robotProject.getRuntimeEnvironment();
        if (runtimeEnvironment == null) {
            throw newCoreException("There is no active runtime environment for project '" + project.getName() + "'",
                    null);
        }
        if (!runtimeEnvironment.hasRobotInstalled()) {
            throw newCoreException("The runtime environment " + runtimeEnvironment.getFile().getAbsolutePath()
                    + " is either not a python installation or it has no Robot installed", null);
        }
        return runtimeEnvironment;
    }

    private List<IResource> getSuiteResources(final RobotLaunchConfiguration robotConfig, final IProject project)
            throws CoreException {
        final List<String> suitePaths = robotConfig.getSuitePaths();
        if (suitePaths.isEmpty()) {
            throw newCoreException("There are no suites selected in launch configuration", null);
        }
        final List<IResource> suiteResources = Lists.transform(suitePaths, new Function<String, IResource>() {
            @Override
            public IResource apply(final String suitePath) {
                return project.findMember(org.eclipse.core.runtime.Path.fromPortableString(suitePath));
            }
        });
        for (final IResource resource : suiteResources) {
            if (resource == null || !resource.exists()) {
                throw newCoreException("Suite '" + resource.getFullPath().toString() + "' does not exist in project '"
                        + project.getName() + "'", null);
            }
        }
        return suiteResources;
    }

    private IProject getProject(final RobotLaunchConfiguration robotConfig) throws CoreException {
        final String projectName = robotConfig.getProjectName();
        final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists()) {
            throw newCoreException("Project '" + projectName + "' cannot be found in workspace", null);
        }
        return project;
    }

    private static CoreException newCoreException(final String message, final Throwable cause) {
        return new CoreException(new Status(IStatus.ERROR, RobotFramework.PLUGIN_ID, message, cause));
    }

    private List<String> getSuitesToRun(final List<IResource> suites) {
        final List<String> suiteNames = new ArrayList<String>();

        for (final IResource suite : suites) {
            suiteNames.add(createSuiteName(suite.getFullPath().removeFileExtension()));
        }
        return suiteNames;
    }

    private String createSuiteName(final IPath path) {
        final List<String> upperCased = Lists.transform(Arrays.asList(path.segments()), new Function<String, String>() {
            @Override
            public String apply(final String segment) {
                return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, segment);
            }
        });
        return Joiner.on('.').join(upperCased);
    }

    private void printCommandOnConsole(final String[] cmd, final String executorVersion,
            final ILaunchConfiguration configuration,
            final String description) throws IOException {
        final String consoleName = configuration.getName() + " [Robot] " + description;
        final IConsole[] existingConsoles = ConsolePlugin.getDefault().getConsoleManager().getConsoles();
        for (final IConsole console : existingConsoles) {
            if (console instanceof IOConsole && console.getName().contains(consoleName)) {
                final String command = "Command: " + Joiner.on(' ').join(cmd) + "\n";
                final String env = "Suite Executor: " + executorVersion + "\n";
                ((IOConsole) console).newOutputStream().write(command + env);
            }
        }
    }

    private static void registerPartListener(final RobotPartListener listener) {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                workbench.getActiveWorkbenchWindow().getActivePage().addPartListener(listener);
            }
        });
    }

    private static void unregisterPartListener(final RobotPartListener listener) {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                workbench.getActiveWorkbenchWindow().getActivePage().removePartListener(listener);
            }
        });
    }
}
