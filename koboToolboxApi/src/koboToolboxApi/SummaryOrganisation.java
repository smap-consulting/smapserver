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
import javax.servlet.http.HttpServletResponse;

import managers.DataManager;
import managers.SummaryManager;
import model.DataEndPoint;
import model.SummaryEndPoint;
import model.SummaryResultsC3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.Survey;

import utils.Utils;

/*
 * Returns overview data such as the number of submissions
 *  User can get data for any organisations
 * This is a Smap specific extension to the KoboToolbox API
 */
@Path("/v1/summary/organisation")
public class SummaryOrganisation extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(SummaryOrganisation.class.getName());
	
	public SummaryOrganisation() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ORG);
		a = new Authorise(authorisations, null);
	}
	
	@GET
	@Produces("application/json")
	@Path("/tasks")
	public Response getTasksOrganisation(@Context HttpServletRequest request,
			@QueryParam("organisation") int orgId,
			@QueryParam("group") String group,
			@QueryParam("x") String x,
			@QueryParam("period") String period) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get task summary");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		try {
			
			SummaryManager sm = new SummaryManager(orgId);
			response = sm.getTasks(sd, group, x, period);

		} catch (Exception e) {
			e.printStackTrace();
			response = Response.serverError().build();
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
		
		return response;
		
	}
	
	
}

