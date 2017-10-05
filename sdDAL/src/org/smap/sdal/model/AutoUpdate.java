package org.smap.sdal.model;

public class AutoUpdate {
	public String type;				// imagelabel || ...
	public String tableName;
	public String sourceColName;			// Column that has the source of the data
	public String targetColName;			// Column that will store the labels
	
	public String labelColType;				// For image labels set to label || text
	
	public AutoUpdate(String type) {
		this.type = type;
	}
}
