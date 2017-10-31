package utilities;

import java.io.IOException;

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
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.CustomReportItem;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.LQASGroup;
import org.smap.sdal.model.LQASItem;
import org.smap.sdal.model.LQASdataItem;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableColumnMarkup;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;
import org.smap.sdal.model.Role;

import model.LotCell;
import model.LotRow;

public class XLSCustomReportsManager {
	
	
	private class Column {
		String name;
		String human_name;
		int colNumber;
		
		public Column(ResourceBundle localisation, int col, String n) {
			colNumber = col;
			name = n;
			human_name = n;
		}
		
		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			return width;
		}
		
		/*
		 * Return true if this table column is a question
		 * All table/columns should probably be questions
		 */
		public boolean isQuestion(String type) {
			boolean resp = false;
			if(type != null &&
					type.trim().length() > 0) {
				resp = true;
			}
			return resp;
		}
		// Get a value for this column from the provided properties object
		public String getValue(TableColumn props, Sheet sheet, 
				ArrayList<Column> cols,
				Map<String, CellStyle> styles) {
			String value = null;
			
			if(name.equals("row type")) {
				if(isQuestion(props.type)) {
					value="column";
				}
			} else if(name.equals("data type")) {
				value = props.type;
			} else if(name.equals("name")) {
				value = props.name;
			} else if(name.equals("display name")) {
				value = props.humanName;
			} else if(name.equals("hide")) {
				value = props.readonly ? "yes" : "no";
			} else if(name.equals("filter")) {
				value = props.filter ? "yes" : "no";
			} else if(name.equals("calculation")) {
				if(props.calculation != null) {
					if(props.type != null && props.type.equals("calculate")) {
						if(props.isCondition) {
							value = "condition";
						} else {
							if(props.calculation.expression != null) {
								value = props.calculation.expression.toString();
							}
						}
					}	
				}
			}  
			
			if(value == null) {
				value = "";
			}
			return value;
		}
	
