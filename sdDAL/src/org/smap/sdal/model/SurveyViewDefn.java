package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains data used in a survey view
 */
public class SurveyViewDefn {
	public int viewId;
	public int sId;
	public int managedId;
	public int queryId;
	public ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();		// Table
	public ArrayList<MapLayer> layers = new ArrayList<MapLayer> ();             // Map
	public ArrayList<ChartDefn> charts = new ArrayList<ChartDefn> ();           // Charts
	
	public SurveyViewDefn(int v, int s, int m, int q) {
		viewId = v;
		sId = s;
		managedId = m;
		queryId = q;
	}
}
