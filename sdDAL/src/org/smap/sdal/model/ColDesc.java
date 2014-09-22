package org.smap.sdal.model;

import java.util.ArrayList;

public class ColDesc {
	public String name;
	public String google_type;
	public String db_type;
	public String qType;
	public String label;
	public boolean needsReplace;
	public ArrayList<OptionDesc> optionLabels = null;
	
	public ColDesc(String n, String db_type, String qType, String label, 
			ArrayList<OptionDesc> optionLabels,
			boolean needsReplace) {
		name = n;
		this.db_type = db_type;
		this.qType = qType;
		this.label = label;
		this.optionLabels = optionLabels;
		this.needsReplace = needsReplace;
		
		// Google Maps Engine supports the types "String", "Integer", and "Real"
		if(db_type.startsWith("int") || db_type.equals("serial")) {
			google_type = "Integer";
		} else if(db_type.startsWith("double") || db_type.equals("real")) {
			google_type = "Real";
		} else {
			google_type = "String";
		}
	}
}
