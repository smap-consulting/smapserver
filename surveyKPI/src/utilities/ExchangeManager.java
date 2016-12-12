package utilities;

import java.io.File;
import java.io.FileInputStream;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.ExchangeFile;
import org.smap.sdal.model.TableColumn;

import model.FormDesc;
import surveyKPI.ExportSurveyXls;

public class ExchangeManager {
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyXls.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	Workbook wb = null;
	boolean isXLSX = false;
	
	private class Column {
		String humanName;
		
		int index;
		String name;
		String columnName;
		String type;
		String geomCol;
		ArrayList<String> choices = null;
		boolean write = true;
		
		Column(String h) {
			humanName = h;
		}
		
		Column() {

		}
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
		String basePath = null;
		
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
				
				ArrayList <FormDesc> formList = getFormList(sd, sId);
				
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
					HashMap<String, String> selectMultipleColumnNames = new HashMap<String, String> ();
					
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
						boolean isSelectMultiple = false;
						String selectMultipleQuestionName = null;
						String optionName = null;
						
						if(qType.equals("select")) {
							isSelectMultiple = true;
							selectMultipleQuestionName = c.question_name;
							optionName = c.option_name;
						}
						
						boolean skipSelectMultipleOption = false;
						if(isSelectMultiple) {
							humanName = selectMultipleQuestionName;
							selMultChoiceNames.put(name, optionName);		// Add the name of sql column to a look up table for the get data stage
							String n = selectMultipleColumnNames.get(humanName);
							if(n != null) {
								skipSelectMultipleOption = true;
							} else {
								selectMultipleColumnNames.put(humanName, humanName);		// Record that we have 
							}
						}
						
						if(!skipSelectMultipleOption) {
							addToHeader(sd, cols, "none", humanName, name, qType, sId, f,true);
						}
								
						
						// Set the sql selection text for this column
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
	 * Get a sorted list of forms in order from parents to children
	 */
	public ArrayList <FormDesc> getFormList(Connection sd, int sId) throws SQLException {
		
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

		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	
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
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		return formList;
	}
	
