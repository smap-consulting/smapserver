package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of a change item
 */
public class ChangeItem {
	
	// Reference data about the survey or question or option to be updated or properties of a new element
	public PropertyChange property;	// Details of a change to a question or option property
	public Question question;		// Details of a new question
	
	// Properties for logging
	public int cId;					// The database key for this change (only used when reading a change item from the log)
	public int version;				// The survey version that this applies to
	public String userName;			// The name of the user who made this change
	public String changeType;		// The change type copied from the change set
	public String fileName;			// External file name used to load changes to choices
	public Timestamp updatedTime;	// The time that this update was made (Only used when reading a change item from the log)
}
