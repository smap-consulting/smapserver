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
	public String value;
	public String defLabel;
	public boolean externalFile;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	public String text_id;
	public String path;
	public HashMap<String, String> cascadeKeyValues = null;
	
	// Used in updates
	public String optionList;	
}
