package org.smap.sdal.model;

import java.util.logging.Logger;

/*
 * Contains details of a change
 * Contains details of each change to a survey
 * This replaces the old ChangeItem Class
 */
public class ChangeElement {
	
	private static Logger log =
			 Logger.getLogger(ChangeElement.class.getName());
	
	// Constructor to create a ChangeElement from the old ChangeItem object
	public ChangeElement(ChangeItem ci, String action) {
		
		this.action = action;
		
		if(ci.question != null) {
			this.type = "question";
		} else if(ci.option != null) {
			this.type = "option";
		} else if(ci.property != null) {
			if(ci.property.type.equals("option")) {
				this.type = "option";
			} else if(ci.property.type.equals("question")) {
				this.type = "question";
			} else if(ci.property.type.equals("optionlist")) {
				this.type = "optionlist";
			} else {
				this.type = "unknown";
				log.info("Error: unknown change type");
			}
		}
		
		source = ci.source;
		fileName = ci.fileName;
		origSId = ci.origSId;
		if(ci.property != null) {
			property = new PropertyChangeElement(ci.property);
		}
		question = ci.question;
		option = ci.option;
		
	}
	
	public ChangeElement() {
		
	}
	
	public String action;			// move | add | delete | update | external option
	public String type;				// question | option
	public String source;			// editor | file
	
	// Reference data about the survey or question or option to be updated or properties of a new element
	public PropertyChangeElement property;	// Details of a change to a question or option property
	public Question question;				// Details of a new question
	public Option option;					// Details of a new option
	
	// Miscelaneous data
	public String fileName;			// External file name used to load changes to choices
	public String msg;
	public int origSId;
	
	// Properties for logging
	//public int cId;					// The database key for this change (only used when reading a change item from the log)
	//public int version;				// The survey version that this applies to
	//public String userName;			// The name of the user who made this change

	//public String changeType;		// Copied from the changeset and added to the log for each change item

	//public Timestamp updatedTime;	// The time that this update was made (Only used when reading a change item from the log)
	//public boolean  apply_results;	// Set true once the change has been attempted to be applied to the results database
	//public boolean success;			// Set true if the change item has been successfully applied to the results database
	//public String msg;				// Error messages
}
