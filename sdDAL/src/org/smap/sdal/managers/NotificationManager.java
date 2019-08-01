package org.smap.sdal.managers;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;

import org.smap.notifications.interfaces.EmitAwsSMS;
import org.smap.notifications.interfaces.EmitDeviceNotification;
import org.smap.notifications.interfaces.EmitSMS;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.SubmissionMessage;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;

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
	
	private ResourceBundle localisation;
	
	public NotificationManager(ResourceBundle l) {
		localisation = l;
	}
	
	/*
	 * Get all Enabled notifications
	 * Used by Subscriber to do forwarding
	 */
	public ArrayList<Notification> getEnabledNotifications(
			Connection sd, 
			String trigger,
			String target) throws SQLException {
		
		ArrayList<Notification> forwards = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, "
				+ "f.s_id, "
				+ "f.enabled, " +
				" f.remote_s_id, "
				+ "f.remote_s_name, "
				+ "f.remote_host, "
				+ "f.remote_user, "
				+ "f.trigger, "
				+ "f.target, "
				+ "s.display_name, "
				+ "f.notify_details, "
				+ "f.filter, "
				+ "f.name, "
				+ "f.tg_id,"
				+ "f.period,"
				+ "f.remote_password "
				+ "from forward f, survey s "
				+ "where f.s_id = s.s_id "
				+ "and f.enabled = 'true' "
				+ "and f.trigger = ? ";
		PreparedStatement pstmt = null;
		
		try {
			if(target.equals("forward")) {
				sql += " and f.target = 'forward' and f.remote_host is not null";
			} else if(target.equals("message")) {
				sql += " and (f.target = 'email' or f.target = 'sms')";
			} else if(target.equals("document")) {
				sql += " and f.target = 'document'";
			}	
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, trigger);
			resultSet = pstmt.executeQuery();
			
			addToList(sd, resultSet, forwards, true, false);
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return forwards;
		
	}
	
	/*
	 * Add a record to the notification table
	 */
	public void addNotification(Connection sd, PreparedStatement pstmt, String user, 
			Notification n) throws Exception {
					
			String sql = "insert into forward(" +
					" s_id, enabled, " +
					" remote_s_id, remote_s_name, remote_host, remote_user, remote_password, notify_details, "
					+ "trigger, target, filter, name, tg_id, period) " +
					" values (?, ?, ?, ?, ?, ?, ?, ?"
					+ ", ?, ?, ?, ?, ?, ?); ";
	
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
			pstmt.setString(9, n.trigger);
			pstmt.setString(10, n.target);
			pstmt.setString(11, n.filter);
			pstmt.setString(12, n.name);
			pstmt.setInt(13, n.tgId);
			pstmt.setString(14, n.period);
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
					" trigger = ?, " +
					" target = ?, " +
					" filter = ?, " +
					" name = ?, " +
					" tg_id = ?, " +
					" period = ?, " +
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
					" trigger = ?, " +
					" target = ?, " +
					" filter = ?, " +
					" name = ?, " +
					" tg_id = ?, " +
					" period = ? " +
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
		pstmt.setString(8, n.trigger);
		pstmt.setString(9, n.target);
		pstmt.setString(10, n.filter);
		pstmt.setString(11, n.name);
		pstmt.setInt(12, n.tgId);
		pstmt.setString(13, n.period);
		if(n.update_password) {
			pstmt.setString(14, n.remote_password);
			pstmt.setInt(15, n.id);
		} else {
			pstmt.setInt(14, n.id);
		}

		log.info("Update Forward: " + pstmt.toString());
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
		
		log.info("Checking for forwarding feedback loop. Current server is: " + server + " : " + remote_host);
		
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
		String sql = "select f.id, f.s_id, f.enabled, "
				+ "f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user,"
				+ "f.trigger, f.target, s.display_name, f.notify_details, f.filter, f.name,"
				+ "f.tg_id, f.period "
				+ "from forward f, survey s, users u, user_project up, project p "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and s.s_id = f.s_id "
				+ "and u.ident = ? "
				+ "and s.p_id = ? "
				+ "and s.deleted = 'false' "
				+ "order by f.name, s.display_name asc";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setString(1, user);
		pstmt.setInt(2, projectId);
		log.info("Project Notifications: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		addToList(sd, resultSet, notifications, false, false);

		return notifications;
		
	}
	
	/*
	 * Get all Notifications in the users organisation
	 */
	public ArrayList<Notification> getAllNotifications(
			Connection sd, 
			PreparedStatement pstmt,
			int oId) throws SQLException {
		
		ArrayList<Notification> notifications = new ArrayList<Notification>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, "
				+ "f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user,"
				+ "f.trigger, f.target, s.display_name, f.notify_details, f.filter, f.name,"
				+ "f.tg_id, f.period, p.name  "
				+ "from forward f, survey s, project p "
				+ "where s.s_id = f.s_id "
				+ "and p.o_id = ? "
				+ "and s.deleted = 'false' "
				+ "order by p.name, f.name, s.display_name asc";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setInt(1, oId);
		log.info("All Notifications: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		addToList(sd, resultSet, notifications, false, true);

		return notifications;
		
	}
	
	/*
	 * Get a list of notification types
	 */
	public ArrayList<String> getNotificationTypes(Connection sd, String user) throws SQLException {
		
		ArrayList<String> types = new ArrayList<>();
		
		PreparedStatement pstmt = null;
		String sql = "select s.sms_url, s.document_sync from server s";
		
		PreparedStatement orgLevelPstmt = null;
		String sqlOrgLevel = "select o.can_sms from organisation o, users u "
				+ "where o.id = u.o_id "
				+ "and u.ident = ?";
		
		types.add("email");
		types.add("forward");
		
		boolean awsSMS = false;
		
		try {
		
			pstmt = sd.prepareStatement(sql);	 			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String smsUrl = rs.getString("sms_url");
				if(smsUrl != null) {
					if(smsUrl.trim().length() > 0  && !smsUrl.equals("aws")) {
						types.add("sms");
					} else if(smsUrl.equals("aws")) {
						awsSMS = true;
					}
				}
				if(rs.getBoolean("document_sync")) {
					types.add("document");
				}
			}
			
			// If SMS is enabled using AWS then check users organisation for SMS being enabled
			if(awsSMS) {
				orgLevelPstmt = sd.prepareStatement(sqlOrgLevel);
				orgLevelPstmt.setString(1, user);
				log.info("Check for SMS: " + orgLevelPstmt.toString());
				rs = orgLevelPstmt.executeQuery();
				if(rs.next()) {
					if(rs.getBoolean(1)) {
						types.add("sms");
					}
				}
			}
			
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (orgLevelPstmt != null) {orgLevelPstmt.close();}} catch (SQLException e) {}
		}

		return types;
		
	}
	
	/*
	 * Delete the notification
	 */
	public void deleteNotification
	(Connection sd,
			String user,
			int id) throws SQLException {
		
		String nName = GeneralUtilityMethods.getNotificationName(sd, id);
		
		String sql = "delete from forward where id = ?; ";
		PreparedStatement pstmt = null;
		
		try {
			
			// Delete
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, id);
			log.info("Delete: " + pstmt.toString());
			pstmt.executeUpdate();
		
			// Log the delete event
			String logMessage = localisation.getString("lm_del_notification");
			if(nName == null) {
				nName = "";
			}
			logMessage = logMessage.replaceAll("%s1", nName);
			lm.writeLog(sd, 0, user, LogManager.DELETE, logMessage);
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}
		
	}

	private void addToList(Connection sd, 
			ResultSet resultSet, 
			ArrayList<Notification> notifications, 
			boolean getPassword,
			boolean getProject) throws SQLException {
		
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
			n.trigger = resultSet.getString(8);
			n.target = resultSet.getString(9);
			n.s_name = resultSet.getString(10);
			String notifyDetailsString = resultSet.getString(11);
			n.notifyDetails = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
			// Temporary - set question name from question id if this is set
			if(n.notifyDetails.emailQuestionName == null && n.notifyDetails.emailQuestion > 0) {
				n.notifyDetails.emailQuestionName = GeneralUtilityMethods.getQuestionNameFromId(sd, n.s_id, n.notifyDetails.emailQuestion);
			}
			n.filter = resultSet.getString(12);
			n.name = resultSet.getString(13);
			n.tgId = resultSet.getInt(14);
			n.period = resultSet.getString(15);
			if(getPassword) {
				n.remote_password = resultSet.getString(16);
			}
			if(getProject) {
				n.project = resultSet.getString(16);
			}
			
			if(n.trigger.equals("task_reminder")) {
				n.tg_name = GeneralUtilityMethods.getTaskGroupName(sd, n.tgId);
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
			int ue_id,
			String remoteUser,
			String scheme,
			String serverName,
			String basePath,
			String serverRoot,
			String ident,			// Survey Ident
			String instanceId,
			int pId,
			boolean excludeEmpty) throws Exception {
		
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
		PreparedStatement pstmtGetNotifications = null;
		PreparedStatement pstmtUpdateUploadEvent = null;
		
		// TODO remove sId as key for survey in notifications and replace with sIdent
		int sId = GeneralUtilityMethods.getSurveyId(sd, ident);
		
		try {
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			MessagingManager mm = new MessagingManager();
			int oId = GeneralUtilityMethods.getOrganisationIdForSurvey(sd, sId);
			
			log.info("notifyForSubmission:: " + ue_id);
			
			String sqlGetNotifications = "select n.target, n.notify_details, n.filter "
					+ "from forward n "
					+ "where n.s_id = ? " 
					+ "and n.target != 'forward' "
					+ "and n.target != 'document' "
					+ "and n.enabled = 'true' "
					+ "and n.trigger = 'submission'";
			pstmtGetNotifications = sd.prepareStatement(sqlGetNotifications);
			
			String sqlUpdateUploadEvent = "update upload_event set notifications_applied = 'true' where ue_id = ?; ";
			pstmtUpdateUploadEvent = sd.prepareStatement(sqlUpdateUploadEvent);
	
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, remoteUser);
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";		// Set default time to UTC
			
			pstmtGetNotifications.setInt(1, sId);
			log.info("Get notifications:: " + pstmtGetNotifications.toString());
			rsNotifications = pstmtGetNotifications.executeQuery();
			while(rsNotifications.next()) {
				
				String target = rsNotifications.getString(1);
				String notifyDetailsString = rsNotifications.getString(2);
				String filter = rsNotifications.getString(3);
				NotifyDetails nd = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
				
				/*
				 * Get survey details
				 */
				SurveyManager sm = new SurveyManager(localisation, "UTC");
				Survey survey = sm.getById(sd, cResults, remoteUser, sId, true, basePath, 
						instanceId, true, false, true, false, true, "real", 
						false, false, 
						true, 			// pretend to be super user
						"geojson",
						false,			// Do not follow links to child surveys
						false	// launched only
						);	
				
				/*
				 * Test the filter
				 */
				boolean proceed = true;
				if(filter != null && filter.trim().length() > 0) {
					try {
						proceed = GeneralUtilityMethods.testFilter(cResults, localisation, survey, filter, instanceId, tz);
					} catch(Exception e) {
						String msg = e.getMessage();
						if(msg == null) {
							msg = "";
						}
						log.log(Level.SEVERE, e.getMessage(), e);
						lm.writeLog(sd, sId, "subscriber", LogManager.NOTIFICATION, 
								localisation.getString("filter_error")
								.replace("%s1", filter)
								.replace("%s2", msg));
					}
				}
				
				if(!proceed) {
					lm.writeLog(sd, sId, "subscriber", LogManager.NOTIFICATION, 
							localisation.getString("filter_reject")
							.replace("%s1", survey.displayName)
							.replace("%s2", filter)
							.replace("%s3", instanceId));
				} else {
		
					SubmissionMessage subMsg = new SubmissionMessage(
							0,				// Task Id - ignore, only relevant for a reminder
							ident,			// Survey Ident
							pId,
							instanceId, 
							nd.from,
							nd.subject, 
							nd.content,
							nd.attach,
							nd.include_references,
							nd.launched_only,
							nd.emailQuestion,
							nd.emailQuestionName,
							nd.emailMeta,
							nd.emails,
							target,
							remoteUser,
							scheme,
							serverName,
							basePath);
					mm.createMessage(sd, oId, "submission", "", gson.toJson(subMsg));
					
				}
			}
				
			/*
			 * Update upload event to record application of notifications
			 */
			pstmtUpdateUploadEvent.setInt(1, ue_id);
			pstmtUpdateUploadEvent.executeUpdate();
		} finally {
			try {if (pstmtGetNotifications != null) {pstmtGetNotifications.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateUploadEvent != null) {pstmtUpdateUploadEvent.close();}} catch (SQLException e) {}
		}
			
	}
	
	/*
	 * Process a submission notification
	 * Has access to the data from the submitted survey
	 */
	public void processSubmissionNotification(Connection sd, 
			Connection cResults, 
			Organisation organisation,
			String tz,
			SubmissionMessage msg,
			int messageId) throws Exception {
		
		String docURL = null;
		String filePath = null;
		String filename = "instance";
		String logContent = null;
		
		boolean writeToMonitor = true;
		
		HashMap<String, String> sentEndPoints = new HashMap<> ();
		boolean generateBlank =  (msg.instanceId == null) ? true : false;	// If false only show selected options
		
		PreparedStatement pstmtGetSMSUrl = null;
		
		PreparedStatement pstmtNotificationLog = null;
		String sqlNotificationLog = "insert into notification_log " +
				"(o_id, p_id, s_id, notify_details, status, status_details, event_time, message_id, type) " +
				"values( ?, ?,?, ?, ?, ?, now(), ?, 'submission'); ";
		
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		int surveyId;
		if(msg.survey_ident != null) {
			surveyId = GeneralUtilityMethods.getSurveyId(sd, msg.survey_ident);
		} else {
			surveyId = msg.sId;		// A legacy message
		}
		
		Survey survey = sm.getById(sd, cResults, msg.user, surveyId, true, msg.basePath, 
				msg.instanceId, true, generateBlank, true, false, true, "real", 
				false, false, true, "geojson",
				msg.include_references,	// For PDFs follow links to referenced surveys
				msg.launchedOnly			// launched only
				);
		
		PDFSurveyManager pm = new PDFSurveyManager(localisation, sd, cResults, survey, msg.user, organisation.timeZone);
		
		try {
			
			pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);
			
			// Notification log
			ArrayList<String> unsubscribedList  = new ArrayList<> ();
			String error_details = null;
			String notify_details = null;
			String status = null;
			
			if(organisation.can_notify) {
				
				/*
				 * Add details from the survey to the subject and email content
				 */
				msg.subject = sm.fillStringTemplate(survey, msg.subject);
				msg.content = sm.fillStringTemplate(survey, msg.content);
				TextManager tm = new TextManager(localisation, organisation.timeZone);
				ArrayList<String> text = new ArrayList<> ();
				text.add(msg.subject);
				text.add(msg.content);
				tm.createTextOutput(sd,
							cResults,
							text,
							msg.basePath, 
							msg.user,
							survey,
							"none",
							organisation.id);
				msg.subject = text.get(0);
				msg.content = text.get(1);
				
				if(msg.attach != null && !msg.attach.equals("none")) {
					
					if(msg.attach.startsWith("pdf")) {
						docURL = null;
						
						// Create temporary PDF and get file name
						filePath = msg.basePath + "/temp/" + String.valueOf(UUID.randomUUID()) + ".pdf";
						FileOutputStream outputStream = null;
						try {
							outputStream = new FileOutputStream(filePath); 
						} catch (Exception e) {
							log.log(Level.SEVERE, "Error creating temporary PDF file", e);
						}
											
						// Split orientation from nd.attach
						boolean landscape = false;
						if(msg.attach != null && msg.attach.startsWith("pdf")) {
							landscape = msg.attach.equals("pdf_landscape");
							msg.attach = "pdf";
						}
		
						filename = pm.createPdf(
								outputStream,
								msg.basePath, 
								msg.serverRoot,
								msg.user,
								"none", 
								generateBlank,
								null,
								landscape,
								null);
						
						logContent = filePath;
						
					} else {
						docURL = "/webForm/" + msg.survey_ident +
								"?datakey=instanceid&datakeyvalue=" + msg.instanceId;
						logContent = docURL;
					}
				} 
					
				/*
				 * Send document to target
				 */
				status = "success";					// Notification log
				notify_details = null;				// Notification log
				error_details = null;				// Notification log
				if(msg.target.equals("email")) {
					EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, msg.user);
					if(emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {
						ArrayList<String> emailList = null;
						log.info("Email question: " + msg.getEmailQuestionName(sd));
						if(msg.emailQuestionSet()) {
							emailList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, surveyId, msg.getEmailQuestionName(sd), msg.instanceId);
						} else {
							emailList = new ArrayList<String> ();
						}
						
						// Add any meta email addresses to the per question emails
						String metaEmail = GeneralUtilityMethods.getResponseMetaValue(sd, cResults, surveyId, msg.emailMeta, msg.instanceId);
						if(metaEmail != null) {
							emailList.add(metaEmail);
						}
						
						// Add the static emails to the per question emails
						if(msg.emails != null) {
							for(String email : msg.emails) {
								if(email.length() > 0) {
									log.info("Adding static email: " + email); 
									emailList.add(email);
								}
							}
						}
								
						// Convert emails into a comma separated string
						String emails = "";
						for(String email : emailList) {	
							if(sentEndPoints.get(email) == null) {
								if(UtilityMethodsEmail.isValidEmail(email)) {
									if(emails.length() > 0) {
										emails += ",";
									}
									emails += email;
								} else {
									log.info("Email Notifications: Discarding invalid email: " + email);
								}
								sentEndPoints.put(email, email);
							} else {
								log.info("Duplicate email: " + email);
							}
						}
							
						if(emails.trim().length() > 0) {
							log.info("userevent: " + msg.user + " sending email of '" + logContent + "' to " + emails);
							
							// Set the subject
							String subject = "";
							if(msg.subject != null && msg.subject.trim().length() > 0) {
								subject = msg.subject;
							} else {
								if(msg.server != null && msg.server.contains("smap")) {
									subject = "Smap ";
								}
								subject += localisation.getString("c_notify");
							}
							
							String from = "smap";
							if(msg.from != null && msg.from.trim().length() > 0) {
								from = msg.from;
							}
							String content = null;
							if(msg.content != null && msg.content.trim().length() > 0) {
								content = msg.content;
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
								PeopleManager peopleMgr = new PeopleManager(localisation);
								InternetAddress[] emailArray = InternetAddress.parse(emails);
								String emailKey = null;
								
								for(InternetAddress ia : emailArray) {								
									emailKey = peopleMgr.getEmailKey(sd, organisation.id, ia.getAddress());							
									if(emailKey == null) {
										unsubscribedList.add(ia.getAddress());		// Person has unsubscribed
									} else {
										em.sendEmail(
												ia.getAddress(), 
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
												msg.scheme,
												msg.server,
												emailKey,
												localisation,
												organisation.server_description);
									}
								}
							} catch(Exception e) {
								status = "error";
								error_details = e.getMessage();
								log.log(Level.INFO, error_details);
							}
						} else {
							log.log(Level.INFO, "Info: List of email recipients is empty");
							lm.writeLog(sd, surveyId, "subscriber", LogManager.EMAIL, localisation.getString("email_nr"));
							writeToMonitor = false;
						}
					} else {
						status = "error";
						error_details = "smtp_host not set";
						log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
					}
					
				} else if(msg.target.equals("sms")) {   // SMS URL notification - SMS message is posted to an arbitrary URL 
					
					// Get the URL to use in sending the SMS
					String sql = "select s.sms_url "
							+ "from server s";
					
					String sms_url = null;
					pstmtGetSMSUrl = sd.prepareStatement(sql);
					ResultSet rs = pstmtGetSMSUrl.executeQuery();
					if(rs.next()) {
						sms_url = rs.getString("sms_url");	
					}
					
					if(sms_url != null) {
						ArrayList<String> smsList = null;
						ArrayList<String> responseList = new ArrayList<> ();
						log.info("SMS question: " + msg.getEmailQuestionName(sd));
						if(msg.emailQuestionSet()) {
							smsList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, surveyId, msg.getEmailQuestionName(sd), msg.instanceId);
						} else {
							smsList = new ArrayList<String> ();
						}
						
						// Add the static sms numbers to the per question sms numbers
						for(String sms : msg.emails) {
							if(sms.length() > 0) {
								log.info("Adding static sms: " + sms); 
								smsList.add(sms);
							}
						}
						
						if(smsList.size() > 0) {
							
							EmitSMS smsMgr = null;
							if(sms_url.equals("aws")) {
								String sender_id = "Smap";
								if(msg.subject != null && msg.subject.trim().length() > 0) {
									sender_id = msg.subject;
								}
								smsMgr = new EmitAwsSMS(sender_id, localisation);
							} else {
								smsMgr = new SMSExternalManager(sms_url, localisation);
							}
							
							for(String sms : smsList) {
								
								if(sentEndPoints.get(sms) == null) {
									log.info("userevent: " + msg.user + " sending sms of '" + msg.content + "' to " + sms);
									try {
										responseList.add(smsMgr.sendSMS(sms, msg.content));
									} catch (Exception e) {
										status = "error";
										error_details = e.getMessage();
									}
									sentEndPoints.put(sms, sms);
								} else {
									log.info("Duplicate phone number: " + sms);
								}
								
							} 
						} else {
							log.info("No phone numbers to send to");
							writeToMonitor = false;
						}
						
						notify_details = "Sending sms " + smsList.toString() 
								+ ((logContent == null || logContent.equals("null")) ? "" :" containing link " + logContent)
								+ " with response " + responseList.toString();
						
					} else {
						status = "error";
						error_details = "SMS URL not set";
						log.log(Level.SEVERE, "Error: Attempt to do SMS notification but SMS URL not set");
					}
		
					
				} else {
					status = "error";
					error_details = "Invalid target: " + msg.target;
					log.log(Level.SEVERE, "Error: Invalid target" + msg.target);
				}
			} else {
				notify_details = organisation.name;
				status = "error";
				error_details = localisation.getString("susp_notify");
				log.log(Level.SEVERE, "Error: notification services suspended");
			}
			
			// Write log message
			if(writeToMonitor) {
				if(!unsubscribedList.isEmpty()) {
					if(error_details == null) {
						error_details = "";
					}
					error_details += localisation.getString("c_unsubscribed") + ": " + String.join(",", unsubscribedList);
				}
				pstmtNotificationLog.setInt(1, organisation.id);
				pstmtNotificationLog.setInt(2, msg.pId);
				pstmtNotificationLog.setInt(3, surveyId);
				pstmtNotificationLog.setString(4, notify_details);
				pstmtNotificationLog.setString(5, status);
				pstmtNotificationLog.setString(6, error_details);
				pstmtNotificationLog.setInt(7, messageId);
				
				pstmtNotificationLog.executeUpdate();
			}
			
			/*
			 * If this notification is for a record then update the Record Event Manager
			 */
			if(msg.instanceId != null) {
				RecordEventManager rem = new RecordEventManager(localisation, tz);
				String tableName = GeneralUtilityMethods.getMainResultsTableSurveyIdent(sd, cResults, msg.survey_ident);
				Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				
				rem.writeEvent(
						sd, 
						cResults, 
						RecordEventManager.NOTIFICATION, 
						status, 
						msg.user, 
						tableName, 
						msg.instanceId, 
						null, 
						null, 
						gson.toJson(msg),
						null, 
						0, 
						msg.survey_ident,
						0,
						0);
			}
			
		} finally {
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			try {if (pstmtGetSMSUrl != null) {pstmtGetSMSUrl.close();}} catch (SQLException e) {}
			
		}
	}
	
	
	/*
	 * Process a reminder
	 * Has access to data from the task
	 */
	public void processReminderNotification(Connection sd, 
			Connection cResults, 
			Organisation organisation,
			String tz,
			SubmissionMessage msg,
			int messageId) throws Exception {
		
		
		String logContent = null;
		
		boolean writeToMonitor = true;
		
		HashMap<String, String> sentEndPoints = new HashMap<> ();

		PreparedStatement pstmtGetSMSUrl = null;
		
		PreparedStatement pstmtNotificationLog = null;
		String sqlNotificationLog = "insert into notification_log " +
				"(o_id, p_id, s_id, notify_details, status, status_details, event_time, message_id, type) " +
				"values( ?, ?,?, ?, ?, ?, now(), ?, 'reminder'); ";
		
		// TODO get Task information
		String urlprefix = msg.scheme + "://" + msg.server;
		TaskManager tm = new TaskManager(localisation, tz);
		TaskListGeoJson t = tm.getTasks(
				sd, 
				urlprefix,
				0,		// Organisation id 
				0, 		// task group id
				msg.taskId,
				true, 
				0,		// userId 
				null, 
				null,	// period 
				0,		// start 
				0,		// limit
				null,	// sort
				null);	// sort direction	
		
		TaskProperties task = null;
		if(t != null && t.features.size() > 0) {
			task = t.features.get(0).properties;
		}
		
		try {
			
			pstmtNotificationLog = sd.prepareStatement(sqlNotificationLog);
			
			// Notification log
			ArrayList<String> unsubscribedList  = new ArrayList<> ();
			String error_details = null;
			String notify_details = null;
			String status = null;
			
			int surveyId;
			if(msg.survey_ident != null) {
				surveyId = GeneralUtilityMethods.getSurveyId(sd, msg.survey_ident);
			} else {
				surveyId = msg.sId;		// A legacy message
			}
			
			if(organisation.can_notify) {

				msg.subject = tm.fillStringTaskTemplate(task, msg, msg.subject);
				msg.content = tm.fillStringTaskTemplate(task, msg, msg.content);
				
				/*
				 * Send document to target
				 */
				status = "success";					// Notification log
				notify_details = null;				// Notification log
				error_details = null;				// Notification log
				if(msg.target.equals("email")) {
					EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, msg.user);
					if(emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {
						ArrayList<String> emailList = null;
						log.info("Email question: " + msg.getEmailQuestionName(sd));
						if(msg.emailQuestionSet()) {
							emailList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, surveyId, msg.getEmailQuestionName(sd), msg.instanceId);
						} else {
							emailList = new ArrayList<String> ();
						}
						
						// Add any meta email addresses to the per question emails
						String metaEmail = GeneralUtilityMethods.getResponseMetaValue(sd, cResults, surveyId, msg.emailMeta, msg.instanceId);
						if(metaEmail != null) {
							emailList.add(metaEmail);
						}
						
						// Add the static emails to the per question emails
						if(msg.emails != null) {
							for(String email : msg.emails) {
								if(email.length() > 0) {
									log.info("Adding static email: " + email); 
									emailList.add(email);
								}
							}
						}
								
						// Convert emails into a comma separated string
						String emails = "";
						for(String email : emailList) {	
							if(sentEndPoints.get(email) == null) {
								if(UtilityMethodsEmail.isValidEmail(email)) {
									if(emails.length() > 0) {
										emails += ",";
									}
									emails += email;
								} else {
									log.info("Email Notifications: Discarding invalid email: " + email);
								}
								sentEndPoints.put(email, email);
							} else {
								log.info("Duplicate email: " + email);
							}
						}
							
						if(emails.trim().length() > 0) {
							log.info("userevent: " + msg.user + " sending email of '" + logContent + "' to " + emails);
							
							// Set the subject
							String subject = "";
							if(msg.subject != null && msg.subject.trim().length() > 0) {
								subject = msg.subject;
							} else {
								if(msg.server != null && msg.server.contains("smap")) {
									subject = "Smap ";
								}
								subject += localisation.getString("c_notify");
							}
							
							String from = "smap";
							if(msg.from != null && msg.from.trim().length() > 0) {
								from = msg.from;
							}
							String content = null;
							if(msg.content != null && msg.content.trim().length() > 0) {
								content = msg.content;
							} else {
								content = organisation.default_email_content;
							}
							
							notify_details = "Sending email to: " + emails + " containing link " + logContent;
							
							log.info("+++ emailing reminder to: " + emails + " docUrl: " + logContent + 
									" from: " + from + 
									" subject: " + subject +
									" smtp_host: " + emailServer.smtpHost +
									" email_domain: " + emailServer.emailDomain);
							try {
								EmailManager em = new EmailManager();
								PeopleManager peopleMgr = new PeopleManager(localisation);
								InternetAddress[] emailArray = InternetAddress.parse(emails);
								String emailKey = null;
								
								for(InternetAddress ia : emailArray) {								
									emailKey = peopleMgr.getEmailKey(sd, organisation.id, ia.getAddress());							
									if(emailKey == null) {
										unsubscribedList.add(ia.getAddress());		// Person has unsubscribed
									} else {
										em.sendEmail(
												ia.getAddress(), 
												null, 
												"notify", 
												subject, 
												content,
												from,		
												null, 
												null, 
												null, 
												null, 
												null,
												null,
												organisation.getAdminEmail(), 
												emailServer,
												msg.scheme,
												msg.server,
												emailKey,
												localisation,
												organisation.server_description);
									}
								}
							} catch(Exception e) {
								status = "error";
								error_details = e.getMessage();
							}
						} else {
							log.log(Level.INFO, "Info: List of email recipients is empty");
							lm.writeLog(sd, surveyId, "subscriber", LogManager.EMAIL, localisation.getString("email_nr"));
							writeToMonitor = false;
						}
					} else {
						status = "error";
						error_details = "smtp_host not set";
						log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
					}
					
				} else if(msg.target.equals("sms")) {   // SMS URL notification - SMS message is posted to an arbitrary URL 
					
					// Get the URL to use in sending the SMS
					String sql = "select s.sms_url "
							+ "from server s";
					
					String sms_url = null;
					pstmtGetSMSUrl = sd.prepareStatement(sql);
					ResultSet rs = pstmtGetSMSUrl.executeQuery();
					if(rs.next()) {
						sms_url = rs.getString("sms_url");	
					}
					
					if(sms_url != null) {
						ArrayList<String> smsList = null;
						ArrayList<String> responseList = new ArrayList<> ();
						log.info("SMS question: " + msg.getEmailQuestionName(sd));
						if(msg.emailQuestionSet()) {
							smsList = GeneralUtilityMethods.getResponseForEmailQuestion(sd, cResults, surveyId, msg.getEmailQuestionName(sd), msg.instanceId);
						} else {
							smsList = new ArrayList<String> ();
						}
						
						// Add the static sms numbers to the per question sms numbers
						for(String sms : msg.emails) {
							if(sms.length() > 0) {
								log.info("Adding static sms: " + sms); 
								smsList.add(sms);
							}
						}
						
						if(smsList.size() > 0) {
							
							EmitSMS smsMgr = null;
							if(sms_url.equals("aws")) {
								String sender_id = "Smap";
								if(msg.subject != null && msg.subject.trim().length() > 0) {
									sender_id = msg.subject;
								}
								smsMgr = new EmitAwsSMS(sender_id, localisation);
							} else {
								smsMgr = new SMSExternalManager(sms_url, localisation);
							}
							
							for(String sms : smsList) {
								
								if(sentEndPoints.get(sms) == null) {
									log.info("userevent: " + msg.user + " sending sms of '" + msg.content + "' to " + sms);
									responseList.add(smsMgr.sendSMS(sms, msg.content));
									sentEndPoints.put(sms, sms);
								} else {
									log.info("Duplicate phone number: " + sms);
								}
								
							} 
						} else {
							log.info("No phone numbers to send to");
							writeToMonitor = false;
						}
						
						notify_details = "Sending sms " + smsList.toString() 
								+ ((logContent == null || logContent.equals("null")) ? "" :" containing link " + logContent)
								+ " with response " + responseList.toString();
						
					} else {
						status = "error";
						error_details = "SMS URL not set";
						log.log(Level.SEVERE, "Error: Attempt to do SMS notification but SMS URL not set");
					}
		
					
				} else {
					status = "error";
					error_details = "Invalid target: " + msg.target;
					log.log(Level.SEVERE, "Error: Invalid target" + msg.target);
				}
			} else {
				notify_details = organisation.name;
				status = "error";
				error_details = localisation.getString("susp_notify");
				log.log(Level.SEVERE, "Error: notification services suspended");
			}
			
			// Write log message
			if(writeToMonitor) {
				if(!unsubscribedList.isEmpty()) {
					if(error_details == null) {
						error_details = "";
					}
					error_details += localisation.getString("c_unsubscribed") + ": " + String.join(",", unsubscribedList);
				}
				pstmtNotificationLog.setInt(1, organisation.id);
				pstmtNotificationLog.setInt(2, msg.pId);
				pstmtNotificationLog.setInt(3, surveyId);
				pstmtNotificationLog.setString(4, notify_details);
				pstmtNotificationLog.setString(5, status);
				pstmtNotificationLog.setString(6, error_details);
				pstmtNotificationLog.setInt(7, messageId);
				
				pstmtNotificationLog.executeUpdate();
			}
		} finally {
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			try {if (pstmtGetSMSUrl != null) {pstmtGetSMSUrl.close();}} catch (SQLException e) {}
			
		}
	}
	
}


