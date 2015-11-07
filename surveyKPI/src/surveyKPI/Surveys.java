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
import javax.ws.rs.Consumes;
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

import model.Settings;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import utilities.MediaInfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/surveys")
public class Surveys extends Application {

	Authorise a = null;
	Authorise aDel = null;
	
	private static Logger log =
			 Logger.getLogger(Surveys.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Surveys.class);
		return s;
	}
	
	public Surveys() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		a = new Authorise(authorisations, null);
		aDel = new Authorise(authorisations, null);
		
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
			aDel.isAuthorised(connectionSD, request.getRemoteUser());
		} else {
			a.isAuthorised(connectionSD, request.getRemoteUser());
		}
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		ArrayList<org.smap.sdal.model.Survey> surveys = null;
		
		Response response = null;
		PreparedStatement pstmt = null;
		SurveyManager sm = new SurveyManager();
		try {
			surveys = sm.getSurveys(connectionSD, pstmt,
					request.getRemoteUser(), getDeleted, getBlocked, projectId);
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
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
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
		a.isAuthorised(connectionSD, request.getRemoteUser());
		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		org.smap.sdal.model.Survey survey = null;
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		Response response = null;
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Surveys");
		SurveyManager sm = new SurveyManager();
		try {
			survey = sm.getById(connectionSD, cResults,  request.getRemoteUser(), sId, true, basePath, null, false, false);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
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
		a.isAuthorised(connectionSD, request.getRemoteUser());	
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		
		for(ChangeSet cs : changes) {
			for (ChangeItem ci : cs.items) {
				// Option changes are not checked as they are explicitly applied to all option lists in the provided survey
				// Delete questions are auto validated in the delete sql statement
				if(ci.property != null) {
					if(!ci.property.type.equals("option")) {
						log.info("Validating question for type: " + ci.property.type);
						a.isValidQuestion(connectionSD, request.getRemoteUser(), sId, ci.property.qId);
					}
				}
			}
		}
		// End Authorisation

		Response response = null;

		try {
	
			SurveyManager sm = new SurveyManager();
			ChangeResponse resp = sm.applyChangeSetArray(connectionSD, sId, request.getRemoteUser(), changes);
					
			String respString = gson.toJson(resp);	// Create the response	
			response = Response.ok(respString).build();
			
			
		}  catch (Exception e) {
			try {connectionSD.rollback();} catch (Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
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
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		FileItem pdfItem = null;
		String fileName = null;
		String newSurveyName = null;
		String settings = null;
				
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGet = null;
		
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
							settings = item.getString();
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
			
			// Get the existing survey display name, plain old name and project id
			String sqlGet = "select name, display_name, p_id from survey where s_id = ?";
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
			}
			
			if(originalName != null) {
				int idx = originalName.lastIndexOf('/');
				if(idx > 0) {
					newSurveyName = originalName.substring(0, idx + 1) + GeneralUtilityMethods.convertDisplayNameToFileName(survey.displayName) + ".xml";
				}
			}
			
			// Update the settings
			String sql = "update survey set display_name = ?, name = ?, def_lang = ?, task_file = ?, p_id = ?, instance_name = ? where s_id = ?;";		
		
			System.out.println("Survey: " + survey.instanceNameDefn);
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, survey.displayName);
			pstmt.setString(2, newSurveyName);
			pstmt.setString(3, survey.def_lang);
			pstmt.setBoolean(4, survey.task_file);
			pstmt.setInt(5, survey.p_id);
			pstmt.setString(6, GeneralUtilityMethods.convertAllxlsNames(survey.instanceNameDefn, sId, connectionSD));
			pstmt.setInt(7, sId);
			
			log.info("Saving survey: " + pstmt.toString());
			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to update survey");
			} else {
				log.info("Info: Survey updated");
			}
			
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
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception loading settings", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			if (pstmtGet != null) try {pstmtGet.close();} catch (SQLException e) {}
			if (pstmt != null) try {pstmt.close();} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
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

