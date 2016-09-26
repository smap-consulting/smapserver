package org.smap.sdal.model;

import java.util.ArrayList;

import net.sourceforge.jeval.Evaluator;

/*
 * Contains set of changes that need to be applied in a single transaction
 */
public class LQASItem {
	public String ident;
	public String desc;
	public String correctRespValue;
	public String correctRespText;
	public ArrayList<String> sourceColumns;
	
	public LQASItem(String ident, String desc, String correctRespValue, String correctRespText, 
			ArrayList<String> sources) {
		this.ident = ident;
		this.desc = desc;
		this.correctRespValue = correctRespValue;
		this.correctRespText = correctRespText;
		this.sourceColumns = sources;
	}
}
