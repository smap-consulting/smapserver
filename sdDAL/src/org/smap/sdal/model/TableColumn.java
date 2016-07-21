package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * TableColumn class used to manage data shown in a table
 */
public class TableColumn {
	public String name;
	public int qId;
	public String option_name;
	public String humanName;
	public boolean include;		// Include in the table
	public boolean hide;		// Only show on expand
	public boolean barcode;		// Show as barcode
	public boolean mgmt = false;
	public boolean filter = false;
	public String filterValue;
	
	// Manage updating of data
	public boolean readonly;	// Can't be modified by form management
	public String type;			// text || select_one || date || calculate
	public ArrayList<String> choices;			// If type is select_one
	public ArrayList<TableColumnMarkup> markup;	// Specify how to present the data
	
	// Manage extraction of data
	public SqlFrag calculation = null;	// Server only
	
	public TableColumn(String n, String hn) {
		name = n;
		humanName = hn;
		include = true;
		hide = false;
		
		readonly = true;
	}
	public TableColumn() {
		include = true;
		hide = false;
	}
	
	public boolean isGeometry () {
		boolean geom = false;
		if(type.equals("geopoint") || type.equals("geopolygon") || type.equals("geolinestring") 
				|| type.equals("geoshape") || type.equals("geotrace")) {
			geom = true;
		}
		return geom;
	}
	
	public boolean isCalculate() {
		boolean isCalculate = false;
		if(type.equals("calculate")) {
			isCalculate = true;
		}
		return isCalculate;
	}
	
	public boolean isAttachment() {
		boolean isAttachment = false;
		if(type.equals("image") || type.equals("audio") || type.equals("video")) {
			isAttachment = true;
		}
		return isAttachment;
	}
	
	/*
	 * Get the sql to select this column from the database
	 */
	public String getSqlSelect(String urlprefix) {
		String selName = null;
		
		if(isAttachment()) {
			selName = "'" + urlprefix + "' || " + name + " as " + name;
		} else if(isGeometry()) {
			selName = "ST_AsGeoJson(" + name + ") ";
		} else if(isCalculate() && calculation != null) {
			selName = calculation.sql.toString();
		} else {
			selName = name;
		}
		return selName;
	}
}
