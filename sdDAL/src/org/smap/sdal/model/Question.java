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
	public String list_name;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	
}
