package org.smap.sdal.model;

public class Column {
	public int qId;
	public String question_name;
	public String option_name;
	public String name;
	public String qType;
	public boolean ro;
	public String humanName;
	
	public boolean isGeometry () {
		boolean geom = false;
		if(qType.equals("geopoint") || qType.equals("geopolygon") || qType.equals("geolinestring") || qType.equals("geotrace")) {
			geom = true;
		}
		return geom;
	}
}
