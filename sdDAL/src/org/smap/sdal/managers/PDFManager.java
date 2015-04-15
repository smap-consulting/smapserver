package org.smap.sdal.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.User;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PushbuttonField;
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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class PDFManager {
	
	private static Logger log =
			 Logger.getLogger(PDFManager.class.getName());
	
	public static Font WingDings = null;
	public static Font defaultFont = null;



	public String createTemporaryPdfFile(
			Connection connectionSD,
			Connection cResults,
			String basePath, 
			String filename, 
			String remoteUser,
			String language, 
			int sId, 
			String instanceId) {
		
		
		String filePath = basePath + "/temp/" + filename;
					
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		
		org.smap.sdal.model.Survey survey = null;
		User user = null;
		
	
		SurveyManager sm = new SurveyManager();
		UserManager um = new UserManager();

		try {
			
			// Get fonts and embed them
			String os = System.getProperty("os.name");
			log.info("Operating System:" + os);
			
			if(os.startsWith("Mac")) {
				FontFactory.register("/Library/Fonts/Wingdings.ttf", "wingdings");
				FontFactory.register("/Library/Fonts/Arial Unicode.ttf", "default");
			} else if(os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0) {
				// Linux / Unix
				FontFactory.register("/usr/share/fonts/truetype/Wingdings.ttf", "wingdings");
				FontFactory.register("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", "default");
			}
			
			WingDings = FontFactory.getFont("wingdings", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			defaultFont = FontFactory.getFont("default", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 
			
			/*
			 * Get the results and details of the user that submitted the survey
			 */
			survey = sm.getById(connectionSD, cResults, remoteUser, sId, true, basePath, instanceId, true);
			System.out.println("User Ident: " + survey.instance.user);
			if(survey.instance.user != null) {
				user = um.getByIdent(connectionSD, survey.instance.user);
			}
			
			/*
			 * Get a template for the PDF report if it exists
			 * The template name will be the same as the XLS form name but with an extension of pdf
			 */
			int idx = survey.name.lastIndexOf('.');
			String templateName = survey.name.substring(0, idx) + ".pdf";
			System.out.println("Get the pdf template: " + templateName);
			File templateFile = new File(templateName);
			if(templateFile.exists()) {
				
				System.out.println("Template Exists");
				
				FileOutputStream outputStream = new FileOutputStream(filePath); 
				
				PdfReader reader = new PdfReader(templateName);
				PdfStamper stamper = new PdfStamper(reader, outputStream);
				int languageIdx = getLanguageIdx(survey, language);
				for(int i = 0; i < survey.instance.results.size(); i++) {
					fillTemplate(stamper.getAcroFields(), survey.instance.results.get(i), basePath, null, i, survey, languageIdx);
				}
				if(user != null) {
					fillTemplateUserDetails(stamper.getAcroFields(), user, basePath);
				}
				stamper.setFormFlattening(true);
				stamper.close();
			} else {
				System.out.println("++++No template");
			}
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			
		}
		
		return filePath;
	}
	
	/*
	 * Get the index in the language array for the provided language
	 */
	private int getLanguageIdx(org.smap.sdal.model.Survey survey, String language) {
		int idx = 0;
		
		if(survey != null && survey.languages != null) {
			for(int i = 0; i < survey.languages.size(); i++) {
				if(survey.languages.get(i).equals(language)) {
					idx = i;
					break;
				}
			}
		}
		return idx;
	}
	
	
	/*
	 * Fill the template with data from the survey
	 */
	private void fillTemplate(
			AcroFields pdfForm, 
			ArrayList<Result> record, 
			String basePath,
			String formName,
			int repeatIndex,
			org.smap.sdal.model.Survey survey,
			int languageIdx) throws IOException, DocumentException {
		try {
			
			boolean status = false;
			String value = "";
			for(Result r : record) {
				
				boolean hideLabel = false;
				String fieldName = getFieldName(formName, repeatIndex, r.name);
				
				if(r.type.equals("form")) {
					for(int k = 0; k < r.subForm.size(); k++) {
						fillTemplate(pdfForm, r.subForm.get(k), basePath, fieldName, k, survey, languageIdx);
					} 
				} else if(r.type.equals("select1")) {
					for(Result c : r.choices) {
						if(c.isSet) {
							// value = c.name;
							if(c.name.equals("other")) {
								hideLabel = true;
							}
							
							Option option = survey.optionLists.get(c.listName).get(c.cIdx);
							Label label = option.labels.get(languageIdx);
							value = label.text;
							
							break;
						}
					}
				} else if(r.type.equals("image")) {
					System.out.println("adding image: " + fieldName + " : " + r.value);
					PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
					if(ad != null) {
						ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
						ad.setProportionalIcon(true);
						try {
							ad.setImage(Image.getInstance(basePath + "/" + r.value));
						} catch (Exception e) {
							log.info("Error: Failed to add image " + basePath + "/" + r.value + " to pdf");
						}
						pdfForm.replacePushbuttonField(fieldName, ad.getField());
						System.out.println("Adding image to: " + fieldName);
					} else {
						System.out.println("Picture field: " + fieldName + " not found");
					}
				} else {
					value = r.value;
				}
	
				if(value != null && !value.equals("") && !r.type.equals("image")) {
					status = pdfForm.setField(fieldName, value);			
					System.out.println("Set field: " + status + " : " + fieldName + " : " + value);
					if(hideLabel) {
						pdfForm.removeField(fieldName);
					}
							
				} else {
					System.out.println("Skipping field: " + status + " : " + fieldName + " : " + value);
				}
				
				if(value == null || value.trim().equals("")) {
					pdfForm.removeField(fieldName);
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
				
				System.out.println("adding signature: " + user.signature);
				PushbuttonField ad = pdfForm.getNewPushbuttonFromField("user_signature");
				if(ad != null) {
					ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
					ad.setProportionalIcon(true);
					try {
						ad.setImage(Image.getInstance(basePath + "/" + user.signature));
					} catch (Exception e) {
						log.info("Error: Failed to add signature " + basePath + "/" + user.signature + " to pdf");
					}
					pdfForm.replacePushbuttonField("user_signature", ad.getField());
					System.out.println("Adding image to: signature");
				} else {
					System.out.println("Picture field: user_signature not found");
				}
			}
				
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error filling template", e);
		}
	}
}


