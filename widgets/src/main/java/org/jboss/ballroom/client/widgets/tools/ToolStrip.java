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

package org.jboss.ballroom.client.widgets.tools;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.jboss.ballroom.client.rbac.AuthorisationDecision;
import org.jboss.ballroom.client.rbac.SecurityContext;
import org.jboss.ballroom.client.rbac.SecurityContextAware;
import org.jboss.ballroom.client.rbac.SecurityService;
import org.jboss.ballroom.client.spi.Framework;

/**
 * @author Heiko Braun
 */
public class ToolStrip extends HorizontalPanel implements SecurityContextAware {

    static Framework FRAMEWORK = GWT.create(Framework.class);
    static SecurityService SECURITY_SERVICE = FRAMEWORK.getSecurityService();

    private final String id;
    private String filter;
    private HorizontalPanel left;
    private HorizontalPanel right;
    private String resourceAddress = null;

    public ToolStrip() {
        super();
        this.id = Document.get().createUniqueId();
        getElement().setId(id);

        setStyleName("default-toolstrip");
        getElement().setAttribute("role", "toolbar");

        left = new HorizontalPanel();
        right = new HorizontalPanel();

        add(left);
        add(right);

        left.getElement().getParentElement().setAttribute("width", "50%");
        right.getElement().getParentElement().setAttribute("width", "50%");
        right.getElement().getParentElement().setAttribute("align", "right");
    }

    public ToolStrip(String resourceAddress) {
        this();
        this.resourceAddress = resourceAddress;
    }

    @Override
    protected void onLoad() {
        SECURITY_SERVICE.registerWidget(id, this);
        applySecurity(SECURITY_SERVICE.getSecurityContext(), false);
    }

    @Override
    protected void onUnload() {
        SECURITY_SERVICE.unregisterWidget(id);
    }

    @Override
    public void setFilter(final String filter) {
        this.filter = filter;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public void updateSecurityContext(final SecurityContext securityContext) {
        applySecurity(securityContext, true);
    }

    public void addToolButton(ToolButton button) {
        left.add(button);
    }

    public void addToolButtonRight(ToolButton button) {
        button.getElement().setAttribute("style", "margin-right:5px;");
        button.addStyleName("toolstrip-button-secondary");
        right.add(button);

    }

    public boolean hasButtons() {
        return left.getWidgetCount() > 0 || right.getWidgetCount() > 0;
    }

    public void addToolWidget(Widget widget) {
        left.add(widget);
    }

    public void addToolWidgetRight(Widget widget) {
        right.add(widget);
    }

    private void applySecurity(final SecurityContext securityContext, boolean update) {
        int visibleItemsLeft = countVisibleWidgets(left, securityContext, update);
        int visibleItemsRight = countVisibleWidgets(right, securityContext, update);

        // nothing accessible within the toolbar, so we disable it completely
        if (visibleItemsLeft + visibleItemsRight == 0) {
            setVisible(false);
            getElement().addClassName("rbac-suppressed");
        } else {
            setVisible(true);
            getElement().removeClassName("rbac-suppressed");
        }
    }

    private int countVisibleWidgets(HorizontalPanel panel, SecurityContext securityContext, boolean update) {
        int visibleItems = 0;
        int visibleButtons = 0;
        boolean overallPrivilege = this.resourceAddress != null ?
                securityContext.getWritePrivilege(this.resourceAddress).isGranted() :
                securityContext.getWritePriviledge().isGranted();

        for (int i = 0; i < panel.getWidgetCount(); i++) {
            Widget widget = panel.getWidget(i);
            if (widget instanceof ToolButton) {
                ToolButton btn = (ToolButton) widget;
                boolean granted;
                if (btn.hasOperationAddress()) {
                    // fine grained, doesn't usually apply but can help to overcome dge cases
                    String[] operationAddress = btn.getOperationAddress();
                    AuthorisationDecision operationPriviledge = securityContext
                            .getOperationPriviledge(operationAddress[0], operationAddress[1]);
                    granted = operationPriviledge.isGranted();
                } else {
                    granted = overallPrivilege; // coarse grained, inherited from parent
                }

                btn.setVisible(granted);

                if (update) {
                    if (granted) {
                        widget.getElement().removeClassName("rbac-suppressed");
                        visibleButtons++;
                    } else {
                        widget.getElement().addClassName("rbac-suppressed");
                    }
                } else {
                    if (!granted) {
                        widget.getElement().addClassName("rbac-suppressed");
                    }
                    if (granted) {
                        visibleButtons++;
                    }
                }
            } else {
                visibleItems++;
            }
        }
        // If there are no buttons, the visibility depends on the number of other widgets
        return visibleButtons == 0 ? visibleItems : visibleItems + visibleButtons;
    }
}
