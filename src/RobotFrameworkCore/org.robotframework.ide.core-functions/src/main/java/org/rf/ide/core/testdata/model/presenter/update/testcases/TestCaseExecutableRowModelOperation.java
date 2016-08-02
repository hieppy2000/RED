package org.rf.ide.core.testdata.model.presenter.update.testcases;

import java.util.List;

import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.presenter.update.ITestCaseTableElementOperation;
import org.rf.ide.core.testdata.model.table.RobotExecutableRow;
import org.rf.ide.core.testdata.model.table.testcases.TestCase;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;


public class TestCaseExecutableRowModelOperation implements ITestCaseTableElementOperation {

    @Override
    public boolean isApplicable(final ModelType elementType) {
        return elementType == ModelType.TEST_CASE_EXECUTABLE_ROW;
    }

    @Override
    public boolean isApplicable(final IRobotTokenType elementType) {
        return elementType == RobotTokenType.TEST_CASE_ACTION_NAME;
    }

    @Override
    public AModelElement<?> create(final TestCase testCase, final String actionName, final List<String> args,
            final String comment) {
        final RobotExecutableRow<TestCase> row = new RobotExecutableRow<>();
        row.setParent(testCase);

        row.setAction(RobotToken.create(actionName));
        for (final String argument : args) {
            row.addArgument(RobotToken.create(argument));
        }
        if (comment != null && !comment.isEmpty()) {
            row.setComment(comment);
        }
        return row;
    }

    @Override
    public void update(final AModelElement<?> modelElement, final int index, final String value) {
        final RobotExecutableRow<?> row = (RobotExecutableRow<?>) modelElement;

        if (value != null) {
            row.setArgument(index, value);
        } else if (index < row.getArguments().size()) {
            row.removeElementToken(index);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void remove(final TestCase testCase, final AModelElement<?> modelElement) {
        testCase.removeExecutableRow((RobotExecutableRow<TestCase>) modelElement);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void insert(final TestCase testCase, final int index, final AModelElement<?> modelElement) {
        final RobotExecutableRow<TestCase> executableRow = (RobotExecutableRow<TestCase>) modelElement;

        testCase.addTestExecutionRow(executableRow, index);
    }
}