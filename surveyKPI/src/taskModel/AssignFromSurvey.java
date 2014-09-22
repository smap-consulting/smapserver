package taskModel;

import java.util.ArrayList;

import model.SqlWhereClause;

public class AssignFromSurvey {
	public String task_group_name;
	public int source_survey_id;
	public String project_name;
	public String source_survey_name;
	public String survey_name;
	public int form_id;
	public boolean update_results;
	public NewTasks new_tasks;			// Set if tasks created on the client are to be set
	public ArrayList<TaskAddressSettings> address_columns;
	public SqlWhereClause filter;
}
