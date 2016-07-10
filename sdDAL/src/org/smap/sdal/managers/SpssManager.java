package org.smap.sdal.managers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
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
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Row;
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
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PushbuttonField;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.css.CssFile;
import com.itextpdf.tool.xml.css.StyleAttrCSSResolver;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.ElementHandlerPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;
import com.sun.org.apache.xerces.internal.impl.xs.identity.Selector.Matcher;

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
	
	public static Font Symbols = null;
	public static Font defaultFont = null;
	private static final String DEFAULT_CSS = "/smap/bin/resources/css/default_pdf.css";
	//private static int GROUP_WIDTH_DEFAULT = 4;
	private static int NUMBER_TABLE_COLS = 10;
	private static int NUMBER_QUESTION_COLS = 10;
	
	Font font = new Font(FontFamily.HELVETICA, 10);
    Font fontbold = new Font(FontFamily.HELVETICA, 10, Font.BOLD);

	private class Parser {
		XMLParser xmlParser = null;
		ElementList elements = null;
	}

	/*
	 * Call this function to create a PDF
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
		SurveyManager sm = new SurveyManager();
		StringBuffer sps = new StringBuffer();
		
		try {
			

		
			/*
			 * Get the results and details of the user that submitted the survey
			 */
			survey = sm.getById(connectionSD, null, remoteUser, sId, true, null, null, false, false, true, false, "real");
			int languageIdx = getLanguageIdx(survey, language);
			
			/*
			 * Add the variable labels
			 */
			sps.append("VARIABLE LABELS\n");
			for(int i = 0; i < survey.forms.size(); i++) {
				Form f = survey.forms.get(i);
				
				for(int j = 0; j < f.questions.size(); j++) {
					Question q = f.questions.get(j);
					System.out.println(" " + q.name + " : " + q.type);
					String label = q.labels.get(languageIdx).text;
					if(label != null && !q.type.equals("end group")) {
						if(q.type.equals("select")) {
							addSelectVariables(sps, q, label, languageIdx, survey.optionLists);
						} else {
							sps.append(" ");
							sps.append(q.columnName);
							addSpaces(sps, 10 - q.columnName.length());
							sps.append("'");
							sps.append(spssVariable(label));
							sps.append("'\n");
						}
					}
				}
			}	
			sps.append(".");
			
			// Add the value labels
			sps.append("\n");
			sps.append("VALUE LABELS\n");
			for(int i = 0; i < survey.forms.size(); i++) {
				Form f = survey.forms.get(i);
				
				for(int j = 0; j < f.questions.size(); j++) {
					Question q = f.questions.get(j);
						
					if(q.type.equals("select1")) {
						sps.append(" ");
						sps.append(q.columnName);
						sps.append("\n");
						
						addSelect1Values(sps, q, languageIdx, survey.optionLists);
						
					} else if(q.type.equals("select")) {
						
						
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
		
		for(Option o : options) {
			String optionName = q.columnName + "__" + o.columnName;
			String label = o.labels.get(languageIdx).text;
			if(label != null) {
				sps.append(" ");
				sps.append(optionName);
				addSpaces(sps, 10 - optionName.length());
				sps.append("'");
				sps.append(spssVariable(qLabel));
				sps.append(" - ");
				sps.append(label);
				sps.append("'\n");
			}
		}
		
	}
	
	private void addSelect1Values(StringBuffer sps, Question q, int languageIdx, HashMap<String, OptionList> lists) {
		
		ArrayList<Option> options = lists.get(q.list_name).options;
		
		for(int i = 0; i < options.size(); i++) {
			Option o = options.get(i);
			String optionName = o.columnName;
			String label = o.labels.get(languageIdx).text;
			if(label != null) {
				sps.append("     ");
				sps.append(optionName);
				addSpaces(sps, 10 - optionName.length());
				sps.append("'");
				sps.append(spssVariable(label));
				sps.append("'");
				if(i == options.size() - 1) {
					sps.append("  /");
				}
				sps.append("\n");
			}
		}
		
	}
	
private void addSelectValues(StringBuffer sps, Question q, int languageIdx, HashMap<String, OptionList> lists) {
		
	ArrayList<Option> options = lists.get(q.list_name).options;
		
		for(Option o : options) {
			String optionName = q.columnName + "__" + o.columnName;
			
			sps.append(" ");
			sps.append(optionName);
			sps.append("\n");
			
			sps.append("     1         'yes'\n");
			sps.append("     0         'no'  /\n");
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
		
		return out;
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
	
	

	

	
	private class UserSettings {
		String title;
		String license;
	}
	


	

	


	/*
	 * Set the attributes for this question from keys set in the appearance column
	 */
	void setQuestionFormats(String appearance, DisplayItem di) {
	
		if(appearance != null) {
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdflabelbg")) {
						setColor(appValues[i], di, true);
					} else if(appValues[i].startsWith("pdfvaluebg")) {
						setColor(appValues[i], di, false);
					} else if(appValues[i].startsWith("pdflabelw")) {
						setWidths(appValues[i], di);
					} else if(appValues[i].startsWith("pdfheight")) {
						setHeight(appValues[i], di);
					} else if(appValues[i].startsWith("pdfspace")) {
						setSpace(appValues[i], di);
					} else if(appValues[i].equals("pdflabelcaps")) {
						di.labelcaps = true;
					} else if(appValues[i].equals("pdflabelbold")) {
						di.labelbold = true;
					}
				}
			}
		}
	}
	
	/*
	 * Get the color values for a single appearance value
	 * Format is:  xxxx_0Xrr_0Xgg_0xbb
	 */
	void setColor(String aValue, DisplayItem di, boolean isLabel) {
		
		di.labelbg = null;
		BaseColor color = null;
		
		String [] parts = aValue.split("_");
		if(parts.length >= 4) {
			if(parts[1].startsWith("0x")) {
				color = new BaseColor(Integer.decode(parts[1]), 
						Integer.decode(parts[2]),
						Integer.decode(parts[3]));
			} else {
				color = new BaseColor(Integer.decode("0x" + parts[1]), 
					Integer.decode("0x" + parts[2]),
					Integer.decode("0x" + parts[3]));
			}
		}
		
		if(isLabel) {
			di.labelbg = color;
		} else {
			di.valuebg = color;
		}

	}
	
	/*
	 * Set the widths of the label and the value
	 * Appearance is:  pdflabelw_## where ## is a number from 0 to 10
	 */
	void setWidths(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.widthLabel = Integer.valueOf(parts[1]);   		
		}
		
		// Do bounds checking
		if(di.widthLabel < 0 || di.widthLabel > 10) {
			di.widthLabel = 5;		
		}
		
	}
	
	/*
	 * Set the height of the value
	 * Appearance is:  pdfheight_## where ## is the height
	 */
	void setHeight(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.valueHeight = Double.valueOf(parts[1]);   		
		}
		
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
			ListItem item = new ListItem(GeneralUtilityMethods.unesc(aChoice.text));
			
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
	
	/*
	 * Fill in user details for the output when their is no template
	 */
	private void fillNonTemplateUserDetails(Document document, User user, String basePath) throws IOException, DocumentException {
		
		String settings = user.settings;
		Type type = new TypeToken<UserSettings>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		UserSettings us = gson.fromJson(settings, type);
		
		float indent = (float) 20.0;
		addValue(document, "Completed by:", (float) 0.0);
		if(user.signature != null && user.signature.trim().length() > 0) {
			String fileName = null;
			try {
				fileName = basePath + File.separator + user.signature;

					Image img = Image.getInstance(fileName);
					img.scaleToFit(200, 50);
					img.setIndentationLeft(indent);
					
				    document.add(img);
					
			} catch (Exception e) {
				log.info("Error: Failed to add image " + fileName + " to pdf");
			}
		}
		addValue(document, user.name, indent);
		addValue(document, user.company_name, indent);
		if(us != null) {
			addValue(document, us.title, indent);
			addValue(document, us.license, indent);
		}

	}
	
	/*
	 * Format a key value pair into a paragraph
	 */
	private void addKeyValuePair(Document document, String key, String value) throws DocumentException {
		Paragraph para = new Paragraph("", font);
		
		para.add(new Chunk(GeneralUtilityMethods.unesc(key), fontbold));
		para.add(new Chunk(GeneralUtilityMethods.unesc(value), font));
		
		document.add(para);
	}
	
	/*
	 * Format a single value into a paragraph
	 */
	private void addValue(Document document, String value, float indent) throws DocumentException {
		
		if(value != null && value.trim().length() > 0) {
			Paragraph para = new Paragraph("", font);	
			para.setIndentationLeft(indent);
			para.add(new Chunk(GeneralUtilityMethods.unesc(value), font));
			document.add(para);
		}
	}
}


