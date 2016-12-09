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
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
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
 * Get the values of the passed in questions and the history of changes
 * Only return non null values or null values which have a history of changes
 */

@Path("/reviewresults/{sId}/{qId}")
public class ReviewResultsText extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ReviewResultsText.class.getName());
	
	private class Result {
		int r_id;
		String text;
		String history;
		String action;
	}
	
	private ArrayList<Result> results = new ArrayList<Result> ();

	@GET
	@Produces("application/json")
	public Response getResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Survey Id
			@PathParam("qId") int qId				// Question Id
			) { 
	
		Response response = null;
		String table = null;
		String name = null;
		PreparedStatement  pstmt = null;

		Connection dConnection = null;
				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Results");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
					
		try {
			dConnection = ResultsDataSource.getConnection("surveyKPI-ReviewResultsText");

			/*
			 * Get the table name and column name containing the text data
			 */
			String sql = "select f.table_name, q.qname from form f, question q " +
					" where f.f_id = q.f_id" +
					" and q.q_id = ?";
	
			log.info(sql + " : " + qId);
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setInt(1, qId);
			ResultSet resultSet = pstmt.executeQuery();

			if(resultSet.next()) {
				table = resultSet.getString(1);
				name = resultSet.getString(2);
				
				if (pstmt != null) {
					pstmt.close();
				}
				
				/*
				 * Get the data
				 */
				sql = "select prikey, t." + name + ",ch.value from " + table + " t" +
						" left outer join change_history ch " +
						" on t.prikey = ch.r_id and ch.s_id = ? and ch.q_id = ? " +
						" where t." + name + " is not null or ch.value is not null" +
						" and t._bad = 'false';" ;
				
				pstmt = dConnection.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				pstmt.setInt(2, qId);
				resultSet = pstmt.executeQuery();
				
				while(resultSet.next()) {
					
					Result r = new Result();
					r.r_id = resultSet.getInt(1);
					r.text = resultSet.getString(2);
					r.history = resultSet.getString(3);
					
					results.add(r);
				}
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(results);
			response = Response.ok(resp).build();

		} catch (SQLException e) {
		    log.info("Message=" + e.getMessage());
		    String msg = e.getMessage();		
			response = Response.status(Status.NOT_FOUND).entity(msg).build();

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Results", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-ReviewResultsText", dConnection);
		}

		return response;

	}

}

