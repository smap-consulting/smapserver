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
public class PDFSurveyManager {
	
	private static Logger log =
			 Logger.getLogger(PDFSurveyManager.class.getName());
	
	public static Font Symbols = null;
	public static Font defaultFont = null;
	public static Font arabicFont = null;
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
	
	int marginLeft = 50;
	int marginRight = 50;
	int marginTop_1 = 130;
	int marginBottom_1 = 100;
	int marginTop_2 = 50;
	int marginBottom_2 = 100;
	
	private class GlobalVariables {																// Level descended in form hierarchy
		//HashMap<String, Integer> count = new HashMap<String, Integer> ();		// Record number at a location given by depth_length as a string
		int [] cols = {NUMBER_QUESTION_COLS};	// Current Array of columns
		boolean hasAppendix = false;
		
		// Map of questions that need to have the results of another question appended to their results in a pdf report
		HashMap <String, ArrayList<String>> addToList = new HashMap <String, ArrayList<String>>();
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
			boolean landscape,					// Set true if landscape
			HttpServletResponse response,
			int utcOffset) {
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		org.smap.sdal.model.Survey survey = null;
		User user = null;
		boolean generateBlank = (instanceId == null) ? true : false;	// If false only show selected options
		
	
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
				FontFactory.register("/Library/Fonts/NotoNaskhArabic-Regular.ttf", "arabic");
			} else if(os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0) {
				// Linux / Unix
				FontFactory.register("/usr/share/fonts/truetype/fontawesome-webfont.ttf", "Symbols");
				FontFactory.register("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", "default");
				FontFactory.register("/usr/share/fonts/truetype/NotoNaskhArabic-Regular.ttf", "arabic");
			}
			
