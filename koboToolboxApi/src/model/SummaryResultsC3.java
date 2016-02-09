package model;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

/*
 * Smap extension
 */
public class SummaryResultsC3 {
	
	private ArrayList<ArrayList<Integer>> data;
	public ArrayList<String> columns;
	public ArrayList<String> x;

	public SummaryResultsC3() {
		columns = new ArrayList<String> ();
		x = new ArrayList<String> ();
		data = new ArrayList<ArrayList<Integer>> ();
	}
	
	public void add(String xValue, String column, int value) {
		
		int xIndex = -1;
		int colIndex = -1;
		
		// 1. Get the column index
		for(int i = 0; i < columns.size(); i++) {
			if(columns.get(i).equals(column)) {
				colIndex = i;
				break;
			}
		}
		if(colIndex < 0) {
			columns.add(column);
			data.add(new ArrayList<Integer> ());
			colIndex = columns.size() - 1;
		}
		
		// 2. Get the data index
		for(int i = x.size() - 1; i >= 0; i--) {
			if(x.get(i).equals(xValue)) {
				xIndex = i;
				break;
			}
		}
		if(xIndex < 0) {
			x.add(xValue);
		}
		
		// 3. Add the data
		data.get(colIndex).add(value);
	}
}
