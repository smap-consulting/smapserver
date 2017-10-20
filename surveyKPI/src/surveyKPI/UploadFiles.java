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
import org.smap.sdal.model.CustomReportItem;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.Survey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/upload")
public class UploadFiles extends Application {

	// Allow analysts and admin to upload resources for the whole organisation
	Authorise auth = null;

	LogManager lm = new LogManager();		// Application log

	public UploadFiles() {

		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		auth = new Authorise(authorisations, null);
	}

	private static Logger log =
			Logger.getLogger(UploadFiles.class.getName());


	@POST
	@Produces("application/json")
	@Path("/media")
	public Response sendMedia(
			@QueryParam("getlist") boolean getlist,
			@QueryParam("survey_id") int sId,
			@Context HttpServletRequest request
			) throws IOException {

		Response response = null;

		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String user = request.getRemoteUser();

		log.info("upload files - media -----------------------");

		fileItemFactory.setSizeThreshold(5*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);

		Connection connectionSD = null; 
		Connection cResults = null;
		boolean superUser = false;

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

					if(item.getFieldName().equals("survey_id")) {
						try {
							sId = Integer.parseInt(item.getString());
						} catch (Exception e) {

						}
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
					auth.isAuthorised(connectionSD, request.getRemoteUser());
					if(sId > 0) {
						try {
							superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
						} catch (Exception e) {
						}
						auth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
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
						
						String contentType = UtilityMethodsEmail.getContentType(fileName);
						
						String filePath = folderPath + "/" + fileName;
						File savedFile = new File(filePath);
						File oldFile = new File (filePath + ".old");
						
						// If this is a CSV file save the old version if it exists so that we can do a diff on it
						if(savedFile.exists() && (contentType.equals("text/csv") || fileName.endsWith(".csv"))) {
							Files.copy(savedFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
						}

						item.write(savedFile);  // Save the new file

						// Create thumbnails
						UtilityMethodsEmail.createThumbnail(fileName, folderPath, savedFile);

						// Apply changes from CSV files to survey definition
						if(contentType.equals("text/csv") || fileName.endsWith(".csv")) {
							applyCSVChanges(connectionSD, cResults, user, sId, fileName, savedFile, oldFile, basePath, mediaInfo);
						}

						if(getlist) {
							MediaResponse mResponse = new MediaResponse ();
							mResponse.files = mediaInfo.get();			
							Gson gson = new GsonBuilder().disableHtmlEscaping().create();
							String resp = gson.toJson(mResponse);
							log.info("Responding with " + mResponse.files.size() + " files");

							response = Response.ok(resp).build();
						} else {
							response = Response.ok().build();
						}

					} else {
						log.log(Level.SEVERE, "Media folder not found");
						response = Response.serverError().entity("Media folder not found").build();
					}


				}
			}

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
		auth.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation		

		try {
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String serverName = request.getServerName();

			deleteFile(request, connectionSD, basePath, serverName, null, oId, filename, request.getRemoteUser());

			MediaInfo mediaInfo = new MediaInfo();
			mediaInfo.setServer(request.getRequestURL().toString());
			mediaInfo.setFolder(basePath, request.getRemoteUser(), null, connectionSD, false);				 

			MediaResponse mResponse = new MediaResponse ();
			mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			response = Response.ok(resp).build();	

		} catch(Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
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
		auth.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation		

		try {

			String basePath = GeneralUtilityMethods.getBasePath(request);
			String serverName = request.getServerName(); 

			deleteFile(request, connectionSD, basePath, serverName, sIdent, 0, filename, request.getRemoteUser());

			MediaInfo mediaInfo = new MediaInfo();
			mediaInfo.setServer(request.getRequestURL().toString());
			mediaInfo.setFolder(basePath, 0, sIdent, connectionSD);

			MediaResponse mResponse = new MediaResponse ();
			mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			response = Response.ok(resp).build();	

		} catch(Exception e) {
			log.log(Level.SEVERE,e.getMessage(), e);
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
			@QueryParam("survey_id") int sId
			) throws IOException {

		Response response = null;
		String user = request.getRemoteUser();
		boolean superUser = false;

		/*
		 * Authorise
		 *  If survey ident is passed then check user access to survey
		 *  Else provide access to the media for the organisation
		 */
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
		if(sId > 0) {
			try {
				superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			} catch (Exception e) {
			}
			auth.isAuthorised(connectionSD, request.getRemoteUser());
			auth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		} else {
			auth.isAuthorised(connectionSD, request.getRemoteUser());
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

	/*
	 * Load oversight form
	 */
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
			FileItem fileItem = null;
			String filetype = null;
			String fieldName = null;
			String reportName = null;
			String reportType = null;

			while(itr.hasNext()) {

				FileItem item = (FileItem) itr.next();
				// Get form parameters	
				if(item.isFormField()) {
					fieldName = item.getFieldName();
					if(fieldName.equals("name")) {
						reportName = item.getString();
					} else if(fieldName.equals("type")) {
						reportType = item.getString();
					} 
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
							", File Name = "+item.getName()+
							", Content type = "+item.getContentType()+
							", File Size = "+item.getSize());

					fieldName = item.getFieldName();
					if(fieldName.equals("filename")) {
						fileName = item.getName();
						fileItem = item;
						int idx = fileName.lastIndexOf('.');
						if(reportName == null && idx > 0) {
							reportName = fileName.substring(0, idx);
						}

						if(fileName.endsWith("xlsx")) {
							filetype = "xlsx";
						} else if(fileName.endsWith("xls")) {
							filetype = "xls";
						} else {
							log.info("unknown file type for item: " + fileName);
						}
					}	
				}
			}

			// Authorisation - Access
			sd = SDDataSource.getConnection("Tasks-LocationUpload");
			auth.isAuthorised(sd, request.getRemoteUser());
			// End authorisation

			boolean isSecurityManager = GeneralUtilityMethods.hasSecurityRole(sd, request.getRemoteUser());

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);

			if(fileName != null) {

				// Process xls file
				XLSCustomReportsManager xcr = new XLSCustomReportsManager();
				ReportConfig config = xcr.getOversightDefinition(sd, 
						oId, 
						filetype, 
						fileItem.getInputStream(), 
						localisation, 
						isSecurityManager);

				/*
				 * Only save configuration if we found some columns, otherwise its likely to be an error
				 */
				if(config.columns.size() > 0) {

					// Save configuration to the database
					log.info("userevent: " + request.getRemoteUser() + " : upload custom report from xls file: " + fileName + " for organisation: " + oId);
					CustomReportsManager crm = new CustomReportsManager();

					Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
					String configString = gson.toJson(config);

					crm.save(sd, reportName, configString, oId, reportType);
					lm.writeLog(sd, 0, request.getRemoteUser(), "resources", config.columns.size() + " custom report definition uploaded from file " + fileName);

					ArrayList<CustomReportItem> reportsList = crm.getList(sd, oId, reportType, false);
					// Return custom report list			 
					String resp = gson.toJson(reportsList);

					response = Response.ok(resp).build();

				} else {
					response = Response.serverError().entity(localisation.getString("mf_nrc")).build();
				}
			} else {
				// This error shouldn't happen therefore no translation specified
				response = Response.serverError().entity("no file specified").build();
			}


		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			String msg = ex.getMessage();
			if(msg!= null && msg.contains("duplicate")) {
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
	 * Load an LQAS report
	 */
	@POST
	@Produces("application/json")
	@Path("/lqasreport")
	public Response sendLQASReport(
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
			FileItem fileItem = null;
			String filetype = null;
			String fieldName = null;
			String reportName = null;
			String reportType = null;

			while(itr.hasNext()) {

				FileItem item = (FileItem) itr.next();
				// Get form parameters	
				if(item.isFormField()) {
					fieldName = item.getFieldName();
					if(fieldName.equals("name")) {
						reportName = item.getString();
					} else if(fieldName.equals("type")) {
						reportType = item.getString();
					} 
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
							", File Name = "+item.getName()+
							", Content type = "+item.getContentType()+
							", File Size = "+item.getSize());

					fieldName = item.getFieldName();
					if(fieldName.equals("filename")) {
						fileName = item.getName();
						fileItem = item;
						int idx = fileName.lastIndexOf('.');
						if(reportName == null && idx > 0) {
							reportName = fileName.substring(0, idx);
						}

						if(fileName.endsWith("xlsx")) {
							filetype = "xlsx";
						} else if(fileName.endsWith("xls")) {
							filetype = "xls";
						} else {
							log.info("unknown file type for item: " + fileName);
						}
					}	
				}
			}

			// Authorisation - Access
			sd = SDDataSource.getConnection("Tasks-LocationUpload");
			auth.isAuthorised(sd, request.getRemoteUser());
			// End authorisation

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);

			if(fileName != null) {

				// Process xls file
				XLSCustomReportsManager xcr = new XLSCustomReportsManager();
				LQAS lqas = xcr.getLQASReport(sd, oId, filetype, fileItem.getInputStream(), localisation);


				/*
				 * Only save configuration if we found some columns, otherwise its likely to be an error
				 */
				if(lqas.dataItems.size() > 0) {

					// Save configuration to the database
					log.info("userevent: " + request.getRemoteUser() + " : upload custom report from xls file: " + fileName + " for organisation: " + oId);

					Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
					String configString = gson.toJson(lqas);

					CustomReportsManager crm = new CustomReportsManager();
					crm.save(sd, reportName, configString, oId, reportType);

					ArrayList<CustomReportItem> reportsList = crm.getList(sd, oId, "lqas", false);
					// Return custom report list			 
					String resp = gson.toJson(reportsList);

					response = Response.ok(resp).build();

				} else {
					response = Response.serverError().entity(localisation.getString("mf_nrc")).build();
				}
			} else {
				// This error shouldn't happen therefore no translation specified
				response = Response.serverError().entity("no file specified").build();
			}


		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			String msg = ex.getMessage();
			if(msg!= null && msg.contains("duplicate")) {
				msg = "A report with this name already exists";
			}
			log.info(ex.getMessage());
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
			File oldCsvFile,
			String basePath,
			MediaInfo mediaInfo) throws Exception {
		/*
		 * Find surveys that use this CSV file
		 */
		if(sId > 0) {  // TODO A specific survey has been requested

			applyCSVChangesToSurvey(connectionSD, cResults, user, sId, csvFileName, csvFile, oldCsvFile);

		} else {		// Organisational level

			// Get all the surveys that reference this CSV file and are in the same organisation
			SurveyManager sm = new SurveyManager();
			ArrayList<Survey> surveys = sm.getByOrganisationAndExternalCSV(connectionSD, user,	csvFileName);
			for(Survey s : surveys) {

				// Check that there is not already a survey level file with the same name				
				String surveyUrl = mediaInfo.getUrlForSurveyId(s.id, connectionSD);
				if(surveyUrl != null) {
					String surveyPath = basePath + surveyUrl + "/" + csvFileName;
					File surveyFile = new File(surveyPath);
					if(surveyFile.exists()) {
						continue;	// This survey has a survey specific version of the CSV file
					}
				}

				applyCSVChangesToSurvey(connectionSD, cResults, user, s.id, csvFileName, csvFile, oldCsvFile);
			}
		}
	}



	private void applyCSVChangesToSurvey(
			Connection connectionSD, 
			Connection cResults,
			String user, 
			int sId, 
			String csvFileName,
			File csvFile,
			File oldCsvFile) throws Exception {

		QuestionManager qm = new QuestionManager();
		SurveyManager sm = new SurveyManager();
		ArrayList<org.smap.sdal.model.Question> questions = qm.getByCSV(connectionSD, sId, csvFileName);
		ArrayList<ChangeSet> changes = new ArrayList<ChangeSet> ();
		
		String sql = "delete from option where l_id = ? and externalfile = 'true'";
		PreparedStatement pstmt = null;
		pstmt = connectionSD.prepareStatement(sql);

		try {
			// Create one change set per question
			for(org.smap.sdal.model.Question q : questions) {
	
				/*
				 * Create a changeset
				 */
				if(csvFile != null) {
					if(q.type.startsWith("select")) {		// Only add select multiples to ensure the column names are created
						ChangeSet cs = qm.getCSVChangeSetForQuestion(connectionSD, csvFile, oldCsvFile, csvFileName, q);
						if(cs.items.size() > 0) {
							changes.add(cs);
						}
					}
				} else {
					// File is being deleted just remove the external options for this questions
					pstmt.setInt(1, q.l_id);
					log.info("REmove external options: " + pstmt.toString());
					pstmt.executeUpdate();
				}
	
			}
	
			// Apply the changes 
			if(changes.size() > 0) {
				sm.applyChangeSetArray(connectionSD, cResults, sId, user, changes, false);
			} else {
				// No changes to the survey definition but we will update the survey version so that it gets downloaded with the new CSV data (pulldata only surveys will follow this path)
				GeneralUtilityMethods.updateVersion(connectionSD, sId);
			}
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

	}

	/*
	 * Delete the file
	 */
	private void deleteFile(
			HttpServletRequest request, 
			Connection sd, 
			String basePath, 
			String serverName, 
			String sIdent, 
			int oId, 
			String filename, 
			String user) throws Exception {

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

			// Apply changes from CSV files to survey definition	
			File f = new File(path);
			File oldFile = new File(path + ".old");
			String fileName = f.getName();
			
			// Delete options added to the database for this file
			if(fileName.endsWith(".csv")) {
				int sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
				MediaInfo mediaInfo = new MediaInfo();
				if(sId > 0) {
					mediaInfo.setFolder(basePath, sId, null, sd);
				} else {	
					// Upload to organisations folder
					mediaInfo.setFolder(basePath, user, null, sd, false);				 
				}
				mediaInfo.setServer(request.getRequestURL().toString());
				
				applyCSVChanges(sd, null, user, sId, fileName, null, null, basePath, mediaInfo);
			}

			f.delete();		
			if(oldFile.exists()) {
				oldFile.delete();	
			}
			
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


