package model;

/*
 * Contains attributes of a cell for XLS 
 */

public abstract class XLSCell {
	String value = null;
	int colNum;
	int colWidth;
	boolean isFormula;
	
	public XLSCell(String value, int colNum, int colWidth, boolean isFormula) {
		this.value = value;
		this.colNum = colNum;
		this.colWidth = colWidth;
		this.isFormula = isFormula;
	}
}
