package utilities;

import java.io.File;
import java.io.FileOutputStream;

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
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.ExchangeFile;
import org.smap.sdal.model.TableColumn;
import surveyKPI.ExportSurveyXls;

public class ExchangeManager {
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyXls.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	Workbook wb = null;
	boolean isXLSX = false;
	String basePath = null;

	private class Column {
		String humanName;
		
		Column(String h) {
			humanName = h;
		}
	}
	
	private class FormDesc {
		int f_id;
		String name;
		int parent;
		String table_name;
		String columns = null;
		Boolean visible = true;					// Set to false if the data from this form should not be included in the export
		ArrayList<FormDesc> children = null;
		ArrayList<TableColumn> columnList = null;
		

		
	}
	
	public ExchangeManager() {
	}
	
	HashMap<String, String> surveyNames = null;
	
	public ArrayList<ExchangeFile> createExchangeFiles(
			Connection sd, 
			Connection connectionResults,
			String user,
			int sId,
			HttpServletRequest request,
			String dirPath,
			boolean superUser) throws Exception {
		
		wb = new SXSSFWorkbook(10);
		Sheet sheet = null;
		ArrayList<ExchangeFile> files = new ArrayList<ExchangeFile> ();
			
		String filename = "data.xlsx";
		String filePath = dirPath + "/" + filename;
		OutputStream outputStream = new FileOutputStream(dirPath + "/data.xlsx");
		files.add(new ExchangeFile(filename, filePath));
		
		HashMap<String, String> selMultChoiceNames = new HashMap<String, String> ();

		Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);
		surveyNames = new HashMap<String, String> ();
		
		String dateName = null;
		int dateForm = 0;
		if(sId != 0) {
			
			PreparedStatement pstmt2 = null;
			PreparedStatement pstmtSSC = null;
			PreparedStatement pstmtQType = null;
			PreparedStatement pstmtDateFilter = null;

			try {
				
				basePath = GeneralUtilityMethods.getBasePath(request);
				
				// Prepare statement to get server side includes
				String sqlSSC = "select ssc.name, ssc.function, ssc.type, ssc.units from ssc ssc, form f " +
						" where f.f_id = ssc.f_id " +
						" and f.table_name = ? " +
						" order by ssc.id;";
				pstmtSSC = sd.prepareStatement(sqlSSC);
				
				// Prepare the statement to get the question type and read only attribute
				String sqlQType = "select q.qtype, q.readonly from question q, form f " +
						" where q.f_id = f.f_id " +
						" and f.table_name = ? " +
						" and q.qname = ?;";
				pstmtQType = sd.prepareStatement(sqlQType);
				
				HashMap<String, FormDesc> forms = new HashMap<String, FormDesc> ();			// A description of each form in the survey
				ArrayList <FormDesc> formList = new ArrayList<FormDesc> ();					// A list of all the forms
				FormDesc topForm = null;	
				
				
				/*
				 * Get the tables / forms in this survey 
				 */
				String sql = null;
				sql = "SELECT name, f_id, table_name, parentform FROM form" +
						" WHERE s_id = ? " +
						" ORDER BY f_id;";	

				PreparedStatement  pstmt = sd.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				ResultSet resultSet = pstmt.executeQuery();
				
				while (resultSet.next()) {

					FormDesc fd = new FormDesc();
					fd.name = resultSet.getString("name");
					fd.f_id = resultSet.getInt("f_id");
					fd.parent = resultSet.getInt("parentform");
					fd.table_name = resultSet.getString("table_name");
					forms.put(fd.name, fd);
					if(fd.parent == 0) {
						topForm = fd;
					}
				}
				
				/*
				 * Put the forms into a list in top down order
				 */
				formList.add(topForm);		// The top level form
				addChildren(topForm, forms, formList);
				
				/*
				 * Create a work sheet for each form
				 */
				for(FormDesc f : formList) {
					
					sheet = wb.createSheet("d_" + f.name);	
					
					TableColumn c;
					String geomType = null;
					int parentId = 0;
					if(f.parent > 0) {
						parentId = f.parent;
					}
					
					// Get the list of table columns
					f.columnList = GeneralUtilityMethods.getColumnsInForm(
							sd,
							connectionResults,
							sId,
							user,
							parentId,
							f.f_id,
							f.table_name,
							false,		// Don't include Read Only
							true,		// Include parent key
							false,		// Don't include "bad" columns
							false,		// Don't include instance id
							true,		// Include other meta data
							superUser,
							false);
						
					// Get the list of spreadsheet columns
					ArrayList<Column> cols = new ArrayList<Column> ();
					for(int j = 0; j < f.columnList.size(); j++) {

						c = f.columnList.get(j);
						String name = c.name;
						String qType = c.type;
						String humanName = c.humanName;
						
						addToHeader(sd, cols, "none", humanName, name, qType, false, sId, f,
										false);
								
						
						// Set the sql selection text for this column (Only need to do this once, not for every repeating record)	
						String selName = null;
						if(c.isGeometry()) {
							selName = "ST_AsTEXT(" + name + ") ";
							geomType = c.type;
						} else if(qType.equals("dateTime")) {	// Return all timestamps at UTC with no time zone
							selName = "timezone('UTC', " + name + ") as " + name;	
						} else {
							selName = name;
						}
						
						if(f.columns == null) {
							f.columns = selName;
						} else {
							f.columns += "," + selName;
						}
					}
					
					createHeader(cols, sheet, styles);
					
					getData(sd, 
							connectionResults, 
							formList, 
							f, 
							selMultChoiceNames,
							cols,
							sheet,
							styles,
							sId,
							null, 
							null, 
							dateName,
							dateForm,
							basePath,
							dirPath,
							files);
					
				}
 
			} finally {
				
				try {if (pstmt2 != null) {pstmt2.close();	}} catch (SQLException e) {	}
				try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
				try {if (pstmtQType != null) {pstmtQType.close();	}} catch (SQLException e) {	}
				try {if (pstmtDateFilter != null) {pstmtDateFilter.close();	}} catch (SQLException e) {	}
				
			}
		}
		
