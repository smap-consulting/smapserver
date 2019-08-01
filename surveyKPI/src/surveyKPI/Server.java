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
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.model.ServerData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/server")
public class Server extends Application {

	Authorise aServerLevel = new Authorise(null, Authorise.OWNER);
	Authorise aUserLevel = null;
	
	private static Logger log =
			 Logger.getLogger(Server.class.getName());
		
	public Server() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		aUserLevel = new Authorise(authorisations, null);
	}
	
	@GET
	@Produces("application/json")
	public Response getServerSettings(@Context HttpServletRequest request) { 

		Response response = null;
		String connectionString = "SurveyKPI-getServerSettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aServerLevel.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation

		try {
			
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			ServerManager sm = new ServerManager();
			ServerData data = sm.getServer(sd, localisation);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;
	}
	
	/*
	 * Save updated server settings
	 */
	@POST
	public Response saveServerSettings(@Context HttpServletRequest request,
			@FormParam("settings") String settings) { 

		Response response = null;
		String connectionString = "surveyKPI-SaveServerSettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aServerLevel.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation
		
		ServerData data = new Gson().fromJson(settings, ServerData.class);
		
		String sql = "update server set "
				+ "smtp_host = ?,"
				+ "email_domain = ?,"
				+ "email_user = ?,"
				+ "email_password = ?,"
				+ "email_port = ?,"
				+ "mapbox_default = ?,"
				+ "google_key = ?,"
				+ "sms_url = ?";
		
		PreparedStatement pstmt = null;

		String sqlInsert = "insert into server(smtp_host) values(?)";
		PreparedStatement pstmtInsert = null;
		
		try {
			
			// Add the updated data
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, data.smtp_host);
			pstmt.setString(2, data.email_domain);
			pstmt.setString(3, data.email_user);
			pstmt.setString(4, data.email_password);
			pstmt.setInt(5, data.email_port);
			pstmt.setString(6, data.mapbox_default);
			pstmt.setString(7, data.google_key);
			pstmt.setString(8, data.sms_url);
			int count = pstmt.executeUpdate();
			
			if(count == 0) {
				
				pstmtInsert = sd.prepareStatement(sqlInsert);
				pstmtInsert.setString(1, null);
				pstmtInsert.executeUpdate();
				pstmt.executeUpdate();
			}
				
		} catch (Exception e) {
			String msg = e.getMessage();
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();	
			
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtInsert != null) {pstmtInsert.close();}} catch (SQLException e) {}
		
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	/*
	 * Get the mapbox key
	 */
	@GET
	@Path("/mapbox")
	@Produces("text/html")
	public Response getMapboxKey(@Context HttpServletRequest request) {

		Response response = null;
		String connectionString = "SurveyKPI-getMapboxKey";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aUserLevel.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation

		String sql = "select mapbox_default from server;";
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			String key = "";
			if(rs.next()) {
				key = rs.getString(1);
			}
			response = Response.ok(key).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (Exception e) {	}
			SDDataSource.closeConnection("connectionString", sd);
			
		}

		return response;
	}
	
	/*
	 * Return the type of sms that is enabled for this server
	 */
	@GET
	@Path("/sms")
	@Produces("text/html")
	public Response getAwsSMS(@Context HttpServletRequest request) {

		Response response = null;
		String connectionString = "SurveyKPI-getSMS";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aUserLevel.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation

		String sql = "select sms_url from server;";
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			String key = "none";
			if(rs.next()) {
				String sms_url = rs.getString(1);
				if(sms_url != null) {
					if(sms_url.equals("aws")) {
						key = "aws";
					} else {
						key = "external";
					}
				}
			}
			response = Response.ok(key).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (Exception e) {	}
			SDDataSource.closeConnection("connectionString", sd);
			
		}

		return response;
	}
	
	/*
	 * Get the google key
	 */
	@GET
	@Path("/googlemaps")
	@Produces("text/html")
	public Response getGoogleKey(@Context HttpServletRequest request) {

		Response response = null;
		String connectionString = "SurveyKPI-getGoogleKey";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aUserLevel.isAuthorised(sd, request.getRemoteUser());
		// End role based authorisation

		String sql = "select google_key from server;";
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			String key = "";
			if(rs.next()) {
				key = rs.getString(1);
			}
			response = Response.ok(key).build();
			
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (Exception e) {	}
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;
	}

}

