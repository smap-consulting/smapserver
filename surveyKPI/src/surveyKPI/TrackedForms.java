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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the questions in the top level form for the requested survey
 */
@Path("/tracked")
public class TrackedForms extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	/*
	 * Return a list of all questions in the top level form
	 *  Exclude read only
	 */
	@GET
	@Path("/questionsInMainForm/{sId}")
	@Produces("application/json")
	public Response getQuestions(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		ArrayList<TableColumn> questions = new ArrayList<TableColumn> ();
		Response response = null;
		
		PreparedStatement pstmt = null;
		try {
			
			String sql = "select  "
					+ "q.qname as name "
					+ "from form f, question q "
					+ "where f.f_id = q.f_id "
					+ "and f.s_id = ? "
					+ "and q.source is not null "
					+ "and q.readonly = 'false' "
					+ "and (f.parentform is null or f.parentform = 0) "
					+ "order by q.seq asc;";
			
			
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setInt(1,  sId);

			log.info("Get questions: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				TableColumn q = new TableColumn(rs.getString("name"));
				
				questions.add(q);
			}
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(questions);
			response = Response.ok(resp).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
			}
		}


		return response;
	}
	
	/*
	 * Returns a list of all questions for the passed in survey starting from the passed in form
	 *  and adding questions from parent forms
	 * This is used for selecting valid questions in an export where only a single line through the form 
	 *  hierarchy is allowed
	 *  Only questions that have a data source are returned. The others are pseudo questions
	 *  used for example to indicate the beginning or end of a group
	 */
	@Path("/{form}")
	@GET
	@Produces("application/json")
	public String getQuestionsFromForm(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language,
			@PathParam("form") int fId) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		JSONArray jaQuestions = new JSONArray();
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetParent = null;
		
		try {
			if(language.equals("none")) {
				language = GeneralUtilityMethods.getDefaultLanguage(connectionSD, sId);
			}
			
			String sql = "SELECT q.q_id, q.qtype, t.value, q.qname " +
					" FROM form f " +
					" INNER JOIN question q " +
					" ON f.f_id = q.f_id " +
					" LEFT OUTER JOIN translation t " +
					" ON q.qtext_id = t.text_id " +
					" AND t.language = ? " +
					" AND t.type = 'none' " + 
					" AND f.s_id = t.s_id " +
					" WHERE f.s_id = ? " +
					" AND f.f_id = ? " +
					" AND q.source is not null" +
					" AND q.readonly = 'false' " +
					" ORDER BY q.seq;";		
		
			pstmt = connectionSD.prepareStatement(sql);	
			
			// Prepared Statement to get parent form id
			String sqlGetParent = "select parentform from form where f_id = ?";
			pstmtGetParent = connectionSD.prepareStatement(sqlGetParent);
			
			// Add the user who submitted the report (this is not in the list of questions for the survey)
			JSONObject joQuestion = new JSONObject();
			
			joQuestion.put("id", 0);
			joQuestion.put("type", "string");
			joQuestion.put("q", "User");
			joQuestion.put("name", "_user");
			jaQuestions.put(joQuestion);	
			
			// Get the other questions
			getQuestionsForm(pstmt, pstmtGetParent, language, sId, fId, jaQuestions);
				
		} catch (SQLException e) {
		    System.out.println("Connection Failed! Check output console");
		    e.printStackTrace();
		    return "Error: Failed to retrieve dataset";
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetParent != null) {pstmtGetParent.close();	}} catch (SQLException e) {	}
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		}


		return jaQuestions.toString();
	}
	
	private void getQuestionsForm(
			PreparedStatement pstmt, 
			PreparedStatement pstmtGetParent,
			String language, 
			int sId, 
			int fId, 
			JSONArray jaQuestions) throws SQLException, JSONException {
		
		// Get the parents questions first
		pstmtGetParent.setInt(1, fId);
		
		log.info("SQL: getting parent id: " + pstmtGetParent.toString());
		ResultSet resultSet = pstmtGetParent.executeQuery();
		if(resultSet.next()) {
			int pId = resultSet.getInt(1);
			if(pId != 0) {
				getQuestionsForm(pstmt, pstmtGetParent, language, sId, pId, jaQuestions);
			}
		}
		
		pstmt.setString(1,  language);
		pstmt.setInt(2,  sId);
		pstmt.setInt(3,  fId);
		
		log.info("Getting question forms: " + pstmt.toString());
		resultSet = pstmt.executeQuery();
		
		while(resultSet.next()) {
			JSONObject joQuestion = new JSONObject();
			
			joQuestion.put("id", resultSet.getString(1));
			joQuestion.put("type",resultSet.getString(2));
			joQuestion.put("q",resultSet.getString(3));
			joQuestion.put("name",resultSet.getString(4));
			jaQuestions.put(joQuestion);			
		}
		
	}

}

