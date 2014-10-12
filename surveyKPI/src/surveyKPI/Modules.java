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
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import model.Module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/*
 * Returns data for the passed in table name
 */
@Path("/modules/{sId}")
public class Modules extends Application {

	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	// Tell class loader about the root classes.  
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Modules.class);
		return s;
	}

	
	/*
	 * Return the list of modules in this survey
	 */
	@GET
	@Produces("application/json")
	public Response getModules(@Context HttpServletRequest request,
			@PathParam("sId") String sId) { 
		
		Response response = null;	
			
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Modules");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		if(sId != null) {
			sId = sId.replace("'", "''"); 
		}
		ArrayList<Module> mods = new ArrayList<Module> ();	
		Stack<ArrayList<Module>> stack = new Stack<ArrayList<Module>> ();

		PreparedStatement pstmt = null;
		try {
			String sql = null;
			
			/*
			 * Restrict list to top level form 
			 */
			sql = "SELECT q.q_id, q.qtype, q.qname " +
					"FROM form f, question q " +  
					"WHERE f.f_id = q.f_id " +
					"AND f.s_id = " + sId + " " +
					"AND f.parentform is null " +
					"ORDER BY q.seq;";

			
			pstmt = connectionSD.prepareStatement(sql);	 			
			ResultSet resultSet = pstmt.executeQuery();

			ArrayList<Module> current = mods;
			while (resultSet.next()) {
				
				String type = resultSet.getString("qtype");
				
				// Restrict modules to groups
				if(type.equals("begin repeat") || type.equals("begin group")) {
					Module m = new Module();
					m.type = type;
					m.id = resultSet.getInt("q_id");
					m.name = resultSet.getString("qname");
					m.children = new ArrayList<Module> ();
					current.add(m);
					stack.push(current);
					current = m.children;
				} else if(type.equals("end group")) {
					current = stack.pop();
				}

				
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mods);
			response = Response.ok(resp).build();	
				
		} catch (SQLException e) {			
		    e.printStackTrace();
			response = Response.serverError().build();
		} catch (Exception e) {
			e.printStackTrace();
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
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		}

		return response;
	}

}

