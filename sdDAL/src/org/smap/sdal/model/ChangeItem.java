package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of a change to a question
 */
public class ChangeItem {
	public int qId;
	public String type;				// question or option
	public String element;
	public String languageName;
	public String newVal;
	public String oldVal;
	public String name;
	public String transId;
	public String optionList;	
	
	public int cId;					// The database key for this change 
	public int version;				// The survey version that this applies to
	public String userName;			// The name of the user who made this change
	public Timestamp updatedTime;	// The time that this update was made
}
