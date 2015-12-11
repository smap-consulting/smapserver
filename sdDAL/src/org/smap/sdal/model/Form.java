package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Form Class
 * Used for survey editing
 */
public class Form {
	public int id;
	public String name;
	public int parentform;
	public int parentFormIndex;		// Used by the editor instead of the parent form id which may not be known during form creation
	public int parentQuestion;
	public int parentQuestionIndex;
	public String tableName;		// Name of the table that holds the results for this form
	public String repeat_path;		// Path to the question that holds repeat count
	public ArrayList<Question> questions = new ArrayList<Question> ();

}