			Symbols = FontFactory.getFont("Symbols", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			defaultFont = FontFactory.getFont("default", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 
			arabicFont = FontFactory.getFont("arabic", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 
			
			/*
			 * Get the results and details of the user that submitted the survey
			 */
			boolean superUser = GeneralUtilityMethods.isSuperUser(connectionSD, remoteUser);
			survey = sm.getById(connectionSD, cResults, remoteUser, sId, true, basePath, 
					instanceId, true, generateBlank, true, false, "real", superUser, utcOffset);
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
				GeneralUtilityMethods.setFilenameInResponse(filename, response);
			}
			
			/*
			 * Get a template for the PDF report if it exists
			 * The template name will be the same as the XLS form name but with an extension of pdf
			 */
			File templateFile = GeneralUtilityMethods.getPdfTemplate(basePath, survey.displayName, survey.p_id);
			
			/*
			 * Get dependencies between Display Items, for example if a question result should be added to another
			 *  question's results
			 */
			GlobalVariables gv = new GlobalVariables();
			if(!generateBlank) {
				for(int i = 0; i < survey.instance.results.size(); i++) {
					getDependencies(gv, survey.instance.results.get(i), survey, i);	
				}
			}
			
			
			if(templateFile.exists()) {
				
				log.info("PDF Template Exists");
				String templateName = templateFile.getAbsolutePath();
				
				PdfReader reader = new PdfReader(templateName);
				PdfStamper stamper = new PdfStamper(reader, outputStream);
				int languageIdx = GeneralUtilityMethods.getLanguageIdx(survey, language);
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
				
				PdfWriter writer = null;
				
				/*
				 * If we need to add a letter head then create document in two passes, the second pass adds the letter head
				 * Else just create the document directly in a single pass
				 */
				Parser parser = getXMLParser();
				
				// Step 1 - Create the underlying document as a byte array
				Document document = null;
				if(landscape) {
					document = new Document(PageSize.A4.rotate());
				} else {
					document = new Document(PageSize.A4);
				}
				document.setMargins(marginLeft, marginRight, marginTop_1, marginBottom_1);
				writer = PdfWriter.getInstance(document, outputStream);
				
				writer.setInitialLeading(12);	
				
				writer.setPageEvent(new PdfPageSizer(survey.displayName, survey.pName, 
						user, basePath, null,
						marginLeft, marginRight, marginTop_2, marginBottom_2)); 
				document.open();
				
				int languageIdx = GeneralUtilityMethods.getLanguageIdx(survey, language);
				
				// If this form has data maintain a list of parent records to lookup ${values}
				ArrayList<ArrayList<Result>> parentRecords = null;
				if(!generateBlank) {
					parentRecords = new ArrayList<ArrayList<Result>> ();
				}
				
				for(int i = 0; i < survey.instance.results.size(); i++) {
					processForm(parser, document, survey.instance.results.get(i), survey, basePath, 
							languageIdx,
							generateBlank,
							0,
							i,
							repIndexes,
							gv,
							false,
							parentRecords);		
				}
				
				fillNonTemplateUserDetails(document, user, basePath);
				
				// Add appendix
				if(gv.hasAppendix) {
					document.newPage();
					document.add(new Paragraph("Appendix", fontbold));
					
					for(int i = 0; i < survey.instance.results.size(); i++) {
						processForm(parser, document, survey.instance.results.get(i), survey, basePath, 
								languageIdx,
								generateBlank,
								0,
								i,
								repIndexes,
								gv,
								true, 
								parentRecords);		
					}
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
			
			for(Result r : record) {
				
				String value = "";
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
				} else if(r.value != null && r.type.equals("image")) {
					value = r.value;
					PdfUtilities.addImageTemplate(pdfForm, fieldName, basePath, value);
					
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
						String filename = basePath + "/media/users/" + user.id + "/sig/"  + user.signature;
						ad.setImage(Image.getInstance(filename));
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
 

 
        // Pipelines
        parser.elements = new ElementList();
        ElementHandlerPipeline end = new ElementHandlerPipeline(parser.elements, null);
 
        String os = System.getProperty("os.name");
		log.info("Operating System:" + os);
		
		
        XMLWorkerFontProvider fontProvider = new XMLWorkerFontProvider();
		if(os.startsWith("Mac")) {
	        fontProvider.register("/Library/Fonts/NotoNaskhArabic-Regular.ttf", BaseFont.IDENTITY_H);
	        FontFactory.register("/Library/Fonts/Arial Unicode.ttf", BaseFont.IDENTITY_H);
	        fontProvider.register("/Library/Fonts/NotoSans-Regular.ttf", BaseFont.IDENTITY_H);
		} else if(os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0) {
			// Linux / Unix
			fontProvider.register("/usr/share/fonts/truetype/NotoNaskhArabic-Regular.ttf", BaseFont.IDENTITY_H);
			FontFactory.register("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", BaseFont.IDENTITY_H);
		    fontProvider.register("/usr/share/fonts/truetype/NotoSans-Regular.ttf", BaseFont.IDENTITY_H);
		}
 
		/*
        System.out.println("Fonts present in " + fontProvider.getClass().getName());
        Set<String> registeredFonts = fontProvider.getRegisteredFonts();
        for (String font : registeredFonts)
            System.out.println(font);
 		*/
        
        CssAppliers cssAppliers = new CssAppliersImpl(fontProvider);
        
        // HTML
        HtmlPipelineContext htmlContext = new HtmlPipelineContext(cssAppliers);
        htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
        htmlContext.autoBookmark(false);
        HtmlPipeline html = new HtmlPipeline(htmlContext, end);
        CssResolverPipeline css = new CssResolverPipeline(cssResolver, html);
        
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
			GlobalVariables gv,
			boolean appendix,
			ArrayList<ArrayList<Result>> parentRecords) throws DocumentException, IOException {
		
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
					for(int k = 0; k < blankRepeats; k++) {
						repIndexes[depth] = k;
						processForm(parser, document, r.subForm.get(0), survey, basePath, languageIdx, 
								generateBlank, 
								depth + 1,
								k,
								repIndexes,
								gv,
								appendix,
								null);
					}
				} else {
					for(int k = 0; k < r.subForm.size(); k++) {
						// Maintain array list of parent records in order to look up ${values}
						parentRecords.add(0, record);		// Push this record in at the beginning of the list as we want to search most recent first
						repIndexes[depth] = k;
						processForm(parser, document, r.subForm.get(k), survey, basePath, languageIdx, 
								generateBlank, 
								depth + 1,
								k,
								repIndexes,
								gv,
								appendix,
								parentRecords);
					} 
				}
			} else if(r.qIdx >= 0) {
				// Process the question
				
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
				//Label label = question.labels.get(languageIdx);
			
				if(includeResult(r, question, appendix, gv)) {
					if(question.type.equals("begin group")) {
						//groupWidth = processGroup(parser, document, question, label);
						if(question.isNewPage()) {
							document.newPage();
						}
					} else if(question.type.equals("end group")) {
						//ignore
					} else {
						Row row = prepareRow(record, survey, j, languageIdx, gv, length, appendix, parentRecords);
						PdfPTable newTable = processRow(parser, row, basePath, generateBlank, depth, repIndexes, gv);
						
						newTable.setWidthPercentage(100);

				        
						// Add a gap if this is the first question of the record
						// or the previous row was at a different depth
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
	private boolean includeResult(Result r, org.smap.sdal.model.Question question, 
			boolean appendix,
			GlobalVariables gv) {
		
		boolean include = true;
		boolean inMeta = question.inMeta;

		// Don't include the question if it has been marked as not to be included
		if(question.appearance != null) {
			if(question.appearance.contains("pdfno")) {
				include = false;
			} else {
				boolean appendixQuestion = question.appearance.contains("pdfapp");
				if(appendixQuestion) {
					gv.hasAppendix = true;
				}
				if(appendix && !appendixQuestion || (!appendix && appendixQuestion) ) {
					include = false;
				}
			}
		} else {
			// Questions without appearance should not appear in the appendix
			if(appendix) {
				include = false;
			}
		}
		
		// Check appendix status
		
		
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
			int[] repIndexes,
			GlobalVariables gv) throws BadElementException, MalformedURLException, IOException {

		PdfPTable table = new PdfPTable(depth + NUMBER_TABLE_COLS);	// Add a column for each level of repeats so that the repeat number can be shown
		
		// Add the cells to record repeat indexes
		for(int i = 0; i < depth; i++) {
			PdfPCell c = new PdfPCell();
			c.addElement(new Paragraph(String.valueOf(repIndexes[i] + 1), font));
			c.setBackgroundColor(BaseColor.LIGHT_GRAY);
			table.addCell(c);

			
		}
		
		int spanCount = NUMBER_TABLE_COLS;
		int numberItems = row.items.size();
		for(DisplayItem di : row.items) {
			//di.debug();
			PdfPCell cell = new PdfPCell();
			cell.addElement(addDisplayItem(parser, di, basePath, generateBlank, gv));
			//PdfPCell cell = new PdfPCell(addDisplayItem(parser, di, basePath, generateBlank, gv));
			cell.setBorderColor(BaseColor.LIGHT_GRAY);
			
			// Make sure the last cell extends to the end of the table
			if(numberItems == 1) {
				di.width = spanCount;
			}
			cell.setColspan(di.width);
			int spaceBefore = row.spaceBefore();
			if(spaceBefore > 0) {
				table.setSpacingBefore(spaceBefore);
			}
			table.addCell(cell);
			
			numberItems--;
			spanCount -= di.width;
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
			GlobalVariables gv,
			int recNumber,
			boolean appendix,
			ArrayList<ArrayList<Result>> parentRecords) {
		
		Row row = new Row();
		row.groupWidth = gv.cols.length;
		
		for(int i = offset; i < record.size(); i++) {
			Result r = record.get(i);
			
			Form form = survey.forms.get(r.fIdx);
			org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
			Label label = question.labels.get(languageIdx);
			
			boolean isNewPage = question.isNewPage();
			
			if(i == offset) {
				// First question of row - update the number of columns
				int [] updateCols = question.updateCols(gv.cols);
				if(updateCols != null) {
					gv.cols = updateCols;			// Can only update the number of columns with the first question of the row
				}
				
				includeQuestion(row.items, gv, i, label, question, offset, survey, languageIdx, r, isNewPage, 
						recNumber,
						record,
						parentRecords);
			} else if(i - offset < gv.cols.length) {
				// 2nd or later questions in the row
				int [] updateCols = question.updateCols(gv.cols);		// Returns null if the number of columns has not changed
				
				
				if(updateCols == null || isNewPage) {
					if(includeResult(r, question, appendix, gv)) {
						includeQuestion(row.items, 
								gv, 
								i, 
								label, 
								question, 
								offset, 
								survey, 
								languageIdx, 
								r, 
								isNewPage, 
								recNumber,
								record,
								parentRecords);
					}
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
	private void includeQuestion(ArrayList<DisplayItem> items, GlobalVariables gv, int colIdx, Label label, 
			org.smap.sdal.model.Question question,
			int offset,
			org.smap.sdal.model.Survey survey,
			int languageIdx,
			Result r,
			boolean isNewPage,
			int recNumber,
			ArrayList<Result> record,
			ArrayList<ArrayList<Result>> parentRecords) {
		
		int [] cols = gv.cols;
		DisplayItem di = new DisplayItem();
		di.width = cols[colIdx-offset];
		di.text = label.text == null ? "" : label.text;
		di.text = lookupReferenceValue(di.text, record, parentRecords);
		
		di.hint = label.hint ==  null ? "" : label.hint;
		di.hint = lookupReferenceValue(di.hint, record, parentRecords);
		
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
		di.fIdx = r.fIdx;
		di.rec_number = recNumber;
		items.add(di);
	}
	
	/*
	 * Where a label incudes a reference value such as ${name} then these need to be converted to the actual value
	 */
	private String lookupReferenceValue(String input, ArrayList<Result> record,ArrayList<ArrayList<Result>> parentRecords) {
		
		StringBuffer newValue = new StringBuffer("");
		String v;
		
		// Return if we are generating a blank template
		if(parentRecords == null) {
			return input;
		}
		
		Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {
			
			String matched = matcher.group();
			String qname = matched.substring(2, matched.length() - 1);
			
			// Add any text before the match
			int startOfGroup = matcher.start();
			newValue.append(input.substring(start, startOfGroup));
			
			// Add the matched value after lookup
			// First check in the current record
			v = lookupInRecord(qname, record);
			
			// If not found try each of the parent records starting from the closest
			if(v == null) {
				for(ArrayList<Result> p : parentRecords) {
					v = lookupInRecord(qname, p);
					if(v != null) {
						break;
					}
				}
			}
			
			// Still null!  well maybe this ${..} pattern was just meant to be
			if(v == null) {
				v = matcher.group();
			}
			newValue.append(v);
			
			// Reset the start
			start = matcher.end();

		}
		
		// Get the remainder of the string
		if(start < input.length()) {
			newValue.append(input.substring(start));		
		}
		
		return newValue.toString();
	}
	
	/*
	 * Lookup the value of a question in a record
	 */
	private String lookupInRecord(String name, ArrayList<Result> record) {
		String value = null;
		
		for(Result r : record) {
			if(r.name.equals(name)) {
				if(r.type.startsWith("select")) {
					value = "";
					for(Result rc : r.choices) {
						if(rc.isSet) {
							if(value.length() > 0) {
								value += ",";
							}
							value += rc.name;
						}
					}
				} else {
					value = r.value;
				}
				break;
			}
		}
		
		return value;
		
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
	 * Add the question label, hint, and any media
	 */
	private PdfPTable addDisplayItem(Parser parser, DisplayItem di, 
			String basePath,
			boolean generateBlank,
			GlobalVariables gv) throws BadElementException, MalformedURLException, IOException {
		
		PdfPCell labelCell = new PdfPCell();
		PdfPCell valueCell = new PdfPCell();
		labelCell.setBorderColor(BaseColor.LIGHT_GRAY);
		valueCell.setBorderColor(BaseColor.LIGHT_GRAY);
		
		PdfPTable tItem = null;
		 
		// Add label
		StringBuffer html = new StringBuffer();
		html.append("<span class='label");
		if(di.labelbold) {
			html.append(" lbold");
		}
		html.append("'>");
		
		String textValue = "";
		if(di.text != null && di.text.trim().length() > 0) {
			textValue = di.text;
		} else {
			textValue = di.name;
		}
		textValue = textValue.trim();
		if(textValue.charAt(textValue.length() - 1) != ':') {
			textValue += ":";
		}
		
		if(di.labelcaps) {
			textValue = textValue.toUpperCase();
		}
		html.append(GeneralUtilityMethods.unesc(textValue));
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
			if(textValue != null && textValue.length() > 0) {
				if(GeneralUtilityMethods.isRtlLanguage(textValue)) {
					labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
				}
			} else if(di.hint != null && di.hint.length() > 0) {
				if(GeneralUtilityMethods.isRtlLanguage(textValue)) {
					labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
				}
			}
			labelCell.addElement(element);
		}
		
		// Set the content of the value cell
		try {
			updateValueCell(valueCell, di, generateBlank, basePath, gv);
		} catch (Exception e) {
			log.info("Error updating value cell, continuing: " + basePath + " : " + di.value);
			log.log(Level.SEVERE, "Exception", e);
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
		if(di.valueHeight > -1.0) {
			valueCell.setMinimumHeight((float)di.valueHeight);
		}
		if(di.valuebg != null) {
			valueCell.setBackgroundColor(di.valuebg);
		}
		
		tItem.addCell(labelCell);
		tItem.addCell(valueCell);
		return tItem;
	}
	
	/*
	 * Set the contents of the value cell
	 */
	private void updateValueCell(PdfPCell valueCell, DisplayItem di, boolean generateBlank, 
			String basePath,
			GlobalVariables gv
			) throws BadElementException, MalformedURLException, IOException {
		
		// Questions that append their values to this question
		ArrayList<String> deps = gv.addToList.get(di.fIdx + "_" + di.rec_number + "_" + di.name);
		
		if(di.type.startsWith("select")) {
			processSelect(valueCell, di, generateBlank, gv);
		} else if (di.type.equals("image")) {
			if(di.value != null && !di.value.trim().equals("") && !di.value.trim().equals("Unknown")) {
				try {
					Image img = Image.getInstance(basePath + "/" + di.value);
					valueCell.addElement(img);
				} catch(Exception e) {
					log.info("Error: image " + basePath + "/" + di.value + " not added: " + e.getMessage());
					log.log(Level.SEVERE, "Adding image to pdf", e);
				}

			} else {
				// TODO add empty image
			}
			
		} else {
			// Todo process other question types
			if(di.value == null || di.value.trim().length() == 0) {
				di.value = " ";	// Need a space to show a blank row
			} else {
				if(di.value != null && di.value.length() > 0) {
					if(GeneralUtilityMethods.isRtlLanguage(di.value)) {
						valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
					}
				}
			}
			valueCell.addElement(getPara(di.value, di, gv, deps));

		}
		

	}
	
	private Paragraph getPara(String value, DisplayItem di, GlobalVariables gv, ArrayList<String> deps) {
		
		boolean hasContent = false;
		Font f = null;
		boolean isRtl = false;
		
		Paragraph para = new Paragraph("", font);

		if(value != null && value.trim().length() > 0) {
			if(GeneralUtilityMethods.isRtlLanguage(value)) {
				f = arabicFont;
				isRtl = true;
				
			} else {
				f= font;
			}
			para.add(new Chunk(GeneralUtilityMethods.unesc(value), f));
			hasContent = true;
		}
		
		// Add dependencies

		if(deps != null) {
			for(String n : deps) {
				if(n != null && n.trim().length() > 0) {
					if(hasContent) {
						para.add(new Chunk(",", font));
					}
					
					if(GeneralUtilityMethods.isRtlLanguage(n)) {
						f = arabicFont;
						isRtl = true;
						
					} else {
						f= font;
					}
					para.add(new Chunk(n, f));
				}
				
			}
		}
		return para;
	}
	
	private void processSelect(PdfPCell cell, DisplayItem di,
			boolean generateBlank,
			GlobalVariables gv) {

		Font f = null;
		boolean isRtl = false;
		
		// If generating blank template
		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		String stringValue = null;
		
		boolean isSelectMultiple = di.type.equals("select") ? true : false;
		
		// Questions that append their values to this question
		ArrayList<String> deps = gv.addToList.get(di.fIdx + "_" + di.rec_number + "_" + di.name);
		
		/*
		 * Add the value of this question unless
		 *   The form is not blank and the value is "other" and their are 1 or more dependent questions
		 *   In this case we assume that its only the values of the dependent questions that are needed
		 */
		if(generateBlank) {
			for(DisplayItem aChoice : di.choices) {
				if(GeneralUtilityMethods.isRtlLanguage(aChoice.text)) {
					f = arabicFont;
					isRtl = true;
					
				} else {
					f= font;
				}
				ListItem item = new ListItem(GeneralUtilityMethods.unesc(aChoice.text), f);
			
				if(isSelectMultiple) {
					if(aChoice.isSet) {
						item.setListSymbol(new Chunk("\uf046", Symbols)); 
						list.add(item);	
					} else {
					
						item.setListSymbol(new Chunk("\uf096", Symbols)); 
						list.add(item);
					}
				
				} else {
					if(aChoice.isSet) {
						item.setListSymbol(new Chunk("\uf111", Symbols)); 
						list.add(item);

					} else {
						//item.setListSymbol(new Chunk("\241", Symbols)); 
						item.setListSymbol(new Chunk("\uf10c", Symbols)); 
						list.add(item);
					}
				}
			}
			
			if(isRtl) {
				cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
			}
			cell.addElement(list);
			
		} else {
			stringValue = getSelectValue(isSelectMultiple, di, deps);
			if(GeneralUtilityMethods.isRtlLanguage(stringValue)) {
				cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
			}
			cell.addElement(getPara(stringValue, di, gv, deps));
		}

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
				//fileName = basePath + user.signature;
				
				fileName = basePath + "/media/users/" + user.id + "/sig/"  + user.signature;
				
				Image img = Image.getInstance(fileName);
				img.scaleToFit(200, 50);
				img.setIndentationLeft(indent);
					
				document.add(img);
					
			} catch (Exception e) {
				log.info("Error: Failed to add signature (non template) " + fileName + " to pdf: " + e.getMessage());
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
	 * Format a single value into a paragraph
	 */
	private void addValue(Document document, String value, float indent) throws DocumentException {
		
		Font f = null;
		
		if(value != null && value.trim().length() > 0) {
			if(GeneralUtilityMethods.isRtlLanguage(value)) {
				f = arabicFont;		
			} else {
				f= font;
			}
			Paragraph para = new Paragraph("", f);	
			para.setIndentationLeft(indent);
			para.add(new Chunk(GeneralUtilityMethods.unesc(value), f));
			document.add(para);
		}
	}
}


