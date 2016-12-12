package surveyKPI;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.ss.usermodel.Workbook;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.ExchangeFile;

import utilities.XLSResultsManager;
import utilities.ExchangeManager;

/*
 * Export data from a survey in XLS worksheets. This is for data exchange rather than analysis
 * The data for each form will be included on  separate worksheet
 *    
 */
@Path("/surveyexchange/{sId}/{filename}")
public class SurveyExchange extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(SurveyExchange.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	ArrayList<StringBuffer> parentRows = null;

	
	Workbook wb = null;
	
	@GET
	public Response exportSurvey (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@PathParam("filename") String filename
			) {
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    try {
		    	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
		    		"Survey: Error: Can't find PostgreSQL JDBC Driver");
		    } catch (Exception ex) {
		    	log.log(Level.SEVERE, "Exception", ex);
		    }
		}
		
		Response responseVal = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ExportSurveyTransfer");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		Connection connectionResults = null;
		
		try {

			lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Export all Survey Data");
			
			connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurveyTransfer");
			
			GeneralUtilityMethods.setFilenameInResponse(filename + ".zip", response);
			response.setHeader("Content-type",  "application/octet-stream; charset=UTF-8");
			
			/*
			 * 1. Create the folder to contain the files
			 */
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
			File folder = new File(filePath);
			folder.mkdir();
			
			
			/*
			 * Save the XLS export into the folder
			 */
			ExchangeManager xm = new ExchangeManager();
			ArrayList<ExchangeFile> files = xm.createExchangeFiles(
					sd, 
					connectionResults,
					request.getRemoteUser(),
					sId, 
					request,
					filePath,
					superUser);
			
			System.out.println("Created "+ files.size() + "  exchange files");
			/*
			 * Save the images into the folder
			 */
			/*
			 * Return the zip file
			 */
			ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
			byte[] buffer = new byte[1024];
			for(int i = 0; i < files.size(); i++) {
				ExchangeFile file = files.get(i);
				ZipEntry ze= new ZipEntry(file.name);
	    		zos.putNextEntry(ze);
	    		FileInputStream in = new FileInputStream(file.path);
	    		
	    		int len;
	    		while ((len = in.read(buffer)) > 0) {
	    			zos.write(buffer, 0, len);
	    		}

	    		in.close();
	    		zos.closeEntry();
			}
			zos.close();
			
			responseVal = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-ExportSurveyTransfer", sd);	
			ResultsDataSource.closeConnection("surveyKPI-ExportSurveyTransfer", connectionResults);
			
		}
		
		return responseVal;
		

		
	}
	
	
	

}
