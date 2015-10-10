package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.Project;
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

public class UserManager {
	
	private static Logger log =
			 Logger.getLogger(UserManager.class.getName());


	/*
	 * Get the user details
	 */
	public User getByIdent(
			Connection connectionSD,
			String ident
			) throws Exception {
		
		PreparedStatement pstmt = null;
		
		User user = new User ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;			
				
			/*
			 * Get the user details
			 */
			sql = "SELECT u.id as id, " +
					"u.name as name, " +
					"u.settings as settings, " +
					"u.signature as signature, " +
					"u.language as language, " +
					"u.email as email, " +
					"u.current_project_id as current_project_id, " +
					"u.current_survey_id as current_survey_id, " +
					"o.id as o_id, " +
					"o.name as organisation_name, " +
					"o.company_name as company_name, " +
					"o.company_address as company_address, " +
					"o.company_phone as company_phone, " +
					"o.company_email as company_email, " +
					"o.allow_email, " +
					"o.allow_facebook, " +
					"o.allow_twitter, " +
					"o.can_edit, " +
					"o.ft_send_trail " +
					" from users u, organisation o " +
					" where u.ident = ? " +
					" and u.o_id = o.id " +
					" order by u.ident;"; 
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, ident);
			log.info("Get user details: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
		
			while(resultSet.next()) {
				user.id = resultSet.getInt("id");
				user.ident = ident;
				user.name = resultSet.getString("name");
				user.settings = resultSet.getString("settings");
				user.signature = resultSet.getString("signature");
				user.language = resultSet.getString("language");
				user.email = resultSet.getString("email");
				user.current_project_id = resultSet.getInt("current_project_id");
				user.current_survey_id = resultSet.getInt("current_survey_id");
				user.o_id = resultSet.getInt("o_id");
				user.organisation_name = resultSet.getString("organisation_name");
				user.company_name = resultSet.getString("company_name");
				user.company_address = resultSet.getString("company_address");
				user.company_phone = resultSet.getString("company_phone");
				user.company_email = resultSet.getString("company_email");
				user.allow_email = resultSet.getBoolean("allow_email");
				user.allow_facebook = resultSet.getBoolean("allow_facebook");
				user.allow_twitter = resultSet.getBoolean("allow_twitter");
				user.can_edit = resultSet.getBoolean("can_edit");
				user.ft_send_trail = resultSet.getBoolean("ft_send_trail");
			}
			
			/*
			 * Set a flag if email is enabled on the server
			 */
			user.sendEmail = UtilityMethodsEmail.getSmtpHost(connectionSD, null, ident) != null;
			
			/*
			 * Get the groups that the user belongs to
			 */
			sql = "SELECT g.id as id, g.name as name " +
					" from groups g, user_group ug " +
					" where g.id = ug.g_id " +
					" and ug.u_id = ? " +
					" order by g.name;";
			
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, user.id);
			log.info("SQL: " + sql + ":" + user.id);
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				if(user.groups == null) {
					user.groups = new ArrayList<UserGroup> ();
				}
				UserGroup group = new UserGroup();
				group.id = resultSet.getInt("id");
				group.name = resultSet.getString("name");
				user.groups.add(group);
			}
			
			/*
			 * Get the projects that the user belongs to
			 */
			sql = "SELECT p.id as id, p.name as name " +
					" from project p, user_project up " +
					" where p.id = up.p_id " +
					" and up.u_id = ? " +
					" order by p.name;";

			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, user.id);
			log.info("SQL: " + sql + ":" + user.id);
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				if(user.projects == null) {
					user.projects = new ArrayList<Project> ();
				}
				Project project = new Project();
				project.id = resultSet.getInt("id");
				project.name = resultSet.getString("name");
				user.projects.add(project);
			}				
				
		} catch (Exception e) {
			
		    throw new Exception(e);
		    
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}

		}
		
		return user;
		
	}


	
}
