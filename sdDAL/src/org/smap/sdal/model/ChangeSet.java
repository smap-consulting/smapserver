package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class ChangeSet {
	public String type;
	public boolean updateFailed = false;
	public String errorMsg;
	public ArrayList<ChangeItem> items;	
}
