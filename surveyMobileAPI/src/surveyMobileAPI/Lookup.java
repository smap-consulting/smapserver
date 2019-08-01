/*****************************************************************************

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

 ******************************************************************************/

package surveyMobileAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.notifications.interfaces.ImageProcessing;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.CsvTableManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.SelectChoice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/*
 * Requests for realtime data from a form
 */

@Path("/lookup")
public class Lookup extends Application{

	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(Lookup.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	private final String CONTAINS = "contains";
	private final String MATCHES = "matches";
	private final String IN = "in";
	private final String STARTS = "startswith";
	private final String ENDS = "endswith";
	
	/*
	 * Get a record from the reference data identified by the filename and key column
	 */
	@GET
	@Path("/{survey_ident}/{filename}/{key_column}/{key_value}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response lookup(@Context HttpServletRequest request,
			@PathParam("survey_ident") String surveyIdent,		// Survey that needs to lookup some data
			@PathParam("filename") String fileName,				// CSV filename, could be the identifier of another survey
			@PathParam("key_column") String keyColumn,
			@PathParam("key_value") String keyValue
			) throws IOException {

		Response response = null;
		String connectionString = "surveyMobileAPI-Lookup";
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		int sId = 0;
		
		log.info("Lookup: Filename=" + fileName + " key_column=" + keyColumn + " key_value=" + keyValue);

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);		
		a.isAuthorised(sd, request.getRemoteUser());
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
			sId = GeneralUtilityMethods.getSurveyId(sd, surveyIdent);
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		Connection cResults = null;
		PreparedStatement pstmt = null;
		
		// Extract the data
		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);			
		
			String tz = "UTC";
			
