package org.smap.sdal.model;

/*
 * Used when downloading tasks to fieldTask
 */
import java.sql.Timestamp;

public class Task {
	public int id;
	public String title;
	public String pid;				// Project id
	public String url;
	public String form_id;
	public String form_version;
	public String update_id;
	public String initial_data_source;	// none || survey || task
	public Timestamp scheduled_at;
	public String location_trigger;
	public boolean repeat;
	public String address;			// Key value pairs representing an unstructured address
	public String type;				// Retain so as not to break older versions of fieldTask
	public int show_dist;			// Show distance in meters
}
