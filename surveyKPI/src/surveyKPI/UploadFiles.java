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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
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
import utilities.XLSCustomReportsManager;
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
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QuestionManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/upload")
public class UploadFiles extends Application {
	
	// Allow analysts and admin to upload resources for the whole organisation
	Authorise surveyLevelAuth = null;
	Authorise orgLevelAuth = null;
	
	LogManager lm = new LogManager();		// Application log
	
	public UploadFiles() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		orgLevelAuth = new Authorise(authorisations, null);	
		surveyLevelAuth = new Authorise(authorisations, null);
	}
	
	private static Logger log =
			 Logger.getLogger(UploadFiles.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UploadFiles.class);
		return s;
	}

			
	@POST
	@Produces("application/json")
	@Path("/media")
	public Response sendMedia(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String serverName = request.getServerName();
		String user = request.getRemoteUser();

		String original_url = "/edit.html?mesg=error loading media file";
		int sId = -1;
		//String settings = "false";
	
		log.info("upload files - media -----------------------");
		log.info("    Server:" + serverName);
		
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
				
					if(item.getFieldName().equals("original_url")) {
						original_url = item.getString();
						log.info("original url:" + original_url);
					} else if(item.getFieldName().equals("survey_id")) {
						try {
							sId = Integer.parseInt(item.getString());
						} catch (Exception e) {
							
						}
						log.info("surveyId:" + sId);
					}
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
	
					// Authorisation - Access
					connectionSD = SDDataSource.getConnection("surveyKPI - uploadFiles - sendMedia");
					if(sId > 0) {
						surveyLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
						surveyLevelAuth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
					} else {
						orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
					}
					// End authorisation
					
					cResults = ResultsDataSource.getConnection("surveyKPI - uploadFiles - sendMedia");
					
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
	
			SDDataSource.closeConnection("surveyKPI - uploadFiles - sendMedia", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI - uploadFiles - sendMedia", cResults);
		}
		
		return response;
		
	}
	
	@DELETE
	@Produces("application/json")
	@Path("/media/organisation/{oId}/{filename}")
	public Response deleteMedia(
			@PathParam("oId") int oId,
			@PathParam("filename") String filename,
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
		orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation		

		try {
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String serverName = request.getServerName();
			
			
			deleteFile(basePath, serverName, null, oId, filename, request.getRemoteUser());
			
			MediaInfo mediaInfo = new MediaInfo();
			mediaInfo.setServer(request.getRequestURL().toString());
			mediaInfo.setFolder(basePath, request.getRemoteUser(), null, connectionSD, false);				 
		
			MediaResponse mResponse = new MediaResponse ();
		    mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			log.info("Responding with " + mResponse.files.size() + " files");
			response = Response.ok(resp).build();	
			
		} catch(Exception e) {
			e.printStackTrace();
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-UploadFiles", connectionSD);
		}
		
		return response;
		
	}
	
	@DELETE
	@Produces("application/json")
	@Path("/media/{sIdent}/{filename}")
	public Response deleteMediaSurvey(
			@PathParam("sIdent") String sIdent,
			@PathParam("filename") String filename,
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
		orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation		

		try {
			
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String serverName = request.getServerName(); 
			
			deleteFile(basePath, serverName, sIdent, 0, filename, request.getRemoteUser());
			
			MediaInfo mediaInfo = new MediaInfo();
			mediaInfo.setServer(request.getRequestURL().toString());
			mediaInfo.setFolder(basePath, 0, sIdent, connectionSD);
			
			MediaResponse mResponse = new MediaResponse ();
		    mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			log.info("Responding with " + mResponse.files.size() + " files");
			response = Response.ok(resp).build();	
			
		} catch(Exception e) {
			e.printStackTrace();
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-UploadFiles", connectionSD);
		}
		
		return response;
		
	}
	/*
	 * Return available files
	 */
	@GET
	@Produces("application/json")
	@Path("/media")
	public Response getMedia(
			@Context HttpServletRequest request,
			@QueryParam("sId") int sId
			) throws IOException {
		
		Response response = null;
		String serverName = request.getServerName();
		String user = request.getRemoteUser();
		
		/*
		 * Authorise
		 *  If survey ident is passed then check user access to survey
		 *  Else provide access to the media for the organisation
		 */
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
		if(sId > 0) {
			surveyLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
			surveyLevelAuth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		} else {
			orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
		}
		// End Authorisation		
		
		/*
		 * Get the path to the files
		 */
		String basePath = GeneralUtilityMethods.getBasePath(request);
	
		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setServer(request.getRequestURL().toString());

		PreparedStatement pstmt = null;		
		try {
					
			// Get the path to the media folder	
			if(sId > 0) {
				mediaInfo.setFolder(basePath, sId, null, connectionSD);
			} else {		
				mediaInfo.setFolder(basePath, user, null, connectionSD, false);				 
			}
			
			log.info("Media query on: " + mediaInfo.getPath());
				
			MediaResponse mResponse = new MediaResponse();
			mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			response = Response.ok(resp).build();		
			
		}  catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().build();
		} finally {
	
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}

			SDDataSource.closeConnection("surveyKPI-UploadFiles", connectionSD);
		}
		
		return response;		
	}
	
	@POST
	@Produces("application/json")
	@Path("/customreport")
	public Response sendCustomReport(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		Connection sd = null; 

		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			String fileName = null;
			String reportName = null;
			FileItem fileItem = null;
			String filetype = null;

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
					
					fileName = item.getName();
					fileItem = item;
					int idx = fileName.lastIndexOf('.');
					if(idx > 0) {
						reportName = fileName.substring(0, idx);
					}
					
					if(fileName.endsWith("xlsx")) {
						filetype = "xlsx";
					} else if(fileName.endsWith("xls")) {
						filetype = "xls";
					} else {
						log.info("unknown file type for item: " + fileName);
						continue;	
					}
	
					break;
						
				}
			}
			
			if(fileName != null) {
				// Authorisation - Access
				sd = SDDataSource.getConnection("Tasks-LocationUpload");
				orgLevelAuth.isAuthorised(sd, request.getRemoteUser());
				// End authorisation
				
				// Process xls file
				XLSCustomReportsManager xcr = new XLSCustomReportsManager();
				ArrayList<TableColumn> config = xcr.getCustomReport(filetype, fileItem.getInputStream());
				
				/*
				 * Only save configuration if we found some columns, otherwise its likely to be an error
				 */
				if(config.size() > 0) {
					
					// Save configuration to the database
					int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
					log.info("userevent: " + request.getRemoteUser() + " : upload custom report from xls file: " + fileName + " for organisation: " + oId);
					CustomReportsManager rm = new CustomReportsManager();
					rm.save(sd, reportName, config, oId, "oversight");
					lm.writeLog(sd, 0, request.getRemoteUser(), "resources", config.size() + " custom report definition uploaded from file " + fileName);
					
					// Return custom report list			 
					Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
					String resp = gson.toJson(config);
				
					response = Response.ok(resp).build();
					
				} else {
					response = Response.serverError().entity("no report columns found").build();
				}
			} else {
				response = Response.serverError().entity("no file found").build();
			}
			
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			String msg = ex.getMessage();
			if(msg.contains("duplicate")) {
				msg = "A report with this name already exists";
			}
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(msg).build();
		} finally {
	
			SDDataSource.closeConnection("Tasks-LocationUpload", sd);
			
		}
		
		return response;
		
	}
	
	/*
	 * Update the survey with any changes resulting from the uploaded CSV file
	 */
	private void applyCSVChanges(Connection connectionSD, 
			Connection cResults,
			String user, 
			int sId, 
			String csvFileName, 
			File csvFile,
			String basePath,
			MediaInfo mediaInfo) throws Exception {
		/*
		 * Find surveys that use this CSV file
		 */
		if(sId > 0) {  // TODO A specific survey has been requested
			
			applyCSVChangesToSurvey(connectionSD, cResults, user, sId, csvFileName, csvFile);
			
		} else {		// Organisational level
			
			log.info("Organisational Level");
			// Get all the surveys that reference this CSV file and are in the same organisation
			SurveyManager sm = new SurveyManager();
			ArrayList<Survey> surveys = sm.getByOrganisationAndExternalCSV(connectionSD, user,	csvFileName);
			for(Survey s : surveys) {
				
				log.info("Survey: " + s.id);
				// Check that there is not already a survey level file with the same name				
				String surveyUrl = mediaInfo.getUrlForSurveyId(sId, connectionSD);
				if(surveyUrl != null) {
					String surveyPath = basePath + surveyUrl + "/" + csvFileName;
					File surveyFile = new File(surveyPath);
					if(surveyFile.exists()) {
						continue;	// This survey has a survey specific version of the CSV file
					}
				}
					
				applyCSVChangesToSurvey(connectionSD, cResults, user, s.id, csvFileName, csvFile);
			}
		}
	}
	
	
	
	private void applyCSVChangesToSurvey(Connection connectionSD, 
			Connection cResults,
			String user, 
			int sId, 
			String csvFileName,
			File csvFile) throws Exception {
		
		log.info("About to update: " + sId);
		QuestionManager qm = new QuestionManager();
		SurveyManager sm = new SurveyManager();
		ArrayList<org.smap.sdal.model.Question> questions = qm.getByCSV(connectionSD, sId, csvFileName);
		ArrayList<ChangeSet> changes = new ArrayList<ChangeSet> ();
		
		// Create one change set per question
		for(org.smap.sdal.model.Question q : questions) {
			
			log.info("Updating question: " + q.name + " : " + q.type);
			
			/*
			 * Create a changeset
			 */
			ChangeSet cs = qm.getCSVChangeSetForQuestion(connectionSD, csvFile, csvFileName, q);
			if(cs.items.size() > 0) {
				changes.add(cs);
			}
			
		}
		 
		// Apply the changes 
		sm.applyChangeSetArray(connectionSD, cResults, sId, user, changes);
		      
	}
	
	/*
	 * Delete the file
	 */
	private void deleteFile(String basePath, String serverName, String sIdent, int oId, String filename, String user) {
		
		String path = null;
		String thumbsFolder = null;
		String fileBase = null;
		
		int idx = filename.lastIndexOf('.');
		if(idx >= 0) {
			fileBase = filename.substring(0, idx);
		}
		
		if(filename != null) {
			if(sIdent != null) {
				path = basePath + "/media/" + sIdent + "/" + filename;
				if(fileBase != null) {
					thumbsFolder = basePath + "/media/" + sIdent + "/thumbs";
				}
			} else if( oId > 0) {
				path = basePath + "/media/organisation/" + oId + "/" + filename;
				if(fileBase != null) {
					thumbsFolder = basePath + "/media/organisation/" + oId + "/thumbs";
				}
			}
			
			File f = new File(path);
			f.delete();
			log.info("userevent: " + user + " : delete media file : " + filename);
			
			// Delete any matching thumbnails
			if(fileBase != null) {
				File thumbs = new File(thumbsFolder);
				for(File thumb : thumbs.listFiles()) {
					if(thumb.getName().startsWith(fileBase)) {
						thumb.delete();
					}
				}
			}
			
		}
		
			
	}

}