		// Get a date value for this column from the provided properties object
		public Timestamp getDateValue(TaskProperties props) {
			Timestamp value = null;
			
			if(name.equals("from")) {
				value = props.from;
			} else if(name.equals("to")) {
				value = props.to;
			} 
			
			return value;
		}
		
	}
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	Workbook wb = null;
	int rowNumber = 1;		// Heading row is 0
	
	ArrayList<KeyValue> markup = new ArrayList<KeyValue> ();

	public XLSCustomReportsManager() {
		// Add list of markup mappings between appearance values and bootstrap classes
		markup.add(new KeyValue("red", "bg-danger", false));
		markup.add(new KeyValue("green", "bg-success", false));
		markup.add(new KeyValue("blue", "bg-info", false));
		markup.add(new KeyValue("yellow", "bg-warning", false));
	}
	
	/*
	 * Create an oversight form definition from an XLS file
	 */
	public ReportConfig getOversightDefinition(Connection sd, int oId, String type, 
			InputStream inputStream, ResourceBundle localisation, boolean isSecurityManager) throws Exception {
		
		ReportConfig config = new ReportConfig();
		config.columns = new ArrayList<TableColumn> ();
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
		if(sheet == null) {
			if(wb.getNumberOfSheets() == 1) {
				sheet = wb.getSheetAt(0);
			} else {
				throw new Exception("A worksheet called 'definition' not found");
			}
		}
		if(sheet.getPhysicalNumberOfRows() > 0) {
			
			lastRowNum = sheet.getLastRowNum();
			boolean needHeader = true;
			TableColumn currentCol = null;
			boolean processingConditions = false;
			PreparedStatement pstmtGetRoleId = null;
			String sqlGetRoleId = "select id from role where o_id = ? and name = ?";
			
			try {
				
				// Get ready to process roles
				pstmtGetRoleId = sd.prepareStatement(sqlGetRoleId);
				pstmtGetRoleId.setInt(1,oId);
				
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
		                			config.columns.add(currentCol);
		                			
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
		                			
		                			// Get parameters
		                			try {
		                				currentCol.parameters = getParamObj(getColumn(row, "parameters", header, lastCellNum, null));
		                			} catch (Exception e) {
		                				// Ignore errors if parameters are not found
		                			}
		                			
		                			// Get calculation state
		                			if(currentCol.type.equals("calculate")) {
			                			String calculation = getColumn(row, "calculation", header, lastCellNum, null);
			                			
			                			if(calculation != null && calculation.length() > 0) {
			                				calculation = calculation.trim();
			                				if(calculation.equals("condition")) {
			                					// Calculation set by condition rows
			                					currentCol.isCondition = true;
			                				} else if(calculation.length() > 0) {
			                					currentCol.calculation = new SqlFrag();
			                					currentCol.calculation.addSqlFragment(calculation, localisation, true);
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
		                					currentCol.choices.add(new KeyValue(role, role, true));
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
		                					if(isSecurityManager) {
		                						String roles = getColumn(row, "roles", header, lastCellNum, null);
		                						if(roles != null) {
		                							todo.roles = new ArrayList<Role> ();
		                							String rArray [] = roles.split(",");
		                							for(int i = 0; i < rArray.length; i++) {
		                								pstmtGetRoleId.setString(2, rArray[i].trim());
		                								ResultSet rs = pstmtGetRoleId.executeQuery();
		                								if(rs.next()) {
		                									todo.roles.add(new Role(rs.getInt(1), rArray[i].trim()));
		                								}
		                							}
		                						}
		                					}
		                					// Checks for a valid notification
		                					if(action.equals("respond") && todo.notify_type == null) {
		                						throw new Exception(localisation.getString("mf_mnt") + 
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
		                						currentCol.calculation.addSqlFragment(value, localisation, false);
		                						//currentCol.calculation.addText(value);
		                					} else {
		                						currentCol.calculation.add("WHEN");
		                						currentCol.calculation.addSqlFragment(condition, localisation, true);
		                						currentCol.calculation.add("THEN");
		                						//currentCol.calculation.addText(value);
		                						currentCol.calculation.addSqlFragment(value, localisation, false);
		                					}
		                				}
		                				
		                				
		                				// Add conditional markup and save value for use in export of form
		                				String appearance = getColumn(row, "appearance", header, lastCellNum, null);
		                				if(currentCol.markup == null) {
		                					currentCol.markup = new ArrayList<TableColumnMarkup> ();        					
		                				} 
		                				currentCol.markup.add(new TableColumnMarkup(value, getMarkup(appearance)));
		
		                			} else {
		                				throw new Exception(localisation.getString("mf_uc") + 
		                						" " + localisation.getString("mf_or") + ": " + (j + 1));
		                			} 
		                			
		                		} else if(rowType.equals("settings")) {
		                			config.settings = getParamObj(getColumn(row, "parameters", header, lastCellNum, null));
		                		} else {
		                			throw new Exception(localisation.getString("mf_ur") + 
		                					" " + localisation.getString("mf_or") + ": " + (j + 1));
		                		}
	                		}	
	                		
	                	}
	                	
	                }
	                
	            }
			} finally {
			}
            
        	// Close of any condition type calculations
			if(processingConditions ) {
				processingConditions = false;
				currentCol.calculation.add("END");
			}
		}
	
		return config;
		
		
	}
	
	
	/*
	 * Export an oversight definition to an XLS file
	 */
	public void writeOversightDefinition(
			Connection sd, 
			Connection cResults,
			int oId, 
			String type, 
			OutputStream outputStream, 
			ReportConfig config,
			ResourceBundle localisation) throws Exception {
		
        boolean isXLSX;
 
        if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
			isXLSX = false;
		} else {
			wb = new SXSSFWorkbook(10);
			isXLSX = true;
		}
		
		Sheet sheet = wb.createSheet("definition");
		sheet.createFreezePane(2, 1);
		Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);
		
		ArrayList<Column> cols = getColumnList(localisation);
		createHeader(cols, sheet, styles);	
		processCustomReportListForXLS(config, sheet, styles, cols);
		
		wb.write(outputStream);
		outputStream.close();
		
		// If XLSX then temporary streaming files need to be deleted
		if(isXLSX) {
			((SXSSFWorkbook) wb).dispose();
		}

	}
	
	/*
	 * Create an LQAS definition from an XLS file
	 */
	public LQAS getLQASReport(Connection sd, int oId, String type, 
			InputStream inputStream, ResourceBundle localisation) throws Exception {
		
		LQAS lqas = null;
		String lot = null;
		
		ArrayList<TableColumn> defn = new ArrayList<TableColumn> ();
		Sheet sheet = null;
		Sheet settingsSheet = null;
        Row row = null;
        int lastRowNum = 0;
        HashMap<String, Integer> header = null;
 
        
        System.out.println("Getting LQAS report");
        
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook(inputStream);
		} else {
			wb = new XSSFWorkbook(inputStream);
		}
		
		/*
		 * Get the settings
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
                    	if(k != null && k.trim().toLowerCase().equals("lot")) {
                    		c = row.getCell(1);
                    		lot = c.getStringCellValue();
                    	} 
                    }
                }
			}
		}
		
		if(lot != null) {
			lqas = new LQAS(lot);
		} else {
			throw new Exception("Lot value not specified in settings");
		}
		
		sheet = wb.getSheet("definition");
		if(sheet.getPhysicalNumberOfRows() > 0) {
			
			lastRowNum = sheet.getLastRowNum();
			boolean processingConditions = false;
			boolean needHeader = true;
			LQASGroup currentGroup = null;
			LQASdataItem currentDataItem = null;
			LQASItem currentItem = null;
			TableColumn tc = new TableColumn();

			
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
                			String name = getColumn(row, "name", header, lastCellNum, null);
                			
                			// Close of any condition type calculations
                			if(processingConditions && !rowType.equals("condition")) {
                				processingConditions = false;
                				currentDataItem.select.add("END");
                			}
                			
                			// Process the row
	                		if(rowType.equals("group")) {
	                			String groupName = getColumn(row, "display name", header, lastCellNum, null);
	                			currentGroup = new LQASGroup(groupName);
	                			lqas.groups.add(currentGroup);
	                		
	                		} else if(rowType.equals("data")) {
	                	
	                			SqlFrag select = null; 
	                			String[] sources = null; 
	          			
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
	                			} else {
	                				throw new Exception(localisation.getString("mf_mdt") + " " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			// Get calculation state
		                		String calculation = getColumn(row, "calculation", header, lastCellNum, null);		
	                			if(calculation != null && calculation.length() > 0) {
	                				calculation = calculation.trim();
	                				if(calculation.equals("condition")) {
	                					// Calculation set by condition rows
	                					
	                				} else if(calculation.length() > 0) {
	                					select = new SqlFrag();
	                					select.addSqlFragment(calculation, localisation, false);
	                				} 
	                			} else {
	                				throw new Exception(localisation.getString("mf_mc") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			
	                			currentDataItem = new LQASdataItem(name,  select, dataType.equals("text"));
	                			lqas.dataItems.add(currentDataItem);
	                			
	                			
	                			
	                		} else if(rowType.equals("item")) {
	                	
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
	                			} else {
	                				throw new Exception(localisation.getString("mf_mdt") + " " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			String targetResponseText = getColumn(row, "target response text", header, lastCellNum, null);
	                			String displayName = getColumn(row, "display name", header, lastCellNum, null);

	                			// Get calculation 
		                		String calculation = getColumn(row, "calculation", header, lastCellNum, null);		
	                			if(calculation != null && calculation.length() > 0) {
	                				calculation = calculation.trim();
	                			} else {
	                				throw new Exception(localisation.getString("mf_mc") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
	                			}
	                			
	                			ArrayList<String> sources = getSourcesFromItem(calculation);
	                			currentItem = new LQASItem(name,  displayName, calculation, targetResponseText,  sources);
	                			currentGroup.items.add(currentItem);
	                			
	                		} else if(rowType.equals("footer")) {
	                			
	                			String displayName = getColumn(row, "display name", header, lastCellNum, null);
	                			lqas.footer = new LQASItem("footer",  displayName, null, null,  null);
	                			
	                			
	                		} else if(rowType.equals("condition")) {
	                			
	                			if(currentDataItem != null) {
	                				
	                				processingConditions = true;
	                				
	                				String condition = getColumn(row, "condition", header, lastCellNum, null);
	                				String value = getColumn(row, "value", header, lastCellNum, null);
	                				
	                				if(condition == null) {
	                					throw new Exception("Missing \"condition\" on row: " + (j + 1));
	                				} else {
	                					if(currentDataItem.select == null) {
	                						currentDataItem.select = new SqlFrag();
	                						currentDataItem.select.add("CASE");
	                					}
	                					if(condition.toLowerCase().trim().equals("all")) {
	                						currentDataItem.select.add("ELSE");
	                						currentDataItem.select.addText(value);
	                					} else {
	                						currentDataItem.select.add("WHEN");
	                						currentDataItem.select.addSqlFragment(condition, localisation, true);
	                						currentDataItem.select.add("THEN");
	                						currentDataItem.select.addText(value);
	                					}
	                				}
	                				
	                			
	
	                			} else {
	                				throw new Exception(localisation.getString("mf_uc") + 
	                						" " + localisation.getString("mf_or") + ": " + (j + 1));
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
				currentDataItem.select.add("END");
			}
            
		}
		return lqas;
		
	}
	
	private ArrayList<String> getSourcesFromItem(String calc) {
		ArrayList<String> sources = new ArrayList<String> ();
		
		if(calc != null) {
			String [] tokens = calc.split("[\\s]");  // Split on white space
			for(int j = 0; j < tokens.length; j++) {
				String token = tokens[j].trim();
				if(token.startsWith("#{") && token.endsWith("}")) {
					String name = token.substring(2, token.length() - 1);
					boolean captured = false;
					
					for(int i = 0; i < sources.size(); i++) {
						if(sources.get(i).equals(name)) {
							captured = true;
							break;
						}
					}
					if(!captured) {
						sources.add(name);
					}
				}
			}
		}
		return sources;
	}
	
	/*
	 * Convert an appearance to jquery classes
	 * TODO markup should be handled in the client
	 */
	private String getMarkup(String app) {

		StringBuffer markupString = new StringBuffer("");
		
		if(app != null) {
			String [] apps = app.trim().toLowerCase().split(" ");
			boolean hasMarkup = false;
			for(int i = 0; i < apps.length; i++) {
				for(int j = 0; j < markup.size(); j++) {
					if(markup.get(j).k.equals(apps[i])) {
						if(hasMarkup) {
							markupString.append(" ");
						}
						hasMarkup = true;
						markupString.append(markup.get(j).v);
					}
				}
			}
		}
		
		return markupString.toString().trim();
	}
	
	/*
	 * Convert an appearance to jquery classes
	 * TODO markup should be handled in the client
	 */
	private String markupToAppearance(String markupString) {

		StringBuffer appString = new StringBuffer("");
		
		if(markup != null) {
			String [] markupArray = markupString.split(" ");
			boolean hasApp = false;
			for(int i = 0; i < markupArray.length; i++) {
				for(int j = 0; j < markup.size(); j++) {
					if(markup.get(j).v.equals(markupArray[i])) {
						if(hasApp) {
							appString.append(" ");
						}
						hasApp = true;
						appString.append(markup.get(j).k);
					}
				}
			}
		}
		
		return appString.toString().trim();
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
	
	/*
	 * Get the columns for the oversight form definition sheet
	 */
	private ArrayList<Column> getColumnList(ResourceBundle localisation) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
	
		cols.add(new Column(localisation, colNumber++, "row type"));
		cols.add(new Column(localisation, colNumber++, "data type"));
		cols.add(new Column(localisation, colNumber++, "name"));
		cols.add(new Column(localisation, colNumber++, "display name"));
		cols.add(new Column(localisation, colNumber++, "hide"));
		cols.add(new Column(localisation, colNumber++, "readonly"));
		cols.add(new Column(localisation, colNumber++, "filter"));
		cols.add(new Column(localisation, colNumber++, "calculation"));
		cols.add(new Column(localisation, colNumber++, "condition"));
		cols.add(new Column(localisation, colNumber++, "value"));
		cols.add(new Column(localisation, colNumber++, "appearance"));
		
		return cols;
	}

	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(ArrayList<Column> cols, Sheet sheet, Map<String, CellStyle> styles) {
		// Set column widths
		for(int i = 0; i < cols.size(); i++) {
			sheet.setColumnWidth(i, cols.get(i).getWidth());
		}
				
		// Create survey sheet header row
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
	 * Convert an oversight report configurationto XLS
	 */
	private void processCustomReportListForXLS(
			ReportConfig config, 
			Sheet sheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> cols) throws IOException {
		
		for(TableColumn tc : config.columns)  {
				
			Row row = sheet.createRow(rowNumber++);
			
			// Add question rows
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);			
				Cell cell = row.createCell(i);

				cell.setCellStyle(styles.get("default"));	
				cell.setCellValue(col.getValue(tc, sheet, cols, styles));	
	        }	
			
			// Add condition rows
			if(tc.calculation != null && tc.calculation.conditions != null && tc.isCondition) {
				for(int j = 0; j < tc.calculation.conditions.size(); j++) {
					
					row = sheet.createRow(rowNumber++);
					createCell(row, getIndexCol(cols, "row type"), "condition", styles.get("default"));
					createCell(row, getIndexCol(cols, "condition"), tc.calculation.conditions.get(j), styles.get("default"));
					createCell(row, getIndexCol(cols, "condition"), tc.calculation.conditions.get(j), styles.get("default"));
					if(tc.markup != null) {
						createCell(row, getIndexCol(cols, "value"), tc.markup.get(j).value, styles.get("default"));
						createCell(row, getIndexCol(cols, "appearance"), markupToAppearance(tc.markup.get(j).classes), styles.get("default"));
					}
				}
			}
			
			// Add choice rows
			if(tc.choices != null) {
				for(int j = 0; j < tc.choices.size(); j++) {
					
					KeyValue choice = tc.choices.get(j);
					row = sheet.createRow(rowNumber++);
					
					createCell(row, getIndexCol(cols, "row type"), choice.isRole ? "user_role" : "choice", styles.get("default"));
					createCell(row, getIndexCol(cols, "name"), choice.k, styles.get("default"));
					createCell(row, getIndexCol(cols, "display name"), choice.v, styles.get("default"));
					if(tc.markup != null && j < tc.markup.size()) {
						createCell(row, getIndexCol(cols, "appearance"), markupToAppearance(tc.markup.get(j).classes), styles.get("default"));
					}
				}	
			}
			
			// Add action rows
			if(tc.actions != null) {
				for(int j = 0; j < tc.actions.size(); j++) {
					
					Action a = tc.actions.get(j);
					
					row = sheet.createRow(rowNumber++);
					createCell(row, getIndexCol(cols, "row type"), "action", styles.get("default"));
					createCell(row, getIndexCol(cols, "action"), a.action, styles.get("default"));
					createCell(row, getIndexCol(cols, "notify type"), a.notify_type, styles.get("default"));
					createCell(row, getIndexCol(cols, "notify person"), a.notify_person, styles.get("default"));
					StringBuffer roles = new StringBuffer("");
					if(a.roles != null) {
						for(int k = 0; k < a.roles.size(); k++) {
							Role r = a.roles.get(k);
							roles.append(r.name);
						}
					}
					
				}
			}
			

		}
		
	}

	private int getIndexCol(ArrayList<Column> cols, String name) {
		int col = -1;
		
		for(int i = 0; i < cols.size(); i++) {
			if(cols.get(i).name.equals(name)) {
				col = i;
				break;
			}
		}
		
		return col;
	}

	private void createCell(Row row, int colIdx, String value, CellStyle style) {
		if(colIdx >= 0) {
			Cell cell = row.createCell(colIdx);
			cell.setCellStyle(style);	
			cell.setCellValue(value);
		}
	}
	
	private HashMap<String, String> getParamObj(String parameters) {
		
		HashMap<String, String> paramObj = null;
		
		if(parameters != null) {
			paramObj = new HashMap<String, String> ();
			String [] params = parameters.split(" ");
			for(int i = 0; i < params.length; i++) {
				String[] p = params[i].split("=");
				if(p.length > 1) {
					if(p[0].equals("rows")) {
						try {
							int rows = Integer.valueOf(p[1]);   // Check that rows is an integer
							paramObj.put(p[0], p[1]);
						} catch (Exception e) {
							// Ignore exceptions
						}
					} else if(p[0].equals("source")) {	
						paramObj.put(p[0], p[1]);						
					} else if(p[0].equals("form_data")) {	
						paramObj.put(p[0], p[1]);						
					} else if(p[0].equals("auto")) {	
						paramObj.put(p[0], p[1]);						
					}
					// Ignore parameters that we don't know about
				} 
			}
		}
		
		return paramObj;
	}
	
}
