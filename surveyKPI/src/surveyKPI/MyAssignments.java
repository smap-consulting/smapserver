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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.JsonAuthorisationException;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ExternalFileManager;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.Assignment;
import org.smap.sdal.model.Geometry;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.Task;
import org.smap.sdal.model.TaskAssignment;
import org.smap.sdal.model.TaskLocation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import model.Settings;
import taskModel.FieldTaskSettings;
import taskModel.FormLocator;
import taskModel.PointEntry;
import taskModel.TaskCompletionInfo;
import taskModel.TaskResponse;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns data for the passed in table name
 */
@Path("/myassignments")
public class MyAssignments extends Application {

	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(Survey.class.getName());
	
	class KeyValue {
		String name;
		String value;
		
		public KeyValue(String k, String v) {
			name = k;
			value = v;
		}
	}

	/*
	 * Get assignments for user authenticated with credentials
	 */
	@GET
	@Produces("application/json")
	public Response getTasksCredentials(@Context HttpServletRequest request) throws SQLException {
		log.info("webserviceevent : getTasksCredentials");
		return getTasks(request, request.getRemoteUser());
	}
	
	/*
	 * Get assignments for user authenticated with a key
	 */
	@GET
	@Produces("application/json")
	@Path("/key/{key}")
	public Response getTaskskey(
			@PathParam("key") String key,
			@Context HttpServletRequest request) throws SQLException {
		
		log.info("webserviceevent : getTaskskey");
		
		String user = null;		
		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-Upload");
		
		try {
			user = GeneralUtilityMethods.getDynamicUser(connectionSD, key);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection("surveyMobileAPI-Upload", connectionSD);
		}
		
		if (user == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}
		return getTasks(request, user);
	}
	
	/*
	 * Post assignments for user authenticated with a key
	 */
	@POST
	@Produces("application/json")
	@Path("/key/{key}")
	public Response updateTasksKey(
			@PathParam("key") String key,
			@FormParam("assignInput") String assignInput,
			@Context HttpServletRequest request) {
		
		log.info("webserviceevent : updateTasksKey");
		
		String user = null;		
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UpdateTasksKey");
		
		try {
			user = GeneralUtilityMethods.getDynamicUser(connectionSD, key);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection("surveyKPI-UpdateTasksKey", connectionSD);
		}
		
		if (user == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}

		return updateTasks(request, assignInput, user);
	}
	
	/*
	 * Update assignments for user authenticated with credentials
	 */
	@POST
	@Produces("application/json")
	public Response updateTasksCredentials(
			@Context HttpServletRequest request, 
			@FormParam("assignInput") String assignInput) {
		
		log.info("webserviceevent : updateTasksCredentials");
		return updateTasks(request, assignInput, request.getRemoteUser());
	}
	
