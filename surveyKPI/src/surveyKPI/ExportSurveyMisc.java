package surveyKPI;

import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
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
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QueryManager;
import org.smap.sdal.managers.SpssManager;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.ExportForm;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.QueryForm;
import org.smap.sdal.model.SqlDesc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Various types of export related to a survey
 *    
 */
@Path("/exportSurveyMisc/{sId}/{filename}")
public class ExportSurveyMisc extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyMisc.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
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
			//@QueryParam("forms") String forms,
			@QueryParam("query") boolean query,			// Set true if the value in sId is a query id rather than a survey id
			@Context HttpServletResponse response) {

		ResponseBuilder builder = Response.ok();
		Response responseVal = null;
		ResourceBundle localisation = null;
		
		HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();
		
		log.info("userevent: " + request.getRemoteUser() + " Export " + targetId + " as a "+ format + " file to " + filename + " starting from form " + fId);
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
				
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurveyMisc");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		
		a.isAuthorised(connectionSD, request.getRemoteUser());
		
		/*
		if(formList != null) {
			HashMap<Integer, String> checkedSurveys = new HashMap<Integer, String> ();
			for(int i = 0; i < formList.size(); i++) {
				int survey = formList.get(i).sId;
				if(checkedSurveys.get(new Integer(survey)) == null) {
					a.isValidSurvey(connectionSD, request.getRemoteUser(), formList.get(i).sId, false, superUser);
					checkedSurveys.put(new Integer(survey), "checked");
				}
			}
		} else {
		*/
		if(query) {
			a.isValidQuery(connectionSD, request.getRemoteUser(), targetId);
		} else {
			a.isValidSurvey(connectionSD, request.getRemoteUser(), targetId, false, superUser);
		}
		// End Authorisation

		lm.writeLog(connectionSD, targetId, request.getRemoteUser(), "view", "Export as: " + format);
		
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
			
			try {
		
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request.getRemoteUser()));
				localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
						
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
				if((format.equals("stata") || format.equals("spss")) && language.equals("none")) {
					// A language should be set for stata / spss exports, use the default
					String sqlDefLang = "select def_lang from survey where s_id = ?; ";
					pstmtDefLang = connectionSD.prepareStatement(sqlDefLang);
					pstmtDefLang.setInt(1, targetId);			// TODO How will this work with queries?
					ResultSet resultSet = pstmtDefLang.executeQuery();
					if (resultSet.next()) {
						language = resultSet.getString(1);
						if(language == null) {
							// Just get the first language in the list	
							String sqlDefLang2 = "select distinct language from translation where s_id = ?; ";
							pstmtDefLang2 = connectionSD.prepareStatement(sqlDefLang2);
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
					queryList = qm.getFormListFromQuery(connectionSD, targetId);	// Get the form list from the query
				} else {
					queryList = qm.getFormList(connectionSD, targetId, fId);		// Get a form list for this survey / form combo
				}
				QueryForm startingForm = qm.getQueryTree(connectionSD, queryList);	// Convert the query list into a tree
				
				// Get the SQL for this query
				SqlDesc sqlDesc = QueryGenerator.gen(connectionSD, 
						connectionResults,
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
						superUser,
						startingForm);

				String basePath = GeneralUtilityMethods.getBasePath(request);					
				String filepath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
					
				/*
				 * Create a VRT file for VRT exports
				 */
				if(format.equals("vrt")) {
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
		    	 	
		    	 	for(int i = 0; i < sqlDesc.colNames.size(); i++) {
		    	 		ColDesc cd = sqlDesc.colNames.get(i);
		    	 		if(!cd.name.equals("the_geom")) {
			    	 		e = outputXML.createElement("Field");
				    	 	e.setAttribute("name", cd.name);
				    	 	e.setAttribute("src", cd.name);
				    	 	e.setAttribute("type", cd.google_type);
				    	 	layerElement.appendChild(e);
		    	 		}
		    	 	}
			    	 	
		        	Transformer transformer = TransformerFactory.newInstance().newTransformer();
		        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		        	DOMSource source = new DOMSource(outputXML);
		        	transformer.transform(source, out);

				} else if(format.equals("stata")) {
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
					w.println("import delimited bindquote(strict) "+  sqlDesc.target_table + ".csv, clear");
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
						for(int i = 0; i < sqlDesc.colNames.size(); i++) {
			    	 		ColDesc cd = sqlDesc.colNames.get(i);
			    	 		w.println("\n* variable: " + cd.name);
			    	 		log.info("Stata types: " + cd.db_type + " : " + cd.qType);
				    	 	writeStataDataConversion(w, cd);
				    	 	writeStataQuestionLabel(w,cd);
				    	 	if(cd.qType != null && cd.qType.equals("select1")) {
				    	 		String valueLabel = labelListMap.get(cd.optionLabels);
				    	 		if(cd.needsReplace) {
				    	 			writeStataEncodeString(w, cd, valueLabel);
				    	 		}
				    	 		w.println("label values " + cd.name + " " + valueLabel);
				    	 	}
				    	 	if(cd.qType != null && cd.qType.equals("select")) {
				    	 		w.println("label values " + cd.name + " yesno");
				    	 	}
			    	 	}
						
						w.close();	

					} else if(format.equals("spss")) {
						/*
						 * Create an SPSS "sps" file 
						 */
						log.info("Writing spss sps file: " + filepath + "/" + sqlDesc.target_table + ".sps");

						// Create the file
						FileUtils.forceMkdir(new File(filepath));
						File f = new File(filepath, sqlDesc.target_table + ".sps");
						PrintWriter w = new PrintWriter(f);
								
						SpssManager spssm = new SpssManager();  
						String sps = spssm.createSPS(
								connectionSD,
								request.getRemoteUser(),
								language,
								targetId);
						
						w.print(sps);
						w.close();	

					}
				
					
					/*
					 * Export the data 
					 */
					int code = 0;
					String modifiedFormat = format;
					if(format.equals("spss")) {
						modifiedFormat = "stata";		// hack to generate a zip file with a csv file in it
					}
					Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/smap_bin/getshape.sh " + 
							database_name + " " +
							sqlDesc.target_table + " " +
							"\"" + sqlDesc.sql + "\" " +
        					filepath + 
        					" " + modifiedFormat +
        					" >> /var/log/tomcat7/survey.log 2>&1"});
					code = proc.waitFor();
					
	                log.info("Process exitValue: " + code);
	        		
	                if(code == 0) {
	                	File file = new File(filepath + ".zip");
	                	
	                	if(file.exists()) {
			            	builder = Response.ok(file);
			            	if(format.equals("kml")) {
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
				
				SDDataSource.closeConnection("surveyKPI-ExportSurvey", connectionSD);
				ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", connectionResults);
			}
		}
		
		return responseVal;
		
	}
	
	/*
	 * Generate Stata do file commands to convert date/time/geometry fields to stata format
	 */
	void writeStataDataConversion(PrintWriter w, ColDesc cd) {
		 if(cd.qType != null && cd.qType.equals("date")) {
			w.println("generate double `temp' = date(" + cd.name + ", \"YMD\")");		// Convert to double
			w.println("format %-tdCCYY-NN-DD `temp'");
		} else if(cd.qType != null && cd.qType.equals("time")) {
			w.println("generate double `temp' = clock(" + cd.name + ", \"hms\")");		// Convert to double
			w.println("format %-tcHH:MM:SS `temp'");
		} else if(cd.qType != null && cd.qType.equals("dateTime")) {
			w.println("generate double `temp' = clock(" + cd.name + ", \"YMDhms\")");		// Convert to double
			w.println("format %-tcCCYY-NN-DD_HH:MM:SS `temp'");
		} else if(cd.db_type.equals("timestamptz")) {
			w.println("generate double `temp' = clock(" + cd.name + ", \"YMDhms\")");	// Convert to double
			w.println("format %-tcCCYY-NN-DD_HH:MM:SS `temp'");							// Set the display format

		} else {
			return;		// Not a date / time / geometry question
		}
	 	
		// rename the temp file created by the date functions 
		w.println("move `temp' " + cd.name);										// Move to the location of the variable
		w.println("drop " + cd.name);												// Remove the old variable
		w.println("rename `temp' " + cd.name);										// Rename the temporary variable
	}
	
	void writeStataEncodeString(PrintWriter w, ColDesc cd, String valueLabel) {
		w.println("capture {");			// Capture errors as if there is no data then there will be a type mismatch
		for(int i = 0; i < cd.optionLabels.size(); i++) {
			OptionDesc od = cd.optionLabels.get(i);
			w.println("replace " + cd.name + " = \"" + od.label + "\" if (" + cd.name + " == \"" + od.value + "\")");	// Replace values with labels
		}
		w.println("encode " + cd.name + ", generate(`temp') label(" + valueLabel + ")");			// Encode the variable
		w.println("drop " + cd.name);												// Remove the old variable
		w.println("rename `temp' " + cd.name);										// Rename the temporary variable
		w.println("}");
	}
	
	void writeStataQuestionLabel(PrintWriter w, ColDesc cd) {
		if(cd.label != null) {
			w.println("label variable " + cd.name + " \"" + cd.label + "\"");			// Set the label
		}
	}
	
	



	

}
