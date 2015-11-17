package org.smap.sdal.model;


public class Language {
	public String name;					// Language Name
	public boolean isDefault = false;			// Set true if this is the default language
	
	public Language(String n, boolean id) {
		name = n;
		isDefault = id;
	}
	
}
