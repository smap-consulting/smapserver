package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.itextpdf.text.BaseColor;

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
	public String path;
	public String list_name;		// A reference to the list of options
	public int seq;
	public String defaultanswer;
	public String appearance;
	public String choice_filter;
	public String source;
	public String calculation;
	public String constraint;
	public String constraint_msg;
	public String relevant;
	public boolean visible;
	public boolean inMeta;			// Set true if the question is in the meta group
	int width = -1;						// Display width, generated from appearance column 
									//  (for the moment - should probably have its own database 
									//  entry but I want to maintain compatibility with xlsform)
	public ArrayList<Label> labels = new ArrayList<Label> ();
	
	private static Logger log =
			 Logger.getLogger(Question.class.getName());

	/*
	 * Get the selectable choices for this question
	 *  If the choices came from an external file then one of the choices will be a dummy choice describing the file
	 *  in that case only return the choices marked as coming from an external file
	 */
	public Collection<Option> getValidChoices(Survey s) {
		
		ArrayList<Option> externalChoices = new ArrayList<Option> ();
		ArrayList<Option> choiceArray = s.optionLists.get(list_name).options;
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
	 * Update the column settings if the appearance option in this question is set to pdfcols
	 *  Return null if this question does not change the column settings
	 */
	public int [] updateCols(int [] currentCols) {
		
		int [] newCols;
		int totalWidth = 0;
		
		if(appearance != null && appearance.contains("pdfcols")) {
			
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdfcols")) {
						
						String [] parts = appValues[i].split("_");
						
						newCols = new int [parts.length - 1];
						for(int j = 1; j < parts.length; j++) {
							newCols[j - 1] = Integer.valueOf(parts[j]);
							totalWidth += newCols[j - 1];
						}
						if(totalWidth != 10) {
							newCols[newCols.length -1] += 10 - totalWidth;		// Make sure widths all add up to 10
						}
						
						if(newCols.length != currentCols.length) {
							return newCols;
						}
						
						for(int j = 0; j < newCols.length; j++) {
							if(newCols[j] != currentCols[j]) {
								return newCols;
							}
						}
						
						break;
					}
				}
			
			}
			
		}
		
		return null;

	}
	
	/*
	 * Return true if this question needs to be displayed on a new page in pdf reports
	 */
	public boolean isNewPage() {
		boolean newPage = false;
		
		if(appearance != null && appearance.contains("pdfnewpage")) {
			
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].equals("pdfnewpage")) {
						newPage = true;
						break;
					}
				}
			}
		}
		
		
		return newPage;
	}
	
}
