package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;

/*
 * Task Properties
 */
public class TaskProperties {	
	// Properties exposed via Task API
	public int tg_id;
	public String name;
	public int form_id;						// Survey ID sent back from client - Deprecate, survey_ident should be used
	public String survey_ident;
	public String assignee_type;				// user || role   (Default to user)
	public String assignee_ident;
	public boolean generate_user;			// Create a temporary user ident (Default to false_
	public String initial_data_source;		// none || survey || task			
	public String update_id;					// Unique identifier of record to be updated
	public Timestamp from;
	public Timestamp to;
	public String location_trigger;			// NFC UID
	public String location_group;
	public String location_name;	
	public String save_type;
	public boolean repeat;
	public int repeat_count;
	public String address;			// Text address
	public String guidance;			// Key value pairs representing an unstructured address
	public String emails;
	public double lon;				// Duplicates geometry information, used when updating the location
	public double lat;
	public boolean complete_all;			// When set true all the assignments associated to a task need to be completed	
	public Instance initial_data;	// The actual data in json format
	public int show_dist;			// The distance at which the task will be shown on the phone	
	
	// Other properties
	public int id;
	public String tg_name;
	public int a_id;					// The assignment ID which is the true identifier of the task (ie the task is the task defn + assignment)
	public String action_link;
	public String survey_name;
	public boolean blocked;
	public String form_version;
	public int assignee;
	public String assignee_name;
	public String comment;
	public String status;
}
