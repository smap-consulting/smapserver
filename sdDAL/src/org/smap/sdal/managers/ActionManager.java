package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*****************************************************************************
 * 
 * This file is part of SMAP.
 * 
 * SMAP is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * SMAP is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SMAP. If not, see <http://www.gnu.org/licenses/>.
 * 
 ******************************************************************************/

/*
 * Manage the table that stores details on the forwarding of data onto other
 * systems
 */
public class ActionManager {

	private static Logger log = Logger.getLogger(ActionManager.class.getName());

	// Alert priorities
	public final static int PRI_LOW = 3;
	public final static int PRI_MED = 2;
	public final static int PRI_HIGH = 1;

	/*
	 * Update a data record from an anonymous form
	 */
	class Update {
		String name;
		String value;
		String currentValue;
		int prikey;
	}

	/*
	 * Apply actions resulting from a change to managed forms
	 */
	public void applyManagedFormActions(@Context HttpServletRequest request, Connection sd, TableColumn tc, int oId,
			int sId, int pId, int managedId, int prikey, int priority, String value, ResourceBundle localisation)
			throws Exception {

		for (int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);

			// Add the action specific settings
			a.sId = sId;
			a.pId = pId;
			a.managedId = managedId;
			a.prikey = prikey;

			log.info("Apply managed actions: Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);

			addAction(request, sd, a, oId, localisation, a.action, null, priority, value);
		}
	}

	/*
	 * Get the priority of a record 1 - high 2 - medium 3 - low
	 */
	public int getPriority(Connection cResults, String tableName, int prikey) throws Exception {

		String sql = "select priority from " + tableName + " where prikey = ?";
		PreparedStatement pstmt = null;
		int priority = ActionManager.PRI_LOW; // Default to a low priority
		try {
			String priType = GeneralUtilityMethods.columnType(cResults, tableName, "priority");
			if (priType != null && priType.equals("integer")) {
				pstmt = cResults.prepareStatement(sql);
				pstmt.setInt(1, prikey);
				log.info("Get priority: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				if (rs.next()) {
					priority = rs.getInt(1);
				}
			} else {
				log.info("Cannot get priority for table: " + tableName + " and prikey " + prikey);
			}
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (Exception e) {
				}
			}
		}

