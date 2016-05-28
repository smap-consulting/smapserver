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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.ResultsDataSource;

import utilities.FeatureInfo;
import utilities.QuestionInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns the data in the project table
 * This is only used for Smap Consulting web site, no authorisation is required
 */
@Path("/project")
public class ProjectSvc extends Application {
	
	private static Logger log =
			 Logger.getLogger(ProjectSvc.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ProjectSvc.class);
		return s;
	}

	// Return projects
	@GET
	@Produces("application/json")
	public String getProject() {
			
		JSONObject results = null;
		
		QuestionInfo date = null;
		QuestionInfo geom = null;
		ArrayList<QuestionInfo> q = new ArrayList<QuestionInfo> ();
		
		
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Error: Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		 
		Connection dConnection = null;
		PreparedStatement pstmt = null;
		try {
			dConnection = ResultsDataSource.getConnection("surveyKPI-ProjectSvc");

			/*
			 * Get the data
			 */			
			String sql = "select smap_project.r_id, smap_project._start, ST_AsGeoJSON(countries.the_geom) as the_geom, countries.name, " +
					" smap_project.title, smap_project.description, smap_project_item.caption, smap_project_item.item" +
					" from smap_project " +
					" inner join countries on smap_project.country = countries.name" +
					" left outer join smap_project_item on smap_project_item.r_id = smap_project.r_id" +
					" order by smap_project._start asc;";
			log.info(sql);
			
			pstmt = dConnection.prepareStatement(sql);	 			
			ResultSet resultSet = pstmt.executeQuery();
			
			HashMap<String, FeatureInfo> featureHash = new HashMap<String, FeatureInfo>();
			
			results = new JSONObject();
			JSONObject featureCollection = new JSONObject();
			JSONArray featuresArray = new JSONArray();
			JSONArray itemArray = new JSONArray();
			
			// Assemble
			results.put("geo", featureCollection);
			results.put("items", itemArray);
			featureCollection.put("type", "FeatureCollection");
			featureCollection.put("features", featuresArray);
			
			FeatureInfo info = null;	// The current feature info
			JSONArray links = null;
			int currentId = -1;
			int featureIndex = -1;
			int itemIndex = -1;
			while(resultSet.next()) {
			
				int projectId = resultSet.getInt(1);
				Date theDate = resultSet.getDate(2);
				String theGeom = resultSet.getString(3);
				String locn = resultSet.getString(4);
				String title = resultSet.getString(5);
				String desc = resultSet.getString(6);
				String item_caption = resultSet.getString(7);
				String item_link = resultSet.getString(8);
				
				// If this is a new geometry create a new feature object
				if(featureHash.get(locn) == null) {
					featureIndex++;
					
					JSONObject feature = new JSONObject();
					JSONObject featureProps = new JSONObject();
					featuresArray.put(feature);
					
					JSONObject jg = new JSONObject(theGeom);
					feature.put("geometry", jg);
					feature.put("properties", featureProps);
					feature.put("type", "feature");
					
					JSONArray featureItems = new JSONArray();
					featureProps.put("items", featureItems);
					featureProps.put("value", 0);	// Value set the colour of the feature	
					featureProps.put("name", locn);
					
					// Create an info object to store details of this location
					info = new FeatureInfo();
					info.featureItems = featureItems;
					info.index = featureIndex;
					featureHash.put(locn, info);

				} else {
					info = featureHash.get(locn);
				}
				
				// If this is a new item then create an item object and the data object to hold the data
				if(currentId != projectId) {
					projectId = currentId;
					itemIndex++;
					
					info.featureItems.put(itemIndex);
					
					JSONObject item = new JSONObject();
					JSONObject data = new JSONObject();
					itemArray.put(item);
					item.put("data", data);
					item.put("fIdx", info.index);
					
					data.put("locn", locn);
					data.put("title", title);
					data.put("desc", desc);
					data.put("date", theDate.getTime());
					
					links = new JSONArray();
					data.put("links", links);
				}
				
				// Add the item data to the data object
				if(item_link != null) {
					JSONObject item = new JSONObject();
					item.put("caption", item_caption);
					item.put("link", item_link);
					links.put(item);
				}
				
			}
									
				
		} catch (SQLException e) {
		    log.log(Level.SEVERE,"SQL Error", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE,"Exception", e);
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			ResultsDataSource.closeConnection("surveyKPI-ProjectSvc", dConnection);
		}


		if(results != null) {
			return results.toString();
		} else {
			// TODO return error
			return "";
		}

	}
	


}

