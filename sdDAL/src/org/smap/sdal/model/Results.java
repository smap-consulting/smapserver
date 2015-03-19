package org.smap.sdal.model;

import java.util.ArrayList;


public class Results {
	public String name;
	public String value;
	public boolean isSet;		// Used with choices
	public String resultsType;	// form || bg (begin group) || eg (end group) || question || choice || select || select1
	public ArrayList<ArrayList<Results>> subForm = null;
	public ArrayList<Results> choices = null;
	
	public Results (String n, String t, String v, boolean set) {
		name = n;
		resultsType = t;
		value = v;
		isSet = set;
		
		if(t.startsWith("select")) {
			choices = new ArrayList<Results> ();
		} else if(t.equals("form")) {
			subForm = new ArrayList<ArrayList<Results>> ();
		}
	}
}
