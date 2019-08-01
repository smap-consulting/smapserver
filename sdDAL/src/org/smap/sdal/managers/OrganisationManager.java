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

import javax.ws.rs.core.Response;

import org.apache.commons.fileupload.FileItem;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.AppearanceOptions;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.MySensitiveData;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.SensitiveData;
import org.smap.sdal.model.WebformOptions;

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
 * This class supports access to User and Organsiation information in the database
 */
public class OrganisationManager {
	
	LogManager lm = new LogManager();		// Application log
	
	private static Logger log =
			 Logger.getLogger(OrganisationManager.class.getName());
	
	private ResourceBundle localisation;
	
	public OrganisationManager(ResourceBundle l) {
		localisation = l;
	}

	/*
	 * Update a new organisation
	 */
	public void updateOrganisation(
			Connection sd,
			Organisation o,
			String userIdent,
			String bannerFileName,
			String mainFileName,
			String requestUrl,
			String basePath,
			FileItem bannerLogoItem,
			FileItem mainLogoItem,
			String serverName,
			String scheme
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
				" email_task = ?, " +
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
				" server_description = ?, " +
				" changed_by = ?, " + 
				" can_notify = ?, " + 
				" can_use_api = ?, " + 
				" can_submit = ?, " + 
				" set_as_theme = ?, " + 
				" navbar_color = ?, " + 
				" can_sms = ?, " + 
				" changed_ts = now() " + 
				" where " +
				" id = ?;";
	
		PreparedStatement pstmt = null;
		
		try {
			
			// Get the current settings in case we need to notify the administrator of a change
			Organisation originalOrg = GeneralUtilityMethods.getOrganisation(sd, o.id);
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, o.name);
			pstmt.setString(2, o.company_name);
			pstmt.setString(3, o.company_address);
			pstmt.setString(4, o.company_phone);
			pstmt.setString(5, o.company_email);
			pstmt.setBoolean(6, o.allow_email);
			pstmt.setBoolean(7, o.allow_facebook);
			pstmt.setBoolean(8, o.allow_twitter);
			pstmt.setBoolean(9, o.can_edit);
			pstmt.setBoolean(10, o.email_task);
			pstmt.setString(11, o.admin_email);
			pstmt.setString(12, o.smtp_host);
			pstmt.setString(13, o.email_domain);
			pstmt.setString(14, o.email_user);
			pstmt.setString(15, o.email_password);
			pstmt.setInt(16, o.email_port);
			pstmt.setString(17, o.default_email_content);
			pstmt.setString(18, o.website);
			pstmt.setString(19, o.locale);
			pstmt.setString(20, o.timeZone);
			pstmt.setString(21, o.server_description);
			pstmt.setString(22, userIdent);
			pstmt.setBoolean(23, o.can_notify);
			pstmt.setBoolean(24, o.can_use_api);
			pstmt.setBoolean(25, o.can_submit);
			pstmt.setBoolean(26, o.appearance.set_as_theme);
			pstmt.setString(27, o.appearance.navbar_color);
			pstmt.setBoolean(28, o.can_sms);
			pstmt.setInt(29, o.id);
					
			log.info("Update organisation: " + pstmt.toString());
			pstmt.executeUpdate();
	
			// Save the banner logo, if it has been passed
			if(bannerFileName != null) {
				writeLogo(sd, bannerFileName, bannerLogoItem, o.id, basePath, userIdent, requestUrl, "bannerLogo");
			}
			// Save the main logo, if it has been passed
			if(mainFileName != null) {
				writeLogo(sd, mainFileName, mainLogoItem, o.id, basePath, userIdent, requestUrl, "mainLogo");
			}
			
			/*
			 * Notify the administrator if access permissions to the organisation have been changed
			 */
			if(originalOrg.can_notify != o.can_notify 
					|| originalOrg.can_use_api != o.can_use_api 
					|| originalOrg.can_submit != o.can_submit
					|| originalOrg.email_task != o.email_task
					|| originalOrg.can_sms != o.can_sms) {
				
				EmailServer emailServer = null;
				String emailKey = null;
				
				if(originalOrg.admin_email != null) {
					emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, userIdent);
					if(emailServer.smtpHost != null) {
						
						PeopleManager pm = new PeopleManager(localisation);
						emailKey = pm.getEmailKey(sd, o.id, originalOrg.getAdminEmail());
						if(emailKey == null) {
							// Person has unsubscribed
							String msg = localisation.getString("email_us");
							msg = msg.replaceFirst("%s1", originalOrg.getAdminEmail());
							log.info(msg);
						} else {
							String subject = localisation.getString("email_org_change");
							subject = subject.replaceAll("%s1", serverName);
							subject = subject.replaceAll("%s2", originalOrg.name);
							System.out.println("Sending email confirmation: Header = " + subject);
							
							String content = localisation.getString("org_change");
							content = content.replaceAll("%s1", originalOrg.name);
							StringBuffer contentBuf = new StringBuffer(content);
							if(originalOrg.can_notify != o.can_notify) {
								contentBuf.append("\n    ").append(o.can_notify ? localisation.getString("en_notify") : localisation.getString("susp_notify"));
							}
							if(originalOrg.can_use_api != o.can_use_api) {
								contentBuf.append("\n    ").append(o.can_use_api ? localisation.getString("en_api") : localisation.getString("susp_api"));
							}
							if(originalOrg.can_submit != o.can_submit) {
								contentBuf.append("\n    ").append(o.can_submit ? localisation.getString("en_submit") : localisation.getString("susp_submit"));
							}
							if(originalOrg.can_sms != o.can_sms) {
								contentBuf.append("\n    ").append(o.can_sms ? localisation.getString("en_sms") : localisation.getString("susp_sms"));
							}
							if(originalOrg.email_task != o.email_task) {
								contentBuf.append("\n    ").append(o.email_task ? localisation.getString("en_email_tasks") : localisation.getString("susp_email_tasks"));
							}
							
							String sender = "";
							EmailManager em = new EmailManager();
							// Catch and log exceptions
							try {
								em.sendEmail(
										originalOrg.getAdminEmail(), 
										null, 
										"orgchange", 
										subject, 
										contentBuf.toString(), 
										sender, 
										"", 
										null, 
										null, 
										null, 
										null,
										null,
										originalOrg.getAdminEmail(), 
										emailServer,
										scheme,
										serverName,
										emailKey,
										localisation,
										originalOrg.server_description);
							} catch(Exception e) {
								lm.writeLogOrganisation(sd, o.id, userIdent, LogManager.ORGANISATION_UPDATE, e.getMessage());
							}
						}
					}
				}
				
			}
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			
		}
		
	}
	
	/*
	 * Create a new organisation
	 */
	public int createOrganisation(
			Connection sd,
			Organisation o,
			String userIdent,
			String bannerFileName,
			String mainFileName,
			String requestUrl,
			String basePath,
			FileItem bannerLogoItem,
			FileItem mainLogoItem,
			String email
			) throws SQLException {
		
		int o_id = 0;
		
		String sqlAddOrgList = "insert into user_organisation (u_id, o_id) values (?,?)";
		PreparedStatement pstmtAddOrgList = null;
		
		String sqlDeleteOrganisation = "delete from organisation where id = ?;";
		PreparedStatement pstmtDeleteOrganisation = null;
		
		String sqlCheckInactive = "select id from organisation "
				+ "where name = ? "
				+ "and id not in (select o_id from users where password_reset = 'true' "
				+ "or email = ?)";		// Make sure user does not have same email to discourage sending of multiple 
										//  emails to the smame possibly wrong address		
		PreparedStatement pstmtCheckInactive = null;
		
		String sql = "insert into organisation (name, company_name, "
				+ "company_address, "
				+ "company_phone, "
				+ "company_email, "
				+ "allow_email, allow_facebook, allow_twitter, can_edit, email_task, "
				+ "changed_by, admin_email, smtp_host, email_domain, email_user, email_password, "
				+ "email_port, default_email_content, website, locale, timezone, "
				+ "can_notify, can_use_api, can_submit, set_as_theme, e_id, ft_backward_navigation, ft_navigation, ft_image_size, ft_send, ft_delete, "
				+ "ft_send_location, ft_pw_policy, navbar_color, can_sms, changed_ts) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?,"
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, now());";
		PreparedStatement pstmt = null;
		
		try {
			/*
			 * If there is an existing organisation with the same name then it can be overridden if
			 * there are no users that have logged in to the organisation.  In that case it is assumed
			 * to be inactive.
			 */
			pstmtCheckInactive = sd.prepareStatement(sqlCheckInactive);
			pstmtCheckInactive.setString(1,  o.name);
			pstmtCheckInactive.setString(2,  email);
			log.info("Check for inactive organisations: " + pstmtCheckInactive.toString());
			ResultSet rs = pstmtCheckInactive.executeQuery();
			
			if(rs.next()) {
				pstmtDeleteOrganisation = sd.prepareStatement(sqlDeleteOrganisation);
				pstmtDeleteOrganisation.setInt(1, rs.getInt(1));
				
				log.info("SQL delete inactive organisation: " + pstmtDeleteOrganisation.toString());
				pstmtDeleteOrganisation.executeUpdate();
			}
			
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setString(1, o.name);
			pstmt.setString(2, o.company_name);
			pstmt.setString(3, o.company_address);
			pstmt.setString(4, o.company_phone);
			pstmt.setString(5, o.company_email);
			pstmt.setBoolean(6, o.allow_email);
			pstmt.setBoolean(7, o.allow_facebook);
			pstmt.setBoolean(8, o.allow_twitter);
			pstmt.setBoolean(9, o.can_edit);
			pstmt.setBoolean(10, o.email_task);
			pstmt.setString(11, userIdent);
			pstmt.setString(12, o.admin_email);
			pstmt.setString(13, o.smtp_host);
			pstmt.setString(14, o.email_domain);
			pstmt.setString(15, o.email_user);
			pstmt.setString(16, o.email_password);
			pstmt.setInt(17, o.email_port);
			pstmt.setString(18, o.default_email_content);
			pstmt.setString(19, o.website);
			pstmt.setString(20, o.locale);
			
			if(o.timeZone == null || o.timeZone.trim().length() == 0) {
				o.timeZone = "UTC";			// Default time zone for organisation
			}
			pstmt.setString(21, o.timeZone);
			
			pstmt.setBoolean(22, o.can_notify);
			pstmt.setBoolean(23, o.can_use_api);
			pstmt.setBoolean(24, o.can_submit);
			pstmt.setBoolean(25, o.appearance.set_as_theme);
			pstmt.setInt(26, o.e_id);			// TODO set from current organisation enterprise id
			pstmt.setString(27, "not set");		// backward navigation
			pstmt.setString(28, "not set");		// screen navigation
			pstmt.setString(29, "not set");		// image size
			pstmt.setString(30, "not set");		// send automatically
			pstmt.setString(31, "not set");		// FT delete after sending
			pstmt.setString(32, "not set");		// Send location
			pstmt.setInt(33, -1);				// Never require re-entry of FT password
			String navBarColor = o.appearance.navbar_color;
			if(navBarColor == null) {
				navBarColor =  Organisation.DEFAULT_NAVBAR_COLOR;
			}
			pstmt.setString(34,navBarColor);
			pstmt.setBoolean(35, o.can_sms);
			log.info("Insert organisation: " + pstmt.toString());
			pstmt.executeUpdate();
			
			rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
            		o_id = rs.getInt(1);
            }
            rs.close();
            
			// Save the banner logo, if it has been passed
			if(bannerFileName != null) {			
				writeLogo(sd, bannerFileName, bannerLogoItem, o_id, basePath, userIdent, requestUrl, "bannerLogo");
	        } 
			// Save the main logo, if it has been passed
			if(mainFileName != null) {			
				writeLogo(sd, mainFileName, mainLogoItem, o_id, basePath, userIdent, requestUrl, "mainLogo");
			} 
			// Add this new organisation to the requesting users list of organisations
			if(userIdent != null) {
				int u_id = GeneralUtilityMethods.getUserId(sd, userIdent);
				if(u_id > 0) {
					pstmtAddOrgList = sd.prepareStatement(sqlAddOrgList);
					pstmtAddOrgList.setInt(1, u_id);
					pstmtAddOrgList.setInt(2, o_id);
					pstmtAddOrgList.executeUpdate();	
				}
			}
	            
		} catch (SQLException e) {
			throw e;
		} finally {
			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
			try {if (pstmtCheckInactive != null) {pstmtCheckInactive.close();} } catch (SQLException e) {}
			try {if (pstmtDeleteOrganisation != null) {pstmtDeleteOrganisation.close();} } catch (SQLException e) {}
			try {if (pstmtAddOrgList != null) {pstmtAddOrgList.close();} } catch (SQLException e) {}	
		}
		
		return o_id;
		
	}
	
	private void writeLogo( 
			Connection sd, 
			String fileName, 
			FileItem logoItem,
			int oId,
			String basePath,
			String userIdent,
			String requestUrl,
			String storageName) {
		
		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setFolder(basePath, userIdent, oId, sd, true);				 
		mediaInfo.setServer(requestUrl);
		
		String folderPath = mediaInfo.getPath();
		fileName = mediaInfo.getFileName(fileName);
		if(folderPath != null) {						
			String filePath = folderPath + "/" + storageName;
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

	public void updateSensitiveData( 
			Connection sd, 
			int oId,
			SensitiveData sensitiveData) throws SQLException {
		
		String sql = "update organisation set "
				+ "sensitive_data = ? "
				+ "where id = ?";
		PreparedStatement pstmt = null;
		
		try {
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String data = gson.toJson(sensitiveData);
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, data);
			pstmt.setInt(2, oId);
			pstmt.executeUpdate();
		} finally {
			if(pstmt != null) {try{pstmt.close();}catch(Exception e) {}}
		}
	}

	
	public MySensitiveData getMySensitiveData( 
			Connection sd, 
			String user) throws SQLException {
		
		MySensitiveData msd = new MySensitiveData();
		
		String sql = "select sensitive_data "
				+ "from organisation "
				+ "where "
				+ "id = (select o_id from users where ident = ?)";	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, user);
					
			log.info("Get organisation sensitivity details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			if(rs.next()) {
				SensitiveData sensData = gson.fromJson(rs.getString(1), SensitiveData.class);
				if(sensData != null && sensData.signature != null && !sensData.signature.equals("none")) {
					if(sensData.signature.equals("admin_only")) {
						boolean isAdmin = GeneralUtilityMethods.isAdminUser(sd, user);
						if(!isAdmin) {
							msd.signature = true;
						}
					}
				}
			}
			
		} finally {
			if(pstmt != null) {try{pstmt.close();}catch(Exception e) {}}
		}
		return msd;
	}
	
	/*
	 * Get webform options
	 */
	public WebformOptions getWebform(Connection sd, String user) throws SQLException {
		
		WebformOptions webform = null;
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		String sql = "select webform "
				+ "from organisation "
				+ "where "
				+ "id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, user);
					
			log.info("Get organisation webform options: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			if(rs.next()) {
				
				String wfString =  rs.getString("webform");
				if(wfString != null && wfString.trim().startsWith("{")) {
					webform = gson.fromJson(rs.getString("webform"), WebformOptions.class);
				}									
			} 
			if(webform == null) {
				webform = new WebformOptions();
				webform.page_background_color = "#f0f0f0";
				webform.paper_background_color = "#fff";
				webform.footer_horizontal_offset = 5;
			}
			
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}	
		}
		
		return webform;
	}
	
	/*
	 * Get appearance options
	 */
	public AppearanceOptions getAppearance(Connection sd, String user) throws SQLException {
		
		String sql = "select set_as_theme, navbar_color "
				+ "from organisation "
				+ "where "
				+ "id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		AppearanceOptions ao = new AppearanceOptions();
		try {
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, user);
					
			log.info("Get organisation appearance options: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			if(rs.next()) {
				
				ao.set_as_theme = rs.getBoolean(1);
				ao.navbar_color = rs.getString(2);								
			} 
			if(ao.navbar_color == null) {
				ao.navbar_color = Organisation.DEFAULT_NAVBAR_COLOR;
			}
			
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}	
		}
		
		return ao;
	}
	
}
