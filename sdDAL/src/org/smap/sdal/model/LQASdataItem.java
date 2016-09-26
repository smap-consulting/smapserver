package org.smap.sdal.model;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASdataItem {
	public String ident;
	public SqlFrag select;
	//public String [] sourceColumns;
	public boolean isString;
	
	public LQASdataItem(String ident, SqlFrag select, 
			boolean isString) {
		this.ident = ident;
		this.select = select;
		//this.sourceColumns = sources;
		this.isString = isString;
	}
}
