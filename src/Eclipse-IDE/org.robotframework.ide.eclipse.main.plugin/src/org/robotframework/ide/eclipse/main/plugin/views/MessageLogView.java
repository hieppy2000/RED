package org.robotframework.ide.eclipse.main.plugin.views;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;


/**
 * @author mmarzec
 *
 */
public class MessageLogView {

    private StyledText styledText;
    
    @PostConstruct
    public void postConstruct(Composite parent) {
        
        FillLayout layout = new FillLayout();
        layout.marginHeight=2;
        layout.marginWidth=2;
        parent.setLayout(layout);
        
        styledText = new StyledText(parent, SWT.H_SCROLL | SWT.V_SCROLL);
        styledText.setFont(JFaceResources.getTextFont());
        styledText.setEditable(false);
    }
    
    @Focus
    public void onFocus() {
        
    }
    
    public void appendLine(String line) {
        styledText.append(line);
        styledText.setTopIndex(styledText.getLineCount() - 1);
    }
    
    @Inject
    @Optional
    private void lineEvent(@UIEventTopic("MessageLogView/AppendLine") String line) {
        appendLine(line);
    }
    
    @Inject
    @Optional
    private void clearEvent(@UIEventTopic("MessageLogView/Clear") String s) {
        styledText.setText("");
    }
}
