package org.smap.sdal.model;

public class OptionDesc {
	public String value;
	public String label;
	public int num_value;		// Stata only accepts numeric values
	public boolean replace;		// Set true if the existing non numeric value needs to be replaced with the numeric one
}
