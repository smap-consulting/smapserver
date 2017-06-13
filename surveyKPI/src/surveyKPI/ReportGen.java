package surveyKPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.DocumentDataManager;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Report2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.DocumentXLSManager;

/*
 * Reports
 */
@Path("/reportgen")
public class ReportGen extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ReportGen.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	@GET
	@Produces("application/json")
	public Response getAvailable(@Context HttpServletRequest request) {
		
		Response response = null;
		String fn = "SurveyKPI - Get Available Reports";
		
		// No need for authorisation - all reports the user has access to are returned
		
		Connection sd = SDDataSource.getConnection(fn);
		PreparedStatement pstmt = null;
		
		StringBuffer sql = new StringBuffer("select id, name, s_id from report where s_id in "
				+ "(select s_id from survey s, users u, user_project up, project p "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and u.ident = ? "
				+ "and not s.deleted ");
		
		ArrayList<Report2> reports = new ArrayList<Report2> ();
		
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		
		try {		
			
			if(!superUser) {
				// Add RBAC
				sql.append(GeneralUtilityMethods.getSurveyRBAC());
			}

			sql.append(")");
			ResultSet resultSet = null;
			
			pstmt = sd.prepareStatement(sql.toString());
			pstmt.setString(1, request.getRemoteUser());
			
			if(!superUser) {
				pstmt.setString(2, request.getRemoteUser());
			}
			
			
			log.info("Get report list: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				Report2 report = new Report2();
				report.id = resultSet.getInt("id");
				report.name = resultSet.getString("name");
				report.sId = resultSet.getInt("s_id");
				reports.add(report);
		
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(reports);
			response = Response.ok(resp).build();
						
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
			
			SDDataSource.closeConnection(fn, sd);
		}

		return response;
	}
	
	@GET
	@Path("/{sId}/{filename}")
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
