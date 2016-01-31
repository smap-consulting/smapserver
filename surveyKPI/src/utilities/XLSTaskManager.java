package utilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.Tag;
import org.w3c.dom.Element;

public class XLSTaskManager {
	
	Workbook wb = null;
	int rowNumberSurvey = 1;		// Heading row is 0
	int rowNumberChoices = 1;		// Heading row is 0
	int rowNumberSettings = 1;		// Heading row is 0

	public XLSTaskManager() {

	}
	
	public ArrayList<Tag> convertWorksheetToTagArray(InputStream inputStream, String type) throws IOException {
		
		Sheet sheet = null;
        Row row = null;
        int lastRowNum = 0;
        String group = null;
        ArrayList<Tag> tags = new ArrayList<Tag> ();
        HashMap<String, Integer> header = null;
        
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook(inputStream);
		} else {
			wb = new XSSFWorkbook(inputStream);
		}
		
		int numSheets = wb.getNumberOfSheets();
		
		for(int i = 0; i < numSheets; i++) {
			sheet = wb.getSheetAt(i);
			if(sheet.getPhysicalNumberOfRows() > 0) {
				
				group = sheet.getSheetName();
				lastRowNum = sheet.getLastRowNum();
				boolean needHeader = true;
				
                for(int j = 0; j <= lastRowNum; j++) {
                    
                	row = sheet.getRow(j);
                    
                    if(row != null) {
                    	
                        int lastCellNum = row.getLastCellNum();
                        
                    	if(needHeader) {
                    		header = getHeader(row, lastCellNum);
                    		needHeader = false;
                    	} else {
                    		Tag t = new Tag();
                    		t.group = group;
                    		t.type = "nfc";
                    		t.uid = getColumn(row, "uid", header, lastCellNum);
                    		t.name = getColumn(row, "tagname", header, lastCellNum);
                    		tags.add(t);
                    	}
                    	
                    }
                    
                }
			}
		}
		
		return tags;

	}
	
	/*
	 * Get a hashmap of column name and column index
	 */
	private HashMap<String, Integer> getHeader(Row row, int lastCellNum) {
		HashMap<String, Integer> header = new HashMap<String, Integer> ();
		
		Cell cell = null;
		String name = null;
		
        for(int i = 0; i <= lastCellNum; i++) {
            cell = row.getCell(i);
            if(cell != null) {
                name = cell.getStringCellValue();
                if(name != null && name.trim().length() > 0) {
                	name = name.toLowerCase();
                    header.put(name, i);
                }
            }
        }
            
		return header;
	}
	
	/*
	 * Convert a row to Json
	 */
	private String getColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum) {
		
		Integer cellIndex;
		int idx;
		String value = null;
	
		cellIndex = header.get(name);
		if(cellIndex != null) {
			idx = cellIndex;
			if(idx <= lastCellNum) {
				Cell c = row.getCell(idx);
				if(c != null) {
					value = c.toString();
					if(c.getCellType() == Cell.CELL_TYPE_NUMERIC) {
						if(value != null && value.endsWith(".0")) {
							value = value.substring(0, value.lastIndexOf('.'));
						}
					}
				}
			}
		}

		return value;
	}

}
