package surveyKPI;

import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
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
import java.util.Locale;
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
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QueryManager;
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

import utilities.QuestionInfo;

/*
 * Exports media
 *  Pass in a list of question identifiers in order to generate the 
 *   name of the media file from the identifier
 *    
 */
@Path("/exportSurveyMedia/{sId}/{filename}")
public class ExportSurveyMedia extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyMedia.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	
	/*
	 * Export media in a zip file
	 */
	@GET
	public Response exportMedia (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("mediaquestion") int mediaQuestion,			
			@QueryParam("namequestions") String nameQuestionIdList,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@QueryParam("dateId") int dateId,
			@QueryParam("forms") String forms,
			@Context HttpServletResponse response
			) {

		ResponseBuilder builder = Response.ok();
		Response responseVal = null;
		ResourceBundle localisation = null;
		
		HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();
		
		log.info("userevent: " + request.getRemoteUser() + " Export media " + sId + " file to " + filename );
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    try {
		    	responseVal = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    } catch (Exception ex) {
		    	log.log(Level.SEVERE, "Exception", ex);
		    }
		}
		
		/*
		 * Get the list of forms and surveys to be exported
		 * Needs to be done prior to authorisation as it includes the list of surveys
		 */
		ArrayList<QueryForm> formList = null;
		
		if(forms != null) {
			Type type = new TypeToken<ArrayList<QueryForm>>(){}.getType();
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			formList = gson.fromJson(forms, type);
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurvey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		if(formList != null) {
			HashMap<Integer, String> checkedSurveys = new HashMap<Integer, String> ();
			for(int i = 0; i < formList.size(); i++) {
				int survey = formList.get(i).survey;
				if(checkedSurveys.get(new Integer(survey)) == null) {
					a.isValidSurvey(connectionSD, request.getRemoteUser(), formList.get(i).survey, false, superUser);
					checkedSurveys.put(new Integer(survey), "checked");
				}
			}
		} else {
			a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		}
		// End Authorisation

		lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "Export Media from a survey");
		
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

		if(sId != 0) {
			
			Connection connectionResults = null;
			PreparedStatement pstmtGetData = null;
			PreparedStatement pstmtIdent = null;
			
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

				String language = "none";
				
				/*
				 * Get the question names
				 */
				QuestionInfo mediaQInfo = new QuestionInfo(sId, mediaQuestion, connectionSD, false, language, urlprefix);	
				String media_name = mediaQInfo.getColumnName();
				ArrayList<String> namedQuestions = new ArrayList<String> ();
				ArrayList<String> requiredColumns = new ArrayList<String> ();
				requiredColumns.add("_prikey_lowest");	// Always get the lowest level primary key for media that is the key for the media item itself
				requiredColumns.add(media_name);			// Get the media question too
				if(nameQuestionIdList != null) {
					String nameQ [] = nameQuestionIdList.split(",");
					if(nameQ.length > 0) {
						for(int i = 0; i < nameQ.length; i++) {
							int nameQId = Integer.parseInt(nameQ[i]);
							QuestionInfo qi = new QuestionInfo(sId, nameQId, connectionSD, false, language, urlprefix);
							if(qi.getColumnName() != null) {
								namedQuestions.add(qi.getColumnName());
								requiredColumns.add(qi.getColumnName());
							}
						}
					}
				}
			
				/*
				 * Update the form list with additional info
				 */
				QueryManager qm = new QueryManager();
				if(formList == null) {
					formList = qm.getFormList(connectionSD, sId, mediaQInfo.getFId());
				} else {
					qm.extendFormList(connectionSD, formList);
				}
				
				// Get the SQL for this query
				SqlDesc sqlDesc = QueryGenerator.gen(connectionSD, 
						connectionResults,
						sId,
						mediaQInfo.getFId(),
						language, 
						"media", 
						urlprefix,
						false,
						false,
						false,
						labelListMap,
						false,
						false,
						null,
						requiredColumns,
						namedQuestions,
						request.getRemoteUser(),
						startDate,
						endDate,
						dateId,
						superUser,
						formList,
						formList.size() - 1);
				
				/*
				 * 1. Create the target folder
				 */
				String basePath = GeneralUtilityMethods.getBasePath(request);
				String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
				File folder = new File(filePath);
				folder.mkdir();
				
				log.info("Creating media export in folder: " + filePath);
				
				/*
				 * 2. Copy files to the folder, renaming them as per the export request
				 */
				pstmtGetData = connectionResults.prepareStatement(sqlDesc.sql); 
				log.info("Generated SQL:" + pstmtGetData.toString());
				ResultSet rs = pstmtGetData.executeQuery();
				while(rs.next()) {
					/*
					 * Get the target name
					 */
					String mediafilename = "";
					for(int k = 0; k < sqlDesc.availableColumns.size(); k++) {
						String v = rs.getString(sqlDesc.availableColumns.get(k));
						if(v != null) {
							if(mediafilename.trim().length() > 0) {
								mediafilename += "_";
							}
							mediafilename += v;
						}
					}
					String v = rs.getString("prikey");
					if(v != null) {
						if(mediafilename.trim().length() > 0) {
							mediafilename += "_";
						}
						mediafilename += v;
					}
					String source_file = rs.getString(media_name);
					
					if(source_file != null) {		// A media file may not exist for this record
					
						// Remove hostname if this is included (only for old data)
						if(source_file != null && !source_file.startsWith("attachments")) {
							int idx = source_file.indexOf("attachments");
							if(idx >= 0) {
								source_file = source_file.substring(source_file.indexOf("attachments"));
							}
						}
						int idx = source_file.lastIndexOf('.');
						String ext = "";
						if(idx >= 0) {
							ext = source_file.substring(idx);
						}
						mediafilename = mediafilename + ext;
						
						/*
						 * Copy the file
						 */
						String mf = basePath + "/" + source_file;
						File source = new File(mf);
						if (source.exists()) {
							File dest = new File(filePath + "/" + mediafilename);
							FileUtils.copyFile(source, dest);				
						} else {
							log.info("Error: media file does not exist: " + mf);
						}
					}
					
				}
				/*
				 * 3. Zip the file up
				 */
				int code = 0;
				//Process proc = Runtime.getRuntime().exec(new String [] {"/usr/bin/zip -rj ",filePath + ".zip ",filePath});
				
				Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/smap_bin/getshape.sh " + 
							database_name + " " +
							sqlDesc.target_table + " " +
							"\"" + sqlDesc.sql + "\" " +
        					filePath + 
        					" " + "media" +
        					" >> /var/log/tomcat7/survey.log 2>&1"});
        					
				code = proc.waitFor();
					
	            log.info("Process exitValue: " + code);
	        		
	            if(code == 0) {
	            	
	            	File file = new File(filePath + ".zip");
	            	if(file.exists()) {
		            	builder = Response.ok(file);
		            	builder.header("Content-Disposition", "attachment;Filename=\"" + escapedFileName + ".zip\"");
		            	responseVal = builder.build();
	            	} else {
	            		throw new ApplicationException(localisation.getString("msg_no_images"));
	            	}
		            
				} else {
	                log.info("Error exporting media files file");
	                responseVal = Response.serverError().entity("Error exporting media files").build();
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
				

				try {if (pstmtGetData != null) {pstmtGetData.close();}} catch (SQLException e) {}
				try {if (pstmtIdent != null) {pstmtIdent.close();}} catch (SQLException e) {}
				
				
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
