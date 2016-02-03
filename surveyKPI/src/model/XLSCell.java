package model;

/*
 * Contains attributes of a cell for XLS 
 */

public abstract class XLSCell {
	String value = null;
	int colNum;
	int colWidth;
	
	public XLSCell(String value, int colNum, int colWidth) {
		this.value = value;
		this.colNum = colNum;
		this.colWidth = colWidth;
	}
}
