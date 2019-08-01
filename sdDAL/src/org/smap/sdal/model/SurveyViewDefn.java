package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * Contains data used in a survey view
 */
public class SurveyViewDefn {
	public SurveySettingsDefn ssd;
	public int sId;
	public int managedId;
	public int queryId;
	public HashMap<String, String> parameters = null;
	public HashMap<String, Integer> mainColumnNames = new HashMap<>();
	public ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();		// Table
	public ArrayList<MapLayer> layers = new ArrayList<MapLayer> ();             // Map
	public ArrayList<ChartDefn> charts = new ArrayList<ChartDefn> ();           // Charts
	public ArrayList<ChoiceList> choiceLists;           						// Choices
	public String tableName;
	
	public SurveyViewDefn() {
		
	}
	
	public SurveyViewDefn(SurveySettingsDefn ssd, int s, int m, int q) {
		this.ssd = ssd;
		sId = s;
		managedId = m;
		queryId = q;
	}
}
