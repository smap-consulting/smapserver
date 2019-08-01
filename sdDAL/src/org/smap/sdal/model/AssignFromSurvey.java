package org.smap.sdal.model;

import java.util.ArrayList;

public class AssignFromSurvey {
	public String task_group_name;
	public int dl_dist;
	public int show_dist;			// The distance in meters at which the task will be downloaded
	public int source_survey_id;		// This value is not maintained and will change if the survey is replaced - the current version will be in a separate database column
	public int target_survey_id;		// This value is not maintained and will change if the survey is replaced - the current version will be in a separate database column
	public String project_name;
	public String source_survey_name;
	public String survey_name;
	public int user_id;				// Either user id, or role id are used to assign people directly at task group creation
	public int role_id;
	public int fixed_role_id;
	public String assign_data;		// Rule used to identify the person or the role that the task should be assigned to using the base data
	public int task_group_id;
	public boolean blank;			// initial data - only one of these can be true
	public boolean prepopulate;		// initial data - only one of these can be true
	public boolean update_results;	// initial data - only one of these can be true
	public boolean add_future;
	public boolean add_current;
	public NewTasks new_tasks;			// Set if tasks created on the client are to be set
	public ArrayList<TaskAddressSettings> address_columns;
	public SqlWhereClause filter;
	public int taskStart;				// ID of question that determines start time
	public String taskStartType;			// date o datetime
	public int taskAfter;				// How long after start date
	public String taskUnits;				// Units for how long after
	public int taskDuration;				// Duration of the task
	public String durationUnits;			// Units for the duration 
	public String emails;				// Comma separated list of emails
	public TaskEmailDetails emailDetails;	
}
