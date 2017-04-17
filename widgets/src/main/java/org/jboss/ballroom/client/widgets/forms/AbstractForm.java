package org.jboss.ballroom.client.widgets.forms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.client.ui.DeckPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.RowCountChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SingleSelectionModel;
import org.jboss.ballroom.client.I18n;
import org.jboss.ballroom.client.rbac.SecurityContext;
import org.jboss.ballroom.client.rbac.SecurityContextAware;
import org.jboss.ballroom.client.rbac.SecurityService;
import org.jboss.ballroom.client.spi.Framework;
import org.jboss.ballroom.client.widgets.window.DialogueOptions;
import org.jboss.ballroom.client.widgets.window.Feedback;

/**
 * @author Heiko Braun
 * @date 11/12/12
 */
public abstract class AbstractForm<T> implements FormAdapter<T> {

    public static final String EXPR_TAG = "EXPRESSIONS";
    private static final String RBAC_SUPPRESSED = "rbac-suppressed";

    protected final Map<String, Map<String, FormItem>> formItems = new LinkedHashMap<String, Map<String, FormItem>>();
    protected final Map<String,GroupRenderer> renderer = new HashMap<String, GroupRenderer>();
    protected int numColumns = 1;
    protected int nextId = 1;
    protected int maxTitleLength = 0;
    protected boolean isTransient;

    private FormDeckPanel deck;
    private DeckPanel resetPanel;
    protected List<PlainFormView> plainViews = new ArrayList<PlainFormView>();
    private boolean isEnabled =true;

    final static String DEFAULT_GROUP = "default";
    protected FormCallback toolsCallback = null; // if set, the tools will be provided externally
    protected FormResetCallback resetCallback; // if set, the reset will be provided externally

    private HTML edit;
    private HTML reset;
    private final List<FormValidator> formValidators = new LinkedList<>();

    /**
     * Must delegate to {@link #setOperational(boolean)}
     * @param bean The bean to be edited.
     */
    public abstract void edit(T bean);

    /**
     * Set's the form operational is not suppressed by RBAC
     * @param isOperational
     */
    protected void setOperational(boolean isOperational) {

        if (edit != null && !edit.getElement().hasClassName(RBAC_SUPPRESSED)) {
            if(isOperational)
                edit.getElement().getStyle().setDisplay(Style.Display.INLINE);
            else
                edit.getElement().getStyle().setDisplay(Style.Display.NONE);
        }

        if (reset != null && !reset.getElement().hasClassName(RBAC_SUPPRESSED)) {
            if(isOperational)
                reset.getElement().getStyle().setDisplay(Style.Display.INLINE);
            else
                reset.getElement().getStyle().setDisplay(Style.Display.NONE);
        }

    }

    public abstract void cancel();

    public abstract Map<String, Object> getChangedValues();

    public abstract T getUpdatedEntity();

    static Framework FRAMEWORK  = GWT.create(Framework.class);
    static SecurityService SECURITY_SERVICE = FRAMEWORK.getSecurityService();

    protected String resourceAddress; // RBAC, optional

    @Override
    public void addFormValidator(final FormValidator formValidator) {
        formValidators.add(formValidator);
    }

    public FormValidation validate() {
        List<FormItem> items = new ArrayList<>();
        FormValidation outcome = new FormValidation();

        for (Map<String, FormItem> groupItems : formItems.values()) {
            for (FormItem item : groupItems.values()) {
                items.add(item);
                // two cases: empty form (create entity) and updating an existing entity
                // we basically force validation on newly created entities
                boolean requiresValidation = getEditedEntity() != null ? (item.isModified() || isTransient) : true;

                if (requiresValidation) {
                    Object value = item.getValue();

                    // ascii or empty string are ok. the later will be checked in each form item implementation.
                    String stringValue = String.valueOf(value);
                    boolean ascii = stringValue.isEmpty() ||
                            stringValue.matches("^[\\u0020-\\u007e]+$");

                    if (!ascii) {
                        outcome.addError(item.getName());
                        item.setErroneous(true);
                    } else {
                        boolean validValue = item.validate(value);
                        if (validValue) {
                            item.setErroneous(false);
                        } else {
                            outcome.addError(item.getName());
                            item.setErroneous(true);
                        }
                    }
                }
            }
        }

        // apply additional validation checks
        for (FormValidator formValidator : formValidators) {
            formValidator.validate(items, outcome);
        }

        return outcome;
    }


