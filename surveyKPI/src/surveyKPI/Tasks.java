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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
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
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MiscPDFManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskEmailDetails;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskServerDefn;

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
		
		//GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getTaskGroups");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), projectId);
		// End authorisation
	
		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			// Get task groups
			TaskManager tm = new TaskManager(localisation, tz);
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
			@QueryParam("user") int userId,
			@QueryParam("period") String period
			) throws IOException {
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getTasks");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId);
		// End authorisation
	
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			
			// Get assignments
			String urlprefix = request.getScheme() + "://" + request.getServerName();
			TaskManager tm = new TaskManager(localisation, tz);
			TaskListGeoJson t = tm.getTasks(
					sd, 
					urlprefix,
					0, 
					tgId,
					0,		// task id
					true, 
					userId, 
					null, 
					period, 
					0, 
					0,
					"scheduled", "desc");		
			
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
	 * Get the task locations
	 */
	@GET
	@Produces("application/json")
	@Path("/locations")
	public Response getLocations(
			@Context HttpServletRequest request
			) throws IOException {
		
		//GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getLocations");
		a.isAuthorised(sd, request.getRemoteUser());
		// End authorisation
	
		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			// Get locations
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			TaskManager tm = new TaskManager(localisation, tz);
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
	 * Upload locations and nfc tags used in task assignment from an XLS file
	 */
	@POST
	@Produces("application/json")
	@Path("/locations/upload")
	public Response uploadLocations(
			@Context HttpServletRequest request) {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		
		//GeneralUtilityMethods.assertBusinessServer(request.getServerName());

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection sd = null; 
		
		// Authorisation - Access
		sd = SDDataSource.getConnection("surveyKPI - Tasks - getLocations");
		a.isAuthorised(sd, request.getRemoteUser());
		// End authorisation

		try {
			
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			String fileName = null;
			FileItem fileItem = null;
			String filetype = null;

			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
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
					
					if(fileName.endsWith("xlsx") || fileName.endsWith("xlsm")) {
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
				
				// Process xls file
				XLSTaskManager xf = new XLSTaskManager();
				ArrayList<Location> locations = xf.convertWorksheetToTagArray(fileItem.getInputStream(), filetype);
				
				/*
				 * Only save tags if we found some, otherwise its likely to be an error
				 * An alternate mechanism is available to delete all locations
				 */
				if(locations.size() > 0) {
					// Save locations to disk
					int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
					log.info("userevent: " + request.getRemoteUser() + " : upload locations from xls file: " + fileName + " for organisation: " + oId);
					TaskManager tm = new TaskManager(localisation, tz);
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
	 * Download nfc identifiers into an XLS file
	 */
	@GET
	@Path ("/locations/download")
	@Produces("application/x-download")
	public Response getXLSNfcService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@QueryParam("filetype") String filetype) throws Exception {
	
		String connectionString = "Download Locations";
		Connection sd = SDDataSource.getConnection(connectionString);	
		// Authorisation - Access
		a.isAuthorised(sd, request.getRemoteUser());
		// End authorisation
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());			
			
			String tz = "UTC";	// Set default for timezone
			
			TaskManager tm = new TaskManager(localisation, tz);
			
			// Get the current locations
			ArrayList<Location> locations = tm.getLocations(sd, organisation.id);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(localisation.getString("res_locations") + "." + filetype, response);
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype, request.getScheme(), request.getServerName());
			xf.createXLSLocationsFile(response.getOutputStream(), locations, localisation);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);	
			
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
			@QueryParam("filetype") String filetype,
			@QueryParam("inc_status") String incStatus,
			@QueryParam("period") String period) throws Exception {

		String connectionString = "Download Tasks";
		Connection sd = SDDataSource.getConnection(connectionString);	
		// Authorisation - Access

		a.isAuthorised(sd, request.getRemoteUser());		
		if(tgId > 0) {
			a.isValidTaskGroup(sd, request.getRemoteUser(), tgId);
		}
		// End Authorisation 
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		if(tz == null) {
			tz = "UTC";
		}
		
		log.info("Exporting tasks with timzone: " + tz);
		
		try {
			
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			TaskManager tm = new TaskManager(localisation, tz);
			
			String filename = null;
			if(tgId > 0) {
				TaskGroup tg = tm.getTaskGroupDetails(sd, tgId);		// Get the task group name
				filename = tg.name + "." + filetype;
			} else {
				filename = organisation.name + " - " + localisation.getString("c_tasks") + "." + filetype;
			}
			GeneralUtilityMethods.setFilenameInResponse(filename, response); // Set file name
			
			if(period == null) {
				period = "week";
			}
			
			String urlprefix = request.getScheme() + "://" + request.getServerName();
			
			TaskListGeoJson tl = tm.getTasks(
					sd, 
					urlprefix,
					organisation.id, tgId, 
					0,		// task id
					true, 0, incStatus, period, 0, 0,
					"scheduled", "desc");	// Get the task list
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype, request.getScheme(), request.getServerName());
			xf.createXLSTaskFile(response.getOutputStream(), tl, localisation, tz);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);	
			
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
		
		//GeneralUtilityMethods.assertBusinessServer(request.getServerName());

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection sd = null; 
		Connection cResults = null;
		String fileName = null;
		String filetype = null;
		FileItem file = null;

		String requester = "Tasks-TaskUpload";
		
		try {
			
			sd = SDDataSource.getConnection(requester);
			cResults = ResultsDataSource.getConnection(requester);
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
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
					if(fileName.endsWith("xlsx") || fileName.endsWith("xlsm")) {
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
				a.isValidTaskGroup(sd, request.getRemoteUser(), tgId);
				// End authorisation

				int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
				
				// Process xls file
				XLSTaskManager xf = new XLSTaskManager();
				ArrayList<TaskServerDefn> tArray = xf.getXLSTaskList(filetype, file.getInputStream(), localisation, tz);
				
				// Save tasks to the database
				TaskManager tm = new TaskManager(localisation, tz);
				
				if(tgClear) {
					tm.deleteTasksInTaskGroup(sd, tgId);
				}
				tm.writeTaskList(sd, cResults, tArray, tgId, 
						request.getScheme() + "://" + request.getServerName(), 
						true, 		// update resources
						oId, 
						false, 
						request.getRemoteUser());
				
				/*
				 * Get the tasks out of the database
				 * This is required because saving an external task list adds additional default data including geometries
				 *  from latitude and longitude
				 *  Also we may not want to return complete tasks
				 */
				String urlprefix = request.getScheme() + "://" + request.getServerName();
				TaskListGeoJson tl = tm.getTasks(
						sd, 
						urlprefix,
						0, 
						tgId, 
						0,	// task id 
						true, userId, null, "all", 0, 0,
						"scheduled", "desc");	// TODO set "complete" flag from passed in parameter
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
	
			SDDataSource.closeConnection(requester, sd);
			ResultsDataSource.closeConnection(requester, sd);
			
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

		String user = request.getRemoteUser();
		log.info("Update task start: " + task);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tasks");
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskFeature tf = gson.fromJson(task, TaskFeature.class);
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			TaskManager tm = new TaskManager(localisation, tz);
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
		String connectionString = "surveyKPI-tasks-bulk";
		String user = request.getRemoteUser();
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskBulkAction bulkAction = gson.fromJson(tasks, TaskBulkAction.class);
	
		
		log.info("userevent: " + request.getRemoteUser() + " : bulk action for : " + tgId + " " 
					+ bulkAction.action + " : assign user: " + bulkAction.userId + " : " + bulkAction.tasks.toString());
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			TaskManager tm = new TaskManager(localisation, tz);
			tm.applyBulkAction(request, sd, tgId, pId, bulkAction);
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {			
			SDDataSource.closeConnection(connectionString, sd);			
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
		
		log.info("Create PDF for task group:" + tgId + " for task group: " + tgId);
		
		String connectionString = "surveyKPI-tasks-createPdf";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);	
		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId);
		// End Authorisation 
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
	
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			MiscPDFManager pm = new MiscPDFManager(localisation, tz);  			
			pm.createTasksPdf(
					sd,
					response.getOutputStream(),
					basePath, 
					request,
					response,
					tgId);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);	
			
		}
		return Response.ok("").build();
	}
	
	/*
	 * Update an email details
	 */
	@POST
	@Path("/emaildetails/{pId}/{tgId}")
	@Consumes("application/json")
	public Response updateEmailDetailsTask(
			@Context HttpServletRequest request,
			@PathParam("pId") int pId,
			@PathParam("tgId") int tgId,
			@FormParam("emaildetails") String emaildetails
			) { 
		
		Response response = null;

		String user = request.getRemoteUser();
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tasks");
		a.isAuthorised(sd, user);
		a.isValidProject(sd, user, pId);
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId);
		// End Authorisation
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskEmailDetails ted = gson.fromJson(emaildetails, TaskEmailDetails.class);	
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";	// Set default for timezone
			
			TaskManager tm = new TaskManager(localisation, tz);
			tm.updateEmailDetails(sd, pId, tgId, ted);
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection("surveyKPI-tasks", sd);
			
		}
		
		return response;
	}

}
