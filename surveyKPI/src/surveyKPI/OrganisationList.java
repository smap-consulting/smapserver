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
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CsvTableManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.OrganisationManager;
import org.smap.sdal.model.AppearanceOptions;
import org.smap.sdal.model.DeviceSettings;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.SensitiveData;
import org.smap.sdal.model.User;
import org.smap.sdal.model.WebformOptions;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Returns a list of all projects that are in the same organisation as the user making the request
 */
@Path("/organisationList")
public class OrganisationList extends Application {
	
	Authorise a = null;
	Authorise aAdmin = null;
	Authorise aSecurity = null;

	private static Logger log =
			 Logger.getLogger(OrganisationList.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	public OrganisationList() {
		
		a = new Authorise(null, Authorise.ORG);
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		aAdmin = new Authorise(authorisations, null);
		
		aSecurity = new Authorise(null, Authorise.SECURITY);
	}
	
	@GET
	@Produces("application/json")
	public Response getOrganisations(@Context HttpServletRequest request,
			@QueryParam("enterprise") int e_id) { 

		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-OrganisationList-getOrganisations");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		ArrayList<Organisation> organisations = new ArrayList<Organisation> ();
		
		try {
			
			if(e_id > 0) {
				// Check that the user is an enterprise administrator
				if(!GeneralUtilityMethods.isEntUser(sd, request.getRemoteUser())) {
					e_id = 0;
				}
			}
			
			// Use the users enterprise id if one was not specified
			if(e_id == 0) {
				e_id = GeneralUtilityMethods.getEnterpriseId(sd, request.getRemoteUser());
			}
			
			String sql = null;
			ResultSet resultSet = null;
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			
			/*
			 * Get the organisations
			 */
			sql = "select id, name, "
					+ "company_name, "
					+ "company_address, "
					+ "company_phone, "
					+ "company_email, "
					+ "allow_email, "
					+ "allow_facebook, "
					+ "allow_twitter, "
					+ "can_edit, "
					+ "can_notify, "
					+ "can_use_api, "
					+ "can_submit, "
					+ "can_sms, "
					+ "set_as_theme, "
					+ "navbar_color, "
					+ "email_task, "
					+ "changed_by, "
					+ "changed_ts," 
					+ "admin_email, "
					+ "smtp_host, "
					+ "email_domain, "
					+ "email_user, "
					+ "email_password, "
					+ "email_port, "
					+ "default_email_content, "
					+ "website, "
					+ "locale,"
					+ "timezone,"
					+ "server_description "
					+ "from organisation "
					+ "where organisation.e_id = ? "
					+ "order by name asc;";			
						
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, e_id);
			log.info("Get organisation list: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				Organisation org = new Organisation();
				org.id = resultSet.getInt("id");
				org.name = resultSet.getString("name");
				org.company_name = resultSet.getString("company_name");
				org.company_address = resultSet.getString("company_address");
				org.company_phone = resultSet.getString("company_phone");
				org.company_email = resultSet.getString("company_email");
				org.allow_email = resultSet.getBoolean("allow_email");
				org.allow_facebook = resultSet.getBoolean("allow_facebook");
				org.allow_twitter = resultSet.getBoolean("allow_twitter"); 
				org.can_edit = resultSet.getBoolean("can_edit");
				org.can_notify = resultSet.getBoolean("can_notify");
				org.can_use_api = resultSet.getBoolean("can_use_api");
				org.can_submit = resultSet.getBoolean("can_submit");
				org.can_sms = resultSet.getBoolean("can_sms");
				org.appearance.set_as_theme = resultSet.getBoolean("set_as_theme");
				org.appearance.navbar_color = resultSet.getString("navbar_color");
				org.email_task = resultSet.getBoolean("email_task");
				org.changed_by = resultSet.getString("changed_by");
				org.changed_ts = resultSet.getString("changed_ts");
				org.admin_email = resultSet.getString("admin_email");
				org.smtp_host = resultSet.getString("smtp_host");
				org.email_domain = resultSet.getString("email_domain");
				org.email_user = resultSet.getString("email_user");
				org.email_password = resultSet.getString("email_password");
				org.email_port = resultSet.getInt("email_port");
				org.default_email_content = resultSet.getString("default_email_content");
				org.website = resultSet.getString("website");
				org.locale = resultSet.getString("locale");
				if(org.locale == null) {
					org.locale = "en";	// Default english
				}
				org.timeZone = resultSet.getString("timeZone");
				if(org.timeZone == null) {
					org.timeZone = "UTC";
				}
				org.server_description = resultSet.getString("server_description");
				organisations.add(org);
			}
	
			String resp = gson.toJson(organisations);
			response = Response.ok(resp).build();
			
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-OrganisationList-getOrganisations", sd);
		}

		return response;
	}
	
