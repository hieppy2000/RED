package org.robotframework.ide.eclipse.main.plugin.project.editor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.robotframework.ide.eclipse.main.plugin.project.RobotProjectConfig;

class RedProjectConfigurationFileChangeListener implements IResourceChangeListener {

    private final IProject project;

    private final Runnable performOnChange;

    RedProjectConfigurationFileChangeListener(final IProject project, final Runnable performOnChange) {
        this.project = project;
        this.performOnChange = performOnChange;
    }

    @Override
    public void resourceChanged(final IResourceChangeEvent event) {
        if (event.getType() == IResourceChangeEvent.POST_CHANGE && event.getDelta() != null) {
            final IResourceDelta delta = event.getDelta();
            try {
                delta.accept(new IResourceDeltaVisitor() {
                    @Override
                    public boolean visit(final IResourceDelta delta) {
                        if (delta.getResource().getFullPath().segmentCount() > 2) {
                            return false;
                        }
                        if (delta.getResource().equals(project.getFile(RobotProjectConfig.FILENAME))) {
                            performOnChange.run();
                            return false;
                        }
                        return true;
                    }
                });
            } catch (final CoreException e) {
                // nothing to do
            }
        }
    }
}