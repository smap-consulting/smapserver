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
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/*
 * Returns a list of all select_one, select_multiple questions that have other text for the passed in survey
 *  Only questions that have a data source are returned. The others are pseudo questions
 *  used for example to indicate the beginning or end of a group
 */
@Path("/review/{sId}/{language}/other")
public class ReviewQuestionsOther extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ReviewQuestionsOther.class);
		return s;
	}

	ArrayList<Question> selectQuestions = new ArrayList<Question> ();
	ArrayList<Question> textQuestions = new ArrayList<Question> ();
	ArrayList<Question> questions = new ArrayList<Question> ();
	
	private class Option {
		int id;
		String value;
		String label;
	}
	
	private class RelevantQuestion {
		String path;
		String otherValue;
	}
	
	private class Question {
		int id;
		String type;
		String relevant;
		String name;
		String label;
		String path;
		ArrayList<RelevantQuestion> relevantQuestions = new ArrayList<RelevantQuestion> ();
		ArrayList<Question> others =  new ArrayList<Question> ();
		ArrayList<Option> options =  new ArrayList<Option> ();
	}
	
	private static Logger log =
			 Logger.getLogger(Region.class.getName());
	
	/*
	 * Return a list of select1 and select type questions that have a text question that is 
	 * shown conditionally based on the selection of one of the options.
	 * Return a list of each of these dependent text questions along with each select question
	 */
	@GET
	@Produces("application/json")
	public Response getQuestions(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
		    return Response.serverError().entity(e.getMessage()).build();

		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		Response response = null;

		/*
		 * For this survey get all the questions that have  "select one" and 
		 * "select multiple" questions
		 */	
		PreparedStatement pstmt = null;
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			/*
			 * Get the select1 and select many questions in this survey
			 * Also get the text questions that have a "relevant" clause
			 * TODO support multiple languages
			 */
			sql = "SELECT q.q_id, q.qtype, t.value, q.relevant, q.qname, q.path " +
					" FROM form f, question q, translation t " +  
					" WHERE f.f_id = q.f_id " +
					" AND f.s_id = t.s_id " +
					" AND q.qtext_id = t.text_id " +
					" AND t.language = ? " +
					" AND t.type = 'none' " +		// TODO support multiple types as questions
					" AND f.s_id = ? " +
					" AND q.source is not null" +	
					" AND q.readonly = 'false' " +
			        " ORDER BY f.table_name, q.seq;";
			
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setString(1,  language);
			pstmt.setInt(2,  sId);
			
			log.info("SQL: " + sql + " : " + language + " : " + sId);
			resultSet = pstmt.executeQuery();
			
			/*
			 * Get the questions
			 */
			while(resultSet.next()) {
				
				Question q = new Question();
				q.id = resultSet.getInt(1);
				q.type = resultSet.getString(2);
				q.label = resultSet.getString(3);
				q.relevant = resultSet.getString(4);
				q.name = resultSet.getString(5);
				q.path = resultSet.getString(6);
				
				
				System.out.println("Question: " + q.name + " : " + q.type + " : " + q.relevant);
				if(q.type.startsWith("select")) {
					selectQuestions.add(q);					
				} else if (q.type.equals("string") && q.relevant != null) {
					addRelevantQuestionNames(q);
					textQuestions.add(q);
				}				
			}
			
			/*
			 * Add text questions to the output that are dependent on a select question
			 */
			for(int i = 0; i < selectQuestions.size(); i++) {
				Question sQ = selectQuestions.get(i);
				for(int j = 0; j < textQuestions.size(); j++) {
					Question tQ = textQuestions.get(j);
					for(int k = 0; k < tQ.relevantQuestions.size(); k++) {
						if (sQ.path.equals(tQ.relevantQuestions.get(k).path)) {
							getOptions(sQ, language, connectionSD);		// Get the options for the select question
							sQ.others.add(tQ);
						}
					}
				}
				if(sQ.others.size() > 0) {
					questions.add(sQ);
				}
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(questions);
			response = Response.ok(resp).build();
				
		} catch (SQLException e) {
		    System.out.println("Connection Failed! Check output console");
		    e.printStackTrace();
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
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

		return response;
	}
	
	private void getOptions(Question q, String language, Connection connectionSD) throws SQLException {
		
		PreparedStatement pstmt = null;

		String sql = null;
		ResultSet resultSet = null;
		
		/*
		 * Get the options for the supplied question
		 */
		sql = "SELECT o.o_id, o.ovalue, t.value " +
				" FROM option o, translation t, question q " +  
				" WHERE o.label_id = t.text_id " +
				" AND t.language = ? " +
				" AND t.type = 'none' " +		
				" AND q.q_id = ?" +	
				" AND q.l_id = o.l_id" +
		        " ORDER BY o.seq;";
		
		pstmt = connectionSD.prepareStatement(sql);	 
		pstmt.setString(1,  language);
		pstmt.setInt(2,  q.id);
		
		log.info("SQL: " + sql + " : " + language + " : " + q.id);
		resultSet = pstmt.executeQuery();

		while(resultSet.next()) {
			
			Option o = new Option();
			o.id = resultSet.getInt(1);
			o.value = resultSet.getString(2);
			o.label = resultSet.getString(3);
			
			q.options.add(o);
		}
	}
	
	private void addRelevantQuestionNames(Question q) {
		int idx1 = -1;
		int idx2 = -1;
		
		String path = "";
		String otherValue = "";
		
		System.out.println("addRelevantQuestionNames: " + q.relevant);
		while((idx1 = q.relevant.indexOf("selected", idx1 + 1)) >= 0) {
			idx1 = q.relevant.indexOf("(", idx1 + 1);
			if(idx1 > 0) {
				idx2 = q.relevant.indexOf(",", idx1);
				if(idx2 > 0) {
					path = q.relevant.substring(idx1+1, idx2);
					
					System.out.println("=== " + q.name + ":" + path);
				}
			}
			RelevantQuestion rq = new RelevantQuestion();
			rq.path = path.trim();
			q.relevantQuestions.add(rq);
		}
	}

}

