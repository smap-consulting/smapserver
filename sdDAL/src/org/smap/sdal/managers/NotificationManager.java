package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;

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
				"f.target, s.display_name, f.notify_details, " +
				"f.remote_password " +
				" from forward f, survey s " +
				" where f.s_id = s.s_id " +
				" and f.enabled = 'true' ";
		
		if(forward_only) {
			sql += " and f.target = 'forward' and f.remote_host is not null";
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
					" remote_s_id, remote_s_name, remote_host, remote_user, remote_password, notify_details, target) " +
					" values (?, ?, ?, ?, ?, ?, ?, ?, ?); ";
	
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String notifyDetails = gson.toJson(n.notifyDetails);
			
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, n.s_id);
			pstmt.setBoolean(2, n.enabled);
			pstmt.setString(3, n.remote_s_ident);
			pstmt.setString(4, n.remote_s_name);
			pstmt.setString(5, n.remote_host);
			pstmt.setString(6, n.remote_user);
			pstmt.setString(7, n.remote_password);
			pstmt.setString(8, notifyDetails);
			pstmt.setString(9, n.target);
			pstmt.executeUpdate();
	}
	
	/*
	 * Update a record to the forwarding table
	 */
	public void updateNotification(Connection sd, PreparedStatement pstmt, String user, 
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
					" notify_details = ?, " +
					" target = ?, " +
					" remote_password = ? " +
					" where id = ?; ";
		} else {
			sql = "update forward set " +
					" s_id = ?, " +
					" enabled = ?, " +
					" remote_s_id = ?, " +
					" remote_s_name = ?, " +
					" remote_host = ?, " +
					" remote_user = ?, " +
					" notify_details = ?, " +
					" target = ? " +
					" where id = ?; ";
		}
			

		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		String notifyDetails = gson.toJson(n.notifyDetails);
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setInt(1, n.s_id);
		pstmt.setBoolean(2, n.enabled);
		pstmt.setString(3, n.remote_s_ident);
		pstmt.setString(4, n.remote_s_name);
		pstmt.setString(5, n.remote_host);
		pstmt.setString(6, n.remote_user);
		pstmt.setString(7, notifyDetails);
		pstmt.setString(8, n.target);
		if(n.update_password) {
			pstmt.setString(9, n.remote_password);
			pstmt.setInt(10, n.id);
		} else {
			pstmt.setInt(9, n.id);
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
	 * Get all Notifications that are accessible by the requesting user and in a specific project
	 */
	public ArrayList<Notification> getProjectNotifications(Connection sd, PreparedStatement pstmt,
			String user,
			int projectId) throws SQLException {
		
		ArrayList<Notification> notifications = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user," +
				" f.target, s.display_name, f.notify_details" +
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
			String notifyDetailsString = resultSet.getString(10);
			n.notifyDetails = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
			if(getPassword) {
				n.remote_password = resultSet.getString(11);
			}
			
			notifications.add(n);
			
		} 
	}
	
	/*
	 * Apply any notification for the passed in submission
	 */
	public void notifyForSubmission(
			Connection sd, 
			Connection cResults,
			PreparedStatement pstmtGetUploadEvent, 
			PreparedStatement pstmtGetNotifications, 
			PreparedStatement pstmtUpdateUploadEvent, 
			PreparedStatement pstmtNotificationLog,
			int ue_id,
			String remoteUser,
			String serverName) throws SQLException, Exception {
		/*
		 * 1. Get notifications that may apply to the passed in upload event.
		 * 		Notifications can be re-applied so the the notifications flag in upload event is ignored
		 * 2. Apply any additional filtering
		 * 3. Invoke each notification
		 *    3a) Create document
		 *    3b) Send document to target
		 *    3c) Update notification log
		 * 4. Update upload event table to show that notifications have been applied
		 */
		
		ResultSet rs = null;
		ResultSet rsNotifications = null;
		int s_id = 0;
		String ident = null;
		String instanceId = null;
		
		System.out.println("notifyForSubmission:: " + ue_id);
				
		String sqlGetUploadEvent = "select ue.s_id, ue.ident, ue.instanceid " +
				" from upload_event ue " +
				" where ue.ue_id = ?;";
		try {if (pstmtGetUploadEvent != null) { pstmtGetUploadEvent.close();}} catch (SQLException e) {}
		pstmtGetUploadEvent = sd.prepareStatement(sqlGetUploadEvent);
		
		String sqlGetNotifications = "select n.target, n.notify_details " +
				" from forward n " +
				" where n.s_id = ? " + 
				" and n.target != 'forward'";
		try {if (pstmtGetNotifications != null) { pstmtGetNotifications.close();}} catch (SQLException e) {}
		pstmtGetNotifications = sd.prepareStatement(sqlGetNotifications);
		
		String sqlUpdateUploadEvent = "update upload_event set notifications_applied = 'true' where ue_id = ?; ";
		try {if (pstmtUpdateUploadEvent != null) { pstmtUpdateUploadEvent.close();}} catch (SQLException e) {}
		pstmtUpdateUploadEvent = sd.prepareStatement(sqlUpdateUploadEvent);
		
		String sqlNotificationLog = "insert into notification_log " +
				"(o_id, notify_details, status, status_details, event_time) " +
				"values( ?, ?, ?, ?, now()); ";
		try {if (pstmtNotificationLog != null) { pstmtNotificationLog.close();}} catch (SQLException e) {}
		pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);

		// Get the admin email
		String adminEmail = UtilityMethodsEmail.getAdminEmail(sd, remoteUser);
		int o_id = GeneralUtilityMethods.getOrganisationId(sd, remoteUser);
		System.out.println("Organisation for user " + remoteUser + " is " + o_id);
		
		
		// Get the details from the upload event
		pstmtGetUploadEvent.setInt(1, ue_id);
		rs = pstmtGetUploadEvent.executeQuery();
		if(rs.next()) {
			s_id = rs.getInt(1);
			ident = rs.getString(2);
			instanceId = rs.getString(3);
			
			System.out.println("Get notifications:: " + s_id + " : " + ident + " : " + instanceId);
			pstmtGetNotifications.setInt(1, s_id);
			rsNotifications = pstmtGetNotifications.executeQuery();
			while(rsNotifications.next()) {
				System.out.println("++++++ Notification: " + rsNotifications.getString(1) + " " + rsNotifications.getString(2));
				String target = rsNotifications.getString(1);
				String notifyDetailsString = rsNotifications.getString(2);
				NotifyDetails nd = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
				
				/*
				 * Create the document
				 * TODO: Allow creation of PDFs, reports, aggregations
				 */
				String docUrl = "/webForm/" + ident +
						"&datakey=instanceid&datakeyvalue=" + instanceId;
				
				/*
				 * Send document to target
				 */
				String status = "success";				// Notification log
				String notify_details = null;			// Notification log
				String error_details = null;			// Notification log
				if(target.equals("email")) {
					String smtp_host = UtilityMethodsEmail.getSmtpHost(sd, null, remoteUser);
					if(smtp_host != null && smtp_host.trim().length() > 0) {
						ArrayList<String> emailList = null;
						if(nd.emailQuestion > 0) {
							emailList = GeneralUtilityMethods.getResponseForQuestion(cResults, s_id, nd.emailQuestion, instanceId);
						} else {
							emailList = new ArrayList<String> ();
						}
						
						for(String email : nd.emails) {
							emailList.add(email);
						}
						// TODO validate email list
						String emails = "";
						for(String email : emailList) {
							if(emails.length() > 0) {
								emails += ";";
							}
							emails += email;
						}
						String subject = "Smap Notification";
						if(nd.subject != null) {
							subject = nd.subject;
						}
						String from = "smap";
						if(nd.from != null) {
							from = nd.from;
						}
						
						notify_details = "Sending email to: " + emails + " containing link " + docUrl;
						
						System.out.println("+++ emailing to: " + emails + " : " + docUrl + 
								" from: " + from + 
								" subject: " + subject +
								" smtp_host: " + smtp_host);
						try {
							UtilityMethodsEmail.sendEmail(
									emails, 
									null, 
									"notify", 
									subject, 	
									from,		
									null, 
									null, 
									null, 
									docUrl, 
									adminEmail, 
									smtp_host,
									serverName);
						} catch(Exception e) {
							status = "error";
							error_details = e.getMessage();
						}
					} else {
						status = "error";
						error_details = "smtp_host not set";
						System.out.println("Error: Attempt to do email notification but email server not set");
					}
					
				} else {
					status = "error";
					error_details = "Invalid target" + target;
					System.out.println("Error: Invalid target" + target);
				}
				
				// Write log message
				pstmtNotificationLog.setInt(1, o_id);
				pstmtNotificationLog.setString(2, notify_details);
				pstmtNotificationLog.setString(3, status);
				pstmtNotificationLog.setString(4, error_details);
				pstmtNotificationLog.executeUpdate();
			}
			
			/*
			 * Update upload event to record application of notifications
			 */
			pstmtUpdateUploadEvent.setInt(1, ue_id);
			pstmtUpdateUploadEvent.executeUpdate();
			
		}
	}
}


