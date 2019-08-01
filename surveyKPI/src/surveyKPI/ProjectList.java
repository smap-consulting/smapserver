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
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.ProjectManager;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all projects that are in the same organisation as the user making the request
 */
@Path("/projectList")
public class ProjectList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ADMIN);
	Authorise aSM = new Authorise(null, Authorise.SECURITY);

	private static Logger log =
			 Logger.getLogger(ProjectList.class.getName());

	
	@GET
	@Produces("application/json")
	public Response getProjects(@Context HttpServletRequest request) { 

		Response response = null;
		String connectionString = "surveyKPI-ProjectList";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		ArrayList<Project> projects = null;
		
		try {
			ProjectManager pm = new ProjectManager();
			projects = pm.getProjects(sd, request.getRemoteUser(), 
					true	,	// always get all projects in organisation
					false, 	// Don't get links
					null		// Dn't need url prefix
					);
				
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(projects);
			response = Response.ok(resp).build();
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;
	}
	
	/*
	 * Update the project details
	 */
	@POST
	@Consumes("application/json")
	public Response updateProject(@Context HttpServletRequest request, @FormParam("projects") String projects) { 
		
		Response response = null;
		
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
			ResultSet resultSet = null;
			
			/*
			 * Get the organisation and the user id
			 */
			sql = "SELECT u.o_id, u.id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				u_id = resultSet.getInt(2);
				
				ProjectManager pm = new ProjectManager();
				
				for(int i = 0; i < pArray.size(); i++) {
					Project p = pArray.get(i);
					
					if(p.id == -1) {
						
						// New project
						pm.createProject(connectionSD, p, o_id, u_id, request.getRemoteUser());
						
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
						log.info("SQL: " + pstmt.toString());
						resultSet = pstmt.executeQuery();
						
						if(resultSet.next()) {
							sql = "update project set " +
									" name = ?, " + 
									" description = ?, " + 
									" tasks_only = ?, " + 
									" changed_by = ?, " + 
									" changed_ts = now() " + 
									" where " +
									" id = ?;";
						
							try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setString(1, p.name);
							pstmt.setString(2, p.desc);
							pstmt.setBoolean(3,p.tasks_only);
							pstmt.setString(4, request.getRemoteUser());
							pstmt.setInt(5, p.id);
							
							log.info("update project: " + pstmt.toString());
							pstmt.executeUpdate();
						}
						
						// Record the project change so that devices can be notified
						MessagingManager mm = new MessagingManager();
						mm.projectChange(connectionSD, p.id, o_id);
					}
				}
			
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().entity("No Organisation").build();
			}
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).entity("Duplicate Project Name").build();
			} else {
				response = Response.serverError().entity(e.getMessage()).build();
				log.log(Level.SEVERE,"Error", e);
			}
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-ProjectList", connectionSD);
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
		String connectionString = "surveyKPI-ProjectList";
		Connection cResults = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation			
					
		Type type = new TypeToken<ArrayList<Project>>(){}.getType();		
		ArrayList<Project> pArray = new Gson().fromJson(projects, type);
		
		PreparedStatement pstmt = null;
		try {	
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			sd.setAutoCommit(false);
			
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			/*
			 * Get the organisation
			 */
			sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
				
				for(int i = 0; i < pArray.size(); i++) {
					Project p = pArray.get(i);
					
					a.projectInUsersOrganisation(sd, request.getRemoteUser(), p.id);		// Authorise deletion of this project
					
					/*
					 * Ensure that there are no undeleted surveys in this project
					 * Don't count hidden surveys which have been replaced
					 */
					sql = "select count(*) "
							+ " from survey s " 
							+ " where s.p_id = ? "
							+ "and s.hidden = false";
					
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1, p.id);
					log.info("Check for undeleted surveys: " + pstmt.toString());
					resultSet = pstmt.executeQuery();
					if(resultSet.next()) {
						int count = resultSet.getInt(1);
						if(count > 0) {
							String msg = localisation.getString("msg_undel_proj").replace("%s1", String.valueOf(p.id));
							throw new Exception(msg);
						}
					} else {
						throw new Exception("Error getting survey count");
					}			
					
					// Erase any hidden forms 
					ServerManager sm = new ServerManager();
					sql = "select s_id, ident, display_name "
							+ " from survey s " 
							+ " where s.p_id = ? "
							+ "and s.hidden = true";
					try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1, p.id);
					cResults = ResultsDataSource.getConnection(connectionString);
					String basePath = GeneralUtilityMethods.getBasePath(request);
					
					ResultSet rs = pstmt.executeQuery();
					while(rs.next()) {
						int sId = rs.getInt(1);
						String ident = rs.getString(2);
						String displayName = rs.getString(3);
						sm.deleteSurvey(		// Delete the replaced survey
								sd, 
								cResults,
								request.getRemoteUser(),
								p.id,
								sId,
								ident,
								displayName,
								basePath,
								true,
								"yes");
					}
					
					// Delete the project
					sql = "delete from project p " 
							+ "where p.id = ? "
							+ "and p.o_id = ?";			// Ensure the project is in the same organisation as the administrator doing the editing
						
					try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1, p.id);
					pstmt.setInt(2, o_id);
					log.info("Delete project: " + pstmt.toString());
					pstmt.executeUpdate();

				}
			
				response = Response.ok().build();
			} else {
				log.log(Level.SEVERE,"Error: No organisation");
			    response = Response.serverError().build();
			}
			
			sd.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
			try{
				sd.rollback();
			} catch(Exception e2) {
				
			}
			
		} finally {
			
			try {
				if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
		}
		
		return response;
	}
	

}

