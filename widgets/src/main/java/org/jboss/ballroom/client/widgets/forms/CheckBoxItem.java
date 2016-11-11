/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.ballroom.client.widgets.forms;

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Heiko Braun
 * @date 3/2/11
 */
public class CheckBoxItem extends FormItem<Boolean> {

    private static final String SWITCH_TO_EXPRESSION_TITLE = "Shift click for expression input";

    private CheckBox checkBox;
    private final TextBox textBox;
    private final HorizontalPanel panel;
    private final InputElementWrapper textBoxWrapper;
    private final InputElementWrapper checkBoxWrapper;
    private boolean expressionAllowed = true;

    public CheckBoxItem(String name, String title) {
        super(name, title);
        checkBox = new CheckBox();
        checkBox.setName(name);
        checkBox.setTitle(SWITCH_TO_EXPRESSION_TITLE);
        checkBox.setTabIndex(0);
        checkBox.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
            @Override
            public void onValueChange(ValueChangeEvent<Boolean> booleanValueChangeEvent) {
                setModified(true);
                setUndefined(false);
            }
        });
        setUndefined(false);

        checkBox.addClickHandler(new  ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                if (clickEvent.isShiftKeyDown() && expressionAllowed) {
                    toogleTextInput();
                    clickEvent.preventDefault();
                }
            }
        });

        textBox = new TextBox();
        textBox.setTitle("Shift click for regular input");
        textBox.addClickHandler(new  ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                if (clickEvent.isShiftKeyDown()) {
                    toogleBooleanInput();
                    clickEvent.preventDefault();
                }
            }
        });

        textBox.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                setModified(true);
                setUndefined(event.getValue().equals(""));
            }
        });

        textBoxWrapper = new InputElementWrapper(textBox, this);
        textBoxWrapper.setExpression(true);

        checkBoxWrapper = new InputElementWrapper(checkBox, this);

        panel = new HorizontalPanel();
        panel.add(checkBoxWrapper);
        panel.add(textBoxWrapper);

        // force boolean input at startup
        toogleBooleanInput();

        this.errMessage = "Invalid expression value";

    }

    @Override
    public void setFiltered(boolean filtered) {
        super.setFiltered(filtered);
        super.toggleAccessConstraint(checkBox, filtered);
        checkBox.setEnabled(!filtered);
        textBoxWrapper.setConstraintsApply(filtered);
    }

    private void toogleTextInput() {
        textBoxWrapper.setVisible(true);
        checkBoxWrapper.setVisible(false);
    }

    private void toogleBooleanInput() {
        textBoxWrapper.setVisible(false);
        checkBoxWrapper.setVisible(true);
    }

    public Element getInputElement() {
        return checkBox.getElement().getFirstChildElement();
    }

    @Override
    public void resetMetaData() {
        super.resetMetaData();
        setUndefined(false); // implicitly defined
        checkBox.setValue(false);
        textBox.setText("");

        setModified(false); // important: needs to be done after calling textBox.setValue()
    }

    @Override
    public String asExpressionValue() {
        String expr = textBoxWrapper.isVisible() ? textBox.getValue() : null;
        return expr;
    }

    @Override
    public Boolean getValue() {
        return checkBox.getValue();
    }

    @Override
    public void setExpressionValue(String expr) {

        this.expressionValue = expr;
        if(expr!=null) {
            toogleTextInput();
            textBox.setText(expr);
        }
    }

    @Override
    public void setValue(Boolean value) {
        textBox.setText("");
        checkBox.setValue(value);
        toogleBooleanInput();
    }

    @Override
    public void setErroneous(boolean b) {
        checkBoxWrapper.setErroneous(b);
        textBoxWrapper.setErroneous(b);
    }

    @Override
    public Widget asWidget() {
        return panel;
    }

    @Override
    public void setEnabled(boolean b) {
        checkBox.setEnabled(b);
        textBox.setEnabled(b);
    }

    @Override
    public boolean validate(Boolean value) {
        boolean isValid = true;

        if(textBoxWrapper.isVisible())
        {
            // expression mode
            String text = textBox.getText();
            isValid = text !=null  && text.startsWith("${") && text.endsWith("}");
        }

        return isValid;
    }

    @Override
    public void clearValue() {
        setValue(false);
    }

    public void setExpressionAllowed(boolean expressionAllowed) {
        this.expressionAllowed = expressionAllowed;
        checkBox.setTitle(expressionAllowed ? SWITCH_TO_EXPRESSION_TITLE : "");
        toogleBooleanInput();
    }
}
