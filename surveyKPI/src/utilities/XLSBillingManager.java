package utilities;

/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

import java.io.IOException;
import java.io.OutputStream;



//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.smap.sdal.model.BillLineItem;
import org.smap.sdal.model.BillingDetail;



public class XLSBillingManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	Workbook wb = null;
	int rowNumber = 0;		// Heading row is 0
	ResourceBundle localisation = null;
	
	HashMap<String, Integer> cols = new HashMap<String, Integer> ();
	
	public XLSBillingManager(ResourceBundle l) {
		localisation = l;
		wb = new XSSFWorkbook();
	}
	
	/*
	 * Write a list of bills to an XLS file
	 */
	public void createXLSBillFile(OutputStream outputStream, 
			ArrayList<BillingDetail> bills, 
			ResourceBundle localisation,
			int year,
			int month) throws IOException {
		
		Sheet billSheet = wb.createSheet("bill");

		createSettings(billSheet, year, month);
		createHeader(billSheet);	
		processBillListForXLSX(bills, billSheet);
		
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Add settings
	 */
	private void createSettings(Sheet sheet, int year, int month) {
			
		Row headerRow = sheet.createRow(rowNumber++);

        Cell cell = headerRow.createCell(0);
        cell.setCellValue(localisation.getString("bill_year"));
        cell = headerRow.createCell(1);
        cell.setCellValue(year);

        headerRow = sheet.createRow(rowNumber++);
        cell = headerRow.createCell(0);
        cell.setCellValue(localisation.getString("bill_month"));
        cell = headerRow.createCell(1);
        cell.setCellValue(month);
        
        headerRow = sheet.createRow(rowNumber++);		// Add a blank row
  
	}
	
	/*
	 * Create a header row 
	 */
	private void createHeader(Sheet sheet) {
			
		Row headerRow = sheet.createRow(rowNumber++);
		
		int idx = 0;
        Cell cell = headerRow.createCell(idx);
        cell.setCellValue("id");
        cols.put("id", idx++);
        
        cell = headerRow.createCell(idx);
        cell.setCellValue(localisation.getString("bill_org"));
        cols.put("organisation", idx++);
        
        cell = headerRow.createCell(idx);
        cell.setCellValue(localisation.getString("bill_sub"));
        cols.put("submissions", idx++);
        
        cell = headerRow.createCell(idx);
        cell.setCellValue(localisation.getString("bill_disk"));
        cols.put("disk", idx++);
	}
	
	/*
	 * Convert a task list array to XLSX
	 */
	private void processBillListForXLSX(
			ArrayList<BillingDetail> bills, 
			Sheet sheet) throws IOException {
		
		for(BillingDetail bill : bills)  {
			
			Row row = sheet.createRow(rowNumber++);
			int idx = 0;
				
			Cell cell = row.createCell(idx++);
		    cell.setCellValue(bill.oId);
		    
			cell = row.createCell(idx++);
		    cell.setCellValue(bill.oName);
		    
		    for(BillLineItem li : bill.line) {
		    		int i = cols.get(li.name);
		    		if(i > 0) {
		    			cell = row.createCell(i);
		    			cell.setCellValue(li.quantity);
		    		}    		
		    }
	
		}
	}
	
	

}
