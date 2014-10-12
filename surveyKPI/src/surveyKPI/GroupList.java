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

import model.Group;
import model.Project;
import model.User;

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
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(GroupList.class);
		return s;
	}

	
	/*
	 * Return a list of groups
	 */
	@GET
	@Produces("application/json")
	public Response getGroups(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-GroupList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<Group> groups = new ArrayList<Group> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;
			
			sql = "select id, name " +
					" from groups " +  
					" order by name ASC;";				
						
			pstmt = connectionSD.prepareStatement(sql);

			log.info("SQL: " + sql);
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				Group group = new Group();
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
				log.log(Level.SEVERE,"Failed to close connection: ", e);
			}
		}

		return response;
	}

}

