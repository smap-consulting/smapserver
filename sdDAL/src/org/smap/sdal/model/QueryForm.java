package org.smap.sdal.model;

import java.util.ArrayList;

public class QueryForm {
	public int project;
	public String project_name;
	
	public int survey;
	public String survey_name;
	
	public int form;
	public String form_name;
	
	public int fromQuestionId;
	public int toQuestionId;
	
	// Used during export
	public String table;
	public int parent;
	public int surveyLevel;
	public boolean hidden;		// This is a hidden form only used for linking.  No data is returned	
	public ArrayList<QueryForm> childForms = null;
}
