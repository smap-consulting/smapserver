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
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.LanguageItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionLite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

/*
 * Returns a list of all options for the specified question
 */
@Path("/optionList/{sId}/{language}/{qId}")
public class OptionList extends Application {

	Authorise a = null;
	
	public OptionList() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	@GET
	@Produces("application/json")
	public String getOptions(@Context HttpServletRequest request,
			@PathParam("sId") int sId, 
			@PathParam("language") String language,
			@PathParam("qId") int qId) { 
	
		String connectionString = "surveyKPI-OptionList";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidQuestion(sd, request.getRemoteUser(), sId, qId);
		// End Authorisation
		
		ArrayList<OptionLite> options = new ArrayList<> ();
		Gson gson =  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();

		Connection cResults = null;
		PreparedStatement pstmt = null;
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tz = "UTC";
			
			cResults = ResultsDataSource.getConnection(connectionString);
			boolean external = GeneralUtilityMethods.hasExternalChoices(sd, qId);
			
			if(external) {
				String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
				int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
				ArrayList<Option> oExternal = GeneralUtilityMethods.getExternalChoices(sd, 
						cResults, localisation, request.getRemoteUser(), oId, sId, qId, null, surveyIdent, tz);
				int idx = 0;
				int languageIdx = 0;
				for(Option o : oExternal) {
					OptionLite ol = new OptionLite();
					ol.id = o.id;
					ol.value = o.value;
					
					// Get the label for the passed in language
					if(idx++ == 0) {		// Get the language index - only need to do this for the first choice					
						for(LanguageItem item : o.externalLabel) {
							if(language == null || language.equals("none") || language.equals(item.language)) {
								break;
							} else {
								languageIdx++;
							}
						}
					} 
					if(o.labels != null && o.labels.size() > languageIdx) {
						ol.label = o.labels.get(languageIdx).text;
					}
					
					options.add(ol);
				}
			} else {
				String sql = null;
				ResultSet resultSet = null;
				
				/*
				 * Get the internal options for this question
				 */
				sql = "SELECT o.o_id, o.ovalue, t.value " +
						"FROM option o, translation t, question q " +  		
						"WHERE o.label_id = t.text_id " +
						"AND t.s_id =  ? " + 
						"AND t.language = ? " +
						"AND q.q_id = ? " +
						"AND q.l_id = o.l_id " +
						"ORDER BY o.seq;";			
				
				pstmt = sd.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				pstmt.setString(2, language);
				pstmt.setInt(3, qId);
				resultSet = pstmt.executeQuery(); 
				while(resultSet.next()) {
					OptionLite o = new OptionLite();
					
					o.id = resultSet.getInt(1);
					o.value = resultSet.getString(2);
					o.label = resultSet.getString(3);
	
					options.add(o);			
				}
			}
				
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
		}


		return gson.toJson(options);
	}

}

