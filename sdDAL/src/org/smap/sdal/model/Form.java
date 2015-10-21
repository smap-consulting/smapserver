package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Form Class
 * Used for survey editing
 */
public class Form {
	public int id;
	public String name;
	public int parentform;
	public int parentQuestion;
	public String tableName;		// Name of the table that holds the results for this form
	public String repeat_count;
	public ArrayList<Question> questions = new ArrayList<Question> ();

}
