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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import model.Lot;
import model.LotCell;
import model.LotRow;
import model.LotSummary;
import net.sourceforge.jeval.Evaluator;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.LQASGroup;
import org.smap.sdal.model.LQASItem;
import org.smap.sdal.model.LQASdataItem;
import org.smap.sdal.model.LQASdataItemOld;
import org.smap.sdal.model.LQASold;

import surveyKPI.ExportLQAS;

/*
 * Class to manage the creation of an LQAS workbook
 * Assume all the data is in a single table
 */
public class XLS_LQAS_Manager {
	
	private static Logger log =
			 Logger.getLogger(XLS_LQAS_Manager.class.getName());
	
	Workbook wb = null;
	
	public XLS_LQAS_Manager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	public void createLQASForm(Connection sd, Connection cResults, OutputStream outputStream, 
			org.smap.sdal.model.Survey survey, 
			LQASold lqasOld,
			boolean showSources) throws Exception {
		
		// Get the table name
		String tableName = GeneralUtilityMethods.getTableForQuestion(sd, survey.id, lqasOld.lot);
		
		// Create a work sheet for each lot
		ArrayList<Lot> lots = getLots(cResults, tableName, lqasOld.lot );
		LotSummary sum = new LotSummary(wb);
		PreparedStatement pstmt = null;
		
		// Create styles
		Map<String, CellStyle> styles = createStyles(wb);
		HashMap<String, String> columnNames = new HashMap<String, String> ();		// Keep track of duplicate column names
		
		try {
			
			Evaluator eval = new Evaluator();
			
			/*
			 * Create the Data Query
			 */
			StringBuffer sbSql = new StringBuffer("select ");
			boolean gotOne = false;
			for(LQASdataItemOld dataItem : lqasOld.dataItems) {
				if(gotOne) {
					sbSql.append(",");
				}
				sbSql.append(dataItem.select);
				sbSql.append(" as ");
				sbSql.append(dataItem.ident);
				
				// If show sources was selected then add the source columns
				if(showSources && dataItem.sourceColumns != null) {
					for(int i = 0; i < dataItem.sourceColumns.length; i++ ) {
						if(columnNames.get(dataItem.sourceColumns[i]) == null) {
							sbSql.append(",");
							sbSql.append(dataItem.sourceColumns[i]);
							columnNames.put(dataItem.sourceColumns[i], dataItem.sourceColumns[i]);
						}
					}
				}
				
				gotOne = true;
			}
			
			sbSql.append(" from ");
			sbSql.append(tableName);
			sbSql.append(" where ");
			sbSql.append(lqasOld.lot);
			sbSql.append(" = ? order by prikey asc;");
			
			pstmt = cResults.prepareStatement(sbSql.toString());
		
			/*
			 * Populate the worksheets
			 */
			for(Lot lot : lots) {			
				
				/*
				 * Add rows to each Lot and fill in the data independent columns
				 */
				int rowNum = 3;		// Arbitrarily start from 4th row as per example LQAS output
				
				LotRow new_row = new LotRow(rowNum++, false, false, null, null, false, false);			// Title
				new_row.addCell(new LotCell(survey.displayName, 2, 10, false, styles.get("title"), 0));
				lot.addRow(new_row);
				
				new_row = new LotRow(rowNum++, false, false, null, null, false, false);			// Signature and date
				new_row.addCell(new LotCell("Survey Team Supervisor: ______________", 5, 5, false,styles.get("no_border"), 0));
				new_row.addCell(new LotCell("Date: ______________", 12, 5, false,styles.get("no_border"), 0));
				lot.addRow(new_row);
				
				rowNum++;	// blank
				
				LotRow heading_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Heading row
				heading_row.addCell(new LotCell("#", heading_row.colNum++, 1, false,styles.get("data_header"), 8 * 256));
				heading_row.addCell(new LotCell("Indicator", heading_row.colNum++, 1, false,styles.get("data_header"), 40 * 256));
				heading_row.addCell(new LotCell("Correct Response Key", heading_row.colNum++, 1, false,styles.get("data_header"), 15 * 256));
				lot.addRow(heading_row);
				
				for(LQASGroup group : lqasOld.groups) {
					new_row = new LotRow(rowNum++, false, true, null, null, false, false);
					new_row.addCell(new LotCell(group.ident, 0, 1, false, styles.get("group"), 0));
					lot.addRow(new_row);
					
					for(LQASItem item : group.items) {
						// Add the source data if required
						if(showSources && item.sourceColumns != null) {
							//for(int i = 0; i < item.sourceColumns.length; i++ ) {
							for(String sourceColumn : item.sourceColumns) {
								
								// Add the raw source columns
								for(int j = 0; j < lqasOld.dataItems.size(); j++) {
									LQASdataItemOld di = lqasOld.dataItems.get(j);
									if(di.ident.equals(sourceColumn)) {
										if(di.sourceColumns != null) {
											for(int k = 0; k < di.sourceColumns.length; k++) {
												new_row = new LotRow(rowNum++, true, false, di.sourceColumns[k], null, true, true);
												new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												new_row.addCell(new LotCell(di.sourceColumns[k], new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												lot.addRow(new_row);
												new_row.formulaStart = new_row.colNum;
											}
										}
										break;
									}
									
								}
								new_row = new LotRow(rowNum++, true, false, "#{" + sourceColumn + "}", null, true, false);
								new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("source"), 0));
								new_row.addCell(new LotCell(sourceColumn, new_row.colNum++, 1, false, styles.get("source"), 0));
								new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("source"), 0));
								lot.addRow(new_row);
								new_row.formulaStart = new_row.colNum;
							}
						}
						new_row = new LotRow(rowNum++, true, false, "", item.correctRespValue, false, false);
						new_row.addCell(new LotCell(item.ident, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.desc, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.correctRespText, new_row.colNum++, 1, false, styles.get("default"), 0));
						lot.addRow(new_row);
						new_row.formulaStart = new_row.colNum;
					}
				}
				
				rowNum++;	// blank
				
				LotRow f1_row = new LotRow(rowNum++, false, true, null, null, false, false);	// Footer row
				f1_row.addCell(new LotCell("The eight food groups are: 1. milk other than breast milk, cheese or yogurt (Q9 L); 2. foods made from grains, roots, and tubers, including porridge, fortified baby food from grains (Q9 A or C); 3. vitamin A-rich fruits and vegetables (and red palm oil) (Q9 B, D, E or P); 4. other fruits and vegetables (Q9 F); 5. eggs (Q9 I); 6. meat, poultry, fish, and shellfish (and organ meats) (Q9 G, H, J or O); 7. legumes and nuts (Q.9 K); 8. foods made with oil, fat, butter (Q9M). ", 
						0, 1, false, styles.get("no_border"), 0));
				lot.addRow(f1_row);
				
				if(showSources) {
					rowNum++;
					LotRow k0_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k0_row.addCell(new LotCell("Key", 0, 1, false, styles.get("data_header"), 0));
					lot.addRow(k0_row);
					
					LotRow k1_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k1_row.addCell(new LotCell("Calculated Data", 0, 1, false, styles.get("source"), 0));
					lot.addRow(k1_row);
					
					LotRow k2_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k2_row.addCell(new LotCell("Collected data", 0, 1, false, styles.get("raw_source"), 0));
					lot.addRow(k2_row);
				}
							
				/*
				 * Get the data for this lot
				 */
				pstmt.setString(1, lot.name);
				log.info("Get LQAS data: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				int index = 0;
				while(rs.next()) {
					
					/*
					 * Set the evaluation parameters
					 */
					for(LQASdataItemOld dataItem : lqasOld.dataItems) {
						String v = rs.getString(dataItem.ident);
						if(dataItem.isString) {
							eval.putVariable(dataItem.ident, "'" + v + "'");
						} else {
							eval.putVariable(dataItem.ident, v);
						}
					}
					
					
					index++;
					
					for(LotRow row : lot.rows) {
						if(row.dataRow) {
							
							String value = null;
							
							if(!row.sourceRow) {
								value = eval.evaluate(row.correctRespValue);
								if(value == null || value.trim().length() == 0) {
									value = "X";
								}
								if(row.correctRespValue != null && 
										!row.correctRespValue.equals("#") &&
										!(row.correctRespValue.trim().length() == 0)) {
									
									if(value.endsWith(".0")) {
										value = value.substring(0, value.length() - 2);
									} else {
										// Strip quotes from value
										if(value.charAt(0) == '\'') {
											value = value.substring(1);
										}
										if(value.charAt(value.length() - 1) == '\'') {
											value = value.substring(0, value.length() - 1);
										}
									}
									
								} 
							} else if(!row.rawSource) {
								value = eval.evaluate(row.colName);
								if(value.endsWith(".0")) {
									value = value.substring(0, value.length() - 2);
								} else {
									// Strip quotes from value
									if(value.charAt(0) == '\'') {
										value = value.substring(1);
									}
									if(value.charAt(value.length() - 1) == '\'') {
										value = value.substring(0, value.length() - 1);
									}
								}
							} else {
								value = rs.getString(row.colName);
							}
							
							if(value == null || value.equals("null")) {
								value = "X";
							}
							
							if(heading_row.colNum == row.colNum) {
								heading_row.addCell(new LotCell(String.valueOf(index), heading_row.colNum++, 1, false, styles.get("data_header"), 256 *3));
							}
	
							row.addCell(new LotCell(value, row.colNum++, 1, false, row.sourceRow ? styles.get("source") : styles.get("data"), 0));
							row.formulaEnd = row.colNum - 1;
							
						}
					}
					
				}
				
				/*
				 * Add the calculations at the end of each data row
				 */
				heading_row.addCell(new LotCell("Total Correct", heading_row.colNum++, 1, false, styles.get("default"), 0));
				heading_row.addCell(new LotCell("Total Sample Size (All 1's and 0's)", heading_row.colNum++, 1, false, styles.get("default"), 0));
				for(LotRow row : lot.rows) {
					if(row.dataRow) {
						String value = row.getTotalCorrectFormula();
						row.addCell(new LotCell(value, row.colNum++, 1, true, styles.get("default"), 0));
						
						value = row.getSampleSizeFormula();
						row.addCell(new LotCell(value, row.colNum++, 1, true, styles.get("default"), 0));
					}
				}
				
				/*
				 * Set the width of group rows
				 */
				for(LotRow row : lot.rows) {
					if(row.groupRow) {
						row.setCellMerge(0, heading_row.colNum);
					}
				}
				
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		
		/*
		 * Write the cells in each Lot to its work sheet
		 */
		for(Lot lot : lots) {
			lot.writeToWorkSheet();
		}
		
		// Write the workbook to the output stream
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Create an LQAS spreadsheet report
	 */
	public void createLQASForm(Connection sd, Connection cResults, OutputStream outputStream, 
			org.smap.sdal.model.Survey survey, 
			LQAS lqas,
			boolean showSources) throws Exception {
		
		Sheet errorSheet = null;
		int errorRow = 1;
		
		// Get the table name
		String tableName = null;
		boolean templateError = false;
		try {
			tableName = GeneralUtilityMethods.getTableForQuestion(sd, survey.id, lqas.lot);
		} catch (Exception e) {
			errorSheet = writeErrorToWorkSheet(errorSheet, e.getMessage(), errorRow++);
			wb.write(outputStream);
			outputStream.close();
			templateError = true;
			log.log(Level.SEVERE, "Exception", e);
		}
		if(templateError) {
			return;
		}
		
		// Create a work sheet for each lot
		ArrayList<Lot> lots = getLots(cResults, tableName, lqas.lot );
		LotSummary sum = new LotSummary(wb);
		PreparedStatement pstmt = null;
		
		// Create styles
		Map<String, CellStyle> styles = createStyles(wb);
		HashMap<String, String> columnNames = new HashMap<String, String> ();		// Keep track of duplicate column names
		
		try {
			
			Evaluator eval = new Evaluator();
			
			/*
			 * Create the Data Query
			 */
			StringBuffer sbSql = new StringBuffer("select ");
			boolean gotOne = false;
			for(LQASdataItem dataItem : lqas.dataItems) {
				if(gotOne) {
					sbSql.append(",");
				}
				sbSql.append(dataItem.select.sql);
				sbSql.append(" as ");
				sbSql.append(dataItem.ident);
				
				// If show sources was selected then add the source columns
				if(showSources && dataItem.select.columns != null) {
					//for(int i = 0; i < dataItem.sourceColumns.length; i++ ) {
					for(String col : dataItem.select.columns){
						if(columnNames.get(col) == null) {
							sbSql.append(",");
							sbSql.append(col);
							columnNames.put(col, col);
						}
					}
				}
				
				gotOne = true;
			}
			
			sbSql.append(" from ");
			sbSql.append(tableName);
			sbSql.append(" where ");
			sbSql.append(lqas.lot);
			sbSql.append(" = ? order by prikey asc;");

			/*
			 * Populate the worksheets
			 */
			for(Lot lot : lots) {			
				
				int idx = 1;
				pstmt = cResults.prepareStatement(sbSql.toString());
				for(LQASdataItem dataItem : lqas.dataItems) {
					if(dataItem.select.params.size() > 0) {
						for(int i = 0; i < dataItem.select.params.size(); i++) {
							pstmt.setString(idx++, dataItem.select.params.get(i).sValue);
						}
					}
				}
				pstmt.setString(idx++, lot.name);
				
				/*
				 * Add rows to each Lot and fill in the data independent columns
				 */
				int rowNum = 3;		// Arbitrarily start from 4th row as per example LQAS output
				
				LotRow new_row = new LotRow(rowNum++, false, false, null, null, false, false);			// Title
				new_row.addCell(new LotCell(survey.displayName, 2, 10, false, styles.get("title"), 0));
				lot.addRow(new_row);
				
				new_row = new LotRow(rowNum++, false, false, null, null, false, false);			// Signature and date
				new_row.addCell(new LotCell("Survey Team Supervisor: ______________", 5, 5, false,styles.get("no_border"), 0));
				new_row.addCell(new LotCell("Date: ______________", 12, 5, false,styles.get("no_border"), 0));
				lot.addRow(new_row);
				
				rowNum++;	// blank
				
				LotRow heading_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Heading row
				heading_row.addCell(new LotCell("#", heading_row.colNum++, 1, false,styles.get("data_header"), 8 * 256));
				heading_row.addCell(new LotCell("Indicator", heading_row.colNum++, 1, false,styles.get("data_header"), 40 * 256));
				heading_row.addCell(new LotCell("Correct Response Key", heading_row.colNum++, 1, false,styles.get("data_header"), 15 * 256));
				lot.addRow(heading_row);
				
				for(LQASGroup group : lqas.groups) {
					new_row = new LotRow(rowNum++, false, true, null, null, false, false);
					new_row.addCell(new LotCell(group.ident, 0, 1, false, styles.get("group"), 0));
					lot.addRow(new_row);
					
					for(LQASItem item : group.items) {
						// Add the source data if required
						if(showSources && item.sourceColumns != null) {
							//for(int i = 0; i < item.sourceColumns.length; i++ ) {
							for(String sourceColumn : item.sourceColumns) {
								
								// Add the raw source columns
								for(int j = 0; j < lqas.dataItems.size(); j++) {
									LQASdataItem di = lqas.dataItems.get(j);
									if(di.ident.equals(sourceColumn)) {
										if(di.select.columns != null) {
											//for(int k = 0; k < di.sourceColumns.length; k++) {
											for(String col : di.select.columns) {
												new_row = new LotRow(rowNum++, true, false, col, null, true, true);
												new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												new_row.addCell(new LotCell(col, new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("raw_source"), 0));
												lot.addRow(new_row);
												new_row.formulaStart = new_row.colNum;
											}
										}
										break;
									}
									
								}
								new_row = new LotRow(rowNum++, true, false, "#{" + sourceColumn + "}", null, true, false);
								new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("source"), 0));
								new_row.addCell(new LotCell(sourceColumn, new_row.colNum++, 1, false, styles.get("source"), 0));
								new_row.addCell(new LotCell("", new_row.colNum++, 1, false, styles.get("source"), 0));
								lot.addRow(new_row);
								new_row.formulaStart = new_row.colNum;
							}
						}
						new_row = new LotRow(rowNum++, true, false, "", item.correctRespValue, false, false);
						new_row.addCell(new LotCell(item.ident, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.desc, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.correctRespText, new_row.colNum++, 1, false, styles.get("default"), 0));
						lot.addRow(new_row);
						new_row.formulaStart = new_row.colNum;
					}
				}
				
				if(lqas.footer != null) {
					rowNum++;	// blank
				
					LotRow f1_row = new LotRow(rowNum++, false, true, null, null, false, false);	// Footer row
					f1_row.addCell(new LotCell( lqas.footer.desc, 0, 1, false, styles.get("no_border"), 0));
					lot.addRow(f1_row);
				}
				
				if(showSources) {
					rowNum++;
					LotRow k0_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k0_row.addCell(new LotCell("Key", 0, 1, false, styles.get("data_header"), 0));
					lot.addRow(k0_row);
					
					LotRow k1_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k1_row.addCell(new LotCell("Calculated Data", 0, 1, false, styles.get("source"), 0));
					lot.addRow(k1_row);
					
					LotRow k2_row = new LotRow(rowNum++, false, false, null, null, false, false);	// Key sources
					k2_row.addCell(new LotCell("Collected data", 0, 1, false, styles.get("raw_source"), 0));
					lot.addRow(k2_row);
				}
							
				/*
				 * Get the data for this lot
				 */
				log.info("Get LQAS data: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				int index = 0;
				while(rs.next()) {
					
					/*
					 * Set the evaluation parameters
					 */
					for(LQASdataItem dataItem : lqas.dataItems) {
						String v = rs.getString(dataItem.ident);
						if(dataItem.isString) {
							eval.putVariable(dataItem.ident, "'" + v + "'");
						} else {
							eval.putVariable(dataItem.ident, v);
						}
					}
					
					
					index++;
					
					for(LotRow row : lot.rows) {
						if(row.dataRow) {
							
							String value = null;
							
							if(!row.sourceRow) {
								try {
									value = eval.evaluate(row.correctRespValue);
								} catch (Exception e) {
									String msg = "Invalid expression: " + row.correctRespValue + " on row " + row.rowNum + " " + row.colName;
									errorSheet = writeErrorToWorkSheet(errorSheet, msg, errorRow++);	
									log.log(Level.SEVERE, msg, e);
								}
								if(value == null || value.trim().length() == 0) {
									value = "X";
								}
								if(row.correctRespValue != null && 
										!row.correctRespValue.equals("#") &&
										!(row.correctRespValue.trim().length() == 0)) {
									
									if(value.endsWith(".0")) {
										value = value.substring(0, value.length() - 2);
									} else {
										// Strip quotes from value
										if(value.charAt(0) == '\'') {
											value = value.substring(1);
										}
										if(value.charAt(value.length() - 1) == '\'') {
											value = value.substring(0, value.length() - 1);
										}
									}
									
								} 
							} else if(!row.rawSource) {
								value = eval.evaluate(row.colName);
								if(value.endsWith(".0")) {
									value = value.substring(0, value.length() - 2);
								} else {
									// Strip quotes from value
									if(value.charAt(0) == '\'') {
										value = value.substring(1);
									}
									if(value.charAt(value.length() - 1) == '\'') {
										value = value.substring(0, value.length() - 1);
									}
								}
							} else {
								value = rs.getString(row.colName);
							}
							
							if(value == null || value.equals("null")) {
								value = "X";
							}
							
							if(heading_row.colNum == row.colNum) {
								heading_row.addCell(new LotCell(String.valueOf(index), heading_row.colNum++, 1, false, styles.get("data_header"), 256 *3));
							}
	
							row.addCell(new LotCell(value, row.colNum++, 1, false, row.sourceRow ? styles.get("source") : styles.get("data"), 0));
							row.formulaEnd = row.colNum - 1;
							
						}
					}
					
				}
				
				/*
				 * Add the calculations at the end of each data row
				 */
				heading_row.addCell(new LotCell("Total Correct", heading_row.colNum++, 1, false, styles.get("default"), 0));
				heading_row.addCell(new LotCell("Total Sample Size (All 1's and 0's)", heading_row.colNum++, 1, false, styles.get("default"), 0));
				for(LotRow row : lot.rows) {
					if(row.dataRow) {
						String value = row.getTotalCorrectFormula();
						row.addCell(new LotCell(value, row.colNum++, 1, true, styles.get("default"), 0));
						
						value = row.getSampleSizeFormula();
						row.addCell(new LotCell(value, row.colNum++, 1, true, styles.get("default"), 0));
					}
				}
				
				/*
				 * Set the width of group rows
				 */
				for(LotRow row : lot.rows) {
					if(row.groupRow) {
						row.setCellMerge(0, heading_row.colNum);
					}
				}
				
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		
		/*
		 * Write the cells in each Lot to its work sheet
		 */
		for(Lot lot : lots) {
			lot.writeToWorkSheet();
		}
		
		// Write the workbook to the output stream
		wb.write(outputStream);
		outputStream.close();
	}
    
    private ArrayList<Lot> getLots(Connection cResults, String tableName, String lot_column_name) throws SQLException {
    	
    	ArrayList<Lot> lots = new ArrayList<Lot> ();
    	
    	String sql = "select distinct " + lot_column_name + " from " + 
    				tableName + " order by " + lot_column_name + " asc";
    	
    	PreparedStatement pstmt = null;
    	
    	try {
    		pstmt = cResults.prepareStatement(sql);
    		
    		log.info("Getting lots: " + pstmt.toString());
    		ResultSet rs = pstmt.executeQuery();
    		while(rs.next()) {
    			lots.add(new Lot(rs.getString(1), wb));
    		}
    		
    	} finally {
    		try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
    	}
    	
    	return lots;
    }
    
    /**
     * create a library of cell styles
     */
    private Map<String, CellStyle> createStyles(Workbook wb){
        
    	Map<String, CellStyle> styles = new HashMap<String, CellStyle>();

    	/*
    	 * Create fonts
    	 */
        Font largeFont = wb.createFont();
        largeFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        largeFont.setFontHeightInPoints((short) 14);
        
        Font boldFont = wb.createFont();
        boldFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        
        /*
         * Create styles
         */
        CellStyle style = wb.createCellStyle();
        style.setFont(largeFont);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        styles.put("title", style);

        style = wb.createCellStyle();	
        style.setWrapText(true);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        styles.put("no_border", style);
        
        // Remaining styles are all derived from a common base style
        style = getBaseStyle();
        style = wb.createCellStyle();
        style.setFillForegroundColor(HSSFColor.GREY_25_PERCENT.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setFont(boldFont);
        styles.put("group", style);
        
        style = getBaseStyle();
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("data", style);
        
        style = getBaseStyle();
        style.setFillForegroundColor(HSSFColor.LIGHT_YELLOW.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("source", style);
        
        style = getBaseStyle();
        style.setFillForegroundColor(HSSFColor.LAVENDER.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("raw_source", style);
        
        style = getBaseStyle();
        style.setAlignment(CellStyle.ALIGN_CENTER);
        style.setFont(boldFont);  
        styles.put("data_header", style);
        
        style = getBaseStyle();
        styles.put("default", style);
        

        return styles;
    }
    
    private CellStyle getBaseStyle() {
    	
    	CellStyle style = wb.createCellStyle();
    	
        style.setWrapText(true);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        style.setBorderBottom(HSSFCellStyle.BORDER_THIN);
        style.setBorderTop(HSSFCellStyle.BORDER_THIN);
        style.setBorderRight(HSSFCellStyle.BORDER_THIN);
        style.setBorderLeft(HSSFCellStyle.BORDER_THIN);
        
        return style;
    }
    
    private Sheet writeErrorToWorkSheet(Sheet errorSheet, String msg, int rowNumber) {
		if(errorSheet == null) {
			errorSheet = wb.createSheet("error");
		}
		Row r = errorSheet.createRow(rowNumber);
		Cell c = r.createCell(1);
		c.setCellValue("Error:");
		c = r.createCell(2);
		c.setCellValue(msg);
		
		return errorSheet;
    }

}