	/*
	 * Load data from a file into the form
	 */
	public void loadFormDataFromFile(
			Connection results,
			PreparedStatement pstmtGetCol, 
			PreparedStatement pstmtGetChoices,
			File file,
			FormDesc form,
			String sIdent,
			HashMap<String, File> mediaFiles,
			boolean isCSV,
			ArrayList<String> responseMsg,
			String basePath
			) throws Exception {
		
		CSVReader reader = null;
		XlsReader xlsReader = null;
		boolean hasGeopoint = false;
		int lonIndex = -1;			// Column containing longitude
		int latIndex = -1;			// Column containing latitude
		SimpleDateFormat dateFormatDT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		PreparedStatement pstmtInsert = null;
		PreparedStatement pstmtDeleteExisting = null;
		
		try {
			
			form.keyMap = new HashMap<Integer, Integer> ();
			pstmtGetCol.setInt(1, form.f_id);		// Prepare the statement to get column names for the form
			
			String [] line;
			if(isCSV) {
				reader = new CSVReader(new InputStreamReader(new FileInputStream(file)));
				line = reader.readNext();
			} else {
				xlsReader = new XlsReader(new FileInputStream(file), form.name);
				line = xlsReader.readNext();
			}
			
			ArrayList<Column> columns = new ArrayList<Column> ();
			if(line != null && line.length > 0) {
				
				/*
				 * Get the columns in the file that are also in the form
				 * Assume first line is the header
				 */
				for(int i = 0; i < line.length; i++) {
					String colName = line[i].replace("'", "''");	// Escape apostrophes
					
					// If this column is in the survey then add it to the list of columns to be processed
					Column col = getColumn(pstmtGetCol, pstmtGetChoices, colName, columns, responseMsg);
					if(col != null) {
						col.index = i;
						if(col.geomCol != null) {
							// Do not add the geom columns to the list of columns to be parsed
							if(col.geomCol.equals("lon")) {
								lonIndex = i;
							} else if(col.geomCol.equals("lat")) {
								latIndex = i;
							}
						} else {
							columns.add(col);
						}
					} else {
						responseMsg.add("Column " + colName + " not found in form: " + form.name);  
					}

				}
				
				log.info("Loading data from " + columns.size() + " columns out of " + line.length + " columns in the data file");
				
				if(columns.size() > 0 || (lonIndex >= 0 && latIndex >= 0)) {
								
					/*
					 * Create the insert statement
					 */		
					boolean addedCol = false;
					StringBuffer sqlInsert = new StringBuffer("insert into " + form.table_name + "(");
					if(form.parent == 0) {
						sqlInsert.append("instanceid");
						addedCol = true;
					}
					
					for(int i = 0; i < columns.size(); i++) {
						
						Column col = columns.get(i);
						
						if(col.write) {
							if(col.type.equals("select")) {
								for(int j = 0; j < col.choices.size(); j++) {
									if(addedCol) {
										sqlInsert.append(",");
									}
									sqlInsert.append(col.columnName + "__" + col.choices.get(j));
									addedCol = true;
								}
							} else {
								if(addedCol) {
									sqlInsert.append(",");
								}
								sqlInsert.append(col.columnName);
								addedCol = true;
							}
						}
	
					}
					
					// Add the geopoint column if latitude and longitude were provided in the data file
					if(lonIndex >= 0 && latIndex >= 0 ) {
						if(addedCol) {
							sqlInsert.append(",");
						}
						hasGeopoint = true;
						sqlInsert.append("the_geom");
					}
					
					/*
					 * Add place holders for the data
					 */
					addedCol = false;
					sqlInsert.append(") values("); 
					if(form.parent == 0) {
						sqlInsert.append("?");		// instanceid
						addedCol = true;
					}
					
					
					for(int i = 0; i < columns.size(); i++) {
						
						Column col = columns.get(i);
						
						if(col.write) {
							if(col.type.equals("select")) {
								
								for(int j = 0; j < col.choices.size(); j++) {
									if(addedCol) {
										sqlInsert.append(",");
									}	
									sqlInsert.append("?");
									addedCol = true;
								}
							} else if(col.type.equals("geoshape")) {
								if(addedCol) {
									sqlInsert.append(",");
								}
								sqlInsert.append("ST_GeomFromText('POLYGON((' || ? || '))', 4326)");
								addedCol = true;
								
							} else if(col.type.equals("geotrace")) {
								if(addedCol) {
									sqlInsert.append(",");
								}
								sqlInsert.append("ST_GeomFromText('LINESTRING(' || ? || ')', 4326)");
								addedCol = true;
							} else {
								if(addedCol) {
									sqlInsert.append(",");
								}
								sqlInsert.append("?");
								addedCol = true;
							}
						}
					}
					
					// Add the geopoint value
					if(hasGeopoint) {
						if(addedCol) {
							sqlInsert.append(",");
						}
						sqlInsert.append("ST_GeomFromText('POINT(' || ? || ' ' || ? ||')', 4326)");
					}
					sqlInsert.append(");");
					
					pstmtInsert = results.prepareStatement(sqlInsert.toString(), Statement.RETURN_GENERATED_KEYS);
					
					/*
					 * Get the data
					 */
					while (true) {
						
						if(isCSV) {
							line = reader.readNext();
						} else {
							line = xlsReader.readNext();
						}
						if(line == null) {
							break;
						}
						
						int index = 1;
						int prikey = -1;
						boolean writeRecord = true;
						if(form.parent == 0) {
							pstmtInsert.setString(index++, "uuid:" + String.valueOf(UUID.randomUUID()));
						} 
						
						for(int i = 0; i < columns.size(); i++) {
							Column col = columns.get(i);
							
							// ignore empty columns at end of line
							if(col.index >= line.length) {
								String v;
								if(col.index == lonIndex || col.index == latIndex) {
									v = "0.0";
								} else {
									v = null;
								}
								pstmtInsert.setString(index++, v);
								continue;
							}
							
							String value = line[col.index].trim();	
	
							if(col.name.equals("prikey")) {
								try { prikey = Integer.parseInt(value);} catch (Exception e) {}
							} else if(col.name.equals("parkey")) {
								if(form.parent == 0) {
									pstmtInsert.setInt(index++, 0);
								} else {
									int parkey = -1;
									try { parkey = Integer.parseInt(value);} catch (Exception e) {}
									Integer newParKey = form.parentForm.keyMap.get(parkey);
									if(newParKey == null) {
										responseMsg.add("Parent record not found for record with parent key " + parkey + " in form " + form.name);
										writeRecord = false;
									} else {
										pstmtInsert.setInt(index++, newParKey);
									}
								}
							} else if(col.type.equals("audio") || col.type.equals("video") || col.type.equals("image")) {
								
								// If the data references a media file then process the attachment
								File srcPathFile = mediaFiles.get(value);
								if(srcPathFile != null) {
									value = GeneralUtilityMethods.createAttachments(
										value, 
										srcPathFile, 
										basePath, 
										sIdent);
								}
								if(value != null && value.trim().length() == 0) {
									value = null;
								}
								pstmtInsert.setString(index++, value);
							} else if(col.type.equals("select")) {
								String [] choices = value.split("\\s");
								for(int k = 0; k < col.choices.size(); k++) {
									String cVal = col.choices.get(k);
									boolean hasChoice = false;
									for(int l = 0; l < choices.length; l++) {
										if(cVal.equals(choices[l])) {
											hasChoice = true;
											break;
										}
									}
									if(hasChoice) {
										pstmtInsert.setInt(index++, 1);
									} else {
										pstmtInsert.setInt(index++, 0);
									}
									
								}
							} else if(col.type.equals("int")) {
								int iVal = 0;
								try { iVal = Integer.parseInt(value);} catch (Exception e) {}
								pstmtInsert.setInt(index++, iVal);
							} else if(col.type.equals("decimal")) {
								double dVal = 0.0;
								try { dVal = Double.parseDouble(value);} catch (Exception e) {}
								pstmtInsert.setDouble(index++, dVal);
							} else if(col.type.equals("boolean")) {
								boolean bVal = false;
								try { bVal = Boolean.parseBoolean(value);} catch (Exception e) {}
								pstmtInsert.setBoolean(index++, bVal);
							} else if(col.type.equals("date")) {
								Date dateVal = null;
								try {
									dateVal = Date.valueOf(value); 
								} catch (Exception e) {
									log.info("Error parsing date: " + col.columnName + " : " + value + " : " + e.getMessage());
								}
								pstmtInsert.setDate(index++, dateVal);
							} else if(col.type.equals("dateTime")) {
								Timestamp tsVal = null;
								try {
									java.util.Date uDate = dateFormatDT.parse(value);
									tsVal = new Timestamp(uDate.getTime());
								} catch (Exception e) {
									log.info("Error parsing datetime: " + value + " : " + e.getMessage());
								}
								
								pstmtInsert.setTimestamp(index++, tsVal);
							} else if(col.type.equals("time")) {
								
								int hour = 0;
								int minute = 0;
								int second = 0;
								try {
									String [] tVals = value.split(":");
									if(tVals.length > 0) {
										hour = Integer.parseInt(tVals[0]);
									}
									if(tVals.length > 1) {
										minute = Integer.parseInt(tVals[1]);
									}
									if(tVals.length > 2) {
										second = Integer.parseInt(tVals[2]);
									}
								} catch (Exception e) {
									log.info("Error parsing datetime: " + value + " : " + e.getMessage());
								}
								
								Time tVal = new Time(hour, minute, second);
								pstmtInsert.setTime(index++, tVal);
							} else {
								pstmtInsert.setString(index++, value);
							}
							
						}
						
						// Add the geopoint value if it exists
						if(hasGeopoint) {
							String lon = line[lonIndex];
							String lat = line[latIndex];
							if(lon == null || lon.length() == 0) {
								lon = "0.0";
							}
							if(lat == null || lat.length() == 0) {
								lat = "0.0";
							}
							pstmtInsert.setString(index++, lon);
							pstmtInsert.setString(index++, lat);

						}
						
						if(writeRecord) {
							log.info("Inserting row: " + pstmtInsert.toString());
							pstmtInsert.executeUpdate();
							ResultSet rs = pstmtInsert.getGeneratedKeys();
							if(rs.next()) {
								System.out.println("New key:  " + rs.getInt(1) + " for prikey: " + prikey);
								form.keyMap.put(prikey, rs.getInt(1));
							}
						}
						
				    }
					results.commit();
					
				} else {
					responseMsg.add("No columns found in the data file that match questions in form " + form.name);
				}
				
			}
		} finally {
			try {if (reader != null) {reader.close();}} catch (Exception e) {}
			
			try {if (pstmtInsert != null) {pstmtInsert.close();}} catch (Exception e) {}
			try {if (pstmtDeleteExisting != null) {pstmtDeleteExisting.close();}} catch (Exception e) {}
		}
	}
	
