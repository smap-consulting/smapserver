	package surveyKPI;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.SqlDesc;

/*
 * Various types of export related to a survey
 *    
 */
@Path("/exportSurveyLocation/{sId}/{filename}")
public class ExportSurveyLocation extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyLocation.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	
	/*
	 * Export location and trail data as:
	 *    a) shape file
	 */
	@GET
	@Produces("application/x-download")
	public Response exportShape (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("form") int fId,
			@QueryParam("format") String format,
			@QueryParam("type") String type) {

		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		HashMap<ArrayList<OptionDesc>, String> labelListMap = new  HashMap<ArrayList<OptionDesc>, String> ();
		
		log.info("userevent: " + request.getRemoteUser() + " Location Export " + sId + " as a "+ format + " file to " + filename + " starting from form " + fId);	
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    try {
		    	response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    } catch (Exception ex) {
		    	log.log(Level.SEVERE, "Exception", ex);
		    }
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurvey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "Export to " + format);
		
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

		PreparedStatement pstmtGetIdent = null;
		
		if(sId != 0) {
			
			try {
		
				String ident = null;
				String sqlGetIdent = "select ident from survey where s_id = ?;"; 
		    	pstmtGetIdent = connectionSD.prepareStatement(sqlGetIdent);
		    	pstmtGetIdent.setInt(1, sId);
		    	ResultSet rs = pstmtGetIdent.executeQuery();
		    	if(rs.next()) {
		    		ident = rs.getString(1);
		    	} else {
		    		log.info("Error: ident not found");
		    	}
		    	
				/*
				 * Get the name of the database
				 */
				DatabaseMetaData databaseMetaData = connectionSD.getMetaData();
				String dbUrl = databaseMetaData.getURL();
				String database_name = dbUrl.substring(dbUrl.lastIndexOf('/') + 1);

				SqlDesc sqlDesc = new SqlDesc();
				
				if(type.equals("trail")) {
					sqlDesc.target_table = "user_trail";
					sqlDesc.sql = "select u.name, ut.device_id, timezone('UTC', ut.event_time), " +
							" ut.the_geom as the_geom " +
							" from users u, user_trail ut " +
							" where u.id = ut.u_id " +
							" order by ut.id asc";
				} else if(type.equals("event")) {
					sqlDesc.target_table = "task_completion";
					log.info("Location export error: un supported type: " + type);
				} else {
					log.info("Location export error: unknown type: " + type);
				}
	
				String basePath = GeneralUtilityMethods.getBasePath(request);
				String filepath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
				
				log.info("Table: " + sqlDesc.target_table);
				log.info("SQL: " + sqlDesc.sql);
				int code = 0;
				Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/usr/bin/smap/getshape.sh " + 
						database_name + " " +
						sqlDesc.target_table + " " +
						"\"" + sqlDesc.sql + "\" " +
        				filepath + 
        				" " + format +
        				" >> /var/log/tomcat7/survey.log 2>&1"});
				code = proc.waitFor();
					
	            log.info("Process exitValue: " + code);
	        		
                if(code == 0) {
                	File file = new File(filepath + ".zip");
	                byte [] fileData = new byte[(int)file.length()];
	                DataInputStream dis = new DataInputStream(new FileInputStream(file));
	                dis.readFully(fileData);
	                dis.close();
	                
	                builder.header("Content-type","application/zip");
					builder.entity(fileData);
					response = builder.build();
                } else {
                	log.info("Error exporting file");
                	response = Response.serverError().entity("Error exporting file").build();
                }
			
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
				response = Response.serverError().entity(e.getMessage()).build();
			} catch (Exception e) {
				response = Response.serverError().entity(e.getMessage()).build();
				log.log(Level.SEVERE, "Exception", e);
			} finally {
				
				if(pstmtGetIdent != null) try {pstmtGetIdent.close();} catch(Exception e) {};
				
				SDDataSource.closeConnection("surveyKPI-ExportSurvey", connectionSD);		
				
			}
		}
		
		return response;
		
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
