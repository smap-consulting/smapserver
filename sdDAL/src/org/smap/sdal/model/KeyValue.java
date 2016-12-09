package org.smap.sdal.model;

public class KeyValue {
	public String k;
	public String v;
	public boolean isRole = false;
	
	public KeyValue(String key, String value) {
		k = key;
		v = value;
	}
	
	public KeyValue(String key, String value, boolean r) {
		k = key;
		v = value;
		isRole = r;
	}
}
