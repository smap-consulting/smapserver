package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

public class ReadData {
	
	public ArrayList<String> values = new ArrayList<> ();
	public String type;
	public String name;
	
	public boolean isTransform = false;
	
	public ReadData(String name, boolean isTransform, String type) {
		this.name = name;
		this.isTransform = isTransform;
		this.type = type;
	}
}
