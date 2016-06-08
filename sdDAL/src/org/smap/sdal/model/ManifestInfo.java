package org.smap.sdal.model;

/*
 * Group Class
 * Used for survey editing
 */
public class ManifestInfo {
	public String manifest;				// Deprecated - only contains filenames and not, column name info
	public String filename;
	public String filepath;
	public boolean changed = false;
}
