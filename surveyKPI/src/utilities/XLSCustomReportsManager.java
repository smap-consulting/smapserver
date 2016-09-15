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
import java.sql.Connection;
import java.text.SimpleDateFormat;


//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.KeyValue;
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
	public ArrayList<TableColumn> getCustomReport(Connection sd, int oId, String type, 
			InputStream inputStream, ResourceBundle localisation) throws Exception {
		
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
                			rowType = rowType.trim().toLowerCase();
                			
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
	                						!dataType.equals("decimal") &&
	                						!dataType.equals("integer") &&
	                						!dataType.equals("select_one")){
	                					throw new Exception(localisation.getString("mf_idt") + ": " + dataType + 
	                							" " + localisation.getString("mf_or") + ": " + (j + 1));
	                				}
	                				currentCol.type = dataType;
	                			} else {
	                				throw new Exception(localisation.getString("mf_mdt") + " " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			// Get column name
	                			String colName = getColumn(row, "name", header, lastCellNum, null);
	                			if(colName != null) {
	                				colName = colName.trim().toLowerCase();
	                				String modColName = colName.replaceAll("[^a-z0-9_]", "");
	                				modColName = GeneralUtilityMethods.cleanName(modColName, true, true, true);
	                				if(colName.length() != modColName.length()) {
	                					throw new Exception(localisation.getString("mf_in") +
	                							": " + colName + " " + localisation.getString("mf_or") + ": " + (j + 1));
	                				} else if(colName.length() > 60) {
	                					throw new Exception(localisation.getString("mf_ntl") + ": " + colName + 
	                							" " + localisation.getString("mf_or") + ": " + (j + 1));
	                				}
	                				
	                				currentCol.name = colName;
	                			} else {
	                				throw new Exception(localisation.getString("mf_mn") + 
	                						localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			// Get display name
	                			String dispName = getColumn(row, "display name", header, lastCellNum, null);
	                			if(dispName != null) {
	              	
	                				currentCol.humanName = dispName;
	                			} else {
	                				throw new Exception(localisation.getString("mf_mdn") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
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
		                			
		                			if(calculation != null && calculation.length() > 0) {
		                				calculation = calculation.trim();
		                				if(calculation.equals("condition")) {
		                					// Calculation set by condition rows
		                				} else if(calculation.length() > 0) {
		                					currentCol.calculation = new SqlFrag();
		                					currentCol.calculation.addRaw(calculation, localisation);
		                				} 
		                			} else {
		                				throw new Exception(localisation.getString("mf_mc") + 
		                						" " + localisation.getString("mf_or") + ": " + (j + 1));
		                			}
	                			}
	                		} else if(rowType.equals("choice")) {
	                			if(currentCol != null && currentCol.type.equals("select_one")) {
	                				String name = getColumn(row, "name", header, lastCellNum, null);
	                				String dispName = getColumn(row, "display name", header, lastCellNum, null);
	                				if(name == null) {
	                					name = getColumn(row, "value", header, lastCellNum, null);	// Legacy implementation
	                					dispName = name;
	                				}

	                				if(name != null && dispName != null) {
	                					if(currentCol.choices == null) {
	                						currentCol.choices = new ArrayList<KeyValue> ();
	                						currentCol.choices.add(new KeyValue("", ""));		// Add the not selected choice automatically as this has to be the default
	                					}
	                					currentCol.choices.add(new KeyValue(name, dispName));
	                					currentCol.filter = true;
	                					
	                					// Add conditional color
		                				String appearance = getColumn(row, "appearance", header, lastCellNum, null);
		                				if(appearance != null) {
		                					if(currentCol.markup == null) {
		                						currentCol.markup = new ArrayList<TableColumnMarkup> ();
		                					}
		                					currentCol.markup.add(new TableColumnMarkup(name, getMarkup(appearance)));
		                					
		                				} 
	                				} else {
	                					throw new Exception(localisation.getString("mf_mv") + 
		                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                				}
	                			} else {
	                				throw new Exception(localisation.getString("mf_un_c") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                		} else if(rowType.equals("user_role")) {
	                			if(currentCol != null && currentCol.type.equals("select_one")) {
	                				String role = getColumn(row, "name", header, lastCellNum, null);
	                				if(role != null) {
	                					if(currentCol.choices == null) {
	                						currentCol.choices = new ArrayList<KeyValue> ();
	                						currentCol.choices.add(new KeyValue("", ""));		// Add the not selected choice automatically as this has to be the default
	                					}
	                					
	                					// Get the users that have this role
	                					currentCol.choices.addAll(GeneralUtilityMethods.getUsersWithRole(sd, oId, role));
	                					currentCol.filter = true;
	                					
	                				} else {
	                					throw new Exception(localisation.getString("mf_mv_o") + 
		                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                				}
	                			} else {
	                				throw new Exception(localisation.getString("mf_un_u") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                		} else if(rowType.equals("action")) {
	                			if(currentCol != null) {
	                				String action = getColumn(row, "action", header, lastCellNum, null);
	                				if(action != null) {
	                					if(action.equals("respond")) {
		                					if(currentCol.actions == null) {
		                						currentCol.actions = new ArrayList<Action> ();
		                					}
	                					} else {
	                						throw new Exception(localisation.getString("mf_ia") + 
			                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                					}
	                					
	                					// Get the action details
	                					Action todo = new Action(action);
	                					todo.notify_type = getColumn(row, "notify type", header, lastCellNum, null);
	                					todo.notify_person = getColumn(row, "notify person", header, lastCellNum, null);
	                					
	                					// Checks for a valid notification
	                					if(action.equals("respond") && todo.notify_type == null) {
	                						throw new Exception(localisation.getString("mf_mnt") + 
			                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                					} 
	                					if(action.equals("respond") && todo.notify_person == null) {
	                						throw new Exception(localisation.getString("mf_mnp") + 
			                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                					}
	                					
	                					currentCol.actions.add(todo);
	                					
	                				} else {
	                					throw new Exception(localisation.getString("mf_mv_a") + 
		                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                				}
	                			} else {
	                				throw new Exception(localisation.getString("mf_un_a") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
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
	                						currentCol.calculation.addRaw(condition, localisation);
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
	                			
	                		} else {
	                			throw new Exception(localisation.getString("mf_ur") + 
	                					" " + localisation.getString("mf_or") + ": " + (j + 1));
	                		}
                		}	
                		
                	}
                	
                }
                
            }
            
        	// Close of any condition type calculations
			if(processingConditions ) {
				processingConditions = false;
				currentCol.calculation.add("END");
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
