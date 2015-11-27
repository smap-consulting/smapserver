package utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

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

public class GetData {
	
	private static Logger log =
			 Logger.getLogger(GetData.class.getName());
	
	private LinkedHashMap <String, HashMap<String,ResultsRecord>> results = null;
	
	public GetData() {
		results = new LinkedHashMap<String, HashMap<String,ResultsRecord>>();
	}
	
	/*
	 * 	Has timeseries
	 *  No geometry
	 */
	public LinkedHashMap <String, HashMap<String,ResultsRecord>> getTimeseries(OptionInfo[] o, QuestionInfo value, QuestionInfo date, 
			String start, String end,
			Connection connection) throws SQLException, JSONException {
				
		for(int optionIndex = 0; optionIndex < o.length; optionIndex++) {
			
			String sql = "SELECT count(s." + o[optionIndex].getColumnName() + "), s." + o[optionIndex].getColumnName() + ", " + 
					"dt.day as date_result " + 
					"from  days dt left join " + date.getTableName() + " s " + 
					"on s." + date.getColumnName() + "::date = dt.day " +
					"where dt.day >= '" + start + "' " +
					"and dt.day <= '" + end + "' " +
					"group by date_result, " + o[optionIndex].getColumnName() + " " +
					"order by date_result asc;";
			
			log.fine("Getting data for option:" + o[optionIndex].getColumnName() + " Label:" + o[optionIndex].getIdent());
			log.info(sql);
			PreparedStatement  pstmt = connection.prepareStatement(sql);	 			
			ResultSet resultSet = pstmt.executeQuery();
	
			/*
			 * Get the data
			 */
			String currentDate = "";
			int total = 0;
			int count = 0;
			while(resultSet.next()) {
				String c = resultSet.getString(1);
				String v = resultSet.getString(2);
				String d = resultSet.getString(3);
				log.fine("Date:" + d + " Value:" + v + " Count:" + c);
				
				
				// Process a change of date
				if(!d.equals(currentDate)) {
					if(!currentDate.equals("")) {
						
						ResultsRecord r = getRecord(currentDate, "None");
						r.setTotalCounts(total);
						r.addItem(o[optionIndex].getIdent(), String.valueOf(count));
					}
					
					// Reset variables
					total = 0;
					count = 0;
					currentDate = d;
				}
				
				log.fine("CurrentDate:" + currentDate + " Total:" + total + " Count:" + count);
				
				// Increment the count variables
				if(c != null) {
					total += Integer.parseInt(c);
					if(v!= null && v.equals(o[optionIndex].getTargetValue())) {
						count += Integer.parseInt(c);
					}
				}
	
			}
			
			// Write out the last record
			if(!currentDate.equals("")) {				
				ResultsRecord r = getRecord(currentDate, "None");
				r.setTotalCounts(total);
				r.addItem(o[optionIndex].getIdent(), String.valueOf(count));			
			}
		}
		
		
		return results;
	}
	
	/*
	 * Get the data
	 * 	No timeseries
	 *  No geometry
	 */
	public LinkedHashMap <String, HashMap<String,ResultsRecord>>  get(OptionInfo[] o, QuestionInfo value, 
			Connection connection) throws SQLException, JSONException {
		
		for(int optionIndex = 0; optionIndex < o.length; optionIndex++) {
			
			String sql = "SELECT count(" + o[optionIndex].getColumnName() + ") " +
					"from " + value.getTableName() + " " +
					"where " + o[optionIndex].getColumnName() + " = '" + o[optionIndex].getTargetValue() +"';";
		
			log.info(sql);
			PreparedStatement  pstmt = connection.prepareStatement(sql);	 			
			ResultSet resultSet = pstmt.executeQuery();
	
			/*
			 * Get the data
			 */
			while(resultSet.next()) {
				String count = resultSet.getString(1);
	
				ResultsRecord r = getRecord("None", "None");	
				r.addItem(o[optionIndex].getIdent(), count);		
			}
		}
		
		return results;
	}
	
	/*
	 * 	No timeseries
	 *  Has geometry
	 */
	public LinkedHashMap <String, HashMap<String, ResultsRecord>>  getGeometry(OptionInfo[] o, QuestionInfo value, 
			String geoTable, String geoAgg,
			Connection connection) throws SQLException, JSONException {
		
		for(int optionIndex = 0; optionIndex < o.length; optionIndex++) {
			
			// TODO allow for geo table not being the same as the value table
			String sql = null;
			if(geoAgg.equals("None")) {
				sql = "select count(" + o[optionIndex].getColumnName() + "), " +
					"ST_AsGeoJSON(the_geom) " +
					"from " + value.getTableName() + " " +
					"where " + o[optionIndex].getColumnName() + " = '" + o[optionIndex].getTargetValue() + "' " + 
					"group by the_geom;";
			} else {
				sql = "select count(" + o[optionIndex].getColumnName() + "), " +
						"ST_AsGeoJSON(a.the_geom), " +
						"a.name " +
						"from " + value.getTableName() + " d, " +
						geoAgg + " a " +
						"where d." + o[optionIndex].getColumnName() + " = '" + o[optionIndex].getTargetValue() + "' " + 
						"and ST_Within(d.the_geom, a.the_geom) " +
						"group by a.the_geom, a.name;";
			}
		
			log.info(sql);
			PreparedStatement  pstmt = connection.prepareStatement(sql);	 			
			ResultSet resultSet = pstmt.executeQuery();
	
			/*
			 * Get the data
			 */
			while(resultSet.next()) {
				String count = resultSet.getString(1);
				// If there is no geometry data associated with this result then geom will be null
				// Discard the item if this is the case
				String theGeom = resultSet.getString(2);
				if(theGeom != null) {
					JSONObject jg = new JSONObject(theGeom);
					String geomName = null;
					if(!geoAgg.equals("None")) {
						geomName = resultSet.getString(3);
					}
					
					ResultsRecord r = getRecord("None", jg.toString());	
					r.setTheGeom(jg);
					r.setTheGeomName(geomName);
					r.addItem(o[optionIndex].getIdent(), count);	
				}
	
			}
		}
		
		return results;
	}
	
	/*
	 * The results record are a 2 dimensional arrays keyed on date and location
	 */
	private ResultsRecord getRecord(String dateDimension, String geomDimension) {
		
		/*
		 * Get the record list for this date
		 * If it does not exist then create it
		 */
		HashMap<String, ResultsRecord> rList = results.get(dateDimension);
		if(rList == null) {
			rList = new HashMap<String,ResultsRecord>();
			results.put(dateDimension, rList);
		} 

		/*
		 * Get the record for this geometry 
		 */
		ResultsRecord r = rList.get(geomDimension);
		if(r == null) {
			r = new ResultsRecord();
			rList.put(geomDimension,r);
		} 
		
		return r;
	}
}
