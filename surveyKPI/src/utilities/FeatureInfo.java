package utilities;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class FeatureInfo {
	public JSONArray featureItems;
	public JSONObject featureProps;
	public int index;
	
	private int recordCount;
	private Map<String, Double> totals = new HashMap<String, Double>();
	
	public Map<String, Double> getTotals() {
		return totals;
	}
	
	public void addItem(String key , double value) {
		Double itemTotal = totals.get(key);
		
		if(itemTotal == null) {
			Double newTotal = new Double(value);
			totals.put(key, newTotal);
		} else {
			itemTotal += new Double(value);
			totals.put(key, itemTotal);
		}
		
	}
	
	public void incRecordCount() {
		recordCount++;
	}
	
	public int getRecordCount() {
		return recordCount;
	}
	
	public void printTotals() {
		for (Map.Entry<String, Double> entry : totals.entrySet()) {
		    String key = entry.getKey();
		    Double value = entry.getValue();
		}
	}
	
	public void addTotalsToJSONObject(JSONObject jo, String fn, JSONArray cols, boolean firstTime) {
		for (Map.Entry<String, Double> entry : totals.entrySet()) {
		    String key = entry.getKey();
		    Double value = entry.getValue();
		    if(fn.equals("percent")) {
		    	value = (double) (Math.round(100 * value * 100 / recordCount)) / 100; 
		    } else if(fn.equals("average")) {
		    	value = value / recordCount;
		    	value = (double) (Math.round(value * 100)) / 100;	// round to 2 decimal places
		    } else if(fn.equals("total")) {
		    	// Value is already set to the total
		    	value = (double) (Math.round(value * 100)) / 100;	// round to 2 decimal places
		    }
		    
		    if(firstTime) {		// If this is the first time that a group has been added then set the column names
		    	cols.put(key);
		    }

		    try {
				jo.put(key, value);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}
}
