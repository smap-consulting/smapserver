package org.smap.sdal.model;

public class Alert {
	public int id;
	public String userIdent;
	public int status;
	public int priority;
	public String link;
	public String message;
	public String updatedTime;
	public int since;				// Seconds since the alert was raised
	
}
