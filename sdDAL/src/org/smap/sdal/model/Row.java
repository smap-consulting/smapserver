package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * A row of display items to be added to a document
 */
public class Row {
	public ArrayList<DisplayItem> items = new ArrayList<DisplayItem> ();
	public int groupWidth;
	public int spaceBefore() {
		int space = 0;
		for(DisplayItem di : items) {
			if(di.space > space) {
				space = di.space;
			}
		}
		return space;
	}
}
