/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.presenter.update.testcases;

import java.util.List;

import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.presenter.update.IExecutablesStepsHolderElementOperation;
import org.rf.ide.core.testdata.model.table.testcases.TestCase;
import org.rf.ide.core.testdata.model.table.testcases.TestCaseTemplate;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;

public class TestCaseTemplateModelOperation implements IExecutablesStepsHolderElementOperation<TestCase> {

    @Override
    public boolean isApplicable(final ModelType elementType) {
        return elementType == ModelType.TEST_CASE_TEMPLATE;
    }
    
    @Override
    public boolean isApplicable(final IRobotTokenType elementType) {
        return elementType == RobotTokenType.TEST_CASE_SETTING_TEMPLATE;
    }
    
    @Override
    public AModelElement<?> create(final TestCase testCase, final int index, final String settingName,
            final List<String> args, final String comment) {
        final TestCaseTemplate template = testCase.newTemplate(index);
        template.getDeclaration().setText(settingName);
        template.getDeclaration().setRaw(settingName);

        if (!args.isEmpty()) {
            template.setKeywordName(args.get(0));
            for (int i = 1; i < args.size(); i++) {
                template.addUnexpectedTrashArgument(args.get(i));
            }
        }
        if (comment != null && !comment.isEmpty()) {
            template.setComment(comment);
        }
        return template;
    }

    @Override
    public AModelElement<?> insert(final TestCase testCase, final int index, final AModelElement<?> modelElement) {
        testCase.addElement((TestCaseTemplate) modelElement, index);
        return modelElement;
    }

    @Override
    public void update(final AModelElement<?> modelElement, final int index, final String value) {
        final TestCaseTemplate template = (TestCaseTemplate) modelElement;
        if (index == 0) {
            template.setKeywordName(value != null ? value : "");
        } else if (index > 0) {
            if (value != null) {
                template.setUnexpectedTrashArguments(index - 1, value);
            } else {
                template.removeElementToken(index - 1);
            }
        }
    }

    @Override
    public void update(final AModelElement<?> modelElement, final List<String> newArguments) {
        final TestCaseTemplate template = (TestCaseTemplate) modelElement;

        template.setKeywordName(newArguments.isEmpty() ? null : RobotToken.create(newArguments.get(0)));
        final int elementsToRemove = template.getUnexpectedTrashArguments().size();
        for (int i = 0; i < elementsToRemove; i++) {
            template.removeElementToken(0);
        }
        for (int i = 1; i < newArguments.size(); i++) {
            template.setUnexpectedTrashArguments(i - 1, newArguments.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void remove(final TestCase testCase, final AModelElement<?> modelElement) {
        testCase.removeElement((AModelElement<TestCase>) modelElement);
    }
}
