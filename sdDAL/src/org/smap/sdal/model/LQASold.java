package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASold {
	public String lot;				// Name of question that identified the "lot" to sample, create worksheet for each lot
	public ArrayList<LQASdataItemOld> dataItems = new ArrayList<LQASdataItemOld> ();
	public ArrayList<LQASGroup> groups = new ArrayList<LQASGroup> ();

	public LQASold(String n) {
		lot = n;
	}
}
