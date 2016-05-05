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

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;

import utilities.XLSTaskManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import model.Settings;

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
	 * Get the tasks for a task group
	 */
	@GET
	@Produces("application/json")
	@Path("/assignments/{tgId}")
	public Response getTasks(
			@Context HttpServletRequest request,
			@PathParam("tgId") int tgId,
			@QueryParam("completed") boolean completed
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("fieldManager-MediaUpload");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
		// End authorisation
	
		try {
			
			// Get assignments
			TaskManager tm = new TaskManager();
			TaskListGeoJson t = tm.getTasks(sd, tgId, completed);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
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
	 * Upload locations used in task assignment from an XLS file
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
					sd = SDDataSource.getConnection("Tasks-LocationUpload");
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
					
					if(locations.size() > 0) {
						response = Response.ok(resp).build();
					} else {
						response = Response.serverError().entity("no tags found").build();
					}
					
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
	
	/*
	 * Download Tasks for a task group in an XLS file
	 */
	@GET
	@Path ("/xls/{tgId}")
	@Produces("application/x-download")
	public Response getXLSTasksService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("tgId") int tgId,
			@QueryParam("filetype") String filetype) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
				
		Connection sd = SDDataSource.getConnection("createXLSTasks");	
		// Authorisation - Access

		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
		// End Authorisation 
		
		TaskManager tm = new TaskManager();
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Get the task group name
			TaskGroup tg = tm.getTaskGroupDetails(sd, tgId);
			
			// Get the task list
			TaskListGeoJson tl = tm.getTasks(sd, tgId, true);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(tg.name + "." + filetype, response);
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype);
			xf.createXLSTaskFile(response.getOutputStream(), tl, localisation);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		return Response.ok("").build();
	}

	/*
	 * Upload tasks for a task group from an XLS file
	 */
	@POST
	@Produces("application/json")
	@Path("/xls/{pId}")
	public Response uploadTasks(
			@Context HttpServletRequest request,
			@PathParam("pId") int pId
			) throws IOException {
		
		Response response = null;
		int tgId = 0;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		

		log.info("userevent: " + request.getRemoteUser() + " : upload tasks from xls file for project: " + pId);
		
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
					if(item.getFieldName().equals("tg")) {
						tgId = Integer.valueOf(item.getString());
					}
					
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
					sd = SDDataSource.getConnection("Tasks-TaskUpload");
					a.isAuthorised(sd, request.getRemoteUser());
					a.isValidProject(sd, request.getRemoteUser(), pId);
					a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
					// End authorisation

					// Process xls file
					XLSTaskManager xf = new XLSTaskManager();
					TaskListGeoJson tl = xf.getXLSTaskList(filetype, item.getInputStream());
					
					// Save tasks to the database
					TaskManager tm = new TaskManager();
					tm.writeTaskList(sd, tl, pId, tgId, request.getServerName());
					
					/*
					 * Get the tasks out of the database
					 * This is required because saving an external task list adds additional default data including geometries
					 *  from latitude and longitude
					 *  Also we may not want to return complete tasks
					 */
					tl = tm.getTasks(sd, tgId, true);	// TODO set "complete" flag from passed in parameter
					Gson gson = new GsonBuilder().disableHtmlEscaping().create();
					String resp = gson.toJson(tl);
					
					System.out.println("Task list: " + resp);
					
					if(tl.features.size() > 0) {
						response = Response.ok(resp).build();
					} else {
						response = Response.serverError().entity("no tasks found").build();
					}
					
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
	
	/*
	 * Modify a task or create a new task
	 */
	@POST
	@Path("/task/{pId}/{tgId}")
	@Consumes("application/json")
	public Response updateTask(
			@Context HttpServletRequest request,
			@PathParam("pId") int pId,
			@PathParam("tgId") int tgId,
			@FormParam("task") String task
			) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String user = request.getRemoteUser();
		log.info("TaskFeature:" + task);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tasks");
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		TaskFeature tf = gson.fromJson(task, TaskFeature.class);
		TaskManager tm = new TaskManager();
		
		try {
			tm.writeTask(sd, pId, tgId, tf, request.getServerName());
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
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
