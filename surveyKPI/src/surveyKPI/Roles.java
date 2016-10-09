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
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.model.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Services for managing roles
 */

@Path("/role")
public class Roles extends Application {
	
	Authorise aSM = null;

	private static Logger log =
			 Logger.getLogger(Roles.class.getName());
	
	public Roles() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		
		// Only allow security administrators and organisational administrators to view or update the roles
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.SECURITY);
		authorisations.add(Authorise.ORG);
		aSM = new Authorise(authorisations, null);
		
	}
	
	/*
	 * Get the roles in the organisation
	 */
	@Path("/roles")
	@GET
	@Produces("application/json")
	public Response getRoles(
			@Context HttpServletRequest request
			) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-UserList");
		aSM.isAuthorised(sd, request.getRemoteUser());
		
		// End Authorisation
		
		RoleManager rm = new RoleManager();
		try {
			int o_id  = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			ArrayList<Role> roles = rm.getRoles(sd, o_id);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(roles);
			response = Response.ok(resp).build();
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}

		return response;
	}
	
	/*
	 * Update the role details
	 */
	@Path("/roles")
	@POST
	@Consumes("application/json")
	public Response updateRoles(@Context HttpServletRequest request, @FormParam("roles") String roles) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-RoleList");
		aSM.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<Role>>(){}.getType();		
		ArrayList<Role> rArray = new Gson().fromJson(roles, type);
		
		try {	
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			RoleManager rm = new RoleManager();
			
			for(int i = 0; i < rArray.size(); i++) {
				Role r = rArray.get(i);
				
				if(r.id == -1) {
					
					// New role
					rm.createRole(sd, r, o_id, request.getRemoteUser());
					
				} else {
					// Existing role
					rm.updateRole(sd, r, o_id, request.getRemoteUser());
			
				}
			
				response = Response.ok().build();
			}
				
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}
		
		return response;
	}
	
	/*
	 * Delete roles
	 */
	@Path("/roles")
	@DELETE
	@Consumes("application/json")
	public Response delRole(@Context HttpServletRequest request, @FormParam("roles") String roles) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-userList - delete roles");
		aSM.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation			
					
		Type type = new TypeToken<ArrayList<Role>>(){}.getType();		
		ArrayList<Role> rArray = new Gson().fromJson(roles, type);
		
		RoleManager rm = new RoleManager();
		
		try {	
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			rm.deleteRoles(sd, rArray, o_id);
			response = Response.ok().build();			
		}  catch (Exception ex) {
			log.log(Level.SEVERE, ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
		} finally {			
			SDDataSource.closeConnection("surveyKPI-userList - delete roles", sd);
		}
		
		return response;
	}
	
	
	/*
	 * Get the roles in a survey
	 */
	@Path("/survey/{sId}")
	@GET
	@Produces("application/json")
	public Response getSurveyRoles(
			@Context HttpServletRequest request,
			@PathParam("sId") int sId
			) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-UserList");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aSM.isAuthorised(sd, request.getRemoteUser());
		aSM.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		
		// End Authorisation
		
		RoleManager rm = new RoleManager();
		try {
	
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			ArrayList<Role> roles = rm.getSurveyRoles(sd, sId, oId);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(roles);
			response = Response.ok(resp).build();
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}

		return response;
	}
	
	/*
	 * Update a survey role
	 */
	@Path("/survey/{sId}/{property}")
	@POST
	@Consumes("application/json")
	@Produces("application/json")
	public Response updateSurveyRoles(
			@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("property") String property,
			@FormParam("role") String roleString
			) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		Role role = new Gson().fromJson(roleString, Role.class);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-UserList");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aSM.isAuthorised(sd, request.getRemoteUser());
		aSM.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		aSM.isValidRole(sd, request.getRemoteUser(), role.id);
		// End Authorisation
		
		RoleManager rm = new RoleManager();
		try {
			
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			if(property.equals("enabled")) {
				role.linkid = rm.updateSurveyLink(sd, sId, role.id, role.linkid, role.enabled);
			} else if(property.equals("row_filter")) {
				rm.updateSurveyRoleRowFilter(sd, sId, role, localisation);
			} else if(property.equals("column_filter")) {
				rm.updateSurveyRoleColumnFilter(sd, sId, role, localisation);
			}
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(role);
			response = Response.ok(resp).build();
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}

		return response;
	}


}

