package surveyKPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.constants.SmapExportTypes;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QueryManager;
import org.smap.sdal.managers.SpssManager;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.ColValues;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.QueryForm;
import org.smap.sdal.model.SqlDesc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * Various types of export related to a survey
 *    
 */
@Path("/exportSurveyMisc/{sId}/{filename}")
public class ExportSurveyMisc extends Application {

	Authorise a = null;

	private static Logger log =
			Logger.getLogger(ExportSurveyMisc.class.getName());

	LogManager lm = new LogManager();		// Application log

	public ExportSurveyMisc() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Export as:
	 *    a) shape file
	 *    b) vrt file
	 *    c) csv file
	 *    d) kml file
	 *    e) stata file
	 *    f) spss file
	 * Not just shape exports.  All of these formats are exported via a single SQL query that
	 *  writes the result to a file that is zipped and then downloaded.
	 * For all of these formats the SQL is created from the passed in form and includes
	 *  all columns from the parent forms.
	 * 
	 * A list of forms and surveys can be specified in forms
	 * Alternatively a single form can be specified in form and its parents will also be included unless excludeparents=true 
	 * 
	 */
	@GET
	@Path("/shape")
	public Response exportSurveyMisc (@Context HttpServletRequest request, 
			@PathParam("sId") int targetId,
			@PathParam("filename") String filename,
			@QueryParam("form") int fId,
			@QueryParam("language") String language,
			@QueryParam("exp_ro") boolean exp_ro,
			@QueryParam("excludeparents") boolean excludeParents,
			@QueryParam("format") String format,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@QueryParam("dateId") int dateId,
			@QueryParam("query") boolean query,			// Set true if the value in sId is a query id rather than a survey id
			@QueryParam("filter") String filter,
			@QueryParam("merge_select_multiple") boolean merge_select_multiple,
			@Context HttpServletResponse response) {

		ResponseBuilder builder = Response.ok();
		Response responseVal = null;

		HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();

		log.info("userevent: " + request.getRemoteUser() + " Export " + targetId + " as a "+ format + " file to " + filename + " starting from form " + fId);

		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		

		String tz = "UTC";		// Default to UTC

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ExportSurveyMisc");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}

		a.isAuthorised(sd, request.getRemoteUser());

		if(query) {
			a.isValidQuery(sd, request.getRemoteUser(), targetId);
		} else {
			a.isValidSurvey(sd, request.getRemoteUser(), targetId, false, superUser);
		}
		// End Authorisation

		lm.writeLog(sd, targetId, request.getRemoteUser(), "view", "Export as: " + format);

		String escapedFileName = null;
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes
		
		if(targetId != 0) {

			Connection connectionResults = null;
			PreparedStatement pstmtDefLang = null;
			PreparedStatement pstmtDefLang2 = null;
			PreparedStatement pstmt = null;

			try {

				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

				String surveyName = GeneralUtilityMethods.getSurveyName(sd, targetId);
				
				/*
				 * Get the name of the database
				 */
				connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");
				DatabaseMetaData databaseMetaData = connectionResults.getMetaData();
				String dbUrl = databaseMetaData.getURL();
				String database_name = dbUrl.substring(dbUrl.lastIndexOf('/') + 1);

				/*
				 * Set the language
				 */
				if(language == null) {
					language = "none";
				}
				if((format.equals(SmapExportTypes.STATA) || format.equals(SmapExportTypes.SPSS)) && language.equals("none")) {
					// A language should be set for stata / spss exports, use the default
					String sqlDefLang = "select def_lang from survey where s_id = ?; ";
					pstmtDefLang = sd.prepareStatement(sqlDefLang);
					pstmtDefLang.setInt(1, targetId);			// TODO How will this work with queries?
					ResultSet resultSet = pstmtDefLang.executeQuery();
					if (resultSet.next()) {
						language = resultSet.getString(1);
						if(language == null) {
							// Just get the first language in the list	
							String sqlDefLang2 = "select distinct language from translation where s_id = ?; ";
							pstmtDefLang2 = sd.prepareStatement(sqlDefLang2);
							pstmtDefLang2.setInt(1, targetId);
							ResultSet resultSet2 = pstmtDefLang2.executeQuery();
							if (resultSet2.next()) {
								language = resultSet2.getString(1);
							}
						}
					}
				}

				/*
				 * Get the list of forms and surveys to be exported
				 */
				ArrayList<QueryForm> queryList = null;
				QueryManager qm = new QueryManager();
				if(query) {
					queryList = qm.getFormListFromQuery(sd, targetId);	// Get the form list from the query
				} else {
					queryList = qm.getFormList(sd, targetId, fId);		// Get a form list for this survey / form combo
				}
				QueryForm startingForm = qm.getQueryTree(sd, queryList);	// Convert the query list into a tree

				// Get the SQL for this query
				SqlDesc sqlDesc = QueryGenerator.gen(sd, 
						connectionResults,
						localisation,
						targetId,
						fId,
						language, 
						format, 
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
						request.getRemoteUser(),
						startDate,
						endDate,
						dateId,
						false,			// superUser - Always apply filters
						startingForm,
						filter,
						null,			// transform
						true,
						false,
						tz);			// Get all columns (not just instanceid)

				String basePath = GeneralUtilityMethods.getBasePath(request);					
				String filepath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique

				/*
				 * Create a VRT file for VRT exports
				 */
				if(format.equals(SmapExportTypes.VRT)) {
					log.info("Writing VRT file: " + filepath + "/" + sqlDesc.target_table + ".vrt");
					// Write the vrt file to the file system

					// Create the file
					FileUtils.forceMkdir(new File(filepath));
					File f = new File(filepath, sqlDesc.target_table + ".vrt");
					StreamResult out = new StreamResult(f);

					// Create the document
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					DocumentBuilder b = dbf.newDocumentBuilder();    		
					Document outputXML = b.newDocument(); 

					Element rootElement = outputXML.createElement("OGRVRTDataSource");
					outputXML.appendChild(rootElement); 

					Element layerElement = outputXML.createElement("OGRVRTLayer");
					layerElement.setAttribute("name", sqlDesc.target_table);
					rootElement.appendChild(layerElement);

					Element e = outputXML.createElement("SrcDataSource");
					e.setAttribute("relativeToVrt", "1");
					e.setTextContent(sqlDesc.target_table + ".csv");
					layerElement.appendChild(e);

					e = outputXML.createElement("GeometryType");
					e.setTextContent(sqlDesc.geometry_type);
					layerElement.appendChild(e);

					e = outputXML.createElement("LayerSRS");
					e.setTextContent("WGS84");
					layerElement.appendChild(e);

					e = outputXML.createElement("GeometryField");
					e.setAttribute("encoding", "WKT");	// WKB not supported by google maps
					e.setAttribute("reportSrcColumn", "false");
					e.setAttribute("field", "the_geom");
					layerElement.appendChild(e);

					for(int i = 0; i < sqlDesc.column_details.size(); i++) {
						ColDesc cd = sqlDesc.column_details.get(i);
						if(!cd.column_name.equals("the_geom")) {
							e = outputXML.createElement("Field");
							e.setAttribute("name", cd.column_name);
							e.setAttribute("src", cd.column_name);
							e.setAttribute("type", cd.google_type);
							layerElement.appendChild(e);
						}
					}

					Transformer transformer = TransformerFactory.newInstance().newTransformer();
					transformer.setOutputProperty(OutputKeys.INDENT, "yes");
					DOMSource source = new DOMSource(outputXML);
					transformer.transform(source, out);

				} else if(format.equals(SmapExportTypes.STATA)) {
					/*
					 * Create a Stata "do" file 
					 */
					log.info("Writing stata do file: " + filepath + "/" + sqlDesc.target_table + ".do");
					// Write the do file to the file system

					// Create the file
					FileUtils.forceMkdir(new File(filepath));
					File f = new File(filepath, sqlDesc.target_table + ".do");
					PrintWriter w = new PrintWriter(f);

					w.println("* Created by Smap Server");
					w.println("version 9");
					w.println("import delimited "+  sqlDesc.target_table + ".csv, bindquote(strict) clear");
					w.println("tempvar temp");

					// Write the label values
					w.println("\n* Value Labels ");
					w.println("#delimit ;");
					Iterator it = labelListMap.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry m = (Map.Entry)it.next();
						ArrayList<OptionDesc> options = (ArrayList<OptionDesc>) m.getKey();
						String listName =  (String) m.getValue();

						w.println("label define " + listName);			       
						for(int i = 0; i < options.size(); i++) {
							w.println("    " + options.get(i).num_value + " \"" + options.get(i).label + "\"");	 
						}
						w.println(";");
					}
					w.println("#delimit cr");

					// Define a label for select multiple questions which are 0 or 1
					w.println("\n* Define a label for select multiple questions");
					w.println("label define yesno 0 \"No\" 1 \"Yes\"");

					// Write the variable commands
					for(int i = 0; i < sqlDesc.column_details.size(); i++) {
						ColDesc cd = sqlDesc.column_details.get(i);
						if(cd.qType != null && cd.qType.equals("select") && cd.compressed) {			
							if(!merge_select_multiple && cd.choices != null) {
								for(int j = 0; j < cd.choices.size(); j++) {
									String selName = cd.column_name + "__" + cd.choices.get(j).k;
									String selLabel = cd.label + " - " + cd.choices.get(j).v;
									w.println("\n* variable: " + selName);
									w.println("label variable " + selName + " \"" + selLabel +"\"");
									w.println("label values " + selName + " yesno");
								}
							} else {
								w.println("label values " + cd.column_name + " yesno");
							}
						} else {
							w.println("\n* variable: " + cd.column_name);
							writeStataDataConversion(w, cd);
							writeStataQuestionLabel(w,cd);
							if(cd.qType != null && cd.qType.equals("select1")) {
								String valueLabel = labelListMap.get(cd.optionLabels);
								if(cd.needsReplace) {
									writeStataEncodeString(w, cd, valueLabel);
								}
								w.println("label values " + cd.column_name + " " + valueLabel);
							}
							if(cd.qType != null && cd.qType.equals("select")) {	
								w.println("label values " + cd.column_name + " yesno");
							}
						}
					}

					w.close();	

				} else if(format.equals(SmapExportTypes.SPSS)) {
					/*
					 * Create an SPSS "sps" file 
					 */
					log.info("Writing spss sps file: " + filepath + "/" + sqlDesc.target_table + ".sps");

					// Create the file
					FileUtils.forceMkdir(new File(filepath));
					File f = new File(filepath, sqlDesc.target_table + ".sps");
					PrintWriter w = new PrintWriter(f);

					SpssManager spssm = new SpssManager(localisation);  
					String sps = spssm.createSPS(
							sd,
							request.getRemoteUser(),
							language,
							targetId);

					w.print('\ufeff');		// Write the UTF-8 BOM
					w.print(sps);
					w.close();	

				}


				/*
				 * Export the data 
				 */
				int code = 0;
				boolean fastExport = true;
				String modifiedFormat = format;
				if(format.equals(SmapExportTypes.SPSS)) {
					modifiedFormat = SmapExportTypes.STATA;		// hack to generate a zip file with a csv file in it
					fastExport = false;
					merge_select_multiple = false;	// Ignore merge select multiple setting
				} else if(format.equals(SmapExportTypes.STATA)) {
					fastExport = false;
					merge_select_multiple = false;	// Ignore merge select multiple setting
				} else if(format.equals(SmapExportTypes.SHAPE)) {
					fastExport = true;
					merge_select_multiple = true;	// Ignore merge select multiple setting
				} else if(format.equals(SmapExportTypes.CSV) && !merge_select_multiple) {
					fastExport = false;
				}
				
				boolean split_locn = false;						// TODO
				
				if(fastExport) {
					Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/smap_bin/getshape.sh " + 
							database_name + " " +
							sqlDesc.target_table + " " +
							"\"" + sqlDesc.sql + "\" " +
							filepath + 
							" " + modifiedFormat +
					" >> /var/log/tomcat7/survey.log 2>&1"});
					code = proc.waitFor();

					log.info("Process exitValue: " + code);
				} else {
					log.info("############## Slow export");
					log.info(sqlDesc.sql);
					
					// Create the file
					FileUtils.forceMkdir(new File(filepath));
					File f = new File(filepath, sqlDesc.target_table + ".csv");
					PrintWriter w = new PrintWriter(f);

					/*
					 * Write the header
					 */
					int dataColumn = 0;
					StringBuffer header = new StringBuffer("");
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
							
						if(split_locn && values.name.equals("the_geom")) {
							addValueToBuf(header, "Latitude");
							addValueToBuf(header, "Longitude");
						} else if(item.qType != null && item.qType.equals("select") && !merge_select_multiple && item.choices != null && item.compressed) {
							for(int i = 0; i < item.choices.size(); i++) {
								addValueToBuf(header, values.name + "__" + item.choices.get(i).k);
							}
						} else {
							if((item.qType == null || !item.qType.equals("select")) && (item.displayName != null && item.displayName.trim().length() > 0)) {
								addValueToBuf(header, item.displayName);
							} else {
								addValueToBuf(header, item.column_name);
							}
						}
					}
					
					w.println(header.toString());
					
					/*
					 * Write the data
					 */
					pstmt = connectionResults.prepareStatement(sqlDesc.sql);
					log.info("Get results for slow export: " + pstmt.toString());
					ResultSet rs = pstmt.executeQuery();
					while(rs.next()) {
						
						dataColumn = 0;
						StringBuffer dataBuffer = new StringBuffer("");
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

							if(split_locn && values.value != null && values.value.startsWith("POINT")) {

								String coords [] = GeneralUtilityMethods.getLonLat(values.value);

								if(coords.length > 1) {
									addValueToBuf(dataBuffer, coords[1]);
									addValueToBuf(dataBuffer, coords[0]);
								} else {
									addValueToBuf(dataBuffer, values.value);
									addValueToBuf(dataBuffer, values.value);
								}
							} else if(split_locn && values.value != null && (values.value.startsWith("POLYGON") || values.value.startsWith("LINESTRING"))) {

								// Can't split linestrings and polygons, leave latitude and longitude as blank
								addValueToBuf(dataBuffer, values.value);
								addValueToBuf(dataBuffer, values.value);

							} else if(split_locn && values.type != null && values.type.equals("geopoint") ) {
								// Geopoint that needs to be split but there is no data
								addValueToBuf(dataBuffer, "");
								addValueToBuf(dataBuffer, "");
							} else if(item.qType != null && item.qType.equals("select") && !merge_select_multiple && item.choices != null  && item.compressed) {
								
								String [] vArray = null;
								if(values.value != null) {
									vArray = values.value.split(" ");
								} 
								
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
									addValueToBuf(dataBuffer, v);
										
								}
							} else {
								addValueToBuf(dataBuffer, values.value);
							}
						}
						w.println(dataBuffer.toString());
						
					}
					w.close();	
					
					/*
					 * Zip the directory contents
					 */
					File zip = new File(filepath + ".zip");
					File zipdir = new File(filepath);
					FileOutputStream fos = new FileOutputStream(zip);
					GeneralUtilityMethods.writeDirToZipOutputStream(new ZipOutputStream(fos), zipdir);	
					fos.close();
				}

				if(code == 0) {
					File file = new File(filepath + ".zip");

					if(file.exists()) {
						builder = Response.ok(file);
						if(format.equals(SmapExportTypes.KML)) {
							builder.header("Content-Disposition", "attachment;Filename=\"" + escapedFileName + ".kmz\"");
						} else {
							builder.header("Content-Disposition", "attachment;Filename=\"" + escapedFileName + ".zip\"");
						}
						builder.header("Content-type","application/zip");
						responseVal = builder.build();
					} else {
						throw new ApplicationException(localisation.getString("msg_no_data"));
					}

				} else {
					throw new ApplicationException("Error exporting file");
				}



			} catch (ApplicationException e) {
				response.setHeader("Content-type",  "text/html; charset=UTF-8");
				// Return an OK status so the message gets added to the web page
				// Prepend the message with "Error: ", this will be removed by the client
				responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
			} catch (Exception e) {
				log.log(Level.SEVERE, "Error", e);
				response.setHeader("Content-type",  "text/html; charset=UTF-8");
				responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
			} finally {	

				try {if (pstmtDefLang != null) {pstmtDefLang.close();}} catch (SQLException e) {}
				try {if (pstmtDefLang2 != null) {pstmtDefLang2.close();}} catch (SQLException e) {}	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	

				SDDataSource.closeConnection("surveyKPI-ExportSurvey", sd);
				ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", connectionResults);
			}
		}

		return responseVal;

	}

	/*
	 * Generate Stata do file commands to convert date/time/geometry fields to stata format
	 */
	void writeStataDataConversion(PrintWriter w, ColDesc cd) {
		if((cd.qType != null && cd.qType.equals("date")) || (cd.db_type != null && cd.db_type.equals("date"))) {
			w.println("generate double `temp' = date(" + cd.column_name + ", \"YMD\")");		// Convert to double
			w.println("format %-tdCCYY-NN-DD `temp'");
		} else if(cd.qType != null && cd.qType.equals("time")) {
			w.println("generate double `temp' = clock(" + cd.column_name + ", \"hms\")");		// Convert to double
			w.println("format %-tcHH:MM:SS `temp'");
		} else if((cd.qType != null && cd.qType.equals("dateTime")) || (cd.db_type != null && cd.db_type.equals("dateTime"))) {
			w.println("generate double `temp' = clock(" + cd.column_name + ", \"YMDhms\")");		// Convert to double
			w.println("format %-tcCCYY-NN-DD_HH:MM:SS `temp'");
		} else if(cd.db_type.equals("timestamptz")) {
			w.println("generate double `temp' = clock(" + cd.column_name + ", \"YMDhms\")");	// Convert to double
			w.println("format %-tcCCYY-NN-DD_HH:MM:SS `temp'");							// Set the display format

		} else {
			return;		// Not a date / time / geometry question
		}

		// rename the temp file created by the date functions 
		w.println("move `temp' " + cd.column_name);										// Move to the location of the variable
		w.println("drop " + cd.column_name);												// Remove the old variable
		w.println("rename `temp' " + cd.column_name);										// Rename the temporary variable
	}

	void writeStataEncodeString(PrintWriter w, ColDesc cd, String valueLabel) {
		w.println("capture {");			// Capture errors as if there is no data then there will be a type mismatch
		for(int i = 0; i < cd.optionLabels.size(); i++) {
			OptionDesc od = cd.optionLabels.get(i);
			w.println("replace " + cd.column_name + " = \"" + od.label + "\" if (" + cd.column_name + " == \"" + od.value + "\")");	// Replace values with labels
		}
		w.println("encode " + cd.column_name + ", generate(`temp') label(" + valueLabel + ")");			// Encode the variable
		w.println("drop " + cd.column_name);												// Remove the old variable
		w.println("rename `temp' " + cd.column_name);										// Rename the temporary variable
		w.println("}");
	}

	void writeStataQuestionLabel(PrintWriter w, ColDesc cd) {
		if(cd.label != null) {
			w.println("label variable " + cd.column_name + " \"" + cd.label + "\"");			// Set the label
		}
	}

	void addValueToBuf(StringBuffer buf, String value) {
		if(buf.length() > 0) {
			buf.append(",");
		}
		if(value == null) {
			value = "";
		}
		String escaped = StringEscapeUtils.escapeCsv(value);
		buf.append(escaped);
	}

}
