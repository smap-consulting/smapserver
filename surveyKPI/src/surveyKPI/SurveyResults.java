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
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/*
 * Delete a surveys results
 */

@Path("/surveyResults/{sId}")
public class SurveyResults extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(SurveyResults.class);
		return s;
	}

	

		@DELETE
		public Response deleteSurveyResults(@Context HttpServletRequest request,
				@PathParam("sId") String sId) { 
			
			Response response = null;
			
			try {
			    Class.forName("org.postgresql.Driver");	 
			} catch (ClassNotFoundException e) {
			    System.out.println("Survey: Error: Can't find PostgreSQL JDBC Driver");
			    e.printStackTrace();
			    return Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
			}
			
			// Authorisation - Access
			Connection connectionSD = SDDataSource.getConnection("surveyKPI-SurveyResults");
			a.isAuthorised(connectionSD, request.getRemoteUser());
			// End Authorisation
			
			ArrayList<String> tables = new ArrayList<String> ();
			// Escape any quotes
			if(sId != null) {
				sId = sId.replace("'", "''"); 
	
				String sql = null;				
				Connection connectionRel = null; 
				PreparedStatement pstmt = null;
				try {
					connectionRel = ResultsDataSource.getConnection("surveyKPI-SurveyResults");

					// Delete tables associated with this survey
					
					sql = "SELECT DISTINCT f.table_name FROM form f " +
							"WHERE f.s_id = " + sId + " " +
							"ORDER BY f.table_name;";						
				
					System.out.println("Delete get tables: " + sql);
					pstmt = connectionSD.prepareStatement(sql);	 			
					ResultSet resultSet = pstmt.executeQuery();
						
					while (resultSet.next()) {					
						tables.add(resultSet.getString(1));
					}
					resultSet.close();
					
					for (int i = 0; i < tables.size(); i++) {		
						
						String tableName = tables.get(i);					

						sql = "TRUNCATE TABLE " + tableName + ";";
						System.out.println("Delete truncates: " + sql);
						Statement stmtRel = connectionRel.createStatement();
						stmtRel.executeUpdate(sql);
						System.out.println("Deleted"); 
					}
					
					System.out.println("Building response");
					response = Response.ok("").build();
					
				} catch (SQLException e) {
				    System.out.println("Survey: Connection Failed! Check output console");
				    e.printStackTrace();
				    response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				} finally {
					try {
						if (pstmt != null) {
							pstmt.close();
						}
					} catch (SQLException e) {
					
					}
					System.out.println("closed pstmt");
					try {
						if (connectionSD != null) {
							connectionSD.close();
							connectionSD = null;
						}
					} catch (SQLException e) {
						System.out.println("Survey: Failed to close connection");
					    e.printStackTrace();
					}
					System.out.println("closed sd");
					try {
						if (connectionRel != null) {
							connectionRel.close();
							connectionRel = null;
						}
					} catch (SQLException e) {
						System.out.println("Survey: Failed to close connection");
					    e.printStackTrace();
					}
					System.out.println("closed rel");
				}
			}

			return response; 
		}
}

