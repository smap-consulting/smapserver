package model;

import java.util.ArrayList;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;


public class LotRow {
	
	int rowNum = 0;
	boolean dataRow = false;		// Cells added for each data record
	boolean groupRow = false;		// Cell width changes with each data record
	
	ArrayList<LotCell> cells = new ArrayList<LotCell> ();
	
	public LotRow(int rowNum, boolean dataRow, boolean groupRow) {
		this.rowNum = rowNum;
		this.dataRow = dataRow;
		this.groupRow = groupRow;
	}
	
	public void addCell(LotCell cell) {
		cells.add(cell);
	}
	
	public void writeToWorkSheet(Sheet sheet) {
		Row row = sheet.createRow(rowNum);
		for (LotCell cell : cells) {
			cell.writeToWorkSheet(row);
			if(cell.colWidth > 1) {
				sheet.addMergedRegion(
						new CellRangeAddress(rowNum, rowNum, cell.colNum, cell.colNum + cell.colWidth - 1));
			}
		}
	}
}
