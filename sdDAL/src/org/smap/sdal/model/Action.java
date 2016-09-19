package org.smap.sdal.model;

public class Action {
	public String action;
	public String notify_type;
	public String notify_person;
	public String link;
	
	// Attributes specific to an action type
	public int sId;
	public int managedId;
	
	public Action(String a) {
		action = a;
	}
}