		wb.write(outputStream);
		outputStream.close();
		
		// XLSX temporary streaming files need to be deleted
		((SXSSFWorkbook) wb).dispose();
		
		return files;
	}
	
	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(
			ArrayList<Column> cols, 
			Sheet sheet, 
			Map<String, CellStyle> styles) {
				
		// Create survey sheet header row
		Row headerRow = sheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(col.humanName);
        }
	}
	
	/*
	 * Create a header row and set column widths
	 */
	private void writeValue(
			Row row,
			int idx,
			String value, 
			Sheet sheet, 
			Map<String, CellStyle> styles) throws IOException {
		
		CreationHelper createHelper = wb.getCreationHelper();
		
		
		CellStyle style = styles.get("default");
		
			
        Cell cell = row.createCell(idx);        
		cell.setCellStyle(style);
        
        cell.setCellValue(value);


	}
	
	/*
	 * Add the list of children to parent forms
	 */
	private void addChildren(FormDesc parentForm, HashMap<String, FormDesc> forms, ArrayList<FormDesc> formList) {
		
		for(FormDesc fd : forms.values()) {
			if(fd.parent != 0 && fd.parent == parentForm.f_id) {
				if(parentForm.children == null) {
					parentForm.children = new ArrayList<FormDesc> ();
				}
				parentForm.children.add(fd);
				formList.add(fd);
				addChildren(fd,  forms, formList);
			}
		}
		
	}
	
	/*
	 * Add to the header
	 */
	private void addToHeader(Connection sd, 
			ArrayList<Column> cols, 
			String language, 
			String human_name, 
			String colName, 
			String qType, 
			boolean split_locn, 
			int sId, 
			FormDesc f,
			boolean merge_select_multiple) throws SQLException {
		
		if(split_locn && qType != null && qType.equals("geopoint")) {
			cols.add(new Column("Latitude"));
			cols.add(new Column("Longitude"));
		} else {
			Column col = new Column(human_name);
			if(qType.equals("image")) {
			}
			cols.add(col);
			
		}
		
	}
	
	/*
	 * Return the text
	 */
	private ArrayList<String> getContent(Connection con, String value, boolean firstCol, String columnName,
			String columnType) throws NumberFormatException, SQLException {

		ArrayList<String> out = new ArrayList<String>();
		if(value == null) {
			value = "";
		}
		
		if(value.startsWith("POINT")) {

			String coords [] = getLonLat(value);

			if(coords.length > 1) {
				out.add(coords[1]);
				out.add(coords[0]); 
			} else {
				out.add(value);
				out.add(value);
			}
				
			
		} else if(value.startsWith("POLYGON") || value.startsWith("LINESTRING")) {
			
			// Can't split linestrings and polygons so just remove the POLYGON or LINESTRING wrapper
			int idx = value.lastIndexOf('(');
			int idx2 = value.indexOf(')');
			if(idx2 > idx && idx > -1) {
				out.add(value.substring(idx + 1, idx2));
			} else {
				out.add("");
			}

			
		} else if(columnName.equals("_device")) {
			out.add(value);				
		} else if(columnName.equals("_complete")) {
			out.add(value.equals("f") ? "No" : "Yes"); 
				
		} else if(columnName.equals("_s_id")) {
			String displayName = surveyNames.get(out);
			if(displayName == null) {
				try {
					displayName = GeneralUtilityMethods.getSurveyName(con, Integer.parseInt(value));
				} catch (Exception e) {
					displayName = "";
				}
				surveyNames.put(value, displayName);
			}
			out.add(displayName); 
				
		} else if(columnType.equals("dateTime")) {
			// Convert the timestamp to the excel format specified in the xl2 mso-format
			int idx1 = out.indexOf('.');	// Drop the milliseconds
			if(idx1 > 0) {
				value = value.substring(0, idx1);
			} 
			out.add(value);
		} else {
			out.add(value);
		}

		return out;
	}
	
	/*
	 * Write out the data
	 */
	private void getData(Connection sd, 
			Connection connectionResults, 
			ArrayList<FormDesc> formList, 
			FormDesc f,
			HashMap<String, String> choiceNames,
			ArrayList<Column> cols, 
			Sheet sheet, 
			Map<String, CellStyle> styles,
			int sId,
			Date startDate,
			Date endDate,
			String dateName,
			int dateForm,
			String basePath,
			String dirPath,
			ArrayList<ExchangeFile> files) throws Exception {
		
		StringBuffer sql = new StringBuffer();
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		
		/*
		 * Retrieve the data for this table
		 */
		sql.append("select ");
		sql.append(f.columns);
		sql.append(" from ");
		sql.append(f.table_name);
		sql.append(" where _bad is false order by prikey asc");		

		try {
			pstmt = connectionResults.prepareStatement(sql.toString());
			log.info("Get data: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			
			int rowIndex = 1;
			while (resultSet.next()) {	
				System.out.println("----------- Row: " + rowIndex);
				Row row = sheet.createRow(rowIndex);
				int colIndex = 0;	// Column index
				for(int i = 0; i < f.columnList.size(); i++) {
					
					System.out.println("      Col: " + colIndex);
					TableColumn c = f.columnList.get(i);

					String columnName = c.name;
					String columnType = c.type;
					String value = resultSet.getString(i + 1);
					
					if(value == null) {
						value = "";	
					}
					
					if(c.isAttachment()) {
						
						// Path to attachment
						String attachmentPath = basePath + "/" + value;
						
						// Get name
						int idx = value.lastIndexOf('/');
						if(idx > -1) {
							value = value.substring(idx + 1);
						}
						
						// Copy file to temporary zip folder
						File source = new File(attachmentPath);
						if (source.exists()) {
							String newPath = dirPath + "/" + value;
							File dest = new File(newPath);
							FileUtils.copyFile(source, dest);				
							files.add(new ExchangeFile(value, newPath));
						} else {
							log.info("Error: media file does not exist: " + attachmentPath);
						}
						
					}
					
					ArrayList<String> values = getContent(sd, value, false, columnName, columnType);
					for(int j = 0; j < values.size(); j++) {
						writeValue(row, colIndex++, values.get(j), sheet, styles);
					}
				}
				rowIndex++;

				
			}
			
		} finally {
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
	}
	
	/*
	 * Get the longitude and latitude from a WKT POINT
	 */
	private String [] getLonLat(String point) {
		String [] coords = null;
		int idx1 = point.indexOf("(");
		int idx2 = point.indexOf(")");
		if(idx2 > idx1) {
			String lonLat = point.substring(idx1 + 1, idx2);
			coords = lonLat.split(" ");
		}
		return coords;
	}
	
	/*
	 * 
	 */
	String updateMultipleChoiceValue(String dbValue, String choiceName, String currentValue) {
		boolean isSet = false;
		String newValue = currentValue;
		
		if(dbValue.equals("1")) {
			isSet = true;
		}
		
		if(isSet) {
			if(currentValue == null) {
				newValue = choiceName;
			} else {
				newValue += " " + choiceName;
			}
		}
		
		return newValue;
	}
	
 

}
