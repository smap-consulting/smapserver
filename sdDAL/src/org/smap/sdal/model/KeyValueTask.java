package org.smap.sdal.model;

/*
 * There are too many key value classes!
 * This one is used specifically for task addresses
 */
public class KeyValueTask {
	String name;
	String value;

	public KeyValueTask(String k, String v) {
		name = k;
		value = v;
	}
}
