package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Geometry where the coordinates are an array of strings
 */
public class Polygon extends Geometry{
	public ArrayList<ArrayList<ArrayList<Double>>> coordinates;
}
