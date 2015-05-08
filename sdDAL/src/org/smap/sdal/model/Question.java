package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Form Class
 * Used for survey editing
 */
public class Question {
	public int id;
	public int fId;
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
	int width = -1;						// Display width, generated from appearance column 
									//  (for the moment - should probably have its own database 
									//  entry but I want to maintain compatability with xlsform)
	public ArrayList<Label> labels = new ArrayList<Label> ();
	//public ArrayList<Label> labels_orig = new ArrayList<Label> ();
	
	private static Logger log =
			 Logger.getLogger(Question.class.getName());

	/*
	 * Get the selectable choices for this question
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
	
	/*
	 * Get the width of the question
	 *  This is obtained primarily from the width attribute, if it is not set their then get it
	 *   from the appearance attribute
	 *  Special values:
	 *    -1:  Width not set
	 *    0:   No width specified set to remaining width of row
	 */
	public int getWidth() {
		
		/*
		 * If the width has not been set yet attempt to get it from appearance
		 */
		if(width == -1) {
			width = 0;
			if(appearance != null) {
				if(appearance.matches("w[0-9]")) {		// TODO proper regex to match any w[0-9]
					
					int idx1 = appearance.indexOf('w');	// TODO do this properly
					int idx2 = appearance.indexOf(' ', idx1);
					String sWidth = null;
					if(idx2 > 0) {
						sWidth = appearance.substring(idx1 + 1, idx2);
					} else {
						sWidth = appearance.substring(idx1 + 1);
					}
					try {
						width = Integer.parseInt(sWidth);
					} catch (Exception e) {
						log.log(Level.SEVERE, "Could not parse group width: " + width);
					}
				}
			}
		}
		return width;
	}
	
}