		return priority;
	}

	/*
	 * Get details of an action after a request from a temporary user
	 */
	public Action getAction(Connection sd, String userIdent) throws SQLException {

		Action a = null;

		String sql = "select action_details from users " + "where " + "temporary = true " + "and ident = ?";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, userIdent);
			log.info("Get action details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				a = new Gson().fromJson(rs.getString(1), Action.class);
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return a;
	}

	/*
	 * Create a temporary user to complete an action Add an alert into the alerts
	 * table - Deprecate add a message into message table (Replaces alerts)
	 */
	private void addAction(@Context HttpServletRequest request, Connection sd, Action a, int oId,
			ResourceBundle localisation, String action, String msg, int priority, String value) throws Exception {

		String link = null;

		if (a.action.equals("respond") /* && actionId == 0 */) {
			link = request.getScheme() + "://" + request.getServerName() + getLink(sd, a, oId);
		}

		// Get the topic
		String topic = null;
		if (a.notify_type != null) {
			if (a.notify_type.equals("ident")) {

				if (a.notify_person == null) {
					a.notify_person = value; // Use the value that is being
												// set as the ident of the
												// person to notify
				}
				if (a.notify_person != null && a.notify_person.trim().length() > 0) {
					topic = GeneralUtilityMethods.getUserEmail(sd, a.notify_person);
				}
			} else if (a.notify_type.equals("email")) {
				if (a.notify_person == null) {
					topic = value;
				} else {
					topic = a.notify_person;
				}
			} else {
				log.info("Info: User attempted to use a notify type other than ident");
			}
		} else {
			log.info("Error: Notify type null for message: " + msg);
		}

		/*
		 * If this alert is no longer assigned to an individual and has no subscriptions
		 * (TODO) then it can be deleted
		 */

		if (action != null && msg == null) {
			msg = localisation.getString("action_" + action);
		}

		if (link != null) {
			msg += " " + link;
		}

		MessagingManager mm = new MessagingManager();
		if (topic != null) {
			mm.createMessage(sd, oId, topic, msg, null);
		} else {
			log.info("Error: Null topic for message: " + msg);
		}

	}

	public String getLink(Connection sd, Action a, int oId) throws Exception {

		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

		String resource = gson.toJson(a);
		String tempUserId = null;
		String link = null;

		// String sqlResourceHasUser = "select ident from users where action_details =
		// ?";
		// PreparedStatement pstmtResourceHasUser = null;

		try {
			// If a temporary user already exists then use that user
			// pstmtResourceHasUser = sd.prepareStatement(sqlResourceHasUser);
			// pstmtResourceHasUser.setString(1, resource);
			// log.info("Check for resource with user: " + pstmtResourceHasUser);
			// ResultSet rs2 = pstmtResourceHasUser.executeQuery();
			// if(rs2.next()) {
			// tempUserId = rs2.getString(1);
			// }

			if (tempUserId == null) {
				UserManager um = new UserManager();
				tempUserId = "u" + String.valueOf(UUID.randomUUID());
				User u = new User();
				u.ident = tempUserId;
				u.name = a.notify_person;
				u.action_details = resource;

				// Add the project that contains the survey
				u.projects = new ArrayList<Project>();
				Project p = new Project();
				p.id = a.pId;
				u.projects.add(p);

				// Add the roles for the temporary user
				u.roles = a.roles;

				um.createTemporaryUser(sd, u, oId);
			}

			link = "/action/" + tempUserId;
		} finally {
			// try {if (pstmtResourceHasUser != null) {pstmtResourceHasUser.close();}} catch
			// (SQLException e) {}
		}

		return link;
	}

	/*
	 * Process an update request that came either from an anonymous form or from the
	 * managed forms page
	 */
	public Response processUpdate(HttpServletRequest request, Connection sd, Connection cResults, String userIdent,
			int sId, int managedId, String settings) {

		Response response = null;

		Type type = new TypeToken<ArrayList<Update>>() {
		}.getType();
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		ArrayList<Update> updates = gson.fromJson(settings, type);

		String sqlCanUpdate = "select p_id from survey " + "where s_id = ? " + "and managed_id = ? "
				+ "and blocked = 'false' " + "and deleted = 'false';";
		PreparedStatement pstmtCanUpdate = null;
		PreparedStatement pstmtUpdate = null;
		int priority = -1;

		try {

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, userIdent));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			int oId = GeneralUtilityMethods.getOrganisationId(sd, userIdent, 0);
			int pId = 0;
			/*
			 * Verify that the survey is managed by the provided data processing id and get
			 */
			pstmtCanUpdate = sd.prepareStatement(sqlCanUpdate);
			pstmtCanUpdate.setInt(1, sId);
			pstmtCanUpdate.setInt(2, managedId);
			ResultSet rs = pstmtCanUpdate.executeQuery();
			if (rs.next()) {
				pId = rs.getInt(1);
			}
			if (pId == 0) {
				throw new Exception(localisation.getString("mf_blocked"));
			}

			/*
			 * Get the data processing columns
			 */
			SurveyViewDefn svd = new SurveyViewDefn();
			SurveyViewManager svm = new SurveyViewManager();
			svm.getDataProcessingConfig(sd, managedId, svd, null, oId);

			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId); // Get the table name of the top level form
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

			/*
			 * Process each column
			 */
			cResults.setAutoCommit(false);
			for (int i = 0; i < updates.size(); i++) {

				Update u = updates.get(i);

				// 1. Escape quotes in update name, though not really necessary due to next step
				u.name = u.name.replace("'", "''").trim();

				// 2. Confirm this is an editable managed column
				boolean updateable = false;
				TableColumn tc = null;
				for (int j = 0; j < svd.columns.size(); j++) {
					TableColumn xx = svd.columns.get(j);
					if (xx.name.equals(u.name)) {
						if (!xx.readonly) {
							updateable = true;
							tc = xx;
						}
						break;
					}
				}
				if (!updateable) {
					throw new Exception(u.name + " " + localisation.getString("mf_nu"));
				}

				String sqlUpdate = "update " + f.tableName;

				if (u.value == null) {
					sqlUpdate += " set " + u.name + " = null ";
				} else {
					sqlUpdate += " set " + u.name + " = ? ";
				}
				sqlUpdate += "where " + "prikey = ? ";

				try {
					if (pstmtUpdate != null) {
						pstmtUpdate.close();
					}
				} catch (Exception e) {
				}
				pstmtUpdate = cResults.prepareStatement(sqlUpdate);

				// Set the parameters
				int paramCount = 1;
				if (u.value != null) {
					if (tc.type.equals("text") || tc.type.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.value);
					} else if (tc.type.equals("date")) {
						if (u.value == null || u.value.trim().length() == 0) {
							pstmtUpdate.setDate(paramCount++, null);
						} else {
							java.util.Date inputDate = dateFormat.parse(u.value);
							pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
						}
					} else if (tc.type.equals("integer")) {
						int inputInt = Integer.parseInt(u.value);
						pstmtUpdate.setInt(paramCount++, inputInt);
					} else if (tc.type.equals("decimal")) {
						double inputDouble = Double.parseDouble(u.value);
						pstmtUpdate.setDouble(paramCount++, inputDouble);
					} else {
						log.info("Warning: unknown type: " + tc.type + " value: " + u.value);
						pstmtUpdate.setString(paramCount++, u.value);
					}
				}
				pstmtUpdate.setInt(paramCount++, u.prikey);

				log.info("Updating managed survey: " + pstmtUpdate.toString());
				int count = pstmtUpdate.executeUpdate();
				if (count == 0) {
					throw new Exception(
							"Update failed: " + "Try refreshing your view of the data as someone may already "
									+ "have updated this record.");
				}

				/*
				 * Apply any required actions
				 */
				if (tc.actions != null && tc.actions.size() > 0) {
					if (priority < 0) {
						priority = getPriority(cResults, f.tableName, u.prikey);
					}
					applyManagedFormActions(request, sd, tc, oId, sId, pId, managedId, u.prikey, priority, u.value,
							localisation);
				}

			}
			cResults.commit();
			response = Response.ok().build();

		} catch (Exception e) {
			try {
				cResults.rollback();
			} catch (Exception ex) {
			}
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE, "Error", e);
		} finally {

			try {
				cResults.setAutoCommit(true);
			} catch (Exception ex) {
			}

			try {
				if (pstmtCanUpdate != null) {
					pstmtCanUpdate.close();
				}
			} catch (Exception e) {
			}
			try {
				if (pstmtUpdate != null) {
					pstmtUpdate.close();
				}
			} catch (Exception e) {
			}

		}

		return response;
	}

}
