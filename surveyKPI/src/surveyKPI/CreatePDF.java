package surveyKPI;

/*
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

*/

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Row;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.text.BadElementException;
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
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
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

/*
 * Creates a PDF
 * HTML Fragments 
 *   <h3>  - Use for group Labels
 *   .group - group elements
 *   .hint - Hints
 */

@Path("/pdf/{sId}")
public class CreatePDF extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(CreatePDF.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}
	
	public static Font WingDings = null;
	public static Font arial = null;
	private static int GROUP_WIDTH_DEFAULT = 4;
	private static final String DEFAULT_CSS = "/resources/css/default_pdf.css";

	private class Parser {
		XMLParser xmlParser = null;
		ElementList elements = null;
	}
	
	@GET
	@Path("/debug")
	@Produces("application/json")
	public Response getPDFDebug (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@QueryParam("instance") String instanceId,
			@QueryParam("language") String language,
			@QueryParam("filename") String filename) throws Exception {

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Create PDF from survey:" + sId + " for record: " + instanceId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation 
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		
		SurveyManager sm = new SurveyManager();
		Connection cResults = ResultsDataSource.getConnection("createPDF");
		try {
			
			/*
			 * Get the results
			 */
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, instanceId, true);
			// Return data as JSON - Debug only
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			throw new Exception("SQL Error: " + e.getMessage());
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		
		return response;

	}
	
	@GET
	@Produces("application/x-download")
	public void getPDF (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@QueryParam("instance") String instanceId,
			@QueryParam("language") String language,
			@QueryParam("filename") String filename) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Create PDF from survey:" + sId + " for record: " + instanceId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation 
		
		/*
		 * Get parameters
		 */
		if(filename == null || filename.trim().length() == 0) {
			filename = "template";
		}
		String escapedFileName = null;
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Encoding Filename Error", e);
		}
		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes

		
		escapedFileName = escapedFileName + ".pdf";
		
		response.setHeader("Content-Disposition", "attachment; filename=\"" + escapedFileName +"\"");	
		response.setStatus(HttpServletResponse.SC_OK);
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		
		SurveyManager sm = new SurveyManager();
		Connection cResults = ResultsDataSource.getConnection("createPDF");
		try {
			
			// Get fonts and embed them
			String os = System.getProperty("os.name");
			log.info("Operating System:" + os);
			
			if(os.startsWith("Mac")) {
				FontFactory.register("/Library/Fonts/Wingdings.ttf", "wingdings");
				FontFactory.register("/Library/Fonts/Arial Unicode.ttf", "arial");
			} else {
				// Assume on Linux
			}
			WingDings = FontFactory.getFont("wingdings", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			arial = FontFactory.getFont("arial", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 
			
			/*
			 * Get the results
			 */
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, instanceId, true);
			ArrayList<ArrayList<Result>> results = survey.results;	
			 
			/*
			 * Create the PDF
			 */			
			Parser parser = getXMLParser(request);
			
			Document document = new Document();
			PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
			writer.setInitialLeading(12);
			document.open();
	        
			int languageIdx = getLanguageIdx(survey, language);
			for(int i = 0; i < results.size(); i++) {
				processForm(parser, document, results.get(i), survey, basePath, languageIdx);		
			}
			document.close();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			throw new Exception("SQL Error: " + e.getMessage());
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}

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
			int languageIdx) throws DocumentException, IOException {
		
		int groupWidth = 4;
		
		for(int j = 0; j < record.size(); j++) {
			Result r = record.get(j);
			if(r.type.equals("form")) {
				for(int k = 0; k < r.subForm.size(); k++) {
					processForm(parser, document, r.subForm.get(k), survey, basePath, languageIdx);
				} 
			} else if(r.qIdx >= 0) {
				// Process the question
				
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
				Label label = question.labels.get(languageIdx);
			
				if(includeResult(r, question.inMeta)) {
					if(question.type.equals("begin group")) {
						groupWidth = processGroup(parser, document, question, label);
					} else {
						Row row = prepareRow(groupWidth, record, survey, j, languageIdx);
						document.add(processRow(parser, row, basePath));
						j += row.items.size() - 1;	// Jump over multiple questions if more than one was added to the row
					}
				}
				
			}
		}
		
		return;
	}
	
	/*
	 * Make a decision as to whether this result should be included in the PDF
	 */
	private boolean includeResult(Result r, boolean inMeta) {
		boolean include = true;
		
		if(r.name == null) {
			include = false;
		} else if(r.name.startsWith("meta") && r.type.equals("begin group")){
			include = false;
		} else if(inMeta) {
			include = false;
		} else if(r.name.startsWith("meta_group")) {
			include = false;
		} else if(r.name.equals("_task_key")) {
			include = false;
		}
		
		
		return include;
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

				Option option = survey.optionLists.get(r.listName).get(r.cIdx);
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
	 * Add the table row to the document
	 */
	PdfPTable processRow(Parser parser, Row row, String basePath) throws BadElementException, MalformedURLException, IOException {
		PdfPTable table = new PdfPTable(row.groupWidth);
		for(DisplayItem di : row.items) {
			di.debug();
			table.addCell(addDisplayItem(parser, di, basePath));
		}
		return table;
	}
	
	/*
	 * Add the question label, hint, and any media
	 */
	private PdfPCell addDisplayItem(Parser parser, DisplayItem di, String basePath) throws BadElementException, MalformedURLException, IOException {
			
		PdfPCell cell = new PdfPCell();
		 
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
			cell.addElement(element);
		}
		
		
		if(di.type.startsWith("select")) {
			processSelect(cell, di);
		} else if (di.type.equals("image")) {
			if(di.value != null && !di.value.trim().equals("") && !di.value.trim().equals("Unknown")) {
				Image img = Image.getInstance(basePath + "/" + di.value);
				img.scaleToFit(200, 300);
				cell.addElement(img);
			} else {
				// TODO add empty image
			}
			
		} else {
			// Todo process other question types
			cell.addElement(new Paragraph(di.value));
		}
		cell.setColspan(di.width);
		return cell;
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
	
	private void processSelect(PdfPCell cell, DisplayItem di) { 

		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		boolean isSelect = di.type.equals("select") ? true : false;
		for(DisplayItem aChoice : di.choices) {
			ListItem item = new ListItem(aChoice.text);
			
			if(isSelect) {
				if(aChoice.isSet) {
					item.setListSymbol(new Chunk("\376", WingDings)); 
				} else {
					item.setListSymbol(new Chunk("\250", WingDings)); 
				}
			} else {
				if(aChoice.isSet) {
					item.setListSymbol(new Chunk("\244", WingDings)); 
				} else {
					item.setListSymbol(new Chunk("\241", WingDings)); 
				}
			}
			list.add(item);
			//aChoice.debug();
			
		}
		cell.addElement(list);

	}
	
	private Parser getXMLParser(HttpServletRequest request) {
		
		Parser parser = new Parser();
		
        // CSS
		 CSSResolver cssResolver = new StyleAttrCSSResolver();
		 try {
		     CssFile cssFile = XMLWorkerHelper.getCSS( request.getServletContext().getResourceAsStream(DEFAULT_CSS));
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
        
		//styles = new StyleSheet();
		
		//styles.loadTagStyle("body", "face", "arial");
		//styles.loadTagStyle("h3", "font-size", "20px");
		//styles.loadStyle("group", "color", "#ce4f07");	
		//styles.loadTagStyle("body", "encoding", BaseFont.IDENTITY_H);
		
	}
	

}