	/*
	 * Return the list of tasks allocated to the requesting user
	 */
	public Response getTasks(HttpServletRequest request, String userName) throws SQLException {
		
		Response response = null;
		
		TaskResponse tr = new TaskResponse();
		tr.message = "OK Task retrieved";	// Overwritten if there is an error
		tr.status = "200";
		tr.version = 1;
					
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-MyAssignments");
		a.isAuthorised(connectionSD, userName);
		// End Authorisation
		
		PreparedStatement pstmtGetForms = null;
		PreparedStatement pstmtGetSettings = null;
		PreparedStatement pstmtGetProjects = null;
		PreparedStatement pstmtGeo = null;
		PreparedStatement pstmt = null;
		
		Connection cRel = null;
		
		int oId = GeneralUtilityMethods.getOrganisationId(connectionSD, userName, 0);
		
		try {
			String sql = null;
			
			cRel = ResultsDataSource.getConnection("surveyKPI-MyAssignments");
			
			connectionSD.setAutoCommit(true);
			
			// Get the assignments
			sql = "SELECT " +
					"t.id as task_id," +
					"t.type," +
					"t.title," +
					"t.url," +
					"s.ident as form_ident," +
					"s.version as form_version," +
					"s.p_id as pid," +
					"t.initial_data," +
					"t.update_id," +
					"t.schedule_at," +
					"t.location_trigger," +
					"t.repeat," +
					"a.status as assignment_status," +
					"a.id as assignment_id, " +
					"t.address as address, " +
					"t.guidance as guidance, " +
					"t.geo_type as geo_type " +
					"from tasks t, assignments a, users u, survey s, user_project up, project p " +
					"where t.id = a.task_id " +
					"and t.form_id = s.s_id " +
					"and u.id = up.u_id " +
					"and s.p_id = up.p_id " +
					"and s.p_id = p.id " +
					"and s.deleted = 'false' " +
					"and s.blocked = 'false' " +
					"and a.assignee = u.id " +
					"and (a.status = 'pending' or a.status = 'cancelled' or a.status = 'missed' " +
						"or a.status = 'accepted') " +
					"and u.ident = ? " +
					"and p.o_id = ?";
						
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, userName);
			pstmt.setInt(2, oId);
			
			log.info("Getting assignments: " + pstmt.toString());
			ResultSet resultSet = pstmt.executeQuery();

			int t_id = 0;
			while (resultSet.next()) {
				
				// Create the list of task assignments if it has not already been created
				if(tr.taskAssignments == null) {
					tr.taskAssignments = new ArrayList<TaskAssignment>();
				}
				
				// Create the new Task Assignment Objects
				TaskAssignment ta = new TaskAssignment();
				ta.task = new Task();
				ta.location = new TaskLocation();
				ta.assignment = new Assignment();

				// Populate the new Task Assignment
				t_id = resultSet.getInt("task_id");
				ta.task.id = t_id;
				ta.task.type = resultSet.getString("type");
				ta.task.title = resultSet.getString("title");
				ta.task.pid = resultSet.getString("pid");
				ta.task.url = resultSet.getString("url");
				ta.task.form_id = resultSet.getString("form_ident");		// Form id is survey ident
				ta.task.form_version = resultSet.getString("form_version");
				ta.task.initial_data = resultSet.getString("initial_data");
				ta.task.update_id = resultSet.getString("update_id");
				ta.task.scheduled_at = resultSet.getTimestamp("schedule_at");
				ta.task.location_trigger = resultSet.getString("location_trigger");
				if(ta.task.location_trigger != null && ta.task.location_trigger.trim().length() == 0) {
					ta.task.location_trigger = null;
				}
				ta.task.repeat = resultSet.getBoolean("repeat");
				ta.task.address = resultSet.getString("address");
				ta.task.address = addKeyValuePair(ta.task.address, "guidance", resultSet.getString("guidance"));	// Address stored as json key value pairs
				
				ta.assignment.assignment_id = resultSet.getInt("assignment_id");
				ta.assignment.assignment_status = resultSet.getString("assignment_status");

				
				String geo_type = resultSet.getString("geo_type");
				// Get the coordinates
				if(geo_type != null) {
					// Add the coordinates

					ta.location.geometry = new Geometry();
					if(geo_type.equals("POINT")) {
						sql = "select ST_AsText(geo_point) from tasks where id = ?;";
					} else if (geo_type.equals("POLYGON")) {
						sql = "select ST_AsText(geo_polygon) from tasks where id = ?;";
					} else if (geo_type.equals("LINESTRING")) {
						sql = "select ST_AsText(geo_linestring) from tasks where id = ?;";
					}
					pstmtGeo = connectionSD.prepareStatement(sql);
					pstmtGeo.setInt(1, t_id);
					ResultSet resultSetGeo = pstmtGeo.executeQuery();
					if(resultSetGeo.next()) {
						String geoString = resultSetGeo.getString(1);
						int startIdx = geoString.lastIndexOf('(');			// Assume no multi polygons
						int endIdx = geoString.indexOf(')');
						if(startIdx > 0 && endIdx > 0) {
							String geoString2 = geoString.substring(startIdx + 1, endIdx);
							ta.location.geometry.type = geo_type;
							ta.location.geometry.coordinates = geoString2.split(",");
						}
					}

				}
				
				// Add the new task assignment to the list of task assignments
				tr.taskAssignments.add(ta);
			}
			
			/*
			 * Get the complete list of forms accessible by this user
			 */
			sql = "SELECT " +
					"s.s_id," +
					"s.ident, " +
					"s.version, " +
					"s.display_name, " +
					"p.name, " +
					"p.id as pid, " +
					"p.tasks_only as tasks_only " +
					"from users u, survey s, user_project up, project p " +
					"where u.id = up.u_id " +
					"and s.p_id = up.p_id " +
					"and p.id = up.p_id " +
					"and s.deleted = 'false' " +
					"and s.blocked = 'false'" +
					"and u.ident = ? " +
					"and p.o_id = ?";
						
			pstmtGetForms = connectionSD.prepareStatement(sql);	
			pstmtGetForms.setString(1, userName);
			pstmtGetForms.setInt(2, oId);
			
			log.info("Getting forms: " + pstmtGetForms.toString());
			resultSet = pstmtGetForms.executeQuery();
			
			TranslationManager translationMgr = new TranslationManager();
			
			tr.forms = new ArrayList<FormLocator> ();
			while(resultSet.next()) {
				int sId = resultSet.getInt("s_id");
				
				/*
				 * For each form that has a manifest that links to another form
				 *  generate the new CSV files if the linked data has changed
				 */
				List<ManifestValue> manifestList = translationMgr.
						getLinkedManifests(connectionSD, request.getRemoteUser(), sId);
				
				for( ManifestValue m : manifestList) {
					
					String filepath = null;
					
					log.info("Linked file:" + m.fileName);
					
					// Create file 
					ExternalFileManager efm = new ExternalFileManager();
					String basepath = GeneralUtilityMethods.getBasePath(request);
					String sIdent = GeneralUtilityMethods.getSurveyIdent(connectionSD, sId);
					filepath = basepath + "/media/" + sIdent+ "/" + m.fileName;
					
					efm.createLinkedFile(connectionSD, cRel, sId, m.fileName , filepath + ".csv");
					
					filepath += ".csv";
					m.fileName += ".csv";
				}
				
				
				
				FormLocator fl = new FormLocator();
				fl.ident = resultSet.getString("ident");
				fl.version = resultSet.getInt("version");
				fl.name = resultSet.getString("display_name");
				fl.project = resultSet.getString("name");
				fl.pid = resultSet.getInt("pid");
				fl.tasks_only = resultSet.getBoolean("tasks_only");
				fl.hasManifest = translationMgr.hasManifest(connectionSD, userName, sId);
				
				tr.forms.add(fl);
			}
			
			/*
			 * Get the settings for the phone
			 */
			tr.settings = new FieldTaskSettings();
			sql = "SELECT " +
					"o.ft_delete_submitted," +
					"o.ft_send_trail, " +
					"o.ft_sync_incomplete " +
					"from organisation o, users u " +
					"where u.o_id = o.id " +
					"and u.ident = ?;";
			
			pstmtGetSettings = connectionSD.prepareStatement(sql);	
			pstmtGetSettings.setString(1, userName);
			log.info("Getting settings: " + pstmtGetSettings.toString());
			resultSet = pstmtGetSettings.executeQuery();
			
			if(resultSet.next()) {
				tr.settings.ft_delete_submitted = resultSet.getBoolean(1);
				tr.settings.ft_send_trail = resultSet.getBoolean(2);
				tr.settings.ft_sync_incomplete = resultSet.getBoolean(3);
				tr.settings.ft_location_trigger = GeneralUtilityMethods.isBusinessServer(request.getServerName());
			}
			
			/*
			 * Get the projects
			 */
			tr.projects = new ArrayList<Project> ();
			sql = "select p.id, p.name, p.description " +
					" from users u, user_project up, project p " + 
					"where u.id = up.u_id " +
					"and p.id = up.p_id " +
					"and u.ident = ? " +
					"and p.o_id = ? " +
					"order by name ASC;";	
			
			pstmtGetProjects = connectionSD.prepareStatement(sql);	
			pstmtGetProjects.setString(1, userName);
			pstmtGetProjects.setInt(2, oId);
			
			log.info("Getting projects: " + pstmtGetProjects.toString());
			resultSet = pstmtGetProjects.executeQuery();
			
			while(resultSet.next()) {
				Project p = new Project();
				p.id = resultSet.getInt(1);
				p.name = resultSet.getString(2);
				p.desc = resultSet.getString(3);
				tr.projects.add(p);
			}
			
			
			/*
			 * Return the response
			 */
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm").create();
			String resp = gson.toJson(tr);
			response = Response.ok(resp).build();
				
		} catch (SQLException e) {
			tr.message = "SQL Error: Message=" + e.getMessage();
			tr.status = "400";
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
		} catch (Exception e) {
			tr.message = "Error: Message=" + e.getMessage();
			tr.status = "400";
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
		} finally {
			try {if (pstmtGetForms != null) {pstmtGetForms.close();} } catch (Exception e) {}
			try {if (pstmtGetSettings != null) {pstmtGetSettings.close();} } catch (Exception e) {}
			try {if (pstmtGeo != null) {pstmtGeo.close();} } catch (Exception e) {}
			try {if (pstmt != null) {pstmt.close();} } catch (Exception e) {}
			
			SDDataSource.closeConnection("surveyKPI-MyAssignments", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-MyAssignments", cRel);	
		}

		return response;
	}
	
