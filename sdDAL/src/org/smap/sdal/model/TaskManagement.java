package org.smap.sdal.model;

import java.util.ArrayList;

public class TaskManagement {
	public String type;
	public ArrayList<TaskFeature> features; 
	
	public TaskManagement() {
		type = "FeatureCollection";
		features = new ArrayList<TaskFeature> ();
	}
}
