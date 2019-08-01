package model;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import koboToolboxApi.TaskStatistics;

/*
 * Smap extension
 */
public class StatsResultsC3 {
	
	private static Logger log =
			 Logger.getLogger(TaskStatistics.class.getName());
	
	private ArrayList<ArrayList<Integer>> data;
	public ArrayList<String> groups;
	public ArrayList<String> x;
	public String xFormat;

	public StatsResultsC3() {
		groups = new ArrayList<String> ();
		x = new ArrayList<String> ();
		data = new ArrayList<ArrayList<Integer>> ();
	}
	
	/*
	 * Set all the groups
	 */
	public void setGroups(String group) {
		
		boolean groupExists = false;
		
		for(int i = 0; i < groups.size(); i++) {
			if(groups.get(i).equals(group)) {
				groupExists = true;
			}
		}
		if(!groupExists) {
			groups.add(group);
			data.add(new ArrayList<Integer> ());
		}
	}
	
	/*
	 * Add a data value
	 */
	public void add(String xValue, String group, int value) {
		
		int xIndex = -1;
		int colIndex = -1;
		
		// 1. Get the group index
		for(int i = 0; i < groups.size(); i++) {
			if(groups.get(i).equals(group)) {
				colIndex = i;
				break;
			}
		}
		
		// 2. Get the x interval index
		for(int i = x.size() - 1; i >= 0; i--) {
			if(x.get(i).equals(xValue)) {
				xIndex = i;
				break;
			}
		}
		if(xIndex < 0) {
			x.add(xValue);
			xIndex = x.size() - 1;
			
			// New xValue  initialise the values for this xvalue
			for(int i = 0; i < groups.size(); i++) {
				data.get(i).add(0);	
			}
		}
		
		// 3. Add the data
		data.get(colIndex).set(xIndex, value);
	}
	
}
