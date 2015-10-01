package org.smap.sdal.model;

import java.util.ArrayList;

import com.itextpdf.text.BaseColor;


public class DisplayItem {

	public int width;
	public String value;
	public String name;
	public String hint;
	public String text;
	public String type;
	public boolean isSet = false;
	public ArrayList<DisplayItem> choices = null;
	public BaseColor labelbg;
	
	public void debug() {
		System.out.println("======== Display Item:   width: " + width + "   value: " + value + " text: " + text + " : " + type  );
	}
}
