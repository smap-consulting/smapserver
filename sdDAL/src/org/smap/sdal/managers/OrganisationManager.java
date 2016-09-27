package org.smap.sdal.managers;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.fileupload.FileItem;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.model.Organisation;

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
public class OrganisationManager {
	
	private static Logger log =
			 Logger.getLogger(OrganisationManager.class.getName());

	/*
	 * Update a new organisation
	 */
	public void updateOrganisation(
			Connection connectionSD,
			Organisation o,
			String userIdent,
			String fileName,
			String requestUrl,
			String basePath,
			FileItem logoItem
			) throws SQLException {
		
		String sql = "update organisation set " +
				" name = ?, " + 
				" company_name = ?, " + 
				" company_address = ?, " + 
				" company_phone = ?, " + 
				" company_email = ?, " + 
				" allow_email = ?, " +
				" allow_facebook = ?, " +
				" allow_twitter = ?, " +
				" can_edit = ?, " +
				" ft_delete_submitted = ?, " +
				" ft_send_trail = ?, " +
				" ft_sync_incomplete = ?, " +
				" admin_email = ?, " +
				" smtp_host = ?, " +
				" email_domain = ?, " +
				" email_user = ?, " +
				" email_password = ?, " +
				" email_port = ?, " +
				" default_email_content = ?, " +
				" website = ?, " +
				" locale = ?, " +
				" timezone = ?, " +
				" changed_by = ?, " + 
				" changed_ts = now() " + 
				" where " +
				" id = ?;";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, o.name);
			pstmt.setString(2, o.company_name);
			pstmt.setString(3, o.company_address);
			pstmt.setString(4, o.company_phone);
			pstmt.setString(5, o.company_email);
			pstmt.setBoolean(6, o.allow_email);
			pstmt.setBoolean(7, o.allow_facebook);
			pstmt.setBoolean(8, o.allow_twitter);
			pstmt.setBoolean(9, o.can_edit);
			pstmt.setBoolean(10, o.ft_delete_submitted);
			pstmt.setBoolean(11, o.ft_send_trail);
			pstmt.setBoolean(12, o.ft_sync_incomplete);
			pstmt.setString(13, o.admin_email);
			pstmt.setString(14, o.smtp_host);
			pstmt.setString(15, o.email_domain);
			pstmt.setString(16, o.email_user);
			pstmt.setString(17, o.email_password);
			pstmt.setInt(18, o.email_port);
			pstmt.setString(19, o.default_email_content);
			pstmt.setString(20, o.website);
			pstmt.setString(21, o.locale);
			pstmt.setString(22, o.timeZone);
			pstmt.setString(23, userIdent);
			pstmt.setInt(24, o.id);
					
			log.info("Update organisation: " + pstmt.toString());
			pstmt.executeUpdate();
	
