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
import managers.TasksManager;
import model.TasksEndPoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;

/*
 * Returns overview data such as the number of submissions
 * This is a Smap specific extension to the KoboToolbox API
 */
@Path("/v1/tasks")
@Produces("application/json")
public class Tasks extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Tasks.class.getName());
	
	public Tasks() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 *  Get the available end points for tasks
	 */
	@GET
	@Produces("application/json")
	@Path("/endpoints")
	public Response getEndPoints(@Context HttpServletRequest request) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().build();
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("KoboToolBoxAPI - Tasks - Endpoints");
		a.isAuthorised(sd, request.getRemoteUser());

		SDDataSource.closeConnection("KoboToolBoxAPI - Tasks - Endpoints", sd);
		
		ArrayList<TasksEndPoint> endPoints = new ArrayList<TasksEndPoint> ();
		
		TasksEndPoint ep = new TasksEndPoint(request, 
				"stats", 
				"Statistics on tasks",
				"status",
				"scheduled",
				"year,month,week, day",
				"user",
				"?group=status&x=scheduled&period=month");
		endPoints.add(ep);
		
		ep = new TasksEndPoint(request, 
				null, 
				"Individual tasks",
				null,
				null,
				null,
				"user",
				"?user=2");
		endPoints.add(ep);
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		String resp = gson.toJson(endPoints);
		response = Response.ok(resp).build();

		return response;
	}
	
	/*
	 * KoboToolBox API version 1 - get a list of tasks /data (Smap extension)
	 */
	@GET
	@Produces("application/json")
	public Response getTasks(@Context HttpServletRequest request,
			@QueryParam("limit") int limit,
			@QueryParam("user") int userId) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get individual tasks");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		try {
			int orgId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			
			if(limit == 0) {
				limit = 20;
			}
			
			if(orgId > 0) {
				TasksManager sm = new TasksManager(orgId);
				response = sm.getTasks(sd, orgId, userId, limit);
			}
		} catch (Exception e) {
			e.printStackTrace();
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("koboToolboxApi - get individual tasks", sd);
		}
		
	
		return response;
	}
	
	/*
	 * KoboToolBox API version 1 for statistics on tasks /data (Smap extension)
	 */
	@GET
	@Produces("application/json")
	@Path("/stats")
	public Response getTaskStatistics(@Context HttpServletRequest request,
			@QueryParam("group") String group,
			@QueryParam("x") String x,
			@QueryParam("period") String period,
			@QueryParam("user") int userId) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get task statistics");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		try {
			
			int orgId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			
			if(orgId > 0) {
				TasksManager sm = new TasksManager(orgId);
				response = sm.getTaskStats(sd, orgId, group, x, period, userId);
			}
		} catch (Exception e) {
			e.printStackTrace();
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("koboToolboxApi - get task statistics", sd);
		}
		
		return response;
		
	}
	
}

