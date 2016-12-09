package org.smap.sdal.model;

public class Alert {
	public int id;
	public String userIdent;
	public String status;			// open || complete				
	public int priority;			// 1 - High, 3 - Low
	public String link;
	public String message;
	public String updatedTime;
	public int since;				// Seconds since the alert was raised
	
}
