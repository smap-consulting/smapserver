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
import org.smap.sdal.model.Form;
import org.smap.sdal.model.QuestionLite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all questions for the passed in survey
 *  Only questions that have a data source are returned. The others are pseudo questions
 *  used for example to indicate the beginning or end of a group
 */
@Path("/questionList/{sId}/{language}")
public class QuestionList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(QuestionList.class);
		return s;
	}

	
	/*
	 * Return a list of all questions in the survey
	 * Deprecate.  This service has been replaced by the next one. It should be deleted however 
	 *  there appears to be quite a lot of existing code that depends on the question ID being returned as a string.
	 */
	@GET
	@Produces("application/json")
	public String getQuestions(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language,
			@QueryParam("single_type") String single_type,
			@QueryParam("exc_read_only") boolean exc_read_only,
			@QueryParam("exc_ssc") boolean exc_ssc) { 

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
		String response = null;

		log.info("Get questions: " + single_type + " : " + exc_read_only);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSSC = null;
		try {
			String sql = null;
			String sql1 = null;
			String sqlro = null;
			String sqlst = null;
			String sqlEnd = null;
			ResultSet resultSet = null;

			if(language.equals("none")) {
				language = GeneralUtilityMethods.getDefaultLanguage(connectionSD, sId);
			}
			
			sql1 = "SELECT q.q_id, q.qtype, t.value, q.qname " +
					" FROM form f " +
					" INNER JOIN question q " +
					" ON f.f_id = q.f_id " +
					" LEFT OUTER JOIN translation t " +
					" ON q.qtext_id = t.text_id " +
					" AND t.language = ? " +
					" AND t.type = 'none' " + 
					" AND f.s_id = t.s_id " +
					" WHERE f.s_id = ? " +
					" AND q.source is not null "
					+ "and q.soft_deleted = false ";
			
			if(exc_read_only) {
				sqlro = " AND q.readonly = 'false' ";
			} else {
				sqlro = "";
			}
			
			if(single_type != null) {
				sqlst = " AND q.qtype = ? ";
			} else {
				sqlst = "";
			}
			
			sqlEnd = " ORDER BY f.table_name, q.seq;";
			
			
			sql = sql1 + sqlro  + sqlst + sqlEnd;	
			
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setString(1,  language);
			pstmt.setInt(2,  sId);
			if(single_type != null) {
				pstmt.setString(3,  single_type);
			}

			log.info("Get questions: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				JSONObject joQuestion = new JSONObject();
				
				joQuestion.put("id", resultSet.getString(1));
				joQuestion.put("type",resultSet.getString(2));
				joQuestion.put("q",resultSet.getString(3));
				joQuestion.put("name",resultSet.getString(4));
				jaQuestions.put(joQuestion);			
			}
			
			/*
			 * get the server side calculation questions
			 */
			if(!exc_ssc) {
				String sqlSSC = "select id, name, function, f_id from ssc " +
						" where s_id = ? " + 
						" order by id;";
				pstmtSSC = connectionSD.prepareStatement(sqlSSC);	
				pstmtSSC.setInt(1, sId);
				resultSet = pstmtSSC.executeQuery();
				while(resultSet.next()) {
					JSONObject joQuestion = new JSONObject();
					
					joQuestion.put("id", "s:" + resultSet.getString(1));
					joQuestion.put("name",resultSet.getString(2));
					joQuestion.put("fn",resultSet.getString(3));
					joQuestion.put("f_id",resultSet.getInt(4));
					joQuestion.put("type", "decimal");
					joQuestion.put("is_ssc", true);
					jaQuestions.put(joQuestion);			
				}
			}
			
			response = jaQuestions.toString();
				
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);
		    response = "Error: Failed to retrieve question list";
		} catch (JSONException e) {
			log.log(Level.SEVERE, "Json Error", e);
			response = "Error: Failed to retrieve question list";
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-QuestionList", connectionSD);
		}


		return response;
	}
	
	/*
	 * Return a list of all questions in the survey
	 */
	@GET
	@Path("/new")
	@Produces("application/json")
	public Response getQuestionsNew(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language,
			@QueryParam("single_type") String single_type,
			@QueryParam("exc_read_only") boolean exc_read_only,
			@QueryParam("exc_ssc") boolean exc_ssc) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
		    return Response.serverError().entity("Error: Can't find PostgreSQL JDBC Driver").build();
		}
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		ArrayList<QuestionLite> questions = new ArrayList<QuestionLite> ();
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSSC = null;
		try {
			Form tf = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			
			StringBuffer combinedSql = new StringBuffer("");
			String sql = null;
			String sql1 = null;
			String sqlro = null;
			String sqlst = null;
			String sqlEnd = null;
			ResultSet resultSet = null;

			if(language.equals("none")) {
				language = GeneralUtilityMethods.getDefaultLanguage(sd, sId);
			}
			
			sql1 = "select q.q_id, q.qtype, t.value, q.qname, q.f_id "
					+ "from form f "
					+ "inner join question q "
					+ "on f.f_id = q.f_id "
					+ "left outer join translation t "
					+ "on q.qtext_id = t.text_id "
					+ "and t.language = ? "
					+ "and t.type = 'none' " 
					+ "and f.s_id = t.s_id "
					+ "where f.s_id = ? "
					+ "and q.source is not null "
					+ "and q.soft_deleted = false ";
								
			
			if(exc_read_only) {
				sqlro = " and q.readonly = 'false' ";
			} else {
				sqlro = "";
			}
			
			if(single_type != null) {
				sqlst = " and q.qtype = ? ";
			} else {
				sqlst = "";
			}
			
			sqlEnd = " order by q.q_id asc;";		// Order required for Role Column Merge in survey_roles.js
			
			combinedSql.append(sql1);
			combinedSql.append(sqlro);
			combinedSql.append(sqlst);
			combinedSql.append(sqlEnd);
			sql = combinedSql.toString();	
			
			pstmt = sd.prepareStatement(sql);	 
			pstmt.setString(1,  language);
			pstmt.setInt(2,  sId);
			if(single_type != null) {
				pstmt.setString(3,  single_type);
			}

			log.info("Get questions: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				QuestionLite q = new QuestionLite();
				
				q.id = resultSet.getInt(1);
				q.type = resultSet.getString(2);
				q.q = resultSet.getString(3);
				q.name = resultSet.getString(4);
				q.f_id = resultSet.getInt(5);
				q.toplevel = (q.f_id == tf.id);
				questions.add(q);			
			}
			
			/*
			 * get the server side calculation questions
			 */
			if(!exc_ssc) {
				String sqlSSC = "select id, name, function, f_id from ssc " +
						" where s_id = ? " + 
						" order by id;";
				pstmtSSC = sd.prepareStatement(sqlSSC);	
				pstmtSSC.setInt(1, sId);
				resultSet = pstmtSSC.executeQuery();
				while(resultSet.next()) {
					QuestionLite q = new QuestionLite();
					
					//joQuestion.put("id", "s:" + resultSet.getString(1));
					q.id = resultSet.getInt(1);
					q.name = resultSet.getString(2);
					q.fn = resultSet.getString(3);
					q.f_id = resultSet.getInt(4);
					q.type = "decimal";
					q.is_ssc = true;
					questions.add(q);			
				}
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			response = Response.ok(gson.toJson(questions)).build();
				
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);	    
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-QuestionList", sd);
		}


		return response;
	}
	
	/*
	 * Return a list of all questions in the top level form for the survey
	 */
	@GET
	@Path("/new/topform")
	@Produces("application/json")
	public Response getQuestionsNewTopForm(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language,
			@QueryParam("exc_read_only") boolean exc_read_only) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
		    return Response.serverError().entity("Error: Can't find PostgreSQL JDBC Driver").build();
		}
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		ArrayList<QuestionLite> questions = new ArrayList<QuestionLite> ();
		
		PreparedStatement pstmt = null;
		try {
			StringBuffer combinedSql = new StringBuffer("");
			String sql = null;
			String sql1 = null;
			String sqlro = null;
			String sqlEnd = null;
			ResultSet resultSet = null;

			if(language.equals("none")) {
				language = GeneralUtilityMethods.getDefaultLanguage(sd, sId);
			}
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			
			sql1 = "select q.q_id, q.qtype, t.value, q.qname "
					+"from question q "
					+ "left outer join translation t "
					+ "on q.qtext_id = t.text_id "
					+ "and t.s_id = ? " 
					+ "and t.language = ? "
					+ "and t.type = 'none' "
					+ "where q.f_id = ? "
					+ "and q.source is not null "
					+ "and q.soft_deleted = false ";
								
			
			if(exc_read_only) {
				sqlro = " and q.readonly = 'false' ";
			} else {
				sqlro = "";
			}
			
			sqlEnd = " order by q.q_id asc;";		// Order required for Role Column Merge in survey_roles.js
			
			combinedSql.append(sql1);
			combinedSql.append(sqlro);
			combinedSql.append(sqlEnd);
			sql = combinedSql.toString();	
			
			pstmt = sd.prepareStatement(sql);	 
			pstmt.setInt(1, sId);
			pstmt.setString(2,  language);
			pstmt.setInt(3,  f.id);

			log.info("Get questions for top level form: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				QuestionLite q = new QuestionLite();
				
				q.id = resultSet.getInt(1);
				q.type = resultSet.getString(2);
				q.q = resultSet.getString(3);
				q.name = resultSet.getString(4);
				questions.add(q);			
			}
			

			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			response = Response.ok(gson.toJson(questions)).build();
				
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);	    
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-QuestionList", sd);
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
		String response;
		
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
				
			response = jaQuestions.toString();
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);
		    response = "Error: Failed to retrieve question list";
		} catch (JSONException e) {
			e.printStackTrace();
			response = "Error: Failed to retrieve question list";
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetParent != null) {pstmtGetParent.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-QuestionList", connectionSD);
		}


		return response;
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

