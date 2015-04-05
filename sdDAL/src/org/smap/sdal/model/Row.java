package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * A row of display items to be added to a document
 */
public class Row {
	public ArrayList<DisplayItem> items = new ArrayList<DisplayItem> ();
	//public int numberQuestions;		// Number of questions in the row
	public int groupWidth;
}
