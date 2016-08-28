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
import java.sql.Connection;
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

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFHyperlink;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.TableColumn;
import surveyKPI.ExportSurveyXls;

public class XLSResultsManager {
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyXls.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	Workbook wb = null;
	boolean isXLSX = false;
	int rowIndex = 0;		// Heading row is 0

	private class Column {
		String name;
		String label;
		String humanName;
		
		Column(String n, String l, String h) {
			name = n;
			label = l;
			humanName = h;
		}
	}
	
	private class RecordDesc {
		String prikey;
		String parkey;
		ArrayList<String> record;
	}
	
	private class FormDesc {
		String f_id;
		String parent;
		String table_name;
		String columns = null;
		String parkey = null;
		Integer maxRepeats;
		int columnCount = 0;					// Number of displayed columns for this form
		Boolean visible = true;					// Set to false if the data from this form should not be included in the export
		Boolean flat = false;					// Set to true if this forms data should be flattened so it appears on a single line
		ArrayList<RecordDesc> records = null;
		ArrayList<FormDesc> children = null;
		ArrayList<TableColumn> columnList = null;
		
		@SuppressWarnings("unused")
		public void debugForm() {
			System.out.println("Form=============");
			System.out.println("    f_id:" + f_id);
			System.out.println("    parent:" + parent);
			System.out.println("    table_name:" + table_name);
			System.out.println("    maxRepeats:" + maxRepeats);
			System.out.println("    visible:" + visible);
			System.out.println("    flat:" + flat);
			System.out.println("End Form=============");
		}
		
		public void clearRecords() {
			records = null;
				if(children != null) {
				for(int i = 0; i < children.size(); i++) {
					children.get(i).clearRecords();
				}
			}
		}
		
		public void addRecord(String prikey, String parkey, ArrayList<String> rec) {
			if(records == null) {
				records = new ArrayList<RecordDesc> ();
			}

			RecordDesc rd = new RecordDesc();
			rd.prikey = prikey;
			rd.parkey = parkey;
			rd.record = rec;
			records.add(rd);
		}
		
		// Used for debugging
		@SuppressWarnings("unused")
		public void printRecords(int spacing, boolean long_form) {
			String padding = "";
			for(int i = 0; i < spacing; i++) {
				padding += " ";
			}

			if(records != null) {		
				for(int i = 0; i < records.size(); i++) {
					if(long_form) {
						System.out.println(padding + f_id + ":  " + records.get(i).prikey + 
								" : " + records.get(i).record.toString() );
					} else { 
						System.out.println(padding + f_id + ":  " + records.get(i).record.toString().substring(0, 50));
					}
				}
			}
			
			if(long_form && children != null) {
				for(int i = 0; i < children.size(); i++) {
					children.get(i).printRecords(spacing + 4, long_form);
				}
			}
		}
	}
	
