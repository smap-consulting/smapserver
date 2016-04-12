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
	public String form_version;
	public int assignee;
	public String assignee_ident;
	public String assignee_name;
	public String initial_data;
	public String update_id;
	public Timestamp scheduled_at;
	public String location_trigger;
	public boolean repeat;
	public String address;			// Key value pairs representing an unstructured address
	
	public String status;
}
