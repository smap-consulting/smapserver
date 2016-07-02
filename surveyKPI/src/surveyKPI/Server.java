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
	
	private class ServerData {
		String smtp_host;
		String email_domain;
		String email_user;
		String email_password;
		int email_port;
		String version;
		String mapbox_default;
		String google_key;
	}
	
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
		Connection sd = SDDataSource.getConnection("SurveyKPI - version");
		String sql = "select smtp_host,"
				+ "email_domain,"
				+ "email_user,"
				+ "email_password,"
				+ "email_port,"
				+ "version,"
				+ "mapbox_default,"
				+ "google_key "
				+ "from server;";
		PreparedStatement pstmt = null;
		ServerData data = new ServerData();

		try {
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				data.email_domain = rs.getString("email_domain");
				data.email_user = rs.getString("email_user");
				data.email_password = rs.getString("email_password");
				data.email_port = rs.getInt("email_port");
				data.version = rs.getString("version");
				data.mapbox_default = rs.getString("mapbox_default");
				data.google_key = rs.getString("google_key");
			}
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("SurveyKPI - version", sd);
			
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
		
		String sql = "select smtp_host,"
				+ "email_domain,"
				+ "email_user,"
				+ "email_password,"
				+ "email_port,"
				+ "version,"
				+ "mapbox_default,"
				+ "google_key "
				+ "from server;";
		
		PreparedStatement pstmt = null;

		try {
			
			sd.setAutoCommit(false);
			// Delete the existing data
			pstmtDel = sd.prepareStatement(sqlDel);
			pstmtDel.executeUpdate();
			
			// Add the updated data
			pstmt = sd.prepareStatement(sql);
			
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

