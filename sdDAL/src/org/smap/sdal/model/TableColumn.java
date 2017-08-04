package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * TableColumn class used to manage data shown in a table
 */
public class TableColumn {
	public String name;
	public int qId;
	public String question_name;
	public String option_name;
	public String humanName;
	public boolean include;		// Include in the table
	public boolean hide;		// Only show on expand
	public boolean barcode;		// Show as barcode
	public boolean mgmt = false;
	public boolean filter = false;
	public String filterValue;
	public String hxlCode;
	public boolean isCondition = false;	// For a calculate sql is created from an array of conditions
	public String startName = null;
	public String endName = null;
	public int l_id;
	
	// Manage updating of data
	public boolean readonly;	// Can't be modified by form management
	public String type;			// text || select_one || date || calculate
	public String chart_type;
	public int width;
	public ArrayList<KeyValue> choices;			// If type is select_one
	public ArrayList<Action> actions;			// Actions to take when the column changes
	public ArrayList<TableColumnMarkup> markup;	// Specify how to present the data
	
	// Manage extraction of data
	public SqlFrag calculation = null;
	
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
	
	public String getGeomType () {
		String geomType = null;
		if(type.equals("geopoint")) { 
			geomType = "Point"; 
		} else if(type.equals("geopolygon") || type.equals("geoshape")) {
			geomType = "Polygon";
		} else if(type.equals("geolinestring") || type.equals("geotrace")) {
			geomType = "Linestring";	
		} 
		return geomType;
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
			selName = "ST_AsGeoJson(the_geom) ";
		} else if(isCalculate() && calculation != null) {
			selName = calculation.sql.toString();
		} else if(type.equals("duration")) {
			if(startName != null && endName != null) {
				selName = "extract(epoch FROM (" + endName + " - " + startName + ")) as "+ name;
			}
		} else {
			selName = name;
		}
		
		// open and close curly brackets are used to delimit quotes when these should not be used to identify a parameter
		// For example integer + '7'  for adding 7 days to a date
		if(selName != null) {
			selName = selName.replace('{', '\'');
			selName = selName.replace('}', '\'');
		}
		return selName;
	}
}
