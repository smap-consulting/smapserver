package model;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;


public class LotCell extends XLSCell {

	public LotCell(String value, int colNum, int colWidth, boolean isFormula) {
		super(value, colNum, colWidth, isFormula);
	}
	
	public void writeToWorkSheet(Row row) {
		Cell cell = row.createCell(colNum);
		//CellStyle style = col.getStyle(styles, q);
		//if(style != null) {	cell.setCellStyle(style); }
		
		if(isFormula) {
			cell.setCellFormula(value);
		} else {
			if(StringUtils.isNumeric(value)) {
				if(value.indexOf('.') > 0) {
					cell.setCellValue(Double.parseDouble(value));
				} else {
					cell.setCellValue(Integer.parseInt(value));
				}
			} else {
				cell.setCellValue(value);
			}
		}
	}
}
