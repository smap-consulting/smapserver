package org.smap.sdal.model;

import java.util.ArrayList;


public class DisplayItem {

	public int width;
	public String value;
	public String hint;
	public String text;
	public String type;
	public ArrayList<Result> choices = null;
	
	public void debug() {
		System.out.println("========\n   width: " + width + "\n   value: " + value + "\n     text: " + text + "\n    " + type  );
	}
}
