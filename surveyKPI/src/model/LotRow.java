package model;

import java.util.ArrayList;

import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;


public class LotRow {
	
	public int rowNum = 0;
	public int colNum = 0;					// Next available column in this row
	public boolean dataRow = false;		// Cells added for each data record
	public boolean groupRow = false;		// Cell width changes with each data record
	public String colName = null;
	public String correctRespValue = null;
	public int formulaStart = 0;
	public int formulaEnd = 0;
	
	ArrayList<LotCell> cells = new ArrayList<LotCell> ();
	
	public LotRow(int rowNum, boolean dataRow, boolean groupRow, String colName, String correctRespValue) {
		this.rowNum = rowNum;
		this.dataRow = dataRow;
		this.groupRow = groupRow;
		this.colName = colName;
		this.correctRespValue = correctRespValue;
		
		if(this.correctRespValue != null) {
			this.correctRespValue = this.correctRespValue.toLowerCase().trim();
		}
	}
	
	public void addCell(LotCell cell) {
		cells.add(cell);
	}
	
	public void writeToWorkSheet(Sheet sheet) {
		Row row = sheet.createRow(rowNum);
		for (LotCell cell : cells) {
			cell.writeToWorkSheet(row);
			if(cell.colMerge > 1) {
				sheet.addMergedRegion(
						new CellRangeAddress(rowNum, rowNum, cell.colNum, cell.colNum + cell.colMerge - 1));
			}
			if(cell.colWidth > 0) {
				sheet.setColumnWidth(cell.colNum, cell.colWidth);
			}
		}
	}
	
	public String getTotalCorrectFormula() {
		String formula = null;
		if(formulaEnd > formulaStart) {
			CellReference cStart = new CellReference(rowNum, formulaStart);
			CellReference cEnd = new CellReference(rowNum, formulaEnd);
			formula = "SUM(" + cStart.formatAsString() + ":" + cEnd.formatAsString() + ")";
		}
		return formula;		
	}
	
	public String getSampleSizeFormula() {
		String formula = null;
		if(formulaEnd > formulaStart) {
			CellReference cStart = new CellReference(rowNum, formulaStart);
			CellReference cEnd = new CellReference(rowNum, formulaEnd);
			formula = "COUNTIF(" + cStart.formatAsString() + ":" + cEnd.formatAsString() + ",1)+" +
					"COUNTIF(" + cStart.formatAsString() + ":" + cEnd.formatAsString() + ",0)";
		}
		return formula;
			
	}
	
	public void setCellMerge(int index, int merge) {
		LotCell cell = cells.get(index);
		cell.colMerge = merge;
	}
}
