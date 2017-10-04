package org.smap.sdal.model;

public class ImageLabelMessage {
	public String imagePath;
	public String tableName;
	public String colName;			// Column that will store the labels
	public String colType;			// Label or Text (if label data will be stored as JSON)
	
	public ImageLabelMessage(String imagePath, String tableName, String colName, String colType) {
		this.imagePath = imagePath;
		this.tableName = tableName;
		this.colName = colName;
		this.colType = colType;
	}
}
