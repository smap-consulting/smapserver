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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import model.Project;
import model.User;

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
 * Returns a list of all projects that are in the same organisation as the user making the request
 */
@Path("/projectList")
public class ProjectList extends Application {
	
	Authorise a = new Authorise(Authorise.ADMIN);

	private static Logger log =
			 Logger.getLogger(ProjectList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ProjectList.class);
		return s;
	}

	
	@GET
	@Produces("application/json")
	public Response getProjects(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ProjectList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<Project> projects = new ArrayList<Project> ();
		
		try {
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			
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
				
				sql = "select id, name, changed_by, changed_ts " +
						" from project " + 
						" where o_id = ? " +
						" order by name ASC;";				
							
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, o_id);

				log.info("SQL: " + sql + ":" + o_id);
				resultSet = pstmt.executeQuery();
				while(resultSet.next()) {
					Project project = new Project();
					project.id = resultSet.getInt("id");
					project.name = resultSet.getString("name");
					project.changed_by = resultSet.getString("changed_by");
					project.changed_ts = resultSet.getString("changed_ts");
					projects.add(project);
			
				}
				
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				String resp = gson.toJson(projects);
				response = Response.ok(resp).build();
						
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
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
	public Response updateProject(@Context HttpServletRequest request, @FormParam("projects") String projects) { 
		
		Response response = null;
		System.out.println("Project List:" + projects);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		String user = request.getRemoteUser();
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ProjectList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Project>>(){}.getType();		
		ArrayList<Project> pArray = new Gson().fromJson(projects, type);
		
		PreparedStatement pstmt = null;
		try {	
			String sql = null;
			int o_id;
			int u_id;
			int p_id = 0;
			ResultSet resultSet = null;
			/*
			 * Get the organisation and the user id
			 */
			sql = "SELECT u.o_id, u.id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			log.info("SQL: " + sql + ":" + user);
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				u_id = resultSet.getInt(2);
				
				for(int i = 0; i < pArray.size(); i++) {
					Project p = pArray.get(i);
					if(p.id == -1) {
						
						// New project
						connectionSD.setAutoCommit(false);
						
						sql = "insert into project (name, o_id, changed_by, changed_ts) " +
								" values (?, ?, ?, now());";
						
						pstmt = connectionSD.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
						pstmt.setString(1, p.name);
						pstmt.setInt(2, o_id);
						pstmt.setString(3, request.getRemoteUser());
						log.info("SQL: " + sql + " : " + p.name + " : " + o_id);
						pstmt.executeUpdate();
						ResultSet rs = pstmt.getGeneratedKeys();
						if(rs.next()) {
							p_id = rs.getInt(1);
						}
						pstmt.close();
						
						if(p_id > 0) {
							// Add the user to the new project by default
							sql = "insert into user_project (u_id, p_id) " +
									" values (?, ?);";
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setInt(1, u_id);
							pstmt.setInt(2, p_id);
							log.info("SQL: " + sql + " : " + u_id + " : " + p_id);
							pstmt.executeUpdate();
							pstmt.close();
						}
						
						connectionSD.commit();
					} else {
						// Existing project
						
						// Check the project is in the same organisation as the administrator doing the editing
						sql = "SELECT p.id " +
								" FROM project p " +  
								" WHERE p.id = ? " +
								" AND p.o_id = ?;";				
									
						pstmt = connectionSD.prepareStatement(sql);
						pstmt.setInt(1, p.id);
						pstmt.setInt(2, o_id);
						log.info("SQL: " + sql + ":" + p.id + ":" + o_id);
						resultSet = pstmt.executeQuery();
						
						if(resultSet.next()) {
							sql = "update project set " +
									" name = ?, " + 
									" changed_by = ?, " + 
									" changed_ts = now() " + 
									" where " +
									" id = ?;";
						
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setString(1, p.name);
							pstmt.setString(2, request.getRemoteUser());
							pstmt.setInt(3, p.id);
							
							log.info("SQL: " + sql + ":" + p.name + ":" + p.id);
							pstmt.executeUpdate();
			
						}
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
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).build();
			} else {
				response = Response.serverError().build();
				log.log(Level.SEVERE,"Error", e);
			}
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Delete project
	 */
	@DELETE
	@Consumes("application/json")
	public Response delProject(@Context HttpServletRequest request, @FormParam("projects") String projects) { 
		
		Response response = null;
		System.out.println("Project List:" + projects);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ProjectList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Project>>(){}.getType();		
		ArrayList<Project> pArray = new Gson().fromJson(projects, type);
		
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
				
				for(int i = 0; i < pArray.size(); i++) {
					Project p = pArray.get(i);
					
					/*
					 * Ensure that there are no undeleted surveys in this project
					 */
					sql = "SELECT count(*) " +
							" FROM survey u " +  
							" WHERE u.p_id = ?;";
					
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, p.id);
					log.info("SQL: " + sql + ":" + p.id);
					resultSet = pstmt.executeQuery();
					if(resultSet.next()) {
						int count = resultSet.getInt(1);
						if(count > 0) {
							System.out.println("Count:" + count);
							throw new Exception("Error: Project " + p.id + " has undeleted surveys. Hint: You need to erase " +
									"all surveys from a project before it can be deleted. Try selecting" +
									" \"Show deleted surveys\" on the template management screen for the project that you" +
									" have finished with. Then erase those deleted surveys.");
						}
					} else {
						throw new Exception("Error getting survey count");
					}
					
					// Ensure the project is in the same organisation as the administrator doing the editing
					sql = "DELETE FROM project p " +  
							" WHERE p.id = ? " +
							" AND p.o_id = ?;";				
								
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, p.id);
					pstmt.setInt(2, o_id);
					log.info("SQL: " + sql + ":" + p.id + ":" + o_id);
					pstmt.executeUpdate();

				}
			
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
			
			connectionSD.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
			try{
				connectionSD.rollback();
			} catch(Exception e2) {
				
			}
			
		} finally {
			
			try {
				if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			
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

