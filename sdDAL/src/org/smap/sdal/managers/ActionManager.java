package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

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
public class ActionManager {
	
	private static Logger log =
			 Logger.getLogger(ActionManager.class.getName());
	
	
	/*
	 * Apply actions resulting from a change to managed forms
	 */
	public void applyManagedFormActions(Connection sd, TableColumn tc, int oId, int sId, int managedId) throws Exception {
		for(int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);
			a.sId = sId;
			a.managedId = managedId;
			System.out.println("Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);
			
			addAction(sd, a, oId);
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
	private void addAction(Connection sd, Action a, int oId) throws Exception {
		
		String sql = "insert into alert"
				+ "(u_id, status, priority, updated_time, link, message) "
				+ "values(?, ?, ?, now(), ?, ?)";
		PreparedStatement pstmt = null;
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		int uId = 0;
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
				
				uId = um.createTemporaryUser(sd, u, oId);
				link = "/action/" + tempUserId;
			}
			
			// TODO create action
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1,  uId);			// User
			pstmt.setString(2, "open");    	// Status: open || reject || complete
			pstmt.setInt(3, 1);				// Priority
			pstmt.setString(4,  link);		// Link for the user to click on to complete the action
			pstmt.setString(5,  null);		// Message TODO set for info type actions
			
			log.info("Create alert: " + pstmt.toString());
			pstmt.executeUpdate();
					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		
	}


}


