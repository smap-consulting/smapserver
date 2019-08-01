package koboToolboxApi;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;
import org.smap.sdal.model.TaskServerDefn;

/*
 * Provides access to collected data
 */
@Path("/v1/tasks")
public class Tasks extends Application {

	Authorise a = null;

	private static Logger log =
			Logger.getLogger(Tasks.class.getName());

	LogManager lm = new LogManager();		// Application log

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Tasks.class);
		return s;
	}

	public Tasks() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.MANAGE);
		a = new Authorise(authorisations, null);
	}

	/*
	 * Returns a list of tasks
	 */
	@GET
	@Produces("application/json")
	public Response getTasks(@Context HttpServletRequest request,
			@QueryParam("user") String userIdent,		// User to get tasks for
			@QueryParam("period") String period,			// Period to get tasks for all || week || month
			@QueryParam("tz") String tz,					// Timezone
			@QueryParam("tg_id") int tg_id,				// Task group			
			@QueryParam("start") int start,				// Task id to start from			
			@QueryParam("limit") int limit,				// Number of records to return
			@QueryParam("sort") String sort,				// Column to sort on
			@QueryParam("dirn") String dirn,				// Sort direction, asc || desc
			@QueryParam("status") String incStatus		// Comma separated list of status values
			
			) throws ApplicationException, Exception { 
		
		String connectionString = "surveyKPI - Tasks - getTasks";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		if(tg_id > 0) {
			a.isValidTaskGroup(sd, request.getRemoteUser(), tg_id);
		}
		// End authorisation

		Response response = null;
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			int oId = 0;
			if(tg_id == 0) {
				oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			}
			
			// Parameters
			int userId = 0;								// All Users
			if(userIdent != null) {
				if(userIdent.equals("_unassigned")) {
					userId = -1;							// Only unassigned
				} else {
					userId = GeneralUtilityMethods.getUserId(sd, userIdent);
				}
			}
			
			if(period == null) {
				period = "week";		// As per default in UI
			}
			
			String urlprefix = request.getScheme() + "://" + request.getServerName();
			
			// Get assignments
			TaskManager tm = new TaskManager(localisation, tz);
			TaskListGeoJson t = tm.getTasks(sd, 
					urlprefix,
					oId, 
					tg_id, 
					0,			// task id
					true, 
					userId, 
					incStatus, 		// include status
					period, 
					start, 
					limit,
					sort,
					dirn);		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(t);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	/*
	 * Returns a single task
	 */
	@GET
	@Path("/{id}")
	@Produces("application/json")
	public Response getTask(@Context HttpServletRequest request,
			@PathParam("id") int taskId,
			@QueryParam("tz") String tz					// Timezone
			) throws ApplicationException, Exception { 
		
		String connectionString = "surveyKPI - Tasks - get Task";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidTask(sd, request.getRemoteUser(), taskId);
		// End authorisation

		Response response = null;
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String urlprefix = request.getScheme() + "://" + request.getServerName();
			
			// Get assignments
			TaskManager tm = new TaskManager(localisation, tz);
			TaskListGeoJson t = tm.getTasks(
					sd, 
					urlprefix,
					0,		// Organisation id 
					0, 		// task group id
					taskId,
					true, 
					0,		// userId 
					null, 
					null,	// period 
					0,		// start 
					0,		// limit
					null,	// sort
					null);	// sort direction	
			
			if(t != null && t.features.size() > 0) {
				TaskProperties tp = t.features.get(0).properties;
				
				// Return groups to calling program
				Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				String resp = gson.toJson(tp);	
				response = Response.ok(resp).build();	
			} else {
				response = Response.serverError().entity(localisation.getString("mf_nf")).build();
			}
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
	
	/*
	 * Creates a new task
	 */
	@POST
	@Produces("application/json")
	public Response createTask(@Context HttpServletRequest request,
			@QueryParam("tz") String tz,					// Timezone
			@FormParam("task") String task
			) throws ApplicationException, Exception { 
		
		Response response = null;
		String connectionString = "surveyKPI - Tasks - add new task";
		log.info("New task: " + task);
		
		if(task == null) {
			response = Response.serverError().entity("No task has been provided").build();
			return response;
		}
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		TaskProperties tp = gson.fromJson(task, TaskProperties.class);	
		
		if(tp == null) {
			response = Response.serverError().entity("Error reading task form data: " + task).build();
			return response;
		}
		
		// Authorisation - Access
		Connection cResults = null;
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
			
		}
		a.isAuthorised(sd, request.getRemoteUser());
		if(tp.form_id > 0) {
			a.isValidSurvey(sd, request.getRemoteUser(), tp.form_id, false, superUser);
		} else {
			a.isValidSurveyIdent(sd, request.getRemoteUser(), tp.survey_ident, false, superUser);
			tp.form_id = GeneralUtilityMethods.getSurveyId(sd, tp.survey_ident);
		}
		
		if(tp.assignee_ident != null) {
			tp.assignee = GeneralUtilityMethods.getUserId(sd, tp.assignee_ident);
			a.isValidUser(sd, request.getRemoteUser(), tp.assignee);
		}
		
		if(tp.tg_id > 0) {
			a.isValidTaskGroup(sd, request.getRemoteUser(), tp.tg_id);
		}
		// End Authorisation
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			TaskManager tm = new TaskManager(localisation, tz);
			SurveyManager sm = new SurveyManager(localisation, tz);
			if(tp.tg_id <= 0) {
				
				Survey s = sm.getById(sd, cResults, request.getRemoteUser(), tp.form_id, 
						false, null, null, false, false, 
						false, false, false, null, false, false, false, null, false, false);
				
				// Create a task group based on the survey
				tp.tg_id = tm.createTaskGroup(sd, s.displayName, 
						s.p_id,
						null,	// address columns
						null,	// setting
						0,	// source survey id
						0,	// Target survey id
						0,	// Download distance
						true		// don't use an existing task group of the same name
						);	
			}
			
			tp.survey_ident = GeneralUtilityMethods.getSurveyIdent(sd, tp.form_id);
			
			if(tz == null) {
				tz = "UTC";	// Set default for timezone
			}
			
			cResults = ResultsDataSource.getConnection(connectionString);
			
			TaskFeature tf = new TaskFeature();
			tf.properties = (TaskProperties) tp;
			
			TaskServerDefn tsd = tm.convertTaskFeature(tf);
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			tm.writeTask(sd, cResults, tp.tg_id, tsd, request.getServerName(), false, oId, true, request.getRemoteUser());
			response = Response.ok().build();
		
		} catch (Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
	
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
			
		}
		
		return response;
	}

	/*
	 * Returns a list of task groups
	 */
	@GET
	@Path("/groups/{projectId}")
	@Produces("application/json")
	public Response getTaskGroups(@Context HttpServletRequest request,
			@PathParam("projectId") int pId,				// Project Id
			@QueryParam("tz") String tz					// Timezone
			) throws ApplicationException, Exception { 
		
		String connectionString = "surveyKPI - Tasks - getTasks";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		
		boolean isAdminUser = GeneralUtilityMethods.isAdminUser(sd, request.getRemoteUser());
		if(isAdminUser) {
			// Check that the project is in the users organisation
			a.projectInUsersOrganisation(sd, request.getRemoteUser(), pId);
		} else {
			// Check that the user is a member of the project
			a.isValidProject(sd, request.getRemoteUser(), pId);
		}
		// End authorisation

		Response response = null;
		
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Get groups
			TaskManager tm = new TaskManager(localisation, tz);
			ArrayList<TaskGroup> tgList = tm.getTaskGroups(sd, pId);	
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(tgList);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}

}

