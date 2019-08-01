package surveyKPI;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.ss.usermodel.Workbook;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.FileDescription;

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
			@PathParam("filename") String filename,
			@QueryParam("media") boolean media
			) {
		
		Response responseVal = null;
		
		String connectionName = "surveyKPI-ExportSurveyTransfer";
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionName);
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

			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";
			
			lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Export all Survey Data");
			
			connectionResults = ResultsDataSource.getConnection(connectionName);
			
			GeneralUtilityMethods.setFilenameInResponse(filename + ".zip", response);
			response.setHeader("Content-type",  "application/octet-stream; charset=UTF-8");
			
			/*
			 * 1. Create the folder to contain the files
			 */
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
			File folder = new File(filePath);
			folder.mkdir();
	
			// restore the media files as probably they have been archived off to S3
			if(media) {
				String sIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
				GeneralUtilityMethods.restoreUploadedFiles(sIdent, "attachments");
			}
			
			/*
			 * Save the XLS export into the folder
			 */
			ExchangeManager xm = new ExchangeManager(localisation, tz);
			ArrayList<FileDescription> files = xm.createExchangeFiles(
					sd, 
					connectionResults,
					request.getRemoteUser(),
					sId, 
					request,
					filePath,
					superUser,
					media);
						
			GeneralUtilityMethods.writeFilesToZipOutputStream(new ZipOutputStream(response.getOutputStream()), files);			
			responseVal = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection(connectionName, sd);	
			ResultsDataSource.closeConnection(connectionName, connectionResults);
			
		}
		
		return responseVal;
		

		
	}
	
	
	

}
