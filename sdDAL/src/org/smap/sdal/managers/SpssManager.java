package org.smap.sdal.managers;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

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

/*
 * Manage SPSS files
 */
public class SpssManager {
	
	private static Logger log =
			 Logger.getLogger(SpssManager.class.getName());
	
	private ResourceBundle localisation;
	
	public SpssManager(ResourceBundle l) {
		localisation = l;
	}
	/*
	 * Call this function to create am SPSS variables file
	 * Return a suggested name for the PDF file derived from the results
	 */
	public String createSPS(
			Connection connectionSD, 
			String remoteUser,
			String language, 
			int sId) throws Exception {
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		org.smap.sdal.model.Survey survey = null;
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		StringBuffer sps = new StringBuffer();
		
		try {
			

		
			/*
			 * Get the results and details of the user that submitted the survey
			 */
			boolean superUser = GeneralUtilityMethods.isSuperUser(connectionSD, remoteUser);
			survey = sm.getById(connectionSD, null, remoteUser, sId, true, null, null, 
					false, false, true, false, false, "real", false, false, superUser, null,
					false,		// Do not follow links to child surveys
					false		// launched only
					);
			int languageIdx = GeneralUtilityMethods.getLanguageIdx(survey, language);
			
			/*
			 * Add the variable labels
			 */
			boolean hasVariable = false;
			sps.append("VARIABLE LABELS\n");
			for(int i = 0; i < survey.forms.size(); i++) {
				Form f = survey.forms.get(i);
				
				for(int j = 0; j < f.questions.size(); j++) {
					Question q = f.questions.get(j);

					String label = q.labels.get(languageIdx).text;
					if(label != null && !q.type.equals("begin group") && !q.type.equals("end group") && !q.readonly  && !q.columnName.equals("the_geom")) {
						if(hasVariable) {
							sps.append("\n");
						}
						hasVariable = true;
						if(q.type.equals("select")) {
							addSelectVariables(sps, q, label, languageIdx, survey.optionLists);
						} else {
							sps.append(" ");
							sps.append(q.columnName);
							addSpaces(sps, 10 - q.columnName.length());
							sps.append("'");
							sps.append(spssVariable(label));
							sps.append("'");
						}
					}
				}
			}	
			sps.append(".");
			
			// Add the value labels
			sps.append("\n");
			sps.append("\n");
			sps.append("VALUE LABELS\n");
			boolean hasValue = false;
			for(int i = 0; i < survey.forms.size(); i++) {
				Form f = survey.forms.get(i);
				
				for(int j = 0; j < f.questions.size(); j++) {
					Question q = f.questions.get(j);
					
					if((q.type.equals("select1") || q.type.equals("select")) && hasValue) {
						sps.append("\n");
					}
					
					if(q.type.equals("select1")) {
						hasValue = true;
						sps.append(" ");
						sps.append(q.columnName);
						sps.append("\n");
						
						addSelect1Values(sps, q, languageIdx, survey.optionLists);
						
					} else if(q.type.equals("select")) {
						hasValue = true;
						addSelectValues(sps, q, languageIdx, survey.optionLists);
					}
				}
			}	
			sps.append(".");
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);	
			throw e;
		} finally {
			
		}
		
		return sps.toString();
	
	}
	
	private void addSelectVariables(StringBuffer sps, Question q, String qLabel, int languageIdx, HashMap<String, OptionList> lists) {
		ArrayList<Option> options = lists.get(q.list_name).options;
		
		boolean hasVariable = false;
		for(Option o : options) {
			String optionName = q.columnName + "__" + o.columnName;
			String label = o.labels.get(languageIdx).text;
			if(label != null) {
				if(hasVariable) {
					sps.append("'\n");
				}
				hasVariable = true;
				sps.append(" ");
				sps.append(optionName);
				addSpaces(sps, 10 - optionName.length());
				sps.append("'");
				String combinedLabel = label + " - " + qLabel;
				sps.append(spssVariable(combinedLabel));
				
			}
		}
		
	}
	
	private void addSelect1Values(StringBuffer sps, Question q, int languageIdx, HashMap<String, OptionList> lists) {
		
		ArrayList<Option> options = lists.get(q.list_name).options;
		
		boolean hasLabel = false;
		for(int i = 0; i < options.size(); i++) {
			Option o = options.get(i);
			String optionName = o.columnName;
			String label = o.labels.get(languageIdx).text;
			if(label != null) {
				if(hasLabel) {
					sps.append("\n");
				}
				hasLabel = true;
				sps.append("     ");
				sps.append(optionName);
				addSpaces(sps, 10 - optionName.length());
				sps.append("'");
				sps.append(spssVariable(label));
				sps.append("'");
				if(i == options.size() - 1) {
					sps.append("  /");
				}
			}
		}
		
	}
	
	private void addSelectValues(StringBuffer sps, Question q, int languageIdx, HashMap<String, OptionList> lists) {
		
		ArrayList<Option> options = lists.get(q.list_name).options;
		
		boolean hasLabel = false;
		for(Option o : options) {
			String optionName = q.columnName + "__" + o.columnName;
		
			if(hasLabel) {
				sps.append("\n");
			}
			hasLabel = true;
				
			sps.append(" ");
			sps.append(optionName);
			sps.append("\n");
			
			sps.append("     1         'yes'\n");
			sps.append("     0         'no'  /");
		}
		
	}

	private void addSpaces(StringBuffer sb, int length) {
		if(length < 2) {
			length = 2;			// Add a minimum of 2 spaces
		}
		for(int i = 0; i < length; i ++) {
			sb.append(" ");
		}
	}

	/*
	 * Fix up any invalid characters in SPSS variables
	 */
	private String spssVariable(String in) {
		
		String out = in;
		
		// Escape quotes
		out = out.replaceAll("'", "''");
		
		// restrict to 251 bytes
		if(out.length() > 248) {
			out = out.substring(0, 248);
		}
		
		return out;
	}
		



	
	/*
	 * Set space before this item
	 * Appearance is:  pdfheight_## where ## is the height
	 */
	void setSpace(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.space = Integer.valueOf(parts[1]);   		
		}
		
	}
	

}


