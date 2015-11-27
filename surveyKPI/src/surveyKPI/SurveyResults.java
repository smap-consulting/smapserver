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
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Delete results for a survey
 */
@Path("/surveyResults/{sId}")
public class SurveyResults extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Results.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(SurveyResults.class);
		return s;
	}

	

		@DELETE
		public Response deleteSurveyResults(@Context HttpServletRequest request,
				@PathParam("sId") int sId) { 
			
			Response response = null;
			
			try {
			    Class.forName("org.postgresql.Driver");	 
			} catch (ClassNotFoundException e) {
				log.log(Level.SEVERE, "Survey: Error: Can't find PostgreSQL JDBC Driver", e);
			    return Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
			}
			
			// Authorisation - Access
			Connection connectionSD = SDDataSource.getConnection("surveyKPI-SurveyResults");
			a.isAuthorised(connectionSD, request.getRemoteUser());
			// End Authorisation
			
			ArrayList<String> tables = new ArrayList<String> ();
			// Escape any quotes
			if(sId > 0) {
	
				String sql = null;				
				Connection connectionRel = null; 
				PreparedStatement pstmt = null;
				PreparedStatement pstmtUnPublish = null;
				PreparedStatement pstmtRemoveChangeHistory = null;
				PreparedStatement pstmtRemoveChangeset = null;
				Statement stmtRel = null;
				try {
					connectionRel = ResultsDataSource.getConnection("surveyKPI-SurveyResults");

					// Delete tables associated with this survey
					
					String sqlUnPublish = "update question set published = 'false' where f_id in (select f_id from form where s_id = ?);";
					pstmtUnPublish = connectionSD.prepareStatement(sqlUnPublish);
					
					String sqlRemoveChangeHistory = "delete from change_history where s_id = ?;";
					pstmtRemoveChangeHistory = connectionRel.prepareStatement(sqlRemoveChangeHistory);
					
					String sqlRemoveChangeset = "delete from changeset where s_id = ?;";
					pstmtRemoveChangeset = connectionRel.prepareStatement(sqlRemoveChangeset);
					
					sql = "select distinct f.table_name, f.f_id FROM form f " +
							"where f.s_id = ? " +
							"order by f.table_name;";						
					pstmt = connectionSD.prepareStatement(sql);	
					pstmt.setInt(1, sId);
					log.info("Get tables for delete: " + pstmt.toString());
					ResultSet resultSet = pstmt.executeQuery();
						
					while (resultSet.next()) {					
						tables.add(resultSet.getString(1));
					}
					
					resultSet.close();
					
					for (int i = 0; i < tables.size(); i++) {	
						
						String tableName = tables.get(i);					

						sql = "drop TABLE " + tableName + ";";
						log.info("Delete table contents and drop table: " + sql);
						
						try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
						stmtRel = connectionRel.createStatement();
						stmtRel.executeUpdate(sql);
						log.info("userevent: " + request.getRemoteUser() + " : delete results : " + tableName + " in survey : "+ sId); 
					}
					
					pstmtUnPublish.setInt(1, sId);
					pstmtUnPublish.executeUpdate();
					
					pstmtRemoveChangeHistory.setInt(1, sId);
					pstmtRemoveChangeHistory.executeUpdate();
					
					pstmtRemoveChangeset.setInt(1, sId);
					pstmtRemoveChangeset.executeUpdate();
					
					response = Response.ok("").build();
					
				} catch (SQLException e) {
					log.log(Level.SEVERE, "Survey: Connection Failed! Check output console");
				    e.printStackTrace();
				    response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				} finally {
					try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
					try {if (pstmtUnPublish != null) {pstmtUnPublish.close();}} catch (SQLException e) {}
					try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
					try {if (pstmtRemoveChangeHistory != null) {pstmtRemoveChangeHistory.close();}} catch (SQLException e) {}

					try {
						if (connectionSD != null) {
							connectionSD.close();
							connectionSD = null;
						}
					} catch (SQLException e) {
						log.log(Level.SEVERE,"Survey: Failed to close connection", e);
					}

					try {
						if (connectionRel != null) {
							connectionRel.close();
							connectionRel = null;
						}
					} catch (SQLException e) {
						log.log(Level.SEVERE, "Survey: Failed to close connection", e);
					}
				}
			}

			return response; 
		}
}

