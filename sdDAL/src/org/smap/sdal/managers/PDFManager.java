package org.smap.sdal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
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
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
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
	
	public static Font Symbols = null;
	public static Font defaultFont = null;
	private static final String DEFAULT_CSS = "/usr/bin/smap/resources/css/default_pdf.css";
	private static int GROUP_WIDTH_DEFAULT = 4;

	private class Parser {
		XMLParser xmlParser = null;
		ElementList elements = null;
	}

	/*
	 * Call this function to create a PDF
	 * Return a suggested name for the PDF file derived from the results
	 */
	public String createPdf(
			Connection connectionSD,
			Connection cResults,
			OutputStream outputStream,
			String basePath, 
			String remoteUser,
			String language, 
			int sId, 
			String instanceId,
			String filename,
			HttpServletResponse response) {
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		org.smap.sdal.model.Survey survey = null;
		User user = null;
		boolean generateBlank = (instanceId == null) ? true : false;	// If false only show selected options.
		
	
		SurveyManager sm = new SurveyManager();
		UserManager um = new UserManager();

		try {
			
			// Get fonts and embed them
			String os = System.getProperty("os.name");
			log.info("Operating System:" + os);
			
			if(os.startsWith("Mac")) {
				FontFactory.register("/Library/Fonts/fontawesome-webfont.ttf", "Symbols");
				FontFactory.register("/Library/Fonts/Arial Unicode.ttf", "default");
			} else if(os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0) {
				// Linux / Unix
				FontFactory.register("/usr/share/fonts/truetype/fontawesome-webfont.ttf", "Symbols");
				FontFactory.register("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", "default");
			}
			
			Symbols = FontFactory.getFont("Symbols", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			defaultFont = FontFactory.getFont("default", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 
			
			/*
			 * Get the results and details of the user that submitted the survey
			 */
			survey = sm.getById(connectionSD, cResults, remoteUser, sId, true, basePath, instanceId, true);
			log.info("User Ident who submitted the survey: " + survey.instance.user);
			if(survey.instance.user != null) {
				user = um.getByIdent(connectionSD, survey.instance.user);
			}
			
			// If a filename was not specified then get one from the survey data
			// This filename is returned to the calling program so that it can be used as a permanent name for the temporary file created here
			// If the PDF is to be returned in an http response then the header is set now before writing to the output stream
			log.info("Filename passed to createPDF is: " + filename);
			if(filename == null) {
				filename = survey.getInstanceName() + ".pdf";
			} else {
				if(!filename.endsWith(".pdf")) {
					filename += ".pdf";
				}
			}
			
			// If the PDF is to be returned in an http response then set the file name now
			if(response != null) {
				log.info("Setting filename to: " + filename);
				setFilenameInResponse(filename, response);
			}
			
			/*
			 * Get a template for the PDF report if it exists
			 * The template name will be the same as the XLS form name but with an extension of pdf
			 */
			File templateFile = GeneralUtilityMethods.getPdfTemplate(basePath, survey.displayName, survey.p_id);
			
			if(templateFile.exists()) {
				
				log.info("PDF Template Exists");
				String templateName = templateFile.getAbsolutePath();
				
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
				log.info("++++No template exists creating a pdf file programmatically");
				
				/*
				 * Create a PDF without the template
				 */				
				
				// TODO Attempt to get letter head for the survey
				// TODO get letter head for Page 1.
				// TODO get letter head for general pages
					
				Parser parser = getXMLParser();
				
				Document document = new Document();
				PdfWriter writer = PdfWriter.getInstance(document, outputStream);
				writer.setInitialLeading(12);
				document.open();
		        
				int languageIdx = getLanguageIdx(survey, language);
				for(int i = 0; i < survey.instance.results.size(); i++) {
					processForm(parser, document, survey.instance.results.get(i), survey, basePath, 
							languageIdx,
							generateBlank);		
				}
				document.close();
			}
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			
		}
		
		return filename;
	
	}
	
	/*
	 * Add the filename to the response
	 */
	private void setFilenameInResponse(String filename, HttpServletResponse response) {

		String escapedFileName = null;
		
		log.info("Setting filename in response: " + filename);
		if(filename == null) {
			filename = "survey";
		}
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Encoding Filename Error", e);
		}
		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes
		
		response.setHeader("Content-Disposition", "attachment; filename=\"" + escapedFileName +"\"");	
		response.setStatus(HttpServletResponse.SC_OK);	
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
				} else if(r.type.equals("image")) {
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
						log.info("Adding image to: " + fieldName);
					} else {
						//log.info("Picture field: " + fieldName + " not found");
					}
				} else {
					value = r.value;
				}
	
				if(value != null && !value.equals("") && !r.type.equals("image")) {
					status = pdfForm.setField(fieldName, value);			
					log.info("Set field: " + status + " : " + fieldName + " : " + value);
					if(hideLabel) {
						pdfForm.removeField(fieldName);
					}
							
				} else {
					//log.info("Skipping field: " + status + " : " + fieldName + " : " + value);
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
					log.info("Adding image to: signature");
				} else {
					//log.info("Picture field: user_signature not found");
				}
			}
				
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error filling template", e);
		}
	}
	
	/*
	 * Get an XML Parser
	 */
	private Parser getXMLParser() {
		
		Parser parser = new Parser();
		
        // CSS
		 CSSResolver cssResolver = new StyleAttrCSSResolver();
		 try {
			 CssFile cssFile = XMLWorkerHelper.getCSS( new FileInputStream(DEFAULT_CSS));
		     cssResolver.addCss(cssFile);
		 } catch(Exception e) {
			 log.log(Level.SEVERE, "Failed to get CSS file", e);
			 cssResolver = XMLWorkerHelper.getInstance().getDefaultCssResolver(true);
		 }
 
        // HTML
        HtmlPipelineContext htmlContext = new HtmlPipelineContext(null);
        htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
        htmlContext.autoBookmark(false);
 
        // Pipelines
        parser.elements = new ElementList();
        ElementHandlerPipeline end = new ElementHandlerPipeline(parser.elements, null);
        HtmlPipeline html = new HtmlPipeline(htmlContext, end);
        CssResolverPipeline css = new CssResolverPipeline(cssResolver, html);
 
        // XML Worker
        XMLWorker worker = new XMLWorker(css, true);        
        parser.xmlParser = new XMLParser(worker);
        
        return parser;
		
	}
	
	/*
	 * Process the form
	 * Attempt to follow the standard set by enketo for the layout of forms so that the same layout directives
	 *  can be applied to showing the form on the screen and generating the PDF
	 */
	private void processForm(
			Parser parser,
			Document document,  
			ArrayList<Result> record,
			org.smap.sdal.model.Survey survey,
			String basePath,
			int languageIdx,
			boolean generateBlank) throws DocumentException, IOException {
		
		int groupWidth = 4;
		
		for(int j = 0; j < record.size(); j++) {
			Result r = record.get(j);
			if(r.type.equals("form")) {
				for(int k = 0; k < r.subForm.size(); k++) {
					processForm(parser, document, r.subForm.get(k), survey, basePath, languageIdx, generateBlank);
				} 
			} else if(r.qIdx >= 0) {
				// Process the question
				
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
				Label label = question.labels.get(languageIdx);
			
				if(includeResult(r, question)) {
					if(question.type.equals("begin group")) {
						groupWidth = processGroup(parser, document, question, label);
					} else {
						Row row = prepareRow(groupWidth, record, survey, j, languageIdx);
						document.add(processRow(parser, row, basePath, generateBlank));
						j += row.items.size() - 1;	// Jump over multiple questions if more than one was added to the row
					}
				}
				
			}
		}
		
		return;
	}
	
	private int processGroup(
			Parser parser,
			Document document, 
			org.smap.sdal.model.Question question, 
			Label label
			) throws IOException, DocumentException {
		
		
		StringBuffer html = new StringBuffer();
		html.append("<span class='group'><h3>");
		html.append(label.text);
		html.append("</h3></span>");
		
		parser.elements.clear();
		parser.xmlParser.parse(new StringReader(html.toString()));
		for(Element element : parser.elements) {
			document.add(element);
		}
		
		int width = question.getWidth();
		if(width <= 0) {
			width = GROUP_WIDTH_DEFAULT;
		}
		
		return width;
	}
	
	/*
	 * Make a decision as to whether this result should be included in the PDF
	 */
	private boolean includeResult(Result r, org.smap.sdal.model.Question question) {
		
		boolean include = true;
		boolean inMeta = question.inMeta;

		// Don't include the question if it has been marked as not to be included
		if(question.appearance != null && question.appearance.contains("pdfno")) {
			include = false;
		}
		
		if(include) {
			if(r.name == null) {
				include = false;
			} else if(r.name.startsWith("meta") && r.type.equals("begin group")){
				include = false;
			} else if(inMeta) {
				include = false;
			} else if(r.name.startsWith("meta_group")) {
				include = false;
			} else if(r.name.startsWith("_")) {
				// Don't include questions that start with "_",  these are only added to the letter head
				include = false;
			} 
		}
		
		
		return include;
	}
	
	
	/*
	 * Add the table row to the document
	 */
	PdfPTable processRow(Parser parser, Row row, String basePath,
			boolean generateBlank) throws BadElementException, MalformedURLException, IOException {
		PdfPTable table = new PdfPTable(row.groupWidth);
		for(DisplayItem di : row.items) {
			//di.debug();
			ArrayList<PdfPCell> cells = addDisplayItem(parser, di, basePath, generateBlank);
			for(PdfPCell cell : cells) {
				table.addCell(cell);
			}
		}
		return table;
	}
	
	/*
	 * Add a row of questions
	 * Each row is created as a table
	 * converts questions and results to display items
	 * As many display items are added as will fit in the current groupWidth
	 * If the total width of the display items does not add up to the groupWidth then the last item
	 *  will be extended so that the total is equal to the group width
	 */
	private Row prepareRow(
			int groupWidth, 
			ArrayList<Result> record, 
			org.smap.sdal.model.Survey survey, 
			int offest,
			int languageIdx) {
		
		Row row = new Row();
		row.groupWidth = groupWidth;
		
		int totalWidth = 0;
		for(int i = offest; i < record.size(); i++) {
			Result r = record.get(i);
			
			Form form = survey.forms.get(r.fIdx);
			org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
			Label label = question.labels.get(languageIdx);
			
			// Decide whether or not to add the next question to this row
			int qWidth  = question.getWidth();
			if(qWidth == 0) {
				// Adjust zero width questions to have the width of the rest of the row
				qWidth = groupWidth - totalWidth;
			}
			if(qWidth > 0 && (totalWidth == 0 || (qWidth + totalWidth <= groupWidth))) {
				// Include this question
				DisplayItem di = new DisplayItem();
				di.width = qWidth;
				di.text = label.text == null ? "" : label.text;
				di.hint = label.hint ==  null ? "" : label.hint;
				di.type = question.type;
				di.name = question.name;
				di.value = r.value;
				di.choices = convertChoiceListToDisplayItems(
						survey, 
						question,
						r.choices, 
						languageIdx);
				setColors(question.appearance, di);
				row.items.add(di);
				
				totalWidth += qWidth;
			} else {
				// Adjust width of last question added so that the total is the full width of the row
				if(totalWidth < groupWidth) {
					row.items.get(row.items.size() - 1).width += (groupWidth - totalWidth);
				}
				break;
			}
			
			
		}
		return row;
	}
	
	/*
	 * Set the colors for the question
	 */
	void setColors(String appearance, DisplayItem di) {
	
		if(appearance != null) {
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdflabelbg")) {
						di.labelbg = getColor(appValues[i]);
					}
				}
			}
		}
	}
	
	/*
	 * Get the color values for a single appearance value
	 * Format is:  xxxx_0Xrr_0Xgg_0xbb
	 */
	BaseColor getColor(String aValue) {
		
		BaseColor c = null;
		String [] parts = aValue.split("_");
		if(parts.length >= 4) {
			c = new BaseColor(Integer.decode("0x" + parts[1]), 
					Integer.decode("0x" + parts[2]),
					Integer.decode("0x" + parts[3]));
		}
		
		return c;
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
				di.type = "choice";
				di.isSet = r.isSet;
				diList.add(di);
			}
		}
		return diList;
	}
	
	/*
	 * Add the question label, hint, and any media
	 */
	private ArrayList<PdfPCell> addDisplayItem(Parser parser, DisplayItem di, 
			String basePath,
			boolean generateBlank) throws BadElementException, MalformedURLException, IOException {
		
		ArrayList<PdfPCell> cells = new ArrayList <PdfPCell> ();
		PdfPCell labelCell = new PdfPCell();
		PdfPCell valueCell = new PdfPCell();
		 
		// Add label
		StringBuffer html = new StringBuffer();
		html.append("<span class='label'>");
		if(di.text != null && di.text.trim().length() > 0) {
			html.append(di.text);
		} else {
			html.append(di.name);
		}
		html.append("</span>");
		html.append("<span class='hint'>");
		if(di.hint != null) {
			html.append(di.hint);
		html.append("</span>");
		}
		
		
		parser.elements.clear();
		parser.xmlParser.parse(new StringReader(html.toString()));
		for(Element element : parser.elements) {
			labelCell.addElement(element);
		}
		
		
		if(di.type.startsWith("select")) {
			processSelect(valueCell, di, generateBlank);
		} else if (di.type.equals("image")) {
			if(di.value != null && !di.value.trim().equals("") && !di.value.trim().equals("Unknown")) {
				Image img = Image.getInstance(basePath + "/" + di.value);
				img.scaleToFit(200, 300);
				valueCell.addElement(img);
			} else {
				// TODO add empty image
			}
			
		} else {
			// Todo process other question types
			valueCell.addElement(new Paragraph(di.value));
		}
		labelCell.setColspan(di.width / 2);
		if(di.labelbg != null) {
			labelCell.setBackgroundColor(di.labelbg);
		}
		valueCell.setColspan(di.width / 2);
		cells.add(labelCell);
		cells.add(valueCell);
		return cells;
	}
	
	private void processSelect(PdfPCell cell, DisplayItem di,
			boolean generateBlank) { 

		// If generating blank template
		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		// If recording selected values
		StringBuilder sb = new StringBuilder("");
		
		boolean isSelect = di.type.equals("select") ? true : false;
		
		for(DisplayItem aChoice : di.choices) {
			ListItem item = new ListItem(aChoice.text);
			
			if(isSelect) {
				if(aChoice.isSet) {
					if(generateBlank) {
						item.setListSymbol(new Chunk("\uf046", Symbols)); 
						list.add(item);
					} else {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}
				} else {
					if(generateBlank) {
						item.setListSymbol(new Chunk("\uf096", Symbols)); 
						list.add(item);
					}
				}
			} else {
				if(aChoice.isSet) {
					if(generateBlank) {
						item.setListSymbol(new Chunk("\uf111", Symbols)); 
						list.add(item);
					} else {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}
				} else {
					//item.setListSymbol(new Chunk("\241", Symbols)); 
					if(generateBlank) {
						item.setListSymbol(new Chunk("\uf10c", Symbols)); 
						list.add(item);
					}
				}
			}
			
			//aChoice.debug();
			
		}
		if(generateBlank) {
			cell.addElement(list);
		} else {
			cell.addElement(new Paragraph(sb.toString()));
		}

	}
}


