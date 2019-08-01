package org.smap.sdal.managers;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Survey;
import com.itextpdf.text.DocumentException;

/*****************************************************************************

This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 ******************************************************************************/

public class TextManager {
	
	private static Logger log =
			 Logger.getLogger(TextManager.class.getName());
	
	private ResourceBundle localisation;
	private String tz;
	private ChoiceManager choiceManager = null;
	
	private class GlobalVariables {																// Level descended in form hierarchy

		// Map of questions that need to have the results of another question appended to their results in a pdf report
		HashMap <String, ArrayList<String>> addToList = new HashMap <String, ArrayList<String>>();
	}

	public TextManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
		choiceManager = new ChoiceManager(l, tz);
	}
	/*
	 * Create the text output for an email message
	 */
	public ArrayList<String> createTextOutput(
			Connection sd,
			Connection cResults,
			ArrayList<String> text,
			String basePath, 
			String remoteUser,
			Survey survey,
			String language,
			int oId) {		

		try {
			
			if(language != null) {
				language = language.replace("'", "''");	// Escape apostrophes
			} else {
				language = "none";
			}
			int languageIdx = GeneralUtilityMethods.getLanguageIdx(survey, language);
	
			/*
			 * Get dependencies between Display Items, for example if a question result should be added to another
			 *  question's results
			 */
			GlobalVariables gv = new GlobalVariables();
			if(survey != null) {
				for(int i = 0; i < survey.instance.results.size(); i++) {
					getDependencies(gv, survey.instance.results.get(i), survey, i);	
				}		
				
				for(int i = 0; i < survey.instance.results.size(); i++) {
					replaceTextParameters(sd, cResults, remoteUser, gv, text, survey.instance.results.get(i), basePath, null, i, survey, languageIdx, oId);
				}
			} else {
				// Perhaps the survey has been deleted
				log.log(Level.SEVERE, "Survey null when attempting to get data values");
			}
				
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			
		}
		
		return text;
	
	}
	
	/*
	 * Get dependencies between question
	 */
	private void getDependencies(GlobalVariables gv,
			ArrayList<Result> record,
			org.smap.sdal.model.Survey survey,
			int recNumber) {
		
		for(int j = 0; j < record.size(); j++) {
			Result r = record.get(j);
			if(r.type.equals("form")) {
				for(int k = 0; k < r.subForm.size(); k++) {
					getDependencies(gv, r.subForm.get(k), survey, k);
				}
			} else {
				
				if(r.appearance != null && r.appearance.contains("pdfaddto")) {
					String name = getReferencedQuestion(r.appearance);
					if(name != null) {
						String refKey = r.fIdx + "_" + recNumber + "_" + name; 
						ArrayList<String> deps = gv.addToList.get(refKey);
						
						if(deps == null) {
							deps = new ArrayList<String> ();
							gv.addToList.put(refKey, deps);
						}
						deps.add(r.value);
					}
				}
			}
		}
	}
	
	private String getReferencedQuestion(String app) {
		String name = null;
		
		String [] appValues = app.split(" ");
		for(int i = 0; i < appValues.length; i++) {
			if(appValues[i].startsWith("pdfaddto")) {
				int idx = appValues[i].indexOf('_');
				if(idx > -1) {
					name = appValues[i].substring(idx + 1);
				}
				break;
			}
		}
		
		return name;
	}
	
	
	/*
	 * Fill the template with data from the survey
	 */
	private void replaceTextParameters(
			Connection sd,
			Connection cResults,
			String user,
			GlobalVariables gv,
			ArrayList<String> text,
			ArrayList<Result> record, 
			String basePath,
			String formName,
			int repeatIndex,
			Survey survey,
			int languageIdx,
			int oId) throws IOException, DocumentException {
		try {
			
			for(Result r : record) {
				
				String value = "";
				String fieldName = getFieldName(formName, repeatIndex, r.name);
				
				/*
				 * Set the value based on the result
				 * Process subforms if this is a repeating group
				 */
				if(r.type.equals("form")) {
					for(int k = 0; k < r.subForm.size(); k++) {
						replaceTextParameters(sd, cResults, user, gv, text, r.subForm.get(k), basePath, fieldName, k, survey, languageIdx, oId);
					} 
				} else if(r.type.equals("select1")) {
					Form form = survey.forms.get(r.fIdx);
					Question question = form.questions.get(r.qIdx);
					
					ArrayList<String> matches = new ArrayList<String> ();
					matches.add(r.value);
					value = choiceManager.getLabel(sd, cResults, user, oId, survey.id, question.id, question.l_id, question.external_choices, question.external_table, 
							survey.languages.get(languageIdx).name, languageIdx, matches, survey.ident);
				
				} else if(r.type.equals("select")) {
					
					String nameValue = r.value;
					if(nameValue != null) {
						String vArray [] = value.split(" ");
						ArrayList<String> matches = new ArrayList<String> ();
						if(vArray != null) {
							for(String v : vArray) {
								matches.add(v);
							}
						}
						Form form = survey.forms.get(r.fIdx);
						Question question = form.questions.get(r.qIdx);

						value = choiceManager.getLabel(sd, cResults, user, oId, survey.id, question.id, question.l_id, question.external_choices, question.external_table, 
									survey.languages.get(languageIdx).name, languageIdx, matches, survey.ident);
							
					}
				
				} else {
					value = r.value;
				}
				
				/*
				 * Add the value to the text
				 */
				if(value == null ) {
					value = "";
				} else if(r.type.equals("geopoint") || r.type.equals("geoshape") 
						|| r.type.equals("geotrace") || r.type.startsWith("geopolygon_") 
						|| r.type.startsWith("geolinestring_")) {			
					
				} else if(r.type.equals("image")) {

				} else {
					
					for(int i = 0; i < text.size(); i++) {
						String s = text.get(i);
						if(s != null) {
							/*
							 * Try a specific replacement including path and then a general replacement with just the name
							 */
							s = s.replaceAll("\\$\\{" + fieldName + "\\}", value);
							String [] fArray = fieldName.split("\\]\\.");
							if(fArray.length > 0) {
								s = s.replaceAll("\\$\\{" + fArray[fArray.length - 1] + "\\}", value);
							}
							
							text.set(i, s);
						}
					}
				} 
				
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error filling template", e);
		}
	}
	
	private String getFieldName(String formName, int index, String qName) {
		String name = null;
		
		if(formName == null || formName.equals("")) {
			name = qName;
		} else {
			name = formName + "\\[" + index + "\\]." + qName;
		}
		return name;
	}
	

}


