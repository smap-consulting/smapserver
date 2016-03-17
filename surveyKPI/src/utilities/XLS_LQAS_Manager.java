package utilities;

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
import java.util.logging.Logger;

import model.Lot;
import model.LotCell;
import model.LotRow;
import model.LotSummary;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.LQASGroup;
import org.smap.sdal.model.LQASItem;

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
			LQAS lqas,
			boolean showSources) throws Exception {
		
		// Get the table name
		String tableName = GeneralUtilityMethods.getTableForQuestion(sd, survey.id, lqas.lot);
		
		// Create a work sheet for each lot
		ArrayList<Lot> lots = getLots(cResults, tableName, lqas.lot );
		LotSummary sum = new LotSummary(wb);
		PreparedStatement pstmt = null;
		
		// Create styles
		Map<String, CellStyle> styles = createStyles(wb);
		
		try {
			
			/*
			 * Create the Data Query
			 */
			StringBuffer sbSql = new StringBuffer("select ");
			boolean gotOne = false;
			for(LQASGroup group : lqas.groups) {
				for(LQASItem item : group.items) {
					if(gotOne) {
						sbSql.append(",");
					}
					sbSql.append(item.select);
					sbSql.append(" as ");
					sbSql.append(item.col_name);
					gotOne = true;
				}
			}
			sbSql.append(" from ");
			sbSql.append(tableName);
			sbSql.append(" where ");
			sbSql.append(lqas.lot);
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
				
				LotRow new_row = new LotRow(rowNum++, false, false, null, null);			// Title
				new_row.addCell(new LotCell(survey.displayName, 2, 10, false, styles.get("title"), 0));
				lot.addRow(new_row);
				
				new_row = new LotRow(rowNum++, false, false, null, null);			// Signature and date
				new_row.addCell(new LotCell("Survey Team Supervisor: ______________", 5, 5, false,styles.get("default"), 0));
				new_row.addCell(new LotCell("Date: ______________", 12, 5, false,styles.get("default"), 0));
				lot.addRow(new_row);
				
				rowNum++;	// blank
				
				LotRow heading_row = new LotRow(rowNum++, false, false, null, null);	// Heading row
				heading_row.addCell(new LotCell("#", heading_row.colNum++, 1, false,styles.get("data_header"), 8 * 256));
				heading_row.addCell(new LotCell("Indicator", heading_row.colNum++, 1, false,styles.get("data_header"), 40 * 256));
				heading_row.addCell(new LotCell("Correct Response Key", heading_row.colNum++, 1, false,styles.get("data_header"), 15 * 256));
				lot.addRow(heading_row);
				
				for(LQASGroup group : lqas.groups) {
					new_row = new LotRow(rowNum++, false, true, null, null);
					new_row.addCell(new LotCell(group.ident, 0, 1, false, styles.get("group"), 0));
					lot.addRow(new_row);
					
					for(LQASItem item : group.items) {
						new_row = new LotRow(rowNum++, true, false, item.col_name, item.correctRespValue);
						new_row.addCell(new LotCell(item.ident, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.desc, new_row.colNum++, 1, false, styles.get("default"), 0));
						new_row.addCell(new LotCell(item.correctRespText, new_row.colNum++, 1, false, styles.get("default"), 0));
						lot.addRow(new_row);
						new_row.formulaStart = new_row.colNum;
					}
				}
				
							
				/*
				 * Get the data for this lot
				 */
				pstmt.setString(1, lot.name);
				log.info("Get LQAS data: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				int index = 0;
				while(rs.next()) {
					
					index++;
					
					for(LotRow row : lot.rows) {
						if(row.dataRow) {
							
							String value = rs.getString(row.colName);
							if(value == null || value.trim().length() == 0) {
								value = "X";
							}
							if(row.correctRespValue != null && 
									!row.correctRespValue.equals("#") &&
									!(row.correctRespValue.trim().length() == 0)) {
								
								System.out.println("Row: " + row.colName + " : " + value + " : "  + row.correctRespValue);
								value = value.toLowerCase().trim().equals(row.correctRespValue) ? "1" : "0";
							}
							if(heading_row.colNum == row.colNum) {
								heading_row.addCell(new LotCell(String.valueOf(index), heading_row.colNum++, 1, false, styles.get("data_header"), 256 *3));
							}
							row.addCell(new LotCell(value, row.colNum++, 1, false, styles.get("data"), 0));
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
    private static Map<String, CellStyle> createStyles(Workbook wb){
        
    	Map<String, CellStyle> styles = new HashMap<String, CellStyle>();

        Font largeFont = wb.createFont();
        largeFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        largeFont.setFontHeightInPoints((short) 14);
        
        Font boldFont = wb.createFont();
        boldFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        
        CellStyle style = wb.createCellStyle();
        style.setFont(largeFont);
        styles.put("title", style);

        style = wb.createCellStyle();
        style.setWrapText(true);
       
        style.setFillForegroundColor(HSSFColor.GREY_25_PERCENT.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setFont(boldFont);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        styles.put("group", style);
        
        style = wb.createCellStyle();
        style.setAlignment(CellStyle.ALIGN_CENTER);
        style.setWrapText(true);
        styles.put("data", style);
        
        style = wb.createCellStyle();
        style.setAlignment(CellStyle.ALIGN_CENTER);
        style.setWrapText(true);
        style.setFont(boldFont);
        styles.put("data_header", style);
        
        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("default", style);
        

        return styles;
    }

}
