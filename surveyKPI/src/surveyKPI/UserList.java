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

import model.Group;
import model.Project;
import model.Settings;
import model.User;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

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
 * Returns a list of all users that are in the same organisaiton as the user making the request
 */
@Path("/userList")
public class UserList extends Application {
	
	Authorise a = new Authorise(Authorise.ADMIN);

	private static Logger log =
			 Logger.getLogger(UserList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UserList.class);
		return s;
	}

	
	@GET
	@Produces("application/json")
	public Response getUsers(@Context HttpServletRequest request) { 

		Response response = null;
		
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
			boolean isOrgUser = isOrgUser(connectionSD, request.getRemoteUser());
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				/*
				 * Get the users, groups and projects for this organisation
				 * Do this in one outer join query rather than running separate group and project queries for 
				 *  each user. This is to reduce the need for potentially a large number of queries if
				 *  an organisation had a large number of users
				 */
				sql = "SELECT users.id as id, " +
						"users.ident as ident, " +
						"users.name as name, " +
						"users.email as email, " +
						"groups.name as group_name, " +
						"project.name as project_name, " +
						"groups.id as group_id, " +
						"project.id as project_id " +
						" from users " +
						" left outer join user_group on user_group.u_id = users.id " + 
						" left outer join groups on groups.id = user_group.g_id " +
						" left outer join user_project on user_project.u_id = users.id " + 
						" left outer join project on project.id = user_project.p_id " +
						" where users.o_id = ? " +
						" order by users.ident, groups.name;";
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, o_id);
				log.info("SQL: " + sql + ":" + o_id);
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
					if(current_user == null || !current_user.equals(ident)) {
						
						// New user
						user = new User();
						user.id = id;
						user.ident = ident;
						user.name = resultSet.getString("name");
						user.email = resultSet.getString("email");
						user.groups = new ArrayList<Group> ();
						user.projects = new ArrayList<Project> ();
						
						Group group = new Group();
						group.name = group_name;
						group.id = group_id;
						if(group_id != 4 || isOrgUser) {
							user.groups.add(group);
						}
						
						Project project = new Project();
						project.name = project_name;
						project.id = project_id;
						user.projects.add(project);
						
						users.add(user);
						current_user = ident;
						current_group = group.name;
						current_project = project.name;
					
					} else {
						if(current_group != null && !current_group.equals(group_name)) {
						
							// new group
							Group group = new Group();
							group.name = group_name;
							if(group_id != 4 || isOrgUser) {
								user.groups.add(group);
							}
							current_group = group.name;
						}
						
						if(current_project != null && !current_project.equals(project_name)) {
						
							// new project
							Project project = new Project();
							project.name = project_name;
							project.id = project_id;
							user.projects.add(project);
							current_project = project.name;
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
	 * Update the settings
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;
		System.out.println("User List:" + users);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		try {	
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			boolean isOrgUser = isOrgUser(connectionSD, request.getRemoteUser());
			
			connectionSD.setAutoCommit(false);
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					
					if(u.id == -1) {
						// New user
						
						sql = "insert into users (ident, realm, name, email, o_id, password) " +
								" values (?, ?, ?, ?, ?, md5(?));";
						
						String pwdString = u.ident + ":smap:" + u.password;
						pstmt = connectionSD.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
						pstmt.setString(1, u.ident);
						pstmt.setString(2, "smap");
						pstmt.setString(3, u.name);
						pstmt.setString(4, u.email);
						pstmt.setInt(5, o_id);
						pstmt.setString(6, pwdString);
						log.info("SQL: " + sql + ":" + u.ident + ":" + "smap" + ":" + u.name + ":" + u.email + ":" + o_id);
						pstmt.executeUpdate();
						
						int u_id = -1;
						ResultSet rs = pstmt.getGeneratedKeys();
						if (rs.next()){
						    u_id = rs.getInt(1);
						    insertUserGroupsProjects(connectionSD, u, u_id, isOrgUser);
						}
								
					} else {
						// Existing user
						
						// Check the user is in the same organisation as the administrator doing the editing
						sql = "SELECT u.id " +
								" FROM users u " +  
								" WHERE u.id = ? " +
								" AND u.o_id = ?;";				
									
						pstmt = connectionSD.prepareStatement(sql);
						pstmt.setInt(1, u.id);
						pstmt.setInt(2, o_id);
						log.info("SQL: " + sql + ":" + u.id + ":" + o_id);
						resultSet = pstmt.executeQuery();
						
						if(resultSet.next()) {
							
							// Delete existing user groups
							if(isOrgUser) {
								sql = "delete from user_group where u_id = ?;";
							} else {
								sql = "delete from user_group where u_id = ? and g_id != 4;";		// Cannot change super user group
							}
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setInt(1, u.id);
							log.info("SQL: " + sql + ":" + u.id);
							pstmt.executeUpdate();
							
							// Delete existing user projects
							sql = "delete from user_project where u_id = ?;";
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setInt(1, u.id);
							log.info("SQL: " + sql + ":" + u.id);
							pstmt.executeUpdate();
							
							// update existing user
							System.out.println("password:" + u.password);
							String pwdString = null;
							if(u.password == null) {
								// Do not update the password
								sql = "update users set " +
										" ident = ?, " +
										" realm = ?, " +
										" name = ?, " + 
										" email = ? " +
										" where " +
										" id = ?;";
							} else {
								// Update the password
								sql = "update users set " +
										" ident = ?, " +
										" realm = ?, " +
										" name = ?, " + 
										" email = ?, " +
										" password = md5(?) " +
										" where " +
										" id = ?;";
								
								pwdString = u.ident + ":smap:" + u.password;
							}
						
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setString(1, u.ident);
							pstmt.setString(2, "smap");
							pstmt.setString(3, u.name);
							pstmt.setString(4, u.email);
							if(u.password == null) {
								pstmt.setInt(5, u.id);
							} else {
								pstmt.setString(5, pwdString);
								pstmt.setInt(6, u.id);
							}
							
							log.info("SQL: " + sql + ":" + u.ident + ":" + "smap");
							pstmt.executeUpdate();
						
							// Update the groups and projects
							insertUserGroupsProjects(connectionSD, u, u.id, isOrgUser);
			
						}
					}
				}
		
				connectionSD.commit();
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
				
		} catch (SQLException e) {
			
			try{connectionSD.rollback();} catch(Exception er) {log.log(Level.SEVERE,"Failed to rollback connection", er);}
			
			String state = e.getSQLState();
			log.info("sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).build();
			} else {
				response = Response.serverError().build();
				log.log(Level.SEVERE,"Error", e);
			}
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	private boolean isOrgUser(Connection con, String ident) {
		 
		String sql = "SELECT count(*) " +
				" FROM users u, user_group ug " +  
				" WHERE u.id = ug.u_id " +
				" AND ug.g_id = 4 " +
				" AND u.ident = ?; ";				
		
		boolean isOrg = false;
		PreparedStatement pstmt = null;
		try {
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, ident);
			log.info("SQL: " + sql + ":" + ident);
			ResultSet resultSet = pstmt.executeQuery();
			
			if(resultSet.next()) {
				if(resultSet.getInt(1) == 1) {
					isOrg = true;
				}
			}
		} catch(Exception e) {
			log.log(Level.SEVERE,"Error", e);
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return isOrg;
		
	}
	
	private void insertUserGroupsProjects(Connection conn, User u, int u_id, boolean isOrgUser) throws SQLException {

		String sql;
		PreparedStatement pstmt = null;
		
		System.out.println("Update groups and projects user id:" + u_id);
		
		for(int j = 0; j < u.groups.size(); j++) {
			Group g = u.groups.get(j);
			if(g.id != 4 || isOrgUser) {
				sql = "insert into user_group (u_id, g_id) values (?, ?);";
				pstmt = conn.prepareStatement(sql);
				pstmt.setInt(1, u_id);
				pstmt.setInt(2, g.id);
				log.info("SQL: " + sql + ":" + u_id + ":" + g.id);
				pstmt.executeUpdate();
			}
		}
			
		for(int j = 0; j < u.projects.size(); j++) {
			Project p = u.projects.get(j);
			sql = "insert into user_project (u_id, p_id) values (?, ?);";
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, u_id);
			pstmt.setInt(2, p.id);
			log.info("SQL: " + sql + ":" + u_id + ":" + p.id);
			pstmt.executeUpdate();
		}
	}
	
	/*
	 * Delete users
	 */
	@DELETE
	@Consumes("application/json")
	public Response delUser(@Context HttpServletRequest request, @FormParam("users") String users) { 
		
		Response response = null;
		System.out.println("User List:" + users);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UserList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		PreparedStatement pstmt = null;
		try {	
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			
			connectionSD.setAutoCommit(false);
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				for(int i = 0; i < uArray.size(); i++) {
					User u = uArray.get(i);
					
					// Ensure the user is in the same organisation as the administrator doing the editing
					sql = "DELETE FROM users u " +  
							" WHERE u.id = ? " +
							" AND u.o_id = ?;";				
								
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, u.id);
					pstmt.setInt(2, o_id);
					log.info("SQL: " + sql + ":" + u.id + ":" + o_id);
					pstmt.executeUpdate();

				}
		
				connectionSD.commit();			
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
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	

}

