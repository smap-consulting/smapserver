package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.RoleColumnFilter;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.SqlFragParam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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
	public ArrayList<Role> getSurveyRoles(Connection sd, int s_id, int o_id) throws SQLException {
		PreparedStatement pstmt = null;
		ArrayList<Role> roles = new ArrayList<Role> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			sql = "SELECT r.id as id, "
					+ "r.name as name, "
					+ "sr.enabled, "
					+ "sr.id as linkid,"
					+ "sr.row_filter,"
					+ "sr.column_filter "
					+ "from role r "
					+ "left outer join survey_role sr "
					+ "on r.id = sr.r_id "
					+ "and sr.s_id = ? "
					+ "where o_id = ? "
					+ "order by r.name asc";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, s_id);
			pstmt.setInt(2, o_id);
			log.info("Get survey roles: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			Role role = null;
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			Type colFilterType = new TypeToken<ArrayList<RoleColumnFilter>>(){}.getType();
			while(resultSet.next()) {		
				role = new Role();
				role.linkid = resultSet.getInt("linkid");
				role.id = resultSet.getInt("id");
				role.name = resultSet.getString("name");
				role.enabled = resultSet.getBoolean("enabled");
				
				String sqlFragString = resultSet.getString("row_filter");
				if(sqlFragString != null) {
					SqlFrag sq = gson.fromJson(sqlFragString, SqlFrag.class);
					if(sq.sql != null) {
						role.row_filter = sq.raw.toString();
					}
				}
				
				String colFilter = resultSet.getString("column_filter");
				if(colFilter != null) {
					role.column_filter = gson.fromJson(colFilter, colFilterType);
				}
				
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
	
	/*
	 * Update the row filter in a survey link
	 */
	public void updateSurveyRoleRowFilter(Connection sd, int sId, 
			Role role, ResourceBundle localisation) throws Exception {
		
		PreparedStatement pstmt = null;
		
		SqlFrag sq = new SqlFrag();
		sq.addRaw(role.row_filter, localisation);
		StringBuilder bad = new StringBuilder();
		for(int i = 0; i < sq.columns.size(); i++) {
			if(!GeneralUtilityMethods.surveyHasQuestion(sd, sId, sq.columns.get(i))) {
				if(bad.length() > 0) {
					bad.append(", ");
				}
				bad.append(sq.columns.get(i));
			}
		}
		if(bad.length() > 0) {
			throw new Exception(localisation.getString("r_mc") + " " + bad);
		}
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		String configString = gson.toJson(sq);
		
		try {
			String sql = "update survey_role "
					+ "set row_filter = ? "
					+ "where id = ? "
					+ "and s_id = ?";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, configString);
			pstmt.setInt(2, role.linkid);
			pstmt.setInt(3, sId);
			
			log.info("Update survey roles: " + pstmt.toString());
			pstmt.executeUpdate();

			    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
	}
	
	/*
	 * Update the column filter in a survey link
	 */
	public void updateSurveyRoleColumnFilter(Connection sd, int sId, 
			Role role, ResourceBundle localisation) throws Exception {
		
		PreparedStatement pstmt = null;
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		String configString = gson.toJson(role.column_filter);
		
		try {
			String sql = "update survey_role "
					+ "set column_filter = ? "
					+ "where id = ? "
					+ "and s_id = ?";
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, configString);
			pstmt.setInt(2, role.linkid);
			pstmt.setInt(3, sId);
			
			log.info("Update survey roles: " + pstmt.toString());
			pstmt.executeUpdate();

			    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
	}
	
	/*
	 * Get the sql for a survey role filter for a specific user and survey
	 * A user can have multiple roles as can a survey hence an array of roles is returned
	 */
	public ArrayList<SqlFrag> getSurveyRowFilter(Connection sd, int sId, String user) throws SQLException {
		
		PreparedStatement pstmt = null;
		ArrayList<SqlFrag> rfArray = new ArrayList<SqlFrag> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			sql = "SELECT sr.row_filter "
					+ "from survey_role sr, user_role ur, users u "
					+ "where sr.s_id = ? "
					+ "and sr.r_id = ur.r_id "
					+ "and ur.u_id = u.id "
					+ "and u.ident = ?";
							
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, user);
			log.info("Get surveyRowFilter: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			while(resultSet.next()) {		
				String sqlFragString = resultSet.getString("row_filter");
				if(sqlFragString != null) {
					rfArray.add(gson.fromJson(sqlFragString, SqlFrag.class));
				}		
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return rfArray;
	}
	
	/*
	 * Convert an array of sql fragments into raw SQL
	 */
	public String convertSqlFragsToSql(ArrayList<SqlFrag> rfArray) {
		StringBuffer rfString = new StringBuffer("");
		StringBuffer sqlFilter = new StringBuffer("");
		if(rfArray.size() > 0) {
			for(SqlFrag rf : rfArray) {
				if(rf.columns.size() > 0) {
					if(rfString.length() > 0) {
						rfString.append(" or");
					}
					rfString.append(" (");
					rfString.append(rf.sql.toString());
					rfString.append(")");
				}
			}
			sqlFilter.append("(");
			sqlFilter.append(rfString);
			sqlFilter.append(")");
		}
		return sqlFilter.toString();
	}
	
	/*
	 * Set the parameters for RBAC statement
	 */
	public int setRbacParameters(PreparedStatement pstmt, ArrayList<SqlFrag> rfArray, int index) throws SQLException {
		int attribIdx = index;
		for(SqlFrag rf : rfArray) {
			for(int i = 0; i < rf.params.size(); i++) {
				SqlFragParam p = rf.params.get(i);
				if(p.type.equals("text")) {
					pstmt.setString(attribIdx++, p.sValue);
				} else if(p.type.equals("integer")) {
					pstmt.setInt(attribIdx++,  p.iValue);
				} else if(p.type.equals("double")) {
					pstmt.setDouble(attribIdx++,  p.dValue);
				}
			}
		}
		return attribIdx;
	}
	
	/*
	 * Get the sql for a survey role column filter for a specific user and survey
	 * A user can have multiple roles as can a survey hence an array of roles is returned
	 */
	public ArrayList<RoleColumnFilter> getSurveyColumnFilter(Connection sd, int sId, String user) throws SQLException {
		
		PreparedStatement pstmt = null;
		ArrayList<RoleColumnFilter> cfArray = new ArrayList<RoleColumnFilter> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			sql = "SELECT sr.column_filter "
					+ "from survey_role sr, user_role ur, users u "
					+ "where sr.s_id = ? "
					+ "and sr.r_id = ur.r_id "
					+ "and ur.u_id = u.id "
					+ "and u.ident = ?";
							
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, user);
			log.info("Get surveyColumnFilter: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
							
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			Type cfArrayType = new TypeToken<ArrayList<RoleColumnFilter>>(){}.getType();
			
			while(resultSet.next()) {		
				String sqlFragString = resultSet.getString("column_filter");
				if(sqlFragString != null) {
					ArrayList<RoleColumnFilter> cols = gson.fromJson(sqlFragString, cfArrayType);
					cfArray.addAll(cols);
				}		
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return cfArray;
	}

}
