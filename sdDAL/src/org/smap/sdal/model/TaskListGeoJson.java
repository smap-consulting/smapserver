package org.smap.sdal.model;

import java.util.ArrayList;

public class TaskListGeoJson {
	public String type;
	public ArrayList<TaskFeature> features; 
	
	public TaskListGeoJson() {
		type = "FeatureCollection";
		features = new ArrayList<TaskFeature> ();
	}
}
