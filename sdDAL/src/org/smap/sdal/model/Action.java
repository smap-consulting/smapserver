package org.smap.sdal.model;

import java.util.ArrayList;

public class Action {
	public String action;
	public String notify_type;
	public String notify_person;
	public String link;
	public ArrayList<Role> roles = null;
	
	// Attributes specific to an action type
	public int sId;
	public int pId;
	public int managedId;
	public int prikey;
	
	public Action(String a) {
		action = a;
	}
}
