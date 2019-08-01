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
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
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
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.ActionLink;
import org.smap.sdal.model.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Manage Access to reports
 * Should replace ReportListSvcDeprecate
 */
@Path("/reporting")
public class Reports extends Application {
	
	Authorise a = null;
	Authorise aSuper = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	public Reports() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.MANAGE);		// Enumerators with MANAGE access can process managed forms
		a = new Authorise(authorisations, null);		
	}

	/*
	 * Update a report and return a link
	 */
	@POST
	@Produces("application/json")
	@Path("/link/{sId}")
	public Response getLink(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@FormParam("report") String report,
			@QueryParam("tz") String tz,			// Keep this one to set up action manager
			@QueryParam("ident") String ident		// Used when updating a link - keep this
			) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI - Reports - GetLink");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
					
			ActionManager am = new ActionManager(localisation, tz);
			Action action = gson.fromJson(report, Action.class);
			
			action.action = "report";
			action.sId = sId;
			action.pId = GeneralUtilityMethods.getProjectId(sd, action.sId);
			action.surveyName = GeneralUtilityMethods.getSurveyName(sd, sId);
			action.filename = (action.filename == null) ? "report" : action.filename;
			
			/*
			 * Validate roles
			 */
			if(!superUser) {
				for(Role role : action.roles) {
					if(!GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser(), role.id)) {
						throw new ApplicationException("User does not have role: " + role.id);
					}
				}
			}
			
			log.info("Creating action for report: " + "");	
			ActionLink al = new ActionLink();
			String link = null;
			if(ident == null) {
				// Create new link
				link = am.getLink(sd, action, oId, false);
			} else {
				// Update link
				link = am.updateLink(sd, action, oId, ident, request.getRemoteUser(), superUser);
			}
			
			al.link = request.getScheme() + "://" + request.getServerName() + link;					
			
			String resp = gson.toJson(al, ActionLink.class);
			response = Response.ok(resp).build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			SDDataSource.closeConnection("surveyKPI - Reports - GetLink", sd);
		}
		
		return response;
	}
	

	@DELETE
	@Path("/link/{ident}")
	public Response delReport(
			@Context HttpServletRequest request, 
			@PathParam("ident") String ident) { 
		
		Response response = null;
		String connectionString = "surveyKPI - delete reports";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation			
			
		String sql = "delete from users where ident = ? and temporary = 'true' and o_id in "
				+ "(select o_id from users where ident = ?)";
		PreparedStatement pstmt = null;
		
		try {	
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  ident);
			pstmt.setString(2, request.getRemoteUser());		
			log.info("Deleting report: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();			
		}  catch (Exception ex) {
			log.log(Level.SEVERE, ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();
			
		} finally {	
			try {pstmt.close();} catch(Exception e) {}
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}
}

