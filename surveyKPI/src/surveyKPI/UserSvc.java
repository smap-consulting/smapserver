package surveyKPI;

/*
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

*/

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.Alert;
import org.smap.sdal.model.GroupSurvey;
import org.smap.sdal.model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Manages the logged in user's details
 */
@Path("/user")
public class UserSvc extends Application {
	
	// Authorisation not required

	private static Logger log =
			 Logger.getLogger(UserSvc.class.getName());

	LogManager lm = new LogManager();		// Application log
	
	private class AlertStatus {
		public String lastalert;
		public boolean seen;
	}
	
	@GET
	@Produces("application/json")
	public Response getUserDetails(@Context HttpServletRequest request) { 

		Response response = null;

		// Authorisation - Not required
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		User user = null;
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			UserManager um = new UserManager(localisation);
			user = um.getByIdent(sd, request.getRemoteUser());

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(user);
			response = Response.ok(resp).build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		

		return response;
	}
	
	/*
	 * Get alerts assigned to a user
	 */
	@GET
	@Path("/alerts")
	@Produces("application/json")
	public Response getMyAlerts(@Context HttpServletRequest request) { 

		Response response = null;

		// Authorisation - Not required
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			UserManager um = new UserManager(localisation);
			ArrayList<Alert> alerts = um.getAlertsByIdent(sd, request.getRemoteUser());

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(alerts);
			response = Response.ok(resp).build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		

		return response;
	}
	
	
	/*
	 * Update the alert status for a user
	 * This includes the last time they were sent an alert
	 * If they have acknowledged the alert
	 */
	@POST
	@Path("/alertstatus")
	@Consumes("application/json")
	public Response alertStatus(@Context HttpServletRequest request, @FormParam("alertstatus") String alertStatus) { 
		
		Response response = null;

		// Authorisation - Not Required
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
			
		AlertStatus as = new Gson().fromJson(alertStatus, AlertStatus.class);
		
		String sql = "update users set lastalert = ?, "
				+ "seen = ?"
				+ "where ident = ?;";
		PreparedStatement pstmt = null;
		try {	
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, as.lastalert);
			pstmt.setBoolean(2, as.seen);
			pstmt.setString(3, request.getRemoteUser());
			
			log.info("Update alert status: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
			
			
		} catch (SQLException e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		
		return response;
	}
	
	/*
	 * Update the user settings
	 */
	@POST
	@Consumes("application/json")
	public Response updateUser(@Context HttpServletRequest request, @FormParam("user") String user) { 
		
		Response response = null;

		// Authorisation - Not Required
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		Type type = new TypeToken<User>(){}.getType();		
		User u = new Gson().fromJson(user, type);		// The user settings
		
		PreparedStatement pstmt = null;
		try {	
			
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Ensure email is null if it has not been set
			if(u.email != null && u.email.trim().isEmpty()) {
				u.email = null;
			}
			
			boolean updateOrg = false;
			if(u.o_id > 0) {
				updateOrg = true;
			} 
			
			if(updateOrg) {
				/*
				 * The user is moving themselves to a different organisation
				 * If the new settings are null then they are not authorised to move to this new organisation
				 */				
				UserManager um = new UserManager(localisation);
				try {
					um.switchUsersOrganisation(sd, u.o_id,	request.getRemoteUser(), true);
				} catch (Exception e) {
					// log but otherwise ignore any errors
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			
			
			/*
			 * Update what can be updated by the user, excluding the current project id, survey id, form id and task group
			 */
			String pwdString = null;
			String sql = null;
			String ident = request.getRemoteUser();
			if(u.password == null) {
				// Do not update the password
				sql = "update users set "
						+ "name = ?, "
						+ "settings = ?, "
						+ "language = ?, "
						+ "email = ?, "
						+ "timezone = ? "
						+ "where "
						+ "ident = ?";
			} else {
				// Update the password
				sql = "update users set "
						+ "name = ?, " 
						+ "settings = ?, "
						+ "language = ?, "
						+ "email = ?, "
						+ "timezone = ?, "
						+ "password = md5(?) "
						+ "where "
						+ "ident = ?";
				
				pwdString = ident + ":smap:" + u.password;
			}
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, u.name);
			pstmt.setString(2, u.settings);
			pstmt.setString(3, u.language);
			pstmt.setString(4, u.email);
			pstmt.setString(5, u.timezone);
			if(u.password == null) {
				pstmt.setString(6, ident);
			} else {
				pstmt.setString(6, pwdString);
				pstmt.setString(7, ident);
			}
			
			log.info("userevent: " + request.getRemoteUser() + (u.password == null ? " : updated user details : " : " : updated password : ") + u.name);
			lm.writeLog(sd, -1, request.getRemoteUser(), "user details", (u.password == null ? "updated user details" : "updated password"));
			log.info("Update user details: " + pstmt.toString());
			pstmt.executeUpdate();
			
			UserManager um = new UserManager(localisation);
			User userResp = um.getByIdent(sd, request.getRemoteUser());

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(userResp);
			response = Response.ok(resp).build();
			
			
		} catch (Exception e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		
		return response;
	}
	
	/*
	 * Update the current survey, project and task group
	 */
	@POST
	@Consumes("application/json")
	@Path("/currentproject")
	public Response updateCurrentProject(@Context HttpServletRequest request, @FormParam("user") String user) { 
		
		Response response = null;

		// Authorisation - Not Required
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
			
		User u = new Gson().fromJson(user, User.class);		// The user settings
		
		PreparedStatement pstmt = null;
		try {	
			
			// Localisation			
			//Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			//ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		
			String sql = null;
			if(u.current_project_id > 0) {
				sql = "update users set current_project_id = ?, "
						+ "current_survey_id = ?, "
						+ "current_task_group_id = ? "
						+ "where ident = ?";
			} else if(u.current_survey_id > 0) {
				// Only update the survey id
				sql = "update users set current_survey_id = ? "
						+ "where ident = ?";
			} else if(u.current_task_group_id > 0) {
				// Only update the task group id
				sql = "update users set current_task_group_id = ? where ident = ?";
			}
						
			pstmt = sd.prepareStatement(sql);
			if(u.current_project_id > 0) {
				pstmt.setInt(1, u.current_project_id);
				pstmt.setInt(2, u.current_survey_id);
				pstmt.setInt(3, u.current_task_group_id);
				pstmt.setString(4, request.getRemoteUser());
			} else if(u.current_survey_id > 0) {
				pstmt.setInt(1, u.current_survey_id);
				pstmt.setString(2, request.getRemoteUser());
			} else if(u.current_task_group_id > 0) {
				pstmt.setInt(1, u.current_task_group_id);
				pstmt.setString(2, request.getRemoteUser());
			}
				
			int count = pstmt.executeUpdate();
			if(count == 0) {
				log.info("Failed to update current project id and survey id");
			}  

			response = Response.ok().build();
				
		} catch (Exception e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		
		return response;
	}
	
	/*
	 * Update the current survey, project and task group
	 */
	@POST
	@Consumes("application/json")
	@Path("/groupsurvey")
	public Response updateGroupSurvey(@Context HttpServletRequest request, @FormParam("groupSurvey") String group) { 
		
		Response response = null;
		String connectionString = "SurveyKPI - save groupsurvey";
		// Authorisation - Not Required
		Connection sd = SDDataSource.getConnection(connectionString);
		
		GroupSurvey gs = new Gson().fromJson(group, GroupSurvey.class);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtDel = null;
		try {	
			
			// Localisation			
			//Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			//ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		
			String sqlDel = "delete from group_survey "
						+ "where u_ident = ? "
						+ "and s_id = ? ";
			pstmtDel = sd.prepareStatement(sqlDel);
			
			String sql = "insert into group_survey (u_ident, s_id, group_ident) "
					+ "values (?, ? , ?) ";
			pstmt = sd.prepareStatement(sql);
			
			pstmtDel.setString(1, request.getRemoteUser());
			pstmtDel.setInt(2, gs.sId);
			pstmtDel.executeUpdate();	
			
			// Add the group ident if it is not zero length (ie set to none)
			if(gs.groupIdent != null && gs.groupIdent.length() > 0) {
			pstmt.setString(1, request.getRemoteUser());
				pstmt.setInt(2, gs.sId);
				pstmt.setString(3, gs.groupIdent);
				log.info("Update group survey: " + pstmt.toString());
				pstmt.executeUpdate();
			}


			response = Response.ok().build();
				
		} catch (Exception e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtDel != null) {pstmtDel.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}


	/*
	 * No Key - Get the user from apache authentication
	 */
	@POST
	@Path("/details")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postUserDetailsNoKey(
			@Context HttpServletRequest request) throws IOException {
		
		return updateUserDetails(request, null);
	}
	
	/*
	 * No Key Get the user from the provided key
	 */
	@POST
	@Path("/details/key/{instanceId}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postUpdateInstance(
			@Context HttpServletRequest request,
	        @PathParam("key") String key) throws IOException {
		
		return updateUserDetails(request, key);
	}

	/*
	 * Update the user details
	 * This service should be merged with the preceeding one however:
	 *  current the user settings can be updated from many places
	 *  user details on the webform screen adds a user signature which requires
	 *  a multi part mime update however for the moment we will try and avoid
	 *  affecting the other locations that update settings
	 */
	private Response updateUserDetails(@Context HttpServletRequest request, String key) { 
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();	
		fileItemFactory.setSizeThreshold(5*1024*1024); // 5 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);

		// Authorisation - Not Required
		
		Connection sd = SDDataSource.getConnection("surveyKPI-UserSvc");
		
		
		FileItem sigItem = null;
		String fileName = null;
		String sigPath = null;
		String userFolderPath = null;
		String sigFolderPath = null;
		String settings = null;
		String user = null;
		
		PreparedStatement pstmt = null;
		try {	
			
			/*
			 * Get the user
			 */
			if(key != null) {
				try {
					user = GeneralUtilityMethods.getDynamicUser(sd, key);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				log.info("Getting user with key: " + key);
			} else {
				user = request.getRemoteUser();
			}
			log.info("user: " + user);
			
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();

			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				if(item.isFormField()) {
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
				
					if(item.getFieldName().equals("settings")) {
						try {
							settings = item.getString();
						} catch (Exception e) {
							
						}
					}
					
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					// Get the base path
					String basePath = GeneralUtilityMethods.getBasePath(request);
					
					if(item.getSize() > 0) {
						sigItem = item;
						fileName = String.valueOf(UUID.randomUUID());
						int userId = GeneralUtilityMethods.getUserId(sd, user);
						
						userFolderPath = basePath + "/media/users/" +  userId;
						sigFolderPath = userFolderPath + "/sig";
						sigPath = sigFolderPath + "/" + fileName;				
						// 1. Create the users folder if it does not exist
						File folder = new File(basePath + "/media/users/");
						FileUtils.forceMkdir(folder);
						
						// 2. Create the folder for the individual user if it does not exist
						folder = new File(userFolderPath);
					    FileUtils.forceMkdir(folder);
					    
					    // 3. Delete any existing signature files
					    folder = new File(sigFolderPath);
					    try { 	
							FileUtils.deleteDirectory(folder);
						} catch (IOException e) {
							log.log(Level.SEVERE, "Error deleting signature directory");
						}
					    
					    // 4. Create the signature folder
					    folder = new File(sigFolderPath);
					    FileUtils.forceMkdir(folder);
					    
					    // 5. Save the file
					    File savedFile = new File(sigPath);
					    item.write(savedFile);
					}					
				}

			}
			
			Type type = new TypeToken<User>(){}.getType();		
			User u = new Gson().fromJson(settings, type);		
			
			// Ensure email is null if it has not been set
			if(u.email != null && u.email.trim().isEmpty()) {
				u.email = null;
			}
		

			String sql = null;
			String ident = user;
			
			if(sigPath == null && !u.delSig) {
				// Do not update the signature
				sql = "update users set " +
						" name = ?, " + 
						" settings = ? " + 
						" where " +
						" ident = ?;";
			} else {
				// Update the signature
				sql = "update users set " +
						" name = ?, " + 
						" settings = ?, " + 
						" signature = ? " +
						" where " +
						" ident = ?;";
				
			}
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, u.name);
			pstmt.setString(2, u.settings);
			if(sigPath == null && !u.delSig) {
				pstmt.setString(3, ident);
			} else {
				pstmt.setString(3, fileName);
				pstmt.setString(4, ident);
			}
			
			log.info("Update user details: " + pstmt.toString());
			log.info("userevent: " + ident + (u.password == null ? " : updated user details : " : " : updated password : ") + u.name);
			pstmt.executeUpdate();
			
			// Set the updated signature and return it in the user id 
			if(fileName != null) {
				u.signature = "/surveyKPI/file/" + fileName + "/users?type=sig";
			} else if(u.delSig) {
				u.signature = null;
			}
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(u);
			
			response = Response.ok(resp).build();
						
		} catch (Exception e) {

			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-UserSvc", sd);
		}
		
		return response;
	}


}

