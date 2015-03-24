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
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Results;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.GrayColor;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RadioCheckField;


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
	
	public static Font WingDings = null;

	
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
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, instanceId);
			ArrayList<ArrayList<Results>> results = survey.results;	
			
			/*
			 * Create the PDF
			 */
			if(filename != null) {
				
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
	
	private void processForm(
			Document document,  
			ArrayList<Results> record,
			org.smap.sdal.model.Survey survey,
			String basePath) throws DocumentException, IOException {
		for(int j = 0; j < record.size(); j++) {
			Results r = record.get(j);
			if(r.resultsType.equals("form")) {
				for(int k = 0; k < r.subForm.size(); k++) {
					processForm(document, r.subForm.get(k), survey, basePath);
				} 
			} else if(r.qIdx >= 0) {
				// Process the question
				int languageIdx = 0;		// TODO get language in parameters
				Form form = survey.forms.get(r.fIdx);
				org.smap.sdal.model.Question q = form.questions.get(r.qIdx);
				Label label = q.labels.get(languageIdx);
			
				addQuestion(document, label);
				
				if(r.resultsType.startsWith("select")) {
					processSelect(document, r, survey, label);
				} if (r.resultsType.equals("image")) {
					System.out.println("Image: " + r.value);
					Image img = Image.getInstance(basePath + "/" + r.value);
					img.scaleToFit(200, 300);
					document.add(img);
				} else {
					String v = r.value;
					if(v == null) {
						v = "";
					}
					document.add(new Paragraph("    " + v));
				}
			}
		}
		
		return;
	}
	
	/*
	 * Add the question label, hint, and any media
	 */
	private void addQuestion(
			Document document, 
			Label label) throws DocumentException {
		document.add(new Paragraph(label.text));
	}
	
	private void processSelect(Document document, 
			Results r, 
			org.smap.sdal.model.Survey survey,
			Label label) throws DocumentException, IOException {

		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		boolean isSelect = r.resultsType.equals("select") ? true : false;
		for(Results aChoice : r.choices) {
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
		document.add(list);
	}
	
	
	

}
