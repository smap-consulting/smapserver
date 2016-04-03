package org.smap.sdal.model;

import com.google.gson.JsonElement;

public class TaskFeature {
	public String type;
	public JsonElement geometry; 
	public TaskProperties properties;
	
	public TaskFeature() {
		type = "Feature";
	}
}
