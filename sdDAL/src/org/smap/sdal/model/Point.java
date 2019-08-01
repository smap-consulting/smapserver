package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;

/*
 * Geometry where the coordinates are an array of strings
 */
public class Point extends Geometry {
	private static Logger log = Logger.getLogger(GeneralUtilityMethods.class.getName());
	
	public ArrayList<Double> coordinates;
	public Double altitude = 0.0;
	public Double accuracy = 0.0;
	
	public Point() {
		type = "POINT";
	}
	public Point(Double lon, Double lat) {
		type = "POINT";
		coordinates = new ArrayList<Double> ();
		coordinates.add(lon);
		coordinates.add(lat);
	}
	
	public String getAsText() {
		StringBuffer out = new StringBuffer("POINT(");
		out.append(String.valueOf(coordinates.get(0))).append(" ").append(coordinates.get(1));
		out.append(")");
		return out.toString();
	}
	
	public String getAsOdk() {
		String value = "";
		if(coordinates != null && coordinates.size() > 1) {
			StringBuffer vBuf = new StringBuffer("");
			vBuf.append(coordinates.get(1)).append(" ").append(coordinates.get(0));
			vBuf.append(" ").append(altitude);
			vBuf.append(" ").append(accuracy);
			value = vBuf.toString();
		} else {
			log.severe("Invalid value for geopoint");
		}	
		return value;
	}
	
}
