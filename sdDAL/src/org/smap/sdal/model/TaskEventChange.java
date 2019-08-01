package org.smap.sdal.model;

import java.util.Date;

/*
 * Contains details of an event within a tasks lifecycle
 */
public class TaskEventChange {
	Date when;
	String name;
	String status;
	String assigned;
	String comment;

	// Normal constructor
	public TaskEventChange(String name, String status, String assigned, String comment) {
		
		this.when = new Date();
		this.name = name;
		this.status = status;
		this.assigned = assigned;
		this.comment = comment;
	}
}
