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
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import model.Settings;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.EmailManager;
import org.smap.sdal.managers.ProjectManager;
import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.RestrictedUser;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;
import org.smap.server.utilities.UtilityMethods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all users that are in the same organisation as the user making the request
 */
@Path("/userList")
public class UserList extends Application {
	
	Authorise a = null;
	Authorise aUpdate = null;
	Authorise aSM = null;

	private static Logger log =
			 Logger.getLogger(UserList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UserList.class);
		return s;
	}
	
	public UserList() {
		
		// Allow administrators and analysts to view the list of users
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
		// Only allow administrators to update user list
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ADMIN);
		aUpdate = new Authorise(authorisations, null);
		
		// Only allow security administrators to view or update the restricted users
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.SECURITY);
		aSM = new Authorise(authorisations, null);
		
	}

	
	@GET
	@Produces("application/json")
	public Response getUsers(
			@Context HttpServletRequest request,
			@QueryParam("month") int month,			// 1 - 12
			@QueryParam("year") int year
			) { 

		Response response = null;
		int previousMonth;
		int previousMonthsYear;
		
		if(month == 1) {
			previousMonth = 12;
			previousMonthsYear = year - 1;
		} else {
			previousMonth = month - 1;
			previousMonthsYear = year;
		}
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<User> users = new ArrayList<User> ();
		
		try {
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(connectionSD, request.getRemoteUser());
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get organisation: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				/*
				 * Get the users, groups and projects for this organisation
				 * Do this in one outer join query rather than running separate group and project queries for 
				 *  each user. This is to reduce the need for potentially a large number of queries if
				 *  an organisation had a large number of users
				 */
				sql = "SELECT users.id as id," +
						"users.ident as ident, " +
						"users.name as name, " +
						"users.email as email, " +
						"groups.name as group_name, " +
						"project.name as project_name, " +
						"groups.id as group_id, " +
						"project.id as project_id, " +
						"user_project.allocated as allocated, " +
						"user_project.restricted as restricted " +
						// Disable all_time its too slow
						//"(select count (*) from upload_event ue, subscriber_event se " +
						//	"where ue.ue_id = se.ue_id " + 
						//	"and se.status = 'success' " +
						//	"and se.subscriber = 'results_db' " +
						//	"and extract(month from upload_time) = ? " + 	// current month
						//	"and extract(year from upload_time) = ? " + 	// current year
						//	"and ue.user_name = users.ident) as this_month, " +
						//"(select count (*) from upload_event ue, subscriber_event se " +
						//	"where ue.ue_id = se.ue_id " + 
						//	"and se.status = 'success' " +
						//	"and se.subscriber = 'results_db' " +
						//	"and extract(month from upload_time) = ? " +	// last month
						//	"and extract(year from upload_time) = ? " + 	// last months year
						//	"and ue.user_name = users.ident) as last_month, " +
						//"(select count (*) from upload_event ue, subscriber_event se " +
						//	"where ue.ue_id = se.ue_id " +
						//	"and se.status = 'success'" +
						//	"and se.subscriber = 'results_db'" +
						//	"and ue.user_name = users.ident) as all_time " +
						"from users " +
						"left outer join user_group on user_group.u_id = users.id " +
						"left outer join groups on groups.id = user_group.g_id " +
						"left outer join user_project on user_project.u_id = users.id " +
						"left outer join project on project.id = user_project.p_id " +
						"where users.o_id = ? " + 
						"order by users.ident, groups.name;";
				
				if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
				pstmt = connectionSD.prepareStatement(sql);
				//pstmt.setInt(1, month);
				//pstmt.setInt(2, year);
				//pstmt.setInt(3, previousMonth);
				//pstmt.setInt(4, previousMonthsYear);
				pstmt.setInt(1, o_id);
				log.info("Get user list: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
				
				String current_user = null;
				String current_group = null;
				String current_project = null;
				User user = null;
				while(resultSet.next()) {

					int id = resultSet.getInt("id");
					String ident = resultSet.getString("ident");
					String group_name = resultSet.getString("group_name");
					String project_name = resultSet.getString("project_name");
					int group_id = resultSet.getInt("group_id");
					int project_id = resultSet.getInt("project_id");
					boolean allocated = resultSet.getBoolean("allocated");
					boolean restricted = resultSet.getBoolean("restricted");
					
					if(current_user == null || !current_user.equals(ident)) {
						
						// New user
						user = new User();
						user.id = id;
						user.ident = ident;
						user.name = resultSet.getString("name");
						user.email = resultSet.getString("email");
						user.groups = new ArrayList<UserGroup> ();
						user.projects = new ArrayList<Project> ();
						//user.this_month = resultSet.getInt("this_month");
						//user.last_month = resultSet.getInt("last_month");
						//user.all_time = resultSet.getInt("all_time");
						
						UserGroup group = new UserGroup();
						group.name = group_name;
						group.id = group_id;
						if(group_id != 4 || isOrgUser) {
							user.groups.add(group);
						}
						
						if(allocated && !restricted) {
							Project project = new Project();
							project.name = project_name;
							project.id = project_id;
							user.projects.add(project);
						}
						
						users.add(user);
						current_user = ident;
						current_group = group.name;
						current_project = project_name;
					
					} else {
						if(current_group != null && !current_group.equals(group_name)) {
						
							// new group
							UserGroup group = new UserGroup();
							group.name = group_name;
							if(group_id != 4 || isOrgUser) {
								user.groups.add(group);
							}
							current_group = group.name;
						}
						
						if(current_project != null && !current_project.equals(project_name)) {
						
							// new project
							if(allocated && !restricted) {
								Project project = new Project();
								project.name = project_name;
								project.id = project_id;
								user.projects.add(project);
							}
							current_project = project_name;
						}
					}
				}
				
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				String resp = gson.toJson(users);
				response = Response.ok(resp).build();
						
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-UserList", connectionSD);

		}

		return response;
	}
	
	/*
	 * Get the users who have access to a specific project
	 */
	@Path("/{projectId}")
	@GET
	@Produces("application/json")
	public Response getUsersForProject(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId
			) { 

		Response response = null;
		
		log.info("userList for project: " + projectId);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<User> users = new ArrayList<User> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			int o_id = 0;
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get organisation: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
			}
			
			/*
			 * Get the users for this project
			 */
			sql = "SELECT u.id as id, " +
					"u.ident as ident, " +
					"u.name as name, " +
					"u.email as email " +
					"from users u, user_project up " +			
					"where u.id = up.u_id " +
					"and up.restricted = false " +
					"and up.allocated = true " +
					"and up.p_id = ? " +
					"and u.o_id = ? " +
					"order by u.ident";
			
			if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, projectId);
			pstmt.setInt(2, o_id);
			log.info("Get users for project: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			User user = null;
			while(resultSet.next()) {
				user = new User();
				user.id = resultSet.getInt("id");
				user.ident = resultSet.getString("ident");
				user.name = resultSet.getString("name");
				user.email = resultSet.getString("email");
				users.add(user);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
					
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-UserList", connectionSD);
		}

		return response;
	}
	
	/*
	 * Get the users who are restricted from accessing a specific project
	 */
	@Path("/{projectId}/restricted")
	@GET
	@Produces("application/json")
	public Response getRestrictedUsersForProject(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId
			) { 

		Response response = null;
		
		log.info("restricted userList for project: " + projectId);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		aSM.isAuthorised(connectionSD, request.getRemoteUser());
		aSM.projectInUsersOrganisation(connectionSD, request.getRemoteUser(), projectId);
		
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<RestrictedUser> users = new ArrayList<RestrictedUser> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			int o_id  = GeneralUtilityMethods.getOrganisationId(connectionSD, request.getRemoteUser());
			
			/*
			 * Get the users for this project
			 */
			sql = "SELECT u.id as id, "
					+ "up.restricted as restricted, "
					+ "u.ident as ident, "
					+ "u.name as name "
					+ "from users u "
					+ "left outer join user_project up "
					+ "on u.id = up.u_id "
					+ "and up.p_id = ? "	
					+ "where u.o_id = ? "
					+ "order by u.ident";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, projectId);
			pstmt.setInt(2, o_id);
			log.info("Get restricted users for project: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			RestrictedUser user = null;
			while(resultSet.next()) {
				user = new RestrictedUser();
				user.id = resultSet.getInt("id");
				user.restricted = resultSet.getBoolean("restricted");
				user.ident = resultSet.getString("ident");
				user.name = resultSet.getString("name");
				users.add(user);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(users);
			response = Response.ok(resp).build();
					
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-UserList", connectionSD);
		}

		return response;
	}
	
	/*
	 * Get the roles in the organisation
	 */
	@Path("/roles")
	@GET
	@Produces("application/json")
	public Response getRoles(
			@Context HttpServletRequest request
			) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-UserList");
		aSM.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		RoleManager rm = new RoleManager();
		try {
			int o_id  = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			ArrayList<Role> roles = rm.getRoles(sd, o_id);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(roles);
			response = Response.ok(resp).build();
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}

		return response;
	}
	
	/*
	 * Update the role details
	 */
	@Path("/roles")
	@POST
	@Consumes("application/json")
	public Response updateRoles(@Context HttpServletRequest request, @FormParam("roles") String roles) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-RoleList");
		aSM.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Role>>(){}.getType();		
		ArrayList<Role> rArray = new Gson().fromJson(roles, type);
		
		try {	
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			RoleManager rm = new RoleManager();
			
			for(int i = 0; i < rArray.size(); i++) {
				Role r = rArray.get(i);
				
				if(r.id == -1) {
					
					// New role
					rm.createRole(sd, r, o_id, request.getRemoteUser());
					
				} else {
					// Existing role
					rm.updateRole(sd, r, o_id, request.getRemoteUser());
			
				}
			
				response = Response.ok().build();
			}
				
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}
		
		return response;
	}
	
	/*
	 * Delete roles
	 */
	@Path("/roles")
	@DELETE
	@Consumes("application/json")
	public Response delRole(@Context HttpServletRequest request, @FormParam("roles") String roles) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-userList - delete roles");
		aSM.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation			
					
		Type type = new TypeToken<ArrayList<Role>>(){}.getType();		
		ArrayList<Role> rArray = new Gson().fromJson(roles, type);
		
		RoleManager rm = new RoleManager();
		
		try {	
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			rm.deleteRoles(sd, rArray, o_id);
			response = Response.ok().build();			
		}  catch (Exception ex) {
			log.log(Level.SEVERE, ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
		} finally {			
			SDDataSource.closeConnection("surveyKPI-userList - delete roles", sd);
		}
		
		return response;
	}
	
	
	/*
	 * Update the settings or create new user
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		try {	
			
			ResourceBundle localisation = null;
			String sql = null;
			int o_id;
			String adminName = null;
			String language;	// Language spoken by user
			ResultSet resultSet = null;
			boolean isOrgUser = GeneralUtilityMethods.isOrgUser(connectionSD, request.getRemoteUser());
			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(connectionSD, request.getRemoteUser());
			
			/*
			 * Get the organisation and name of the user making the request
			 */
			sql = "SELECT u.o_id, u.name, u.language " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				adminName = resultSet.getString(2);
				language = resultSet.getString(3);
				
				// Set locale
				if(language == null) {
					language = "en";
				}
				localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", new Locale(language));
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					
					// Ensure email is null if it has not been set
					if(u.email != null && u.email.trim().isEmpty()) {
						u.email = null;
					}
					
					UserManager um = new UserManager();
					if(u.id == -1) {
						// New user
						um.createUser(connectionSD, u, o_id,
								isOrgUser,
								isSecurityManager,
								request.getRemoteUser(),
								request.getServerName(),
								adminName,
								localisation);

								
					} else {
						// Existing user
						um.updateUser(connectionSD, u, o_id,
								isOrgUser,
								isSecurityManager,
								request.getRemoteUser(),
								request.getServerName(),
								adminName);
					}
				}
		
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
				response = Response.serverError().entity("Error: No organisation").build();
			}
				
		} catch (SQLException e) {
			
			String state = e.getSQLState();
			log.info("sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).entity(e.getMessage()).build();
			} else {
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				log.log(Level.SEVERE,"Error", e);
			}
		} catch (Exception e) {
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserList", connectionSD);
		}
		
		return response;
	}
	

	
	/*
	 * Delete users
	 */
	@DELETE
	@Consumes("application/json")
	public Response delUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtUpdate = null;
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		try {	
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			
			/*
			 * Get the organisation of the person calling this service
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("Get user organisation and id: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					
					// Ensure the user is in the same organisation as the administrator doing the editing
					sql = "DELETE FROM users u " +  
							" WHERE u.id = ? " +
							" AND u.o_id = ?;";				
								
					pstmtUpdate = connectionSD.prepareStatement(sql);
					pstmtUpdate.setInt(1, u.id);
					pstmtUpdate.setInt(2, o_id);
					log.info("Delete user: " + pstmt.toString());
					
					int count = pstmtUpdate.executeUpdate();
					
					// If a user was deleted then delete their directories
					if(count > 0) {						
						GeneralUtilityMethods.deleteDirectory(basePath + "/media/users/" + u.id);
					}					

				}
		
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserList", connectionSD);
		}
		
		return response;
	}
	

}

