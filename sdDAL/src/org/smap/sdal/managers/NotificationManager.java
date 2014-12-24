package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.model.Notification;
import org.smap.sdal.model.Survey;

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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class NotificationManager {
	
	private static Logger log =
			 Logger.getLogger(NotificationManager.class.getName());
	
	/*
	 * Get all Enabled notifications
	 * Used by Subscriber to do forwarding
	 */
	public ArrayList<Notification> getEnabledNotifications(Connection sd, 
			PreparedStatement pstmt, boolean forward_only) throws SQLException {
		
		ArrayList<Notification> forwards = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user, " +
				"f.target, s.display_name, f.notify_emails, " +
				"f.remote_password " +
				" from forward f " +
				" where f.enabled = 'true' ";
		
		if(forward_only) {
			sql += " and f.target = 'forward'";
		} else {
			sql += " and f.target != 'forward'";
		}
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		resultSet = pstmt.executeQuery();
		addToList(resultSet, forwards, true);
		return forwards;
		
	}
	
	/*
	 * Add a record to the notification table
	 */
	public void addNotification(Connection sd, PreparedStatement pstmt, String user, 
			Notification n) throws Exception {
					
			String sql = "insert into forward(" +
					" s_id, enabled, " +
					" remote_s_id, remote_s_name, remote_host, remote_user, remote_password, notify_emails, target) " +
					" values (?, ?, ?, ?, ?, ?, ?, ?, ?); ";
	
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, n.s_id);
			pstmt.setBoolean(2, n.enabled);
			pstmt.setString(3, n.remote_s_ident);
			pstmt.setString(4, n.remote_s_name);
			pstmt.setString(5, n.remote_host);
			pstmt.setString(6, n.remote_user);
			pstmt.setString(7, n.remote_password);
			pstmt.setString(8, n.notify_emails);
			pstmt.setString(9, n.target);
			pstmt.executeUpdate();
	}
	
	/*
	 * Update a record to the forwarding table
	 */
	public void updateForward(Connection sd, PreparedStatement pstmt, String user, 
			Notification n) throws Exception {
			
		String sql = null;
		if(n.update_password) {
			sql = "update forward set " +
					" s_id = ?, " +
					" enabled = ?, " +
					" remote_s_id = ?, " +
					" remote_s_name = ?, " +
					" remote_host = ?, " +
					" remote_user = ?, " +
					" remote_password = ? " +
					" where id = ?; ";
		} else {
			sql = "update forward set " +
					" s_id = ?, " +
					" enabled = ?, " +
					" remote_s_id = ?, " +
					" remote_s_name = ?, " +
					" remote_host = ?, " +
					" remote_user = ? " +
					" where id = ?; ";
		}
			

		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setInt(1, n.s_id);
		pstmt.setBoolean(2, n.enabled);
		pstmt.setString(3, n.remote_s_ident);
		pstmt.setString(4, n.remote_s_name);
		pstmt.setString(5, n.remote_host);
		pstmt.setString(6, n.remote_user);
		if(n.update_password) {
			pstmt.setString(7, n.remote_password);
			pstmt.setInt(8, n.id);
		} else {
			pstmt.setInt(7, n.id);
		}
		log.info("SQL: " + sql + " id:" + n.id);
		pstmt.executeUpdate();
	}
	
	public boolean isFeedbackLoop(Connection con, String server, Notification n) throws SQLException {
		boolean loop = false;
		
		String remote_host = null;;
		
		String [] hostParts = n.remote_host.split("//");
		remote_host = hostParts[1];
		
		System.out.println("Current server is: " + server + " : " + remote_host);
		
		// Get the ident of the local survey to compare with the remote ident
		PreparedStatement pstmt;
		String sql = "select ident from survey where s_id = ?;";
		pstmt = con.prepareStatement(sql);
		pstmt.setInt(1, n.s_id);
		ResultSet rs = pstmt.executeQuery(); 
		if(rs.next()) {
			String local_ident = rs.getString(1);
			System.out.println("Local ident is: " + local_ident + " : " + n.remote_s_ident);
			if(local_ident != null && local_ident.equals(n.remote_s_ident) && remote_host.equals(server)) {
				loop = true;
			}
		}
		pstmt.close();
		
		return loop;
	}
	
	/*
	 * Get all Forwards that are accessible by the requesting user and in a specific project
	 * Used by Subscriber to do the actual forwarding
	 */
	public ArrayList<Notification> getProjectNotifications(Connection sd, PreparedStatement pstmt,
			String user,
			int projectId) throws SQLException {
		
		ArrayList<Notification> notifications = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user," +
				" f.target, s.display_name, f.notify_emails" +
				" from forward f, survey s, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and s.s_id = f.s_id" +
				" and u.ident = ? " +
				" and s.p_id = ?; ";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setString(1, user);
		pstmt.setInt(2, projectId);
		log.info("Project Forwards: " + sql + " : " + user + " : " + projectId);
		resultSet = pstmt.executeQuery();

		addToList(resultSet, notifications, false);

		return notifications;
		
	}
	
	/*
	 * Delete the notification
	 */
	public void deleteNotification(Connection sd, PreparedStatement pstmt,
			String user,
			int id) throws SQLException {
		
		String sql = "delete from forward where id = ?; ";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setInt(1, id);
		log.info("Delete: " + sql + " : " + id + " : " + id);
		pstmt.executeUpdate();
		
	}

	private void addToList(ResultSet resultSet, ArrayList<Notification> notifications, boolean getPassword) throws SQLException {
		
		while (resultSet.next()) {								

			String remote_s_id = resultSet.getString(4);
			Notification n = new Notification();
			n.id = resultSet.getInt(1);
			n.s_id = resultSet.getInt(2);
			n.enabled = resultSet.getBoolean(3);
			n.remote_s_ident = remote_s_id;
			n.remote_s_name = resultSet.getString(5);
			n.remote_host = resultSet.getString(6);
			n.remote_user = resultSet.getString(7);
			n.target = resultSet.getString(8);
			n.s_name = resultSet.getString(9);
			n.notify_emails = resultSet.getString(10);
			if(getPassword) {
				n.remote_password = resultSet.getString(11);
			}
			
			notifications.add(n);
			
		} 
	}
}


