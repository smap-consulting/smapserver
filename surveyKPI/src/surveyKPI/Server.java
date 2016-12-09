package surveyKPI;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;

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

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.model.ServerData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import taskModel.TaskResponse;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/server")
public class Server extends Application {

	Authorise a = new Authorise(null, Authorise.ORG);
	
	private static Logger log =
			 Logger.getLogger(Server.class.getName());
	
	
	@GET
	@Produces("application/json")
	public Response getServerSettings() { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		Response response = null;
		Connection sd = SDDataSource.getConnection("SurveyKPI-getServerSettings");

		try {
			
			ServerManager sm = new ServerManager();
			ServerData data = sm.getServer(sd);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			SDDataSource.closeConnection("SurveyKPI-getServerSettings", sd);
			
		}

		return response;
	}
	
	/*
	 * Load tasks, that is survey results, from a file
	 */
	@POST
	public Response saveServerSettings(@Context HttpServletRequest request,
			@FormParam("settings") String settings) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-SaveServerSettings");
		a.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation
		
		ServerData data = new Gson().fromJson(settings, ServerData.class);
		
		String sqlDel = "truncate table server;";
		PreparedStatement pstmtDel = null;
		
		String sql = "insert into server ("
				+ "smtp_host,"
				+ "email_domain,"
				+ "email_user,"
				+ "email_password,"
				+ "email_port,"
				+ "mapbox_default,"
				+ "google_key) "
				+ "values(?,?,?,?,?,?,?);";
		
		PreparedStatement pstmt = null;



		try {
			
			sd.setAutoCommit(false);
			// Delete the existing data
			pstmtDel = sd.prepareStatement(sqlDel);
			pstmtDel.executeUpdate();
			
			// Add the updated data
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, data.smtp_host);
			pstmt.setString(2, data.email_domain);
			pstmt.setString(3, data.email_user);
			pstmt.setString(4, data.email_password);
			pstmt.setInt(5, data.email_port);
			pstmt.setString(6, data.mapbox_default);
			pstmt.setString(7, data.google_key);
			pstmt.executeUpdate();
			
			sd.setAutoCommit(true);
				
		} catch (Exception e) {
			try {sd.rollback();} catch(Exception ex) {}
			String msg = e.getMessage();
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();	
			
		} finally {
			try {sd.setAutoCommit(true);} catch(Exception e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		
			SDDataSource.closeConnection("surveyKPI-AllAssignments-Save Server Settings", sd);
		}
		
		return response;
	}

}

