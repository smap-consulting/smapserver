package org.smap.sdal.model;

public class TaskGroup {
	public int	tg_id;
	public String name;
	public String address_params;
	public int p_id;
	public AssignFromSurvey rule;
	public int source_s_id;
	public int target_s_id;
	
	public int totalTasks;
	public int completeTasks;
	public TaskEmailDetails emaildetails; 
}
