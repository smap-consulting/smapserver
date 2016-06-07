package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Group Class
 * Used for survey editing
 */
public class ManifestParams {
	public String type;						// csv || linked
	public String name;						// File or survey name
	public ArrayList<String> params;		// New storage for survey level manifest data
}
