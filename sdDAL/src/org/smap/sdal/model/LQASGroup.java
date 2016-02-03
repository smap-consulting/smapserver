package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASGroup {
	public String ident;	
	public ArrayList<LQASItem> items = new ArrayList<LQASItem> ();

	public LQASGroup(String ident) {
		this.ident = ident;
	}
}
