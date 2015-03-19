package surveyKPI;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.smap.sdal.model.Results;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.GrayColor;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RadioCheckField;

/*
 * Provides a survey level export of a survey as CSV
 * If the optional parameter "flat" is passed then this is a flat export where 
 *   children are appended to the end of the parent record.
 *   
 * If this parameter is not passed then a pivot style export is created.
 *  * For example for a parent form with a repeating group of children we might get:
 *    P1 C1
 *    P1 C2
 *    P1 C3
 *    P2 C4
 *    P2 C5
 *    P3 ...    // No children
 *    P4 C6
 *    etc
 *    
 */
@Path("/pdf/{sId}/{instance}")
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
	

	
	@GET
	//@Produces("application/x-download")
	@Produces("application/json")
	public Response getPDF (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("instance") String instanceId,
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
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, instanceId);
			ArrayList<ArrayList<Results>> results = survey.results;		
			if(filename != null) {
				Document document = new Document();
				PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filename));
				writer.setInitialLeading(300);
				document.open();
				
				String indent = "";
				int lines = 0;
				for(int i = 0; i < results.size(); i++) {
					lines = processForm(document, writer, results.get(i), indent, lines);		
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
	
	private int processForm(Document document, PdfWriter writer, ArrayList<Results> record, String indent, int lines) throws DocumentException, IOException {
		document.add(new Paragraph(indent + "---------------- Start Form --------------------"));
		lines = incrementLines(document, lines);
		for(int j = 0; j < record.size(); j++) {
			Results r = record.get(j);
			if(r.resultsType.equals("form")) {
				indent += "    ";
				for(int k = 0; k < r.subForm.size(); k++) {
					lines = processForm(document, writer, r.subForm.get(k), indent, lines);
				} 
			} else if(r.resultsType.startsWith("select")) {
				processSelect(document, writer, r.choices, indent + "    ", lines);
				lines = incrementLines(document, lines);
			} else {
				document.add(new Paragraph(indent + r.name + " : " + r.value));
				lines = incrementLines(document, lines);
			}
		}
		document.add(new Paragraph(indent + "---------------- End Form ----------------------"));
		lines = incrementLines(document, lines);
		
		return lines;
	}
	
	private int incrementLines(Document document, int lines) {
		if(lines == 3) {
			lines = 0;
			document.newPage();
		} else {
			lines++;
		}
		return lines;		
	}
	private int processSelect(Document document, PdfWriter writer, ArrayList<Results> select, String indent, int lines) throws DocumentException, IOException {
		PdfContentByte canvas = writer.getDirectContent();
		Font font = new Font (FontFamily.HELVETICA, 18);
		PdfFormField radiogroup = PdfFormField.createRadioButton(writer, true);
		radiogroup.setFieldName("Field Name");
				
		for(int j = 0; j < select.size(); j++) {
			Results r = select.get(j);
			Rectangle rect = new Rectangle(40, 806 - j * 40, 60, 788 - j * 40 );
			RadioCheckField radio = new RadioCheckField(writer, rect, null, r.name);
			radio.setBorderColor(GrayColor.GRAYWHITE);
			radio.setCheckType(RadioCheckField.TYPE_CIRCLE);
			PdfFormField field = radio.getRadioField();
			radiogroup.addKid(field);
			ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(r.name, font), 70, 790 - j * 40, 0);
			document.add(new Paragraph(indent + r.name + " : " + r.isSet));
		}
		writer.addAnnotation(radiogroup);
		return lines++;
		
	}
	

}
