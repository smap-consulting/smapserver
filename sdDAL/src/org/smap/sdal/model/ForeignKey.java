package org.smap.sdal.model;

public class ForeignKey {
	public int id;
	public String sId;
	public String qName;
	public String instanceId;
	public int primaryKey;		// The primary key of the record that needs to be updated with the foreign key
	public String tableName;		// The table of data that needs to be updated with the foreign key
	public String instanceIdLaunchingForm ;	// This instance Id from the launching form
}
