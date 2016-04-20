package org.smap.sdal.model;


/*
 * TableColumn class used to manage data shown in a table
 */
public class TableColumn {
	public String name;
	public boolean include;		// Include in the table
	public boolean hide;		// Only show on expand
	
	public TableColumn(String n) {
		name = n;
		include = true;
		hide = false;
	}
}
