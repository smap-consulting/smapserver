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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the values of the passed in text question and the history of changes
 * Only return non null values or null values which have a history of changes
 */

@Path("/onetimelogon")
public class ForgotPassword extends Application {
	
	Authorise a = new Authorise(null, Authorise.ADMIN);
	
	private static Logger log =
			 Logger.getLogger(ForgotPassword.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ForgotPassword.class);
		return s;
	}
	

	
	/*
	 * Get the distinct text results for a question
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
				
				String uuid = String.valueOf(UUID.randomUUID());
				System.out.println("uuid:" + uuid + "::::" + uuid.length());
				
				/*
				 * Get the table name and column name containing the text data
				 */
				String sql = "update users set" +
						" one_time_password = ?," +
						" one_time_password_expiry = timestamp 'now' + interval '24 hours'" +		// Set token to expire in 1 day
						" where email = ?";
		
				log.info(sql);
				pstmt = connectionSD.prepareStatement(sql);	
				pstmt.setString(1, uuid);
				pstmt.setString(2, email);
				int count = pstmt.executeUpdate();
				
				if(count > 0) {
					// Update succeeded
					System.out.println("Sending email");
				} else {
					// email was not found - fail silently so the user can't work out if the email is real
					System.out.println("Error password reset.  Email not found:" + email);
				}
				
				response = Response.ok().build();
			

			
			
			} catch (SQLException e) {
				
				String msg = e.getMessage();
				if(msg.contains("does not exist")) {
					log.info("No data: " + msg);
				} else {
					log.log(Level.SEVERE,"Exception", e);
				}	
				response = Response.status(Status.NOT_FOUND).entity(msg).build();
	
			} catch (Exception e) {
				log.log(Level.SEVERE,"Exception", e);
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
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


}

