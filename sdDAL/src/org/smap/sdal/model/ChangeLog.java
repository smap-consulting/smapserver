package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of a change log entry
 */
public class ChangeLog {
	
	// Reference data about the survey or question or option to be updated or properties of a new element
	public ChangeElement change;	// Details of a change
	
	// Properties for logging
	public int cId;					// The database key for this change (only used when reading a change item from the log)
	public int version;				// The survey version that this applies to
	public String userName;			// The name of the user who made this change
	public Timestamp updatedTime;	// The time that this update was made (Only used when reading a change item from the log)
	public boolean  apply_results;	// Set true once the change has been attempted to be applied to the results database
	public boolean success;			// Set true if the change item has been successfully applied to the results database
	public String msg;				// Error messages
}
