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
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import model.Group;
import model.Project;
import model.User;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Manages the logged in user's details
 */
@Path("/user")
public class UserSvc extends Application {
	
	// Authorisation not required

	private static Logger log =
			 Logger.getLogger(UserSvc.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UserSvc.class);
		return s;
	}

	
	@GET
	@Produces("application/json")
	public Response getUserDetails(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Not required
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		User user = new User ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;			
				
			/*
			 * Get the user details
			 */
			sql = "SELECT u.id as id, " +
					"u.name as name, " +
					"u.language as language, " +
					"u.email as email, " +
					"u.current_project_id as current_project_id, " +
					"u.current_survey_id as current_survey_id, " +
					"o.name as organisation_name, " +
					"o.allow_email, " +
					"o.allow_facebook, " +
					"o.allow_twitter, " +
					"o.can_edit, " +
					"o.ft_send_trail " +
					" from users u, organisation o " +
					" where u.ident = ? " +
					" and u.o_id = o.id " +
					" order by u.ident;"; 
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
		
			while(resultSet.next()) {
				user.id = resultSet.getInt("id");
				user.ident = request.getRemoteUser();
				user.name = resultSet.getString("name");
				user.language = resultSet.getString("language");
				user.email = resultSet.getString("email");
				user.current_project_id = resultSet.getInt("current_project_id");
				user.current_survey_id = resultSet.getInt("current_survey_id");
				user.organisation_name = resultSet.getString("organisation_name");
				user.allow_email = resultSet.getBoolean("allow_email");
				user.allow_facebook = resultSet.getBoolean("allow_facebook");
				user.allow_twitter = resultSet.getBoolean("allow_twitter");
				user.can_edit = resultSet.getBoolean("can_edit");
				user.ft_send_trail = resultSet.getBoolean("ft_send_trail");
			}
			
			/*
			 * Set a flag if email is enabled on the server
			 */
			user.sendEmail = UtilityMethods.getSmtpHost(connectionSD, pstmt, request.getRemoteUser()) != null;
			
			/*
			 * Get the groups that the user belongs to
			 */
			sql = "SELECT g.id as id, g.name as name " +
					" from groups g, user_group ug " +
					" where g.id = ug.g_id " +
					" and ug.u_id = ? " +
					" order by g.name;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, user.id);
			log.info("SQL: " + sql + ":" + user.id);
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				if(user.groups == null) {
					user.groups = new ArrayList<Group> ();
				}
				Group group = new Group();
				group.id = resultSet.getInt("id");
				group.name = resultSet.getString("name");
				user.groups.add(group);
			}
			
			/*
			 * Get the projects that the user belongs to
			 */
			sql = "SELECT p.id as id, p.name as name " +
					" from project p, user_project up " +
					" where p.id = up.p_id " +
					" and up.u_id = ? " +
					" order by p.name;";

			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, user.id);
			log.info("SQL: " + sql + ":" + user.id);
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				if(user.projects == null) {
					user.projects = new ArrayList<Project> ();
				}
				Project project = new Project();
				project.id = resultSet.getInt("id");
				project.name = resultSet.getString("name");
				user.projects.add(project);
			}
					
				
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(user);
			response = Response.ok(resp).build();
						
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection: ", e);
			}
		}

		return response;
	}
	
	/*
	 * Update the users details
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("user") String user) { 
		
		Response response = null;
		System.out.println("User details:" + user);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Not Required
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		Type type = new TypeToken<User>(){}.getType();		
		User u = new Gson().fromJson(user, type);
		
		PreparedStatement pstmt = null;
		try {	
			
			// Ensure email is null if it has not been set
			if(u.email != null && u.email.trim().isEmpty()) {
				u.email = null;
			}
			
			if(u.current_project_id > 0 || u.current_survey_id > 0) {
				/*
				 * If the current project/survey is to be changed then only update the project id and survey id
				 */
				String sql = null;
				if(u.current_project_id > 0) {
					sql = "update users set current_project_id = ?, current_survey_id = ? where ident = ?;";
				} else {
					// Only update the survey id
					sql = "update users set current_survey_id = ? where ident = ?;";
				}
							
				pstmt = connectionSD.prepareStatement(sql);
				if(u.current_project_id > 0) {
					pstmt.setInt(1, u.current_project_id);
					pstmt.setInt(2, u.current_survey_id);
					pstmt.setString(3, request.getRemoteUser());
				} else {
					pstmt.setInt(1, u.current_survey_id);
					pstmt.setString(2, request.getRemoteUser());
				}
				
				
				log.info("SQL: " + sql + " : " + u.current_project_id + " : " + u.current_survey_id + " : " + request.getRemoteUser());
				int count = pstmt.executeUpdate();
				if(count == 0) {
					log.info("Failed to update current project id and survey id");
				}  

			} else {
			
				/*
				 * Update what can be updated by the user, excluding the current project id
				 */
				String pwdString = null;
				String sql = null;
				String ident = request.getRemoteUser();
				if(u.password == null) {
					// Do not update the password
					sql = "update users set " +
							" name = ?, " + 
							" language = ?, " + 
							" email = ? " +
							" where " +
							" ident = ?;";
				} else {
					// Update the password
					sql = "update users set " +
							" name = ?, " + 
							" language = ?, " + 
							" email = ?, " +
							" password = md5(?) " +
							" where " +
							" ident = ?;";
					
					pwdString = ident + ":smap:" + u.password;
				}
				
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setString(1, u.name);
				pstmt.setString(2, u.language);
				pstmt.setString(3, u.email);
				if(u.password == null) {
					pstmt.setString(4, ident);
				} else {
					pstmt.setString(4, pwdString);
					pstmt.setString(5, ident);
				}
				
				log.info("userevent: " + request.getRemoteUser() + (u.password == null ? " : updated user details : " : " : updated password : ") + u.name);
				pstmt.executeUpdate();
			}
			
			response = Response.ok().build();
			
			
		} catch (SQLException e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}



}

