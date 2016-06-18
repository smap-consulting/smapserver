package org.smap.sdal.model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Filter {
	public String qType;			// integer ||
	public boolean range = false;	// Set true if filter is a range
	public boolean search =false;	// Set true if searching
	public Timestamp tMin;		// For type date???
	public Timestamp tMax;		// For type date
	
	public int iMin;				// for integer type
	public int iMax;				// for integer type
	public ArrayList<Integer> iValues;	// for integer type
	
	ArrayList<String> values;		// Selectable values where there are less than a preset limit of valid values
}
