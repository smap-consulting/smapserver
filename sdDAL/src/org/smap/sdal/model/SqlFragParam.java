package org.smap.sdal.model;

public class SqlFragParam {
	public String type;		// text || sql || integer || double
	public String sValue;		// text || sql
	public int iValue;			// integer
	public double dValue;		// double
	
	void addTextParam(String v) {
		type = "text";
		sValue = v;
	}
	
	String debug() {
		if(type.equals("text") || type.equals("sql")) {
			return type + " : " + sValue;
		} else {
			return "";
		}
	}
}
