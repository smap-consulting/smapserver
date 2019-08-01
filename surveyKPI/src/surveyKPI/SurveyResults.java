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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ExternalFileManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.QuestionManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.model.GroupDetails;
import org.smap.sdal.model.Question;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;


@Path("/surveyResults/{sId}")
public class SurveyResults extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Results.class.getName());

	LogManager lm = new LogManager();		// Application log

	/*
	 * Delete results for a survey
	 */
	@DELETE
	public Response deleteSurveyResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-SurveyResults");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "delete", "Delete results");
		
		// Escape any quotes
		if(sId > 0) {

			String sql = null;				
			Connection connectionRel = null; 
			PreparedStatement pstmt = null;
			PreparedStatement pstmtUnPublish = null;
			PreparedStatement pstmtUnPublishOption = null;
			PreparedStatement pstmtRemoveChangeset = null;
			PreparedStatement pstmtGetSoftDeletedQuestions = null;
			Statement stmtRel = null;
			try {
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
				connectionRel = ResultsDataSource.getConnection("surveyKPI-SurveyResults");

				// Delete tables associated with this survey
				
				String sqlUnPublish = "update question set published = 'false' where f_id in (select f_id from form where s_id = ?);";
				pstmtUnPublish = sd.prepareStatement(sqlUnPublish);
				
				String sqlUnPublishOption = "update option set published = 'false' where l_id in (select l_id from listname where s_id = ?);";
				pstmtUnPublishOption = sd.prepareStatement(sqlUnPublishOption);
				
				String sqlRemoveChangeset = "delete from changeset where s_id = ?;";
				pstmtRemoveChangeset = connectionRel.prepareStatement(sqlRemoveChangeset);
				
				String sqlGetSoftDeletedQuestions = "select q.f_id, q.qname from question q where soft_deleted = 'true' "
						+ "and q.f_id in (select f_id from form where s_id = ?);";
				pstmtGetSoftDeletedQuestions = sd.prepareStatement(sqlGetSoftDeletedQuestions);
				
				/*
				 * Get the surveys and tables that are part of the group that this survey belongs to
				 */
				SurveyManager sm = new SurveyManager(localisation, "UTC");
				int groupSurveyId = GeneralUtilityMethods.getSurveyGroup(sd, sId);
				ArrayList<GroupDetails> surveys = sm.getGroupDetails(sd, groupSurveyId, request.getRemoteUser(), sId);
				ArrayList<String> tableList = sm.getGroupTables(sd, groupSurveyId, oId, request.getRemoteUser(), sId);
				
				/*
				 * Delete data from each form
				 */
				for(String tableName : tableList) {				

					sql = "drop TABLE " + tableName + ";";
					log.info("*********************************  Delete table contents and drop table: " + sql);
					
					try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
					stmtRel = connectionRel.createStatement();
					try {
						stmtRel.executeUpdate(sql);
					} catch (Exception e) {
						log.info("Error deleting table: " + e.getMessage());
					}
					log.info("userevent: " + request.getRemoteUser() + " : delete results : " + tableName + " in survey : "+ sId); 
				}
				
				/*
				 * Clean up the surveys
				 */
				ExternalFileManager efm = new ExternalFileManager(localisation);
				for(GroupDetails gd : surveys) {
					log.info("Clean survey: " + gd.surveyName);
					
					pstmtUnPublish.setInt(1, gd.sId);			// Mark questions as un-published
					log.info("Marking questions as unpublished: " + pstmtUnPublish.toString());
					pstmtUnPublish.executeUpdate();
					
					pstmtUnPublishOption.setInt(1, gd.sId);			// Mark options as un-published
					log.info("Marking options as unpublished: " + pstmtUnPublishOption.toString());
					pstmtUnPublishOption.executeUpdate();
					
					pstmtRemoveChangeset.setInt(1, gd.sId);
					log.info("Removing changeset: " + pstmtRemoveChangeset.toString());
					pstmtRemoveChangeset.executeUpdate();
					
					// Delete any soft deleted questions from the survey definitions
					QuestionManager qm = new QuestionManager(localisation);
					ArrayList<Question> questions = new ArrayList<Question> ();
					pstmtGetSoftDeletedQuestions.setInt(1, gd.sId);
					log.info("Get soft deleted questions: " + pstmtGetSoftDeletedQuestions.toString());
					ResultSet rs = pstmtGetSoftDeletedQuestions.executeQuery();
					while(rs.next()) {
						Question q = new Question();
						q.fId = rs.getInt(1);
						q.name = rs.getString(2);
						questions.add(q);
					}
					if(questions.size() > 0) {
						qm.delete(sd, connectionRel, gd.sId, questions, false, false);
					}
					
					// Force regeneration of any dynamic CSV files that this survey links to
					efm.linkerChanged(sd, gd.sId);	// deprecated
					try {
						SurveyTableManager stm = new SurveyTableManager(sd, localisation);
						stm.delete(sId);			// Delete references to this survey in the csv table so that they get regenerated
					} catch (Exception e) {
						log.log(Level.SEVERE, e.getMessage(), e);
					}
					
				}
				response = Response.ok("").build();
				
			} catch (Exception e) {
				String msg = e.getMessage();
				if(msg != null && msg.contains("does not exist")) {
					log.log(Level.SEVERE, msg);
					e.printStackTrace();
					response = Response.ok("").build();
				} else {
					log.log(Level.SEVERE, "Survey: Delete REsults");
					e.printStackTrace();
					response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				}
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				try {if (pstmtUnPublish != null) {pstmtUnPublish.close();}} catch (SQLException e) {}
				try {if (pstmtUnPublishOption != null) {pstmtUnPublishOption.close();}} catch (SQLException e) {}
				try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
				try {if (pstmtGetSoftDeletedQuestions != null) {pstmtGetSoftDeletedQuestions.close();}} catch (SQLException e) {}

				SDDataSource.closeConnection("surveyKPI-SurveyResults", sd);
				ResultsDataSource.closeConnection("surveyKPI-SurveyResults", connectionRel);
			}
		}

		return response; 
	}
	
	/*
	 * Restore results for a survey
	 */
	@GET
	@Path("/restore")
	public Response restoreSurveyResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-SurveyResults");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "restore", "Restore results");
		
		// Escape any quotes
		if(sId > 0) {

			String sql = null;				
			Connection connectionRel = null; 
			PreparedStatement pstmt = null;
			PreparedStatement pstmtRestore = null;
			PreparedStatement pstmtReset = null;
			PreparedStatement pstmtUnpublish = null;
			
			Statement stmtRel = null;
			try {
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
				connectionRel = ResultsDataSource.getConnection("surveyKPI-SurveyResults");
				
				// Mark columns as unpublished		
				String sqlUnpublish = "update question set published = 'false' where f_id in (select f_id from form where s_id = ?)";
				pstmtUnpublish = sd.prepareStatement(sqlUnpublish);	
				
				// Delete subscriber entries associated with this survey				
				String sqlRestore = "delete from subscriber_event "
						+ "where subscriber = 'results_db' "
						+ "and se_id in "
						+ "(select se.se_id from upload_event ue, subscriber_event se where ue.ue_id = se.ue_id and ue.ident = ?);";
				pstmtRestore = sd.prepareStatement(sqlRestore);			
				
				String sqlResetLoadFlag = "update upload_event set results_db_applied = 'false' "
						+ "where ident = ?";
				pstmtReset = sd.prepareStatement(sqlResetLoadFlag);
				
				/*
				 * Get the surveys and tables that are part of the group that this survey belongs to
				 */
				SurveyManager sm = new SurveyManager(localisation, "UTC");
				int groupSurveyId = GeneralUtilityMethods.getSurveyGroup(sd, sId);
				ArrayList<GroupDetails> surveys = sm.getGroupDetails(sd, groupSurveyId, request.getRemoteUser(), sId);
				ArrayList<String> tableList = sm.getGroupTables(sd, groupSurveyId, oId, request.getRemoteUser(), sId);
				
				/*
				 * Delete data from each form ready for reload
				 */
				for(String tableName : tableList) {				

					sql = "drop TABLE " + tableName + ";";
					log.info("################################# Delete table contents and drop table prior to restore: " + sql);
					
					try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
					stmtRel = connectionRel.createStatement();
					try {
						stmtRel.executeUpdate(sql);
					} catch (Exception e) {
						log.info("Error deleting table: " + e.getMessage());
					}
					log.info("userevent: " + request.getRemoteUser() + " : delete results : " + tableName + " in survey : "+ sId); 
				}
					
				/*
				 * Mark questions as unpublished
				 */
				connectionRel.setAutoCommit(false);
				for(GroupDetails gd : surveys) {
					pstmtUnpublish.setInt(1, gd.sId);
					log.info("set unpublished " + pstmtUnpublish.toString());
					pstmtUnpublish.executeUpdate();
				}
				
				/*
				 * Reload the surveys
				 */
				ExternalFileManager efm = new ExternalFileManager(localisation);
				connectionRel.setAutoCommit(false);
				for(GroupDetails gd : surveys) {
					// restore backed up files from s3 of raw data
					GeneralUtilityMethods.restoreUploadedFiles(gd.surveyIdent, "uploadedSurveys");
					pstmtRestore.setString(1, gd.surveyIdent);			// Initiate restore
					log.info("Restoring survey " + gd.surveyIdent + ": " + pstmtRestore.toString());
					pstmtRestore.executeUpdate();
							
					pstmtReset.setString(1, gd.surveyIdent);			// Initiate reset of go faster flag
					pstmtReset.executeUpdate();
					
					// Force regeneration of any dynamic CSV files that this survey links to
					efm.linkerChanged(sd, gd.sId);	// deprecated
				}
				connectionRel.commit();
				
				response = Response.ok("").build();
				
			} catch (Exception e) {
				String msg = e.getMessage();
				if(msg != null && msg.contains("does not exist")) {
					response = Response.ok("").build();
				} else {
					log.log(Level.SEVERE, "Survey: Restore Results");
					e.printStackTrace();
					response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				}
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				try {if (pstmtRestore != null) {pstmtRestore.close();}} catch (SQLException e) {}
				try {if (pstmtReset != null) {pstmtReset.close();}} catch (SQLException e) {}
				try {if (pstmtUnpublish != null) {pstmtUnpublish.close();}} catch (SQLException e) {}
				try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
			

				try {connectionRel.setAutoCommit(true);} catch (Exception e) {}
				
				SDDataSource.closeConnection("surveyKPI-SurveyResults", sd);
				ResultsDataSource.closeConnection("surveyKPI-SurveyResults", connectionRel);
			}
		}

		return response; 
	}
	
	/*
	 * Get surveys belonging to a group
	 */
	@GET
	@Path("/groups")
	public Response getGroups(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-SurveyResults - getGroups");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		if(sId > 0) {
		
			PreparedStatement pstmt = null;
			PreparedStatement pstmtRestore = null;
			
			Statement stmtRel = null;
			try {
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				/*
				 * Get the surveys and tables that are part of the group that this survey belongs to
				 */
				SurveyManager sm = new SurveyManager(localisation, "UTC");
				int groupSurveyId = GeneralUtilityMethods.getSurveyGroup(sd, sId);
				ArrayList<GroupDetails> groups = sm.getGroupDetails(sd, groupSurveyId, request.getRemoteUser(), sId);
				
				Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				response = Response.ok(gson.toJson(groups)).build();
				
			} catch (Exception e) {
				String msg = e.getMessage();
				if(msg != null && msg.contains("does not exist")) {
					response = Response.ok("").build();
				} else {
					log.log(Level.SEVERE, "Survey: Restore Results");
					e.printStackTrace();
					response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				}
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				try {if (pstmtRestore != null) {pstmtRestore.close();}} catch (SQLException e) {}
				
				try {if (stmtRel != null) {stmtRel.close();}} catch (SQLException e) {}
		
				
				SDDataSource.closeConnection("surveyKPI-SurveyResults - getGroups", sd);
			}
		}

		return response; 
	}
}



