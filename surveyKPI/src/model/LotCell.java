package model;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;


public class LotCell extends XLSCell {

	public LotCell(String value, int colNum, int colWidth) {
		super(value, colNum, colWidth);
	}
	
	public void writeToWorkSheet(Row row) {
		Cell cell = row.createCell(colNum);
		//CellStyle style = col.getStyle(styles, q);
		//if(style != null) {	cell.setCellStyle(style); }
		
		
		cell.setCellValue(value);
	}
}
