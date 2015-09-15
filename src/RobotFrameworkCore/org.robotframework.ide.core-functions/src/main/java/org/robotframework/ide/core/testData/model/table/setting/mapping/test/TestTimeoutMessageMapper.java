/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.core.testData.model.table.setting.mapping.test;

import java.util.List;
import java.util.Stack;

import org.robotframework.ide.core.testData.model.FilePosition;
import org.robotframework.ide.core.testData.model.RobotFileOutput;
import org.robotframework.ide.core.testData.model.table.SettingTable;
import org.robotframework.ide.core.testData.model.table.mapping.ElementsUtility;
import org.robotframework.ide.core.testData.model.table.mapping.IParsingMapper;
import org.robotframework.ide.core.testData.model.table.setting.TestTimeout;
import org.robotframework.ide.core.testData.text.read.ParsingState;
import org.robotframework.ide.core.testData.text.read.RobotLine;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotToken;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotTokenType;

import com.google.common.annotations.VisibleForTesting;


public class TestTimeoutMessageMapper implements IParsingMapper {

    private final ElementsUtility utility;


    public TestTimeoutMessageMapper() {
        this.utility = new ElementsUtility();
    }


    @Override
    public RobotToken map(RobotLine currentLine,
            Stack<ParsingState> processingState,
            RobotFileOutput robotFileOutput, RobotToken rt, FilePosition fp,
            String text) {
        rt.setType(RobotTokenType.SETTING_TEST_TIMEOUT_MESSAGE);
        rt.setText(new StringBuilder(text));
        rt.setRaw(new StringBuilder(text));

        SettingTable settings = robotFileOutput.getFileModel()
                .getSettingTable();
        List<TestTimeout> timeouts = settings.getTestTimeouts();
        if (!timeouts.isEmpty()) {
            timeouts.get(timeouts.size() - 1).addMessageArgument(rt);
        } else {
            // FIXME: some error
        }
        processingState
                .push(ParsingState.SETTING_TEST_TIMEOUT_MESSAGE_ARGUMENTS);

        return rt;
    }


    @Override
    public boolean checkIfCanBeMapped(RobotFileOutput robotFileOutput,
            RobotLine currentLine, RobotToken rt, String text,
            Stack<ParsingState> processingState) {
        boolean result;
        if (!processingState.isEmpty()) {
            ParsingState currentState = utility
                    .getCurrentStatus(processingState);
            if (currentState == ParsingState.SETTING_TEST_TIMEOUT_VALUE
                    || currentState == ParsingState.SETTING_TEST_TIMEOUT_MESSAGE_ARGUMENTS) {
                result = true;
            } else if (currentState == ParsingState.SETTING_TEST_TIMEOUT) {
                List<TestTimeout> testTimeouts = robotFileOutput.getFileModel()
                        .getSettingTable().getTestTimeouts();
                result = checkIfHasAlreadyValue(testTimeouts);
            } else {
                result = false;
            }
        } else {
            result = false;
        }
        return result;
    }


    @VisibleForTesting
    protected boolean checkIfHasAlreadyValue(List<TestTimeout> testTimeouts) {
        boolean result = false;
        for (TestTimeout setting : testTimeouts) {
            result = (setting.getTimeout() != null);
            result = result || !setting.getMessageArguments().isEmpty();
            if (result) {
                break;
            }
        }

        return result;
    }

}