	/*
	 * Update the organisation details or create a new organisation
	 */
	@POST
	public Response updateOrganisation(@Context HttpServletRequest request) throws Exception { 
		
		Response response = null;
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();	
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-OrganisationList-updateOrganisation");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		FileItem bannerLogoItem = null;
		String bannerFileName = null;
		FileItem mainLogoItem = null;
		String mainFileName = null;
		
		String organisations = null;
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
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
							organisations = item.getString();
						} catch (Exception e) {
							
						}
					}
					
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					if(item.getSize() > 0) {
						String fieldName = item.getFieldName();
						if(fieldName != null) {
							if(fieldName.equals("banner_logo")) {
								bannerLogoItem = item;
								bannerFileName = item.getName().replaceAll(" ", "_"); // Remove spaces from file name
							} else if(fieldName.equals("main_logo")) {
								mainLogoItem = item;
								mainFileName = item.getName().replaceAll(" ", "_");
							}
						}
						
					}
					
				}

			}
			
			Type type = new TypeToken<ArrayList<Organisation>>(){}.getType();		
			ArrayList<Organisation> oArray = new Gson().fromJson(organisations, type);
				
			String requestUrl = request.getRequestURL().toString();
			String userIdent = request.getRemoteUser();
			String basePath = GeneralUtilityMethods.getBasePath(request);
				
			OrganisationManager om = new OrganisationManager(localisation);
			for(int i = 0; i < oArray.size(); i++) {
				Organisation o = oArray.get(i);
				
				if(o.timeZone != null && !o.timeZone.equals("UTC")) {
					if(!GeneralUtilityMethods.isValidTimezone(sd, o.timeZone)) {
						throw new ApplicationException("Invalid Timezone: " + o.timeZone);
					}
				}
				if(o.id == -1) {
					// New organisation
						
					o.e_id = GeneralUtilityMethods.getEnterpriseId(sd, request.getRemoteUser());
					om.createOrganisation(
							sd, 
							o, 
							userIdent, 
							bannerFileName,
							mainFileName,
							requestUrl,
							basePath,
							bannerLogoItem,
							mainLogoItem,
							null);
					
						 
				} else {
					// Existing organisation

					a.isOrganisationInEnterprise(sd, request.getRemoteUser(), o.id);
					
					om.updateOrganisation(
							sd, 
							o, 
							userIdent, 
							bannerFileName,
							mainFileName,
							requestUrl,
							basePath,
							bannerLogoItem,
							mainLogoItem,
							request.getServerName(),
							request.getScheme());	
				}
			
				response = Response.ok().build();
			}
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("Update Organisation: sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).entity(e.getMessage()).build();
			} else {
				response = Response.serverError().entity(e.getMessage()).build();
				log.log(Level.SEVERE,"Error", e);
			}
		} catch (FileUploadException ex) {
			response = Response.serverError().entity(ex.getMessage()).build();
			log.log(Level.SEVERE,"Error", ex);
			
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-OrganisationList-updateOrganisation", sd);
		}
		
		return response;
	}
	
	/*
	 * Update the sensitive data for for an organisation
	 */
	@POST
	@Path("/sensitive")
	public Response updateOrganisationSensitiveData(@Context HttpServletRequest request, @FormParam("sensitive") String sensitive) { 
			
		Response response = null;	
		
		String connectionString = "surveyKPI-updateSensitiveData";
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aSecurity.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			SensitiveData sensitiveData = new Gson().fromJson(sensitive, SensitiveData.class);	
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());		
			OrganisationManager om = new OrganisationManager(localisation);
			om.updateSensitiveData(sd, oId, sensitiveData);		
			
			response = Response.ok().build();
				
		} catch (SQLException e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	@GET
	@Path("/device")
	public Response getDeviceSettings(@Context HttpServletRequest request) {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-getDeviceSettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		String sql = "select ft_delete, ft_send_location, ft_odk_style_menus, "
				+ "ft_specify_instancename, ft_admin_menu, ft_exit_track_menu, "
				+ "ft_review_final, ft_send, ft_number_tasks, ft_image_size, ft_backward_navigation,"
				+ "ft_navigation,"
				+ "ft_pw_policy "
				+ "from organisation "
				+ "where "
				+ "id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, request.getRemoteUser());
					
			log.info("Get organisation device details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			if(rs.next()) {
				DeviceSettings d = new DeviceSettings();
				d.ft_delete = rs.getString(1);
				d.ft_send_location= rs.getString(2);
				d.ft_odk_style_menus = rs.getBoolean(3);
				d.ft_specify_instancename = rs.getBoolean(4);
				d.ft_admin_menu = rs.getBoolean(5);
				d.ft_exit_track_menu = rs.getBoolean(6);
				d.ft_review_final = rs.getBoolean(7);
				d.ft_send = rs.getString(8);
				d.ft_number_tasks = rs.getInt(9);
				d.ft_image_size = rs.getString(10);
				d.ft_backward_navigation = rs.getString(11);
				d.ft_navigation = rs.getString(12);
				d.ft_pw_policy = rs.getInt(13);
				
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				String resp = gson.toJson(d);
				response = Response.ok(resp).build();
			} else {
				response = Response.serverError().entity("not found").build();
			}
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}	
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	@GET
	@Path("/webform")
	public Response getWebformSettings(@Context HttpServletRequest request) {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-getWebformSettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			OrganisationManager om = new OrganisationManager(localisation);		
			WebformOptions webform = om.getWebform(sd, request.getRemoteUser());
			
			String resp = gson.toJson(webform);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	// Get appearance settings for an organisation
	@GET
	@Path("/appearance")
	public Response getAppearanceSettings(@Context HttpServletRequest request) {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-getAppearanceSettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			OrganisationManager om = new OrganisationManager(localisation);		
			AppearanceOptions ao = om.getAppearance(sd, request.getRemoteUser());
			
			String resp = gson.toJson(ao);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	@GET
	@Path("/sensitive")
	public Response getSensitivitySettings(@Context HttpServletRequest request) {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-getSensitivitySettings";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		String sql = "select sensitive_data "
				+ "from organisation "
				+ "where "
				+ "id = (select o_id from users where ident = ?)";	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, request.getRemoteUser());
					
			log.info("Get organisation sensitivity details: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			if(rs.next()) {
				String resp = rs.getString(1);
				if(resp == null || resp.length() == 0) {
					resp = "{}";
				}
				response = Response.ok(resp).build();
			} else {
				response = Response.serverError().entity("{}").build();
			}
			
	
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}	
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}

	
	@POST
	@Path("/device")
	public Response updateDeviceSettings(
			@Context HttpServletRequest request, 
			@FormParam("settings") String settings) {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-updateDeviceSettings";
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		String sql = "update organisation set "
			
				+ " ft_delete = ?, "
				+ " ft_send_location = ?, "
				+ " ft_odk_style_menus = ?, "
				+ " ft_specify_instancename = ?, "
				+ " ft_admin_menu = ?, "
				+ " ft_exit_track_menu = ?, "
				+ " ft_review_final = ?, "
				+ " ft_send = ?, "
				+ " ft_number_tasks = ?, "
				+ " ft_image_size = ?, "
				+ " ft_backward_navigation = ?, "
				+ " ft_navigation = ?, "
				+ "ft_pw_policy = ?, "
				+ " changed_by = ?, "
				+ " changed_ts = now() "
				+ " where "
				+ " id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		try {
			DeviceSettings d = new Gson().fromJson(settings, DeviceSettings.class);
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, d.ft_delete);
			pstmt.setString(2, d.ft_send_location);
			pstmt.setBoolean(3, d.ft_odk_style_menus);
			pstmt.setBoolean(4, d.ft_specify_instancename);
			pstmt.setBoolean(5, d.ft_admin_menu);
			pstmt.setBoolean(6, d.ft_exit_track_menu);
			pstmt.setBoolean(7, d.ft_review_final);
			pstmt.setString(8, d.ft_send);
			pstmt.setInt(9, d.ft_number_tasks);
			pstmt.setString(10, d.ft_image_size);
			pstmt.setString(11, d.ft_backward_navigation);
			pstmt.setString(12, d.ft_navigation);
			pstmt.setInt(13, d.ft_pw_policy);
			pstmt.setString(14, request.getRemoteUser());
			pstmt.setString(15, request.getRemoteUser());
					
			log.info("Update organisation with device details: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
	
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}		
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}

	@POST
	@Path("/webform")
	public Response updateWebformSettings(@Context HttpServletRequest request, @FormParam("settings") String settings) throws ApplicationException {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-updateWebformSettings";
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		WebformOptions webform = gson.fromJson(settings, WebformOptions.class);
		
		/*
		 * Validate options
		 * Objective is to prevent sql injection
		 */
		Pattern pattern;
		Matcher matcher;

		String hexRegex = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
		pattern = Pattern.compile(hexRegex);

		if(webform.page_background_color != null && webform.page_background_color.trim().length() > 0) {
			matcher = pattern.matcher(webform.page_background_color);
			if(!matcher.matches()) {
				throw new ApplicationException("Invalid hex color: " + webform.page_background_color);
			}
		}	
		if(webform.paper_background_color != null && webform.paper_background_color.trim().length() > 0) {
			matcher = pattern.matcher(webform.paper_background_color);
			if(!matcher.matches()) {
				throw new ApplicationException("Invalid hex color: " + webform.paper_background_color);
			}
		}
	
		String sql = "update organisation set " +			
				" webform = ? " +
				" where " +
				" id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, gson.toJson(webform));
			pstmt.setString(2, request.getRemoteUser());
					
			log.info("Update organisation with webform details: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
	
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}		
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	@POST
	@Path("/appearance")
	public Response updateAppearanceSettings(@Context HttpServletRequest request, @FormParam("settings") String settings) throws ApplicationException {
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-updateAppearanceSettings";
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aAdmin.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		AppearanceOptions ao = gson.fromJson(settings, AppearanceOptions.class);
		
		/*
		 * Validate options
		 * Objective is to prevent sql injection
		 */
		Pattern pattern;
		Matcher matcher;

		String hexRegex = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
		pattern = Pattern.compile(hexRegex);

		if(ao.navbar_color != null && ao.navbar_color.trim().length() > 0) {
			matcher = pattern.matcher(ao.navbar_color);
			if(!matcher.matches()) {
				throw new ApplicationException("Invalid hex color: " + ao.navbar_color);
			}
		}	
	
		String sql = "update organisation set "			
				+ "set_as_theme = ?, "
				+ "navbar_color = ? "
				+ "where id = (select o_id from users where ident = ?)";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setBoolean(1, ao.set_as_theme);
			pstmt.setString(2, ao.navbar_color);
			pstmt.setString(3, request.getRemoteUser());
					
			log.info("Update organisation with appearance details: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
	
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}		
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	/*
	 * Delete an organisation
	 */
	@DELETE
	@Consumes("application/json")
	public Response delOrganisation(@Context HttpServletRequest request, @FormParam("organisations") String organisations) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-OrganisationList-delOrganisation");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Organisation>>(){}.getType();		
		ArrayList<Organisation> oArray = new Gson().fromJson(organisations, type);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtDrop = null;
		try {	
			
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String sql = null;
			ResultSet resultSet = null;
			sd.setAutoCommit(false);
				
			for(int i = 0; i < oArray.size(); i++) {
				Organisation o = oArray.get(i);
				a.isOrganisationInEnterprise(sd, request.getRemoteUser(), o.id);
				
				/*
				 * Ensure that there are no undeleted projects with surveys in this organisation
				 */
				sql = "SELECT count(*) " +
						" from project p, survey s " +  
						" where p.id = s.p_id " +
						" and p.o_id = ? " +
						" and s.deleted = 'false';";
					
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, o.id);
				log.info("SQL check for projects in an organisation: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					int count = resultSet.getInt(1);
					if(count > 0) {
						log.info("Count of undeleted projects:" + count);
						log.info("Count of undeleted pganisations:" + count);
						String msg = localisation.getString("msg_undel_orgs");
						msg = msg.replace("%s1", o.name);
						throw new Exception(msg);
					}
				} else {
					throw new Exception("Error getting project count");
				}
					
				sql = "DELETE FROM organisation o " +  
						" WHERE o.id = ?; ";			
				
				if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, o.id);
				log.info("SQL: " + sql + ":" + o.id);
				pstmt.executeUpdate();
				
			    // Delete the organisation shared resources - not necessary
			    CsvTableManager tm = new CsvTableManager(sd, localisation);
			    tm.delete(o.id, 0, null);		
			    
				// Delete the organisation folder
				String basePath = GeneralUtilityMethods.getBasePath(request);
				String fileFolder = basePath + "/media/organisation/" + o.id;
			    File folder = new File(fileFolder);
			    try {
			    	log.info("Deleting organisation folder: " + fileFolder);
					FileUtils.deleteDirectory(folder);
				} catch (IOException e) {
					log.info("Error deleting organisation folder:" + fileFolder + " : " + e.getMessage());
				}	    
			}
			
			response = Response.ok().build();
			sd.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("Delete organisation: sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
			try{
				sd.rollback();
			} catch(Exception e2) {
				
			}
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtDrop != null) {pstmtDrop.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-OrganisationList-delOrganisation", sd);
		}
		
		return response;
	}
	
	/*
	 * Change the organisation a user or project belongs to
	 */
	@POST
	@Path("/setOrganisation")
	@Consumes("application/json")
	public Response changeOrganisation(@Context HttpServletRequest request,
			@FormParam("orgId") int orgId,
			@FormParam("users") String users,
			@FormParam("projects") String projects) { 
		
		Response response = null;
		String connectionString = "surveyKPI-OrganisationList-setOrganisation";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		a.isOrganisationInEnterprise(sd, request.getRemoteUser(), orgId);
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<User>>(){}.getType();		
		ArrayList<User> uArray = new Gson().fromJson(users, type);
		
		type = new TypeToken<ArrayList<Project>>(){}.getType();		
		ArrayList<Project> pArray = new Gson().fromJson(projects, type);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		PreparedStatement pstmt3 = null;
		PreparedStatement pstmt4 = null;
		try {	
			sd.setAutoCommit(false);
			
			String sql = "update users set o_id =  ? " +  
					" WHERE id = ?; ";			
			String sql2 = "delete from user_project where u_id = ? and " +
					"p_id not in (select id from project where o_id = ?);";	
			String sql3 = "update project set o_id =  ? " +  
					" WHERE id = ?; ";			
			String sql4 = "delete from user_project where p_id = ? and " +
					"u_id not in (select id from users where o_id = ?); ";	
			
	
			pstmt = sd.prepareStatement(sql);
			pstmt2 = sd.prepareStatement(sql2);	
			pstmt3 = sd.prepareStatement(sql3);	
			pstmt4 = sd.prepareStatement(sql4);	

			// Move Users = deprecate
			if(uArray != null) {
				for(int i = 0; i < uArray.size(); i++) {
					pstmt.setInt(1, orgId);
					pstmt.setInt(2, uArray.get(i).id);
	
					log.info("Move User: " + pstmt.toString());
					pstmt.executeUpdate();
					
					log.info("userevent: " + request.getRemoteUser() + " : move user : " + uArray.get(i).id + " to: " + orgId);
				}
			}
			
			// Move Projects
			if(pArray != null) {
				for(int i = 0; i < pArray.size(); i++) {
					pstmt3.setInt(1, orgId);
					pstmt3.setInt(2, pArray.get(i).id);
					
					log.info("Move Project: " + pstmt3.toString());
					pstmt3.executeUpdate();
					
					log.info("userevent: " + request.getRemoteUser() + " : move project : " + pArray.get(i).id + " to: " + orgId);
				}
			}
			
			// Remove projects from users if they are in a different organisation
			if(uArray != null) {
				for(int i = 0; i < uArray.size(); i++) {
					
					if(!uArray.get(i).keepProjects) {	// Org admin users keep all of their projects
					
						pstmt2.setInt(1, uArray.get(i).id);
						pstmt2.setInt(2, orgId);
						log.info("Delete Links to projects: " + pstmt2.toString());
						pstmt2.executeUpdate();
					}
				}
			}
			
			// Move users from projects if they are in a different organisation
			if(pArray != null) {
				for(int i = 0; i < pArray.size(); i++) {
					
					pstmt4.setInt(1, pArray.get(i).id);
					pstmt4.setInt(2, orgId);
					log.info("Delete Links to users: " + pstmt4.toString());
					pstmt4.executeUpdate();
	
				}
			}
			
			response = Response.ok().build();
			sd.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("Change organisation. sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();			
			try{	sd.rollback();	} catch(Exception e2) {}
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			try {if (pstmt2 != null) {pstmt2.close();}	} catch (SQLException e) {}
			try {if (pstmt3 != null) {pstmt3.close();}	} catch (SQLException e) {}
			try {if (pstmt4 != null) {pstmt4.close();}	} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	/*
	 * Change the enterprise and organisation belongs to
	 */
	@POST
	@Path("/setEnterprise")
	@Consumes("application/json")
	public Response changeEnterprise(@Context HttpServletRequest request,
			@FormParam("orgId") int orgId,
			@FormParam("entId") int entId) throws SQLException { 
		
		Response response = null;
		
		String connectionString = "surveyKPI-OrganisationList-setEnterprise";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		
		PreparedStatement pstmt = null;
	
		try {	
			
			String sql = "update organisation set e_id =  ? " +  
					" WHERE id = ?; ";			
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1,  entId);
			pstmt.setInt(2, orgId);
			pstmt.executeUpdate();
			log.info("Move organisation: " + pstmt.toString());
			
			lm.writeLog(sd, -1, request.getRemoteUser(), LogManager.MOVE_ORGANISATION, "Organisation " + orgId + " moved to enterprise " + entId);
			
			response = Response.ok().build();
				
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}

}

