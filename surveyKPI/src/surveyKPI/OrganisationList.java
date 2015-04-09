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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;

import utilities.MediaInfo;
import model.MediaResponse;
import model.Organisation;
import model.Project;
import model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all projects that are in the same organisation as the user making the request
 */
@Path("/organisationList")
public class OrganisationList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ORG);

	private static Logger log =
			 Logger.getLogger(OrganisationList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(OrganisationList.class);
		return s;
	}

	
	@GET
	@Produces("application/json")
	public Response getOrganisations(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ProjectList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<Organisation> organisations = new ArrayList<Organisation> ();
		
		try {
			String sql = null;
			int o_id;
			ResultSet resultSet = null;
			
			/*
			 * Get the organisation
			 */
			sql = "select id, name, " +
					" company_name, " +
					" allow_email, " +
					" allow_facebook, " +
					" allow_twitter, " +
					" can_edit, " +
					" ft_delete_submitted," +
					" ft_send_trail," +
					" changed_by, " +
					" changed_ts," +
					" admin_email, " +
					" smtp_host " +
					" from organisation " + 
					" order by name ASC;";			
						
			pstmt = connectionSD.prepareStatement(sql);
			log.info("SQL: " + sql);
			resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				Organisation org = new Organisation();
				org.id = resultSet.getInt("id");
				org.name = resultSet.getString("name");
				org.company_name = resultSet.getString("company_name");
				org.allow_email = resultSet.getBoolean("allow_email");
				org.allow_facebook = resultSet.getBoolean("allow_facebook");
				org.allow_twitter = resultSet.getBoolean("allow_twitter"); 
				org.can_edit = resultSet.getBoolean("can_edit");
				org.ft_delete_submitted = resultSet.getBoolean("ft_delete_submitted");
				org.ft_send_trail = resultSet.getBoolean("ft_send_trail");
				org.changed_by = resultSet.getString("changed_by");
				org.changed_ts = resultSet.getString("changed_ts");
				org.admin_email = resultSet.getString("admin_email");
				org.smtp_host = resultSet.getString("smtp_host");
				organisations.add(org);
			}
	
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(organisations);
			log.info("response: " + resp);
			response = Response.ok(resp).build();
			
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection: ", e);
			}
		}

		return response;
	}
	
	/*
	 * Update the settings
	 */
	@POST
	public Response updateOrganisation(@Context HttpServletRequest request) { 
		
		Response response = null;
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();	
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-OrganisationList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		FileItem logoItem = null;
		String fileName = null;
		String organisations = null;
		PreparedStatement pstmt = null;
		try {
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
						logoItem = item;
						fileName = item.getName();
						fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
					}
					
				}

			}
			
			Type type = new TypeToken<ArrayList<Organisation>>(){}.getType();		
			ArrayList<Organisation> oArray = new Gson().fromJson(organisations, type);
				
				
			String sql = null;
				
			for(int i = 0; i < oArray.size(); i++) {
				Organisation o = oArray.get(i);
				if(o.id == -1) {
					// New organisation
						
					sql = "insert into organisation (name, company_name, " +
							"allow_email, allow_facebook, allow_twitter, can_edit, ft_delete_submitted, ft_send_trail, " +
							"changed_by, admin_email, smtp_host, changed_ts) " +
							" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now());";
					
					pstmt = connectionSD.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					pstmt.setString(1, o.name);
					pstmt.setString(2, o.company_name);
					pstmt.setBoolean(3, o.allow_email);
					pstmt.setBoolean(4, o.allow_facebook);
					pstmt.setBoolean(5, o.allow_twitter);
					pstmt.setBoolean(6, o.can_edit);
					pstmt.setBoolean(7, o.ft_delete_submitted);
					pstmt.setBoolean(8, o.ft_send_trail);
					pstmt.setString(9, request.getRemoteUser());
					pstmt.setString(10, o.admin_email);
					pstmt.setString(11, o.smtp_host);
					log.info("SQL: " + sql + " : " + o.name);
					pstmt.executeUpdate();
					
					// Save the logo, if it has been passed
					if(fileName != null) {
						ResultSet rs = pstmt.getGeneratedKeys();
			            if (rs.next()) {
			            	writeLogo(request, connectionSD, fileName, logoItem, rs.getString(1));
			            } 
			            rs.close();
						
					}
						 
				} else {
					// Existing organisation

					sql = "update organisation set " +
							" name = ?, " + 
							" company_name = ?, " + 
							" allow_email = ?, " +
							" allow_facebook = ?, " +
							" allow_twitter = ?, " +
							" can_edit = ?, " +
							" ft_delete_submitted = ?, " +
							" ft_send_trail = ?, " +
							" admin_email = ?, " +
							" smtp_host = ?, " +
							" changed_by = ?, " + 
							" changed_ts = now() " + 
							" where " +
							" id = ?;";
				
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setString(1, o.name);
					pstmt.setString(2, o.company_name);
					pstmt.setBoolean(3, o.allow_email);
					pstmt.setBoolean(4, o.allow_facebook);
					pstmt.setBoolean(5, o.allow_twitter);
					pstmt.setBoolean(6, o.can_edit);
					pstmt.setBoolean(7, o.ft_delete_submitted);
					pstmt.setBoolean(8, o.ft_send_trail);
					pstmt.setString(9, o.admin_email);
					pstmt.setString(10, o.smtp_host);
					pstmt.setString(11, request.getRemoteUser());
					pstmt.setInt(12, o.id);
							
					log.info("SQL: " + sql + ":" + o.name + ":" + o.id);
					pstmt.executeUpdate();
			
					// Save the logo, if it has been passed
					if(fileName != null) {
						writeLogo(request, connectionSD, fileName, logoItem, String.valueOf(o.id));
					}
					
				}
			
				response = Response.ok().build();
			}
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).build();
			} else {
				response = Response.serverError().build();
				log.log(Level.SEVERE,"Error", e);
			}
		} catch (FileUploadException ex) {
			response = Response.serverError().entity(ex.getMessage()).build();
			log.log(Level.SEVERE,"Error", ex);
			
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	private void writeLogo(HttpServletRequest request, 
			Connection connectionSD, 
			String fileName, 
			FileItem logoItem,
			String organisationId) {
	
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");		
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		
		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setFolder(basePath, request.getRemoteUser(), organisationId, connectionSD, true);				 
		mediaInfo.setServer(request.getRequestURL().toString());
		
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
	
	/*
	 * Delete project
	 */
	@DELETE
	@Consumes("application/json")
	public Response delOrganisation(@Context HttpServletRequest request, @FormParam("organisations") String organisations) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-OrganisationList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Organisation>>(){}.getType();		
		ArrayList<Organisation> oArray = new Gson().fromJson(organisations, type);
		
		PreparedStatement pstmt = null;
		try {	
			String sql = null;
			ResultSet resultSet = null;
			connectionSD.setAutoCommit(false);
				
			for(int i = 0; i < oArray.size(); i++) {
				Organisation o = oArray.get(i);
				
				/*
				 * Ensure that there are no undeleted projects in this organisation
				 */
				sql = "SELECT count(*) " +
						" FROM project p " +  
						" WHERE p.o_id = ?;";
					
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, o.id);
				log.info("SQL: " + sql + ":" + o.id);
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					int count = resultSet.getInt(1);
					if(count > 0) {
						log.info("Count of undeleted projects:" + count);
						throw new Exception("Error: Organisation " + o.name + " has undeleted projects.");
					}
				} else {
					throw new Exception("Error getting project count");
				}
					
				sql = "DELETE FROM organisation o " +  
						" WHERE o.id = ?; ";			
				
				if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, o.id);
				log.info("SQL: " + sql + ":" + o.id);
				pstmt.executeUpdate();
			}

			
			response = Response.ok().build();
			connectionSD.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
			try{
				connectionSD.rollback();
			} catch(Exception e2) {
				
			}
			
		} finally {
			
			try {
				if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Change the orgnisation a user belongs to
	 */
	@POST
	@Path("/setOrganisation")
	@Consumes("application/json")
	public Response changeOrganisation(@Context HttpServletRequest request,
			@FormParam("orgId") int orgId,
			@FormParam("users") String users,
			@FormParam("projects") String projects) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-OrganisationList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
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
			connectionSD.setAutoCommit(false);
			
			String sql = "update users set o_id =  ? " +  
					" WHERE id = ?; ";			
			String sql2 = "delete from user_project where u_id = ? and " +
					"p_id not in (select id from project where o_id = ?);";	
			String sql3 = "update project set o_id =  ? " +  
					" WHERE id = ?; ";			
			String sql4 = "delete from user_project where p_id = ? and " +
					"u_id not in (select id from users where o_id = ?); ";	
			
	
			pstmt = connectionSD.prepareStatement(sql);
			pstmt2 = connectionSD.prepareStatement(sql2);	
			pstmt3 = connectionSD.prepareStatement(sql3);	
			pstmt4 = connectionSD.prepareStatement(sql4);	

			log.info("Move Users");
			// Move Users
			for(int i = 0; i < uArray.size(); i++) {
				pstmt.setInt(1, orgId);
				pstmt.setInt(2, uArray.get(i).id);
				log.info("Move User: " + sql + ":" + orgId + " : " + uArray.get(i).id);
				pstmt.executeUpdate();
			}
			
			// Move Projects
			for(int i = 0; i < pArray.size(); i++) {
				pstmt3.setInt(1, orgId);
				pstmt3.setInt(2, pArray.get(i).id);
				log.info("Move User: " + sql3 + ":" + orgId + " : " + pArray.get(i).id);
				pstmt3.executeUpdate();
			}
			
			// Remove projects from users if they are in a different organisation
			for(int i = 0; i < uArray.size(); i++) {
				
				if(!uArray.get(i).keepProjects) {	// Org admin users keep all of their projects
				
					pstmt2.setInt(1, uArray.get(i).id);
					pstmt2.setInt(2, orgId);
					log.info("Delete Links to projects: " + sql2 + " : " + uArray.get(i).id);
					pstmt2.executeUpdate();
				}
			}
			
			// Move users from projects if they are in a different organisation
			for(int i = 0; i < pArray.size(); i++) {
				
				pstmt4.setInt(1, pArray.get(i).id);
				pstmt4.setInt(2, orgId);
				log.info("Delete Links to users: " + sql4 + " : " + pArray.get(i).id);
				pstmt4.executeUpdate();

			}
	
			response = Response.ok().build();
			connectionSD.commit();
				
		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
			try{
				connectionSD.rollback();
			} catch(Exception e2) {
				
			}
			
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
			try {if (pstmt2 != null) {pstmt2.close();}	} catch (SQLException e) {}
			try {if (pstmt3 != null) {pstmt3.close();}	} catch (SQLException e) {}
			try {if (pstmt4 != null) {pstmt4.close();}	} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}

}

