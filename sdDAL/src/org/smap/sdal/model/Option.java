package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Options Class
 * Used for survey editing
 */
public class Option {
	public int id;
	public String value;
	public ArrayList<Label> labels = new ArrayList<Label> ();
	public String text_id;
}