	public ArrayList<String> getFormsFromXLSX(InputStream inputStream) throws Exception {

		ArrayList<String> forms = new ArrayList<String> ();
		Workbook wb = null;
		try {
			wb = new XSSFWorkbook(inputStream);
			int sheetCount = wb.getNumberOfSheets();
			for(int i = 0; i < sheetCount; i++) {
				String name = wb.getSheetName(i);
				if(name.startsWith("d_"));
				forms.add(name.substring(2));
			}
		} finally {
			try{wb.close();} catch(Exception e) {}
		}
		return forms;
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
	 * Add to the header
	 */
	private void addToHeader(Connection sd, 
			ArrayList<Column> cols, 
			String language, 
			String human_name, 
			String colName, 
			String qType, 
			int sId, 
			FormDesc f,
			boolean merge_select_multiple) throws SQLException {
		
		if(qType != null && qType.equals("geopoint")) {
			cols.add(new Column("lat"));
			cols.add(new Column("lon"));
		} else {
			Column col = new Column(human_name);
			if(qType.equals("image")) {
			}
			cols.add(col);
			
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
	 * Add the list of children to parent forms
	 */
	private void addChildren(FormDesc parentForm, HashMap<String, FormDesc> forms, ArrayList<FormDesc> formList) {
		
		for(FormDesc fd : forms.values()) {
			if(fd.parent != 0 && fd.parent == parentForm.f_id) {
				if(parentForm.children == null) {
					parentForm.children = new ArrayList<FormDesc> ();
				}
				parentForm.children.add(fd);
				fd.parentForm = parentForm;
				formList.add(fd);
				addChildren(fd,  forms, formList);
			}
		}
		
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
				Row row = sheet.createRow(rowIndex);
				
				int colIndex = 0;	// Column index
				String currentSelectMultipleQuestionName = null;
				String multipleChoiceValue = null;
				for(int i = 0; i < f.columnList.size(); i++) {
					
					TableColumn c = f.columnList.get(i);

					String columnName = c.name;
					String columnType = c.type;
					String value = resultSet.getString(i + 1);
					boolean writeValue = true;
					
					if(value == null) {
						value = "";	
					}
					
					String choice = choiceNames.get(columnName);
					if(choice != null) {
						// Have to handle merge of select multiple
						String selectMultipleQuestionName = columnName.substring(0, columnName.indexOf("__"));
						if(currentSelectMultipleQuestionName == null) {
							currentSelectMultipleQuestionName = selectMultipleQuestionName;
							multipleChoiceValue = XLSUtilities.updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							writeValue = false;
						} else if(currentSelectMultipleQuestionName.equals(selectMultipleQuestionName) && (i != f.columnList.size() - 1)) {
							// Continuing on with the same select multiple and its not the end of the record
							multipleChoiceValue = XLSUtilities.updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							writeValue = false;
						} else if (i == f.columnList.size() - 1) {
							//  Its the end of the record		
							multipleChoiceValue = XLSUtilities.updateMultipleChoiceValue(value, choice, multipleChoiceValue);
							value = multipleChoiceValue;
						} else {
							// A second select multiple directly after the first - write out the previous
							String newMultipleChoiceValue = value;
							value = multipleChoiceValue;
					
							// Restart process for the new select multiple
							currentSelectMultipleQuestionName = selectMultipleQuestionName;
							multipleChoiceValue = null;
							multipleChoiceValue = XLSUtilities.updateMultipleChoiceValue(newMultipleChoiceValue, choice, multipleChoiceValue);
						}
					} else {
						if(multipleChoiceValue != null) {
							// Write out the previous multiple choice value before continuing with the non multiple choice value
							ArrayList<String> values = getContent(sd, multipleChoiceValue, false, columnName, columnType);
							for(int j = 0; j < values.size(); j++) {
								writeValue(row, colIndex++, values.get(j), sheet, styles);
							}
							
							// Restart Select Multiple Process
							multipleChoiceValue = null;
							currentSelectMultipleQuestionName = null;
						}
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
					
					if(writeValue) {
						ArrayList<String> values = getContent(sd, value, false, columnName, columnType);
						for(int j = 0; j < values.size(); j++) {
							writeValue(row, colIndex++, values.get(j), sheet, styles);
						}
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
	 * Check to see if a question is in a form
	 */
	private Column getColumn(PreparedStatement pstmtGetCol, 
			PreparedStatement pstmtGetChoices, 
			String qName,
			ArrayList<Column> columns,
			ArrayList<String> responseMsg) throws SQLException {
		
		Column col = null;
		String geomCol = null;
		
		// Cater for lat, lon columns which map to a geopoint
		if(qName.equals("lat") || qName.equals("lon")) {
			geomCol = qName;
			qName = "the_geom";
		} 
		
		// Only add this question if it has not previously been added, questions can only be updated once in a single transaction
		boolean questionExists = false;
		for(Column haveColumn : columns) {
			if(haveColumn.name.equals(qName)) {
				questionExists = true;
				break;
			}
		}
		
		if(!questionExists) {
			if(qName.equals("prikey")) {
				col = new Column();
				col.name = qName;
				col.columnName = "prikey";
				col.type = "int";
				col.write = false;					// Don't write the primary key a new one will be generated
			} else if(qName.equals("parkey")) {
				col = new Column();
				col.name = qName;
				col.columnName = "parkey";
				col.type = "int";
			} else if(qName.equals("Key")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_hrk";
				col.type = "string";
			} else if(qName.equals("User")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_user";
				col.type = "string";
			} else if(qName.equals("Survey Name")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_s_id";
				col.type = "int";
			} else if(qName.equals("Survey Notes")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_survey_notes";
				col.type = "int";
			} else if(qName.equals("Location Trigger")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_location_trigger";
				col.type = "int";
			} else if(qName.equals("Upload Time")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_upload_time";
				col.type = "dateTime";
			} else if(qName.equals("Version")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_version";
				col.type = "int";
			} else if(qName.equals("Complete")) {
				col = new Column();
				col.name = qName;
				col.columnName = "_complete";
				col.type = "boolean";
			} else {
				pstmtGetCol.setString(2, qName.toLowerCase());		// Search for a question
				log.info("Get column: " + pstmtGetCol.toString());
				ResultSet rs = pstmtGetCol.executeQuery();
				if(rs.next()) {
					// This column name is in the survey
					col = new Column();
					col.name = qName;
					col.columnName = rs.getString("column_name");
					col.geomCol = geomCol;				// This column holds the latitude or the longitude or neither
					col.type = rs.getString("qtype");
					
					if(col.type.startsWith("select")) {
						
						// Get choices for this select question
						int qId = rs.getInt("q_id");
						
						col.choices = new ArrayList<String> ();
						pstmtGetChoices.setInt(1, qId);
						log.info("Get choices:" + pstmtGetChoices.toString());
						ResultSet rsChoices = pstmtGetChoices.executeQuery();
						while(rsChoices.next()) {
							col.choices.add(rsChoices.getString("column_name"));
						}
					}
				}
			}
		} else {
			responseMsg.add("Column " + qName + " was included twice");
		}
		
		return col;
	}

}
