package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.smap.sdal.Utilities.GeneralUtilityMethods;

/*
 * TableColumn class used to manage data shown in a table
 * You might will ask how this is different from a question?
 *  Original this new class was created because a select multiple question expanded to multiple columns
 *  Secondly it includes support for managed forms
 */
public class TableColumn {
	public String column_name;
	public int qId;
	public String question_name;
	public String option_name;
	public String humanName;		// Legacy usage but included in reports saved to database - Do not use
	public String name;			// Legacy usage but included in reports saved to database - Do not use
	public String displayName;
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
	public boolean compressed = false;
	public boolean isMeta = false;
	public boolean selectDisplayNames = false;
	
	// Manage updating of data
	public boolean readonly;	// Can't be modified by form management
	public String type;			// text || select_one || date || calculate
	public String chart_type;
	public int width;
	public ArrayList<KeyValue> choices;			// If type is select or select_one
	public ArrayList<Action> actions;			// Actions to take when the column changes
	public ArrayList<TableColumnMarkup> markup;	// Specify how to present the data
	public HashMap<String, String> parameters;
	
	// Manage extraction of data
	public SqlFrag calculation = null;
	
	public TableColumn(String column_name, String question_name, String displayName) {
		this.column_name = column_name;
		this.question_name = question_name;
		this.displayName = displayName;
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
		return GeneralUtilityMethods.isAttachmentType(type);
	}
	
	/*
	 * Get the sql to select this column from the database
	 */
	public String getSqlSelect(String urlprefix, String tz, ArrayList<SqlParam> params) {
		String selName = null;
		
		if(isAttachment()) {
			selName = "'" + urlprefix + "' || " + column_name + " as " + column_name;
		} else if(isGeometry()) {
			selName = "ST_AsGeoJson(the_geom) ";
		} else if(isCalculate() && calculation != null) {
			selName = calculation.sql.toString();
		} else if(type.equals("duration")) {
			if(startName != null && endName != null) {
				selName = "extract(epoch FROM (" + endName + " - " + startName + ")) as "+ column_name;
			}
		} else if(type.equals("duration")) {
			if(startName != null && endName != null) {
				selName = "extract(epoch FROM (" + endName + " - " + startName + ")) as "+ column_name;
			}
		} else if(!tz.equals("UTC") && (type.equals("dateTime") || type.equals("date"))) {
			selName = "to_char(timezone(?, " + column_name;
			params.add(new SqlParam("string", tz));
			if(type.equals("date")) {
				selName += "), 'YYYY-MM-DD') as ";
			} else if(type.equals("dateTime")) {
				selName += "), 'YYYY-MM-DD HH24:MI:SS') as ";
			} 
			selName += column_name;
		} else {
			selName = column_name;
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
