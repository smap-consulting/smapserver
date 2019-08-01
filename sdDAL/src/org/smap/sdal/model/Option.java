package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * Options Class
 * Used for survey editing
 */
public class Option {
	public int id;
	public int seq;
	public int sourceSeq;
	public String value;
	public String columnName;
	public String display_name;
	public String defLabel;
	public boolean externalFile;
	public boolean published;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	public ArrayList<LanguageItem> externalLabel = new ArrayList<LanguageItem> ();
	public String text_id;
	public String path;
	public HashMap<String, String> cascade_filters = null;
	
	// Used in updates
	public String optionList;	
	public int l_id;
	public String sourceOptionList;	
}