    public Widget asWidget() {
        return build();
    }

    public abstract Set<String> getReadOnlyNames();

    public abstract Set<String> getFilteredNames();

    protected SecurityContext getSecurityContext()
    {
        return SECURITY_SERVICE.getSecurityContext(SECURITY_SERVICE.resolveToken());
    }

    private Widget build() {

        deck = new FormDeckPanel(this.resourceAddress);
        deck.setStyleName("fill-layout-width");

        // RBAC
        SecurityContext securityContext = getSecurityContext();
        boolean writePriviledges = this.resourceAddress !=null ?                      // TOOD: Typo in API method
                securityContext.getWritePrivilege(this.resourceAddress).isGranted() :
                securityContext.getWritePriviledge().isGranted();

        // ----------------------
        // view panel

        VerticalPanel viewPanel = new VerticalPanel();
        viewPanel.setStyleName("fill-layout-width");
        viewPanel.addStyleName("form-view-panel");

        HorizontalPanel linkPanel = new HorizontalPanel();

        if(toolsCallback!=null)
        {

            edit = new HTML("<i class='icon-edit'></i>&nbsp; Edit");
            edit.setStyleName("form-edit-button");
            ClickHandler editHandler = new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    setEnabled(true);
                    toggleViews();
                }
            };
            edit.addClickHandler(editHandler);
            linkPanel.add(edit);

            if(!writePriviledges)
                edit.getElement().addClassName(RBAC_SUPPRESSED);

            if (resetCallback != null) {

                resetPanel = new DeckPanel();

                reset = new HTML("<i class='icon-refresh'></i>&nbsp; Reset");
                reset.setStyleName("form-edit-button");
                reset.setTitle("Remove all values of this form.");
                ClickHandler resetHandler = event -> Feedback.confirm(
                        I18n.CONSTANTS.reset_title(),
                        I18n.CONSTANTS.reset_description(),
                        isConfirmed -> {
                            if (isConfirmed) {
                                resetCallback.onReset();
                            }
                        });
                reset.addClickHandler(resetHandler);

                HTML resetDisabled = new HTML("<i class='icon-refresh'></i>&nbsp; Reset");
                resetDisabled.setStyleName("form-edit-button-disabled");

                resetPanel.add(reset);
                resetPanel.add(resetDisabled);

                linkPanel.add(resetPanel);

                if (!writePriviledges)
                    reset.getElement().addClassName(RBAC_SUPPRESSED);
            }

