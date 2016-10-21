package surveyKPI;

import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.ss.usermodel.Workbook;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import utilities.XLSResultsManager;

/*
 * Provides a survey level export of a survey as an XLS file
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
@Path("/exportxls/{sId}/{filename}")
public class ExportSurveyXls extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyXls.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	

	ArrayList<StringBuffer> parentRows = null;

	
	Workbook wb = null;
	
	@GET
	//@Produces("application/x-download")
	public Response exportSurvey (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("split_locn") boolean split_locn,
			@QueryParam("merge_select_multiple") boolean merge_select_multiple,
			@QueryParam("language") String language,
			@QueryParam("exp_ro") boolean exp_ro,
			@QueryParam("embedimages") boolean embedImages,
			@QueryParam("hxl") boolean hxl,
			@QueryParam("forms") String include_forms,
			@QueryParam("filetype") String filetype,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@QueryParam("dateId") int dateId,
			
			@Context HttpServletResponse response) {
		
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
		
		// Set defaults
	
		log.info("New export, filetype:" + filetype + " split:" + split_locn + 
				" forms:" + include_forms + " filename: " + filename + ", merge select: " + merge_select_multiple);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ExportSurvey");
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

			lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Export to XLS");
			
			connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");
			
			// Set file type to "xlsx" unless "xls" has been specified
			if(filetype == null || !filetype.equals("xls")) {
				filetype = "xlsx";
			}
			
			
			if(language != null) {
				language = language.replace("'", "''");	// Escape apostrophes
			} else {
				language = "none";
			}
			
			/*
			 * Get the list of forms to include in the output and their types (ie flat or pivot)
			 */
			int inc_id [] = null;
			boolean inc_flat [] = null;
			if(include_forms != null) {
				String iForms [] = include_forms.split(",");
				if(iForms.length > 0) {
					inc_id = new int [iForms.length];
					inc_flat = new boolean [iForms.length];
					for(int i = 0; i < iForms.length; i++) {
						String f[] = iForms[i].split(":");
						if(f.length > 1) {
							try {
								inc_id[i] = Integer.parseInt(f[0]);
								inc_flat[i] = Boolean.valueOf(f[1]);
							} catch (Exception e) {
								log.info("Invalid form argument in export: " + iForms[i]);
							}
						}
						
					}
				}
			}
			
			GeneralUtilityMethods.setFilenameInResponse(filename + "." + filetype, response);
			response.setHeader("Content-type",  "application/vnd.ms-excel; charset=UTF-8");
			
			XLSResultsManager xr = new XLSResultsManager(filetype);
		
			xr.createXLS(sd, 
					connectionResults,
					request.getRemoteUser(),
					sId, 
					inc_id, 
					inc_flat, 
					exp_ro, 
					merge_select_multiple, 
					language, 
					split_locn,
					request,
					response.getOutputStream(),
					embedImages,
					hxl,
					startDate,
					endDate,
					dateId,
					superUser);
			
			responseVal = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			// Return an OK status so the message gets added to the web page
			// Prepend the message with "Error: ", this will be removed by the client
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("createXLS", sd);	
			ResultsDataSource.closeConnection("createXLS", connectionResults);
			
		}
		
		return responseVal;
		

		
	}
	

}
