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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
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
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(FormXML.class);
		return s;
	}

	
	// Respond with XML no matter what is requested
	@GET
	@Produces(MediaType.TEXT_XML)
	public String getForm(@Context HttpServletRequest request,
			@QueryParam("key") String templateName,
			@QueryParam("user") String userId) throws IOException {
		
		log.info("formXML:" + templateName);
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}

		Survey survey = null;
		String user = request.getRemoteUser();
		if(user == null) {
		    user = userId;		// Should only be allowed in a localhost query
		} 
		if(user != null) {
			Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");
            a.isAuthorised(connectionSD, user);
    		SurveyManager sm = new SurveyManager();
    		survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
    		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
            try {
            	if (connectionSD != null) {
            		connectionSD.close();
            		connectionSD = null;
            	}
            } catch (SQLException e) {
            	log.log(Level.SEVERE, "Failed to close connection", e);
            }
        } else {
        	throw new AuthorisationException();
        }
		// End Authorisation

		String response = null;	
		// Extract the data
		try {	    

			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm();
			response = xForm.get(template);
			log.info("userevent: " + user + " : download survey : " + templateName);		
		
		} catch (Exception e) {
			response = e.getMessage();
			log.log(Level.SEVERE, response, e);
		} 
				
		return response;
	}

}

