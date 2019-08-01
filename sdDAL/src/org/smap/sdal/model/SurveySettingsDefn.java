package org.smap.sdal.model;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * Contains data used in a survey view
 */
public class SurveySettingsDefn {
	
	public int limit;
	public String filter;
	public String dateName;
	public Date fromDate;
	public Date toDate;
	public String overridenDefaultLimit;
	
	public ArrayList<MapLayer> layers = new ArrayList<MapLayer> ();             // Map
	public ArrayList<ChartDefn> charts = new ArrayList<ChartDefn> ();           // Charts

	
}
