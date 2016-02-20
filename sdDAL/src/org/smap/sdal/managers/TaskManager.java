package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

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
	public void updateTasksForSubmission(Connection sd, int sId) throws Exception {
		
		String sqlGetRules = "select rule from task_group where source_s_id = ?;";
		PreparedStatement pstmtGetRules = null;
		
		try {
			
			// Remove existing data
			pstmtGetRules = sd.prepareStatement(sqlGetRules);
			pstmtGetRules.setInt(1, sId);
			
			System.out.println("SQL get task rules: " + pstmtGetRules.toString());
			ResultSet rs = pstmtGetRules.executeQuery();
			while(rs.next()) {
				System.out.println("Rule: " + rs.getString(1));
				AssignFromSurvey as = new Gson().fromJson(rs.getString(1), AssignFromSurvey.class);
				System.out.println("userevent: matching rule: " + as.task_group_name + " for survey: " + sId);
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
				System.out.println("userevent: rule fires: " + (as.filter == null ? "no filter" : "yes filter") + " for survey: " + sId);

				if(fires) {
					writeTask(as);
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
	private void writeTask(AssignFromSurvey as) {
		String insertSql1 = "insert into tasks (" +
				"p_id, " +
				"tg_id, " +
				"type, " +
				"title, " +
				"form_id, " +
				"url, " +
				"geo_type, ";
				
		String insertSql2 =	
				"initial_data, " +
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
				"?, " +
				"?," +
				"now() + interval '7 days'," +  // Schedule for 1 week (TODO allow user to set)
				"?);";	
		PreparedStatement pstmtInsertTask = null;
	}
	

}


