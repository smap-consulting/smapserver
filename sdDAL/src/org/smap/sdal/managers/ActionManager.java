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
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

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
	
	public void getEvents() {
		
	}
	
	/*
	 * Apply actions resulting from a change to managed forms
	 */
	public void applyManagedFormActions(Connection sd, TableColumn tc, int oId) throws Exception {
		for(int i = 0; i < tc.actions.size(); i++) {
			Action a = tc.actions.get(i);
			System.out.println("Action: " + a.action + " : " + a.notify_type + " : " + a.notify_person);
			
			addAction(sd, a, oId);
		}
	}
	
	/*
	 * Add an action into the action table
	 */
	private void addAction(Connection sd, Action a, int oId) throws Exception {
		
		String sql = "insert into action "
				+ "";
		PreparedStatement pstmt = null;
		
		int uId = 0;
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
				
				uId = um.createTemporaryUser(sd, u, oId);
			}
			
			// TODO create action
			
			pstmt = sd.prepareStatement(sql);
			
			

					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		
	}

	/*
	 * Create a response link for a user to follow up on an action
	 */
	private String createResponseLink() {
		String link = "a link";
		return link;
	}
}


