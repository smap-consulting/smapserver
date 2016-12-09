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
import java.sql.Connection;
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
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MiscPDFManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import utilities.XLSTaskManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Manages Tasks
 */

@Path("/tasks")
public class Tasks extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Tasks.class.getName());
	
	LogManager lm = new LogManager();		// Application log

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
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getTaskGroups");
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
			SDDataSource.closeConnection("surveyKPI - Tasks - getTaskGroups", sd);
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
			@QueryParam("completed") boolean completed,
			@QueryParam("user") int userId
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getTasks");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
		// End authorisation
	
		try {
			
			// Get assignments
			TaskManager tm = new TaskManager();
			TaskListGeoJson t = tm.getTasks(sd, tgId, completed, userId);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(t);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI - Tasks - getTasks", sd);
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
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getLocations");
		a.isAuthorised(sd, request.getRemoteUser());
		// End authorisation
	
		try {
			
			// Get locations
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			TaskManager tm = new TaskManager();
			ArrayList<Location> locations = tm.getLocations(sd, oId);
			
			
			// Return tags to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(locations);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI - Tasks - getLocations",sd);
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
			@Context HttpServletRequest request) {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		
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
			String fileName = null;
			FileItem fileItem = null;
			String filetype = null;

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
					
					fileName = item.getName();
					fileItem = item;
					
					if(fileName.endsWith("xlsx")) {
						filetype = "xlsx";
					} else if(fileName.endsWith("xls")) {
						filetype = "xls";
					} else {
						log.info("unknown file type for item: " + fileName);
						continue;	
					}
	
					break;
						
				}
			}
			
			if(fileName != null) {
				// Authorisation - Access
				sd = SDDataSource.getConnection("Tasks-LocationUpload");
				a.isAuthorised(sd, request.getRemoteUser());
				// End authorisation
				
				// Process xls file
				XLSTaskManager xf = new XLSTaskManager();
				ArrayList<Location> locations = xf.convertWorksheetToTagArray(fileItem.getInputStream(), filetype);
				
				/*
				 * Only save tags if we found some, otherwise its likely to be an error
				 * An alternate mechanism is available to delete all locations
				 */
				if(locations.size() > 0) {
					// Save locations to disk
					int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
					log.info("userevent: " + request.getRemoteUser() + " : upload locations from xls file: " + fileName + " for organisation: " + oId);
					TaskManager tm = new TaskManager();
					tm.saveLocations(sd, locations, oId);
					lm.writeLog(sd, 0, request.getRemoteUser(), "resources", locations.size() + " locations / NFC tags uploaded from file " + fileName);
					// Return tags to calling program
					Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
					String resp = gson.toJson(locations);
				
					response = Response.ok(resp).build();
				} else {
					response = Response.serverError().entity("no tags found").build();
				}
			} else {
				response = Response.serverError().entity("no file found").build();
			}
			
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection("Tasks-LocationUpload", sd);
			
		}
		
		return response;
	}
	
	/*
	 * Download locations into an XLS file
	 */
	@GET
	@Path ("/locations/download")
	@Produces("application/x-download")
	public Response getXLSLocationsService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
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
		// End authorisation
		
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
			
			// Get the current locations
			ArrayList<Location> locations = tm.getLocations(sd, organisation.id);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse("locations." + filetype, response);
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype);
			xf.createXLSLocationsFile(response.getOutputStream(), locations, localisation);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection("createXLSTasks", sd);	
			
		}
		return Response.ok("").build();
	}
	
	
	/*
	 * Export Tasks for a task group in an XLS file
	 */
	@GET
	@Path ("/xls/{tgId}")
	@Produces("application/x-download")
	public Response getXLSTasksService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("tgId") int tgId,
			@QueryParam("tz") String tz,
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
		
		if(tz == null) {
			tz = "GMT";
		}
		
		log.info("Exporting tasks with timzone: " + tz);
		
		try {
			
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			
			TaskGroup tg = tm.getTaskGroupDetails(sd, tgId);		// Get the task group name
			TaskListGeoJson tl = tm.getTasks(sd, tgId, true, 0);	// Get the task list
			GeneralUtilityMethods.setFilenameInResponse(tg.name + "." + filetype, response); // Set file name
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype);
			xf.createXLSTaskFile(response.getOutputStream(), tl, localisation, tz);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection("createXLSTasks", sd);	
			
		}
		return Response.ok("").build();
	}

	/*
	 * Import tasks for a task group from an XLS file
	 */
	@POST
	@Produces("application/json")
	@Path("/xls/{pId}")
	public Response uploadTasks(
			@Context HttpServletRequest request,
			@PathParam("pId") int pId,
			@QueryParam("user") int userId
			) throws IOException {
		
		Response response = null;
		int tgId = 0;
		boolean tgClear = false;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		

		log.info("userevent: " + request.getRemoteUser() + " : upload tasks from xls file for project: " + pId);
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection sd = null; 
		String fileName = null;
		String filetype = null;
		FileItem file = null;

		try {
			
			sd = SDDataSource.getConnection("Tasks-TaskUpload");
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
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
						try {
							tgId = Integer.valueOf(item.getString());
						} catch (Exception e) {
							throw new Exception(localisation.getString("t_notg"));
						}
					} else if(item.getFieldName().equals("tg_clear")) {
						tgClear = Boolean.valueOf(item.getString());
					}
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					fileName = item.getName();
					if(fileName.endsWith("xlsx")) {
						filetype = "xlsx";
					} else if(fileName.endsWith("xls")) {
						filetype = "xls";
					} else {
						log.info("unknown file type for item: " + fileName);
						continue;	
					}
					
					file = item;
				}
			}
	
			if(file != null && tgId > 0) {
				// Authorisation - Access
				a.isAuthorised(sd, request.getRemoteUser());
				a.isValidProject(sd, request.getRemoteUser(), pId);
				a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
				// End authorisation

				int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
				
				// Process xls file
				XLSTaskManager xf = new XLSTaskManager();
				TaskListGeoJson tl = xf.getXLSTaskList(filetype, file.getInputStream(), localisation);
				
				// Save tasks to the database
				TaskManager tm = new TaskManager();
				
				if(tgClear) {
					tm.deleteTasksInTaskGroup(sd, tgId);
				}
				tm.writeTaskList(sd, tl, pId, tgId, request.getScheme() + "://" + request.getServerName(), true, oId);
				
				/*
				 * Get the tasks out of the database
				 * This is required because saving an external task list adds additional default data including geometries
				 *  from latitude and longitude
				 *  Also we may not want to return complete tasks
				 */
				tl = tm.getTasks(sd, tgId, true, userId);	// TODO set "complete" flag from passed in parameter
				Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				String resp = gson.toJson(tl);
				
				if(tl.features.size() > 0) {
					response = Response.ok(resp).build();
				} else {
					response = Response.serverError().entity("no tasks found").build();
				}
					
			}
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection("Tasks-TaskUpload", sd);
			
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
		
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskFeature tf = gson.fromJson(task, TaskFeature.class);
		TaskManager tm = new TaskManager();
		
		try {
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			tm.writeTask(sd, pId, tgId, tf, request.getServerName(), false, oId);
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection("surveyKPI-tasks", sd);
			
		}
		
		return response;
	}
	
	/*
	 * Update start date and time of a task
	 */
	@POST
	@Path("/when/{pId}/{tgId}")
	@Consumes("application/json")
	public Response updateTaskFromDate(
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
		log.info("Update task start: " + task);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tasks");
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskFeature tf = gson.fromJson(task, TaskFeature.class);
		TaskManager tm = new TaskManager();
		
		try {
			tm.updateWhen(sd, pId, tf.properties.id, tf.properties.from, tf.properties.to);
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection("surveyKPI-tasks", sd);
			
		}
		
		return response;
	}
	
	/*
	 * Apply an action to an array of task ids
	 */
	@POST
	@Path("/bulk/{pId}/{tgId}")
	@Consumes("application/json")
	public Response bulkAction(
			@Context HttpServletRequest request,
			@PathParam("pId") int pId,
			@PathParam("tgId") int tgId,
			@FormParam("tasks") String tasks
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
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tasks");
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskBulkAction bulkAction = gson.fromJson(tasks, TaskBulkAction.class);
		TaskManager tm = new TaskManager();
		
		log.info("userevent: " + request.getRemoteUser() + " : bulk action for : " + tgId + " " 
					+ bulkAction.action + " : assign user: " + bulkAction.userId + " : " + bulkAction.taskIds.toString());
		
		try {
			tm.applyBulkAction(sd, pId, bulkAction);
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-tasks", sd);
			
		}
		
		return response;
	}
	
	
	/*
	 * Get a PDF of tasks
	 */
	@GET
	@Path("/pdf/{tgId}")
	@Produces("application/x-download")
	public Response getPDFService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("tgId") int tgId,
			@QueryParam("landscape") boolean landscape) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Create PDF for task group:" + tgId + " for record: " + tgId);
		
		// Authorisation - Access
		String user = request.getRemoteUser();
		Connection sd = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
		// End Authorisation 
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
	
		
		try {
			MiscPDFManager pm = new MiscPDFManager();  
			
			pm.createTasksPdf(
					sd,
					response.getOutputStream(),
					basePath, 
					response,
					tgId);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection("createPDF", sd);	
			
		}
		return Response.ok("").build();
	}

}
