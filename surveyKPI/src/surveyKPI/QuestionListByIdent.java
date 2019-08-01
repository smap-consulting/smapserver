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
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.QuestionLite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all questions for the passed in survey
 *  Only questions that have a data source are returned. The others are pseudo questions
 *  used for example to indicate the beginning or end of a group
 */
@Path("/questionListIdent/{sIdent}/{language}")
public class QuestionListByIdent extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());

	public QuestionListByIdent() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Return a list of all questions in the survey that is specified by its ident
	 */
	@GET
	@Produces("application/json")
	public Response getQuestionsNewIdent(@Context HttpServletRequest request,
			@PathParam("sIdent") String sIdent,
			@PathParam("language") String language,
			@QueryParam("single_type") String single_type,
			@QueryParam("exc_read_only") boolean exc_read_only,
			@QueryParam("exc_ssc") boolean exc_ssc,
			@QueryParam("inc_meta") boolean inc_meta) { 

	
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionList");
		boolean superUser = false;
		int sId = 0;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
			sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		ArrayList<QuestionLite> questions = new ArrayList<QuestionLite> ();
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSSC = null;
		try {
			Form tf = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			
			if(inc_meta) {
				ArrayList<MetaItem> metaItems = GeneralUtilityMethods.getPreloads(sd, sId);
				for(MetaItem mi : metaItems) {
					if(mi.isPreload) {
						QuestionLite q = new QuestionLite();
						q.id = mi.id;
						q.name = mi.name;
						q.f_id = tf.id;
						q.type = mi.type;
						q.is_ssc = false;
						
						questions.add(q);
					}	
				}
			}
			
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
			
			if(single_type != null && single_type.equals("string")) {
				sqlst = " and (q.qtype = 'string' or q.qtype = 'calculate' or q.qtype = 'barcode') ";
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
	


}

