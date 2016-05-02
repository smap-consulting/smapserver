package surveyKPI;

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
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.SDDataSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/server")
public class Server extends Application {

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
	}
	
	@GET
	@Produces("application/json")
	public Response getVersion() { 
		
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
				+ "mapbox_default "
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
			}
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}

		return response;
	}
	

}

