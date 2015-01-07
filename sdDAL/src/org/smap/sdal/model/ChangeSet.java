package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class ChangeSet {
	public String type;						// Type of change
	public ArrayList<ChangeItem> items;		// Set of changes of the above type
	
	// Response data 
	public boolean updateFailed = false;
	public String errorMsg;

}
