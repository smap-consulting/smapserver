package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of a change to a question or option property including labels
 */
public class PropertyChange {
	
	// Reference data about the survey or question or option to be updated or properties of a new element
	public int qId;
	public String qType;			// question type
	public String type;				// question or option (Used when updating labels)
	public String name;				// Name of question
	public String propType;			// Type of language element:  text, image, video, audio
	public String prop;				// The property to be changed
	public String languageName;		// Language to be updated
	public boolean allLanguages;	// Set to true if all languages are to be updated with the same value			
	
	// Change properties - Identifies the change to be applied
	public String newVal;			// New value to be applied (For example labels)
	public String oldVal;			// Old value - used for optimistic locking during interactive editing
	public String key;				// For Translation the "text_id" 
									// For option updates the option "value"
}
