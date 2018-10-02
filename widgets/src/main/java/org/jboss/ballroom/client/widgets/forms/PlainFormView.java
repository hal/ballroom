package org.jboss.ballroom.client.widgets.forms;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DefaultCellTableBuilder;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Heiko Braun
 * @date 11/9/11
 */
public class PlainFormView {

    private static final FormItemTableResources DEFAULT_CELL_TABLE_RESOURCES =
            new FormItemTableResources();

    private final String id = "formview-"+ DOM.createUniqueId()+"_";

    private CellTable<Row> table;
    private final List<FormItem> items;
    private List<Row> rows;
    private int numColumns = 1;
    private boolean hasEntity = false;
    private final static String EMPTY_STRING = "";

    public PlainFormView(List<FormItem> items) {
        this.items = items;
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public Widget asWidget(final RenderMetaData metaData) {

        table = new CellTable<Row>(20, DEFAULT_CELL_TABLE_RESOURCES);
        table.setStyleName("form-item-table");

        // see http://www.w3.org/TR/wai-aria/roles#group
        // ... when a group is used in the context of list, authors MUST limit its children to listitem elements

        // table.getElement().setAttribute("role", "group");


        for(int col = 0; col<numColumns; col++)
        {
            final int currentCol = col;

            Column<Row, String> titleCol = new Column<Row, String>(new TitleCell(currentCol)) {
                @Override
                public String getValue(Row row) {
                    FormItem item = row.get(currentCol);
                    return item.getTitle();

                }
            };


            Column<Row, String> valueCol = new Column<Row, String>(new ValueCell(currentCol)) {
                @Override
                public String getValue(Row row) {
                    FormItem item = row.get(currentCol);
                    return item!=null ? valueRepresentation(item, metaData) : "";
                }
            };

            Column<Row, String> iconCol = new Column<Row, String>(new IconCell(currentCol)) {
                @Override
                public String getValue(Row row) {
                    FormItem item = row.get(currentCol);
                    if(item!=null)
                    {
                        boolean isFiltered = metaData.getFilteredFields().contains(item.getName());
                        return isFiltered ? "icon-lock" : "";
                    }
                    else
                    {
                        return "";
                    }
                }

            };

            if(numColumns==1)
            {
                table.addColumnStyleName(0, "cols_1_form-item-title-col");
                table.addColumnStyleName(1, "cols_1_form-item-col");
                table.addColumnStyleName(2, "cols_1_form-icon-col");
            }
            else
            {
                String prefix = "cols_"+numColumns;
                table.addColumnStyleName(0, prefix+"_form-item-title-col");
                table.addColumnStyleName(1, prefix+"_form-item-col");
                table.addColumnStyleName(2, prefix+"_form-icon-col");
            }

            table.addColumn(titleCol);
            table.addColumn(valueCol);
            table.addColumn(iconCol);

        }

        //table.setTableLayoutFixed(true);

        table.setEmptyTableWidget(new HTML());
        table.setLoadingIndicator(new HTML());

        rows = groupItems();

        // HAL-301: debug id's
        table.setTableBuilder(new DefaultCellTableBuilder<Row>(table) {
            int rowIdx = 0;

            @Override
            protected void addRowAttributes(TableRowBuilder row) {
                super.addRowAttributes(row);

                if(rowIdx<items.size()) {
                    row.attribute("data-dmr-attr", items.get(rowIdx).getName());
                    rowIdx++;
                }
                else if(rowIdx==items.size())
                {
                    // reset
                    rowIdx = 0;
                    row.attribute("data-dmr-attr", items.get(rowIdx).getName());
                    rowIdx++;
                }

            }

        });

        table.setRowStyles(new RowStyles<Row>() {
            @Override
            public String getStyleNames(Row row, int rowIndex) {
                return rowIndex==0 ? "first-row form-attribute-row " : "form-attribute-row ";
            }
        });

        return table;
    }

    private String valueRepresentation(FormItem item, RenderMetaData metaData) {

        String representation = null;
        Object value = item.getValue();
        boolean isFiltered = metaData.getFilteredFields().contains(item.getName());

        if(isFiltered)
        {
            representation = "The permissions for your role don't allow to access this data";
        }
        else if(item.isUndefined())
        {
            representation = EMPTY_STRING;
        }
        else if(hasEntity && item.isExpressionValue())
        {
            representation = String.valueOf(item.asExpressionValue());
        }
        else if(hasEntity && (item instanceof PasswordBoxItem))
        {
            // hide passwords
            StringBuffer sb = new StringBuffer();
            String plainText = String.valueOf(value);
            for(int i=0; i<plainText.length(); i++)
                sb.append("*");
            representation = sb.toString();
        }
        else if(hasEntity && (value instanceof Boolean))
        {
            String booleanFallback = hasEntity ? "false" : EMPTY_STRING;
            representation = (Boolean)value ? "true" : booleanFallback;
        }
        else if(hasEntity && (item instanceof ListItem))
        {
            ListItem list = (ListItem)item;
            StringBuffer sb = new StringBuffer();
            for(String s : list.getValue())
                sb.append(s).append("  ");
            representation = sb.toString();
        }
        else
        {
            representation = hasEntity ? item.asString() : EMPTY_STRING;
        }

        return representation;
    }

    public void refresh(boolean hasEntity) {

        this.hasEntity = hasEntity; // changes the display style (no entity at all != empty entity)

        table.setRowCount(rows.size(), true);
        table.setRowData(rows);
    }

    private List<Row> groupItems() {
        List<Row> rows = new ArrayList<Row>();

        int i=0;
        while(i<items.size())
        {
            FormItem[] itemsPerRow = new FormItem[numColumns];

            for(int col=0; col<numColumns;col++)
            {
                if(i+col>=items.size())
                    itemsPerRow[col] = null;
                else
                    itemsPerRow[col] = items.get(i+col);
            }

            rows.add(new Row(i, itemsPerRow));

            i+=numColumns;
        }

        return rows;
    }

    private final class Row {

        FormItem[] items;
        private int rowNum;

        Row(int rowNum, FormItem... items) {
            this.rowNum = rowNum;
            this.items = items;
        }

        public FormItem get(int i) {
            return items[i];
        }

        public int getRowNum() {
            return rowNum;
        }
    }


    interface Template extends SafeHtmlTemplates {
        @Template("<div class='form-item-title' style='outline:none;' id='{0}'>{1}: </div>")
        SafeHtml render(String id, String title);
    }

    interface ValueTemplate extends SafeHtmlTemplates {
        @Template("<span class='form-item-value' aria-labelledBy='{0}'>{1}</span>")
        SafeHtml render(String id, String title);
    }

    interface HyperlinkTemplate extends SafeHtmlTemplates {
        @Template("<a aria-labelledBy='{0}' tabindex=\"-1\" class='gwt-Hyperlink' href='{1}' target='_blank'>{1}</a>")
        SafeHtml render(String id, String title);
    }

    private static final Template TEMPLATE = GWT.create(Template.class);
    private static final ValueTemplate VALUE_TEMPLATE = GWT.create(ValueTemplate.class);
    private static final HyperlinkTemplate HYPERLINK_TEMPLATE = GWT.create(HyperlinkTemplate.class);

    private class TitleCell extends AbstractCell<String> {

        private int index;

        public TitleCell(int index) {
            super();
            this.index = index;
        }

        @Override
        public void render(Cell.Context context, String title, SafeHtmlBuilder safeHtmlBuilder)
        {
            SafeHtml render;
            Row row = (Row)context.getKey();
            final String labelId = id + row.getRowNum() +"_"+index+"_l";
            boolean hasTitle = title!=null && !title.equals("");
            if (hasTitle)
            {
                render = TEMPLATE.render(labelId, title);
            }
            else
            {
                render = new SafeHtmlBuilder().toSafeHtml();
            }
            safeHtmlBuilder.append(render);
        }
    }

    private class ValueCell extends AbstractCell<String> {

        private int index;

        private ValueCell(int index) {
            super();
            this.index = index;
        }

        @Override
        public void render(Cell.Context context, String value, SafeHtmlBuilder safeHtmlBuilder)
        {
            SafeHtml render;
            Row row = (Row)context.getKey();
            final String labelId = id + row.getRowNum() +"_"+index+"_l";
            boolean hasValue = value!=null && !value.equals("");

            if (hasValue)
            {
                if (value.startsWith("http://") || value.startsWith("https://"))
                {
                    render = HYPERLINK_TEMPLATE.render(labelId, SafeHtmlUtils.htmlEscape(value));
                }
                else
                {
                    render = VALUE_TEMPLATE.render(labelId, SafeHtmlUtils.htmlEscape(value));
                }
            }
            else
            {
                render = new SafeHtmlBuilder().toSafeHtml();
            }
            safeHtmlBuilder.append(render);
        }
    }

    private class IconCell extends AbstractCell<String> {

        private int index;

        private IconCell(int index) {
            super();
            this.index = index;
        }

        @Override
        public void render(Cell.Context context, String icon, SafeHtmlBuilder safeHtmlBuilder)
        {
            SafeHtml render;

            boolean hasValue = icon!=null && !icon.equals("");

            if (hasValue)
            {
                SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<i class='" + icon + "'></i>");
                render = builder.toSafeHtml();
            }
            else
            {
                render = new SafeHtmlBuilder().toSafeHtml();
            }

            safeHtmlBuilder.append(render);
        }
    }
}
