package org.smap.sdal.model;

import java.util.ArrayList;

public class SqlDesc {
	//public ArrayList<String> tables = new ArrayList<String>();
	public ArrayList<ColDesc> colNames = new ArrayList<ColDesc>();
	public String target_table;		// Table selected as target of export along with its parents
	public String cols;
	public int numberFields = 0;	// Shapefile is limited to 255 - make sure there is space for the geometry
	public String geometry_type = null;
	public String sql;
	public boolean gotPriKey = false;
	public ArrayList<String> availableColumns = new ArrayList<String>();	// The subset of required columns that are available due to RBAC etc
}
