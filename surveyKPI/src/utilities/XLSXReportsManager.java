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

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.constants.SmapExportTypes;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QueryManager;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.ColValues;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.QueryForm;
import org.smap.sdal.model.ReadData;
import org.smap.sdal.model.SqlDesc;
import org.smap.sdal.model.Transform;
import org.smap.sdal.model.TransformDetail;


/*
 * Manage exporting of data posted from a data table
 */

public class XLSXReportsManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	ResourceBundle localisation = null;
	
	public XLSXReportsManager(ResourceBundle l) {
		localisation = l;
	}

	/*
	 * Create the new style XLSX report
	 */
	public Response getNewReport(
			Connection sd,
			Connection cResults,
			String username,
			HttpServletRequest request,
			HttpServletResponse response,
			int sId, 
			String filename, 
			boolean split_locn, 
			boolean merge_select_multiple,
			String language,
			boolean exp_ro,
			boolean embedImages,
			boolean excludeParents,
			boolean hxl,
			int fId,
			Date startDate,
			Date endDate,
			int dateId,
			String filter,
			Transform transform,
			boolean meta,
			String tz) {
		
		Response responseVal = null;

		HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();

		log.info("userevent: " + username + " Export " + sId + " as an xlsx file to " + filename + " starting from form " + fId);

		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		

		lm.writeLog(sd, sId, username, "view", "Export as: xlsx");

		String escapedFileName = null;
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes
		
		if(sId != 0) {

			PreparedStatement pstmt = null;
			Workbook wb = null;
			int rowNumber = 0;
			Sheet dataSheet = null;
			Sheet settingsSheet = null;
			CellStyle errorStyle = null;

			try {	

				if(language == null) {	// ensure a language is set
					language = "none";
				}

				String surveyName = GeneralUtilityMethods.getSurveyName(sd, sId);
				
				/*
				 * Get the list of forms and surveys to be exported
				 */
				ArrayList<QueryForm> queryList = null;
				QueryManager qm = new QueryManager();				
				queryList = qm.getFormList(sd, sId, fId);		// Get a form list for this survey / form combo

				QueryForm startingForm = qm.getQueryTree(sd, queryList);	// Convert the query list into a tree

				
				/*
				 * Create XLSX File
				 */
				GeneralUtilityMethods.setFilenameInResponse(filename + "." + "xlsx", response); // Set file name
				wb = new SXSSFWorkbook(10);		// Serialised output
				Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);
				CellStyle headerStyle = styles.get("header");
				CellStyle wideStyle = styles.get("wide");
				errorStyle = styles.get("error");
						
				dataSheet = wb.createSheet(localisation.getString("rep_data"));
				settingsSheet = wb.createSheet(localisation.getString("rep_settings"));
				
				/*
				 * Populate settings sheet
				 */
				int settingsRowIdx = 0;
				Row settingsRow = settingsSheet.createRow(settingsRowIdx++);
				Cell sk = settingsRow.createCell(0);
				Cell sv = settingsRow.createCell(1);
				sk.setCellStyle(headerStyle);	
				sk.setCellValue(localisation.getString("a_tz"));
				sv.setCellValue(tz);
				
				settingsRow = settingsSheet.createRow(settingsRowIdx++);
				sk = settingsRow.createCell(0);
				sv = settingsRow.createCell(1);	
				sk.setCellValue(localisation.getString("a_dfq"));
				sv.setCellStyle(headerStyle);	
				sv.setCellValue(GeneralUtilityMethods.getQuestionNameFromId(sd, sId, dateId));
				
				settingsRow = settingsSheet.createRow(settingsRowIdx++);
				sk = settingsRow.createCell(0);
				sv = settingsRow.createCell(1);	
				sk.setCellValue(localisation.getString("a_st"));
				sv.setCellStyle(styles.get("date"));	
				if(startDate != null) {
					sv.setCellValue(startDate);
				}
				
				settingsRow = settingsSheet.createRow(settingsRowIdx++);
				sk = settingsRow.createCell(0);
				sv = settingsRow.createCell(1);
				sk.setCellValue(localisation.getString("a_et"));
				sv.setCellStyle(styles.get("date"));	
				if(endDate != null) {
					sv.setCellValue(endDate);
				}
				
				// Get the SQL for this query
				SqlDesc sqlDesc = QueryGenerator.gen(sd, 
						cResults,
						localisation,
						sId,
						fId,
						language, 
						SmapExportTypes.XLSX, 
						urlprefix, 
						true,
						exp_ro,
						excludeParents,
						labelListMap,
						false,
						false,
						null,
						null,
						null,
						username,
						startDate,
						endDate,
						dateId,
						false,				// Super user - always apply filters
						startingForm,
						filter,
						transform,
						meta,
						false,
						tz);

				String basePath = GeneralUtilityMethods.getBasePath(request);					
				
				// Populate data sheet
				rowNumber = 0;		
				
				/*
				 * Write the labels if language has been set
				 */
				if(language != null && !language.equals("none")) {
					Row headerRow = dataSheet.createRow(rowNumber++);				
					int colNumber = 0;
					int dataColumn = 0;
					while(dataColumn < sqlDesc.column_details.size()) {
						ColValues values = new ColValues();
						ColDesc item = sqlDesc.column_details.get(dataColumn);
						
						dataColumn = GeneralUtilityMethods.getColValues(
								null, 
								values, 
								dataColumn,
								sqlDesc.column_details, 
								merge_select_multiple,
								surveyName);	
						
						// Wide columns in a long to wide transformation are replaced by repeating versions of themselves so don't write the label here
						if(isWideColumn(transform, values.name)) {
							continue;
						}
						
						int tdIndex = getTransformIndex(transform, values.name);
						if(tdIndex >= 0 ) {
							/*
							 * Replace this question with the wide labels
							 */
							for(String tc : transform.transforms.get(tdIndex).wideColumns) {
								for(String tv : transform.transforms.get(tdIndex).values) {
									Cell cell = headerRow.createCell(colNumber++);
									cell.setCellStyle(wideStyle);
									cell.setCellValue(tc + " - " +tv);
								}
							}
								
						} else if(split_locn && values.name.equals("the_geom")) {
							Cell cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							cell.setCellValue(values.label);
							
							cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							cell.setCellValue(values.label);
						} else if(item.qType != null && item.qType.equals("select") && !merge_select_multiple && item.choices != null &&  item.compressed) {
							for(int i = 0; i < item.choices.size(); i++) {
								Cell cell = headerRow.createCell(colNumber++);
								cell.setCellStyle(headerStyle);
								String label = item.choices.get(i).k;
								if(i < item.optionLabels.size()) {
									label = item.optionLabels.get(i).label;
								}
								cell.setCellValue(values.label + " - " + label);
							}
						} else if(item.qType != null && item.qType.equals("rank") && !merge_select_multiple && item.choices != null) {
							for(int i = 0; i < item.choices.size(); i++) {
								Cell cell = headerRow.createCell(colNumber++);
								cell.setCellStyle(headerStyle);
								cell.setCellValue(values.label + " - " + (i + 1));
							}
						} else if(item.qType != null && item.qType.equals("select1") && item.optionLabels != null) {
							StringBuffer label = null;
							if(values.label == null) {
								label = new StringBuffer("");
							} else {
								label = new StringBuffer(values.label);
							}
							label.append(" (");
							for(OptionDesc o : item.optionLabels) {
								label.append(" ").append(o.value).append("=").append(o.label);
								
							}
							label.append(")");
							Cell cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							cell.setCellValue(label.toString());
						} else {
							Cell cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							cell.setCellValue(values.label);
						}
					}
				}
				
				/*
				 * Write Question Name Header
				 */
				Row headerRow = dataSheet.createRow(rowNumber++);				
				int colNumber = 0;
				int dataColumn = 0;
				while(dataColumn < sqlDesc.column_details.size()) {
					ColValues values = new ColValues();
					ColDesc item = sqlDesc.column_details.get(dataColumn);
					dataColumn = GeneralUtilityMethods.getColValues(
							null, 
							values, 
							dataColumn,
							sqlDesc.column_details, 
							merge_select_multiple,
							surveyName);	
						
					// Wide columns in a long to wide transformation are replaced by repeating versions of themselves so don't write the label here
					if(isWideColumn(transform, values.name)) {
						continue;
					}
					
					int tdIndex = getTransformIndex(transform, values.name);					
					if(tdIndex >= 0 ) {
						/*
						 * Replace this question with the wide question names
						 */
						for(String tc : transform.transforms.get(tdIndex).wideColumns) {
							String displayName = GeneralUtilityMethods.getDisplayName(sd, sId, tc);
							for(String tv : transform.transforms.get(tdIndex).values) {
								Cell cell = headerRow.createCell(colNumber++);
								cell.setCellStyle(wideStyle);
								cell.setCellValue(displayName + " - " + tv);
							}
						}
							
					} else if(split_locn && values.name.equals("the_geom")) {
						Cell cell = headerRow.createCell(colNumber++);
						cell.setCellStyle(headerStyle);
						cell.setCellValue("Latitude");
						
						cell = headerRow.createCell(colNumber++);
						cell.setCellStyle(headerStyle);
						cell.setCellValue("Longitude");
					} else if(item.qType != null && item.qType.equals("select") && !merge_select_multiple && item.choices != null  && item.compressed) {
						for(int i = 0; i < item.choices.size(); i++) {
							Cell cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							if(item.selectDisplayNames) {
								cell.setCellValue(item.choices.get(i).v);	// Just show the choice display name
							} else {
								cell.setCellValue(values.name + " - " + item.choices.get(i).v);
							}
						}
					} else if(item.qType != null && item.qType.equals("rank") && !merge_select_multiple && item.choices != null) {
						for(int i = 0; i < item.choices.size(); i++) {
							Cell cell = headerRow.createCell(colNumber++);
							cell.setCellStyle(headerStyle);
							cell.setCellValue(values.name + " - " + (i + 1));
						}
					} else {
						Cell cell = headerRow.createCell(colNumber++);
						cell.setCellStyle(headerStyle);
						cell.setCellValue(values.name);
					}
				}
				
				/*
				 * Accumulate data to be written into an array and write it out in a second pass
				 * This supports functionality such as long to wide transforms where fewer records are written than read
				 */
				String key = "";															// transforms
				String previousKey = null;													// transforms
				HashMap<String, HashMap<String, String>> transformData = null;				// transforms
				
				
				pstmt = cResults.prepareStatement(sqlDesc.sql);
				cResults.setAutoCommit(false);	// page the results to reduce memory usage	
				pstmt.setFetchSize(100);	
				
				log.info("Get results: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				ArrayList<ReadData> dataItems = null;
				Row dataRow = null;
				while(rs.next()) {
					
					// If we are doing a transform then get the key of this record
					if(transform != null && transform.enabled) {
						key = getKeyValue(rs, transform);
						if(previousKey == null) {
							previousKey = key;
						}
					}
					
					/*
					 * Write out the previous record if this report does not use transforms or the key has changed
					 */
					if(dataItems != null && 
							(transform == null || 
							!transform.enabled  || 
							(transform.enabled && !key.equals(previousKey)))) {
						previousKey = key;
						dataRow = dataSheet.createRow(rowNumber++);	
						writeOutData(dataItems, transform, transformData, dataRow, wb, dataSheet, styles, embedImages, 
								basePath, rowNumber);
						transformData = null;
					}
					
					dataItems = new ArrayList<> ();
					
					dataColumn = 0;
					while(dataColumn < sqlDesc.column_details.size()) {
						ColValues values = new ColValues();
						ColDesc item = sqlDesc.column_details.get(dataColumn);
						dataColumn = GeneralUtilityMethods.getColValues(
								rs, 
								values, 
								dataColumn,
								sqlDesc.column_details, 
								merge_select_multiple,
								surveyName);						

						
						// Wide columns in a long to wide transformation are replaced by repeating versions of themselves so don't add the data
						if(isWideColumn(transform, values.name)) {
							continue;
						}
						
						int tdIndex = getTransformIndex(transform, values.name);					
						if(tdIndex >= 0 ) {
							
							ReadData rd = new ReadData(values.name, true, "string");
							dataItems.add(rd);
							if(transformData == null) {
								transformData = new HashMap<> ();
							}
							HashMap<String, String> itemTransform = transformData.get(values.name);
							if(itemTransform == null) {
								itemTransform = new HashMap<String, String> ();
								transformData.put(values.name, itemTransform);
							}
							
							for(String tv : transform.transforms.get(tdIndex).values) {
								if(tv.equals(values.value)) {
									// Valid value
									for(String tc : transform.transforms.get(tdIndex).wideColumns) {
										itemTransform.put(tc + " - " + values.value, rs.getString(sqlDesc.colNameLookup.get(tc)));
									}
									break;
								}
							}
							
								
						} else if(split_locn && values.value != null && values.value.startsWith("POINT")) {

							String coords [] = GeneralUtilityMethods.getLonLat(values.value);

							ReadData rd = new ReadData(values.name, false, values.type);
							dataItems.add(rd);
							
							if(coords.length > 1) {
								rd.values.add(coords[1]);
								rd.values.add(coords[0]);
							} else {
								rd.values.add(values.value);
								rd.values.add(values.value);
							}

						} else if(split_locn && values.value != null && (values.value.startsWith("POLYGON") || values.value.startsWith("LINESTRING"))) {

							// Can't split linestrings and polygons, leave latitude and longitude as blank
							ReadData rd = new ReadData(values.name, false, "string");
							dataItems.add(rd);
							rd.values.add(values.value);
							rd.values.add(values.value);


						} else if(split_locn && values.type != null && values.type.equals("geopoint") ) {
							// Geopoint that needs to be split but there is no data
							ReadData rd = new ReadData(values.name, false, "string");
							dataItems.add(rd);
							rd.values.add("");
							rd.values.add("");

						} else if(item.qType != null && item.qType.equals("select") && !merge_select_multiple && item.choices != null  && item.compressed) {
							
							String [] vArray = null;
							if(values.value != null) {
								vArray = values.value.split(" ");
							} 
							
							ReadData rd = new ReadData(values.name, false, values.type);
							dataItems.add(rd);
							
							for(int i = 0; i < item.choices.size(); i++) {			
								
								String v = "0";
								if(vArray != null) {
									
									String choiceValue = item.choices.get(i).k;
									for(int k = 0; k < vArray.length; k++) {
										if(vArray[k].equals(choiceValue)) {
											v = "1";
											break;
										}
									}
								}
								rd.values.add(v);
									
							}
						} else if(item.qType != null && item.qType.equals("rank") && !merge_select_multiple && item.choices != null) {
							
							String [] vArray = {""};
							if(values.value != null) {
								vArray = values.value.split(" ");
							} 
							
							ReadData rd = new ReadData(values.name, false, values.type);
							dataItems.add(rd);
							
							for(int i = 0; i < item.choices.size(); i++) {							
								if(i < vArray.length) {
									rd.values.add(vArray[i]);	
								} else {
									rd.values.add("");  // Just write spaces
								}		
									
							}
						} else if(item.qType != null && item.qType.equals("select1") && item.selectDisplayNames) {
							
							String value = values.value;
							// Convert the value into the display name
							for(int i = 0; i < item.choices.size(); i++) {							
									
								String choiceValue = item.choices.get(i).k;
								if(choiceValue != null && choiceValue.equals(value)) {
									value = item.choices.get(i).v;
								}
									
							}
							
							ReadData rd = new ReadData(values.name, false, values.type);
							dataItems.add(rd);
							rd.values.add(value);
							rd.type = values.type;
							
						} else {
							ReadData rd = new ReadData(values.name, false, values.type);
							dataItems.add(rd);
							rd.values.add(values.value);
							rd.type = values.type;
						}
					}

				}
				cResults.setAutoCommit(true);
				
				// Write the last row
				dataRow = dataSheet.createRow(rowNumber++);	
				writeOutData(dataItems, transform, transformData, dataRow, wb, dataSheet, styles, embedImages, 
						basePath, rowNumber);
			

			}  catch (Exception e) {
				try {cResults.setAutoCommit(true);} catch (Exception ex) {}
				log.log(Level.SEVERE, "Error", e);
				response.setHeader("Content-type",  "text/html; charset=UTF-8");
				lm.writeLog(sd, sId, username, "error", e.getMessage());
				
				String msg = e.getMessage();
				if(msg.contains("does not exist")) {
					msg = localisation.getString("msg_no_data");
				}
				Row dataRow = dataSheet.createRow(rowNumber + 1);	
				Cell cell = dataRow.createCell(0);
				cell.setCellStyle(errorStyle);
				cell.setCellValue(msg);
				
				responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
			} finally {	

				try {
					OutputStream outputStream = response.getOutputStream();
					wb.write(outputStream);
					wb.close();
					outputStream.close();
					((SXSSFWorkbook) wb).dispose();		// Dispose of temporary files
				} catch (Exception ex) {
					log.log(Level.SEVERE, "Error", ex);
				}
				
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}


			}
		}

		return responseVal;
	}
	
	private void writeOutData(ArrayList<ReadData> dataItems, Transform transform, 
			HashMap<String, HashMap<String, String>> transformData,
			Row dataRow,
			Workbook wb,
			Sheet dataSheet,
			Map<String, CellStyle> styles,
			boolean embedImages,
			String basePath,
			int rowNumber) {
		
		int colNumber = 0;
		if(dataItems != null) {
			for(ReadData item : dataItems) {
				if(item.isTransform) {
					int tdIndex = getTransformIndex(transform, item.name);	
					HashMap<String, String> itemTransform = transformData.get(item.name);
					for(String tc : transform.transforms.get(tdIndex).wideColumns) {
						for(String tv : transform.transforms.get(tdIndex).values) {
							Cell cell = dataRow.createCell(colNumber++);
							XLSUtilities.setCellValue(wb, dataSheet, cell, styles, itemTransform.get(tc + " - " + tv), 
									item.type, embedImages, basePath, rowNumber, colNumber - 1, true);
						}
					}
				} else {
					for(String v : item.values) {
						Cell cell = dataRow.createCell(colNumber++);
						XLSUtilities.setCellValue(wb, dataSheet, cell, styles, v, 
								item.type, embedImages, basePath, rowNumber, colNumber - 1, true);
					}
				}
			}
		}
	}
	
	private boolean isWideColumn(Transform transform, String name) {
		boolean val = false;
		
		if(transform != null && transform.enabled) {
			for(TransformDetail td : transform.transforms) {
				for(String col : td.wideColumns) {
					if(col.equals(name)) {
						val = true;
						break;
					}
				}
				if(val) {
					break;
				}

			}
		}
		return val;
	}
	
	private int getTransformIndex(Transform transform, String name) {
		int idx = -1;
		
		if(transform != null) {
			int count = 0;
			for(TransformDetail td : transform.transforms) {
				if(td.valuesQuestion.equals(name)) {
					idx = count;
					break;
				}
				count++;
			}
		}
		return idx;
	}
	
	private String getKeyValue(ResultSet rs, Transform transform) throws SQLException {
		StringBuilder key = new StringBuilder("");
		for(String c : transform.key_questions) {
			key.append(rs.getString(c));
		}
		return key.toString();
	}

}
