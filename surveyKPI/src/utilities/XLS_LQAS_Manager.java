package utilities;

import java.io.IOException;
import java.io.OutputStream;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.LQAS;

/*
 * Class to manage the creation of an LQAS workbook
 * Assume all the data is in a single table
 */
public class XLS_LQAS_Manager {
	
	Workbook wb = null;

	private class Lot{
		String name = null;
		Sheet sheet = null;
		
		public Lot(String n) {
			name = n;
			sheet = wb.createSheet("survey");
		}
	}
	
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
		
		// Create a work book for each lot
		ArrayList<Lot> lots = getLots(cResults, tableName, lqas.lot );
		
		wb.write(outputStream);
		outputStream.close();
	}
	

	
   /**
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
        styles.put("label", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin repeat", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_YELLOW.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin group", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("is_required", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("not_required", style);

        return styles;
    }
    
    private ArrayList<Lot> getLots(Connection cResults, String tableName, String lot_column_name) {
    	
    	ArrayList<Lot> lots = new ArrayList<Lot> ();
    	
    	PreparedStatement pstmt = null;
    	
    	try {
    	} finally {
    		try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
    	}
    	return lots;
    }

}
