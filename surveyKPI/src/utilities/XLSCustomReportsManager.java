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

import java.io.InputStream;
import java.text.SimpleDateFormat;


//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableColumnMarkup;



public class XLSCustomReportsManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	Workbook wb = null;
	int rowNumber = 1;		// Heading row is 0
	

	public XLSCustomReportsManager() {

	}
	
	public XLSCustomReportsManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	/*
	 * Create an customer report definition from an XLS file
	 */
	public ArrayList<TableColumn> getCustomReport(String type, InputStream inputStream) throws Exception {
		
		ArrayList<TableColumn> defn = new ArrayList<TableColumn> ();
		Sheet sheet = null;
		Sheet settingsSheet = null;
        Row row = null;
        int lastRowNum = 0;
        HashMap<String, Integer> header = null;
 
        
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook(inputStream);
		} else {
			wb = new XSSFWorkbook(inputStream);
		}
		
		/*
		 * Get the task sheet settings
		 */
		settingsSheet = wb.getSheet("settings");
		if(settingsSheet != null && settingsSheet.getPhysicalNumberOfRows() > 0) {
			int lastSettingsRow = settingsSheet.getLastRowNum();
			for(int j = 0; j <= lastSettingsRow; j++) {
				row = settingsSheet.getRow(j);
                
                if(row != null) {         	
                    int lastCellNum = row.getLastCellNum();
                    if(lastCellNum > 0) {
                    	Cell c = row.getCell(0);
                    	String k = c.getStringCellValue();
                    	if(k != null && k.trim().toLowerCase().equals("time zone:")) {
                    		c = row.getCell(1);
                    		break;
                    	}
                    }
                }
			}
		}
		
		sheet = wb.getSheet("definition");
		if(sheet.getPhysicalNumberOfRows() > 0) {
			
			lastRowNum = sheet.getLastRowNum();
			boolean needHeader = true;
			TableColumn currentCol = null;
			boolean processingConditions = false;
			
            for(int j = 0; j <= lastRowNum; j++) {
                
            	row = sheet.getRow(j);
                
                if(row != null) {
                	
                    int lastCellNum = row.getLastCellNum();
                    
                	if(needHeader) {
                		header = getHeader(row, lastCellNum);
                		needHeader = false;
                	} else {
                		String rowType = getColumn(row, "row type", header, lastCellNum, null);
                		
                		if(rowType != null) {
                			System.out.println("New row: " + rowType);
                			
                			// Close of any condition type calculations
                			if(processingConditions && !rowType.equals("condition")) {
                				processingConditions = false;
                				currentCol.calculation.add("END");
                			}
                			
                			// Process the row
	                		if(rowType.equals("column")) {
	                			currentCol = new TableColumn();
	                			defn.add(currentCol);
	                			
	                			// Get data type
	                			String dataType = getColumn(row, "data type", header, lastCellNum, null);
	                			if(dataType != null) {
	                				if(!dataType.equals("text") &&
	                						!dataType.equals("date") &&
	                						!dataType.equals("calculate") &&
	                						!dataType.equals("select_one")){
	                					throw new Exception("Invalid data type: " + dataType + " on row: " + (j + 1));
	                				}
	                				currentCol.type = dataType;
	                			} else {
	                				throw new Exception("Missing data type on row: " + (j + 1));
	                			}
	                			
	                			// Get column name
	                			String colName = getColumn(row, "name", header, lastCellNum, null);
	                			if(colName != null) {
	                				colName = colName.trim().toLowerCase();
	                				String modColName = colName.replaceAll("[^a-z0-9_]", "");
	                				if(colName.length() != modColName.length()) {
	                					throw new Exception("Invalid name: " + colName + " on row: " + (j + 1));
	                				} else if(colName.length() > 60) {
	                					throw new Exception("Name is too long (must be <= 60): " + colName + " on row: " + (j + 1));
	                				} 
	                				currentCol.name = colName;
	                			} else {
	                				throw new Exception("Missing name on row: " + (j + 1));
	                			}
	                			
	                			// Get display name
	                			String dispName = getColumn(row, "display name", header, lastCellNum, null);
	                			if(dispName != null) {
	              	
	                				currentCol.humanName = dispName;
	                			} else {
	                				throw new Exception("Missing display name on row: " + (j + 1));
	                			}
	                			
	                			// Get hide state
	                			String hide = getColumn(row, "hide", header, lastCellNum, null);
	                			currentCol.hide = false;
	                			if(hide != null) {
	                				hide = hide.toLowerCase().trim();
	                				if(hide.equals("yes") || hide.equals("true")) {
	                					currentCol.hide = true;
	                				}
	                			} 
	                			
	                			// Get readonly state
	                			String readonly = getColumn(row, "readonly", header, lastCellNum, null);
	                			currentCol.readonly = false;
	                			if(readonly != null) {
	                				readonly = readonly.toLowerCase().trim();
	                				if(readonly.equals("yes") || readonly.equals("true")) {
	                					currentCol.readonly = true;
	                				}
	                			}
	                			
	                			// Get filter state
	                			String filter = getColumn(row, "filter", header, lastCellNum, null);
	                			currentCol.filter = false;
	                			if(filter != null) {
	                				filter = filter.toLowerCase().trim();
	                				if(filter.equals("yes") || filter.equals("true")) {
	                					currentCol.filter = true;
	                				}
	                			}
	                			
	                			// Get calculation state
	                			if(currentCol.type.equals("calculate")) {
		                			String calculation = getColumn(row, "calculation", header, lastCellNum, null);
		                			
		                			if(calculation != null) {
		                				calculation = calculation.trim();
		                				if(calculation.equals("condition")) {
		                					// Calculation set by condition rows
		                				} else {
		                					throw new Exception("Unknown calculation " + calculation + " on row: " + (j + 1));
		                				}
		                			} else {
		                				throw new Exception("Missing calculation on row: " + (j + 1));
		                			}
	                			}
	                		} else if(rowType.equals("choice")) {
	                			if(currentCol != null && currentCol.type.equals("select_one")) {
	                				String value = getColumn(row, "value", header, lastCellNum, null);
	                				if(value != null) {
	                					if(currentCol.choices == null) {
	                						currentCol.choices = new ArrayList<String> ();
	                						currentCol.choices.add("");		// Add the not selected choice automatically as this has to be the default
	                					}
	                					currentCol.choices.add(value);
	                					currentCol.filter = true;
	                					
	                					// Add conditional color
		                				String appearance = getColumn(row, "appearance", header, lastCellNum, null);
		                				if(appearance != null) {
		                					if(currentCol.markup == null) {
		                						currentCol.markup = new ArrayList<TableColumnMarkup> ();
		                					}
		                					currentCol.markup.add(new TableColumnMarkup(value, getMarkup(appearance)));
		                					
		                				} 
	                				} else {
	                					throw new Exception("Missing value on row: " + (j + 1));
	                				}
	                			} else {
	                				throw new Exception("Unexpected \"choice\" on row: " + (j + 1));
	                			}
	                			
	                		} else if(rowType.equals("condition")) {
	                			
	                			if(currentCol != null && currentCol.type.equals("calculate")) {
	                				
	                				processingConditions = true;
	                				
	                				String condition = getColumn(row, "condition", header, lastCellNum, null);
	                				String value = getColumn(row, "value", header, lastCellNum, null);
	                				
	                				if(condition == null) {
	                					throw new Exception("Missing \"condition\" on row: " + (j + 1));
	                				} else {
	                					if(currentCol.calculation == null) {
	                						currentCol.calculation = new SqlFrag();
	                						currentCol.calculation.add("CASE");
	                					}
	                					if(condition.toLowerCase().trim().equals("all")) {
	                						currentCol.calculation.add("ELSE");
	                						currentCol.calculation.addText(value);
	                					} else {
	                						currentCol.calculation.add("WHEN");
	                						currentCol.calculation.addRaw(condition);
	                						currentCol.calculation.add("THEN");
	                						currentCol.calculation.addText(value);
	                					}
	                				}
	                				
	                				// Add conditional color
	                				String appearance = getColumn(row, "appearance", header, lastCellNum, null);
	                				if(appearance != null) {
	                					if(currentCol.markup == null) {
	                						currentCol.markup = new ArrayList<TableColumnMarkup> ();
	                					}
	                					currentCol.markup.add(new TableColumnMarkup(value, getMarkup(appearance)));
	                					
	                				} 
	
	                			} else {
	                				throw new Exception("Unexpected \"condition\" on row: " + (j + 1));
	                			} 
	                			
	                		}
                		}	
                		
                	}
                	
                }
                
            }
		}
	
		return defn;
		
		
	}
	

	/*
	 * Convert an appearance to jquery classes
	 */
	private String getMarkup(String app) {
		StringBuffer markup = new StringBuffer("");
		String [] apps = app.split(" ");
		
		for(int i = 0; i < apps.length; i++) {
			if(apps[i].equals("red")) {
				markup.append(" bg-danger");
			} else if(apps[i].equals("green")) {
				markup.append(" bg-success");
			} else if(apps[i].equals("blue")) {
				markup.append(" bg-info");
			} else if(apps[i].equals("yellow")) {
				markup.append(" bg-warning");
			}
		}
		
		return markup.toString().trim();
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
	 * Get the value of a cell at the specified column
	 */
	private String getColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum, String def) throws Exception {
		
		Integer cellIndex;
		int idx;
		String value = null;
		double dValue = 0.0;
		Date dateValue = null;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
	
		cellIndex = header.get(name);
		if(cellIndex != null) {
			idx = cellIndex;
			if(idx <= lastCellNum) {
				Cell c = row.getCell(idx);
				if(c != null) {
					if(c.getCellType() == Cell.CELL_TYPE_NUMERIC) {
						if (HSSFDateUtil.isCellDateFormatted(c)) {
							dateValue = c.getDateCellValue();
							value = dateFormat.format(dateValue);
						} else {
							dValue = c.getNumericCellValue();
							value = String.valueOf(dValue);
							if(value != null && value.endsWith(".0")) {
								value = value.substring(0, value.lastIndexOf('.'));
							}
						}
					} else if(c.getCellType() == Cell.CELL_TYPE_STRING) {
						value = c.getStringCellValue();
					} else {
						value = null;
					}

				}
			}
		} else {
			throw new Exception("Column " + name + " not found");
		}

		if(value == null) {		// Set to default value if null
			value = def;
		}
		
		return value;
	}
	



}
