/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.build.causes;

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordsSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSettingsSection;
import org.robotframework.ide.eclipse.main.plugin.project.build.AdditionalMarkerAttributes;
import org.robotframework.ide.eclipse.main.plugin.project.build.RobotProblem;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.AddPrefixToKeywordUsage;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.ChangeKeywordNameFixer;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.ChangeToFixer;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.CreateKeywordFixer;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.DocumentToDocumentationWordFixer;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.ImportLibraryFixer;
import org.robotframework.ide.eclipse.main.plugin.project.build.fix.RemoveKeywordFixer;

import com.google.common.base.Splitter;

public enum KeywordsProblem implements IProblemCause {
    UNKNOWN_KEYWORD {

        @Override
        public String getProblemDescription() {
            return "Unknown keyword '%s'";
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            final String keywordName = marker.getAttribute(AdditionalMarkerAttributes.NAME, null);
            final String keywordOriginalName = marker.getAttribute(AdditionalMarkerAttributes.ORIGINAL_NAME, null);
            final IFile suiteFile = (IFile) marker.getResource();

            final ArrayList<IMarkerResolution> fixers = newArrayList();
            fixers.addAll(ImportLibraryFixer.createFixers(suiteFile, keywordName));
            fixers.addAll(CreateKeywordFixer.createFixers(keywordOriginalName));
            fixers.addAll(ChangeToFixer.createFixers(RobotProblem.getRegionOf(marker),
                    new SimilaritiesAnalyst().provideSimilarAccessibleKeywords(suiteFile, keywordName)));

            return fixers;
        }
    },
    AMBIGUOUS_KEYWORD {

        @Override
        public String getProblemDescription() {
            return "Ambiguous keyword '%s' reference. Matching keywords are defined in: %s";
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            final ArrayList<IMarkerResolution> fixers = newArrayList();
            final String name = marker.getAttribute(AdditionalMarkerAttributes.NAME, null);
            final List<String> sources = Splitter.on(';')
                    .splitToList(marker.getAttribute(AdditionalMarkerAttributes.SOURCES, ""));

            fixers.addAll(AddPrefixToKeywordUsage.createFixers(name, sources));
            return fixers;
        }
    },
    DEPRECATED_KEYWORD {

        @Override
        public Severity getSeverity() {
            return Severity.WARNING;
        }

        @Override
        public String getProblemDescription() {
            return "Keyword '%s' is deprecated";
        }
    },
    DUPLICATED_KEYWORD {

        @Override
        public String getProblemDescription() {
            return "Duplicated keyword definition '%s'";
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            return newArrayList(new RemoveKeywordFixer(marker.getAttribute(AdditionalMarkerAttributes.NAME, null)));
        }
    },
    EMPTY_KEYWORD {

        @Override
        public String getProblemDescription() {
            return "Keyword '%s' contains no keywords to execute";
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            return newArrayList(new RemoveKeywordFixer(marker.getAttribute(AdditionalMarkerAttributes.NAME, null)));
        }
    },
    KEYWORD_FROM_NESTED_LIBRARY {

        @Override
        public Severity getSeverity() {
            return Severity.WARNING;
        }

        @Override
        public String getProblemDescription() {
            return "Keyword '%s' is from a library nested in a resource file";
        }
    },
    ARGUMENTS_DEFINED_TWICE {

        @Override
        public String getProblemDescription() {
            return "Keyword '%s' defines arguments by using both embedded syntax and [Arguments] setting";
        }
    },
    MISSING_KEYWORD {

        @Override
        public String getProblemDescription() {
            return "There is no keyword to execute specified";
        }
    },
    KEYWORD_OCCURRENCE_NOT_CONSISTENT_WITH_DEFINITION {

        @Override
        public Severity getSeverity() {
            return Severity.WARNING;
        }

        @Override
        public String getProblemDescription() {
            return "Given keyword name '%s' is not consistent with keyword definition: %s";
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            return newArrayList(new ChangeKeywordNameFixer(marker.getAttribute(AdditionalMarkerAttributes.NAME, null),
                    marker.getAttribute(AdditionalMarkerAttributes.ORIGINAL_NAME, null),
                    marker.getAttribute(AdditionalMarkerAttributes.SOURCES, "")));
        }
    },
    DEPRACATED_DOCUMENT_WORD_FROM_30 {

        @Override
        public Severity getSeverity() {
            return Severity.WARNING;
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public String getProblemDescription() {
            return "Keyword setting '%s' is depracated from Robot Framework 3.0. Use Documentation syntax instead of current.";
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            return newArrayList(new DocumentToDocumentationWordFixer(RobotKeywordsSection.class));
        }
    },
    DEPRACATED_POSTCONDITION_SYNONIM_FROM_30 {

        @Override
        public Severity getSeverity() {
            return Severity.WARNING;
        }

        @Override
        public boolean hasResolution() {
            return true;
        }

        @Override
        public String getProblemDescription() {
            return "Setting '%s' is depracated from Robot Framework 3.0. Use [Teardown] syntax instead of current.";
        }

        @Override
        public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
            return newArrayList(new DocumentToDocumentationWordFixer(RobotSettingsSection.class));
        }
    };

    @Override
    public List<? extends IMarkerResolution> createFixers(final IMarker marker) {
        return newArrayList();
    }

    @Override
    public Severity getSeverity() {
        return Severity.ERROR;
    }

    @Override
    public boolean hasResolution() {
        return false;
    }

    @Override
    public ProblemCategory getProblemCategory() {
        return null;
    }

    @Override
    public String getEnumClassName() {
        return KeywordsProblem.class.getName();
    }
}
