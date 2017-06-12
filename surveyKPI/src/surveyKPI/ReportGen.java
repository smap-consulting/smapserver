package surveyKPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
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
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.DocumentDataManager;
import org.smap.sdal.model.KeyValue;

import utilities.DocumentXLSManager;

/*
 * Reports
 */
@Path("/reportgen/{sId}/{filename}")
public class ReportGen extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ReportGen.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	
	@GET
	@Produces("application/x-download")
	public Response reportGen (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@Context HttpServletResponse response) throws IOException, Exception {

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ReportGen");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-ReportGen");
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Report");
		
		Response responseVal = null;
		String filetype = "xlsx";
		GeneralUtilityMethods.setFilenameInResponse(filename + "." + filetype, response);
		response.setHeader("Content-type",  "application/vnd.ms-excel; charset=UTF-8");
		
		DocumentDataManager rdm = new DocumentDataManager(sd, sId);
		ArrayList<KeyValue> data = rdm.getData(sd, cResults, sId, startDate, endDate);
		
		DocumentXLSManager rxm = new DocumentXLSManager();
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), sId);
		rxm.create(data, response.getOutputStream(), basePath, oId);
		
		responseVal = Response.ok("").build();
		
		return responseVal;

		
	}
	



}
