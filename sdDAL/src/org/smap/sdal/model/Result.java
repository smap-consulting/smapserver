package org.smap.sdal.model;

import java.util.ArrayList;


public class Result {
	public int fIdx;			// Form Index
	public int qIdx;			// Question Index
	public int cIdx;			// Choice Index
	public String listName;		// List name if this was a choice
	public String name;
	public String value;
	public boolean isSet;		// Used with choices
	public String type;	// form || choice || key || || user || a question type
	public ArrayList<ArrayList<Result>> subForm = null;
	//public ArrayList<Result> choices = null;   -- Move to compressed format
	//public Label label;
	public String appearance;	// Appearance directives including nopdf
	
	public Result (String n, String t, String v, boolean set, int f, int q, int c, String ln, String app) {
		name = n;
		type = t;
		value = v;
		isSet = set;
		fIdx = f;
		qIdx = q;
		cIdx = c;
		listName = ln;
		appearance = app;
		
		//if(t.startsWith("select")) {
		//	choices = new ArrayList<Result> ();
		//} else 
		if(t.equals("form")) {
			subForm = new ArrayList<ArrayList<Result>> ();
		}
	}
}
