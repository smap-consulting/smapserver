package org.smap.sdal.model;

/*
 * Group Class
 * Used for survey editing
 */
public class ManifestInfo {
	public String manifest;				// Deprecated - only contains filenames and not, column name info
	public String manifestParams;		// New storage for survey level manifest data
	public String filename;
	public String filepath;
	public boolean changed = false;
}