	public XLSResultsManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
			isXLSX = false;
		} else {
			wb = new XSSFWorkbook();
			isXLSX = true;
		}
	}
	
	HashMap<String, String> surveyNames = null;
	
	public void createXLS(
			Connection sd, 
			Connection connectionResults,
			int sId,
			int [] inc_id,
			boolean [] inc_flat,
			boolean exp_ro,
			boolean merge_select_multiple,
			String language,
			boolean split_locn,
			HttpServletRequest request,
			OutputStream outputStream) throws IOException {
		
		Sheet resultsSheet = wb.createSheet("survey");
		HashMap<String, String> selMultChoiceNames = new HashMap<String, String> ();
		HashMap<String, String> selectMultipleColumnNames = new HashMap<String, String> ();
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		

		Map<String, CellStyle> styles = createStyles(wb);
		surveyNames = new HashMap<String, String> ();
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		if(sId != 0) {
			
			PreparedStatement pstmt2 = null;
			PreparedStatement pstmtSSC = null;
			PreparedStatement pstmtQType = null;
			

			
			try {
				
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
				sql = "SELECT f_id, table_name, parentform FROM form" +
						" WHERE s_id = ? " +
						" ORDER BY f_id;";	

				PreparedStatement  pstmt = sd.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				ResultSet resultSet = pstmt.executeQuery();
				
				while (resultSet.next()) {

					FormDesc fd = new FormDesc();
					fd.f_id = resultSet.getString("f_id");
					fd.parent = resultSet.getString("parentform");
					fd.table_name = resultSet.getString("table_name");
					if(inc_id != null) {
						boolean showForm = false;
						boolean setFlat = false;
						int fId = Integer.parseInt(fd.f_id);
						for(int i = 0; i < inc_id.length; i++) {
							if(fId == inc_id[i]) {
								showForm = true;
								setFlat = inc_flat[i];
								break;
							}
						}
						fd.visible = showForm;
						fd.flat = setFlat;
					}
					forms.put(fd.f_id, fd);
					if(fd.parent == null || fd.parent.equals("0")) {
						topForm = fd;
					}
					// Get max records for flat export
					fd.maxRepeats = 1;	// Default
					if(fd.flat && fd.parent != null) {
						fd.maxRepeats = getMaxRepeats(sd, connectionResults, sId, Integer.parseInt(fd.f_id));
					}
				}
				
				/*
				 * Put the forms into a list in top down order
				 */
				formList.add(topForm);		// The top level form
				addChildren(topForm, forms, formList);
					

				if(topForm.visible) {
					cols.add(new Column("prikey", "", "Record"));
				}
				
				/*
				 * Add to each form description
				 *  1) The maximum number of repeats (if the form is to be flattened)
				 *  2) The columns that contain the data to be shown
				 */
				for(FormDesc f : formList) {
					TableColumn c;
					String geomType = null;
					int parentId = 0;
					if(f.parent != null) {
						parentId = Integer.parseInt(f.parent);
					}
					f.columnList = GeneralUtilityMethods.getColumnsInForm(
							sd,
							connectionResults,
							parentId,
							Integer.parseInt(f.f_id),
							f.table_name,
							exp_ro,
							false,		// Don't include parent key
							false,		// Don't include "bad" columns
							false,		// Don't include instance id
							true		// Include other meta data
							);
						
							
					for(int k = 0; k < f.maxRepeats; k++) {
						for(int j = 0; j < f.columnList.size(); j++) {

							c = f.columnList.get(j);
							String name = c.name;
							String qType = c.type;
							boolean ro = c.readonly;
							String humanName = c.humanName;
							
							boolean isAttachment = false;
							boolean isSelectMultiple = false;
							String selectMultipleQuestionName = null;
							String optionName = null;
								
							if(!exp_ro && ro) {
								continue;			// Drop read only columns if they are not selected to be exported				
							}
								
							if(qType.equals("image") || qType.equals("audio") || qType.equals("video")) {
								isAttachment = true;
							}

							if(qType.equals("select")) {
								isSelectMultiple = true;
								selectMultipleQuestionName = c.question_name;
								optionName = c.option_name;
							}
											
							if(isSelectMultiple && merge_select_multiple) {
								humanName = selectMultipleQuestionName;
								
								// Add the name of sql column to a look up table for the get data stage
								selMultChoiceNames.put(name, optionName);
							}
							
							if(qType.equals("dateTime")) {
								humanName += " (GMT)";
							}
							
							if(f.maxRepeats > 1) {	// Columns need to be repeated horizontally
								humanName += "(r " + (k + 1) + ")";
							}
							
							// If the user has requested that select multiples be merged then we only want the question added once
							boolean skipSelectMultipleOption = false;
							if(isSelectMultiple && merge_select_multiple) {
								String n = selectMultipleColumnNames.get(humanName);
								if(n != null) {
									skipSelectMultipleOption = true;
								} else {
									selectMultipleColumnNames.put(humanName, humanName);
								}
							}
							
							if(!name.equals("prikey") && !skipSelectMultipleOption) {	// Primary key is only added once for all the tables
								if(f.visible) {	// Add column headings if the form is visible
						
									addToHeader(sd, cols, language, humanName, name, qType, split_locn, sId, f,
											merge_select_multiple);
									
								}
							}
							
							// Set the sql selection text for this column (Only need to do this once, not for every repeating record)
							if(k == 0) {
								
								String selName = null;
								if(c.isGeometry()) {
									selName = "ST_AsTEXT(" + name + ") ";
									geomType = c.type;
								} else if(qType.equals("dateTime")) {	// Return all timestamps at UTC with no time zone
									selName = "timezone('UTC', " + name + ") as " + name;	
								} else {
									
									if(isAttachment) {
										selName = "'" + urlprefix + "' || " + name + " as " + name;
									} else {
										selName = name;
									}
									
								}
								
								if(f.columns == null) {
									f.columns = selName;
								} else {
									f.columns += "," + selName;
								}
								f.columnCount++;
							}
						}
						
						/*
						 * Add the server side calculations
						 */ 
						pstmtSSC.setString(1, f.table_name);
						log.info("sql: " + sqlSSC + " : " + f.table_name);
						resultSet = pstmtSSC.executeQuery();
						while(resultSet.next()) {
							String sscName = resultSet.getString(1);
							String sscFn = resultSet.getString(2);
							String sscType = resultSet.getString(3);
							String sscUnits = resultSet.getString(4);
							if(sscType == null) {
								sscType = "decimal";
							}

							String colName = sscName + " (" + sscUnits + ")";
							
							if(f.maxRepeats > 1) {	// Columns need to be repeated horizontally
								colName += "_" + (k + 1);
							}

							if(f.visible) {	// Add column headings if the form is visible
								
								addToHeader(sd, cols, language, colName, colName, sscType, split_locn, sId, f,
										merge_select_multiple);
				
							}
							
							
							// Set the sql selection text for this column (Only need to do this once, not for every repeating record)
							if(k == 0) {
								
								String selName = null;
								
								if(sscFn.equals("area")) {
									
									selName = "ST_Area(geography(the_geom), true)";
									if(sscUnits.equals("hectares")) {
										selName += " / 10000";
									}
									selName += " as \"" + colName + "\"";
									
								} else if (sscFn.equals("length")) {
									
									if(geomType.equals("geopolygon") || geomType.equals("geoshape")) {
										selName = "ST_Length(geography(the_geom), true)";
									} else {
										selName = "ST_Length(geography(the_geom), true)";
									}
									if(sscUnits.equals("km")) {
										selName += " / 1000";
									}
									selName += " as \"" + colName + "\"";
									
								} else {
									selName= sscName;
								}
								
								if(f.columns == null) {
									f.columns = selName;
								} else {
									f.columns += "," + selName;
								}
								
								TableColumn tc = new TableColumn();
								tc.name = selName;
								tc.humanName = selName;
								tc.type = sscType;
								f.columnList.add(tc);
								
								f.columnCount++;
							}

						}
					}

				}

				// Write out the column headings
				if(!language.equals("none")) {	// Add the questions / option labels if requested
					createHeader(cols, resultsSheet, styles, true);
				} 
				createHeader(cols, resultsSheet, styles, false);	// Add the names

				/*
				 * Add the data
				 */
				getData(sd, connectionResults, formList, topForm, split_locn, merge_select_multiple, selMultChoiceNames,
						cols,
						resultsSheet,
						styles);
	
			
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Exception", e);
			} finally {
				
				try {if (pstmt2 != null) {pstmt2.close();	}} catch (SQLException e) {	}
				try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
				try {if (pstmtQType != null) {pstmtQType.close();	}} catch (SQLException e) {	}
				
				SDDataSource.closeConnection("surveyKPI-ExportSurvey", sd);
				ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", connectionResults);
			}
		}
		
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(
			ArrayList<Column> cols, 
			Sheet sheet, 
			Map<String, CellStyle> styles, 
			boolean label) {
				
		// Create survey sheet header row
		Row headerRow = sheet.createRow(rowIndex++);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            if(label) {
            	cell.setCellValue(col.label);
            } else {
            	cell.setCellValue(col.humanName);
            }
        }
	}
	
	/*
	 * Create a header row and set column widths
	 */
	private void closeRecord(
			ArrayList<String> record, 
			Sheet sheet, 
			Map<String, CellStyle> styles) {
		
		CreationHelper createHelper = wb.getCreationHelper();
		
		Row row = sheet.createRow(rowIndex++);
		CellStyle style = styles.get("default");
		for(int i = 0; i < record.size(); i++) {
			String v = record.get(i);
			
            Cell cell = row.createCell(i);
            
            if(v != null && (v.startsWith("https://") || v.startsWith("http://"))) {
				cell.setCellStyle(styles.get("link"));
				if(isXLSX) {
					XSSFHyperlink url = (XSSFHyperlink)createHelper.createHyperlink(Hyperlink.LINK_URL);
					url.setAddress(v);
					cell.setHyperlink(url);
				} else {
					HSSFHyperlink url = new HSSFHyperlink(HSSFHyperlink.LINK_URL);
					url.setAddress(v);
					cell.setHyperlink(url);
				}
				
			} else {
				cell.setCellStyle(style);
			}
            
            cell.setCellValue(v);

        }
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
        styles.put("default", style);
        
		style = wb.createCellStyle();
		Font linkFont = wb.createFont();
		linkFont.setUnderline(Font.U_SINGLE);
	    linkFont.setColor(IndexedColors.BLUE.getIndex());
	    style.setFont(linkFont);
	    styles.put("link", style);
        

        return styles;
    }
    
	private int getMaxRepeats(Connection con, Connection results_con, int sId, int formId)  {
		int maxRepeats = 1;
		
		String sql = "SELECT table_name, parentform FROM form" +
				" WHERE s_id = ? " +
				" AND f_id = ?;";	
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetCount = null;
		try {
			pstmt = con.prepareStatement(sql);
			ArrayList<String> tables = new ArrayList<String> ();
			getTableHierarchy(pstmt, tables, sId, formId);
			int numTables = tables.size();
			
			StringBuffer sqlBuf = new StringBuffer();
			sqlBuf.append("select max(t.cnt)  from " +
					"(select count(");
			sqlBuf.append(tables.get(numTables - 1));
			sqlBuf.append(".prikey) cnt " +
					" from ");
			
			
			for(int i = 0; i < numTables; i++) {
				if(i > 0) {
					sqlBuf.append(",");
				}
				sqlBuf.append(tables.get(i));
			}
			
			// where clause
			sqlBuf.append(" where ");
			sqlBuf.append(tables.get(0));
			sqlBuf.append("._bad='false'");
			if(numTables > 1) {
				for(int i = 0; i < numTables - 1; i++) {
					
					sqlBuf.append(" and ");
					
					sqlBuf.append(tables.get(i));
					sqlBuf.append(".parkey = ");
					sqlBuf.append(tables.get(i+1));
					sqlBuf.append(".prikey");
				}
			}
			
			sqlBuf.append(" group by ");
			sqlBuf.append(tables.get(numTables - 1));
			sqlBuf.append(".prikey) AS t;");
			
			pstmtGetCount = results_con.prepareStatement(sqlBuf.toString());
			ResultSet rsCount = pstmtGetCount.executeQuery();
			if(rsCount.next()) {
				maxRepeats = rsCount.getInt(1);
			}
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetCount != null) {pstmtGetCount.close();	}} catch (SQLException e) {	}
		}
		return maxRepeats;
	}
	
	/*
	 * Add the list of children to parent forms
	 */
	private void addChildren(FormDesc parentForm, HashMap<String, FormDesc> forms, ArrayList<FormDesc> formList) {
		
		for(FormDesc fd : forms.values()) {
			if(fd.parent != null && fd.parent.equals(parentForm.f_id)) {
				if(parentForm.children == null) {
					parentForm.children = new ArrayList<FormDesc> ();
				}
				parentForm.children.add(fd);
				formList.add(fd);
				addChildren(fd,  forms, formList);
			}
		}
		
	}
	
	private void getTableHierarchy(PreparedStatement pstmt, ArrayList<String> tables, int sId, int formId) throws SQLException {
		
		pstmt.setInt(1, sId);
		pstmt.setInt(2, formId);
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			tables.add(rs.getString(1));
			getTableHierarchy(pstmt, tables, sId, rs.getInt(2));
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
		
		String label = null;
		if(!language.equals("none")) {
			label = getQuestion(sd, colName, sId, f, language, merge_select_multiple);
		}
		
		if(split_locn && qType != null && qType.equals("geopoint")) {
			cols.add(new Column("Latitude", "Latitude", "Latitude"));
			cols.add(new Column("Longitude", "Longitude", "Longitude"));
		} else {
			cols.add(new Column(colName, label, human_name));
		}
		
	}
	
	/*
	 * Return the text formatted for html or csv
	 */
	private ArrayList<String> getContent(Connection con, String value, boolean firstCol, String columnName,
			String columnType, boolean split_locn) throws NumberFormatException, SQLException {

		ArrayList<String> out = new ArrayList<String>();
		if(value == null) {
			value = "";
		}
		
		if(split_locn && value.startsWith("POINT")) {

			String coords [] = getLonLat(value);

			if(coords.length > 1) {
				out.add(coords[1]);
				out.add(coords[0]); 
			} else {
				out.add(value);
				out.add(value);
			}
				
			
		} else if(split_locn && (value.startsWith("POLYGON") || value.startsWith("LINESTRING"))) {
			
			// Can't split linestrings and polygons, leave latitude and longitude as blank
			out.add("");
			out.add("");
			
		} else if(value.startsWith("POINT")) {
			String coords [] = getLonLat(value);
			if(coords.length > 1) {
				out.add("http://www.openstreetmap.org/?mlat=" +
						coords[1] +
						"&mlon=" +
						coords[0] +
						"&zoom=14\">" +
						value);
			
			} else {
				out.add(value);
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
	 * For each record in the top level table all records in other tables that
	 * can link back to the top level record are retrieved.  These are then combined 
	 * to create a tree structure containing the output
	 * 
	 * The function is called recursively until the last table
	 */
	private void getData(Connection con, 
			Connection connectionResults, 
			ArrayList<FormDesc> formList, 
			FormDesc f,
			boolean split_locn, 
			boolean merge_select_multiple, 
			HashMap<String, String> choiceNames,
			ArrayList<Column> cols, 
			Sheet resultsSheet, 
			Map<String, CellStyle> styles) {
		
		String sql = null;
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		//ResultSetMetaData rsMetaData = null;
		
		/*
		 * Retrieve the data for this table
		 */
		sql = "SELECT " + f.columns + " FROM " + f.table_name +
				" WHERE _bad IS FALSE";		
		
		if(f.parkey != null && !f.parkey.equals("0")) {
			sql += " AND parkey=?";
		}
		sql += " ORDER BY prikey ASC;";	
		
		try {
			pstmt = connectionResults.prepareStatement(sql);
			if(f.parkey != null) {
				pstmt.setInt(1, Integer.parseInt(f.parkey));
			}
			log.info("Get data: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			
			while (resultSet.next()) {

				String prikey = resultSet.getString(1);
				ArrayList<String> record = new ArrayList<String>();
				
				// If this is the top level form reset the current parents and add the primary key
				if(f.parkey == null || f.parkey.equals("0")) {
					f.clearRecords();
					record.addAll(getContent(con, prikey, true, "prikey", "key", split_locn));
				}
				
				
				// Add the other questions to the output record
				String currentSelectMultipleQuestionName = null;
				String multipleChoiceValue = null;
				for(int i = 1; i < f.columnList.size(); i++) {
					
					TableColumn c = f.columnList.get(i);

					String columnName = c.name;
					String columnType = c.type;
					String value = resultSet.getString(i + 1);
					
					if(value == null) {
						value = "";	
					}
					
					if(merge_select_multiple) {
						String choice = choiceNames.get(columnName);
						if(choice != null) {
							// Have to handle merge of select multiple
							String selectMultipleQuestionName = columnName.substring(0, columnName.indexOf("__"));
							if(currentSelectMultipleQuestionName == null) {
								currentSelectMultipleQuestionName = selectMultipleQuestionName;
								multipleChoiceValue = updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							} else if(currentSelectMultipleQuestionName.equals(selectMultipleQuestionName) && (i != f.columnList.size() - 1)) {
								// Continuing on with the same select multiple and its not the end of the record
								multipleChoiceValue = updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							} else if (i == f.columnList.size() - 1) {
								//  Its the end of the record		
								multipleChoiceValue = updateMultipleChoiceValue(value, choice, multipleChoiceValue);
								
								record.addAll(getContent(con, multipleChoiceValue, false, columnName, columnType, split_locn));
							} else {
								// A second select multiple directly after the first - write out the previous
								record.addAll(getContent(con, multipleChoiceValue, false, columnName, columnType, split_locn));
								
								// Restart process for the new select multiple
								currentSelectMultipleQuestionName = selectMultipleQuestionName;
								multipleChoiceValue = null;
								multipleChoiceValue = updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							}
						} else {
							if(multipleChoiceValue != null) {
								// Write out the previous multiple choice value before continuing with the non multiple choice value
								record.addAll(getContent(con, multipleChoiceValue, false, columnName, columnType, split_locn));
								
								// Restart Process
								multipleChoiceValue = null;
								currentSelectMultipleQuestionName = null;
							}
							record.addAll(getContent(con, value, false, columnName, columnType, split_locn));
						}
					} else {
						record.addAll(getContent(con, value, false, columnName, columnType, split_locn));
					}
				}
				f.addRecord(prikey, f.parkey, record);
								
				// Process child tables
				if(f.children != null) {
					for(int j = 0; j < f.children.size(); j++) {
						FormDesc nextForm = f.children.get(j);
						nextForm.parkey = prikey;
						getData(con, connectionResults, formList, nextForm, split_locn, merge_select_multiple, choiceNames,
								cols, resultsSheet, styles);
					}
				}
				
				/*
				 * For each complete survey retrieved combine the results
				 *  into a serial list that match the column headers. Where there are missing forms in the
				 *  data, Ie there was no data recorded for a form, then the results are padded
				 *  with empty values.
				 */
				if(f.parkey == null || f.parkey.equals("0")) {
					
					//f.printRecords(4, true);
					appendToOutput(con, new ArrayList<String> (), 
							formList.get(0), formList, 0, null, resultsSheet, styles);
					
				}
			}
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
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
	 * Construct the output
	 */
	private void appendToOutput(Connection sd,  ArrayList<String> in, 
			FormDesc f, ArrayList<FormDesc> formList, int index, 
			String parent,
			Sheet resultsSheet, 
			Map<String, CellStyle> styles
			) throws NumberFormatException, SQLException {

		int number_records = 0;
		if(f.records != null) {
			number_records = f.records.size(); 
		} 
		
		if(f.visible) {
			
			if(f.flat) {
				ArrayList<String> newRec = new ArrayList<String> ();
				newRec.addAll(in);
				for(int i = 0; i < number_records; i++) {
					newRec.addAll(f.records.get(i).record);
				}
				
				log.info("flat------>" + f.table_name + "Number records: " + number_records);
				// Pad up to max repeats
				for(int i = number_records; i < f.maxRepeats; i++) {
					ArrayList<String> record = new ArrayList<String>();
					for(int j = 1; j < f.columnCount; j++) {	// Start from one to ignore primary key
						record.addAll(getContent(sd, "",  false, "", "empty", false));
					}
					newRec.addAll(record);
				}

				if(index < formList.size() - 1) {
					appendToOutput(sd,  newRec , formList.get(index + 1), 
							formList, index + 1, null, resultsSheet, styles);
				} else {
					closeRecord(newRec, resultsSheet, styles);
				}
				
			} else {
				boolean found_non_matching_record = false;
				boolean hasMatchingRecord = false;
				if(number_records == 0) {
					if(index < formList.size() - 1) {
						
						/*
						 * Add an empty record for this form
						 */
						ArrayList<String> newRec = new ArrayList<String> ();
						newRec.addAll(in);
						for(int j = 1; j < f.columnCount; j++) {	// Start from one to ignore primary key
							newRec.addAll(getContent(sd, "", false, "", "empty", false));
						}
						
						FormDesc nextForm = formList.get(index + 1);
						String filter = null;
						
						appendToOutput(sd, newRec , nextForm, formList, index + 1, filter, resultsSheet, styles);
					} else {
						closeRecord(in, resultsSheet, styles);
					}
				} else {
					/*
					 * First check all the records to see if there is at least one matching record
					 */
					for(int i = 0; i < number_records; i++) {
						RecordDesc rd = f.records.get(i);
						
						if(parent == null || parent.equals("0") || parent.equals(rd.parkey)) {
							hasMatchingRecord = true;
						}
					}
					
					for(int i = 0; i < number_records; i++) {
						RecordDesc rd = f.records.get(i);
						
						if(parent == null || parent.equals("0") || parent.equals(rd.parkey)) {
							ArrayList<String> newRec = new ArrayList<String> ();
							newRec.addAll(in);
							newRec.addAll(f.records.get(i).record);
			
							if(index < formList.size() - 1) {
								/*
								 * If the next form is a child of this one then pass the primary key of the current record
								 * to filter the matching child records
								 */
								FormDesc nextForm = formList.get(index + 1);
								String filter = null;
								if(nextForm.parent.equals(f.f_id)) {
									filter = rd.prikey;
								}
								appendToOutput(sd, newRec , nextForm, formList, index + 1, filter, resultsSheet, styles);
							} else {
								closeRecord(newRec, resultsSheet, styles);
							} 
						} else {
							/*
							 * Non matching record  Continue processing the other forms in the list once.
							 * This is only done once as multiple non matching records are effectively duplicates
							 * It is also only done if we have not already found a matching record as 
							 *  
							 */
							if(!found_non_matching_record) {
								found_non_matching_record = true;

								if(index < formList.size() - 1) {
									
									/*
									 * Add an empty record for this form
									 */
									ArrayList<String> newRec = new ArrayList<String> ();
									newRec.addAll(in);
									for(int j = 1; j < f.columnCount; j++) {	// Start from one to ignore primary key
										newRec.addAll(getContent(sd, "",  false, "", "empty", false));
									}
									
									FormDesc nextForm = formList.get(index + 1);
									String filter = null;
									
									appendToOutput(sd, newRec , nextForm, formList, index + 1, filter, resultsSheet, styles);
								} else {
									/*
									 * Add the record if there are no matching records for this form
									 * This means that if a child form was not completed the main form will still be shown (outer join)
									 */
									if(!hasMatchingRecord) {
										closeRecord(in, resultsSheet, styles);
									}
								}
							}
						}
					}
				}
			}
		} else {
			log.info("Ignoring invisible form: " + f.table_name);
			// Proceed with any lower level forms
			if(index < formList.size() - 1) {
				appendToOutput(sd,  in , formList.get(index + 1), 
						formList, index + 1, null, resultsSheet, styles);
			} else {
				closeRecord(in, resultsSheet, styles);
			}
		}
		
	}
	
	private String getQuestion(Connection conn, String colName, int sId, FormDesc form, String language, boolean merge_select_multiple) throws SQLException {
		String questionText = "";
		String qColName = null;
		String optionColName = null;
		String qType = null;
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		int qId = -1;
		
		if(colName != null && language != null) {
			// Split the column name into the question and option part
			// Assume that double underscore is a unique separator
			int idx = colName.indexOf("__");
			if(idx == -1) {
				qColName = colName;
			} else {
				qColName = colName.substring(0, idx);
				optionColName = colName.substring(idx+2);
			}
			
			String sql = null;
	
			sql = "SELECT t.value AS qtext, q.qtype AS qtype, q.q_id FROM question q, translation t" +
					" WHERE q.f_id = ? " +
					" AND q.qtext_id = t.text_id " +
					" AND t.language = ? " +
					" AND t.s_id = ? " +
					" AND q.column_name = ?;";

			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, Integer.parseInt(form.f_id));
			pstmt.setString(2, language);
			pstmt.setInt(3, sId);
			pstmt.setString(4, qColName);
			resultSet = pstmt.executeQuery();
		
			if (resultSet.next()) {
				questionText = resultSet.getString("qtext");
				qType = resultSet.getString("qtype");
				qId = resultSet.getInt("q_id");
			}
			
			// Get any option text
			if(qType != null && qType.startsWith("select")) {
				sql = "SELECT t.value AS otext, o.ovalue AS ovalue, o.column_name FROM option o, question q, translation t" +
						" WHERE q.q_id = ? " +
						" AND o.l_id = q.l_id " +
						" AND o.label_id = t.text_id" +
						" AND t.language = ? " +
						" AND t.s_id = ? " +
						" ORDER BY o.seq ASC;";
						
				pstmt = conn.prepareStatement(sql);	 
				pstmt.setInt(1, qId);
				pstmt.setString(2, language);
				pstmt.setInt(3, sId);
				resultSet = pstmt.executeQuery();
			
				while (resultSet.next()) {
					String name = resultSet.getString("ovalue");
					String columnName = resultSet.getString("column_name").toLowerCase();
					String label = resultSet.getString("otext");
					if(qType.equals("select1") || merge_select_multiple) {
						// Put all options in the same column
						questionText += " " + name + "=" + label;
					} else if(optionColName != null) {
						// Only one option in each column
						if(columnName.equals(optionColName)) {
							questionText += " " + label;
						}
					}
				}
			}
			
		}
		
		try{
			if(resultSet != null) {resultSet.close();};
			if(pstmt != null) {pstmt.close();};
		} catch (Exception ex) {
			log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
		}
		
		return questionText;
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