			// Save the logo, if it has been passed
			if(fileName != null) {
				writeLogo(connectionSD, fileName, logoItem, String.valueOf(o.id), basePath, userIdent, requestUrl);
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}
		
	}
	
	/*
	 * Create a new organisation
	 */
	public int createOrganisation(
			Connection connectionSD,
			Organisation o,
			String userIdent,
			String fileName,
			String requestUrl,
			String basePath,
			FileItem logoItem,
			String email
			) throws SQLException {
		
		int o_id = 0;
		
		String sqlDeleteOrganisation = "delete from organisation where id = ?;";
		PreparedStatement pstmtDeleteOrganisation = null;
		
		String sqlCheckInactive = "select id from organisation "
				+ "where name = ? "
				+ "and id not in (select o_id from users where password_reset = 'true' "
				+ "or email = ?)";		// Make sure user does not have same email to discourage sending of multiple 
										//  emails to the smame possibly wrong address		
		PreparedStatement pstmtCheckInactive = null;
		
		String sql = "insert into organisation (name, company_name, " +
				"company_address, " +
				"company_phone, " +
				"company_email, " +
				"allow_email, allow_facebook, allow_twitter, can_edit, ft_delete_submitted, ft_send_trail, " +
				"ft_sync_incomplete, changed_by, admin_email, smtp_host, email_domain, email_user, email_password, " +
				"email_port, default_email_content, website, locale, timezone, changed_ts) " +
				" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now());";	
		PreparedStatement pstmt = null;
		
		try {
			/*
			 * If there is an existing organisation with the same name then it can be overridden if
			 * there are no users that have logged in to the organisation.  In that case it is assumed
			 * to be inactive.
			 */
			pstmtCheckInactive = connectionSD.prepareStatement(sqlCheckInactive);
			pstmtCheckInactive.setString(1,  o.name);
			pstmtCheckInactive.setString(2,  email);
			log.info("Check for inactive organisations: " + pstmtCheckInactive.toString());
			ResultSet rs = pstmtCheckInactive.executeQuery();
			
			if(rs.next()) {
				pstmtDeleteOrganisation = connectionSD.prepareStatement(sqlDeleteOrganisation);
				pstmtDeleteOrganisation.setInt(1, rs.getInt(1));
				
				log.info("SQL delete inactive organisation: " + pstmtDeleteOrganisation.toString());
				pstmtDeleteOrganisation.executeUpdate();
			}
			
			pstmt = connectionSD.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setString(1, o.name);
			pstmt.setString(2, o.company_name);
			pstmt.setString(3, o.company_address);
			pstmt.setString(4, o.company_phone);
			pstmt.setString(5, o.company_email);
			pstmt.setBoolean(6, o.allow_email);
			pstmt.setBoolean(7, o.allow_facebook);
			pstmt.setBoolean(8, o.allow_twitter);
			pstmt.setBoolean(9, o.can_edit);
			pstmt.setBoolean(10, o.ft_delete_submitted);
			pstmt.setBoolean(11, o.ft_send_trail);
			pstmt.setBoolean(12, o.ft_sync_incomplete);
			pstmt.setString(13, userIdent);
			pstmt.setString(14, o.admin_email);
			pstmt.setString(15, o.smtp_host);
			pstmt.setString(16, o.email_domain);
			pstmt.setString(17, o.email_user);
			pstmt.setString(18, o.email_password);
			pstmt.setInt(19, o.email_port);
			pstmt.setString(20, o.default_email_content);
			pstmt.setString(21, o.website);
			pstmt.setString(22, o.locale);
			
			if(o.timeZone == null || o.timeZone.trim().length() == 0) {
				o.timeZone = "UTC";			// Default time zone for organisation
			}
			pstmt.setString(23, o.timeZone);
			log.info("Insert organisation: " + pstmt.toString());
			pstmt.executeUpdate();
			
			rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
            	o_id = rs.getInt(1);
            }
            rs.close();
            
			// Save the logo, if it has been passed
			if(fileName != null) {			
				writeLogo(connectionSD, fileName, logoItem, String.valueOf(o_id), basePath, userIdent, requestUrl);
	        } 
	            
		} catch (SQLException e) {
			throw e;
		} finally {
			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtCheckInactive != null) {pstmtCheckInactive.close();} } catch (SQLException e) {	}
			try {if (pstmtDeleteOrganisation != null) {pstmtDeleteOrganisation.close();} } catch (SQLException e) {	}
			
		}
		
		return o_id;
		
	}
	
	private void writeLogo( 
			Connection connectionSD, 
			String fileName, 
			FileItem logoItem,
			String organisationId,
			String basePath,
			String userIdent,
			String requestUrl) {
		
		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setFolder(basePath, userIdent, organisationId, connectionSD, true);				 
		mediaInfo.setServer(requestUrl);
		
		String folderPath = mediaInfo.getPath();
		fileName = mediaInfo.getFileName(fileName);
		if(folderPath != null) {						
			String filePath = folderPath + "/bannerLogo";
		    File savedFile = new File(filePath);
		    log.info("Saving file to: " + filePath);
		    try {
				logoItem.write(savedFile);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	
		} else {
			log.log(Level.SEVERE, "Media folder not found");
		}
	}

	
}
