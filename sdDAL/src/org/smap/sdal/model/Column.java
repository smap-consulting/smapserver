package org.smap.sdal.model;

public class Column {
	public int qId;
	public String question_name;
	public String option_name;
	public String name;				// Column name in database
	public String qType;
	public boolean ro;
	public String humanName;
	public String calculation;		// Derived values - used with managed forms

	public boolean isGeometry () {
		boolean geom = false;
		if(qType.equals("geopoint") || qType.equals("geopolygon") || qType.equals("geolinestring") 
				|| qType.equals("geoshape") || qType.equals("geotrace")) {
			geom = true;
		}
		return geom;
	}
	
	public boolean isCalculate() {
		boolean isCalculate = false;
		if(qType.equals("calculate")) {
			isCalculate = true;
		}
		return isCalculate;
	}
	
	public boolean isAttachment() {
		boolean isAttachment = false;
		if(qType.equals("image") || qType.equals("audio") || qType.equals("video")) {
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
		} else if(isCalculate()) {
			selName = calculation;
		} else {
			selName = name;
		}
		return selName;
	}
	
}
