package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of a change item
 */
public class ChangeItem {
	
	// Reference data about the survey or question or option to be updated or properties of a new element
	public int qId;
	public String qType;			// question type
	public String type;				// question or option (Used when updating labels)
	public String name;				// Name of question
	public String element;			// Type of language element:  text, image, video, audio
	public String languageName;		// Language to be updated
	public boolean allLanguages;	// Set to true if all languages are to be updated with the same value			
	
	// Change properties - Identifies the change to be applied
	public String newVal;			// New value to be applied (For example labels)
	public String oldVal;			// Old value - used for optimistic locking during interactive editing
	public String key;				// For Translation the "text_id" 
									// For option updates the option "value"
	
	// Properties for logging
	public int cId;					// The database key for this change (only used when reading a change item from the log)
	public int version;				// The survey version that this applies to
	public String userName;			// The name of the user who made this change
	public Timestamp updatedTime;	// The time that this update was made (Only used when reading a change item from the log)
}
