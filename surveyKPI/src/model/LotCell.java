package model;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;


public class LotCell extends XLSCell {

	CellStyle style;
	
	public LotCell(String value, int colNum, int colMerge, boolean isFormula, CellStyle style, int colWidth) {
		super(value, colNum, colMerge, isFormula, colWidth); 
		this.style = style;
	}
	
	public void writeToWorkSheet(Row row) {
		Cell cell = row.createCell(colNum);
		
		if(style != null) {
			cell.setCellStyle(style);
		}
		
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
