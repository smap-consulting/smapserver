package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * TableColumn class used to manage data shown in a table
 */
public class TableColumn {
	public String name;
	public String humanName;
	public boolean include;		// Include in the table
	public boolean hide;		// Only show on expand
	public boolean barcode;		// Show as barcode
	public boolean mgmt = false;
	public boolean filter = false;
	public String filterValue;
	
	// Manage updating of data
	public boolean readonly;	// Can't be modified by form management
	public String type;		// text || select_one || date || calculate
	public ArrayList<String> choices;			// If type is select_one
	public ArrayList<TableColumnMarkup> markup;	// Specify how to present the data
	
	// Manage extraction of data
	public SqlFrag calculation = null;	// Server only
	
	public TableColumn(String n, String hn) {
		name = n;
		humanName = hn;
		include = true;
		hide = false;
		
		readonly = true;
	}
	public TableColumn() {
		include = true;
		hide = false;
	}
}