	/*
	 * Add a key value pair to an array of key value pairs stored as json
	 */
	String addKeyValuePair(String jIn, String name, String value) {
		
		String jOut = null;
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm").create();
		Type type = new TypeToken<ArrayList<KeyValue>>(){}.getType();
		
		ArrayList<KeyValue> kvArray = null;
		
		// 1. Get the current array
		if(jIn != null && jIn.trim().length() > 0) {
			kvArray = new Gson().fromJson(jIn, type);
		} else {
			kvArray = new ArrayList<KeyValue> ();
		}

		// 2. Add the new kv pair
		if(value != null && value.trim().length() > 0 ) {
			KeyValue newKV = new KeyValue(name, value);
			
			kvArray.add(newKV);
		}
		
		// 3. Return the updated list
		jOut = gson.toJson(kvArray);
		return jOut;
	}
	
	/*
	 * Update the task assignment
	 */
	public Response updateTasks(@Context HttpServletRequest request, 
			String assignInput,
			String userName) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Can't find Postgres JDBC driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-MyAssignments");
		
		// Authorisation not required a user can only update their own assignments
		
		TaskResponse tr = new Gson().fromJson(assignInput, TaskResponse.class);
			
		log.info("Device:" + tr.deviceId + " for user " + userName);
		
		// TODO that the status is valid (A different range of status values depending on the role of the user)
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtForms = null;
		PreparedStatement pstmtFormsDelete = null;
		PreparedStatement pstmtUser = null;
		PreparedStatement pstmtTasks = null;
		PreparedStatement pstmtTrail = null;
		PreparedStatement pstmtRepeats = null;
		try {
			String sql = null;
			
			String sqlRepeats = "UPDATE tasks SET repeat_count = repeat_count + 1 " +
					"where id = (select task_id from assignments where id = ?);";
			pstmtRepeats = connectionSD.prepareStatement(sqlRepeats);

			connectionSD.setAutoCommit(false);
			for(TaskAssignment ta : tr.taskAssignments) {
				log.info("+++++Task Assignment: " + ta.assignment.assignment_status);
				
				/*
				 * If the updated status = "cancelled" then this is an acknowledgment of the status set on the server
				 *   hence update the server status to "deleted"
				 */
				if(ta.assignment.assignment_status.equals("cancelled")) {
					log.info("Assignment:" + ta.assignment.assignment_id + " acknowledge cancel");
					
					sql = "delete from tasks where id in (select a.task_id from assignments a " +
							"where a.id = ? " + 
							"and a.assignee IN (SELECT id FROM users u " +
								"where u.ident = ?));";
					pstmt = connectionSD.prepareStatement(sql);	
					pstmt.setInt(1, ta.assignment.assignment_id);
					pstmt.setString(2, userName);
				} else {
				
					// Apply update making sure the assignment was made to the updating user
					sql = "UPDATE assignments a SET status = ? " +
							"where a.id = ? " + 
							"and a.assignee IN (SELECT id FROM users u " +
								"where u.ident = ?);";
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setString(1, ta.assignment.assignment_status);
					pstmt.setInt(2, ta.assignment.assignment_id);
					pstmt.setString(3, userName);
					
					if(ta.assignment.assignment_status.equals("submitted")) {
						pstmtRepeats.setInt(1, ta.assignment.assignment_id);
						log.info("Updating task repeats: " + pstmtRepeats.toString());
						pstmtRepeats.executeUpdate();
					}
				}
				
				log.info("update assignments: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
			/*
			 * Update the status of down loaded forms
			 */
			sql = "select id from users where ident = ?"; 
			pstmtUser = connectionSD.prepareStatement(sql);
			pstmtUser.setString(1, userName);
			ResultSet rs = pstmtUser.executeQuery();
			if(rs.next()) {
				int userId = rs.getInt(1);
				
				log.info("Updating downloaded forms for user " + userName + " with id " + userId + " and deviceId" + tr.deviceId);
				sql = "delete from form_downloads where u_id = ? and device_id = ?;";
				pstmtFormsDelete = connectionSD.prepareStatement(sql);
				pstmtFormsDelete.setInt(1, userId);
				pstmtFormsDelete.setString(2, tr.deviceId);
				
				log.info("Delete existing form downloads: " + pstmtFormsDelete.toString());
				pstmtFormsDelete.executeUpdate();
				
				sql = "insert into form_downloads (" +
						"u_id, " +
						"device_id, " +
						"form_ident, " +
						"form_version, " +
						"updated_time" +
						") " +
						"values(?, ?, ?, ?, now());";
				pstmtForms = connectionSD.prepareStatement(sql);
				pstmtForms.setInt(1, userId);
				pstmtForms.setString(2, tr.deviceId);
				for(FormLocator f : tr.forms) {
					pstmtForms.setString(3, f.ident);
					pstmtForms.setInt(4, f.version);
					pstmtForms.executeUpdate();
				}
				

				/*
				 * Record task information for any submitted tasks
				 */
				if(tr.taskCompletionInfo != null) {
					sql = "insert into task_completion (" +
							"u_id, " +
							"device_id, " +
							"form_ident, " +
							"form_version, " +
							"uuid,"	+
							"the_geom," +
							"completion_time" +
							") " +
							"values(?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?);";
					pstmtTasks = connectionSD.prepareStatement(sql);
					pstmtTasks.setInt(1, userId);
					pstmtTasks.setString(2, tr.deviceId);
					for(TaskCompletionInfo tci : tr.taskCompletionInfo) {

						pstmtTasks.setString(3, tci.ident);
						pstmtTasks.setInt(4, tci.version);
						pstmtTasks.setString(5, tci.uuid);
						pstmtTasks.setString(6, "POINT(" + tci.lon + " " + tci.lat + ")");
						pstmtTasks.setTimestamp(7, new Timestamp(tci.actFinish));
						
						log.info("Insert task: " + pstmtTasks.toString());
						pstmtTasks.executeUpdate();
					}
					
				}
				
				/*
				 * Record user trail information
				 */
				if(tr.userTrail != null) {
					sql = "insert into user_trail (" +
							"u_id, " +
							"device_id, " +			
							"the_geom," +
							"event_time" +
							") " +
							"values(?, ?, ST_GeomFromText(?, 4326), ?);";
					pstmtTrail = connectionSD.prepareStatement(sql);
					pstmtTrail.setInt(1, userId);
					pstmtTrail.setString(2, tr.deviceId);
					for(PointEntry pe : tr.userTrail) {
						log.info("    Adding point: " + pe.toString());
						
						pstmtTrail.setString(3, "POINT(" + pe.lon + " " + pe.lat + ")");
						pstmtTrail.setTimestamp(4, new Timestamp(pe.time));
						
						log.info("Insert into trail: " + pstmtTrail.toString());
						pstmtTrail.executeUpdate();
					}
					
				}
			}
			
			connectionSD.commit();
			response = Response.ok().build();
			log.info("Assignments updated");	
				
		} catch (Exception e) {		
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Exception", e);
		    try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {if ( pstmt != null ) { pstmt.close(); }} catch (Exception e) {}
			try {if ( pstmtForms != null ) { pstmtForms.close(); }} catch (Exception e) {}
			try {if ( pstmtFormsDelete != null ) { pstmtFormsDelete.close(); }} catch (Exception e) {}
			try {if ( pstmtUser != null ) { pstmtUser.close(); }} catch (Exception e) {}
			try {if ( pstmtTasks != null ) { pstmtTasks.close(); }} catch (Exception e) {}
			try {if ( pstmtTrail != null ) { pstmtTrail.close(); }} catch (Exception e) {}
			try {if ( pstmtRepeats != null ) { pstmtRepeats.close(); }} catch (Exception e) {}
			
			SDDataSource.closeConnection("surveyKPI-MyAssignments", connectionSD);
		}
		
		return response;
	}
}



