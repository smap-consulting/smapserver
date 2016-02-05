package model;

/*
 * Contains attributes of a cell for XLS 
 */

public abstract class XLSCell {
	String value = null;
	int colNum;
	int colMerge;
	boolean isFormula;
	int colWidth;
	
	public XLSCell(String value, int colNum, int colMerge, boolean isFormula, int colWidth) {
		this.value = value;
		this.colNum = colNum;
		this.colMerge = colMerge;
		this.isFormula = isFormula;
		this.colWidth = colWidth;
	}
}
