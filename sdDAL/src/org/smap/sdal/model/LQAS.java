package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQAS {
	public String lot;				// Name of question that identified the "lot" to sample, create worksheet for each lot
	public ArrayList<LQASdataItem> dataItems = new ArrayList<LQASdataItem> ();
	public ArrayList<LQASGroup> groups = new ArrayList<LQASGroup> ();

	public LQAS(String n) {
		lot = n;
	}
}
