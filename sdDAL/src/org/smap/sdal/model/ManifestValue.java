package org.smap.sdal.model;

import java.io.File;

/*
 * Form Class
 * Stores details on a manifes item
 */
public class ManifestValue {
	public String value;
	public String fileName;
	public String url;
	public String thumbsUrl;
	public File file;
	public String filePath;
	public String type;
	public String text_id;
	public int sId;
	public int qId;
	public int oId;
	public int linkedRecords;	// The number of records in a linked form
}
