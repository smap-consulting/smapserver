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
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ExternalFileManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeElement;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.Question;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/surveys")
public class Surveys extends Application {

	Authorise aGet = null;
	Authorise aUpdate = null;
	
	private static Logger log =
			 Logger.getLogger(Surveys.class.getName());

	
	public Surveys() {
		
		ArrayList<String> authorisations1 = new ArrayList<String> ();
		ArrayList<String> authorisations2 = new ArrayList<String> ();
		
		authorisations1.add(Authorise.ANALYST);
		authorisations1.add(Authorise.ADMIN);
		authorisations1.add(Authorise.ENUM);
		
		authorisations2.add(Authorise.ANALYST);
		authorisations2.add(Authorise.ADMIN);
		
		aGet = new Authorise(authorisations1, null);
		aUpdate = new Authorise(authorisations2, null);
		
	}

	// JSON
	@GET
	@Produces("application/json")
	public Response getSurveys(@Context HttpServletRequest request,
			@QueryParam("deleted") boolean getDeleted,
			@QueryParam("blocked")  boolean getBlocked,
			@QueryParam("projectId") int projectId
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");
		if(getDeleted) {
			aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		} else {
			aGet.isAuthorised(connectionSD, request.getRemoteUser());
		}
		aGet.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		ArrayList<org.smap.sdal.model.Survey> surveys = null;
		
		Response response = null;
		PreparedStatement pstmt = null;
		SurveyManager sm = new SurveyManager();
		try {
			boolean superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			
			surveys = sm.getSurveys(connectionSD, pstmt,
					request.getRemoteUser(), 
					getDeleted, 
					getBlocked, 
					projectId,
					superUser);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(surveys);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Surveys", connectionSD);
			
		}

		return response;
	}
	
	/*
	 * Get details on a survey
	 * Used to get the data in order to edit the survey
	 */
	@GET
	@Path("/{sId}")
	@Produces("application/json")
	public Response getSurveyDetails(@Context HttpServletRequest request,
			@PathParam("sId") int sId
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");	
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		aUpdate.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		Response response = null;
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Surveys");
		SurveyManager sm = new SurveyManager();
		try {
			
			survey = sm.getById(connectionSD, cResults,  request.getRemoteUser(), sId, 
					true, 
					basePath, 
					null, 
					false, 
					false, 
					true, 
					true,
					"internal",
					superUser,
					0,
					null);
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-Surveys", connectionSD);	
			ResultsDataSource.closeConnection("surveyKPI-Surveys", cResults);
			
		}

		return response;
	}
	
