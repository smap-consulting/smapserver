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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

import org.apache.commons.lang3.StringEscapeUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
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
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.ZapfDingbatsList;
import com.itextpdf.text.html.simpleparser.HTMLWorker;
import com.itextpdf.text.html.simpleparser.StyleSheet;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.GrayColor;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RadioCheckField;

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
	private StyleSheet styles = null;
	private static int GROUP_WIDTH_DEFAULT = 4;

	
	@GET
	//@Produces("application/x-download")
	@Produces("application/json")
	public Response getPDF (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@QueryParam("instance") String instanceId,
			@QueryParam("filename") String filename) {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		log.info("Create PDF from survey:" + sId + " for record: " + instanceId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		
		Response response = null;
		SurveyManager sm = new SurveyManager();
		Connection cResults = ResultsDataSource.getConnection("createPDF");
		try {
			
			// Get fonts and embed them
			String os = System.getProperty("os.name");
			log.info("Operating System:" + os);
			
			if(os.startsWith("Mac")) {
				FontFactory.register("/Library/Fonts/Wingdings.ttf", "wingdings");
			} else {
				// Assume on Linux
			}
			WingDings = FontFactory.getFont("wingdings", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			
			/*
			 * Get the results
			 */
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, instanceId, true);
			ArrayList<ArrayList<Result>> results = survey.results;	
			
			/*
			 * Create the PDF
			 */
			if(filename != null) {
				
				createStyles();
				
				Document document = new Document();
				PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filename));
				writer.setInitialLeading(16);
				document.open();
		        
				for(int i = 0; i < results.size(); i++) {
					processForm(document, results.get(i), survey, basePath);		
				}
				document.close();
			}
			
			// Return data as JSON - Debug only
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
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
	
	/*
	 * Process the form
	 * Attempt to follow the standard set by enketo for the layout of forms so that the same layout directives
	 *  can be applied to showing the form on the screen and generating the PDF
	 */
	private void processForm(
			Document document,  
			ArrayList<Result> record,
			org.smap.sdal.model.Survey survey,
			String basePath) throws DocumentException, IOException {
		
		int groupWidth = 4;
		
		for(int j = 0; j < record.size(); j++) {
			Result r = record.get(j);
			if(r.resultsType.equals("form")) {
				for(int k = 0; k < r.subForm.size(); k++) {
					processForm(document, r.subForm.get(k), survey, basePath);
				} 
			} else if(r.qIdx >= 0) {
				// Process the question
				int languageIdx = 0;		// TODO get language in parameters
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question question = form.questions.get(r.qIdx);
				Label label = question.labels.get(languageIdx);
			
				if(question.type.equals("begin group")) {
					groupWidth = processGroup(document, question, label);
					System.out.println("######### group width: " + groupWidth);
				} else {
					Row row = prepareRow(groupWidth, record, survey, j, languageIdx);
					document.add(processRow(row, basePath));
					j += row.items.size() - 1;	// Jump over multiple questions if more than one was added to the row
					//addQuestion(document, survey, question, label, r, basePath);
				}
				
			}
		}
		
		return;
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
			System.out.println("   +++ " + label.text + " width: " + qWidth);
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
				di.choices = r.choices;
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
	 * Add the table row to the document
	 */
	PdfPTable processRow(Row row, String basePath) throws BadElementException, MalformedURLException, IOException {
		PdfPTable table = new PdfPTable(row.groupWidth);
		System.out.println("++++ New Row width: " + row.groupWidth);
		for(DisplayItem di : row.items) {
			di.debug();
			table.addCell(addDisplayItem(di, basePath));
		}
		return table;
	}
	
	/*
	 * Add the question label, hint, and any media
	 */
	private PdfPCell addDisplayItem(DisplayItem di, String basePath) throws BadElementException, MalformedURLException, IOException {
			
		PdfPCell cell = new PdfPCell();
		
		// Add label
		StringBuffer html = new StringBuffer();
		html.append("<span class='label'>");
		html.append(di.text);
		html.append("</span>");
		html.append("<span class='hint'>");
		html.append(di.hint);
		html.append("</span>");
		ArrayList<Element> objects = 
				(ArrayList<Element>) HTMLWorker.parseToList(new StringReader(html.toString()), styles, null);
		for(Element element : objects) {
			cell.addElement(element);
		}
		
		
		if(di.type.startsWith("select")) {
			cell = processSelect(di);
		} else if (di.type.equals("image")) {
			if(di.value != null && !di.value.equals("")) {
				Image img = Image.getInstance(basePath + "/" + di.value);
				img.scaleToFit(200, 300);
				cell.addElement(img);
			} else {
				// TODO add empty image
			}
			
		} else {
			// Todo process other question types
			
		}
		cell.setColspan(di.width);
		return cell;
	}
	
	/*
	 * Add the question label, hint, and any media
	 *
	private void addQuestion(
			Document document, 
			org.smap.sdal.model.Survey survey,
			org.smap.sdal.model.Question question,
			Label label,
			Result result,
			String basePath) throws DocumentException, IOException {
		

		
		System.out.println("++++++ result type: " + result.resultsType);
		if(result.resultsType.startsWith("select")) {
			processSelect(document, result, survey, label);
		} else if (result.resultsType.equals("image")) {
			System.out.println("Image: " + result.value);
			if(!result.value.equals("")) {
				Image img = Image.getInstance(basePath + "/" + result.value);
				img.scaleToFit(200, 300);
				document.add(img);
			} else {
				// TODO add empty image
			}
			
		} else {
			String v = result.value;
			if(v == null) {
				v = "";
			}
			document.add(new Paragraph("    " + v));
		}
		
	}
	*/
	
	
	private int processGroup(
			Document document, 
			org.smap.sdal.model.Question question, 
			Label label
			) throws IOException, DocumentException {
		
		
		StringBuffer html = new StringBuffer();
		html.append("<span class='group'><h3>");
		html.append(label.text);
		html.append("</h3></span>");
		ArrayList<Element> objects = 
				(ArrayList<Element>) HTMLWorker.parseToList(new StringReader(html.toString()), styles, null);
		for(Element element : objects) {
			document.add(element);
		}
		
		int width = question.getWidth();
		if(width <= 0) {
			width = GROUP_WIDTH_DEFAULT;
		}
		
		return width;
	}
	
	private PdfPCell processSelect(DisplayItem di) { 

		PdfPCell cell = new PdfPCell();
		
		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		boolean isSelect = di.type.equals("select") ? true : false;
		for(Result aChoice : di.choices) {
			ListItem item = new ListItem(aChoice.name);
			
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
			
		}
		cell.addElement(list);
		return cell;
	}
	
	private void createStyles() {
		styles = new StyleSheet();
		styles.loadTagStyle("h3", "font-size", "20px");
		styles.loadStyle("group", "color", "#ce4f07");
		
	}
	

}
