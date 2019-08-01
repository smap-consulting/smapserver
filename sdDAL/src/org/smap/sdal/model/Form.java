package org.smap.sdal.model;

import java.util.ArrayList;

import org.smap.sdal.Utilities.GeneralUtilityMethods;

/*
 * Form Class
 * Used for survey editing
 */
public class Form {
	public int id;
	public String name;
	public String referenceName;		// The name of the form that contains the data used by a reference form
	public int parentform;
	public int parentFormIndex;		// Used by the editor instead of the parent form id which may not be known during form creation
	public int parentQuestion;
	public int parentQuestionIndex;
	public String tableName;			// Name of the table that holds the results for this form
	public boolean reference;		// True if this form does not contain its own data and just presents a reference view of another forms data
	public boolean merge;			// True if this forms results should be merged with existing repeats
	public boolean replace;			// True if old records should be replaced
	public ArrayList<Question> questions = new ArrayList<Question> ();
	
	// Basic constructor
	public Form() {
		
	}
	
	// Constructor for creating new form
	public Form(String name, int parentFormIndex, int parentQuestionIndex) {
		this.name = name;
		this.parentFormIndex = parentFormIndex;
		this.parentQuestionIndex = parentQuestionIndex;
	}
	
}