            // without any data the form is disabled
            setOperational(false);
            viewPanel.add(linkPanel);
        }

        deck.add(viewPanel.asWidget());

        // ----------------------
        // edit panel

        VerticalPanel editPanel = new VerticalPanel();
        editPanel.setStyleName("fill-layout-width");
        editPanel.addStyleName("form-edit-panel");

        if(toolsCallback!=null)
        {
            final HTML editDisabled = new HTML("<i class='icon-edit'></i>&nbsp; Edit");
            editDisabled.setStyleName("form-edit-button-disabled");
            editPanel.add(editDisabled);
        }

        // RBAC
        Set<String> filteredItems = getFilteredNames();

        RenderMetaData metaData = new RenderMetaData();
        metaData.setNumColumns(numColumns);
        metaData.setTitleWidth(maxTitleLength);
        metaData.setFilteredFields(filteredItems);

        for(String group : formItems.keySet())
        {
            Map<String, FormItem> groupItems = formItems.get(group);

            // RBAC: read-only attributes
            for(String attr : groupItems.keySet())
            {
                FormItem formItem = groupItems.get(attr);
                //formItem.setFiltered(false); // reset to default state

                if(filteredItems.contains(attr))
                {
                    formItem.setFiltered(true);
                }
            }


            GroupRenderer groupRenderer = null;

            if(DEFAULT_GROUP.equals(group))
                groupRenderer = new DefaultGroupRenderer();
            else
                groupRenderer = renderer.get(group)!=null ? renderer.get(group) : new FieldsetRenderer();

            // edit view
            Widget editWidget = groupRenderer.render(metaData, group, groupItems);
            editPanel.add(editWidget);

            // plain view
            PlainFormView plainView = new PlainFormView(new ArrayList<FormItem>(groupItems.values()));
            plainView.setNumColumns(numColumns);
            plainViews.add(plainView);
            Widget viewWidget = groupRenderer.renderPlain(metaData, group, plainView);
            viewPanel.add(viewWidget);
        }

        // inline tools (if callback is provided)
        if(toolsCallback!=null)
        {
            editPanel.add(
                    new DialogueOptions(
                            "Save",
                            new ClickHandler() {
                                @Override
                                public void onClick(ClickEvent event) {

                                    if(!validate().hasErrors())
                                    {
                                        refreshPlainView();
                                        setEnabled(false);
                                        toggleViews();
                                        toolsCallback.onSave(getChangedValues());
                                    }
                                }
                            },
                            "Cancel",
                            new ClickHandler() {
                                @Override
                                public void onClick(ClickEvent event) {
                                    setEnabled(false);
                                    toggleViews();
                                    cancel();

                                    // TODO: used anymore?
                                }
                            }
                    )
            );
        }

        deck.add(editPanel);

        // toggle default view
        toggleViews();
        refreshPlainView(); // make sure it's build, even empty...

        return deck;
    }

    public void setEnableResetButton(boolean val) {
        if (resetPanel != null)
            resetPanel.showWidget(val ? 0: 1);
    }

    /**
     * Number of layout columns.<br>
     * Form fields will fill columns in the order they have been specified
     * in {@link #setFields(org.jboss.ballroom.client.widgets.forms.FormItem[])}.
     *
     * @param columns
     */
    public void setNumColumns(int columns)
    {
        //this.numColumns = columns;
    }

    /**
     * Specify the form fields.
     * Needs to be called before {@link #asWidget()}.
     *
     * @param items
     */
    public void setFields(FormItem... items) {
        setFieldsInGroup(Form.DEFAULT_GROUP, items);
    }

    public void setFields(FormItem[]... items) {
        setFieldsInGroup(Form.DEFAULT_GROUP, flatten(items));
    }

    public void setFieldsInGroup(String group, FormItem... items) {

        // create new group
        LinkedHashMap<String, FormItem> groupItems = new LinkedHashMap<String, FormItem>();
        formItems.put(group, groupItems);

        for(FormItem item : items)
        {
            String title = item.getTitle();
            if(title.length()>maxTitleLength)
            {
                maxTitleLength = title.length();
            }

            // key maybe be used multiple times
            String itemKey = item.getName();

            if(groupItems.containsKey(itemKey)) {
                groupItems.put(itemKey+"#"+nextId, item);
                nextId++;
            }
            else
            {
                groupItems.put(itemKey, item);
            }
        }
    }

    public void setFieldsInGroup(String group, FormItem[]... items) {
        setFieldsInGroup(group, flatten(items));
    }

    public void setFieldsInGroup(String group, GroupRenderer renderer, FormItem... items) {
        this.renderer.put(group, renderer);
        setFieldsInGroup(group, items);
    }

    public void setFieldsInGroup(String group, GroupRenderer renderer, FormItem[]... items) {
        setFieldsInGroup(group, renderer, flatten(items));
    }

    private FormItem<?>[] flatten(FormItem<?>[]... items) {
        List<FormItem<?>> l = new ArrayList<FormItem<?>>();
        for (FormItem<?> [] fiArray : items) {
            for (FormItem<?> fi : fiArray) {
                l.add(fi);
            }
        }
        FormItem<?>[] array = l.toArray(new FormItem<?>[l.size()]);
        return array;
    }

    protected void refreshPlainView() {
        for(PlainFormView view : plainViews)
            view.refresh(getEditedEntity()!=null);
    }

    public List<String> getFormItemNames() {
        List<String> result = new LinkedList<String>();

        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(FormItem item : groupItems.values())
            {
                result.add(item.getName());
            }
        }

        return result;
    }

    public String getFormItemTitle(String ref) {
        String result = null;
        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(FormItem item : groupItems.values())
            {
                if(item.getName().equals(ref))
                {
                    result = item.getTitle();
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Enable/disable this form.
     *
     * @param isEnabled
     */
    public void setEnabled(boolean isEnabled) {

        this.isEnabled = isEnabled;

        if(deck!=null)  // might no be created yet (backwards compatibility)
            toggleViews();
    }

    private void toggleViews() {
        int index = isEnabled ? 1 :0;
        deck.showWidget(index);
    }

    /**
     * Binds a default single selection model to the table
     * that displays selected rows in a form.
     *
     * @param table
     */
    public void bind(CellTable<T> table) {
        SingleSelectionModel<T> selectionModel = (SingleSelectionModel<T>)table.getSelectionModel();
        if (selectionModel == null) {
            selectionModel = new SingleSelectionModel<T>();
        }

        final SingleSelectionModel<T> finalSelectionModel = selectionModel;

        selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {

            public void onSelectionChange(SelectionChangeEvent event) {

                Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {

                    public void execute() {
                        T selectedObject = finalSelectionModel.getSelectedObject();
                        if(selectedObject!=null)
                            edit(selectedObject);
                        else
                        {
                            clearValues();

                        }
                    }
                });
            }
        });

        table.setSelectionModel(finalSelectionModel);

        table.addRowCountChangeHandler(new RowCountChangeEvent.Handler() {

            public void onRowCountChange(RowCountChangeEvent event) {
                if(event.getNewRowCount()==0 && event.isNewRowCountExact())
                    clearValues();

            }
        });

    }

    public void setSecurityContextFilter(String resourceAddress) {
        if (deck != null) {
            deck.setFilter(resourceAddress);
        }
    }

    @Override
    public void setToolsCallback(FormCallback callback) {
        this.toolsCallback = callback;
    }

    @Override
    public void setResetCallback(FormResetCallback callback) {
        this.resetCallback = callback;
    }

    class FormDeckPanel extends DeckPanel implements SecurityContextAware {

        private final String id;
        private final String resourceAddress;
        private String filter;
        private boolean wasEnabled;
        private final String token;

        FormDeckPanel(String resourceAddress) {
            super();
            this.resourceAddress = resourceAddress;
            this.id = Document.get().createUniqueId();
            getElement().setId(this.id);
            token = SECURITY_SERVICE.resolveToken();
        }

        @Override
        public String getToken() {
            return token;
        }

        @Override
        protected void onLoad() {
            SECURITY_SERVICE.registerWidget(id, this);
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
        public void onSecurityContextChanged() {

            SecurityContext securityContext = SECURITY_SERVICE.getSecurityContext(token);

            //System.out.println("<< Process SecurityContext on form "+id+": "+securityContext+">>");

            boolean writePrivilege = this.resourceAddress != null ?
                    securityContext.getWritePrivilege(this.resourceAddress).isGranted() :
                    securityContext.getWritePriviledge().isGranted();

            if (!writePrivilege) {
                wasEnabled = isEnabled;
                if (isEnabled) {
                    setEnabled(false);
                }
                if (edit != null) {
                    edit.getElement().addClassName(RBAC_SUPPRESSED);
                }
                if (reset != null) {
                    reset.getElement().addClassName(RBAC_SUPPRESSED);
                }
            } else {
                // TODO update form fields based on new security context?
                if (edit != null) {
                    edit.getElement().removeClassName(RBAC_SUPPRESSED);
                }
                if (reset != null) {
                    reset.getElement().removeClassName(RBAC_SUPPRESSED);
                }
                if (wasEnabled) {
                    setEnabled(true);
                }
            }
        }
    }
}
