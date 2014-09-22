package utilities;

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

import java.util.HashMap;
import java.util.Iterator;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class ResultsRecord {
	private JSONObject theGeom;
	private String theGeomName;
	private int totalCounts;	// The total number of counts of options for this record
	private HashMap <String, String> items = new HashMap<String,String> ();
	
	public ResultsRecord() {	
	}
	
	/*
	 * Getters
	 */
	public HashMap <String, String> getItems() {
		return items;
	}
	
	public JSONObject getTheGeom() {
		return theGeom;
	}
	
	public String getTheGeomName() {
		return theGeomName;
	}
	
	/*
	 * Setters
	 */
	
	public void setTotalCounts(int v) {
		totalCounts = v;
	}
	
	public void setTheGeom(JSONObject v) {
		theGeom = v;
	}
	
	public void setTheGeomName(String v) {
		theGeomName = v;
	}
	
	public void addItem(String k, String v) {
		items.put(k, v);
	}
	
	/*
	 * Utility
	 */
	public String toString() {
		String output = null;
		
		output = "Total(" + totalCounts + ")";
		Iterator<String> itr = items.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			String v = items.get(k);
			output += "[" + k + "," + v + "]";
		}
		
		return output;
	}
	
	/*
	 * Get the items in the record as a JSON object
	 */
	public JSONObject getItemsJSON() throws JSONException {
		JSONObject output = new JSONObject();
		
		Iterator<String> itr = items.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			String v = items.get(k);
			output.put(k,v);
		}
		
		return output;
	}
	
	/*
	 * Get the items in the record that are also in the supplied Hashmap
	 */
	public JSONObject getItemsJSON(HashMap<String, String> target) throws JSONException {
		JSONObject output = new JSONObject();
		
		Iterator<String> itr = target.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			String v = items.get(k);
			if(v == null) {
				v = "";
			}
			output.put(k,v);
		}
		
		return output;
	}
	
	/*
	 * Add any items in this record to the supplied HashMap
	 */
	public void addItems(HashMap<String, String> target) {
		
		Iterator<String> itr = items.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();

			target.put(k,"");
		}
		
	}
	
}
