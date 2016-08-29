package org.smap.sdal.model;

/*
 * Form Class
 * Used for survey editing
 */
public class QuestionLite {
	public int id;
	public String type;
	public String q;				// The question for a specified language
	public String name;
	
	// Properties for Server Side Calculations
	public String fn;
	public int f_id;
	public boolean is_ssc;
	
	// Properties for Role Based Access
	public String access;
	
}
