package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains details of a change item
 */
public class DataItemChange {
	String col;
	String type;
	String newVal;									// Set for all types other than begin repeat
	String oldVal;									// Set for all types other than begin repeat
	ArrayList<ArrayList<DataItemChange>> changes = null;		// Set if this is a begin repeat

	// Normal constructor
	public DataItemChange(String col, String type, String newVal, String oldVal) {
		this.col = col;
		this.type = type;
		this.newVal = newVal;
		this.oldVal = oldVal;
	}
	
	// Constructor for subform
	public DataItemChange(String col, ArrayList<ArrayList<DataItemChange>> changes) {
		this.col = col;
		type = "begin repeat";
		this.changes = changes;
	}
}
