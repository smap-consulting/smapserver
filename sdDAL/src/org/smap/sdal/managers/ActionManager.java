package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.xml.internal.ws.api.policy.AlternativeSelector;

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
public class ActionManager {
	
	private static Logger log =
			 Logger.getLogger(ActionManager.class.getName());

	// Alert priorities
	public final static int PRI_LOW = 3;
	public final static int PRI_MED = 2;
	public final static int PRI_HIGH = 1;
	
	/*
	 * Apply actions resulting from a change to managed forms
	 */
	public void applyManagedFormActions(Connection sd, 
			TableColumn tc, 
			int oId, 
			int sId, 
			int pId,
			int managedId,
			int prikey,
			int priority,
			String value,
			ResourceBundle localisation) throws Exception {
		
		for(int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);
			
			// Add the action specific settings
			a.sId = sId;
			a.pId = pId;
			a.managedId = managedId;
			a.prikey = prikey;
			
			log.info("Apply managed actions: Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);
			
			addAction(sd, a, oId, localisation, a.action, null, priority, value);
		}
	}
	
	/*
	 * Get the priority of a record
	 *  1 - high
	 *  2 - medium
	 *  3 - low
	 */
	public int getPriority(Connection cResults, 
			String tableName, 
			int prikey) throws Exception {
		
		String sql = "select priority from " + tableName + " where prikey = ?";
		PreparedStatement pstmt = null;
		int priority = ActionManager.PRI_LOW;	// Default to a low priority
		try {
			String priType = GeneralUtilityMethods.columnType(cResults, tableName, "priority");
			if(priType != null && priType.equals("integer")) {
				pstmt = cResults.prepareStatement(sql);
				pstmt.setInt(1, prikey);
				log.info("Get priority: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					priority = rs.getInt(1);
				}
			} else {
				log.info("Cannot get priority for table: "+ tableName + " and prikey " + prikey);
			}
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch (Exception e) {}}
		}
		