	/*
	 * Create a new survey
	 */
	@POST
	@Path("/new/{project}/{name}")
	@Produces("application/json")
	public Response createNewSurvey(@Context HttpServletRequest request,
			@PathParam("project") int projectId,
			@PathParam("name") String name,
			@FormParam("existing") boolean existing,
			@FormParam("existing_survey") int existingSurveyId,
			@FormParam("existing_form") int existingFormId,
			@FormParam("shared_results") boolean sharedResults
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		log.info("userevent: " + request.getRemoteUser() + " create new survey " + name + " (" + existing + "," + 
				existingSurveyId + "," + existingFormId + ")");
		
		// Authorisation - Access
		boolean superUser = false;
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		aUpdate.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		if(existing) {
			try {
				superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			} catch (Exception e) {
			}
			aUpdate.isValidSurvey(connectionSD, request.getRemoteUser(), existingSurveyId, false, superUser);	// Validate that the user can access the existing survey
		}
		// End Authorisation
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		log.info("userevent: " + request.getRemoteUser() + " create new survey " + name + " (" + existing + "," + 
				existingSurveyId + "," + existingFormId + ")");
		
		Response response = null;
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Surveys");
		SurveyManager sm = new SurveyManager();
		try {
			int sId = sm.createNewSurvey(connectionSD, name, projectId, existing, existingSurveyId, existingFormId, sharedResults);
			// Get the survey details.  superUser set to true as this user just created the survey so they are effectively a super user for this survey and we can save a database call
			survey = sm.getById(connectionSD, cResults,  request.getRemoteUser(), sId, true, 
					basePath, null, false, false, true, true, "internal", true, 0, null);
			log.info("userevent: " + request.getRemoteUser() + " : create empty survey : " + name + " in project " + projectId);
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			
			if(e.getMessage().contains("duplicate key")) {
				String msg = "There is already a form called " + name + " in this project";
				response = Response.status(Status.NO_CONTENT).entity(msg).build();
				log.info(msg + e.getMessage());
			} else {
				log.log(Level.SEVERE, "SQL Error", e);
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-Surveys", connectionSD);	
			ResultsDataSource.closeConnection("surveyKPI-Surveys", cResults);
			
		}

		return response;
	}
	

	/*
	 * Update the survey languages
	 */
	@Path("/save_languages/{sId}")
	@POST
	public Response saveLanguages(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("languages") String languages) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Surveys");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(sd, request.getRemoteUser());
		aUpdate.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
		
		SurveyManager sm = new SurveyManager();
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		try {
			/*
			 * Parse the request
			 */
			Type type = new TypeToken<ArrayList<Language>>(){}.getType();
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			ArrayList<Language> languageList = gson.fromJson(languages, type);
			
			// Update the languages
			GeneralUtilityMethods.setLanguages(sd, sId, languageList);
			GeneralUtilityMethods.setMediaForLanguages(sd, sId, languageList);	// Cope with media being duplicated across all languages
			// Get the survey details.  superUser set to true as this user just edited the survey so they are effectively a super user for this survey and we can save a databse call
			org.smap.sdal.model.Survey  survey = sm.getById(sd, null,  request.getRemoteUser(), sId, true, 
					basePath, null, false, false, true, true, "internal", true, 0, null);
			
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"sql error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception loading settings", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-Surveys", sd);
			
		}

		return response;
	}
	
	/*
	 * Update the survey languages
	 */
	@Path("/save_pulldata/{sId}")
	@POST
	public Response savePulldata(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("pulldata") String pulldata) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Surveys");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(sd, request.getRemoteUser());
		aUpdate.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
		
		String sql = "update survey set pulldata = ? where s_id = ?;"; 
		PreparedStatement pstmt = null;
		
		try {
			/*
			 * Parse the request
			 */
			Type type = new TypeToken<ArrayList<Pulldata>>(){}.getType();
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			ArrayList<Pulldata> pulldataList = gson.fromJson(pulldata, type);
			
			String parsedPd = gson.toJson(pulldataList);
			
			// Update the pulldata settings

		
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, parsedPd);
			pstmt.setInt(2, sId);
			pstmt.executeUpdate();	

