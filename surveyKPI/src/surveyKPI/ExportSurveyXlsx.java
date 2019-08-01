package surveyKPI;

import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

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

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import utilities.XLSXReportsManager;

/*
 * Export a survey in XLSX format
 * This export follows the approach of CSV exports where a single sub form can be selected
 *    
 */
@Path("/exportxlsx/{sId}/{filename}")
public class ExportSurveyXlsx extends Application {

	Authorise a = null;

	private static Logger log =
			Logger.getLogger(ExportSurveyXlsx.class.getName());

	LogManager lm = new LogManager();		// Application log

	public ExportSurveyXlsx() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Get an export with a user authenticated by the web server
	 */
	@GET
	public Response exportSurveyXlsx (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("split_locn") boolean split_locn,
			@QueryParam("merge_select_multiple") boolean merge_select_multiple,
			@QueryParam("language") String language,
			@QueryParam("exp_ro") boolean exp_ro,
			@QueryParam("embedimages") boolean embedImages,
			@QueryParam("excludeparents") boolean excludeParents,
			@QueryParam("hxl") boolean hxl,
			@QueryParam("form") int fId,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@QueryParam("dateId") int dateId,
			@QueryParam("filter") String filter,
			@QueryParam("meta") boolean meta,
			@QueryParam("tz") String tz,					// Timezone
			
			@Context HttpServletResponse response) throws Exception {

		Response responseVal;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ExportSurveyMisc");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}

		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		tz = (tz == null) ? "UTC" : tz;
		
		Connection cResults = null;
		try {
			cResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");		
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			XLSXReportsManager rm = new XLSXReportsManager(localisation);
			responseVal = rm.getNewReport(
					sd,
					cResults,
					request.getRemoteUser(),
					request,
					response,
					sId,
					filename,
					split_locn,
					merge_select_multiple,
					language,
					exp_ro,
					embedImages,
					excludeParents,
					hxl,
					fId,
					startDate,
					endDate,
					dateId,
					filter,
					null,
					meta,
					tz);
		} catch(Exception e) {
			log.log(Level.SEVERE, "Error", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			lm.writeLog(sd, sId, request.getRemoteUser(), "error", e.getMessage());
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-ExportSurvey", sd);
			ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", cResults);
		}
		
		return responseVal;

	}



}
