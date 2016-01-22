package org.smap.sdal.model;

public class Column {
	public int qId;
	public String question_name;
	public String option_name;
	public String name;				// Column name in database
	public String qType;
	public boolean ro;
	public String humanName;
	
	public boolean isGeometry () {
		boolean geom = false;
		if(qType.equals("geopoint") || qType.equals("geopolygon") || qType.equals("geolinestring") 
				|| qType.equals("geoshape") || qType.equals("geotrace")) {
			geom = true;
		}
		return geom;
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
		
		System.out.println("Type: " + qType);
		if(isAttachment()) {
			selName = "'" + urlprefix + "' || " + name + " as " + name;
		} else if(isGeometry()) {
			selName = "ST_AsTEXT(" + name + ") ";
		} else {
			selName = name;
		}
		return selName;
	}
	
}
