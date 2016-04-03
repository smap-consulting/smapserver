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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import model.MediaResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.PDFManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.TaskAssignment;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskManagement;

import utilities.XLSFormManager;
import utilities.XLSTaskManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

/*
 * Manages Tasks
 */

@Path("/tasks")
public class Tasks extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Tasks.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}

	public Tasks() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Get the task groups
	 */
	@GET
	@Produces("application/json")
	@Path("/taskgroups/{projectId}")
	public Response getTaskGroups(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId 
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("fieldManager-MediaUpload");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), projectId);
		// End authorisation
	
		try {
			
			// Get task groups
			TaskManager tm = new TaskManager();
			ArrayList<TaskGroup> taskgroups = tm.getTaskGroups(sd, projectId);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(taskgroups);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Get the assignments for a task group
	 */
	@GET
	@Produces("application/json")
	@Path("/assignmentsx/{projectId}")
	public Response getAssignmentsx(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId,
			@QueryParam("tg") int taskGroupId, 
			@QueryParam("completed") boolean completed
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("fieldManager-MediaUpload");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), projectId);
		// End authorisation
	
		try {
			
			// Get assignments
			TaskManager tm = new TaskManager();
			ArrayList<TaskAssignment> tasks = tm.getAssignmentsx(sd, projectId, taskGroupId, completed);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(tasks);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Get the assignments for a task group
	 */
	@GET
	@Produces("application/json")
	@Path("/assignments/{projectId}")
	public Response getAssignments(
			@Context HttpServletRequest request,
			@PathParam("projectId") int projectId,
			@QueryParam("tg") int taskGroupId, 
			@QueryParam("completed") boolean completed
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("fieldManager-MediaUpload");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), projectId);
		// End authorisation
	
		try {
			
			// Get assignments
			TaskManager tm = new TaskManager();
			TaskManagement t = tm.getAssignments(sd, projectId, taskGroupId, completed);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(t);	
			response = Response.ok(resp).build();	
			System.out.println("Resp: " + resp);
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Get the locations
	 */
	@GET
	@Produces("application/json")
	@Path("/locations")
	public Response getLocations(
			@Context HttpServletRequest request
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("fieldManager-MediaUpload");
		a.isAuthorised(sd, request.getRemoteUser());
		// End authorisation
	
		try {
			
			// Get locations
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			TaskManager tm = new TaskManager();
			ArrayList<Location> locations = tm.getLocations(sd, oId);
			
			
			// Return tags to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(locations);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Upload locations used in task assignment
	 * A location can be:
	 *   An NFC tag
	 *   A geofence
	 */
	@POST
	@Produces("application/json")
	@Path("/locations/upload")
	public Response uploadLocations(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		

		log.info("userevent: " + request.getRemoteUser() + " : upload locations from xls file: ");
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection sd = null; 

		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();

			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				// Get form parameters
				
				if(item.isFormField()) {
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					String filetype = null;
					if(fileName.endsWith("xlsx")) {
						filetype = "xlsx";
					} else if(fileName.endsWith("xls")) {
						filetype = "xls";
					} else {
						log.info("unknown file type for item: " + fileName);
						continue;	
					}
	
					// Authorisation - Access
					sd = SDDataSource.getConnection("fieldManager-MediaUpload");
					a.isAuthorised(sd, request.getRemoteUser());
					// End authorisation

					// Process xls file
					XLSTaskManager xf = new XLSTaskManager();
					ArrayList<Location> locations = xf.convertWorksheetToTagArray(item.getInputStream(), filetype);
					
					// Save locations to disk
					int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
					TaskManager tm = new TaskManager();
					tm.saveLocations(sd, locations, oId);
					
					// Return tags to calling program
					Gson gson = new GsonBuilder().disableHtmlEscaping().create();
					String resp = gson.toJson(locations);
						
					response = Response.ok(resp).build();	
					
					break;
						
				}
			}
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
		}
		
		return response;
		
	}
	

	

}
