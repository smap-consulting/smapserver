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
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;
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

/*
 * This class supports access to User and Organsiation information in the database
 */
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
					"u.current_task_group_id as current_task_group_id, " +
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
				String sigFile = resultSet.getString("signature");
				
				if(sigFile != null) {
					sigFile= sigFile.trim();
					if(sigFile.startsWith("/")) {	// Old versions of smap stored a URL rather than the file name, get the file name if this is the case
						int idx = sigFile.lastIndexOf("/");
						sigFile = sigFile.substring(idx + 1);
					}
					user.signature = sigFile;
				}
				user.language = resultSet.getString("language");
				user.email = resultSet.getString("email");
				user.current_project_id = resultSet.getInt("current_project_id");
				user.current_survey_id = resultSet.getInt("current_survey_id");
				user.current_task_group_id = resultSet.getInt("current_task_group_id");
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
			log.info("SQL: " + pstmt.toString());
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
					" and up.restricted = false " +
					" and up.allocated = true " +
					" order by p.name;";

			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, user.id);
			
			log.info("SQL: " + pstmt.toString());
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
			log.log(Level.SEVERE,"Error", e);
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
	
	/*
	 * Create a new user Parameters:
	 *   u: Details of the new user
	 *   isOrgUser:  Set to true if this user should be an organisational administrator
	 *   userIdent:  The ident of the user creating this user
	 *   serverName: The name of the server they are being created on
	 *   adminName:  The full name of the user creating this user
	 */
	public int createUser(Connection sd, 
			User u, 
			int o_id, 
			boolean isOrgUser, 
			boolean isSecurityManager,
			String userIdent,
			String serverName,
			String adminName,
			ResourceBundle localisation) throws Exception {
		
		int u_id = -1;
		String sql = "insert into users (ident, realm, name, email, o_id, password) " +
				" values (?, ?, ?, ?, ?, md5(?));";
		
		PreparedStatement pstmt = null;
		
		try {
			String pwdString = u.ident + ":smap:" + u.password;
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setString(1, u.ident);
			pstmt.setString(2, "smap");
			pstmt.setString(3, u.name);
			pstmt.setString(4, u.email);
			pstmt.setInt(5, o_id);
			pstmt.setString(6, pwdString);
			log.info("SQL: " + pstmt.toString());
			pstmt.executeUpdate();
			
			ResultSet rs = pstmt.getGeneratedKeys();
			if (rs.next()){
			    u_id = rs.getInt(1);
			    insertUserGroupsProjects(sd, u, u_id, isOrgUser, isSecurityManager);
			}
			
			// Send a notification email to the user
			if(u.sendEmail) {
				log.info("Checking to see if email enabled: " + u.sendEmail);
				EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, userIdent);
				if(emailServer.smtpHost != null) {
	
					log.info("Send email");
					Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, userIdent);
					
					String subject = localisation.getString("email_ac") + " " + serverName;
					String interval = "48 hours";
					String uuid = UtilityMethodsEmail.setOnetimePassword(sd, pstmt, u.email, interval);
					ArrayList<String> idents = UtilityMethodsEmail.getIdentsFromEmail(sd, pstmt, u.email);
					String sender = "newuser";
					EmailManager em = new EmailManager();
					em.sendEmail(
							u.email, 
							uuid, 
							"newuser", 
							subject, 
							null, 
							sender, 
							adminName, 
							interval, 
							idents, 
							null, 
							null,
							null,
							organisation.getAdminEmail(), 
							emailServer,
							serverName,
							localisation);
				} else {
					throw new Exception(localisation.getString("email_ne2"));
				}
			}
		}  finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}
		return u_id;
	}
	
	/*
	 * Update a users details
	 */
	public void updateUser(Connection sd, 
			User u, 
			int o_id, 
			boolean isOrgUser, 
			boolean isSecurityManager,
			String userIdent,
			String serverName,
			String adminName) throws Exception {
		
	
		// Check the user is in the same organisation as the administrator doing the editing
		String sql = "SELECT u.id " +
				" FROM users u " +  
				" WHERE u.id = ? " +
				" AND u.o_id = ?;";				
		
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, u.id);
			pstmt.setInt(2, o_id);
			log.info("SQL: " + pstmt.toString());
			ResultSet resultSet = pstmt.executeQuery();
		
			if(resultSet.next()) {
				
				// update existing user
				String pwdString = null;
				if(u.password == null) {
					// Do not update the password
					sql = "update users set " +
							" ident = ?, " +
							" realm = ?, " +
							" name = ?, " + 
							" email = ? " +
							" where " +
							" id = ?;";
				} else {
					// Update the password
					sql = "update users set " +
							" ident = ?, " +
							" realm = ?, " +
							" name = ?, " + 
							" email = ?, " +
							" password = md5(?) " +
							" where " +
							" id = ?;";
					
					pwdString = u.ident + ":smap:" + u.password;
				}
			
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, u.ident);
				pstmt.setString(2, "smap");
				pstmt.setString(3, u.name);
				pstmt.setString(4, u.email);
				if(u.password == null) {
					pstmt.setInt(5, u.id);
				} else {
					pstmt.setString(5, pwdString);
					pstmt.setInt(6, u.id);
				}
				
				log.info("SQL: " + pstmt.toString());
				pstmt.executeUpdate();
			
				// Update the groups and projects
				insertUserGroupsProjects(sd, u, u.id, isOrgUser, isSecurityManager);
	
			} else {
				throw new Exception("Invalid user");
			}
		} finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}
	}


	private void insertUserGroupsProjects(Connection sd, User u, int u_id, boolean isOrgUser, 
			boolean isSecurityManager) throws SQLException {

		String sql;
		PreparedStatement pstmt = null;
		PreparedStatement pstmtInsertUserGroup = null;
		PreparedStatement pstmtUpdateProjectGroup = null;
		PreparedStatement pstmtInsertProjectGroup = null;
		
		log.info("Update groups and projects user id:" + u_id);
		
		// Delete existing user groups
		try {
			sd.setAutoCommit(false);
			
			String sqlInsertUserGroup = "insert into user_group (u_id, g_id) values (?, ?);";
			pstmtInsertUserGroup = sd.prepareStatement(sqlInsertUserGroup);
			pstmtInsertUserGroup.setInt(1, u_id);
			
			String sqlUpdateProjectGroup = "update user_project set allocated = true "
					+ "where u_id = ? "
					+ "and p_id = ?";
			pstmtUpdateProjectGroup = sd.prepareStatement(sqlUpdateProjectGroup);
			pstmtUpdateProjectGroup.setInt(1, u_id);
			
			String sqlInsertProjectGroup = "insert into user_project (u_id, p_id, allocated) values (?, ?, true);";
			pstmtInsertProjectGroup = sd.prepareStatement(sqlInsertProjectGroup);
			pstmtInsertProjectGroup.setInt(1, u_id);
			/*
			 * Update user groups
			 */
			if(isOrgUser) {
				sql = "delete from user_group where u_id = ?;";
			} else if(isSecurityManager) {
				sql = "delete from user_group where u_id = ? and g_id != 4;";					// Cannot change super user group
			} else {
				sql = "delete from user_group where u_id = ? and g_id != 4 and g_id != 6;";		// Cannot change super user group, or security manager
			}
					
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, u.id);
			log.info("SQL: " + pstmt.toString());
			pstmt.executeUpdate();
			
			for(int j = 0; j < u.groups.size(); j++) {
				UserGroup g = u.groups.get(j);
				if(g.id != 4 || isOrgUser) {	
					pstmtInsertUserGroup.setInt(2, g.id);
					pstmtInsertUserGroup.executeUpdate();
				}
			}
			
			sd.commit();	// Commit changes to user group
			
			// Mark existing projects as un-allocated
			sql = "update user_project set allocated = false where u_id = ?;";
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, u.id);
			log.info("SQL: " + pstmt.toString());
			pstmt.executeUpdate();
			
			for(int j = 0; j < u.projects.size(); j++) {
				Project p = u.projects.get(j);
				
				pstmtUpdateProjectGroup.setInt(2, p.id);
				int count = pstmtUpdateProjectGroup.executeUpdate();
				
				if(count == 0) {
					pstmtInsertProjectGroup.setInt(2, p.id);
					pstmtInsertProjectGroup.executeUpdate();
				}
			}
			
		} catch (Exception e) {
			try{sd.rollback();} catch(Exception ex) {}
		} finally {
			sd.setAutoCommit(true);
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtInsertUserGroup != null) {pstmtInsertUserGroup.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateProjectGroup != null) {pstmtUpdateProjectGroup.close();}} catch (SQLException e) {}
			try {if (pstmtInsertProjectGroup != null) {pstmtInsertProjectGroup.close();}} catch (SQLException e) {}
		}
		
	}
	
}
