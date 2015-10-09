package org.smap.sdal.managers;

import java.io.ByteArrayOutputStream;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.AcroFields.Item;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
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
	//private static int GROUP_WIDTH_DEFAULT = 4;
	private static int NUMBER_TABLE_COLS = 10;
	private static int NUMBER_QUESTION_COLS = 10;

	private class Parser {
		XMLParser xmlParser = null;
		ElementList elements = null;
	}
	
	private class RepeatTracker {																// Level descended in form hierarchy
		//HashMap<String, Integer> count = new HashMap<String, Integer> ();		// Record number at a location given by depth_length as a string
		int [] cols = {NUMBER_QUESTION_COLS};	// Current Array of columns
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
		int [] repIndexes = new int[20];		// Assume repeats don't go deeper than 20 levels

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
			String userName = survey.instance.user;
			if(userName == null) {
				userName = remoteUser;
			}
			if(userName != null) {
				user = um.getByIdent(connectionSD, userName);
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
				 * Create a PDF without the stationary
				 */				
				String stationaryName = basePath + File.separator + "misc" + File.separator + "StandardPDFReport.pdf";
				int marginLeft = 36;
				int marginRight = 36;
				int marginTop = 200;
				int marginBottom = 80;
				
				ByteArrayOutputStream baos = null;
				ByteArrayOutputStream baos_s = null;
				PdfWriter writer = null;

				File letterFile = new File(stationaryName);
				
				
				/*
				 * If we need to add a letter head then create document in two passes, the second pass adds the letter head
				 * Else just create the document directly in a single pass
				 */
				System.out.println("Creating file for letter head: " + stationaryName);
				
				Parser parser = getXMLParser();
				
				// Step 1 - Create the underlying document as a byte array
				Document document = new Document(PageSize.A4, marginLeft, marginRight, marginTop, marginBottom);
				
				if(letterFile.exists()) {
					baos = new ByteArrayOutputStream();
					baos_s = new ByteArrayOutputStream();
					writer = PdfWriter.getInstance(document, baos);
				} else {
					writer = PdfWriter.getInstance(document, outputStream);
				}
				
				writer.setInitialLeading(12);
				document.open();
				
				int languageIdx = getLanguageIdx(survey, language);
				RepeatTracker repeat = new RepeatTracker();
				
				for(int i = 0; i < survey.instance.results.size(); i++) {
					processForm(parser, document, survey.instance.results.get(i), survey, basePath, 
							languageIdx,
							generateBlank,
							0,
							0,
							repIndexes,
							repeat);		
				}
				
				document.close();
				
				if(letterFile.exists()) {
					
					// Step 2- Populate any form fields in the stationary
					
					PdfReader s_reader = new PdfReader(stationaryName);			// Stationary
					PdfStamper s_stamper = new PdfStamper(s_reader, baos_s);	// Write stationary output to a byte array output stream
					
					// debug - write out field names
					AcroFields pdfForm = s_stamper.getAcroFields();
					Set<String> fields = pdfForm.getFields().keySet();
					for(String key: fields) {
						System.out.println("Field: " + key);
					}
					if(user != null) {
						pdfForm.setField("organisation", user.company_name);
						pdfForm.setField("form_title", survey.displayName);
					}
					
					s_stamper.setFormFlattening(true);
					s_stamper.close();
					s_reader.close();
					
					// Step 3 - Apply the stationary to the underlying data
					
					PdfReader reader = new PdfReader(baos.toByteArray());	// Underlying document
					PdfReader f_reader = new PdfReader(baos_s.toByteArray());	// Filled in stationary
					
					PdfStamper stamper = new PdfStamper(reader, outputStream);
					PdfImportedPage letter1 = stamper.getImportedPage(f_reader, 1);
					int n = reader.getNumberOfPages();
					PdfContentByte background;
					for(int i = 0; i < n; i++ ) {
						background = stamper.getUnderContent(i + 1);
						background.addTemplate(letter1, 0, 0);
					}
					
					
					stamper.close();
					reader.close();
					
				}
				
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
			boolean generateBlank,
			int depth,
			int length,
			int[] repIndexes,
			RepeatTracker repeat) throws DocumentException, IOException {
		
		System.out.println("Process Form: " + depth + " : " + length);
		//int groupWidth = 4;
		
		// Check that the depth of repeats hasn't exceeded the maximum
		if(depth > repIndexes.length - 1) {
			depth = repIndexes.length - 1;	
		}
		
		boolean firstQuestion = true;
		for(int j = 0; j < record.size(); j++) {
			Result r = record.get(j);
			if(r.type.equals("form")) {
				
				firstQuestion = true;			// Make sure there is a gap when we return from the sub form
				// If this is a blank template check to see the number of times we should repeat this sub form
				if(generateBlank) {
					int blankRepeats = getBlankRepeats(r.appearance);
					System.out.println("Generating " + blankRepeats);
					for(int k = 0; k < blankRepeats; k++) {
						repIndexes[depth] = k;
						processForm(parser, document, r.subForm.get(0), survey, basePath, languageIdx, 
								generateBlank, 
								depth + 1,
								k,
								repIndexes,
								repeat);
					}
				} else {
					for(int k = 0; k < r.subForm.size(); k++) {
						repIndexes[depth] = k;
						processForm(parser, document, r.subForm.get(k), survey, basePath, languageIdx, 
								generateBlank, 
								depth + 1,
								k,
								repIndexes,
								repeat);
					} 
				}
			} else if(r.qIdx >= 0) {
				// Process the question
				
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
				//Label label = question.labels.get(languageIdx);
			
				if(includeResult(r, question)) {
					if(question.type.equals("begin group")) {
						//groupWidth = processGroup(parser, document, question, label);
						if(question.isNewPage()) {
							document.newPage();
						}
					} else if(question.type.equals("end group")) {
						//ignore
					} else {
						Row row = prepareRow(record, survey, j, languageIdx, repeat);
						PdfPTable newTable = processRow(parser, row, basePath, generateBlank, depth, repIndexes);
						
						// Add a gap if this is the first question of the record
						// or the previous row was ata different depth
						if(firstQuestion) {
							newTable.setSpacingBefore(5);
						}
						firstQuestion = false;
						
						// Start a new page if the first question needs to be on a new page
						if(row.items.get(0).isNewPage) {
							document.newPage();
						}
						document.add(newTable);
						j += row.items.size() - 1;	// Jump over multiple questions if more than one was added to the row
					}
				}
				
			}
		}
		
		return;
	}
	
	/*
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
			boolean generateBlank,
			int depth,
			int[] repIndexes) throws BadElementException, MalformedURLException, IOException {

		PdfPTable table = new PdfPTable(depth + NUMBER_TABLE_COLS);	// Add a column for each level of repeats so that the repeat number can be shown
		
		// Add the cells to record repeat indexes
		for(int i = 0; i < depth; i++) {
			
			System.out.println("Index: " + i + " : " + repIndexes[i]);
			PdfPCell c = new PdfPCell();
			c.addElement(new Paragraph(String.valueOf(repIndexes[i] + 1)));
			c.setBackgroundColor(BaseColor.LIGHT_GRAY);
			table.addCell(c);
		}
		
		System.out.println("  Number of items: " + row.items.size());
		for(DisplayItem di : row.items) {
			//di.debug();
			PdfPCell cell = new PdfPCell(addDisplayItem(parser, di, basePath, generateBlank));
			cell.setBorderColor(BaseColor.LIGHT_GRAY);
			cell.setColspan(di.width);
			int spaceBefore = row.spaceBefore();
			if(spaceBefore > 0) {
				table.setSpacingBefore(spaceBefore);
			}
			table.addCell(cell);
			
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
			ArrayList<Result> record, 
			org.smap.sdal.model.Survey survey, 
			int offset,
			int languageIdx,
			RepeatTracker repeat) {
		
		System.out.println("Prepare row");
		Row row = new Row();
		row.groupWidth = repeat.cols.length;
		
		for(int i = offset; i < record.size(); i++) {
			Result r = record.get(i);
			
			Form form = survey.forms.get(r.fIdx);
			org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
			Label label = question.labels.get(languageIdx);
			
			boolean isNewPage = question.isNewPage();
			
			if(i == offset) {
				// First question of row - update the number of columns
				int [] updateCols = question.updateCols(repeat.cols);
				if(updateCols != null) {
					repeat.cols = updateCols;			// Can only update the number of columns with the first question of the row
				}
				
				includeQuestion(row.items, repeat.cols, i, label, question, offset, survey, languageIdx, r, isNewPage);
			} else if(i - offset < repeat.cols.length) {
				// 2nd or later questions in the row
				int [] updateCols = question.updateCols(repeat.cols);		// Returns null if the number of columns has not changed
				
				
				if(updateCols == null || isNewPage) {
					includeQuestion(row.items, repeat.cols, i, label, question, offset, survey, languageIdx, r, isNewPage);
				} else {
					// If the question updated the number of columns then we will need to start a new row
					break;
				}
		
			
			} else {
				break;
			}
			
			
		}
		return row;
	}
	
	/*
	 * Include question in the row
	 */
	private void includeQuestion(ArrayList<DisplayItem> items, int [] cols, int colIdx, Label label, 
			org.smap.sdal.model.Question question,
			int offset,
			org.smap.sdal.model.Survey survey,
			int languageIdx,
			Result r,
			boolean isNewPage) {
		
		DisplayItem di = new DisplayItem();
		di.width = cols[colIdx-offset];
		di.text = label.text == null ? "" : label.text;
		di.hint = label.hint ==  null ? "" : label.hint;
		di.type = question.type;
		di.name = question.name;
		di.value = r.value;
		di.isNewPage = isNewPage;
		di.choices = convertChoiceListToDisplayItems(
				survey, 
				question,
				r.choices, 
				languageIdx);
		setQuestionFormats(question.appearance, di);
		items.add(di);
	}
	
	/*
	 * Get the number of blank repeats to generate
	 */
	int getBlankRepeats(String appearance) {
		int repeats = 1;
		
		if(appearance != null) {
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdfrepeat")) {
						String [] parts = appValues[i].split("_");
						if(parts.length >= 2) {
							repeats = Integer.valueOf(parts[1]);
						}
						break;
					}
				}
			}
		}
					
		return repeats;
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
						setColor(appValues[i], di);
					} else if(appValues[i].startsWith("pdflabelw")) {
						setWidths(appValues[i], di);
					} else if(appValues[i].startsWith("pdfheight")) {
						setHeight(appValues[i], di);
					} else if(appValues[i].startsWith("pdfspace")) {
						setSpace(appValues[i], di);
					} 
				}
			}
		}
	}
	
	/*
	 * Get the color values for a single appearance value
	 * Format is:  xxxx_0Xrr_0Xgg_0xbb
	 */
	void setColor(String aValue, DisplayItem di) {
		
		di.labelbg = null;

		String [] parts = aValue.split("_");
		if(parts.length >= 4) {
			di.labelbg = new BaseColor(Integer.decode("0x" + parts[1]), 
					Integer.decode("0x" + parts[2]),
					Integer.decode("0x" + parts[3]));
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
	private PdfPTable addDisplayItem(Parser parser, DisplayItem di, 
			String basePath,
			boolean generateBlank) throws BadElementException, MalformedURLException, IOException {
		
		PdfPCell labelCell = new PdfPCell();
		PdfPCell valueCell = new PdfPCell();
		labelCell.setBorderColor(BaseColor.LIGHT_GRAY);
		valueCell.setBorderColor(BaseColor.LIGHT_GRAY);
		PdfPTable tItem = null;
		 
		// Add label
		StringBuffer html = new StringBuffer();
		html.append("<span class='label'>");
		if(di.text != null && di.text.trim().length() > 0) {
			html.append(GeneralUtilityMethods.unesc(di.text));
		} else {
			html.append(di.name);
		}
		html.append("</span>");
		
		// Only include hints if we are generating a blank template
		if(generateBlank) {
			html.append("<span class='hint'>");
			if(di.hint != null) {
				html.append(GeneralUtilityMethods.unesc(di.hint));
			html.append("</span>");
			}
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
			if(di.value == null || di.value.trim().length() == 0) {
				di.value = " ";	// Need a space to show a blank row
			}
			valueCell.addElement(new Paragraph(GeneralUtilityMethods.unesc(di.value)));
		}
		
		int widthValue = 5;
		if(di.widthLabel == 10) {
			widthValue = 1;	// Label and value in 1 column
			di.widthLabel = 1;
			tItem = new PdfPTable(1); 
		} else {
			// Label and value in 2 columns
			widthValue = 10 - di.widthLabel;
			tItem = new PdfPTable(10);
		}
		// Format label cell
		labelCell.setColspan(di.widthLabel);
		if(di.labelbg != null) {
			labelCell.setBackgroundColor(di.labelbg);
		}
		
		// Format value cell
		valueCell.setColspan(widthValue);
		if(generateBlank && di.valueHeight > -1.0) {
			valueCell.setFixedHeight((float) di.valueHeight);
		}
		
		tItem.addCell(labelCell);
		tItem.addCell(valueCell);
		return tItem;
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
			ListItem item = new ListItem(GeneralUtilityMethods.unesc(aChoice.text));
			
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


