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
	public ArrayList<Question> questions = new ArrayList<Question> ();

}
