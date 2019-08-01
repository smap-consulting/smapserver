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

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.EmailManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PeopleManager;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;

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
 * Forgotton password
 */

@Path("/onetimelogon")
public class PasswordReset extends Application {
		
	private static Logger log =
			 Logger.getLogger(PasswordReset.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	/*
	 * Send an email with a link for a one time logon
	 */
	@GET
	@Path("/{email}")
	public Response oneTimeLogon(@Context HttpServletRequest request,
			@PathParam("email") String email		 
			) throws ApplicationException { 
	
		Response response = null;

		Connection sd = SDDataSource.getConnection("surveyKPI-onetimelogon");
		PreparedStatement pstmt = null;

		try {
			if(email != null && email.trim().length() > 0) {	
				
				// Localisation
				String hostname = request.getServerName();
				String loc_code = "en";
				if(hostname.contains("kontrolid")) {
					loc_code = "es";
				} 
				Locale locale = new Locale(loc_code);
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				boolean emailSent = UtilityMethodsEmail.hasOnetimePasswordBeenSent(sd, pstmt, email, "3000 seconds");
				if(emailSent) {
					// Potential spam
					log.info("warning: email: " + email + " multiple password reset requests");
					throw new ApplicationException(localisation.getString("email_pas"));
				}
				
				/*
				 * If the "email" does not have an "@" then it may be a user ident
				 *  This is a hacky attempt to support legacy idents that were not emails
				 */
				if(!email.contains("@")) {
					email = UtilityMethodsEmail.getEmailFromIdent(sd, pstmt, email);
				}
				
				String interval = "1 hour";
				String uuid = UtilityMethodsEmail.setOnetimePassword(sd, pstmt, email, interval);
				
				if(uuid != null) {
					// Update succeeded
					log.info("Sending email");
					
					EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, email, request.getRemoteUser());
					
					PeopleManager pm = new PeopleManager(localisation);
					String emailKey = pm.getEmailKey(sd, 0, email);
					if(emailKey == null) {
						// Person has unsubscribed
						String msg = localisation.getString("email_us");
						msg = msg.replaceFirst("%s1", email);
						throw new ApplicationException(msg);
					}
					
					if(emailServer.smtpHost != null) {
						
						ArrayList<String> idents = UtilityMethodsEmail.getIdentsFromEmail(sd, pstmt, email);
					    String sender = "reset";
					    
					    String subject = localisation.getString("c_r_p");
					    EmailManager em = new EmailManager();
						em.sendEmail(email, uuid, "reset", subject, null, sender, null, interval, 
					    		idents, null, null, null, null, emailServer, 
					    		request.getScheme(),
					    		request.getServerName(),
					    		emailKey,
					    		localisation,
					    		null);
					    response = Response.ok().build();
					} else {
						String msg = "Error password reset.  Email not enabled on this server.";
						log.info(msg);
						msg = localisation.getString("email_ne");
						response = Response.status(Status.NOT_FOUND).entity(msg).build();
					}
				} else {
					// email was not found 
					String msg = localisation.getString("email_nf") + " :" + email;
					log.info(msg);
					response = Response.status(Status.NOT_FOUND).entity(msg).build();
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity("Email not specified").build();
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
	
		} catch (ApplicationException e) {
			throw e;
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity("System Error").build();
		} finally {
				
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
			SDDataSource.closeConnection("surveyKPI-onetimelogon", sd);
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
	
		Connection sd = SDDataSource.getConnection("surveyKPI-setPassword");
		
		PasswordDetails pd = new Gson().fromJson(passwordDetails, PasswordDetails.class);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtDel = null;
		PreparedStatement pstmtUpdate = null;
		try {
			
			sd.setAutoCommit(false);
			
			// Get the user ident just for logging, also check that there is a valid onetime token
			String sql = "select ident, name from users where one_time_password = ? and one_time_password_expiry > timestamp 'now'"; 
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, pd.onetime);
			log.info("SQL set password: " + pstmt.toString());
			
			ResultSet rs = pstmt.executeQuery();
			int count = 0;
			while(rs.next()) {
				String ident = rs.getString(1);
				String name = rs.getString(2);
				
				log.info("Updating password for user " + name + " with ident " + ident);
				
				sql = "update users set password = md5(?), password_reset = 'true' where one_time_password = ? and ident = ?;";
				pstmtUpdate = sd.prepareStatement(sql);
				String pwdString = ident + ":smap:" + pd.password;
				pstmtUpdate.setString(1, pwdString);
				pstmtUpdate.setString(2, pd.onetime);
				pstmtUpdate.setString(3, ident);
				
				pstmtUpdate.executeUpdate();
				response = Response.ok().build();
				log.info("Password updated");
				count++;
				
				log.info("userevent: " + ident + "reset password / forgot password");
				lm.writeLog(sd, -1, ident, "user details", "reset password / forgot password");
			} 
			
			if(count == 0) {
				// Clean up an expired token
				sql = "update users set one_time_password = null, one_time_password_expiry = null where one_time_password = ?";
				pstmtDel = sd.prepareStatement(sql);
				pstmtDel.setString(1, pd.onetime);
				int nbrUpdated = pstmtDel.executeUpdate();
				if(nbrUpdated > 0) {
					response = Response.status(Status.NOT_FOUND).entity("Token has expired").build();
				} else {
					response = Response.status(Status.NOT_FOUND).entity("Token not found").build();
				}
				
			}

			sd.commit();
	
				
		} catch (Exception e) {		
			response = Response.serverError().build();
		    e.printStackTrace();
		    try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {if ( pstmt != null ) { pstmt.close(); }} catch (Exception e) {}
			try {if ( pstmtDel != null ) { pstmtDel.close(); }} catch (Exception e) {}
			try {if ( pstmtUpdate != null ) { pstmtUpdate.close(); }} catch (Exception e) {}
			try {
				if (sd != null) {
					sd.setAutoCommit(true);
					sd.close();
				}
			} catch (SQLException e) {
				log.info("Failed to close connection");
			    e.printStackTrace();
			}
		}
		
		return response;
	}

}

