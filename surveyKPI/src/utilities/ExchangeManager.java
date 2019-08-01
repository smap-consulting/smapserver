package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;

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
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.smap.model.FormDesc;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.FileDescription;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.TableColumn;

import com.opencsv.CSVReader;

import model.ExchangeColumn;
import model.ExchangeHeader;

/*
 * Handle import export of files
 * Handle the formats created by Smap Exports
 *     Worksheets containing form data are called "d_" formname
 *     Column containing a unique key for each record is called "prikey"
 *     Column linking to the unique key of a parent record is called "parkey"
 * Attempt to handle exports of Aggregate data from Google sheets    
 *     The names of worksheets will need to be changed before importing
 *          The main worksheet needs to be called "d_main"
 *          The other worksheets need "d_" prepended
 *     Column containing a unique key for each record is called "metainstanceid"
 *         Column linking to the unique key of a parent record is called "parentuid"
 */
public class ExchangeManager {
	
	private static Logger log =
			 Logger.getLogger(ExchangeManager.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	private ResourceBundle localisation;
	private String tz;
	
	Workbook wb = null;
	boolean isXLSX = false;
	
	public ExchangeManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
	}
	
	HashMap<String, String> surveyNames = null;
	
	public ArrayList<FileDescription> createExchangeFiles(
			Connection sd, 
			Connection connectionResults,
			String user,
			int sId,
			HttpServletRequest request,
			String dirPath,
			boolean superUser,
			boolean incMedia) throws Exception {
		
		wb = new SXSSFWorkbook(10);
		Sheet sheet = null;
		ArrayList<FileDescription> files = new ArrayList<FileDescription> ();
			
		String filename = "data.xlsx";
		String filePath = dirPath + "/" + filename;

		OutputStream outputStream = new FileOutputStream(dirPath + "/data.xlsx");
		files.add(new FileDescription(filename, filePath));
		
		HashMap<String, String> selMultChoiceNames = new HashMap<String, String> ();

		Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);
		surveyNames = new HashMap<String, String> ();
		String basePath = null;
		String language = "none";
		
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
				String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
				for(FormDesc f : formList) {
					
					sheet = wb.createSheet("d_" + f.name);	
					
					TableColumn c;
					int parentId = 0;
					if(f.parent > 0) {
						parentId = f.parent;
					}
					HashMap<String, String> selectMultipleColumnNames = new HashMap<String, String> ();
					
					// Get the list of table columns
					f.columnList = GeneralUtilityMethods.getColumnsInForm(
							sd,
							connectionResults,
							localisation,
							language,
							sId,
							surveyIdent,
							user,
							null,		// Roles to apply
							parentId,
							f.f_id,
							f.table_name,
							false,		// Don't include Read Only
							true,		// Include parent key
							false,		// Don't include "bad" columns
							false,		// Don't include instance id
							true,		// Include prikey
							true,		// Include other meta data
							true,		// Include preloads
							true,		// instancename
							false,		// Survey duration
							superUser,
							false,
							false,		// Don't include audit data
							tz,
							false		// mgmt
							);
						
					// Get the list of spreadsheet columns
					ArrayList<ExchangeColumn> cols = new ArrayList<> ();
					for(int j = 0; j < f.columnList.size(); j++) {

						c = f.columnList.get(j);
						//String name = c.column_name;
						String qType = c.type;
						String questionName;
						String optionName = null;
						
						// Hack for meta values use the column name as the question name may have been translated
						if(c.isMeta) {
							questionName = c.column_name;
						} else {
							questionName = c.question_name;
						} 
						
						if(qType.equals("select")) {
							optionName = c.option_name;

							selMultChoiceNames.put(c.column_name, optionName);		// Add the name of sql column to a look up table for the get data stage
							String n = selectMultipleColumnNames.get(questionName);
							if(n == null) {
								// New Select multiple
								selectMultipleColumnNames.put(questionName, questionName);		// Record that we have this select multiple
								addToHeader(sd, cols, "none", questionName, c.column_name, qType, sId, f,true);
							}
						} else {
							addToHeader(sd, cols, "none", questionName, c.column_name, qType, sId, f,true);
						}
						
						// Set the sql selection text for this column
						String selName = null;
						if(c.isGeometry()) {
							selName = "ST_AsTEXT(" + c.column_name + ") ";
						} else if(qType.equals("dateTime")) {	// Return all timestamps at UTC with no time zone
							selName = "timezone('UTC', " + c.column_name + ") as " + c.column_name;	
						} else {
							selName = c.column_name;
						}
						
						if(f.columns == null) {
							f.columns = selName;
						} else {
							f.columns += "," + selName;
						}
					}
					
					createHeader(cols, sheet, styles);
					
					try {
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
								files,
								incMedia);
					} catch(Exception e) {
						// Ignore errors if the only problem is that the tables have not been created yet
						if(e.getMessage() != null) {
							if(e.getMessage().contains("ERROR: relation") && e.getMessage().contains("does not exist")) {
								// all good
							} else {
								throw e;
							}
						} else {
							throw e;
						}
					}
					
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
	public int loadFormDataFromCsvFile(
			Connection results,
			PreparedStatement pstmtGetCol, 
			PreparedStatement pstmtGetColGS,
			PreparedStatement pstmtGetChoices,
			File file,
			FormDesc form,
			String sIdent,
			HashMap<String, File> mediaFiles,
			ArrayList<String> responseMsg,
			String basePath,
			ResourceBundle localisation,
			ArrayList<MetaItem> preloads,
			String importSource,
			Timestamp importTime,
			String serverName,
			SimpleDateFormat sdf
			) throws Exception {
		
		CSVReader reader = null;
		XlsReader xlsReader = null;
		//FileInputStream fis = null;
		
		int recordsWritten = 0;
		
		PreparedStatement pstmtDeleteExisting = null;
		ExchangeHeader eh = new ExchangeHeader();
		
		try {
			
			form.keyMap = new HashMap<String, String> ();
			pstmtGetCol.setInt(1, form.f_id);		// Prepare the statement to get column names for the form
			pstmtGetColGS.setInt(1, form.f_id);		// Prepare the statement to get column names for the form
			
			String [] line;
			//if(isCSV) {
				reader = new CSVReader(new FileReader(file));
				line = reader.readNext();
			//} else {
			//	fis = new FileInputStream(file);
			//	xlsReader = new XlsReader(fis, form.name);
			//	line = xlsReader.readNext(true);
			//}
			
			if(line != null && line.length > 0) {
				
				processHeader(results, 
						pstmtGetCol, 
						pstmtGetChoices,
						pstmtGetColGS,
						responseMsg,
						preloads,
						eh, 
						line, 
						form);	
				
				if(eh.columns.size() > 0 || (eh.lonIndex >= 0 && eh.latIndex >= 0)) {
					
					/*
					 * Get the data
					 */
					while (true) {
						
						//if(isCSV) {
							line = reader.readNext();
						//} else {
						//	line = xlsReader.readNext(false);
						//}
						if(line == null) {
							break;
						}
						
						recordsWritten += processRecord(eh, 
								line, 
								form, 
								importSource, 
								importTime, 
								responseMsg,
								serverName,
								basePath,
								sIdent,
								mediaFiles,
								sdf,
								recordsWritten);

												
				    }
					results.commit();
					
				} else {
					responseMsg.add(
							localisation.getString("pk_nq") + " " + form.name);
				}
				
			}
		} finally {
			
			try{if(xlsReader != null) {xlsReader.close();}} catch (Exception e) {}
			//try{if(fis != null) {fis.close();}} catch (Exception e) {}
			try {if (reader != null) {reader.close();}} catch (Exception e) {}
			
			try {if (eh.pstmtInsert != null) {eh.pstmtInsert.close();}} catch (Exception e) {}
			try {if (pstmtDeleteExisting != null) {pstmtDeleteExisting.close();}} catch (Exception e) {}
		}
		
		log.info("Records written: " + recordsWritten);
		
		return recordsWritten;
	}
	
