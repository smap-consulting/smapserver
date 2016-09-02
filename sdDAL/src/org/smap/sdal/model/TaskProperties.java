package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class TaskProperties {
	public int id;
	public String type;
	public String name;
	public String pid;				// Project id
	public String url;
	public int form_id;
	public String form_name;
	public boolean blocked;
	public String form_version;
	public int assignee;
	public String assignee_ident;
	public boolean generate_user;			// Create a temporary user ident
	public String assignee_name;
	public String initial_data;
	public String update_id;
	public Timestamp from;
	public Timestamp to;
	public String location_trigger;
	public boolean repeat;
	public int repeat_count;
	public String address;			// Text address
	public String guidance;			// Key value pairs representing an unstructured address
	public String email;
	public String location;			// WKT version of geometry, duplicates data in geometry, used when updating location
	
	public String status;
}
