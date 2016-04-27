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
	public boolean mgmt = false;
	
	// Manage updating of data
	public boolean readonly;	// Can't be modified by form management
	public String type;		// text || select_one
	public ArrayList<String> choices;		// If type is select_one
	
	public TableColumn(String n, String hn) {
		name = n;
		humanName = hn;
		include = true;
		hide = false;
		
		readonly = true;
	}
}
