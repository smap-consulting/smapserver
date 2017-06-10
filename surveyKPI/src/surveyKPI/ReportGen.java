package surveyKPI;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
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
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.ReportDataManager;
import org.smap.sdal.model.KeyValue;

import utilities.ReportXLSManager;
import utilities.XLSResultsManager;

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
	public Response exportSurvey (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@Context HttpServletResponse response) throws IOException, Exception {

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
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Report");
		
		Response responseVal = null;
		String filetype = "xls";
		GeneralUtilityMethods.setFilenameInResponse(filename + "." + filetype, response);
		response.setHeader("Content-type",  "application/vnd.ms-excel; charset=UTF-8");
		
		ReportDataManager rdm = new ReportDataManager(sd, sId);
		ArrayList<KeyValue> data = rdm.getData();
		
		ReportXLSManager rxm = new ReportXLSManager();
		
		rxm.create(data, response.getOutputStream());
		
		responseVal = Response.ok("").build();
		
		return responseVal;

		
	}
	



}
