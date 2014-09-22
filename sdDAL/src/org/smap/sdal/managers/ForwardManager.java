package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.model.Forward;
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
public class ForwardManager {
	
	private static Logger log =
			 Logger.getLogger(ForwardManager.class.getName());
	
	/*
	 * Get all Enabled Forwards
	 * Used by Subscriber to do the actual forwarding
	 */
	public ArrayList<Forward> getEnabledForwards(Connection sd, PreparedStatement pstmt) throws SQLException {
		
		ArrayList<Forward> forwards = new ArrayList<Forward>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user, f.remote_password " +
				" from forward f " +
				" where f.enabled = 'true';";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		resultSet = pstmt.executeQuery();
		addToList(resultSet, forwards, true);
		return forwards;
		
	}
	
	/*
	 * Add a record to the forwarding table
	 */
	public void addForward(Connection sd, PreparedStatement pstmt, String user, 
			Forward f) throws Exception {
					
			String sql = "insert into forward(" +
					" s_id, enabled, " +
					" remote_s_id, remote_s_name, remote_host, remote_user, remote_password) " +
					" values (?, ?, ?, ?, ?, ?, ?); ";
	
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, f.getSId());
			pstmt.setBoolean(2, f.isEnabled());
			pstmt.setString(3, f.getRemoteIdent());
			pstmt.setString(4, f.getRemoteSName());
			pstmt.setString(5, f.getRemoteHost());
			pstmt.setString(6, f.getRemoteUser());
			pstmt.setString(7, f.getRemotePassword());
			pstmt.executeUpdate();
	}
	
	/*
	 * Update a record to the forwarding table
	 */
	public void updateForward(Connection sd, PreparedStatement pstmt, String user, 
			Forward f) throws Exception {
			
		String sql = null;
		if(f.isUpdatePassword()) {
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
		pstmt.setInt(1, f.getSId());
		pstmt.setBoolean(2, f.isEnabled());
		pstmt.setString(3, f.getRemoteIdent());
		pstmt.setString(4, f.getRemoteSName());
		pstmt.setString(5, f.getRemoteHost());
		pstmt.setString(6, f.getRemoteUser());
		if(f.isUpdatePassword()) {
			pstmt.setString(7, f.getRemotePassword());
			pstmt.setInt(8, f.getId());
		} else {
			pstmt.setInt(7, f.getId());
		}
		log.info("SQL: " + sql + " id:" + f.getId());
		pstmt.executeUpdate();
	}
	
	public boolean isFeedbackLoop(Connection con, String server, Forward f) throws SQLException {
		boolean loop = false;
		
		String remote_host = null;;
		
		String [] hostParts = f.getRemoteHost().split("//");
		remote_host = hostParts[1];
		
		System.out.println("Current server is: " + server + " : " + remote_host);
		
		// Get the ident of the local survey to compare with the remote ident
		PreparedStatement pstmt;
		String sql = "select ident from survey where s_id = ?;";
		pstmt = con.prepareStatement(sql);
		pstmt.setInt(1, f.getSId());
		ResultSet rs = pstmt.executeQuery(); 
		if(rs.next()) {
			String local_ident = rs.getString(1);
			System.out.println("Local ident is: " + local_ident + " : " + f.getRemoteIdent());
			if(local_ident != null && local_ident.equals(f.getRemoteIdent()) && remote_host.equals(server)) {
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
	public ArrayList<Forward> getProjectForwards(Connection sd, PreparedStatement pstmt,
			String user,
			int projectId) throws SQLException {
		
		ArrayList<Forward> forwards = new ArrayList<Forward>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select f.id, f.s_id, f.enabled, " +
				" f.remote_s_id, f.remote_s_name, f.remote_host, f.remote_user" +
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

		addToList(resultSet, forwards, false);

		return forwards;
		
	}
	
	/*
	 * Delete the forward
	 */
	public void deleteForward(Connection sd, PreparedStatement pstmt,
			String user,
			int id) throws SQLException {
		
		String sql = "delete from forward where id = ?; ";
		
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);	 			

		pstmt.setInt(1, id);
		log.info("Delete: " + sql + " : " + id + " : " + id);
		pstmt.executeUpdate();
		
	}

	private void addToList(ResultSet resultSet, ArrayList<Forward> forwards, boolean getPassword) throws SQLException {
		
		while (resultSet.next()) {								

			Forward f = new Forward();
			f.setId(resultSet.getInt(1));
			f.setSId(resultSet.getInt(2));
			f.setEnabled(resultSet.getBoolean(3));
			f.setRemoteIdent(resultSet.getString(4));
			f.setRemoteSName(resultSet.getString(5));
			f.setRemoteHost(resultSet.getString(6));
			f.setRemoteUser(resultSet.getString(7));
			if(getPassword) {
				f.setRemotePassword(resultSet.getString(8));
			}
			
			forwards.add(f);
		} 
	}
}


