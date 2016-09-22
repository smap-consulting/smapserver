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
	
	// Alert status values
	public final static int ALERT_OPEN = 1;
	public final static int ALERT_DONE = 2;
	public final static int ALERT_DELETED = 3;

	// Alert priorities
	public final static int PRI_LOW = 1;
	public final static int PRI_MED = 2;
	public final static int PRI_HIGH = 3;
	
	/*
	 * Apply actions resulting from a change to managed forms
	 */
	public void applyManagedFormActions(Connection sd, 
			TableColumn tc, 
			int oId, 
			int sId, 
			int managedId,
			int prikey,
			ResourceBundle localisation) throws Exception {
		
		for(int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);
			
			// Add the action specific settings
			a.sId = sId;
			a.managedId = managedId;
			a.prikey = prikey;
			System.out.println("Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);
			
			addAction(sd, a, oId, localisation, a.action, null);
		}
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
	 * Add an alert into the alerts table
	 */
	private void addAction(Connection sd, Action a, int oId, ResourceBundle localisation, 
			String action,
			String msg) throws Exception {
		
		String sql = "insert into alert"
				+ "(u_id, status, priority, updated_time, link, message) "
				+ "values(?, ?, ?, now(), ?, ?)";
		PreparedStatement pstmt = null;
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		String link = null;
		try {
			
			if(a.action.equals("respond")) {
				
				/*
				 * Create a temporary user who can complete this action
				 */
				UserManager um = new UserManager();
				String tempUserId = "u" + String.valueOf(UUID.randomUUID());
				User u = new User();
				u.ident = tempUserId;
				u.name = a.notify_person;
				u.action_details = gson.toJson(a);
				
				um.createTemporaryUser(sd, u, oId);
				link = "/action/" + tempUserId;
			}
			
			// Get the id of the user to notify
			int uId = 0;
			if(a.notify_type != null) {
				if(a.notify_type.equals("ident")) {		// Only ident currently supported
					uId = GeneralUtilityMethods.getUserId(sd, a.notify_person);
				} else {
					log.info("Info: User attempted to use a notify type other than ident");
				}
			}
			if(uId == 0) {
				throw new Exception(
						localisation.getString("mf_u") + " " + 
						a.notify_person + 
						localisation.getString("mf_nf"));
			}
			
			if(action != null && msg == null) {
				msg = localisation.getString("action_" + action);
			}
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1,  uId);			// User
			pstmt.setInt(2, ALERT_OPEN);    // Status: open || reject || complete
			pstmt.setInt(3, PRI_LOW);				// Priority
			pstmt.setString(4,  link);		// Link for the user to click on to complete the action
			pstmt.setString(5,  msg);		// Message TODO set for info type actions
			
			log.info("Create alert: " + pstmt.toString());
			pstmt.executeUpdate();
					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		
	}


}


