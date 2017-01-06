package org.smap.sdal.managers;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Organisation;

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
	
	LogManager lm = new LogManager();		// Application log
	
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
	
	/*
	 * Check that the server is not forwarding to the same survey on the same server
	 */
	public boolean isFeedbackLoop(Connection con, String server, Notification n) throws SQLException {
		boolean loop = false;
		
		String remote_host = null;;
		
		String [] hostParts = n.remote_host.split("//");
		remote_host = hostParts[1];
		
		log.info("Checking for forwardign feedback loop. Current server is: " + server + " : " + remote_host);
		
		// Get the ident of the local survey to compare with the remote ident
		PreparedStatement pstmt;
		String sql = "select ident from survey where s_id = ?;";
		pstmt = con.prepareStatement(sql);
		pstmt.setInt(1, n.s_id);
		ResultSet rs = pstmt.executeQuery(); 
		if(rs.next()) {
			String local_ident = rs.getString(1);
			log.info("Local ident is: " + local_ident + " : " + n.remote_s_ident);
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
				" and s.p_id = ? " + 
				" and s.deleted = 'false'";
		
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
		log.info("Delete: " + pstmt.toString());
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
			PreparedStatement pstmtGetNotifications, 
			PreparedStatement pstmtUpdateUploadEvent, 
			PreparedStatement pstmtNotificationLog,
			int ue_id,
			String remoteUser,
			String scheme,
			String serverName,
			String basePath,
			int sId,
			String ident,
			String instanceId,
			int pId) throws SQLException {
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
		
		ResultSet rsNotifications = null;		
		
		log.info("notifyForSubmission:: " + ue_id);
		
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
				"(o_id, p_id, s_id, notify_details, status, status_details, event_time) " +
				"values( ?, ?,?, ?, ?, ?, now()); ";
		try {if (pstmtNotificationLog != null) { pstmtNotificationLog.close();}} catch (SQLException e) {}
		pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);

		// Localisation
		Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, remoteUser);
		Locale locale = new Locale(organisation.locale);
		ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		
		// Time Zone
		int utcOffset = 0;	
		LocalDateTime dt = LocalDateTime.now();
		if(organisation.timeZone != null) {
			try {
				ZoneId zone = ZoneId.of(organisation.timeZone);
			    ZonedDateTime zdt = dt.atZone(zone);
			    ZoneOffset offset = zdt.getOffset();
			    utcOffset = offset.getTotalSeconds() / 60;
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		pstmtGetNotifications.setInt(1, sId);
		log.info("Get notifications:: " + pstmtGetNotifications.toString());
		rsNotifications = pstmtGetNotifications.executeQuery();
		while(rsNotifications.next()) {
			boolean writeToMonitor = true;
			log.info("++++++ Notification: " + rsNotifications.getString(1) + " " + rsNotifications.getString(2));
			String target = rsNotifications.getString(1);
			String notifyDetailsString = rsNotifications.getString(2);
			NotifyDetails nd = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
			
			/*
			 * Create the document
			 */
			String docURL = null;
			String filePath = null;
			String filename = "instance";
			String logContent = null;
			if(nd.attach != null) {
				System.out.println("Attaching link to email: " + nd.attach);
				if(nd.attach.startsWith("pdf")) {
					docURL = null;
					
					// Create temporary PDF and get file name
					filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID()) + ".pdf";
					FileOutputStream outputStream = null;
					try {
						outputStream = new FileOutputStream(filePath); 
					} catch (Exception e) {
						log.log(Level.SEVERE, "Error creating temporary PDF file", e);
					}
					PDFSurveyManager pm = new PDFSurveyManager();
					
					// Split orientation from nd.attach
					boolean landscape = false;
					if(nd.attach != null && nd.attach.startsWith("pdf")) {
						landscape = nd.attach.equals("pdf_landscape");
						nd.attach = "pdf";
					}

					filename = pm.createPdf(
							sd,
							cResults,
							outputStream,
							basePath, 
							remoteUser,
							"none", 
							sId, 
							instanceId,
							null,
							landscape,
							null,
							utcOffset);
					
					logContent = filePath;
					
				} else {
					docURL = "/webForm/" + ident +
							"?datakey=instanceid&datakeyvalue=" + instanceId;
					logContent = docURL;
				}
			}
				
			/*
			 * Send document to target
			 */
			String status = "success";				// Notification log
			String notify_details = null;			// Notification log
			String error_details = null;			// Notification log
			if(target.equals("email")) {
				EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, remoteUser);
				if(emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {
					ArrayList<String> emailList = null;
					log.info("Email question: " + nd.emailQuestion);
					if(nd.emailQuestion > 0) {
						emailList = GeneralUtilityMethods.getResponseForQuestion(sd, cResults, sId, nd.emailQuestion, instanceId);
					} else {
						emailList = new ArrayList<String> ();
					}
					
					// Add the static emails to the per question emails
					for(String email : nd.emails) {
						if(email.length() > 0) {
							log.info("Adding static email: " + email); 
							emailList.add(email);
						}
					}
							
					// Convert emails into a comma separated string
					String emails = "";
					for(String email : emailList) {		
						if(isValidEmail(email)) {
							if(emails.length() > 0) {
								emails += ",";
							}
							emails += email;
						} else {
							log.info("Email Notifications: Discarding invalid email: " + email);
						}
					}
						
					if(emails.trim().length() > 0) {
						log.info("userevent: " + remoteUser + " sending email of '" + logContent + "' to " + emails);
						
						// Set the subject
						String subject = "";
						if(nd.subject != null && nd.subject.trim().length() > 0) {
							subject = nd.subject;
						} else {
							if(serverName != null && serverName.contains("smap")) {
								subject = "Smap ";
							}
							subject += localisation.getString("c_notify");
						}
						
						String from = "smap";
						if(nd.from != null && nd.from.trim().length() > 0) {
							from = nd.from;
						}
						String content = null;
						if(nd.content != null && nd.content.trim().length() > 0) {
							content = nd.content;
						} else {
							content = organisation.default_email_content;
						}
						
						notify_details = "Sending email to: " + emails + " containing link " + logContent;
						
						log.info("+++ emailing to: " + emails + " docUrl: " + logContent + 
								" from: " + from + 
								" subject: " + subject +
								" smtp_host: " + emailServer.smtpHost +
								" email_domain: " + emailServer.emailDomain);
						try {
							EmailManager em = new EmailManager();
							
							em.sendEmail(
									emails, 
									null, 
									"notify", 
									subject, 
									content,
									from,		
									null, 
									null, 
									null, 
									docURL, 
									filePath,
									filename,
									organisation.getAdminEmail(), 
									emailServer,
									scheme,
									serverName,
									localisation);
						} catch(Exception e) {
							status = "error";
							error_details = e.getMessage();
						}
					} else {
						log.log(Level.INFO, "Info: List of email recipients is empty");
						lm.writeLog(sd, sId, "subscriber", "email", localisation.getString("email_nr"));
						writeToMonitor = false;
					}
				} else {
					status = "error";
					error_details = "smtp_host not set";
					log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
				}
				
			} else {
				status = "error";
				error_details = "Invalid target" + target;
				log.log(Level.SEVERE, "Error: Invalid target" + target);
			}
			
			// Write log message
			if(writeToMonitor) {
				pstmtNotificationLog.setInt(1, organisation.id);
				pstmtNotificationLog.setInt(2, pId);
				pstmtNotificationLog.setInt(3, sId);
				pstmtNotificationLog.setString(4, notify_details);
				pstmtNotificationLog.setString(5, status);
				pstmtNotificationLog.setString(6, error_details);
				pstmtNotificationLog.executeUpdate();
			}
		}
			
		/*
		 * Update upload event to record application of notifications
		 */
		pstmtUpdateUploadEvent.setInt(1, ue_id);
		pstmtUpdateUploadEvent.executeUpdate();
			
	}
	
	/*
	 * Validate an email
	 */
	public boolean isValidEmail(String email) {
		boolean isValid = true;
		try {
		      InternetAddress emailAddr = new InternetAddress(email);
		      emailAddr.validate();
		   } catch (AddressException ex) {
		      isValid = false;
		   }
		return isValid;
	}
}


