package org.smap.sdal.model;

/*
 * TableColumn class used to manage data shown in a table
 */
public class TableColumnConfig {
	
	public String name;
	public boolean hide;		// Only show on expand
	public boolean barcode;		// Show as barcode
	public String filterValue;	
	public String chart_type;
	public int width;
}
