package org.smap.sdal.model;

/*
 * Used when managing hierarchies of task - assignments
 * This should replace Task Feature which combines task and assignment
 */
import java.sql.Timestamp;
import java.util.ArrayList;

public class TaskServerDefn {
	public int id;
	public String name;
	public String survey_ident;
	public String tg_name;
	public String survey_name;
	public Timestamp from;
	public Timestamp to;
	public String location_trigger;
	public String location_group;
	public String location_name;
	public String save_type;
	public boolean repeat;
	public String address;			// Text address
	public String guidance;			// Key value pairs representing an unstructured address
	public Double lat;				// Latitude
	public Double lon;				// Longitude
	public Instance initial_data;
	public int show_dist;				// Download distance in meters
	public String initial_data_source;
	public String update_id;
	
	public ArrayList<AssignmentServerDefn> assignments = new ArrayList<AssignmentServerDefn> ();
}
