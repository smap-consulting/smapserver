package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains details of a change item
 */
public class DataItemChangeEvent {
	
	public String event;
	public String status;
	public String userName;
	public String surveyName;
	public int surveyVersion;
	public String eventTime;
	public String tz;
	public ArrayList<DataItemChange> changes;
	public TaskItemChange task;
	public SubmissionMessage notification;
	public String description;	
}
