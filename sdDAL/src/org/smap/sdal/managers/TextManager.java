package org.smap.sdal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.PdfPageSizer;
import org.smap.sdal.Utilities.PdfUtilities;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Row;
import org.smap.sdal.model.ServerData;
import org.smap.sdal.model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PushbuttonField;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerFontProvider;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.css.CssFile;
import com.itextpdf.tool.xml.css.StyleAttrCSSResolver;
import com.itextpdf.tool.xml.html.CssAppliers;
import com.itextpdf.tool.xml.html.CssAppliersImpl;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.ElementHandlerPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;

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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class TextManager {
	
	private static Logger log =
			 Logger.getLogger(TextManager.class.getName());
	

	
	private class GlobalVariables {																// Level descended in form hierarchy

		// Map of questions that need to have the results of another question appended to their results in a pdf report
		HashMap <String, ArrayList<String>> addToList = new HashMap <String, ArrayList<String>>();
	}

	/*
	 * Call this function to create a PDF
	 * Return a suggested name for the PDF file derived from the results
	 */
	public ArrayList<String> createTextOutput(
			Connection sd,
			Connection cResults,
			ArrayList<String> text,
			String basePath, 
			String remoteUser,
			org.smap.sdal.model.Survey survey,
			int utcOffset,
			String language) {		
		
		User user = null;
		
		ServerManager serverManager = new ServerManager();
		ServerData serverData = serverManager.getServer(sd);
		
		UserManager um = new UserManager();
		int [] repIndexes = new int[20];		// Assume repeats don't go deeper than 20 levels

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
			for(int i = 0; i < survey.instance.results.size(); i++) {
				getDependencies(gv, survey.instance.results.get(i), survey, i);	
			}
			
			
			for(int i = 0; i < survey.instance.results.size(); i++) {
				replaceTextParameters(sd, gv, text, survey.instance.results.get(i), basePath, null, i, survey, languageIdx);
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
			GlobalVariables gv,
			ArrayList<String> text,
			ArrayList<Result> record, 
			String basePath,
			String formName,
			int repeatIndex,
			org.smap.sdal.model.Survey survey,
			int languageIdx) throws IOException, DocumentException {
		try {
			
			for(Result r : record) {
				
				String value = "";
				String fieldName = getFieldName(formName, repeatIndex, r.name);
				
				DisplayItem di = new DisplayItem();
				try {
					Form form = survey.forms.get(r.fIdx);
					org.smap.sdal.model.Question question = form.questions.get(r.qIdx);	
				} catch (Exception e) {
					// If we can't get the question details for this data then that is ok
				}
				
				/*
				 * Set the value based on the result
				 * Process subforms if this is a repeating group
				 */
				if(r.type.equals("form")) {
					for(int k = 0; k < r.subForm.size(); k++) {
						replaceTextParameters(sd, gv, text, r.subForm.get(k), basePath, fieldName, k, survey, languageIdx);
					} 
				} else if(r.type.equals("select1")) {
					for(Result c : r.choices) {
						if(c.isSet) {
							// value = c.name;
							
							Option option = survey.optionLists.get(c.listName).options.get(c.cIdx);
							Label label = option.labels.get(languageIdx);
							value = GeneralUtilityMethods.unesc(label.text);
							
							break;
						}
					}
				} else if(r.type.equals("select")) {
					value = "";		// Going to append multiple selections to value
					for(Result c : r.choices) {
						if(c.isSet) {
							// value = c.name;
							if(!c.name.equals("other")) {
							
								Option option = survey.optionLists.get(c.listName).options.get(c.cIdx);
								Label label = option.labels.get(languageIdx);
								if(value.length() > 0) {
									value += ", ";
								}
								value += GeneralUtilityMethods.unesc(label.text);
							}
						}
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
						s = s.replaceAll("\\$\\{" + fieldName + "\\}", value);
						text.set(i, s);
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
			name = formName + "[" + index + "]." + qName;
		}
		return name;
	}
	
	private class UserSettings {
		String title;
		String license;
	}
	
	/*
	 * Fill the template with data from the survey
	 */
	private static void fillTemplateUserDetails(AcroFields pdfForm, User user, String basePath) throws IOException, DocumentException {
		try {
					
			pdfForm.setField("user_name", user.name);
			pdfForm.setField("user_company", user.company_name);

			/*
			 * User configurable data TODO This should be an array of key value pairs
			 * As interim use a hard coded class to hold the data
			 */
			String settings = user.settings;
			Type type = new TypeToken<UserSettings>(){}.getType();
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			UserSettings us = gson.fromJson(settings, type);
			
			if(us != null) {
				pdfForm.setField("user_title", us.title);
				pdfForm.setField("user_license", us.license);
				
				PushbuttonField ad = pdfForm.getNewPushbuttonFromField("user_signature");
				if(ad != null) {
					ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
					ad.setProportionalIcon(true);
					String filename = null;
					try {
						filename = basePath + "/media/users/" + user.id + "/sig/"  + user.signature;
						ad.setImage(Image.getInstance(filename));
					} catch (Exception e) {
						log.info("Error: Failed to add signature " + filename + " to pdf");
					}
					pdfForm.replacePushbuttonField("user_signature", ad.getField());
				} else {
					//log.info("Picture field: user_signature not found");
				}
			}
				
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error filling template", e);
		}
	}


	
	/*
	 * Convert the results  and survey definition arrays to display items
	 */
	ArrayList<DisplayItem> convertChoiceListToDisplayItems(
			org.smap.sdal.model.Survey survey, 
			org.smap.sdal.model.Question question,
			ArrayList<Result> choiceResults,
			int languageIdx) {
		
		ArrayList<DisplayItem> diList = null;
		if(choiceResults != null) {
			diList = new ArrayList<DisplayItem>();
			for(Result r : choiceResults) {

				Option option = survey.optionLists.get(r.listName).options.get(r.cIdx);
				Label label = option.labels.get(languageIdx);
				DisplayItem di = new DisplayItem();
				di.text = label.text == null ? "" : label.text;
				di.name = r.name;
				di.type = "choice";
				di.isSet = r.isSet;
				diList.add(di);
			}
		}
		return diList;
	}

	/*
	 * Get the value of a select question
	 */
	String getSelectValue(boolean isSelectMultiple, DisplayItem di, ArrayList<String> deps) {
		StringBuffer sb = new StringBuffer("");
		
		for(DisplayItem aChoice : di.choices) {
			
			if(isSelectMultiple) {
				if(aChoice.isSet) {
				
					if(deps == null || (aChoice.name != null && !aChoice.name.trim().toLowerCase().equals("other"))) {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}
					
				} 
			} else {
				if(aChoice.isSet) {
					
					if(deps == null || (aChoice.name != null && !aChoice.name.trim().toLowerCase().equals("other"))) {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}

				}
			}

			
		}
			
		return sb.toString();
		
	}
	

}


