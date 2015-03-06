package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Options Class
 * Used for survey editing
 */
public class Option {
	public int id;
	public int seq;
	public String value;
	public String defLabel;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	//public ArrayList<Label> labels_orig = new ArrayList<Label> ();
	public String text_id;
}
