package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Form Class
 * Used for survey editing
 */
public class Question {
	public int id;
	public String name;
	public String type;
	public String text_id;
	public String hint_id;
	public String list_name;		// A reference to the list of options
	public String appearance;
	public String source;
	public String calculation;
	public boolean inMeta;			// Set true if the question is in the meta group
	public ArrayList<Label> labels = new ArrayList<Label> ();
	
}
