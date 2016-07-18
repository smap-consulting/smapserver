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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of the projects that a user is assigned to
 */
@Path("/myProjectList")
public class MyProjectList extends Application {
	
	//Authorise a = new Authorise(Authorise.ANALYST);

	private static Logger log =
			 Logger.getLogger(MyProjectList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(MyProjectList.class);
		return s;
	}

	
	@GET
	@Produces("application/json")
	public Response getMyProjects(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Authorisation is not required only the users project will be returned
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-MyProjectList");
		// End Authorisation
		
		/*
		 * 
		 */	
		PreparedStatement pstmt = null;
		ArrayList<Project> projects = new ArrayList<Project> ();
		
		try {
			String sql = null;
			ResultSet resultSet = null;

			sql = "select p.id, p.name " +
					" from project p, users u, user_project up " + 
					" where u.ident = ? " +
					" and u.id = up.u_id " + 
					" and p.id = up.p_id " +
					" and p.o_id = u.o_id " +
					" and up.restricted = false " +
					" and up.allocated = true " +
					" order by name ASC;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());

			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			resultSet = pstmt.executeQuery();
			while(resultSet.next()) {
				Project project = new Project();
				project.id = resultSet.getInt("id");
				project.name = resultSet.getString("name");
				projects.add(project);
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(projects);
			response = Response.ok(resp).build();
				
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"Error: ", e);
		    response = Response.serverError().build();
		    
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			SDDataSource.closeConnection("surveyKPI-MyProjectList", connectionSD);
		}

		return response;
	}
	
}

