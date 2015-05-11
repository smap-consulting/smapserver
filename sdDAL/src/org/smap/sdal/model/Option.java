package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Options Class
 * Used for survey editing
 */
public class Option {
	public int id;
	public int seq;
	public String value;
	public String defLabel;
	public boolean externalFile;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	public String text_id;
	public String cascadeFilters;
	
	// Used in updates
	public String optionList;	
	public int sId;			// Survey used in updates
}
