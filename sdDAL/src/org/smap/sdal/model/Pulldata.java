package org.smap.sdal.model;

public class Pulldata {
	public String survey;		// Survey ident containing the data to be pulled
	public String data_key;
	
	public Pulldata(String s, String k) {
		survey = s;
		data_key = k;
	}
}
