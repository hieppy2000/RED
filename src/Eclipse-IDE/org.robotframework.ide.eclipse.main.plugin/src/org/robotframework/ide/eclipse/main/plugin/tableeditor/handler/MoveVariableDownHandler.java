package org.robotframework.ide.eclipse.main.plugin.tableeditor.handler;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.tools.compat.parts.DIHandler;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.robotframework.ide.eclipse.main.plugin.RobotSuiteFileSection;
import org.robotframework.ide.eclipse.main.plugin.RobotVariable;
import org.robotframework.ide.eclipse.main.plugin.cmd.MoveVariableDownCommand;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.RobotEditorCommandsStack;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.handler.MoveVariableDownHandler.E4MoveVariableDownHandler;
import org.robotframework.viewers.Selections;

public class MoveVariableDownHandler extends DIHandler<E4MoveVariableDownHandler> {

    public MoveVariableDownHandler() {
        super(E4MoveVariableDownHandler.class);
    }

    public static class E4MoveVariableDownHandler {

        @Inject
        private RobotEditorCommandsStack stack;

        @Execute
        public Object moveVariableDown(@Named(Selections.SELECTION) final IStructuredSelection selection) {
            final RobotVariable selectedVariable = Selections.getSingleElement(selection, RobotVariable.class);
            final RobotSuiteFileSection variablesSection = (RobotSuiteFileSection) selectedVariable.getParent();
            final int index = variablesSection.getChildren().indexOf(selectedVariable);

            stack.execute(new MoveVariableDownCommand(variablesSection, index));
            return null;
        }
    }
}
