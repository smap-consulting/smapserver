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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/*
 * Returns a list of all options for the specified question
 */
@Path("/optionList/{sId}/{language}/{qId}")
public class OptionList extends Application {

	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(OptionList.class);
		return s;
	}
	
	@GET
	@Produces("application/json")
	public String getOptions(@Context HttpServletRequest request,
			@PathParam("sId") int sId, 
			@PathParam("language") String language,
			@PathParam("qId") int qId) { 
			
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-OptionList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		JSONArray jaOptions = new JSONArray();

		PreparedStatement pstmt = null;
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			/*
			 * Get the options for this question
			 * TODO support multiple languages
			 */
			sql = "SELECT o.o_id, o.ovalue, t.value " +
					"FROM option o, translation t, question q " +  		
					"WHERE o.label_id = t.text_id " +
					"AND t.s_id =  ? " + 
					"AND t.language = ? " +
					"AND q.q_id = ? " +
					"AND q.l_id = o.l_id " +
					"ORDER BY o.seq;";			
			
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setInt(1, sId);
			pstmt.setString(2, language);
			pstmt.setInt(3, qId);
			resultSet = pstmt.executeQuery(); 
			while(resultSet.next()) {
				String id = resultSet.getString(1);
				String v = resultSet.getString(2);
				String o = resultSet.getString(3);
				JSONObject joOptions = new JSONObject();
				
				joOptions.put("id", id);
				joOptions.put("label",o);
				joOptions.put("value", v);
				jaOptions.put(joOptions);			
			}
				
		} catch (SQLException e) {
		    System.out.println("Connection Failed! Check output console");
		    e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-OptionList", connectionSD);
		}


		return jaOptions.toString();
	}

}

