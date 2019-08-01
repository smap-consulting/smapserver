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
import java.util.Locale;
import java.util.ResourceBundle;
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
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.utilities.GetXForm;


/*
 * Get surveys assigned to the user (ODK Format)
 * Output is in JavaRosa compliant XForms
 */

@Path("/formXML")
public class FormXML extends Application{

	Authorise a = new Authorise(null, Authorise.ENUM);

	private static Logger log =
			Logger.getLogger(FormXML.class.getName());


	@GET
	@Produces(MediaType.TEXT_XML)
	public String getForm(@Context HttpServletRequest request,
			@QueryParam("key") String templateName,
			@QueryParam("deviceID") String deviceId) throws IOException {

		log.info("formXML:" + templateName);

		// Authorisation - Access

		Survey survey = null;
		String user = request.getRemoteUser();
		boolean superUser = false;
		ResourceBundle localisation = null;
		String response = null;	

		if(user != null) {
			Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");

			try {

				// Get some data where we will ignore a failure
				try {
					superUser = GeneralUtilityMethods.isSuperUser(connectionSD, user);
					Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request, request.getRemoteUser()));
					localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				} catch (Exception e) {
				}

				String tz = "UTC";
				
				// Authorisation
				a.isAuthorised(connectionSD, user);
				SurveyManager sm = new SurveyManager(localisation, "UTC");
				survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
				a.isValidSurvey(connectionSD, user, survey.id, false, superUser);	// Validate that the user can access this survey

				// Extract the data
				SurveyTemplate template = new SurveyTemplate(localisation);
				template.readDatabase(survey.id, false);
				GetXForm xForm = new GetXForm(localisation, user, tz);
				response = xForm.get(template, false, true, false, user);
				log.info("userevent: " + user + " : download survey : " + templateName);		

				// Record that this form was downloaded by this user
				GeneralUtilityMethods.recordFormDownload(connectionSD, user, survey.ident, survey.version, deviceId);
			} catch (AuthorisationException ae) { 
				throw ae;
			} catch (Exception e) {
				response = e.getMessage();
				log.log(Level.SEVERE, response, e);
			} finally {
				SDDataSource.closeConnection("surveyMobileAPI-FormXML", connectionSD);
			}
		}

		return response;
	}

	/*
	 * Service called if the request includes a temporary user id
	 */
	@GET
	@Path("/id/{temp_user}")
	@Produces(MediaType.TEXT_XML)
	public String getFormTemporaryUser(@Context HttpServletRequest request,
			@PathParam("temp_user") String tempUser,
			@QueryParam("key") String templateName) throws IOException {

		log.info("formXML temporary user:" + templateName);

		Survey survey = null;
		ResourceBundle localisation = null;

		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}

		a.isValidTemporaryUser(connectionSD, tempUser);
		a.isAuthorised(connectionSD, tempUser);

		// Get the users locale
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request, request.getRemoteUser()));
			localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		} catch (Exception e) {

		}

		String tz = "UTC";
		
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
		a.isValidSurvey(connectionSD, tempUser, survey.id, false, superUser);	// Validate that the user can access this survey
		SDDataSource.closeConnection("surveyMobileAPI-FormXML", connectionSD);

		// End Authorisation

		String response = null;	
		// Extract the data
		try {	    

			SurveyTemplate template = new SurveyTemplate(localisation);
			template.readDatabase(survey.id, false);
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm(localisation, request.getRemoteUser(), tz);
			response = xForm.get(template, false, true, false, request.getRemoteUser());
			log.info("userevent Temporary User: " + tempUser + " : download survey : " + templateName);		

		} catch (Exception e) {
			response = e.getMessage();
			log.log(Level.SEVERE, response, e);
		} 

		return response;
	}

}