		return priority;
	}
	
	/*
	 * Get details of an action after a request from a temporary user
	 */
	public Action getAction(Connection sd, String userIdent) throws SQLException {
		
		Action a = null;
		
		String sql = "select action_details from users "
				+ "where "
				+ "temporary = true "
				+ "and ident = ?";
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, userIdent);
			log.info("Get action details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			if(rs.next()) {
				a = new Gson().fromJson(rs.getString(1), Action.class);
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return a;
	}
	
	/*
	 * Create a temporary user to complete an action
	 * Add an alert into the alerts table  - Deprecate
	 * add a message into message table (Replaces alerts)
	 */
	private void addAction(Connection sd, Action a, int oId, ResourceBundle localisation, 
			String action,
			String msg, 
			int priority,
			String value) throws Exception {
		
		String sql = "insert into alert"
				+ "(u_id, status, priority, created_time, updated_time, link, message, s_id, m_id, prikey) "
				+ "values(?, ?, ?, now(), now(), ?, ?, ?, ?, ?)";
		PreparedStatement pstmt = null;
		
		String sqlUpdate = "update alert "
				+ "set "
				+ "u_id = ?,"
				+ "status = ?,"
				+ "priority = ?, "
				+ "updated_time = now(), "
				+ "message = ? "
				+ "where id = ?";
		
		String sqlActionExists = "select id from alert where s_id = ? and m_id = ? and prikey = ?";
		PreparedStatement pstmtActionExists = null;
		
		String sqlDeleteAction = "delete from alert where id = ?";
		PreparedStatement pstmtDeleteAction = null;
		
		String link = null;
		int actionId = 0;
		try {
			
			// Check to see if an action already exists for this managed form
			pstmtActionExists = sd.prepareStatement(sqlActionExists);
			pstmtActionExists.setInt(1, a.sId);
			pstmtActionExists.setInt(2, a.managedId);
			pstmtActionExists.setInt(3, a.prikey);
			ResultSet rs = pstmtActionExists.executeQuery();
			if(rs.next()) {
				actionId = rs.getInt(1);
			}
			
			/*
			 * If this is a new action then create a temporary user that controls access to the resource
			 */
			if(a.action.equals("respond") && actionId == 0) {
				link = getLink(sd, a, oId);
			}
			
			// Get the id of the user to notify
			int uId = 0;
			if(a.notify_type != null) {
				if(a.notify_type.equals("ident")) {		// Only ident currently supported
					if(a.notify_person == null) {
						a.notify_person = value;	// Use the value that is being set as the ident of the person to notify
					}
					if(a.notify_person != null && a.notify_person.trim().length() > 0) {
						uId = GeneralUtilityMethods.getUserId(sd, a.notify_person);
					}
					
				} else {
					log.info("Info: User attempted to use a notify type other than ident");
				}
			}
			
			/*
			 * If this alert is no longer assigned to an individual and has no subscriptions (TODO) then it can be deleted
			 */
			if(uId == 0 && actionId > 0) {
				pstmtDeleteAction = sd.prepareStatement(sqlDeleteAction);
				pstmtDeleteAction.setInt(1, actionId);
				pstmtDeleteAction.executeUpdate();
			} else {
			
				if(action != null && msg == null) {
					msg = localisation.getString("action_" + action);
				}
				
				if(actionId > 0) {
					// Update existing action
					pstmt = sd.prepareStatement(sqlUpdate);
					pstmt.setInt(1,  uId);			// User
					pstmt.setString(2, "open");    // Status: open || reject || complete
					pstmt.setInt(3, priority);		// Priority
					pstmt.setString(4,  msg);		// Message TODO set for info type actions
					pstmt.setInt(5,  actionId);
					log.info("Update alert: " + pstmt.toString());
					pstmt.executeUpdate();
					
				} else {
					// Insert action
					if(action != null && msg == null) {
						msg = localisation.getString("action_" + action);
					}
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1,  uId);			// User
					pstmt.setString(2, "open");    // Status: open || reject || complete
					pstmt.setInt(3, priority);				// Priority
					pstmt.setString(4,  link);		// Link for the user to click on to complete the action
					pstmt.setString(5,  msg);		// Message TODO set for info type actions
					pstmt.setInt(6,  a.sId);
					pstmt.setInt(7,  a.managedId);
					pstmt.setInt(8,  a.prikey);
					
					log.info("Create alert: " + pstmt.toString());
					pstmt.executeUpdate();
				}
			}
					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtActionExists != null) {pstmtActionExists.close();}} catch (SQLException e) {}
			try {if (pstmtDeleteAction != null) {pstmtDeleteAction.close();}} catch (SQLException e) {}
		}
		
		
	}
	
	public String getLink(Connection sd, Action a, int oId) throws Exception {
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		String resource = gson.toJson(a);
		String tempUserId = null;
		String link = null;
		
		String sqlResourceHasUser = "select ident from users where action_details = ?";
		PreparedStatement pstmtResourceHasUser = null;
	
		try {
			//  If a temporary user already exists then use that user
			pstmtResourceHasUser = sd.prepareStatement(sqlResourceHasUser);
			pstmtResourceHasUser.setString(1, resource);
			log.info("Check for resource with user: " + pstmtResourceHasUser);
			ResultSet rs2 = pstmtResourceHasUser.executeQuery();
			if(rs2.next()) {
				tempUserId = rs2.getString(1);
			}
			
			if(tempUserId == null) {
				UserManager um = new UserManager();
				tempUserId = "u" + String.valueOf(UUID.randomUUID());
				User u = new User();
				u.ident = tempUserId;
				u.name = a.notify_person;
				u.action_details = resource;
				
				// Add the project that contains the survey
				u.projects = new ArrayList<Project> ();
				Project p = new Project();
				p.id = a.pId;
				u.projects.add(p);
				
				
				// Add the roles for the temporary user
				u.roles = a.roles;
				
				um.createTemporaryUser(sd, u, oId);
			}
			
			link = "/action/" + tempUserId;
		} finally {
			try {if (pstmtResourceHasUser != null) {pstmtResourceHasUser.close();}} catch (SQLException e) {}
		}
		
		return link;
	}


}


