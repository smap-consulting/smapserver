package surveyKPI;

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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import model.MediaResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.MediaInfo;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.PDFManager;
import org.smap.sdal.managers.SurveyManager;

import utilities.XLSFormManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

/*
 * Manages Tasks
 */

@Path("/tasks")
public class Tasks extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Tasks.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}

	@POST
	@Produces("application/json")
	@Path("/upload")
	public Response uploadTasksFromFile(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String serverName = request.getServerName();
		String user = request.getRemoteUser();

	
		log.info("upload tasks -----------------------");
		
		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection connectionSD = null; 
		Connection cResults = null;

		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();

			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				// Get form parameters
				
				if(item.isFormField()) {
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
	
					// Authorisation - Access
					connectionSD = SDDataSource.getConnection("fieldManager-MediaUpload");
					
					orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());

					// End authorisation
					
					cResults = ResultsDataSource.getConnection("fieldManager-MediaUpload");
					
					String basePath = GeneralUtilityMethods.getBasePath(request);
					
					MediaInfo mediaInfo = new MediaInfo();
					if(sId > 0) {
						mediaInfo.setFolder(basePath, sId, null, connectionSD);
					} else {	
						// Upload to organisations folder
						mediaInfo.setFolder(basePath, user, null, connectionSD, false);				 
					}
					mediaInfo.setServer(request.getRequestURL().toString());
					
					String folderPath = mediaInfo.getPath();
					fileName = mediaInfo.getFileName(fileName);

					if(folderPath != null) {						
						String filePath = folderPath + "/" + fileName;
					    File savedFile = new File(filePath);
					    log.info("Saving file to: " + filePath);
					    item.write(savedFile);
					    
					    // Create thumbnails
					    UtilityMethodsEmail.createThumbnail(fileName, folderPath, savedFile);
					    
					    // Apply changes from CSV files to survey definition
					    String contentType = UtilityMethodsEmail.getContentType(fileName);
					    if(contentType.equals("text/csv")) {
					    	applyCSVChanges(connectionSD, cResults, user, sId, fileName, savedFile, basePath, mediaInfo);
					    }
					    
					    MediaResponse mResponse = new MediaResponse ();
					    mResponse.files = mediaInfo.get();			
						Gson gson = new GsonBuilder().disableHtmlEscaping().create();
						String resp = gson.toJson(mResponse);
						log.info("Responding with " + mResponse.files.size() + " files");
						
						response = Response.ok(resp).build();	
						
					} else {
						log.log(Level.SEVERE, "Media folder not found");
						response = Response.serverError().entity("Media folder not found").build();
					}

						
				}
			}
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
		
	}
	
	@GET
	@Produces("application/x-download")
	public Response getXLSFormService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@QueryParam("filetype") String filetype) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
				
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation 
		
		SurveyManager sm = new SurveyManager();
		org.smap.sdal.model.Survey survey = null;
		Connection cResults = ResultsDataSource.getConnection("createPDF");
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			
			// Get the survey details
			survey = sm.getById(connectionSD, cResults, request.getRemoteUser(), sId, true, basePath, null, false, false, false, false);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(survey.displayName + "." + filetype, response);
			
			// Create XLSForm
			XLSFormManager xf = new XLSFormManager(filetype);
			xf.createXLSForm(response.getOutputStream(), survey);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		return Response.ok("").build();
	}
	

}
