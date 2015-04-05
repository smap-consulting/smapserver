package org.smap.sdal.model;

import java.util.ArrayList;


public class Result {
	public int fIdx;			// Form Index
	public int qIdx;			// Question Index
	public int cIdx;			// Choice Index
	public String name;
	public String value;
	public boolean isSet;		// Used with choices
	public String resultsType;	// form || bg (begin group) || eg (end group) || question type || choice 
	public ArrayList<ArrayList<Result>> subForm = null;
	public ArrayList<Result> choices = null;
	public Label label;
	
	public Result (String n, String t, String v, boolean set, int f, int q, int c) {
		name = n;
		resultsType = t;
		value = v;
		isSet = set;
		fIdx = f;
		qIdx = q;
		cIdx = c;
		
		if(t.startsWith("select")) {
			choices = new ArrayList<Result> ();
		} else if(t.equals("form")) {
			subForm = new ArrayList<ArrayList<Result>> ();
		}
	}
}
