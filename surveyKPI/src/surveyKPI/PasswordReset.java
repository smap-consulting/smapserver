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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.EmailManager;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;

import org.smap.sdal.resources.*;

import com.google.gson.Gson;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
  *Forgotton password
 */

@Path("/onetimelogon")
public class PasswordReset extends Application {
		
	private static Logger log =
			 Logger.getLogger(PasswordReset.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(PasswordReset.class);
		return s;
	}
	

	
	/*
	 * Send an email with a link for a one time logon
	 */
	@GET
	@Path("/{email}")
	public Response oneTimeLogon(@Context HttpServletRequest request,
			@PathParam("email") String email		 
			) { 
	
		Response response = null;

				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}

		Connection connectionSD = SDDataSource.getConnection("surveyKPI-onetimelogon");
		PreparedStatement pstmt = null;

		if(email != null && email.trim().length() > 0) {		
			
			try {	
				

				/*
				 * If the "email" does not have an "@" then it may be a user ident
				 *  This is a hacky attempt to support legacy idents that were not emails
				 */
				if(!email.contains("@")) {
					email = UtilityMethodsEmail.getEmailFromIdent(connectionSD, pstmt, email);
				}
				
				String interval = "1 hour";
				String uuid = UtilityMethodsEmail.setOnetimePassword(connectionSD, pstmt, email, interval);
				
				if(uuid != null) {
					// Update succeeded
					System.out.println("Sending email");
					
					// Localisation
					Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(connectionSD, email, request.getRemoteUser());
					Locale locale = new Locale(organisation.locale);
					ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
					
					EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(connectionSD, email, request.getRemoteUser());
					
					
					if(emailServer.smtpHost != null) {
						
						ArrayList<String> idents = UtilityMethodsEmail.getIdentsFromEmail(connectionSD, pstmt, email);
					    String sender = "reset";
					    
					    String subject = localisation.getString("c_r_p");
					    EmailManager em = new EmailManager();
						em.sendEmail(email, uuid, "reset", subject, null, sender, null, interval, 
					    		idents, null, null, null, organisation.getAdminEmail(), emailServer, 
					    		request.getServerName(),
					    		localisation);
					    response = Response.ok().build();
					} else {
						String msg = "Error password reset.  Email not enabled on this server.";
						log.info(msg);
						msg = localisation.getString("email_ne");
						response = Response.status(Status.NOT_FOUND).entity(msg).build();
					}
				} else {
					// email was not found 
					String msg = "Error password reset.  Email address not found:" + email;
					System.out.println(msg);
					response = Response.status(Status.NOT_FOUND).entity(msg).build();
				}
				

			} catch (SQLException e) {
				
				String msg = e.getMessage();
				String respMsg = "Database Error";
				if(msg.contains("does not exist")) {
					log.info("No data: " + msg);
					respMsg = "Database Error: No data";
				} else {
					log.log(Level.SEVERE,"Exception", e);
				}	
				response = Response.status(Status.NOT_FOUND).entity(respMsg).build();
	
			} catch (Exception e) {
				log.log(Level.SEVERE,"Exception", e);
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity("System Error").build();
			} finally {
				
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
				try {
					if (connectionSD != null) {
						connectionSD.close();
						connectionSD = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE,"Failed to close connection", e);
				}
			}
		} else {
			response = Response.status(Status.NOT_FOUND).entity("Email not specified").build();
		}

		return response;
	}

	/*
	 * Update the users password
	 */
	class PasswordDetails {
		String onetime;
		String password;
	}
	
	@POST
	public Response setPassword(@Context HttpServletRequest request, 
			@FormParam("passwordDetails") String passwordDetails) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-setPassword");
		
		PasswordDetails pd = new Gson().fromJson(passwordDetails, PasswordDetails.class);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtDel = null;
		PreparedStatement pstmtUpdate = null;
		try {
			
			connectionSD.setAutoCommit(false);
			
			// Get the user ident just for logging, also check that there is an valid onetime token
			String sql = "select ident, name from users where one_time_password = ? and one_time_password_expiry > timestamp 'now'"; 
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, pd.onetime);
			System.out.println("SQL set passowrd: " + pstmt.toString());
			
			ResultSet rs = pstmt.executeQuery();
			int count = 0;
			while(rs.next()) {
				String ident = rs.getString(1);
				String name = rs.getString(2);
				
				System.out.println("Updating password for user " + name + " with ident " + ident);
				
				sql = "update users set password = md5(?), password_reset = 'true' where one_time_password = ? and ident = ?;";
				pstmtUpdate = connectionSD.prepareStatement(sql);
				String pwdString = ident + ":smap:" + pd.password;
				pstmtUpdate.setString(1, pwdString);
				pstmtUpdate.setString(2, pd.onetime);
				pstmtUpdate.setString(3, ident);
				
				pstmtUpdate.executeUpdate();
				response = Response.ok().build();
				log.info("Password updated");
				count++;
			} 
			
			if(count == 0) {
				// Clean up an expired token
				sql = "update users set one_time_password = null, one_time_password_expiry = null where one_time_password = ?";
				pstmtDel = connectionSD.prepareStatement(sql);
				pstmtDel.setString(1, pd.onetime);
				int nbrUpdated = pstmtDel.executeUpdate();
				if(nbrUpdated > 0) {
					response = Response.status(Status.NOT_FOUND).entity("Token has expired").build();
				} else {
					response = Response.status(Status.NOT_FOUND).entity("Token not found").build();
				}
				
			}

			connectionSD.commit();
	
				
		} catch (Exception e) {		
			response = Response.serverError().build();
		    e.printStackTrace();
		    try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {if ( pstmt != null ) { pstmt.close(); }} catch (Exception e) {}
			try {if ( pstmtDel != null ) { pstmtDel.close(); }} catch (Exception e) {}
			try {if ( pstmtUpdate != null ) { pstmtUpdate.close(); }} catch (Exception e) {}
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		}
		
		return response;
	}

}

