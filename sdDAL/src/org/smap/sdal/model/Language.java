package org.smap.sdal.model;

public class Language {
	public int id;
	public String name;
	public boolean deleted = false;
	
	public Language () {}
	
	public Language (int id, String name) {
		this.id = id;
		this.name = name;
	}
}
