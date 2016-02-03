package org.smap.sdal.model;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASItem {
	public String ident;
	public String col_name;	
	
	public LQASItem(String ident, String colName) {
		this.ident = ident;
		this.col_name = colName;
	}
}
