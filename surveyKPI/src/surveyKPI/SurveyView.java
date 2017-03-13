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

import org.smap.model.TableManager;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.managers.LinkageManager;
import org.smap.sdal.managers.ManagedFormsManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.ActionLink;
import org.smap.sdal.model.Filter;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Link;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.ManagedFormUserConfig;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.TableColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the questions in the top level form for the requested survey
 */
@Path("/surveyview")
public class SurveyView extends Application {
	
	Authorise aManage = null;
	Authorise aNormal = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	public SurveyView() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.MANAGE);		// Enumerators with MANAGE access can process managed forms
		aManage = new Authorise(authorisations, null);		
	}
	
	/*
	 * Get a list of the survey views available to this user
	 * If a survey id, queryId and/or managed id is specified then only matching views will be returned
	 */
	@GET
	@Path("/list")
	@Produces("application/json")
	public Response getList(
			@QueryParam("survey") int sId,
			@QueryParam("managed") int mId,
			@QueryParam("query") int queryId) {
		Response response = null;
		return response;
	}
	
	/*
	 * Return the survey view
	 */
	@GET
	@Path("/{viewId}")
	@Produces("application/json")
	public Response getReportConfig(@Context HttpServletRequest request,
			@PathParam("viewId") int viewId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-GetReportConfig");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}

		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-GetReportConfig");
		Response response = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {

		
				
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-GetReportConfig", sd);
			ResultsDataSource.closeConnection("surveyKPI-GetReportConfig", cResults);
		}


		return response;
	}	

}

