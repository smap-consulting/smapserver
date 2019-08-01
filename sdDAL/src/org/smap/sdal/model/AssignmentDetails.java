package org.smap.sdal.model;

import java.sql.Timestamp;

public class AssignmentDetails {
	public int id;
	public int assignee;
	public String status;
	public Timestamp completed_date;
	public Timestamp cancelled_date;
	public Timestamp deleted_date;
	public int task_id;
}
