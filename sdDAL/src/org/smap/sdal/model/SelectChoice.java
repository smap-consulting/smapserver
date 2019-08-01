package org.smap.sdal.model;

public class SelectChoice {
	String labelInnerText;
	String value;
	int index;
	
	public SelectChoice(String v, String l, int idx) {
		labelInnerText = l;
		value = v;
		index = idx;
	}
	
}
