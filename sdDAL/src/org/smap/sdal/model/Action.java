package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.managers.EmailManager;

public class Action {
	private static Logger log =
			Logger.getLogger(EmailManager.class.getName());
	
	public String name;
	public String action;		// respond (managed forms) || report || task
	public String notify_type;
	public String notify_person;
	public String link;
	public ArrayList<Role> roles = null;
	
	// Attributes specific to an action type
	public int sId;
	public String surveyName;
	public String surveyIdent;
	public int pId;
	public int managedId;
	public int prikey;
	public int taskKey;
	
	// Attributes for tasks
	public String datakey;
	public String datakeyvalue;
	public int assignmentId;
	
	// Attributes for reports
	public String reportType;
	public String filename;
	public Transform transform;
	
	// General parameters
	public ArrayList<KeyValueSimp> parameters = null;
	
	public Action(String a) {
		action = a;
	}
	
	public int getFormId() {
		int fId = 0;
		for(KeyValueSimp kv : parameters) {
			if(kv.k.equals("form")) {
				try {
					fId = Integer.parseInt(kv.v);
				} catch (Exception e) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}
		return fId;
	}
}
