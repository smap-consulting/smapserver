package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * TableColumn class used to manage data shown in a table
 */
public class SurveyViewDefn {
	int sId;
	int managedId;
	int queryId;
	public ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();		// Table
	public ArrayList<MapLayer> layers = new ArrayList<MapLayer> ();             // Map
	// Charts
}
