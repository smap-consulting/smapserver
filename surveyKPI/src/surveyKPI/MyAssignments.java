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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import taskModel.Assignment;
import taskModel.FieldTaskSettings;
import taskModel.FormLocator;
import taskModel.Geometry;
import taskModel.Location;
import taskModel.PointEntry;
import taskModel.Task;
import taskModel.TaskAssignment;
import taskModel.TaskCompletionInfo;
import taskModel.TaskResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
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
	
	// Tell class loader about the root classes.  
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(MyAssignments.class);
		return s;
	}

	
	/*
	 * Return the list of tasks allocated to the requesting user
	 */
	@GET
	@Produces("application/json")
	public Response getTasks(@Context HttpServletRequest request) {
		
		Response response = null;
		String userName = request.getRemoteUser();
		
		TaskResponse tr = new TaskResponse();
		tr.message = "OK Task retrieved";	// Overwritten if there is an error
		tr.status = "200";
					
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-MyAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmtGetForms = null;
		PreparedStatement pstmtGetSettings = null;
		PreparedStatement pstmtGeo = null;
		PreparedStatement pstmt = null;
		
		try {
			String sql = null;
			ResultSet results = null;
			
			/*
			 * Disable dynamic assignments
			 * 
			int assignmentCount = 0;
			
			// Start transaction scope
			connectionSD.setAutoCommit(false);

			// 1. Get the number of assignments for the user
			sql = "select count(*) " +
					"FROM tasks t, assignments a, users u " +
					"WHERE t.id = a.task_id " +
					"AND a.assignee = u.id " +
					"and (a.status = 'pending' or a.status = 'accepted') " +
					"AND u.name = ?;";
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setString(1, userName);

			results =  pstmt.executeQuery();
			if(results.next()) {
				assignmentCount = results.getInt(1);
			}
			
			// 2. Dynamically generate assignments for tasks with assignment mode "dynamic"
			// TODO: Use current location of user to restrict potential dynamic tasks
			if(assignmentCount < 10) {	// TODO Make this count configurable
				System.out.println("Generating assignments.");
				
				// Get the users primary key
				sql = "select id from users " +
						"where ident = ?;"; 
						
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setString(1, userName);
				results = pstmt.executeQuery();
				int userId = 0;
				if(results.next()) {
					userId = results.getInt(1);
				} else {
					log.severe("Failed to get user id");
				}
				
				// Get open dynamic tasks that (TODO) meet the selection criteria
				sql = "select id from tasks " +
						"where dynamic_open = 'true' " +
						"and assignment_mode = 'dynamic' " +
						"limit ?";
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, 10 - assignmentCount);
				results = pstmt.executeQuery();
				
				// When grabbing the task check that it is still open in case another user has got it since the previous query
				sql = "update tasks set dynamic_open = 'false' where id = ? and dynamic_open = 'true';";
				PreparedStatement pstmtDynamic = connectionSD.prepareStatement(sql);
				sql = "insert into assignments " +
						" (assignee, status, task_id, assigned_date, last_status_changed_date) " +
						" values (?, 'pending', ?, now(), now());";
				PreparedStatement pstmtUpdate = connectionSD.prepareStatement(sql);
				
				while(results.next()) {
					int taskId = results.getInt(1);
					pstmtDynamic.setInt(1, taskId);
					int rowsUpdated = pstmtDynamic.executeUpdate();
					// Ignore if the task has been updated by a different user
					if(rowsUpdated > 0) {
						// Create the assignment				
						pstmtUpdate.setInt(1, userId);
						pstmtUpdate.setInt(2, taskId);
						pstmtUpdate.execute();
					}
					
					connectionSD.commit();
				}
			}
			*/
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
					"t.schedule_at," +
					"a.status as assignment_status," +
					"a.id as assignment_id, " +
					"t.address as address, " +
					"t.country as country, " +
					"t.postcode as postcode, " +
					"t.locality as locality, " +
					"t.street as street, " +
					"t.number as number, " +
					"t.geo_type as geo_type " +
					"from tasks t, assignments a, users u, survey s, user_project up " +
					"where t.id = a.task_id " +
					"and t.form_id = s.s_id " +
					"and u.id = up.u_id " +
					"and s.p_id = up.p_id " +
					"and s.deleted = 'false' " +
					"and a.assignee = u.id " +
					"and (a.status = 'pending' or a.status = 'cancelled' or a.status = 'missed' " +
						"or a.status = 'accepted') " +
					"and u.ident = ?;";
						
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, userName);
			
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
				ta.location = new Location();
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
				ta.task.scheduled_at = resultSet.getDate("schedule_at");
				ta.task.address = resultSet.getString("address");
				
				ta.assignment.assignment_id = resultSet.getInt("assignment_id");
				ta.assignment.assignment_status = resultSet.getString("assignment_status");

				ta.location.country = resultSet.getString("country");
				ta.location.postcode = resultSet.getString("postcode");
				ta.location.locality = resultSet.getString("locality");
				ta.location.street = resultSet.getString("street");
				ta.location.number = resultSet.getString("number");
				
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
						System.out.println("Geo string is:" + geoString);
						int startIdx = geoString.lastIndexOf('(');			// Assume no multi polygons
						int endIdx = geoString.indexOf(')');
						if(startIdx > 0 && endIdx > 0) {
							String geoString2 = geoString.substring(startIdx + 1, endIdx);
							System.out.println(geoString2);
							ta.location.geometry.type = geo_type;
							ta.location.geometry.coordinates = geoString2.split(",");
						}
					}

				}
				
				// Add the new task assignment to the list of task assignments
				tr.taskAssignments.add(ta);
				System.out.println("Created assignment for task:" + ta.task.id);
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
					"p.id as pid " +
					"from users u, survey s, user_project up, project p " +
					"where u.id = up.u_id " +
					"and s.p_id = up.p_id " +
					"and p.id = up.p_id " +
					"and s.deleted = 'false' " +
					"and u.ident = ?;";
						
			pstmtGetForms = connectionSD.prepareStatement(sql);	
			pstmtGetForms.setString(1, userName);
			
			log.info("Getting forms: " + pstmtGetForms.toString());
			resultSet = pstmtGetForms.executeQuery();
			
			String hostname = request.getServerName();
			if(hostname.equals("localhost")) {
					hostname = "10.0.2.2";	// For android emulator
			}
			
			TranslationManager translationMgr = new TranslationManager();
			
			tr.forms = new ArrayList<FormLocator> ();
			while(resultSet.next()) {
				int sId = resultSet.getInt("s_id");
				
				FormLocator fl = new FormLocator();
				fl.ident = resultSet.getString("ident");
				fl.version = resultSet.getInt("version");
				fl.name = resultSet.getString("display_name");
				fl.project = resultSet.getString("name");
				fl.pid = resultSet.getInt("pid");
				fl.hasManifest = translationMgr.hasManifest(connectionSD, userName, sId);
				
				tr.forms.add(fl);
			}
			
			/*
			 * Get the settings for the phone
			 */
			tr.settings = new FieldTaskSettings();
			sql = "SELECT " +
					"o.ft_delete_submitted," +
					"o.ft_send_trail " +
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
			}
			
			/*
			 * Return the response
			 */
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("dd/MM/yyyy hh:mm").create();
			String resp = gson.toJson(tr);
			response = Response.ok(resp).build();
				
		} catch (SQLException e) {
			tr.message = "SQL Error: Message=" + e.getMessage();
			tr.status = "400";
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} catch (Exception e) {
			tr.message = "Error: Message=" + e.getMessage();
			tr.status = "400";
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			try {if (pstmtGetForms != null) {pstmtGetForms.close();} } catch (Exception e) {}
			try {if (pstmtGetSettings != null) {pstmtGetSettings.close();} } catch (Exception e) {}
			try {if (pstmtGeo != null) {pstmtGeo.close();} } catch (Exception e) {}
			try {if (pstmt != null) {pstmt.close();} } catch (Exception e) {}
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}

		return response;
	}
	
	/*
	 * Update the task assignment
	 */
	@POST
	public Response setAssignment(@Context HttpServletRequest request, 
			@FormParam("assignInput") String assignInput) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-MyAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		String userName = request.getRemoteUser();
		
		log.info("Response:" + assignInput);
		TaskResponse tr = new Gson().fromJson(assignInput, TaskResponse.class);
			
		System.out.println("Device:" + tr.deviceId + " for user " + request.getRemoteUser());
		
		// TODO that the status is valid (A different range of status values depending on the role of the user)
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtForms = null;
		PreparedStatement pstmtFormsDelete = null;
		PreparedStatement pstmtUser = null;
		PreparedStatement pstmtTasks = null;
		PreparedStatement pstmtTrail = null;
		try {
			String sql = null;

			connectionSD.setAutoCommit(false);
			for(TaskAssignment ta : tr.taskAssignments) {
				System.out.println("+++++Task Assignment: " + ta.assignment.assignment_status);
				
				/*
				 * If the updated status = "cancelled" then this is an acknowledgment of the status set on the server
				 *   hence update the server status to "deleted"
				 */
				if(ta.assignment.assignment_status.equals("cancelled")) {
					System.out.println("Assignment:" + ta.assignment.assignment_id + " acknowledge cancel");
					
					sql = "delete from tasks where id in (select a.task_id from assignments a " +
							"where a.id = ? " + 
							"and a.assignee IN (SELECT id FROM users u " +
								"where u.ident = ?));";
					pstmt = connectionSD.prepareStatement(sql);	
					log.info("Cancelled:  "+ sql + " : " + ta.assignment.assignment_id + " : " + userName);
					pstmt.setInt(1, ta.assignment.assignment_id);
					pstmt.setString(2, userName);
				} else {
				
					// Apply update making sure the assignment was made to the updating user
					sql = "UPDATE assignments a SET status = ? " +
							"where a.id = ? " + 
							"and a.assignee IN (SELECT id FROM users u " +
								"where u.ident = ?);";
					pstmt = connectionSD.prepareStatement(sql);
					log.info("Cancelled: "+ sql + " : " + ta.assignment.assignment_status + " : " + ta.assignment.assignment_id + " : " + userName);
					pstmt.setString(1, ta.assignment.assignment_status);
					pstmt.setInt(2, ta.assignment.assignment_id);
					pstmt.setString(3, userName);
				}
							
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
				
				System.out.println("Updating downloaded forms for user " + userName + " with id " + userId + " and deviceId" + tr.deviceId);
				sql = "delete from form_downloads where u_id = ? and device_id = ?;";
				pstmtFormsDelete = connectionSD.prepareStatement(sql);
				pstmtFormsDelete.setInt(1, userId);
				pstmtFormsDelete.setString(2, tr.deviceId);
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
						System.out.println("    Adding task: " + tci.uuid);
						pstmtTasks.setString(3, tci.ident);
						pstmtTasks.setInt(4, tci.version);
						pstmtTasks.setString(5, tci.uuid);
						pstmtTasks.setString(6, "POINT(" + tci.lon + " " + tci.lat + ")");
						pstmtTasks.setTimestamp(7, new Timestamp(tci.actFinish));
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
						System.out.println("    Adding point: " + pe.toString());
						
						pstmtTrail.setString(3, "POINT(" + pe.lon + " " + pe.lat + ")");
						pstmtTrail.setTimestamp(4, new Timestamp(pe.time));
						pstmtTrail.executeUpdate();
					}
					
				}
			}
			
			connectionSD.commit();
			response = Response.ok().build();
			log.info("Assignments updated");	
				
		} catch (Exception e) {		
			response = Response.serverError().build();
		    e.printStackTrace();
		    try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {if ( pstmt != null ) { pstmt.close(); }} catch (Exception e) {}
			try {if ( pstmtForms != null ) { pstmtForms.close(); }} catch (Exception e) {}
			try {if ( pstmtFormsDelete != null ) { pstmtFormsDelete.close(); }} catch (Exception e) {}
			try {if ( pstmtUser != null ) { pstmtUser.close(); }} catch (Exception e) {}
			try {if ( pstmtTasks != null ) { pstmtTasks.close(); }} catch (Exception e) {}
			try {if ( pstmtTrail != null ) { pstmtTrail.close(); }} catch (Exception e) {}
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



