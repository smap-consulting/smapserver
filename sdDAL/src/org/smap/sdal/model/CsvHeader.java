package org.smap.sdal.model;

public class CsvHeader {
	public String fName;			// Name in file
	public String tName;			// Name in table
	public CsvHeader(String f, String t) {
		fName = f;
		tName = t;
	}
}
