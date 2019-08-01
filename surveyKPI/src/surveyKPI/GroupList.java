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
import org.smap.sdal.model.UserGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of authorisation groups
 */
@Path("/groupList")
public class GroupList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ADMIN);

	private static Logger log =
			 Logger.getLogger(GroupList.class.getName());

	
	/*
	 * Return a list of groups
	 */
	@GET
	@Produces("application/json")
	public Response getGroups(@Context HttpServletRequest request) { 

		Response response = null;
		String connectionString = "surveyKPI-GroupList";
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection(connectionString);
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
	
		PreparedStatement pstmt = null;
		ArrayList<UserGroup> groups = new ArrayList<UserGroup> ();
		
		try {
			String sql = "select id, name "
					+ "from groups "  
					+ "order by name asc";				
						
			pstmt = connectionSD.prepareStatement(sql);
			ResultSet resultSet = pstmt.executeQuery();
			
			while(resultSet.next()) {
				UserGroup group = new UserGroup();
				group.id = resultSet.getInt("id");
				group.name = resultSet.getString("name");
				groups.add(group);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(groups);
			response = Response.ok(resp).build();
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, connectionSD);
		}

		return response;
	}

}

