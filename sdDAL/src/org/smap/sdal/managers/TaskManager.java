package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import javax.ws.rs.core.Request;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.AssignFromSurvey;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/*****************************************************************************

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

 ******************************************************************************/

/*
 * Manage the table that stores details on tasks
 */
public class TaskManager {
	
	private static Logger log =
			 Logger.getLogger(TaskManager.class.getName());
	
	private class TaskInstanceData {
		int prikey = 0;						// data from submission
		String location = null;				// data from submission
		String address = null;				// data from submission
		String locationTrigger = null;		// data from task set up
	}
	
	/*
	 * Get the current task groups
	 */
	public ArrayList<TaskGroup> getTaskGroups(Connection sd, int projectId) throws Exception {
		
		String sql = "select tg_id, name, address_params, p_id, rule, source_s_id "
				+ "from task_group where p_id = ? order by tg_id asc;";
		PreparedStatement pstmt = null;
		
		String sqlTotal = "select tg.tg_id, count(t.id)  "
				+ "from task_group tg "
				+ "left outer join tasks t "
				+ "on tg.tg_id = t.tg_id " 
				+ "left outer join assignments a "
				+ "on a.task_id = t.id " 
				+ "and a.status != 'deleted' "
				+ "where tg.p_id = ? "
				+ "group by tg.tg_id "
				+ "order by tg.tg_id asc;";
		PreparedStatement pstmtTotal = null;
		
		String sqlComplete = "select tg.tg_id, count(a.id)  "
				+ "from task_group tg "
				+ "left outer join tasks t "
				+ "on tg.tg_id = t.tg_id " 
				+ "left outer join assignments a "
				+ "on a.task_id = t.id " 
				+ "and a.status = 'submitted' "
				+ "where tg.p_id = ? "
				+ "group by tg.tg_id "
				+ "order by tg.tg_id asc;";
		PreparedStatement pstmtComplete = null;
		
		ArrayList<TaskGroup> taskgroups = new ArrayList<TaskGroup> ();
		
		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, projectId);
			log.info("Get task groups: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			pstmtTotal = sd.prepareStatement(sqlTotal);	
			pstmtTotal.setInt(1, projectId);
			log.info("Get task groups totals: " + pstmtTotal.toString());
			ResultSet rsTotal = pstmtTotal.executeQuery();
			
			pstmtComplete = sd.prepareStatement(sqlComplete);	
			pstmtComplete.setInt(1, projectId);
			log.info("Get task groups totals: " + pstmtComplete.toString());
			ResultSet rsComplete = pstmtComplete.executeQuery();
			
			while (rs.next()) {
				TaskGroup tg = new TaskGroup();
				
				tg.tg_id = rs.getInt(1);
				tg.name = rs.getString(2);
				tg.address_params = rs.getString(3);
				tg.p_id = rs.getInt(4);
				tg.rule = rs.getString(5);
				tg.source_s_id = rs.getInt(6);
				
				if(rsTotal.next()) {
					int tg_id = rsTotal.getInt(1);
					if(tg.tg_id == tg_id) {
						tg.totalTasks = rsTotal.getInt(2);
					} else {
						throw new Exception("Total tasks id mismatch");
					}
				}
				
				if(rsComplete.next()) {
					int tg_id = rsComplete.getInt(1);
					if(tg.tg_id == tg_id) {
						tg.completeTasks = rsComplete.getInt(2);
					} else {
						throw new Exception("Total tasks id mismatch");
					}
				}
				taskgroups.add(tg);
			}
			

		} catch(Exception e) {
			throw(e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtTotal != null) {pstmtTotal.close();} } catch (SQLException e) { }
			try {if (pstmtComplete != null) {pstmtComplete.close();} } catch (SQLException e) {	}
		}

		return taskgroups;
	}
	
	/*
	 * Get  details for a task group
	 */
	public TaskGroup getTaskGroupDetails(Connection sd, int tgId) throws Exception {
		
		String sql = "select tg_id, name, address_params, p_id, rule, source_s_id "
				+ "from task_group where tg_id = ?;";
		PreparedStatement pstmt = null;
		
		TaskGroup tg = new TaskGroup ();
		
		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, tgId);
			log.info("Get task group details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			
			
			if (rs.next()) {
				
				tg.tg_id = rs.getInt(1);
				tg.name = rs.getString(2);
				tg.address_params = rs.getString(3);
				tg.p_id = rs.getInt(4);
				tg.rule = rs.getString(5);
				tg.source_s_id = rs.getInt(6);

			}
			

		} catch(Exception e) {
			throw(e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		
		}

		return tg;
	}
	
	/*
	 * Get the tasks for the specified task group
	 */
	public TaskListGeoJson getTasks(Connection sd, 
			int taskGroupId, 
			boolean completed,
			int userId) throws Exception {
		
		String sql = "select t.id as t_id, "
				+ "t.type as type,"
				+ "t.geo_type as geo_type,"
				+ "t.title as name,"
				+ "t.schedule_at as schedule_at,"
				+ "t.schedule_finish as schedule_finish,"
				+ "t.schedule_at + interval '1 hour' as default_finish,"
				+ "t.location_trigger as location_trigger,"
				+ "t.update_id as update_id,"
				+ "t.initial_data as initial_data,"
				+ "t.address as address,"
				+ "t.guidance as guidance,"
				+ "t.email as email,"
				+ "t.repeat as repeat,"
				+ "t.repeat_count as repeat_count,"
				+ "t.url as url,"
				+ "s.s_id as form_id,"
				+ "s.display_name as form_name,"
				+ "s.blocked as blocked,"
				+ "a.id as a_id,"
				+ "a.status as status,"
				+ "u.id as assignee,"
				+ "u.name as assignee_name,"
				+ "u.ident as assignee_ident "
				+ "from tasks t "
				+ "join survey s "
				+ "on t.form_id = s.s_id "
				+ "left outer join assignments a "
				+ "on a.task_id = t.id " 
				+ "left outer join users u "
				+ "on a.assignee = u.id "
				+ "where t.tg_id = ? "
				+ "order by t.schedule_at asc;";
		PreparedStatement pstmt = null;
		
		String sqlPoint = "select ST_AsGeoJSON(geo_point), ST_AsText(geo_point) from tasks where id = ?;";
		PreparedStatement pstmtPoint = null;
		String sqlPoly = "select ST_AsGeoJSON(geo_polygon) from tasks where id = ?;";
		PreparedStatement pstmtLine = null;
		String sqlLine = "select ST_AsGeoJSON(geo_linestring) from tasks where id = ?;";
		PreparedStatement pstmtPoly = null;

		TaskListGeoJson tl = new TaskListGeoJson();
		
		try {
			// Prepare geo queries
			pstmtPoint = sd.prepareStatement(sqlPoint);
			pstmtLine = sd.prepareStatement(sqlLine);
			pstmtPoly = sd.prepareStatement(sqlPoly);
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, taskGroupId);
			log.info("Get tasks: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			JsonParser parser = new JsonParser();
			while (rs.next()) {
				
				String status = rs.getString("status");
				if(status == null) {
					status = "new";
				}
				
				int assignee = rs.getInt("assignee"); 
				
				// Ignore any tasks that are not required
				if(!completed && (status.equals("submitted") || status.equals("complete"))) {
					continue;
				}
				if(userId > 0 && userId != assignee) {	// Assigned to a specific user only
					continue;
				}
				if(userId < 0 && assignee > 0) {		// Unassigned only
					continue;
				}
				
				TaskFeature tf = new TaskFeature();
				tf.properties = new TaskProperties();
				
				tf.properties.id = rs.getInt("t_id");
				tf.properties.type = rs.getString("type");
				tf.properties.name = rs.getString("name");
				tf.properties.from = rs.getTimestamp("schedule_at");
				tf.properties.to = rs.getTimestamp("schedule_finish");
				if(tf.properties.to == null) {
					tf.properties.to = rs.getTimestamp("default_finish");
				}
				tf.properties.status = status;	
				tf.properties.form_id = rs.getInt("form_id");
				tf.properties.form_name = rs.getString("form_name");
				tf.properties.url = rs.getString("url");
				tf.properties.blocked = rs.getBoolean("blocked");
				tf.properties.assignee = rs.getInt("assignee");
				tf.properties.assignee_name = rs.getString("assignee_name");
				tf.properties.assignee_ident = rs.getString("assignee_ident");
				tf.properties.location_trigger = rs.getString("location_trigger");
				tf.properties.update_id = rs.getString("update_id");
				tf.properties.initial_data = rs.getString("initial_data");
				tf.properties.address = rs.getString("address");
				tf.properties.guidance = rs.getString("guidance");
				tf.properties.email = rs.getString("email");
				tf.properties.repeat = rs.getBoolean("repeat");
				tf.properties.repeat_count = rs.getInt("repeat_count");
				
				// Add geometry
				String geo_type = rs.getString("geo_type");
				ResultSet rsGeo = null;
				if(geo_type != null) {
					if(geo_type.equals("POINT")) {
						pstmtPoint.setInt(1, tf.properties.id);
						rsGeo = pstmtPoint.executeQuery();
					} else if (geo_type.equals("POLYGON")) {
						pstmtPoly.setInt(1, tf.properties.id);
						rsGeo = pstmtPoly.executeQuery();
					} else if (geo_type.equals("LINESTRING")) {
						pstmtLine.setInt(1, tf.properties.id);
						rsGeo = pstmtLine.executeQuery();
					}
				}
				if(rsGeo != null && rsGeo.next()) {
					tf.geometry = parser.parse(rsGeo.getString(1)).getAsJsonObject();	
					if(geo_type.equals("POINT")) {
						tf.properties.location = rsGeo.getString(2);
					}
				}
			
				
				tl.features.add(tf);
			}
			

		} catch(Exception e) {
			throw(e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		
		}

		return tl;
	}
	
	/*
	 * Save the tasks for the specified task group
	 */
	public void writeTaskList(Connection sd, 
			TaskListGeoJson tl,
			int pId,
			int tgId,
			String urlPrefix,
			boolean updateResources,
			int oId) throws Exception {
		
		HashMap<String, String> userIdents = new HashMap<>();
		
		ArrayList<TaskFeature> features = tl.features;
		for(int i = 0; i < features.size(); i++) {
			TaskFeature tf = features.get(i);
			writeTask(sd, pId, tgId, tf, urlPrefix, updateResources, oId);
			if(tf.properties.assignee_ident != null) {
				userIdents.put(tf.properties.assignee_ident, tf.properties.assignee_ident);
			}
		}
		
		// Create a notification to alert the new user of the change to the task details
		for(String user : userIdents.keySet()) {
			MessagingManager mm = new MessagingManager();
			mm.userChange(sd, user);
		}	
	
	}
	
	/*
	 * Get the current locations available for an organisation
	 */
	public ArrayList<Location>  getLocations(Connection sd, 
			int oId) throws SQLException {
		
		String sql = "select id, locn_group, locn_type, uid, name from locations where o_id = ? order by id asc;";
		PreparedStatement pstmt = null;
		ArrayList<Location> locations = new ArrayList<Location> ();

		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, oId);
			
			log.info("Get locations: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				Location locn = new Location();
				
				locn.id = rs.getInt(1);
				locn.group = rs.getString(2);
				locn.type = rs.getString(3);
				locn.uid = rs.getString(4);
				locn.name = rs.getString(5);
				
				locations.add(locn);
			}
			

		} catch(Exception e) {
			throw(e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	
		return locations;
		
	}
	
	/*
	 * Save a list of locations replacing the existing ones
	 */
	public void saveLocations(Connection sd, 
			ArrayList<Location> tags,
			int oId) throws SQLException {
		
	
		String sqlDelete = "delete from locations where o_id = ?;";
		PreparedStatement pstmtDelete = null;
		
		String sql = "insert into locations (o_id, locn_group, locn_type, uid, name) values (?, ?, ?, ?, ?);";
		PreparedStatement pstmt = null;

		try {
			
			sd.setAutoCommit(false);
			
			// Remove existing data
			pstmtDelete = sd.prepareStatement(sqlDelete);
			pstmtDelete.setInt(1, oId);
			pstmtDelete.executeUpdate();
			
			// Add new data
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, oId);
			for(int i = 0; i < tags.size(); i++) {
				
				Location t = tags.get(i);
	
				pstmt.setString(2, t.group);
				pstmt.setString(3, t.type);
				pstmt.setString(4, t.uid);
				pstmt.setString(5, t.name);
			
				pstmt.executeUpdate();
			}
			sd.commit();
		} catch(Exception e) {
			sd.rollback();
			throw(e);
		} finally {
			sd.setAutoCommit(true);
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtDelete != null) {pstmtDelete.close();} } catch (SQLException e) {	}
		}
	
		
	}
	
	
	/*
	 * Check the task group rules and add any new tasks based on this submission
	 */
	public void updateTasksForSubmission(Connection sd, 
			Connection cResults,
			int sId, 
			String hostname,
			String instanceId,
			int pId) throws Exception {
		
		String sqlGetRules = "select tg_id, rule from task_group where source_s_id = ?;";
		PreparedStatement pstmtGetRules = null;
		
		try {
			
			// Remove existing data
			pstmtGetRules = sd.prepareStatement(sqlGetRules);
			pstmtGetRules.setInt(1, sId);

			ResultSet rs = pstmtGetRules.executeQuery();
			while(rs.next()) {
					
				int tgId = rs.getInt(1);
				AssignFromSurvey as = new Gson().fromJson(rs.getString(2), AssignFromSurvey.class);

				log.info("userevent: matching rule: " + as.task_group_name + " for survey: " + sId);	// For log
				
				/*
				 * Check filter to see if this rule should be fired
				 * In addition avoid permanent loops of tasks being reassigned after completion
				 *   Don't fire if form to be updated is the same one that has been submitted 
				 */
				boolean fires = false;
				String rule = null;
				// 
				if(as.update_results 
						&& as.source_survey_id == as.target_survey_id) {
					log.info("Rule not fired due to circular reference");
				} else {
					if(as.filter != null) {
						rule = testRule();		// TODO
						if(rule != null) {
							fires = true;
						}
					} else {
						fires = true;
					}
				}
				if(fires) {
					log.info("userevent: rule fires: " + (as.filter == null ? "no filter" : "yes filter") + " for survey: " + sId);
				} else {
					log.info("rule did not fire");
				}
				if(fires) {
					/*
					 * Get data from new submission
					 */
					TaskInstanceData tid = getTaskInstanceData(sd, cResults, sId, instanceId);
					
					/*
					 * Write to the database
					 */
					writeTaskCreatedFromSurveyResults(sd, as, hostname, tgId, pId, sId, tid, instanceId);
				}
			}
		
		} finally {
			
			try {if (pstmtGetRules != null) {pstmtGetRules.close();} } catch (SQLException e) {	}
	
		}
	
		
	}
	
	/*
	 * Return the criteria for firing this rule
	 */
	private String testRule() {
		return null;
	}
	
	/*
	 * Write the task into the task table
	 */
	public void writeTaskCreatedFromSurveyResults(Connection sd,
			AssignFromSurvey as,
			String hostname,
			int tgId,
			int pId,
			int sId,
			TaskInstanceData tid,			// data from submission
			String instanceId	
			) throws Exception {
		
		String insertSql1 = "insert into tasks (" +
				"p_id, " +
				"tg_id, " +
				"type, " +
				"title, " +
				"form_id, " +
				"url, " +
				"geo_type, ";
				
		String insertSql2 =	
				"initial_data,"
				+ "update_id,"
				+ "address,"
				+ "schedule_at,"
				+ "location_trigger,"
				+ "guidance) "
			+ "values ("
				+ "?, " 
				+ "?, " 
				+ "'xform', "
				+ "?,"
				+ "?,"
				+ "?,"
				+ "?,"	
				+ "ST_GeomFromText(?, 4326), "
				+ "?,"
				+ "?,"
				+ "?,"
				+ "now() + interval '7 days',"  // Schedule for 1 week (TODO allow user to set)
				+ "?,"
				+ "?);";	
		
		String assignSQL = "insert into assignments (assignee, status, task_id) values (?, ?, ?);";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtAssign = sd.prepareStatement(assignSQL);
		
		String title = as.project_name + " : " + as.survey_name;
		String location = tid.location;
		
		try {

			String targetSurveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			String formUrl = "http://" + hostname + "/formXML?key=" + targetSurveyIdent;
			String geoType = null;
			String sql = null;
			String initial_data_url = null;
			String targetInstanceId = null;
			
			/*
			 * Set data to be update
			 */
			if(as.update_results) {
				initial_data_url = "http://" + hostname + "/instanceXML/" + 
						targetSurveyIdent + "/0?key=prikey&keyval=" + tid.prikey;					// deprecated
				targetInstanceId = instanceId;										// New way to identify existing records to be updated
			}
			
			/*
			 * Location
			 */
			if(location == null) {
				location = "POINT(0 0)";
			} else if(location.startsWith("LINESTRING")) {
				log.info("Starts with linestring: " + tid.location.split(" ").length);
				if(location.split(" ").length < 3) {	// Convert to point if there is only one location in the line
					location = location.replaceFirst("LINESTRING", "POINT");
				}
			}	 

			if(location.startsWith("POINT")) {
				sql = insertSql1 + "geo_point," + insertSql2;
				geoType = "POINT";
			} else if(location.startsWith("POLYGON")) {
				sql = insertSql1 + "geo_polygon," + insertSql2;
				geoType = "POLYGON";
			} else if(location.startsWith("LINESTRING")) {
				sql = insertSql1 + "geo_linestring," + insertSql2;
				geoType = "LINESTRING";
			} else {
				throw new Exception ("Unknown location type: " + location);
			}
			
			
			/*
			 * Write the task to the database
			 */

			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			
			pstmt.setInt(1, pId);
			pstmt.setInt(2,  tgId);
			pstmt.setString(3,  title);
			pstmt.setInt(4, as.target_survey_id);
			pstmt.setString(5, formUrl);
			pstmt.setString(6, geoType);
			pstmt.setString(7, location);
			pstmt.setString(8, initial_data_url);	
			pstmt.setString(9, targetInstanceId);
			pstmt.setString(10, tid.address);
			pstmt.setString(11, tid.locationTrigger);
			pstmt.setString(12, tid.address);	// Write the address into guidance
			
			log.info("Create a new task: " + pstmt.toString());
			pstmt.executeUpdate();
			
			/*
			 * Assign the user to the new task
			 */
			if(as.user_id > 0) {
				
				ResultSet keys = pstmt.getGeneratedKeys();
				if(keys.next()) {
					int taskId = keys.getInt(1);

					pstmtAssign.setInt(1, as.user_id);
					pstmtAssign.setString(2, "accepted");
					pstmtAssign.setInt(3, taskId);
					
					log.info("Assign user to task:" + pstmtAssign.toString());
					
					pstmtAssign.executeUpdate();
				}
				

		
			}
			
		} finally {
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
			if(pstmtAssign != null) try {	pstmtAssign.close(); } catch(SQLException e) {};
		}
	}
	
	/*
	 * Get the instance data from a submission that is relevant for assigning a task
	 *  location
	 *  address
	 *  location Trigger
	 */
	public TaskInstanceData getTaskInstanceData (Connection sd, Connection cResults, int sId, String instanceId) throws SQLException {
		TaskInstanceData tid = new TaskInstanceData();
		
		/*
		 * Only check the top level form
		 * This differs from the approach taken in AllAssignments.java however I am not sure of the value
		 *  of descending through sub forms in an attempt to get a location.
		 */
		String sqlGetForms = "select f.table_name, f.parentform from form f " +
				"where f.s_id = ? " + 
				"and f.parentform = 0 " +
				"order by f.table_name;";	
		PreparedStatement pstmtGetForms = null; 
		
		String checkGeomSQL = "select count(*) from information_schema.columns where table_name = ? and column_name = 'the_geom'";
		PreparedStatement pstmtCheckGeom = cResults.prepareStatement(checkGeomSQL);
		
		String sql0 = "select prikey from ";
		String sql1 = "select prikey, ST_AsText(the_geom) from ";
		String sql2 = " where instanceid = ?";
		PreparedStatement pstmt = null;
		
		
		try {
			pstmtGetForms = sd.prepareStatement(sqlGetForms);
			pstmtGetForms.setInt(1, sId);
			log.info("Get top level table: " + pstmtGetForms.toString());
			ResultSet rs = pstmtGetForms.executeQuery();
			if(rs.next()) {
				String tableName = rs.getString(1);
				
				/*
				 * Check for a geometry column
				 */
				pstmtCheckGeom.setString(1, tableName);
				ResultSet rsGeom = pstmtCheckGeom.executeQuery();
				String sql = null;
				if(rsGeom.next()) {
					boolean hasGeom = (rsGeom.getInt(1) > 0);
					if(hasGeom) {
						sql = sql1 + tableName + sql2;
					} else {
						sql = sql0 + tableName + sql2;
					}
					pstmt = cResults.prepareStatement(sql);
					pstmt.setString(1, instanceId);
					log.info("Get instance task data: " + pstmt.toString());
					
					ResultSet rsData = pstmt.executeQuery();
					if(rsData.next()) {
						tid.prikey = rsData.getInt(1);
						if(hasGeom) {
							tid.location = rsData.getString(2);
						}
					}
				}

				
			} else {
				log.info("Error: failed to find top level form");
			}
		} finally {
			if(pstmtCheckGeom != null) try {	pstmtCheckGeom.close(); } catch(SQLException e) {};
			if(pstmtGetForms != null) try {	pstmtGetForms.close(); } catch(SQLException e) {};
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
		}
		return tid;
	}
	
	
	/*
	 * Create a new task
	 */
	public void writeTask(Connection sd, int pId, int tgId, 
			TaskFeature tf,
			String urlPrefix,
			boolean updateResources,
			int oId	
			) throws Exception {
		
		String insertSql1 = "insert into tasks (" +
				"p_id, " +
				"tg_id, " +
				"type, " +
				"title, " +
				"form_id, " +
				"url, " +
				"geo_type, ";
				
		String insertSql2 =	
				"initial_data,"
				+ "update_id,"
				+ "address,"
				+ "schedule_at,"
				+ "schedule_finish,"
				+ "location_trigger,"
				+ "repeat,"
				+ "guidance) "
			+ "values ("
				+ "?, "
				+ "?, "
				+ "'xform', "
				+ "?, "
				+ "?, "
				+ "?, "
				+ "?, "	
				+ "ST_GeomFromText(?, 4326), "
				+ "?, "
				+ "?, "
				+ "?,"
				+ "?,"
				+ "?,"
				+ "?,"
				+ "?,"
				+ "?);";	
		PreparedStatement pstmt = null;
		
		String assignSQL = "insert into assignments (assignee, status, task_id) values (?, ?, ?);";
		PreparedStatement pstmtAssign = sd.prepareStatement(assignSQL);
		
		String sqlGetFormIdFromName = "select s_id from survey where display_name = ? and p_id = ? and deleted = 'false'";
		PreparedStatement pstmtGetFormId = sd.prepareStatement(sqlGetFormIdFromName);
		
		String sqlGetAssigneeId = "select u.id from users u, user_project up "
				+ "where u.ident = ? "
				+ "and u.id = up.u_id "
				+ "and up.p_id = ?";
		PreparedStatement pstmtGetAssigneeId = sd.prepareStatement(sqlGetAssigneeId);
		
		String sqlHasLocationTrigger = "select count(*) from locations where o_id = ? and uid = ? and locn_type = 'nfc';";
		PreparedStatement pstmtHasLocationTrigger = null;
		
		String sqlUpdateLocationTrigger = "insert into locations (o_id, locn_group, locn_type, uid, name) values (?, 'tg', 'nfc', ?, ?);";
		PreparedStatement pstmtUpdateLocationTrigger = null;
		
		try {

			// 1. Delete the existing task if it is being updated
			if(tf.properties.id > 0) {
				deleteTask(sd, tf.properties.id, pId);		// Delete the task
			}
			
			// 2. Get the form id, if only the form name is specified
			if(tf.properties.form_id <= 0) {
				if(tf.properties.form_name == null) {
					throw new Exception("Missing form Name");
				} else {
					pstmtGetFormId.setString(1, tf.properties.form_name);
					pstmtGetFormId.setInt(2, pId);
					log.info("Get survey id: " + pstmtGetFormId.toString());
					ResultSet rs = pstmtGetFormId.executeQuery();
					if(rs.next()) {
						tf.properties.form_id = rs.getInt(1);
					} else {
						throw new Exception("Form not found: " + tf.properties.form_name);
					}
				}
			}
			
			/*
			 * 3. If a temporary user is to be created then create the user
			 *   Else Get the assignee id, if only the assignee ident is specified
			 */
			String tempUserId = null;
			if(tf.properties.generate_user) {
				UserManager um = new UserManager();
				tempUserId = "u" + String.valueOf(UUID.randomUUID());
				User u = new User();
				u.ident = tempUserId;
				u.email = tf.properties.email;
				u.name = tf.properties.assignee_name;
				
				// Only allow access to the project used by this task
				u.projects = new ArrayList<Project> ();
				Project p = new Project();
				p.id = pId;
				u.projects.add(p);
				
				// Only allow enum access
				u.groups = new ArrayList<UserGroup> ();
				u.groups.add(new UserGroup(Authorise.ENUM_ID, Authorise.ENUM));
				tf.properties.assignee = um.createTemporaryUser(sd, u, oId);
				
			} else if(tf.properties.assignee <= 0 && tf.properties.assignee_ident != null && tf.properties.assignee_ident.trim().length() > 0) {
				 
				pstmtGetAssigneeId.setString(1, tf.properties.assignee_ident);
				pstmtGetAssigneeId.setInt(2, pId);
				log.info("Get user id: " + pstmtGetAssigneeId.toString());
				ResultSet rs = pstmtGetAssigneeId.executeQuery();
				if(rs.next()) {
					tf.properties.assignee = rs.getInt(1);
				} else {
					throw new Exception("Assignee not found: " + tf.properties.assignee_ident);
				}
			}
			
			
			
			String targetSurveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, tf.properties.form_id);
			String webformUrl = null;
			if(tf.properties.generate_user) {

				webformUrl = urlPrefix + "/webForm/id/" + tempUserId + 
						"/" + targetSurveyIdent;
			} else {
				webformUrl = urlPrefix + "/webForm/" + targetSurveyIdent;
			}
			String geoType = null;
			String sql = null;
			
			/*
			 * 4. Location
			 */
			String location = tf.properties.location;
			if(location == null) {
				location = "POINT(0 0)";
			} else if(location.startsWith("LINESTRING")) {
				log.info("Starts with linestring: " + tf.properties.location.split(" ").length);
				if(location.split(" ").length < 3) {	// Convert to point if there is only one location in the line
					location = location.replaceFirst("LINESTRING", "POINT");
				}
			}	 

			if(location.startsWith("POINT")) {
				sql = insertSql1 + "geo_point," + insertSql2;
				geoType = "POINT";
			} else if(location.startsWith("POLYGON")) {
				sql = insertSql1 + "geo_polygon," + insertSql2;
				geoType = "POLYGON";
			} else if(location.startsWith("LINESTRING")) {
				sql = insertSql1 + "geo_linestring," + insertSql2;
				geoType = "LINESTRING";
			} else {
				throw new Exception ("Unknown location type: " + location);
			}
			
			
			/*
			 * 5. Write the task to the database
			 */
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			
			pstmt.setInt(1, pId);
			pstmt.setInt(2,  tgId);
			pstmt.setString(3,  tf.properties.name);
			pstmt.setInt(4, tf.properties.form_id);
			pstmt.setString(5, webformUrl);
			pstmt.setString(6, geoType);
			pstmt.setString(7, location);
			pstmt.setString(8, tf.properties.initial_data);	
			pstmt.setString(9, tf.properties.update_id);
			pstmt.setString(10, tf.properties.address);
			if(tf.properties.from == null) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.HOUR, 7 * 24);
				tf.properties.from = new Timestamp(cal.getTime().getTime());
			}
			pstmt.setTimestamp(11, tf.properties.from);
				
			pstmt.setTimestamp(12, tf.properties.to);
			pstmt.setString(13, tf.properties.location_trigger);
			pstmt.setBoolean(14, tf.properties.repeat);
			pstmt.setString(15, tf.properties.guidance);
			
			log.info("Insert Tasks: " + pstmt.toString());
			pstmt.executeUpdate();
			
			/*
			 * 6. Assign the user to the new task
			 */
			if(tf.properties.assignee > 0) {
				
				ResultSet keys = pstmt.getGeneratedKeys();
				if(keys.next()) {
					int taskId = keys.getInt(1);

					pstmtAssign = sd.prepareStatement(assignSQL);
					pstmtAssign.setInt(1, tf.properties.assignee);
					pstmtAssign.setString(2, "accepted");
					pstmtAssign.setInt(3, taskId);
					
					log.info("Assign user to task:" + pstmtAssign.toString());
					
					pstmtAssign.executeUpdate();
					
					// Create a notification to alert the new user of the change to the task details
					String userIdent = GeneralUtilityMethods.getUserIdent(sd, tf.properties.assignee);
					MessagingManager mm = new MessagingManager();
					mm.userChange(sd, userIdent);
				}
		
			}
			
			/*
			 * 7. Create a location_trigger resource to match the trigger in the loaded task
			 *    This allows a location trigger to be created when it is loaded with a task from an xls file
			 * 
			 */
			if(updateResources) {
				if(tf.properties.location_trigger != null && tf.properties.location_trigger.trim().length() > 0) {
					pstmtHasLocationTrigger = sd.prepareStatement(sqlHasLocationTrigger);
					pstmtHasLocationTrigger.setInt(1, oId);
					pstmtHasLocationTrigger.setString(2, tf.properties.location_trigger);
					ResultSet rs = pstmtHasLocationTrigger.executeQuery();
					if(rs.next()) {
						int count = rs.getInt(1);
						if(count == 0) {
							pstmtUpdateLocationTrigger = sd.prepareStatement(sqlUpdateLocationTrigger);
							pstmtUpdateLocationTrigger.setInt(1, oId);
							pstmtUpdateLocationTrigger.setString(2,tf.properties.location_trigger);
							pstmtUpdateLocationTrigger.setString(3,tf.properties.name);
							log.info("Adding NFC resource: " + pstmtUpdateLocationTrigger.toString());
							pstmtUpdateLocationTrigger.executeUpdate();
						}
					}
				}
			}
			
			
			
		} finally {
			if(pstmt != null) try {pstmt.close(); } catch(SQLException e) {};
			if(pstmtAssign != null) try {pstmtAssign.close(); } catch(SQLException e) {};
			if(pstmtGetFormId != null) try {pstmtGetFormId.close(); } catch(SQLException e) {};
			if(pstmtGetAssigneeId != null) try {pstmtGetAssigneeId.close(); } catch(SQLException e) {};
			if(pstmtHasLocationTrigger != null) try {pstmtHasLocationTrigger.close(); } catch(SQLException e) {};
			if(pstmtUpdateLocationTrigger != null) try {pstmtUpdateLocationTrigger.close(); } catch(SQLException e) {};
		}
		
	}
	
	/*
	 * Apply an action to multiple tasks
	 */
	public void applyBulkAction(Connection sd, int pId, TaskBulkAction action) throws Exception {
		
		String sqlGetUsers = "select distinct ident from users where temporary = false and id in "
				+ "(select a.assignee from assignments a, tasks t "
				+ "where a.task_id = t.id "
				+ "and t.p_id = ? "
				+ "and a.task_id in ("; 
		
		String sqlUsers = "delete from users where temporary = true and id in "
				+ "(select a.assignee from assignments a, tasks t "
				+ "where a.task_id = t.id "
				+ "and t.p_id = ? "
				+ "and a.task_id in (";  
		
		String deleteSql = "delete from tasks where p_id = ? and id in (";
		String assignSql = "update assignments set assignee = ? "
				+ "where task_id in (select task_id from tasks where p_id = ?) "		// Authorisation
				+ "and task_id in (";
		String sqlGetUnassigned = "select id from tasks "
				+ "where id not in (select task_id from assignments) "
				+ "and id in (";
		String sqlCreateAssignments = "insert into assignments (assignee, status, task_id, assigned_date) "
				+ "values(?, 'accepted', ?, now())";
		String sqlDeleteAssignments = "delete from assignments where task_id in (";
		String whereSql = "";
		

		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetUnassigned = null;
		PreparedStatement pstmtCreateAssignments = null;
		PreparedStatement pstmtDelTempUsers = null;
		PreparedStatement pstmtGetUsers = null;
		
		try {

			if(action.taskIds.size() == 0) {
				throw new Exception("No tasks");
			} 
			
			for(int i = 0; i < action.taskIds.size(); i++) {
				if(i > 0) {
					whereSql += ",";
				}
				whereSql += action.taskIds.get(i).toString();
			}
			whereSql += ")";
			
			// Notify users currently assigned to tasks that are being modified
			MessagingManager mm = new MessagingManager();
			pstmtGetUsers = sd.prepareStatement(sqlGetUsers + whereSql + ")");
			pstmtGetUsers.setInt(1, pId);
			log.info("Notify affected users: " + pstmtGetUsers.toString());
			ResultSet rsNot = pstmtGetUsers.executeQuery();
			while(rsNot.next()) {
				mm.userChange(sd, rsNot.getString(1));
			}
			
			
			if(action.action.equals("delete")) {
				
				// Delete temporary users
				pstmtDelTempUsers = sd.prepareStatement(sqlUsers + whereSql + ")");
				pstmtDelTempUsers.setInt(1, pId);
				log.info("Del temp users created for tasks to be deleted: " + pstmtDelTempUsers.toString());
				pstmtDelTempUsers.executeUpdate();
				
				pstmt = sd.prepareStatement(deleteSql + whereSql);
				pstmt.setInt(1, pId);
				log.info("Bulk update: " + pstmt.toString());
				pstmt.executeUpdate();
				
			} else if(action.action.equals("assign")) {
				
				// Get tasks that have not had an assignment created
				pstmtGetUnassigned = sd.prepareStatement(sqlGetUnassigned + whereSql);
				pstmtCreateAssignments = sd.prepareCall(sqlCreateAssignments);
				log.info("Getting unassigned tasks: " + pstmtGetUnassigned.toString());
				ResultSet rs = pstmtGetUnassigned.executeQuery();
				while (rs.next()) {
					// Create the first assignment for this task
					pstmtCreateAssignments.setInt(1, action.userId);
					pstmtCreateAssignments.setInt(2, rs.getInt(1));
					log.info("Create assignment: " + pstmtCreateAssignments.toString());
					pstmtCreateAssignments.executeUpdate();
				}
				// Update assignments
				if(action.userId >= 0) {
					pstmt = sd.prepareStatement(assignSql + whereSql);
					pstmt.setInt(1,action.userId);
					pstmt.setInt(2, pId);				
					log.info("Bulk update: " + pstmt.toString());
					pstmt.executeUpdate();
				} else {
					pstmt = sd.prepareStatement(sqlDeleteAssignments + whereSql);				
					log.info("Bulk update: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
				// Notify the user who has been assigned the tasks
				String userIdent = GeneralUtilityMethods.getUserIdent(sd, action.userId);
				mm.userChange(sd, userIdent);
			}
				
		} finally {
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
			if(pstmtGetUnassigned != null) try {pstmtGetUnassigned.close(); } catch(SQLException e) {};
			if(pstmtCreateAssignments != null) try {pstmtCreateAssignments.close(); } catch(SQLException e) {};	
			if(pstmtDelTempUsers != null) try {pstmtDelTempUsers.close(); } catch(SQLException e) {};	
			if(pstmtGetUsers != null) try {pstmtGetUsers.close(); } catch(SQLException e) {};	
		}
		
	}
	
	/*
	 * Update a task start date and time
	 */
	public void updateWhen(Connection sd, int pId, int taskId, 
			Timestamp from,
			Timestamp to) throws Exception {
		
		String sql = "update tasks set schedule_at = ?, schedule_finish = ? where p_id = ? and id = ?;";
		PreparedStatement pstmt = null;
		
		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setTimestamp(1, from);
			pstmt.setTimestamp(2, to);
			pstmt.setInt(3, pId);
			pstmt.setInt(4, taskId);
		
			log.info("Update start date and time: " + pstmt.toString());
			pstmt.executeUpdate();
			
			// Create a notification of the change
			MessagingManager mm = new MessagingManager();
			mm.taskChange(sd, taskId);
				
		} finally {
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
		
		}		
	}
	
	/*
	 * Delete the tasks in a task group
	 */
	public void deleteTasksInTaskGroup(Connection sd, int tgId) throws SQLException {
		
		String sqlUsers = "delete from users where temporary = true and id in "
				+ "(select assignee from assignments where task_id in "
				+ "(select id from tasks where tg_id = ?))"; 
		PreparedStatement pstmtUsers = null;
		
		String sql = "delete from tasks where tg_id = ?"; 
		PreparedStatement pstmt = null;
		
		String sqlGetUsers = "select distinct ident from users where temporary = false and id in "
				+ "(select assignee from assignments where task_id in "
				+ "(select id from tasks where tg_id = ?))"; 
		PreparedStatement pstmtGetUsers = null;
		
		try {

			// Delete any temporary users created for this task
			pstmtUsers = sd.prepareStatement(sqlUsers);
			pstmtUsers.setInt(1, tgId);
						
			log.info("Delete temporary user: " + pstmtUsers.toString());
			pstmtUsers.executeUpdate();
				
			// Notify users whose task has been deleted
			MessagingManager mm = new MessagingManager();
			pstmtGetUsers = sd.prepareStatement(sqlGetUsers);
			pstmtGetUsers.setInt(1, tgId);
			log.info("Get task users: " + pstmtGetUsers.toString());
			ResultSet rs = pstmtGetUsers.executeQuery();
			while (rs.next()) {
				mm.userChange(sd, rs.getString(1));
			}			
			
			// Delete the task group
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, tgId);
		
			log.info("Delete tasks in task group: " + pstmt.toString());
			pstmt.executeUpdate();
				
		} finally {
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
			if(pstmtUsers != null) try {	pstmtUsers.close(); } catch(SQLException e) {};
			if(pstmtGetUsers != null) try {	pstmtGetUsers.close(); } catch(SQLException e) {};
		}		
	}
	
	/*
	 * Delete an individual task
	 */
	public void deleteTask(Connection sd, int tId, int pId) throws SQLException {
		
		String sqlGetUsers = "select distinct ident from users where temporary = false and id in "
				+ "(select a.assignee from assignments a, tasks t where a.task_id = ? "
				+ "and a.task_id = t.id "
				+ "and t.p_id = ?)"; 
		PreparedStatement pstmtGetUsers = null;
		
		String sqlTempUsers = "delete from users where temporary = true and id in "
				+ "(select a.assignee from assignments a, tasks t where a.task_id = ? "
				+ "and a.task_id = t.id "
				+ "and t.p_id = ?)"; 
		PreparedStatement pstmtTempUsers = null;
		
		String sql = "delete from tasks where id = ? and p_id = ?"; 
		PreparedStatement pstmt = null;
		
		try {
			
			// Delete any temporary users created for this task
			pstmtTempUsers = sd.prepareStatement(sqlTempUsers);
			pstmtTempUsers.setInt(1, tId);
			pstmtTempUsers.setInt(2, pId);
			
			log.info("Delete temporary user: " + pstmtTempUsers.toString());
			pstmtTempUsers.executeUpdate();
			
			// Notify users whose task has been deleted
			MessagingManager mm = new MessagingManager();
			pstmtGetUsers = sd.prepareStatement(sqlGetUsers);
			pstmtGetUsers.setInt(1, tId);
			pstmtGetUsers.setInt(2, pId);
			
			log.info("Get task users: " + pstmtGetUsers.toString());
			ResultSet rs = pstmtGetUsers.executeQuery();
			while (rs.next()) {
				mm.userChange(sd, rs.getString(1));
			}			
			
			// Delete the task
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, tId);
			pstmt.setInt(2, pId);
		
			log.info("Delete task: " + pstmt.toString());
			pstmt.executeUpdate();
				
		} finally {
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
			if(pstmtTempUsers != null) try {	pstmtTempUsers.close(); } catch(SQLException e) {};
			if(pstmtGetUsers != null) try {	pstmtGetUsers.close(); } catch(SQLException e) {};
		}		
	}
}


