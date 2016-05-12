/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.red.nattable.configs;

import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.config.IEditableRule;
import org.eclipse.nebula.widgets.nattable.data.validate.DefaultDataValidator;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.config.DefaultEditConfiguration;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.red.nattable.NewElementsCreator;
import org.robotframework.red.nattable.edit.AlwaysDeactivatingCellEditor;
import org.robotframework.red.nattable.edit.HorizontalMovingTextCellEditor;


/**
 * @author Michal Anglart
 *
 */
public class RedTableEditConfiguration<T extends RobotElement> extends DefaultEditConfiguration {

    private final IEditableRule editableRule;

    private final NewElementsCreator<T> creator;

    public RedTableEditConfiguration(final RobotSuiteFile fileModel, final NewElementsCreator<T> creator) {
        this.editableRule = SuiteModelEditableRule.createEditableRule(fileModel);
        this.creator = creator;
    }

    @Override
    public void configureRegistry(final IConfigRegistry configRegistry) {
        super.configureRegistry(configRegistry);
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITABLE_RULE, editableRule);
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITOR, createDeactivatingEditor(),
                DisplayMode.NORMAL, AddingElementStyleConfiguration.ELEMENT_ADDER_ROW_CONFIG_LABEL);
        configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITOR, new HorizontalMovingTextCellEditor(),
                DisplayMode.NORMAL, GridRegion.BODY);
        configRegistry.registerConfigAttribute(EditConfigAttributes.DATA_VALIDATOR, new DefaultDataValidator());
        configRegistry.registerConfigAttribute(EditConfigAttributes.OPEN_ADJACENT_EDITOR, Boolean.TRUE,
                DisplayMode.EDIT, GridRegion.BODY);
        configRegistry.registerConfigAttribute(EditConfigAttributes.ACTIVATE_EDITOR_ON_TRAVERSAL, Boolean.TRUE,
                DisplayMode.EDIT, GridRegion.BODY);
    }

    private ICellEditor createDeactivatingEditor() {
        return new AlwaysDeactivatingCellEditor(creator);
    }
}