	public ArrayList<String> getFormsFromXLSX(InputStream inputStream) throws Exception {

		ArrayList<String> forms = new ArrayList<String> ();
		Workbook wb = null;
		try {
			wb = new XSSFWorkbook(inputStream);
			int sheetCount = wb.getNumberOfSheets();
			for(int i = 0; i < sheetCount; i++) {
				String name = wb.getSheetName(i);
				if(name.startsWith("d_")) {
					// Legacy forms remove prefix added by older results exports  30th January 2018
					name = name.substring(2);
				}
				forms.add(name);
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
			ArrayList<ExchangeColumn> cols, 
			Sheet sheet, 
			Map<String, CellStyle> styles) {
				
		// Create survey sheet header row
		Row headerRow = sheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			ExchangeColumn col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(col.humanName);
        }
	}
	
	
	/*
	 * Add to the header
	 */
	private void addToHeader(Connection sd, 
			ArrayList<ExchangeColumn> cols, 
			String language, 
			String human_name, 
			String colName, 
			String qType, 
			int sId, 
			FormDesc f,
			boolean merge_select_multiple) throws SQLException {
		
		if(qType != null && qType.equals("geopoint")) {
			cols.add(new ExchangeColumn("lat"));
			cols.add(new ExchangeColumn("lon"));
		} else {
			ExchangeColumn col = new ExchangeColumn(human_name);
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
	
	private boolean notEmpty(String v) {
		if(v == null || v.trim().length() == 0) {
			return false;
		} else {
			return true;
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
			ArrayList<ExchangeColumn> cols, 
			Sheet sheet, 
			Map<String, CellStyle> styles,
			int sId,
			Date startDate,
			Date endDate,
			String dateName,
			int dateForm,
			String basePath,
			String dirPath,
			ArrayList<FileDescription> files,
			boolean incMedia) throws Exception {
		
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

					String columnName = c.column_name;
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
						// Write out the previous multiple choice value before continuing with the non multiple choice value
						if(currentSelectMultipleQuestionName != null) {
							ArrayList<String> values = getContent(sd, multipleChoiceValue, false, 
									currentSelectMultipleQuestionName, "select");
							for(int j = 0; j < values.size(); j++) {
								writeValue(row, colIndex++, values.get(j), sheet, styles);
							}
						}
						
						// Restart Select Multiple Process
						multipleChoiceValue = null;
						currentSelectMultipleQuestionName = null;
					}
					
					if(c.isAttachment() && value != null && value.trim().length() > 0) {
						
						if(incMedia) {
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
								try {
									FileUtils.copyFile(source, dest);				
									files.add(new FileDescription(value, newPath));
								} catch (Exception e) {
									log.info("Error: Failed to add file " + source + " to exchange export. " + e.getMessage());
								}
							} else {
								log.info("Error: media file does not exist: " + attachmentPath);
							}
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
			if(resultSet != null) try {resultSet.close();} catch(Exception e) {};
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
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
		
		if(columnType.equals("geopoint")) {

			String coords [] = getLonLat(value);

			if(coords != null && coords.length > 1) { 
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
				
		} else if(columnName.equals(SmapServerMeta.SURVEY_ID_NAME)) {
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
	private ExchangeColumn getColumn(
			Connection cResults,
			String tableName,
			PreparedStatement pstmtGetCol, 
			PreparedStatement pstmtGetChoices, 
			String qName,
			ArrayList<ExchangeColumn> columns,
			ArrayList<String> responseMsg,
			ResourceBundle localisation,
			ArrayList<MetaItem> preloads
			) throws SQLException {
		
		ExchangeColumn col = null;
		String geomCol = null;
		
		// Cater for lat, lon columns which map to a geopoint
		if(qName.equals("lat") || qName.equals("lon") 
				|| qName.equals("plotgpsLatitude") || qName.equals("plotgpsLongitude")) {
			geomCol = qName;
			qName = "the_geom";
		} 
		
		// Only add this question if it has not previously been added, questions can only be updated once in a single transaction
		boolean questionExists = false;
		for(ExchangeColumn haveColumn : columns) {
			if(haveColumn.name.equals(qName)) {
				questionExists = true;
				break;
			}
		}
		
		if(!questionExists) {
			if(qName.equals("prikey") || qName.equals("metainstanceid")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "prikey";
				col.type = "int";
				col.write = false;					// Don't write the primary key a new one will be generated
			} else if(qName.equals("parkey") || qName.equals("parentuid")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "parkey";
				col.type = "int";
			} else if(qName.equals("Key") || qName.equals("_hrk")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_hrk";
				col.type = "string";
				if(!GeneralUtilityMethods.hasColumn(cResults, tableName, col.columnName)) {			// Add this column if it is not already in the table
					GeneralUtilityMethods.addColumn(cResults, tableName, col.columnName, "text");
				}
			} else if(qName.equals("User") || qName.equals("_user")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_user";
				col.type = "string";
			} else if(qName.equals("Survey Name") || qName.equals(SmapServerMeta.SURVEY_ID_NAME)) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = SmapServerMeta.SURVEY_ID_NAME;
				col.type = "int";
			} else if(qName.equals("Survey Notes") || qName.equals("_survey_notes")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_survey_notes";
				col.type = "int";
			} else if(qName.equals("Location Trigger") || qName.equals("_location_trigger")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_location_trigger";
				col.type = "int";
			} else if(qName.equals("Upload Time") || qName.equals(SmapServerMeta.UPLOAD_TIME_NAME) || qName.equals("metasubmissiondate")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = SmapServerMeta.UPLOAD_TIME_NAME;
				col.type = "dateTime";
			} else if(qName.equals(SmapServerMeta.SCHEDULED_START_NAME)) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = SmapServerMeta.SCHEDULED_START_NAME;
				col.type = "dateTime";
				if(!GeneralUtilityMethods.hasColumn(cResults, tableName, col.columnName)) {			// Add this column if it is not already in the table
					GeneralUtilityMethods.addColumn(cResults, tableName, col.columnName, "timestamp with time zone");
				}
			} else if(qName.equals("Version") || qName.equals("_version")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_version";
				col.type = "int";
			} else if(qName.equals("Complete") || qName.equals("_complete") || qName.equals("metaiscomplete")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "_complete";
				col.type = "boolean";
			} else if(qName.equals("Instance Name") || qName.equals("instancename")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "instancename";
				col.type = "string";
			} else if(qName.toLowerCase().equals("instanceid")) {
				// Don't add a column, instanceid is added by default, however record the column for this data
			} else if(qName.equals("plotgpsAltitude")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "the_geom_alt";
				col.type = "decimal";
			} else if(qName.equals("plotgpsAccuracy")) {
				col = new ExchangeColumn();
				col.name = qName;
				col.columnName = "the_geom_acc";
				col.type = "decimal";
			} else {
				pstmtGetCol.setString(2, qName.toLowerCase());		// Search for a question
				log.info("Get column: " + pstmtGetCol.toString());
				ResultSet rs = pstmtGetCol.executeQuery();
				if(rs.next()) {
					// This column name is in the survey
					col = new ExchangeColumn();
					col.name = qName;
					col.columnName = rs.getString("column_name");
					col.geomCol = geomCol;				// This column holds the latitude, longitude, Altitude, Accuracy or none of these
					col.type = rs.getString("qtype");
					
					if(col.type.startsWith("select")) {
						
						// Get choices for this select question
						int qId = rs.getInt("q_id");
						
						col.choices = new ArrayList<Option> ();
						pstmtGetChoices.setInt(1, qId);
						log.info("Get choices:" + pstmtGetChoices.toString());
						ResultSet rsChoices = pstmtGetChoices.executeQuery();
						while(rsChoices.next()) {
							Option o = new Option();
							o.columnName = rsChoices.getString("column_name");
							o.value = rsChoices.getString("ovalue");
							col.choices.add(o);
						}
					}
				} else {
					// Check to see if it is a preload
					
					for(MetaItem mi : preloads) {
						if(mi.isPreload) {
							if(mi.name.equals(qName)) {
								col = new ExchangeColumn();
								col.name = qName;
								col.columnName = mi.columnName;
								col.type = mi.type;
								break;
							}
						}
					}
				}
			}
		} else {
			responseMsg.add(
					localisation.getString("mf_col") +
					" " + qName + " " +
					localisation.getString("imp_i2"));

		}
		
		return col;
	}
	
	public void processHeader(
			Connection results,
			PreparedStatement pstmtGetCol,
			PreparedStatement pstmtGetChoices,
			PreparedStatement pstmtGetColGS,
			ArrayList<String> responseMsg,
			ArrayList<MetaItem> preloads,
			ExchangeHeader eh, 
			String [] line, 
			FormDesc form) throws SQLException {
		/*
		 * Get the columns in the file that are also in the form
		 * Assume first line is the header
		 */
		for(int i = 0; i < line.length; i++) {
			String colName = line[i].replace("'", "''");	// Escape apostrophes
			
			if(colName.trim().length() > 0) {
				if(colName.toLowerCase().equals("instanceid")) {
					eh.instanceIdColumn = i;
				}
				// If this column is in the survey then add it to the list of columns to be processed
				// Do this test for a load from excel
				ExchangeColumn col = getColumn(results, form.table_name, pstmtGetCol, 
						pstmtGetChoices, colName, eh.columns, 
						responseMsg, localisation, preloads);
				if(col != null) {
					col.index = i;
					if(col.geomCol != null) {
						// Do not add the geom columns to the list of columns to be parsed
						if(col.geomCol.equals("lon") || col.geomCol.equals("plotgpsLongitude")) {
							eh.lonIndex = i;
						} else if(col.geomCol.equals("lat") || col.geomCol.equals("plotgpsLatitude")) {
							eh.latIndex = i;
						} 
					} else {
						eh.columns.add(col);
					}
				} else {
					// Perform test for a load from a google sheets export
					col = getColumn(results, form.table_name, pstmtGetColGS, pstmtGetChoices, colName, eh.columns, responseMsg, localisation, preloads);
					if(col != null) {
						col.index = i;
						if(col.geomCol != null) {
							// Do not add the geom columns to the list of columns to be parsed
							if(col.geomCol.equals("lon") || col.geomCol.equals("plotgpsLongitude")) {
								eh.lonIndex = i;
							} else if(col.geomCol.equals("lat") || col.geomCol.equals("plotgpsLatitude")) {
								eh.latIndex = i;
							} 
						} else {
							eh.columns.add(col);
						}
					} else {
						responseMsg.add(
								localisation.getString("imp_qn") +
								" " + colName + " " +
								localisation.getString("imp_nfi") +
								": " + form.name);  
					}
				}
			}

		}
		
		log.info("Loading data from " + eh.columns.size() + " columns out of " + line.length + " columns in the data file");
		
		if(eh.columns.size() > 0 || (eh.lonIndex >= 0 && eh.latIndex >= 0)) {
				
			/*
			 * Add the source column if it is not already in the results table
			 */
			if(!GeneralUtilityMethods.hasColumn(results, form.table_name, "_import_source")) {
				GeneralUtilityMethods.addColumn(results, form.table_name, "_import_source", "text");
			}
			if(!GeneralUtilityMethods.hasColumn(results, form.table_name, "_import_time")) {
				GeneralUtilityMethods.addColumn(results, form.table_name, "_import_time", "timestamp with time zone");
			}
			/*
			 * Create the insert statement
			 */		
			StringBuffer sqlInsert = new StringBuffer("insert into " + form.table_name + "(");
			sqlInsert.append("_import_source, _import_time");
			if(form.parent == 0) {
				sqlInsert.append(",instanceid");
			}
			
			for(int i = 0; i < eh.columns.size(); i++) {						
				ExchangeColumn col = eh.columns.get(i);						
				if(col.write) {
					sqlInsert.append(",").append(col.columnName);
				} 
			}
			
			// Add the geopoint column if latitude and longitude were provided in the data file
			if(eh.lonIndex >= 0 && eh.latIndex >= 0 ) {
				sqlInsert.append(",").append("the_geom");;
				eh.hasGeopoint = true;
			}
			
			/*
			 * Add place holders for the data
			 */
			sqlInsert.append(") values("); 
			sqlInsert.append("?, ?");			// _import_source and _import_time
			if(form.parent == 0) {
				sqlInsert.append(",?");		// instanceid
			}
			
			
			for(int i = 0; i < eh.columns.size(); i++) {
				
				ExchangeColumn col = eh.columns.get(i);
				
				if(col.write) {
					if(col.type.equals("geoshape")) {
						sqlInsert.append(",").append("ST_GeomFromText('POLYGON((' || ? || '))', 4326)");
						
					} else if(col.type.equals("geotrace")) {
						sqlInsert.append(",").append("ST_GeomFromText('LINESTRING(' || ? || ')', 4326)");
					} else {
						sqlInsert.append(",").append("?");
					}
				}
			}
			
			// Add the geopoint value
			if(eh.hasGeopoint) {
				sqlInsert.append(",").append("ST_GeomFromText('POINT(' || ? || ' ' || ? ||')', 4326)");
			}
			sqlInsert.append(")");
			
			eh.pstmtInsert = results.prepareStatement(sqlInsert.toString(), Statement.RETURN_GENERATED_KEYS);
		}
	}

	public int processRecord(ExchangeHeader eh, 
			String [] line, 
			FormDesc form,
			String importSource,
			Timestamp importTime,
			ArrayList<String> responseMsg,
			String serverName,
			String basePath,
			String sIdent,
			HashMap<String, File> mediaFiles,
			SimpleDateFormat sdf,
			int recordsWritten) throws SQLException {
		
		int index = 1;
		int count = 0;
		String prikey = null;
		boolean writeRecord = true;
		eh.pstmtInsert.setString(index++, importSource);
		eh.pstmtInsert.setTimestamp(index++, importTime);
		if(form.parent == 0) {
			String instanceId = null;
			if(eh.instanceIdColumn >= 0) {
				instanceId = line[eh.instanceIdColumn].trim();
			}
			if(instanceId == null || instanceId.trim().length() == 0) {
				instanceId = "uuid:" + String.valueOf(UUID.randomUUID());
			}
			eh.pstmtInsert.setString(index++, instanceId);
		} 
		
		for(int i = 0; i < eh.columns.size(); i++) {
			ExchangeColumn col = eh.columns.get(i);
			
			// ignore empty columns at end of line
			if(col.index >= line.length) {
				
				if(col.type.equals("int")) {
					eh.pstmtInsert.setInt(index++, 0);
				} else if(col.type.equals("decimal")) {
					eh.pstmtInsert.setDouble(index++, 0.0);
				} else if(col.type.equals("date")) {
					eh.pstmtInsert.setDate(index++, null);
				} else if(col.type.equals("dateTime")) {
					eh.pstmtInsert.setTimestamp(index++, null);
				} else if(col.type.equals("time")) {
					eh.pstmtInsert.setTime(index++, null);
				} else {
					eh.pstmtInsert.setString(index++, null);
				}
				continue;
			}
			
			String value = line[col.index].trim();	

			if(col.name.equals("prikey") || col.name.equals("metainstanceid")) {
				prikey = value;
			} else if(col.name.equals("parkey") || col.name.equals("parentuid")) {
				if(form.parent == 0) {
					eh.pstmtInsert.setInt(index++, 0);
				} else {
					String parkey = value;
					String newParKey = form.parentForm.keyMap.get(parkey);
					int iParKey = -1;
					try {iParKey = Integer.parseInt(newParKey); } catch (Exception e) {}
					if(newParKey == null || iParKey == -1) {
						responseMsg.add(
								localisation.getString("pk_nf") +
								" " + parkey + " " +
								localisation.getString("pk_if") +
								" " + form.name);
						writeRecord = false;
					} else {
						eh.pstmtInsert.setInt(index++, iParKey);
					}
				}
			} else if(GeneralUtilityMethods.isAttachmentType(col.type)) {
				
				// If the data references a media file then process the attachment
				File srcPathFile = null;
				String srcUrl = null;
				if(value != null && (value.trim().startsWith("https://") || value.trim().startsWith("http://"))) {
					
					// If the link is to a file on the same server (or this is localhost) do not duplicate the media
					value = value.trim();
					String serverHttpsUrl = "https://" + serverName + "/";
					String serverHttpUrl = "http://" + serverName + "/";
					if(serverName.equals("localhost") || value.startsWith(serverHttpUrl) || value.startsWith(serverHttpsUrl)) {
						int idx = value.indexOf(serverName) + serverName.length();
						value = value.substring(idx);
					} else {
						// Get the attachment from the link so it can be loaded
						srcUrl = value;
						value = UUID.randomUUID().toString();	// Create a random name for the initial download
					}
				} else {
					// Attachment should have been loaded with the zip file
					srcPathFile = mediaFiles.get(value);
				}
				
				// Copy the attachments to the target location and get the new name
				if(srcPathFile != null || srcUrl != null) {
					value = GeneralUtilityMethods.createAttachments(
						value, 
						srcPathFile, 
						basePath, 
						sIdent,
						srcUrl);
				}
				if(value != null && value.trim().length() == 0) {
					value = null;
				}
				eh.pstmtInsert.setString(index++, value);
			} else if(col.type.equals("int")) {
				int iVal = 0;
				if(notEmpty(value)) {
					try { iVal = Integer.parseInt(value);} catch (Exception e) {}
				}
				eh.pstmtInsert.setInt(index++, iVal);
			} else if(col.type.equals("decimal") || col.type.equals("range")) {
				double dVal = 0.0;
				if(notEmpty(value)) {
					try { dVal = Double.parseDouble(value);} catch (Exception e) {}
				}
				eh.pstmtInsert.setDouble(index++, dVal);
			} else if(col.type.equals("boolean")) {
				boolean bVal = false;
				if(notEmpty(value)) {
					try { bVal = Boolean.parseBoolean(value);} catch (Exception e) {}
				}
				eh.pstmtInsert.setBoolean(index++, bVal);
			} else if(col.type.equals("date")) {
				Date dateVal = null;
				if(notEmpty(value)) {
					try {
						dateVal = Date.valueOf(value); 
						
					} catch (Exception e) {
						try {
							java.util.Date uDate = sdf.parse(value);		
							dateVal = new Date(uDate.getTime());
						} catch (Exception ex) {
							log.info("Error parsing date: " + col.columnName + " : " + value + " : " + e.getMessage());
						}
					}
				}
				eh.pstmtInsert.setDate(index++, dateVal);
			} else if(col.type.equals("dateTime")) {
				Timestamp tsVal = null;
				if(notEmpty(value)) {
					try {
						java.util.Date uDate = sdf.parse(value);
						tsVal = new Timestamp(uDate.getTime());
					} catch (Exception e) {
						try {
							java.util.Date uDate = sdf.parse(value);		
							tsVal = new Timestamp(uDate.getTime());
						} catch (Exception ex) {
							log.info("Error parsing datetime: " + value + " : " + e.getMessage());
						}
					}
				}
				
				eh.pstmtInsert.setTimestamp(index++, tsVal);
			} else if(col.type.equals("time")) {
				
				int hour = 0;
				int minute = 0;
				int second = 0;
				if(notEmpty(value)) {
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
				}
				
				Time tVal = new Time(hour, minute, second);
				eh.pstmtInsert.setTime(index++, tVal);
			} else {
				eh.pstmtInsert.setString(index++, value);
			}
			
		}
		
		// Add the geopoint value if it exists
		if(eh.hasGeopoint) {
			String lon = line[eh.lonIndex];
			String lat = line[eh.latIndex];
			if(lon == null || lon.length() == 0) {
				lon = "0.0";
			}
			if(lat == null || lat.length() == 0) {
				lat = "0.0";
			}
			eh.pstmtInsert.setString(index++, lon);
			eh.pstmtInsert.setString(index++, lat);

		}
		
		if(writeRecord) {
			if(recordsWritten == 0) {
				log.info("Inserting first record: " + eh.pstmtInsert.toString());
			}
			eh.pstmtInsert.executeUpdate();
			ResultSet rs = eh.pstmtInsert.getGeneratedKeys();
			if(rs.next()) {
				form.keyMap.put(prikey, rs.getString(1));
			}
			count++;
		}
		
		return count;
	}
}
