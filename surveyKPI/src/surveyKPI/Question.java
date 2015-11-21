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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Return meta information about a question
 * 	Question Type
 *  If Select or Select1
 *  Array of option data
 *  	option id
 *  	option value
 *  	option label
 */

@Path("/question/{sId}/{lang}/{qId}")
public class Question extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);

	private static Logger log =
			 Logger.getLogger(Question.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Question.class);
		return s;
	}

	
	@Path("/getMeta")
	@GET
	@Produces("application/json")
	public String getQuestionMeta(
			@Context HttpServletRequest request,
			@PathParam("sId") String sId, 
			@PathParam("lang") String lang, 
			@PathParam("qId") String qId) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Error: Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Question");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		JSONObject jo = new JSONObject();
		String qType = null;
		String qName = null;
		String f_id = null;
		String calculate = null;
		
		// Escape any quotes
		if(sId != null) {
			sId = sId.replace("'", "''"); 
		} 
		if(lang != null) {
			lang = lang.replace("'", "''"); 
		} 
		if(qId != null) {
			qId = qId.replace("'", "''"); 
		} 
			 
		PreparedStatement pstmt = null;
		try {
			// Add the qId to the response so that it is available in the question meta object
			jo.put("qId", qId);
					
			String sql = null;
			ResultSet resultSet = null;
		
			sql = "SELECT qtype, qname, f_id, calculate FROM question " +
					"WHERE q_id = ?;";
			log.info(sql);
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setInt(1, Integer.parseInt(qId));
			resultSet = pstmt.executeQuery();
			
			while (resultSet.next()) {
				qType = resultSet.getString(1);
				qName = resultSet.getString(2);
				f_id = resultSet.getString(3);
				calculate = resultSet.getString(4);
				if(calculate != null) {
					qType = "calculate";	// Need to differentiate type of calculate questions			
				}
			}
			
			jo.put("type", qType);
			jo.put("name", qName);
			jo.put("f_id", f_id);

			if(qType.equals("select1") || qType.equals("select")) {
				
				JSONArray ja = new JSONArray();	
				
				// Close the existing statement and results set
				try {
					if (pstmt != null) {
						pstmt.close();
						pstmt = null;
					}
				} catch (SQLException e) {
				
				}
				
				/*
				 * options for this question
				 */
				sql = "SELECT o.o_id, o.ovalue, t.value" +
						" FROM option o, translation t, question q " +  		
						" WHERE o.label_id = t.text_id" +
						" AND t.s_id = ? " +
						" AND t.language = ? " +
						" AND q.q_id = ? " + 
						" AND q.l_id = o.l_id " +
						" AND t.type = 'none' " +		// TODO support multiple types as options
						" ORDER BY o.seq;";			
				
				log.info(sql);
				pstmt = connectionSD.prepareStatement(sql);	 
				pstmt.setInt(1, Integer.parseInt(sId));
				pstmt.setString(2, lang);
				pstmt.setInt(3, Integer.parseInt(qId));
				resultSet = pstmt.executeQuery();

				while (resultSet.next()) {								

					JSONObject jp = new JSONObject();
					jp.put("id", resultSet.getString(1));
					jp.put("value", resultSet.getString(2));
					jp.put("label", resultSet.getString(3));
					
					ja.put(jp);
				} 
			    
				jo.put("options", ja);
			}
			
			log.fine(jo.toString(4));
			
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE, "Exception", e);
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
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
		}

		return jo.toString();
	}


}

