/*****************************************************************************

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

 ******************************************************************************/

package surveyMobileAPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.utilities.GetXForm;


/*
 * Get instance data
 * Output is in JavaRosa compliant XForms results file
 */

@Path("/instanceXML")
public class InstanceXML extends Application{

	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(InstanceXML.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	/*
	 * Parameters
	 *  sName : Survey Name
	 *  prikey : Primary key of data record in the top level table of this survey
	 *  instructions on whether to preserve or replace each record
	 */
	@GET
	@Path("/{sName}/{priKey}")
	@Produces(MediaType.TEXT_XML)
	public Response getInstance(@Context HttpServletRequest request,
			@PathParam("sName") String templateName,
			@PathParam("priKey") int priKey,
			@QueryParam("taskkey") int taskKey,	// Task id, if set initial data is from task
			@QueryParam("key") String key,		// Optional
			@QueryParam("keyval") String keyval	// Optional
			) throws IOException {

		Response response = null;
		String connectionString = "surveyMobileAPI-InstanceXML";
		
		log.info("instanceXML: Survey=" + templateName + " priKey=" + priKey + " key=" + key + " keyval=" + keyval);
		
		String user = request.getRemoteUser();
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		
		// Get the users locale
		ResourceBundle localisation = null;
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		} catch (Exception e) {

		}
		
		String tz = "UTC";
		
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		Survey survey = sm.getSurveyId(sd, templateName);	// Get the survey id from the templateName / key
		a.isAuthorised(sd, user);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, user, survey.id, false, superUser);	// Validate that the user can access this survey
		a.isBlocked(sd, survey.id, false);			// Validate that the survey is not blocked
		
		lm.writeLog(sd, survey.id, request.getRemoteUser(), "view", "Get results instance: priKey=" + priKey + " key=" + key + " keyval=" + keyval);
		
		SDDataSource.closeConnection(connectionString, sd);
		// End Authorisation
		 
		// Extract the data
		try {
			
           	SurveyTemplate template = new SurveyTemplate(localisation);
			template.readDatabase(survey.id, false);
			
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			GetXForm xForm = new GetXForm(localisation, request.getRemoteUser(), tz);
			String instanceXML = xForm.getInstanceXml(survey.id, templateName, template, key, keyval, priKey, false, false, taskKey, urlprefix);	
			
			response = Response.ok(instanceXML).build();
		
		} catch (ApplicationException e) {
		    String msg = e.getMessage();	
			log.info(msg);	
			lm.writeLog(sd, survey.id, request.getRemoteUser(), "Error", "Failed to get instance data: " + msg);
			response = Response.status(Status.NOT_FOUND).entity(msg).build();
		}  catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			lm.writeLog(sd, survey.id, request.getRemoteUser(), "Error", "Failed to get instance data: " + e.getMessage());
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} 
				
		return response;
	}
	

	
	

    


 
}

