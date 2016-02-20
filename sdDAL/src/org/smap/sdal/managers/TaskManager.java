package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.AssignFromSurvey;
import org.smap.sdal.model.Location;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
	
	/*
	 * Save a list of locations replacing the existing ones
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
		
	
		String sqlTruncate = "truncate table locations;";
		PreparedStatement pstmtTruncate = null;
		
		String sql = "insert into locations (o_id, locn_group, locn_type, uid, name) values (?, ?, ?, ?, ?);";
		PreparedStatement pstmt = null;

		try {
			
			sd.setAutoCommit(false);
			
			// Remove existing data
			pstmtTruncate = sd.prepareStatement(sqlTruncate);
			pstmtTruncate.executeUpdate();
			
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
			try {if (pstmtTruncate != null) {pstmtTruncate.close();} } catch (SQLException e) {	}
		}
	
		
	}
	
	
	/*
	 * Check the task group rules and add any new tasks based on this submission
	 */
	public void updateTasksForSubmission(Connection sd, int sId, String hostname) throws Exception {
		
		String sqlGetRules = "select tg_id, p_id, rule from task_group where source_s_id = ?;";
		PreparedStatement pstmtGetRules = null;
		
		try {
			
			// Remove existing data
			pstmtGetRules = sd.prepareStatement(sqlGetRules);
			pstmtGetRules.setInt(1, sId);
			
			System.out.println("SQL get task rules: " + pstmtGetRules.toString());
			ResultSet rs = pstmtGetRules.executeQuery();
			while(rs.next()) {
					
				int tgId = rs.getInt(1);
				int pId = rs.getInt(2);
				AssignFromSurvey as = new Gson().fromJson(rs.getString(3), AssignFromSurvey.class);

				log.info("userevent: matching rule: " + as.task_group_name + " for survey: " + sId);	// For log
				
				/*
				 * Check filter to see if this rule should be fired
				 */
				boolean fires = false;
				String rule = null;
				if(as.filter != null) {
					rule = testRule();
					if(rule != null) {
						fires = true;
					}
				} else {
					fires = true;
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
					// TODO
					String location = null;
					String instanceId = "1";
					String address = null;
					String locationTrigger = null;
					
					/*
					 * Write to the database
					 */
					writeTask(sd, as, hostname, tgId, pId, sId, location, instanceId, address, locationTrigger);
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
	public void writeTask(Connection sd,
			AssignFromSurvey as,
			String hostname,
			int tgId,
			int pId,
			int sId,
			String location,			// data from submission
			String instanceId,			// data from submission
			String address,				// data from submission
			String locationTrigger		// data from task set up
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
				"update_id," +
				"address," +
				"schedule_at," +
				"location_trigger) " +
			"values (" +
				"?, " + 
				"?, " + 
				"'xform', " +
				"?, " +
				"?, " +
				"?, " +
				"?, " +	
				"ST_GeomFromText(?, 4326), " +
				"?, " +
				"?," +
				"now() + interval '7 days'," +  // Schedule for 1 week (TODO allow user to set)
				"?);";	
		
		String assignSQL = "insert into assignments (assignee, status, task_id) values (?, ?, ?);";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtAssign = sd.prepareStatement(assignSQL);
		
		String title = as.project_name + " : " + as.survey_name;
		
		try {

			String formIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			String formUrl = "http://" + hostname + "/formXML?key=" + formIdent;
			String geoType = null;
			String sql = null;
			
			/*
			 * Location
			 */
			if(location == null) {
				location = "POINT(0 0)";
			} else if(location.startsWith("LINESTRING")) {
				log.info("Starts with linestring: " + location.split(" ").length);
				if(location.split(" ").length < 3) {	// Convert to point if there is only one location in the line
					location = location.replaceFirst("LINESTRING", "POINT");
				}
			}	 
;
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
			pstmt.setInt(4, as.form_id);
			pstmt.setString(5, formUrl);
			pstmt.setString(6, geoType);
			pstmt.setString(7, location);
			pstmt.setString(8, instanceId);
			pstmt.setString(9, address);
			pstmt.setString(10, locationTrigger);
			
			System.out.println("Insert Tasks: " + pstmt.toString());
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
	
	

}


