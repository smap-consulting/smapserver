package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.Collection;

/*
 * Form Class
 * Used for survey editing
 */
public class Question {
	public int id;
	public String name;
	public String colName;			// The name of the database column for this question
	public String type;
	public String text_id;
	public String hint_id;
	public String list_name;		// A reference to the list of options
	public int seq;
	public String defaultanswer;
	public String appearance;
	public String source;
	public String calculation;
	public boolean inMeta;			// Set true if the question is in the meta group
	public ArrayList<Label> labels = new ArrayList<Label> ();
	//public ArrayList<Label> labels_orig = new ArrayList<Label> ();

	/*
	 * Get the selectatble choices for this question
	 *  If the choices came from an external file then one of the choices will be a dummy choice describing the file
	 *  in that case only return the choices marked as coming from an external file
	 */
	public Collection<Option> getValidChoices(Survey s) {
		
		ArrayList<Option> externalChoices = new ArrayList<Option> ();
		ArrayList<Option> choiceArray = s.optionLists.get(list_name);
		boolean external = false;
		for(int i = 0; i < choiceArray.size(); i++) {
			if(choiceArray.get(i).externalFile) {
				external = true;
				externalChoices.add(choiceArray.get(i));
			}
		}
		if(external) {
			return externalChoices;
		} else {
			return choiceArray;
		}
	}
	
}
