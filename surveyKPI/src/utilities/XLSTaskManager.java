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
import java.util.logging.Logger;

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
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;
import org.smap.sdal.model.Location;
import org.w3c.dom.Element;



public class XLSTaskManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	Workbook wb = null;
	int rowNumber = 1;		// Heading row is 0
	
	private class Column {
		String name;
		int colNumber;
		
		public Column(int col, String n) {
			colNumber = col;
			name = n;
		}
		
		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			return width;
		}
		
		// Get a value for this column from the provided properties object
		public String getValue(TaskProperties props) {
			String value = null;
			
			if(name.equals("id")) {
				value = String.valueOf(props.id);
			} else if(name.equals("title")) {
				value = String.valueOf(props.title);
			}
			
			if(value == null) {
				value = "";
			}
			return value;
		}
	}

	public XLSTaskManager() {

	}
	
	public XLSTaskManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	public void createXLSTaskList(OutputStream outputStream, TaskListGeoJson tl) throws IOException {
		
		Sheet taskListSheet = wb.createSheet("survey");
		taskListSheet.createFreezePane(2, 1);
		
		Map<String, CellStyle> styles = createStyles(wb);

		ArrayList<Column> cols = getColumns();
		createHeader(cols, taskListSheet, styles);	
		processTaskListForXLS(tl, taskListSheet, styles, cols);
		
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Get an array of locations from an XLS worksheet
	 */
	public ArrayList<Location> convertWorksheetToTagArray(InputStream inputStream, String type) throws IOException {
		
		Sheet sheet = null;
        Row row = null;
        int lastRowNum = 0;
        String group = null;
        ArrayList<Location> tags = new ArrayList<Location> ();
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
                    		Location t = new Location();
                    		t.group = group;
                    		t.type = "nfc";
                    		try {
                    			t.uid = getColumn(row, "uid", header, lastCellNum);
                    			t.name = getColumn(row, "tagname", header, lastCellNum);
                    			tags.add(t);
                    		} catch (Exception e) {
                    			log.info("Error getting nfc columnL" + e.getMessage());
                    		}
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
	 * The the value of a cell at the specified column
	 */
	private String getColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum) throws Exception {
		
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
		} else {
			throw new Exception("Column " + name + " not found");
		}

		return value;
	}
	
	/*
	 * Get the columns for the settings sheet
	 */
	private ArrayList<Column> getColumns() {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
		
		cols.add(new Column(colNumber++, "id"));
		cols.add(new Column(colNumber++, "title"));
		
		return cols;
	}
	
	/*
     * create a library of cell styles
     */
    private static Map<String, CellStyle> createStyles(Workbook wb){
        Map<String, CellStyle> styles = new HashMap<String, CellStyle>();

        CellStyle style = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        style.setFont(headerFont);
        styles.put("header", style);

        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("default", style);

        return styles;
    }
    
	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(ArrayList<Column> cols, Sheet sheet, Map<String, CellStyle> styles) {
		// Set column widths
		for(int i = 0; i < cols.size(); i++) {
			sheet.setColumnWidth(i, cols.get(i).getWidth());
		}
				
		Row headerRow = sheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(col.name);
        }
	}
	
	/*
	 * Convert a task list array to XLS
	 */
	private void processTaskListForXLS(
			TaskListGeoJson tl, 
			Sheet sheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> cols) throws IOException {
		
		for(TaskFeature feature : tl.features)  {
			
			TaskProperties props = feature.properties;
				
			Row row = sheet.createRow(rowNumber++);
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);			
				Cell cell = row.createCell(i);
				cell.setCellStyle(styles.get("default"));			
				cell.setCellValue(col.getValue(props));
	        }
			
		}
	}

}
