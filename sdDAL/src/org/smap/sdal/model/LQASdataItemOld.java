package org.smap.sdal.model;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASdataItemOld {
	public String ident;
	public String select;
	public String [] sourceColumns;
	public boolean isString;
	
	public LQASdataItemOld(String ident, String select, 
			String[] sources, boolean isString) {
		this.ident = ident;
		this.select = select;
		this.sourceColumns = sources;
		this.isString = isString;
	}
}
