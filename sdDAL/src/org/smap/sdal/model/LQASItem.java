package org.smap.sdal.model;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASItem {
	public String ident;
	public String desc;
	public String correctRespValue;
	public String correctRespText;
	public String col_name;	
	
	public LQASItem(String ident, String desc, String correctRespValue, String correctRespText, String colName) {
		this.ident = ident;
		this.desc = desc;
		this.col_name = colName;
		this.correctRespValue = correctRespValue;
		this.correctRespText = correctRespText;
	}
}
