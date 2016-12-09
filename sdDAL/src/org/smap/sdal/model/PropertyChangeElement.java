package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/*
 * Contains details of an update to a question or option property including labels
 */
public class PropertyChangeElement {
	
	// Constructor to convert legacy Property Change object to PropertyChangeElement
	public PropertyChangeElement(PropertyChange pc) {
		qId = pc.qId;
		l_id = pc.l_id;
		optionList = pc.optionList;
		name = pc.name;
		labelType = pc.propType;
		prop = pc.prop;
		languageName = pc.languageName;
		allLanguages = pc.allLanguages;
		newVal = pc.newVal;
		oldVal = pc.oldVal;
		key = pc.key;
		setVisible = pc.setVisible;
		visibleValue = pc.visibleValue;
		sourceValue = pc.sourceValue;
		
	}
	
	public int qId;
	public int l_id;				// Listname id
	public String optionList;		// Listname
	public String name;				// Name of question
	public String labelType;		// text | image | video | audio
	public String prop;				// The property to be changed
	public String languageName;		// Language to be updated
	public boolean allLanguages;	// Set to true if all languages are to be updated with the same value	
	
	public String newVal;			// New value to be applied (For example labels)
	public String oldVal;			// Old value - used for optimistic locking during interactive editing
	public String key;				// For Translation the "text_id" 
									// For option updates the option "value"
	
	// Type specific supplementary changes required
	public boolean setVisible;		// If true change the visibility and source parameter of the question
	public boolean visibleValue;	// What to set the visibility to
	public String sourceValue;		// Set the source 
	
	//public String qType;			// question type
	//public String optionList;		// Option list name if an option is being updated (deprecated)
	//public String type;				// question or option (Used when updating labels)
	//public String propType;			// Type of language element:  text, image, video, audio


}