			response = Response.ok().build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception updating pulldata settings", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-Surveys", sd);
			
		}

		return response;
	}
	
	/*
	 * Apply updates to the survey
	 */
	@PUT
	@Path("/save/{sId}")
	@Produces("application/json")
	public Response saveSurveyDetails(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("changes") String changesString
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		log.info("Save survey:" + sId + " : " + changesString);
		
		Type type = new TypeToken<ArrayList<ChangeSet>>(){}.getType();
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		ArrayList<ChangeSet> changes = gson.fromJson(changesString, type);	
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());	
		aUpdate.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Surveys");
		
		// Authorise the changes
		boolean updateExternalChoices = false;
		for(ChangeSet cs : changes) {
			for (ChangeItem ci : cs.items) {
				// Check that property changes are being applied to questions in the specified survey
				if(ci.property != null) {
					if(!ci.property.type.equals("option") 
							&& !ci.property.type.equals("optionlist")) {
						log.info("Validating question for type: " + ci.property.type);
						aUpdate.isValidQuestion(connectionSD, request.getRemoteUser(), sId, ci.property.qId);
						
						if(ci.property.type.equals("appearance")) {
							updateExternalChoices = true;
						}
					}
				} else if(ci.question != null && ci.question.appearance != null) {
					updateExternalChoices = true;
				}
			}
		}
		// End Authorisation

		Response response = null;

		try {
	
			SurveyManager sm = new SurveyManager();
			ChangeResponse resp = sm.applyChangeSetArray(connectionSD, cResults, sId, request.getRemoteUser(), changes);
			
			// Add any options that this survey links to in an an external file
			if(updateExternalChoices) {
				String basePath = GeneralUtilityMethods.getBasePath(request); 	// Get base path to files
				sm.writeExternalChoicesForQuestions(connectionSD, 
						cResults, basePath, request.getRemoteUser(), sId);
			}
			
			// Force regeneration of any dynamic CSV files that this survey links to
			ExternalFileManager efm = new ExternalFileManager();
			efm.linkerChanged(connectionSD, sId);
			
			String respString = gson.toJson(resp);	// Create the response	
			response = Response.ok(respString).build();
			
			
		}  catch (Exception e) {
			try {connectionSD.rollback();} catch (Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-Surveys", connectionSD);		
			ResultsDataSource.closeConnection("surveyKPI-Surveys", cResults);
			
		}

		return response;
	}

	/*
	 * Update the survey settings (does not change question / forms etc)
	 */
	@Path("/save_settings/{sId}")
	@POST
	public Response saveSettings(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();	
		fileItemFactory.setSizeThreshold(5*1024*1024); // 5 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		aUpdate.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
		
		FileItem pdfItem = null;
		String fileName = null;
		String newSurveyName = null;
		String settings = null;
		int version = 0;
				
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGet = null;
		PreparedStatement pstmtChangeLog = null;
		PreparedStatement pstmtAddHrk = null;
		
		Connection cResults = null;
		try {
				
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();

			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				if(item.isFormField()) {
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
				
					
					if(item.getFieldName().equals("settings")) {
						try {
							settings = item.getString("UTF-8");  // Set encoding type to UTF-8 as per http://stackoverflow.com/questions/22025999/sending-files-and-text-with-ajax-multipart-form-data-utf-8-encoding
						} catch (Exception e) {
							
						}
					}
					
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					if(item.getSize() > 0) {
						pdfItem = item;
						fileName = item.getName();
						fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
					}				
				}

			}
			
			Type type = new TypeToken<org.smap.sdal.model.Survey>(){}.getType();
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			org.smap.sdal.model.Survey survey = gson.fromJson(settings, type);
			
			// Start transaction
			connectionSD.setAutoCommit(false);
			
			// Get the existing survey display name, plain old name and project id
			String sqlGet = "select name, display_name, p_id, version from survey where s_id = ?";
			pstmtGet = connectionSD.prepareStatement(sqlGet);	
			pstmtGet.setInt(1, sId);
			
			String originalDisplayName = null;
			String originalName = null;
			int originalProjectId = 0;
			ResultSet rs = pstmtGet.executeQuery();
			if(rs.next()) {
				originalName = rs.getString(1);
				originalDisplayName = rs.getString(2);
				originalProjectId = rs.getInt(3);
				version = rs.getInt(4) + 1;
			}
			
			if(originalName != null) {
				int idx = originalName.lastIndexOf('/');
				if(idx > 0) {
					newSurveyName = originalName.substring(0, idx + 1) + GeneralUtilityMethods.convertDisplayNameToFileName(survey.displayName) + ".xml";
				}
			}
			
			String sqlChangeLog = "insert into survey_change " +
					"(s_id, version, changes, user_id, apply_results, updated_time) " +
					"values(?, ?, ?, ?, 'true', ?)";
			
			// Update the settings
			String sql = "update survey set display_name = ?, name = ?, def_lang = ?, task_file = ?, "
					+ "timing_data = ?, "
					+ "p_id = ?, "
					+ "instance_name = ?, "
					+ "version = ?, "
					+ "class = ?,"
					+ "hrk = ? "
					+ "where s_id = ?;";		
		
			if(survey.surveyClass != null && survey.surveyClass.equals("none")) {
				survey.surveyClass = null;
			}
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, survey.displayName);
			pstmt.setString(2, newSurveyName);
			pstmt.setString(3, survey.def_lang);
			pstmt.setBoolean(4, survey.task_file);
			pstmt.setBoolean(5, survey.timing_data);
			pstmt.setInt(6, survey.p_id);
			pstmt.setString(7, survey.instanceNameDefn);
			pstmt.setInt(8, version);
			pstmt.setString(9, survey.surveyClass);
			pstmt.setString(10, survey.hrk);
			pstmt.setInt(11, sId);
			
			log.info("Saving survey: " + pstmt.toString());
			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to update survey");
			} else {
				log.info("Info: Survey updated");
				
				int userId = GeneralUtilityMethods.getUserId(connectionSD, request.getRemoteUser());
				
				ChangeElement change = new ChangeElement();
				change.action = "settings_update";
				change.msg = "Name: " + survey.displayName + ", Default Language: " + survey.def_lang + ", Instance Name: "+ survey.instanceNameDefn; 
				
				// Write to the change log
				pstmtChangeLog = connectionSD.prepareStatement(sqlChangeLog);
				// Write the change log
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(change));
				pstmtChangeLog.setInt(4, userId);
				pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();
			}
			
			// If the human readable key (HRK) is not null then make sure the HRK column exists in the results file
			if(survey.hrk != null) {
				cResults = ResultsDataSource.getConnection("surveyKPI-Surveys-saveSettings");
				String tableName = GeneralUtilityMethods.getMainResultsTable(connectionSD, cResults, sId);
				if(tableName != null){
					boolean hasHrk = GeneralUtilityMethods.hasColumn(cResults, tableName, "_hrk");
					if(!hasHrk) {
						String sqlAddHrk = "alter table " + tableName + " add column _hrk text;";
						pstmtAddHrk = cResults.prepareStatement(sqlAddHrk);
						pstmtAddHrk.executeUpdate();
					}
				}
			}
			
			connectionSD.commit();
			connectionSD.setAutoCommit(true);
			
			if(fileName != null) {  // Save the file				
	            writePdf(request, survey.displayName, pdfItem, survey.p_id);				
			} else {
				// Try to delete the template file if it exists
				delPdf(request, survey.displayName, survey.p_id);
			}
			
			// If the project id has changed update the project in the upload events so that the monitor will still show all events
			if(originalProjectId != survey.p_id) {
				GeneralUtilityMethods.updateUploadEvent(connectionSD, survey.p_id, sId);
			}
			
			// If the display name or project id has changed rename template files
			if((originalDisplayName != null && survey.displayName != null && !originalDisplayName.equals(survey.displayName)) 
					|| originalProjectId != survey.p_id) {
		
				// Rename files
				String basePath = GeneralUtilityMethods.getBasePath(request); 	// Get base path to files
				GeneralUtilityMethods.renameTemplateFiles(originalDisplayName, survey.displayName, basePath, originalProjectId, survey.p_id);
			}
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"sql error", e);
			try{connectionSD.rollback();} catch(Exception ex) {};
		    response = Response.serverError().entity(e.getMessage()).build();
		    try {connectionSD.setAutoCommit(true);} catch(Exception ex) {}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception loading settings", e);
			try{connectionSD.rollback();} catch(Exception ex) {};
		    response = Response.serverError().entity(e.getMessage()).build();
		    try {connectionSD.setAutoCommit(true);} catch(Exception ex) {}
		} finally {
			
			if (pstmtGet != null) try {pstmtGet.close();} catch (SQLException e) {}
			if (pstmt != null) try {pstmt.close();} catch (SQLException e) {}
			if (pstmtChangeLog != null) try {pstmtChangeLog.close();} catch (SQLException e) {}
			if (pstmtAddHrk != null) try {pstmtAddHrk.close();} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-Survey", cResults);
			
		}

		return response;
	}
	
	/*
	 * Set questions to required
	 */
	@Path("/set_required/{sId}/{required}")
	@POST
	public Response setRequired(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("required") boolean required) { 
		
		Response response = null;
		int version;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		aUpdate.isAuthorised(connectionSD, request.getRemoteUser());
		aUpdate.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
		
				
		PreparedStatement pstmtNotRequired = null;
		PreparedStatement pstmtRequired = null;
		PreparedStatement pstmtChangeLog = null;
		PreparedStatement pstmt = null;
		
		try {
			
			/*
			 * Lock the survey
			 * update version number of survey and get the new version
			 */
			connectionSD.setAutoCommit(false);
			
			String sqlUpdateVersion = "update survey set version = version + 1 where s_id = ?";
			String sqlGetVersion = "select version from survey where s_id = ?";
			pstmt = connectionSD.prepareStatement(sqlUpdateVersion);
			pstmt.setInt(1, sId);
			pstmt.execute();
			pstmt.close();
			
			pstmt = connectionSD.prepareStatement(sqlGetVersion);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			rs.next();
			version = rs.getInt(1);
			pstmt.close();
			
			if(!required) {
				// Set all questions to not required
				String sqlNotRequired = "update question set mandatory = 'false' "
						+ "where f_id in (select f_id from form where s_id = ?);";
				pstmtNotRequired = connectionSD.prepareStatement(sqlNotRequired);	
				pstmtNotRequired.setInt(1, sId);
				
				log.info("SQL: Setting questions not required: " + pstmtNotRequired.toString());
				pstmtNotRequired.executeUpdate();
			
			} else {
				// Set all questions to required
				String sqlRequired = "update question set mandatory = 'true' "
						+ "where readonly = 'false' "
						+ "and visible = 'true' "
						+ "and qtype != 'begin repeat' "
						+ "and qtype != 'begin group' "
						+ "and qtype != 'geopolygon' "
						+ "and qtype != 'geolinestring' "
						+ "and f_id in (select f_id from form where s_id = ?);"; 
			
				pstmtRequired = connectionSD.prepareStatement(sqlRequired);	
				pstmtRequired.setInt(1, sId);
				
				log.info("SQL: Setting questions required: " + pstmtRequired.toString());
				pstmtRequired.executeUpdate();
			}
				
			// Write the change log
			int userId = GeneralUtilityMethods.getUserId(connectionSD, request.getRemoteUser());
				
			ChangeElement change = new ChangeElement();
			change.action = "set_required";
			change.msg = required ? "Questions set required" : "Questions set not required"; 
				
			// Write to the change log
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String sqlChangeLog = "insert into survey_change " +
					"(s_id, version, changes, user_id, apply_results, updated_time) " +
					"values(?, ?, ?, ?, 'true', ?)";
			pstmtChangeLog = connectionSD.prepareStatement(sqlChangeLog);
				
			// Write the change log
			pstmtChangeLog.setInt(1, sId);
			pstmtChangeLog.setInt(2, version);
			pstmtChangeLog.setString(3, gson.toJson(change));
			pstmtChangeLog.setInt(4, userId);
			pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
			pstmtChangeLog.execute();

			connectionSD.commit();
			connectionSD.setAutoCommit(true);
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"sql error", e);
		    try {connectionSD.setAutoCommit(true);} catch(Exception ex) {}
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception loading settings", e);
		    try {connectionSD.setAutoCommit(true);} catch(Exception ex) {}
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			if (pstmtNotRequired != null) try {pstmtNotRequired.close();} catch (SQLException e) {}
			if (pstmtRequired != null) try {pstmtRequired.close();} catch (SQLException e) {}
			if (pstmtChangeLog != null) try {pstmtChangeLog.close();} catch (SQLException e) {}
			if (pstmt != null) try {pstmt.close();} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
			
		}

		return response;
	}
	
	/*
	 * Write the PDF to disk
	 */
	private void writePdf(HttpServletRequest request, 
			String fileName, 
			FileItem pdfItem,
			int pId) {
	
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		fileName = GeneralUtilityMethods.getSafeTemplateName(fileName);
		fileName = fileName + "_template.pdf";
		
		String folderPath = basePath + "/templates/" + pId ;						
		String filePath = folderPath + "/" + fileName;
	    File savedFile = new File(filePath);
	    
	    log.info("userevent: " + request.getRemoteUser() + " : saving pdf template : " + filePath);
	    
	    try {
			pdfItem.write(savedFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	 
	}
	
	/*
	 * Delete the pdf template
	 */
	private void delPdf(HttpServletRequest request, 
			String fileName, 
			int pId) {
	
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		fileName = GeneralUtilityMethods.getSafeTemplateName(fileName);
		fileName = fileName + "_template.pdf";
		
		String folderPath = basePath + "/templates/" + pId ;						
		String filePath = folderPath + "/" + fileName;
	    File delFile = new File(filePath);
	    
	    log.info("userevent: " + request.getRemoteUser() + " : delete pdf template : " + filePath);

	    try {
			delFile.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	 
	}
}