			HashMap<String, String> results = null;
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			if(fileName != null) {
				if(fileName.startsWith("linked_s")) {
					// Get data from a survey
					cResults = ResultsDataSource.getConnection(connectionString);				
					SurveyTableManager stm = new SurveyTableManager(sd, cResults, localisation, oId, sId, fileName, request.getRemoteUser());
					stm.initData(pstmt, "lookup", keyColumn, keyValue, null, null, null, tz);
					results = stm.getLineAsHash();
				} else {
					// Get data from a csv file
					CsvTableManager ctm = new CsvTableManager(sd, localisation);
					results = ctm.lookup(oId, sId, fileName + ".csv", keyColumn, keyValue);
				}
			}
			if (results == null) {
				results =  new HashMap<> ();
			}
			response = Response.ok(gson.toJson(results)).build();
		
		}  catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}  finally {
			if(pstmt != null) {try{pstmt.close();}catch(Exception e) {}} 
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
		}
				
		return response;
	}
	
	/*
	 * Get external choices
	 */
	@GET
	@Path("/choices/{survey_ident}/{filename}/{value_column}/{label_columns}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response choices(@Context HttpServletRequest request,
			@PathParam("survey_ident") String surveyIdent,		// Survey that needs to lookup some data
			@PathParam("filename") String fileName,				// CSV filename, could be the identifier of another survey
			@PathParam("value_column") String valueColumn,
			@PathParam("label_columns") String labelColumns,
			@QueryParam("search_type") String searchType,
			@QueryParam("q_column") String qColumn,
			@QueryParam("q_value") String qValue,
			@QueryParam("f_column") String fColumn,
			@QueryParam("f_value") String fValue			
			) throws IOException {

		Response response = null;
		String connectionString = "surveyMobileAPI-Lookup-choices";
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		int sId = 0;
		
		log.info("Lookup choices: Filename=" + fileName + " value_column=" + valueColumn + " label_column=" + labelColumns);

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);		
		a.isAuthorised(sd, request.getRemoteUser());
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
			sId = GeneralUtilityMethods.getSurveyId(sd, surveyIdent);
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		Connection cResults = null;
		PreparedStatement pstmt = null;
		
		// Extract the data
		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);			
		
			String tz = "UTC";
			
			// Clean the data
			ArrayList<String> whereColumns = new ArrayList<String> ();
			if(searchType != null) {
				searchType = searchType.trim().toLowerCase();
			}
			if(qColumn != null) {
				qColumn = qColumn.trim();
				whereColumns.add(qColumn);
			}
			if(qValue != null) {
				qValue = qValue.trim();
			}
			if(fValue != null) {
				fValue = fValue.trim();
			}
			if(fColumn != null) {
				fColumn = fColumn.trim();
				whereColumns.add(fColumn);
			}
			// Create a where clause and where parameters
			StringBuffer selection = new StringBuffer("");
			String selectionString = null;
			ArrayList<String> arguments = new ArrayList<String> ();
			
			if (searchType != null && fColumn != null) {
	            selection.append("( ").append(createLikeExpression(qColumn, qValue, searchType, arguments)).append(" ) and ");
	            selection.append(fColumn).append(" = ? ");
	            arguments.add(fValue);
	        } else if (searchType != null) {
	            selection.append(createLikeExpression(qColumn, qValue, searchType, arguments));    // smap
	        } else if (fColumn != null) {
	            selection.append(fColumn).append(" = ? ");
	            arguments.add(fValue);
	        } else {
	            arguments = null;
	        }
			if(selection.length() > 0) {
				selectionString = selection.toString();
			} else {
				selectionString = null;
			}
			
			ArrayList<SelectChoice> results = null;
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			if(fileName != null) {
				if(fileName.startsWith("linked_s")) {
					// Get data from a survey
					cResults = ResultsDataSource.getConnection(connectionString);				
					SurveyTableManager stm = new SurveyTableManager(sd, cResults, localisation, oId, sId, fileName, request.getRemoteUser());
					stm.initData(pstmt, "choices", null, null,
							selectionString, arguments, whereColumns, tz);
					
					HashMap<String, String> line = null;
					int idx = 0;
					results = new ArrayList<SelectChoice> ();
					while((line = stm.getLineAsHash()) != null) {
						String[] lArray = labelColumns.split(",");
						StringBuffer lOutput = new StringBuffer("");
						for(String l : lArray) {
							if(lOutput.length() > 0) {
								lOutput.append(", ");
							}
							lOutput.append(line.get(l.trim()));
						}
						SelectChoice choice = new SelectChoice(line.get(valueColumn), lOutput.toString(), idx++);
						results.add(choice);
					}
				} else {
					// Get data from a csv file
					CsvTableManager ctm = new CsvTableManager(sd, localisation);
					results = ctm.lookupChoices(oId, sId, fileName + ".csv", valueColumn, labelColumns, 
							selectionString, arguments, whereColumns);
				}
			}
			if (results == null) {
				results =  new ArrayList<SelectChoice> ();
			}
			response = Response.ok(gson.toJson(results)).build();
		
		}  catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}  finally {
			if(pstmt != null) {try{pstmt.close();}catch(Exception e) {}} 
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
		}
				
		return response;
	}
	
	/*
	 * Get get labels from an image
	 */
	@POST
	@Path("/imagelabels/{survey_ident}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response imageLookup(@Context HttpServletRequest request,
			@PathParam("survey_ident") String surveyIdent		// Survey that needs to lookup image label
			) throws IOException {
		
		Response response = null;
		
		String connectionString = "surveyMobileAPI-Lookup-imagelabels";
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		int sId = 0;
		
		// Authorisation - Access
		
		Connection sd = SDDataSource.getConnection(connectionString);		
		a.isAuthorised(sd, request.getRemoteUser());
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
			sId = GeneralUtilityMethods.getSurveyId(sd, surveyIdent);
		} catch (Exception e) {
		}
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser); 
		
		
		HashMap<String, String> results = null;
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		/*
		 * Parse the request
		 */
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();
		fileItemFactory.setSizeThreshold(5*1024*1024);
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
		try {
					
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			File savedFile = null;
			String contentType = null;
			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
	
				if(!item.isFormField()) {
					if(!item.isFormField()) {
						String fileName = item.getName();
						contentType = item.getContentType();
						String tempFileName = UUID.randomUUID().toString();
						String filePath = basePath + "/temp/" + tempFileName;								
						savedFile = new File(filePath);
						item.write(savedFile);  // Save the new file
					}
				}
			}		

			if(savedFile == null) {
				throw new ApplicationException("File not loaded");
			}
			
			if(contentType == null || !contentType.startsWith("image")) {
				// Rekognition supports what? TODO
				throw new ApplicationException("Content type not supported: " + contentType);
			}
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);				

			ImageProcessing ip = new ImageProcessing();		// Can this be handled in a singleton
			String labels = ip.getLabels(request.getServerName(), request.getRemoteUser(), savedFile.getAbsolutePath(), "text");
			System.out.println("Labels: " + labels);
			response = Response.ok(labels).build();
			lm.writeLog(sd, sId, request.getRemoteUser(), "Rekognition Request", "Online for survey: " + surveyIdent);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}  finally {
			SDDataSource.closeConnection(connectionString, sd);
		}
		
		return response;
	}

	// Based on function in odkCollect
    private String createLikeExpression(String qColumn,  String qValue, String type, ArrayList<String> arguments) {
    		
	    	StringBuilder sb = new StringBuilder();
    		type = type.trim().toLowerCase();
    		
    		if(type.equals(IN)) {
    			sb.append(qColumn).append(" in (");
    			
    			String [] values = qValue.split(",");
    			if(values.length == 1 && qValue.contains(" ")) {
    				values = qValue.split(" ");
    			}
    			int idx = 0;
    			for (String v : values) {
    				if (idx++ > 0) {
    					sb.append(", ");
    				}
    				sb.append("?");
    				arguments.add(v);
    			}
    			sb.append(")");
    			
    		} else {
    			sb.append(qColumn).append(" LIKE ? ");
    			if(type.equals(MATCHES)) {
    				arguments.add(qValue);
    			} else if(type.equals(CONTAINS)) {
    				arguments.add("%" + qValue + "%");
    			} else if(type.equals(STARTS)) {
    				arguments.add(qValue + "%");
    			} else if(type.equals(ENDS)) {
    				arguments.add("%" + qValue);
    			}
    		}
    		return sb.toString();

    }
}

