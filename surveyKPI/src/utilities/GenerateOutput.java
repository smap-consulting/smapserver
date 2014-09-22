package utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import surveyKPI.Dashboard;

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

public class GenerateOutput {
	
	private static Logger log =
			 Logger.getLogger(GenerateOutput.class.getName());
	
	private HashMap <String, HashMap<String, ResultsRecord>> results;
	HashMap<String, String> allFeatures;

	public GenerateOutput(HashMap <String, HashMap<String,ResultsRecord>> v) {
		results = v;
		allFeatures = new HashMap<String, String> ();
		getAllFeatures();
		
		Iterator<String> itr = allFeatures.keySet().iterator();
		log.info("Start: ============>");
		while(itr.hasNext()) {
			log.info("Feature:" + itr.next().toString());
		}
		log.info("End: ============>");	
	}
	
	/*
	 * Get the results as geoJSON
	 */
	public JSONArray getGeoJSON() throws JSONException {
		
		JSONArray ja = new JSONArray();	

		Iterator<String> itr = results.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			HashMap<String,ResultsRecord> rList = results.get(k);
			
			JSONArray featuresArray = new JSONArray();
			Iterator<String> itr2 = rList.keySet().iterator();
			while(itr2.hasNext()) {
				String geoString = itr2.next().toString();
				ResultsRecord r = rList.get(geoString);	
				
				JSONObject featureObject = new JSONObject();
				
				JSONObject properties = r.getItemsJSON(allFeatures);
				JSONObject theGeom = r.getTheGeom();
				String theGeomName = r.getTheGeomName();

				featureObject.put("type", "feature");
				featureObject.put("properties", properties);
				if(theGeom != null && !theGeom.equals("None")) {
					featureObject.put("geometry", theGeom);
				}
				if(theGeomName != null) {
					featureObject.put("geomName", theGeomName);
				}
				
				
				featuresArray.put(featureObject);
			}
								
			JSONObject jGeoJSON = new JSONObject();
			jGeoJSON.put("type", "FeatureCollection");
			jGeoJSON.put("features", featuresArray);
			
			JSONObject jo = new JSONObject();
			jo.put(k, jGeoJSON);
			ja.put(jo);
			
		}
			
		return ja;
	}
	
	/*
	 * Get the complete list of features
	 * This is used to generate empty values when an individual record does not have a feature
	 */
	private void getAllFeatures() {
		
		Iterator<String> itr = results.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			HashMap<String,ResultsRecord> rList = results.get(k);
			
			Iterator<String> itr2 = rList.keySet().iterator();
			while(itr2.hasNext()) {
				String geoString = itr2.next().toString();
				ResultsRecord r = rList.get(geoString);
				
				r.addItems(allFeatures);	// Add the features in the record to the complete list of features
				
			}
		}
	}
}
