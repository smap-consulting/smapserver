package utilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheet;

public class XlsReader {

	XSSFWorkbook wb = null;
	Sheet sheet;
	
	int rowNum = -1;
	int lastRowNum = -1;
	
	
	public XlsReader(InputStream is, String formName) throws Exception {
		
		final String sheetName = "d_" + formName;
		
		wb = new XSSFWorkbook(is) {
			 /** Avoid DOM parse of sheets we are not interested in */
            @Override
            public void parseSheet(java.util.Map<String,XSSFSheet> shIdMap, CTSheet ctSheet) {
                if (sheetName.equals(ctSheet.getName())) {
                    super.parseSheet(shIdMap, ctSheet);
                }
            }
		};
		sheet = wb.getSheet("d_" + formName);
		lastRowNum = sheet.getLastRowNum();
	}
	
	public String [] readNext() {
		ArrayList<String> values = new ArrayList<String> ();

		if(rowNum++ >= lastRowNum) {
			return null;
		}
		
		String value;
		Cell cell = null;
		Row row = sheet.getRow(rowNum);
		int lastCellNum = row.getLastCellNum();
		DataFormatter df = new DataFormatter();
	
		for(int i = 0; i <= lastCellNum; i++) {
            cell = row.getCell(i);
            if(cell != null) {
                value = df.formatCellValue(cell);
                if(value == null) {
                	value = "";
                }
                values.add(value);
            }
        }
		
		String[] vArray = new String[values.size()];
		
		return values.toArray(vArray);
	}
	
	public void close() throws Exception {
		wb.close();
	}
}
