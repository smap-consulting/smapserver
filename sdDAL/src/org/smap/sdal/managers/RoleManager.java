package org.smap.sdal.managers;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
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
 * This class supports access to User and Organisation information in the database
 */
public class RoleManager {
	
	private static Logger log =
			 Logger.getLogger(RoleManager.class.getName());


	/*
	 * Get available roles
	 */
	public ArrayList<Role> getRoles(Connection sd, int o_id) throws SQLException {
		PreparedStatement pstmt = null;
		ArrayList<Role> roles = new ArrayList<Role> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			/*
			 * Get the roles for this organisation
			 */
			sql = "SELECT r.id as id, "
					+ "r.name as name, "
					+ "r.description as desc,"
					+ "r.changed_by as changed_by,"
					+ "r.changed_ts as changed_ts "
					+ "from role r "
					+ "where r.o_id = ? "
					+ "order by r.name asc";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, o_id);
			log.info("Get user roles: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			Role role = null;
			while(resultSet.next()) {
				role = new Role();
				role.oId = o_id;
				role.id = resultSet.getInt("id");
				role.name = resultSet.getString("name");
				role.desc = resultSet.getString("desc");
				role.changed_by = resultSet.getString("changed_by");
				role.changed_ts = resultSet.getString("changed_ts");
				roles.add(role);
			}
			

					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return roles;
	}

	
	/*
	 * Create a new Role
	 */
	public void createRole(Connection sd, 
			Role r, 
			int o_id, 
			String ident) throws Exception {
		
		String sql = "insert into role (o_id, name, description, changed_by, changed_ts) " +
				" values (?, ?, ?, ?, now());";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, o_id);
			pstmt.setString(2, r.name);
			pstmt.setString(3, r.desc);
			pstmt.setString(4, ident);

			log.info("SQL: " + pstmt.toString());
			pstmt.executeUpdate();
			
			
		}  finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}

	}
	
	/*
	 * Update a new Role
	 */
	public void updateRole(Connection sd, 
			Role r, 
			int o_id, 
			String ident) throws Exception {
		
		String sql = "update role set name = ?, "
				+ "description = ?,"
				+ "changed_by = ?,"
				+ "changed_ts = now() "
				+ "where o_id = ? "
				+ "and id = ?"; 
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);

			pstmt.setString(1, r.name);
			pstmt.setString(2, r.desc);
			pstmt.setString(3, ident);
			pstmt.setInt(4, o_id);
			pstmt.setInt(5, r.id);

			log.info("SQL: " + pstmt.toString());
			pstmt.executeUpdate();
			
			
		}  finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}

	}
	
	/*
	 * delete roles
	 */
	public void deleteRoles(Connection sd, 
			ArrayList<Role> rArray, 
			int o_id) throws Exception {
		
		String sql = "delete from role where o_id = ? and id = ?";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			
			for(int i = 0; i < rArray.size(); i++) {
			
				pstmt.setInt(1, o_id);
				pstmt.setInt(2, rArray.get(i).id);
			
				pstmt.executeUpdate();
			}
			
			
		}  finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}

	}
	
	/*
	 * Get roles associated with a survey
	 */
	public ArrayList<Role> getSurveyRoles(Connection sd, int s_id) throws SQLException {
		PreparedStatement pstmt = null;
		ArrayList<Role> roles = new ArrayList<Role> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			sql = "SELECT r.id as id, r.name as name, sr.enabled, sr.id as linkid " +
					" from role r "
					+ "left outer join survey_role sr " +
					" on r.id = sr.r_id " +
					" and sr.s_id = ? " +
					" order by r.name asc";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, s_id);
			log.info("Get survey roles: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			Role role = null;
			while(resultSet.next()) {		
				role = new Role();
				role.linkid = resultSet.getInt("linkid");
				role.id = resultSet.getInt("id");
				role.name = resultSet.getString("name");
				role.enabled = resultSet.getBoolean("enabled");
				
				roles.add(role);
			}
			

					    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return roles;
	}
	
	/*
	 * Update a survey link
	 */
	public int updateSurveyLink(Connection sd, int sId, int rId, int linkId, boolean enabled) throws SQLException {
		
		PreparedStatement pstmt = null;
		int newLinkId = linkId;
		
		try {
			String sqlNew = "insert into survey_role (s_id, r_id, enabled) values (?, ?, ?)";
			String sqlExisting = "update survey_role "
					+ "set enabled = ? "
					+ "where id = ? "
					+ "and s_id = ?";
			
			if(linkId > 0) {
				pstmt = sd.prepareStatement(sqlExisting);
				pstmt.setBoolean(1, enabled);
				pstmt.setInt(2, linkId);
				pstmt.setInt(3, sId);
			} else {
				pstmt = sd.prepareStatement(sqlNew, Statement.RETURN_GENERATED_KEYS);
				pstmt.setInt(1, sId);
				pstmt.setInt(2, rId);
				pstmt.setBoolean(3, enabled);	
			}
			
			log.info("Get update survey roles: " + pstmt.toString());
			pstmt.executeUpdate();
			
			if(linkId == 0) {
				ResultSet rs = pstmt.getGeneratedKeys();
				if(rs.next()) {
					newLinkId = rs.getInt(1);
				} 
			}
			    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return newLinkId;
	}

}
