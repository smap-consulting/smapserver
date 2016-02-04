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
	
	public void createLQASForm(Connection sd, Connection cResults, OutputStream outputStream, org.smap.sdal.model.Survey survey, LQAS lqas) throws Exception {
		
		// Get the table name
		String tableName = GeneralUtilityMethods.getTableForQuestion(sd, survey.id, lqas.lot);
		
		// Create a work sheet for each lot
		ArrayList<Lot> lots = getLots(cResults, tableName, lqas.lot );
		LotSummary sum = new LotSummary(wb);
		PreparedStatement pstmt = null;
		
		try {
			
			/*
			 * Create the Data Query
			 */
			StringBuffer sbSql = new StringBuffer("select ");
			for(LQASGroup group : lqas.groups) {
				boolean gotOne = false;
				for(LQASItem item : group.items) {
					if(gotOne) {
						sbSql.append(",");
					}
					sbSql.append(item.col_name);
					gotOne = true;
				}
				sbSql.append(" from ");
				sbSql.append(tableName);
				sbSql.append(" where ");
				sbSql.append(lqas.lot);
				sbSql.append(" = ? order by prikey asc;");
			}
			pstmt = cResults.prepareStatement(sbSql.toString());
		
			/*
			 * Populate the worksheets
			 */
			LotRow row = null;
			for(Lot lot : lots) {			
				
				/*
				 * Add rows to each Lot and fill in the data independent columns
				 */
				int rowNum = 3;		// Arbitrarily start from 4th row as per example LQAS output
				
				row = new LotRow(rowNum++, false, false, null, null);			// Title
				row.addCell(new LotCell(survey.displayName, 2, 10, false));
				lot.addRow(row);
				
				for(LQASGroup group : lqas.groups) {
					row = new LotRow(rowNum++, false, true, null, null);
					row.addCell(new LotCell(group.ident, 0, 1, false));
					lot.addRow(row);
					
					for(LQASItem item : group.items) {
						row = new LotRow(rowNum++, true, false, item.col_name, item.correctRespValue);
						row.addCell(new LotCell(item.ident, row.colNum++, 1, false));
						row.addCell(new LotCell(item.desc, row.colNum++, 1, false));
						row.addCell(new LotCell(item.correctRespText, row.colNum++, 1, false));
						lot.addRow(row);
						row.formulaStart = row.colNum;
					}
				}
				
							
				/*
				 * Get the data for this lot
				 */
				pstmt.setString(1, lot.name);
				
				log.info("Get LQAS data: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				while(rs.next()) {
					for(LotRow lot_row : lot.rows) {
						if(lot_row.dataRow) {
							String value = rs.getString(row.colName);
							if(value == null || value.trim().length() == 0) {
								value = "X";
							}
							if(row.correctRespValue != null) {
								value = value.toLowerCase().trim().equals(row.correctRespValue) ? "1" : "0";
							}
							row.addCell(new LotCell(value, lot_row.colNum++, 1, false));
						}
						row.formulaEnd = row.colNum - 1;
					}
					
				}
				
				/*
				 * Add the calculations at the end of each data row
				 */
				for(LotRow lot_row : lot.rows) {
					if(lot_row.dataRow) {
						String value = lot_row.getTotalCorrectFormula();
						row.addCell(new LotCell(value, lot_row.colNum++, 1, true));
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

}
