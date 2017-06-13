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

/*
 * Return the available regions
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
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/region/{region}")
public class Region extends Application {
	
	private static Logger log =
			 Logger.getLogger(Region.class.getName());
	
	// JSON
	@GET
	@Produces("application/json")
	public String getRegion(@Context HttpServletRequest request,
			@PathParam("region") String region) { 
		
		if(region != null) {
			region = region.replace("'", "''"); 
		} 
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Region");
		ArrayList<String> authorisations = new ArrayList<String> ();
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		Authorise a = new Authorise(authorisations, null);
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		JSONObject jGeoJSON = new JSONObject();
		
		Connection connection = null; 
		PreparedStatement pstmt = null;
		String tableName = null;
		
		try {
			String sql = null;
			
			// Get the table
			sql = "SELECT r.table_name FROM regions r, users u " +
					" WHERE u.ident = ? " +
					" AND u.o_id = r.o_id " +
					" AND r.region_name = ?;";
		
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, request.getRemoteUser());
			pstmt.setString(2, region);
			ResultSet resultSet = pstmt.executeQuery();
			if (resultSet.next()) {
				tableName = resultSet.getString(1);
			}
			
			connection = ResultsDataSource.getConnection("surveyKPI-Region");
			
			sql = "select name, ST_AsGeoJSON(the_geom) " +
					"from " + tableName + ";"; 
		
			log.info("SQL: " + sql + " : " + region);
			
			if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
			pstmt = connection.prepareStatement(sql);	 			
			resultSet = pstmt.executeQuery();

			JSONArray featuresArray = new JSONArray();
			while (resultSet.next()) {	
				
				JSONObject featureObject = new JSONObject();
				featureObject.put("type", "feature");
				
				JSONObject properties = new JSONObject();
				properties.put("name", resultSet.getString(1));
				featureObject.put("properties", properties);
				JSONObject jg = new JSONObject(resultSet.getString(2));
				featureObject.put("geometry", jg);
				
				featuresArray.put(featureObject);
			} 
			

			jGeoJSON.put("type", "FeatureCollection");
			jGeoJSON.put("features", featuresArray);

			
		} catch (SQLException e) {
			
			log.log(Level.SEVERE, "SQL Error", e);
			
		} catch (JSONException e) {
			
			log.log(Level.SEVERE, "JSON error", e);
			
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Region", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-Region", connection);
		}

		return jGeoJSON.toString();
	}

}

