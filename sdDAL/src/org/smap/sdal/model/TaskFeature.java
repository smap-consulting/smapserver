package org.smap.sdal.model;

import java.util.HashMap;

import com.google.gson.JsonElement;

public class TaskFeature {
	public String type;
	public JsonElement geometry; 
	public TaskProperties properties;
	public HashMap<String, String> links;
	
	public TaskFeature() {
		type = "Feature";
	}
}
