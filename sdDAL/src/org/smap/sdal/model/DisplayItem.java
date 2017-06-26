package org.smap.sdal.model;

import java.util.ArrayList;

import com.itextpdf.text.BaseColor;


public class DisplayItem {

	public int width;			// Width of entire cell item relative to enclosing item
	public int widthLabel = 5;	// Percentage width of label (If label is full width value appears below)
	public int space = 0;			// Space to be added before this item
	public String value;
	public double valueHeight = -1.0;
	public String name;
	public String hint;
	public String text;
	public String type;
	public boolean isSet = false;
	public boolean isNewPage = false;
	public ArrayList<DisplayItem> choices = null;
	public BaseColor labelbg;
	public BaseColor valuebg;
	public boolean labelcaps = false;
	public boolean labelbold = false;
	public int fIdx = 0;
	public int rec_number = 0;
	public String map;
	public String location;
	public String zoom;					// Map zoom
	public boolean isBarcode = false;
	public boolean isHyperlink = false;
	
	public void debug() {
		System.out.println("======== Display Item:   width: " + width + "   value: " + value + " text: " + text + " : " + type  );
	}
}
