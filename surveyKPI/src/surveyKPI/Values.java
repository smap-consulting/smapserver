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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.OptionInfo;
import utilities.QuestionInfo;
import utilities.SurveyInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns the values for the specified question and record id
 */


@Path("/values/{sId}/{qId}/{rId}")
public class Values extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Values.class.getName());
	
	ArrayList<String> results = new ArrayList<String> ();
	

	// Return results for an option
	@GET
	@Produces("application/json")
	public Response getResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Survey Id
			@PathParam("qId") int qId,				// Question Id
			@PathParam("rId") int rId,				// Question Id
			@QueryParam("lang") String lang			// Language
		
			) { 
	
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		System.out.println("urlprefix: " + urlprefix);
		
		Response response = null;
		Connection cResults = null;
		PreparedStatement pstmt = null;
				
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Values");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
					
		try {
			cResults = ResultsDataSource.getConnection("surveyKPI-Values");
			
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(cResults, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			
			/*
			 * Get Survey meta data
			 */
			//SurveyInfo survey = new SurveyInfo(sId, connectionSD);
			
			/*
			 * Add the the main question to the array of questions
			 */
			QuestionInfo aQ = new QuestionInfo(localisation, tz, sId, qId, sd, cResults, request.getRemoteUser(), false, lang, urlprefix, oId);

			/*
			 * Create the sql statement
			 */	
			String sql = "select " + aQ.getSelect() + " from " + aQ.getTableName() + 
					" where prikey=?";
				
			log.info(sql + " : " + rId);
			pstmt = cResults.prepareStatement(sql);
			pstmt.setInt(1, rId);
			ResultSet resultSet = pstmt.executeQuery();

			/*
			 * Collect the data
			 */
			System.out.println("Questions type: " + aQ.getType());
			if(resultSet.next()) {
				if(aQ.getType().equals("select")) {
					ArrayList<OptionInfo> oList = aQ.getOptions();
					for(int i = 0; i < oList.size(); i++) {
						boolean optionSet = resultSet.getBoolean(i + 2);
						if(optionSet) {
							OptionInfo o = oList.get(i);
							results.add(o.getColumnName());
						}
					}
				} else if(aQ.getType().equals("select1")) {
					results.add(resultSet.getString(2));
				}
			}

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(results);
			response = Response.ok(resp).build();
				
				
		} catch (SQLException e) {
		    log.info("Message=" + e.getMessage());
		    String msg = e.getMessage();
			if(!msg.contains("does not exist")) {	// Don't do a stack dump if the table did not exist that just means no one has submitted results yet
				log.log(Level.SEVERE,"SQL Error", e);
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(msg).build();
			}

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Values", sd);
			ResultsDataSource.closeConnection("surveyKPI-Values", cResults);
		}


		return response;

	}
	

}