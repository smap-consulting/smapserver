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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;

import utilities.XLSFormManager;

/*
 * Creates an XLS Form from the survey definition
 */

@Path("/xlsForm/{sId}")
public class CreateXLSForm extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(CreateXLSForm.class.getName());
	
	public CreateXLSForm() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);	
	}
	
	@GET
	@Produces("application/x-download")
	public Response getXLSFormService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@QueryParam("filetype") String filetype) throws Exception {
				
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createXLSForm");	
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidDelSurvey(connectionSD, request.getRemoteUser(), sId,superUser);
		// End Authorisation 
		

		org.smap.sdal.model.Survey survey = null;
		Connection cResults = ResultsDataSource.getConnection("createXLSForm");
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			SurveyManager sm = new SurveyManager(localisation, "UTC");
			
			// Get the survey details
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), 
					sId, true, basePath, 
					null, 		// instanceId
					false, 		// get results
					false, 		// Generate dummy data
					true, 		// get property questions
					false, 		// get soft deleted
					false, 		// get hrk
					"internal", // source of choices
					false, 		// get change history
					true, 		// get roles
					superUser, 
					null,		// Geometry format
					false,		// include child surveys
					false		// only get launched
					);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(survey.displayName + "." + filetype, response);
			
			// Create XLSForm
			XLSFormManager xf = new XLSFormManager(filetype);
			xf.createXLSForm(response.getOutputStream(), survey);
			
		} finally {
			
			SDDataSource.closeConnection("createXLSForm", connectionSD);		
			ResultsDataSource.closeConnection("createXLSForm", cResults);
			
		}
		return Response.ok("").build();
	}
	

}
