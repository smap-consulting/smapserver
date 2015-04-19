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

package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SurveyManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyManager.class.getName());

	public ArrayList<Survey> getSurveys(Connection sd, PreparedStatement pstmt,
			String user, 
			boolean getDeleted, 
			boolean getBlocked,
			int projectId			// Set to 0 to get all surveys regardless of project
			) throws SQLException {
		
		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident" +
				" from survey s, users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and p.o_id = u.o_id" +
				" and u.ident = ? ";
		
		// only return surveys in the users organisation unit + assigned project id 
		// If a specific valid project id was passed then restrict surveys to that project as well
		
		if(projectId != 0) {
			sql += "and s.p_id = ? ";
		}
		if(!getDeleted) {
			sql += "and s.deleted = 'false'";
		} 
		if(!getBlocked) {
			sql += "and s.blocked = 'false'";
		}
		sql += "order BY s.display_name;";
	
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setString(1, user);
		if(projectId != 0) {
			pstmt.setInt(2, projectId);
		}
		log.info("Get surveys: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		while (resultSet.next()) {								

			Survey s = new Survey();
			s.setId(resultSet.getInt(1));
			s.setName(resultSet.getString(2));
			s.setDisplayName(resultSet.getString(3));
			s.setDeleted(resultSet.getBoolean(4));
			s.setBlocked(resultSet.getBoolean(5));
			s.setIdent(resultSet.getString(6));
			
			surveys.add(s);
		} 
		return surveys;
		
	}
	
	/*
	 * Return true if there is already a survey with the supplied display name and project id
	 */
	public boolean surveyExists(Connection sd, String displayName, int projectId) {
		boolean exists = false;
		
		ResultSet resultSet = null;
		String sql = "select count(*) from survey s "
				+ " where s.display_name = ? "
				+ " and s.p_id = ?;";
	
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, displayName);
			pstmt.setInt(2, projectId);

			log.info("Check for existence of survey: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {		
				int count = resultSet.getInt(1);
				if(count > 0) {
					exists = true;
				}
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage());
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){};
		}
		return exists;
	}
	
	public Survey getById(
			Connection sd, 
			Connection cResults,
			String user,
			int sId,
			boolean full,		// Get the full details of the survey
			String basePath,
			String instanceId,	// If set get the results for this instance
			boolean getResults	// Set to true to get results, if set and instanceId is null then blank data will be added
			) throws SQLException, Exception {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.s_id, s.name, s.ident, s.display_name, s.deleted, p.name, p.id, s.def_lang, u.o_id, s.class" +
				" from survey s, users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and u.ident = ? " +
				" and s.s_id = ?; ";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, user);
			pstmt.setInt(2, sId);
	
			log.info("Get Survey info: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();	
			if (resultSet.next()) {								
	
				s = new Survey();
				s.setId(resultSet.getInt(1));
				s.setName(resultSet.getString(2));
				s.setIdent(resultSet.getString(3));
				s.setDisplayName(resultSet.getString(4));
				s.setDeleted(resultSet.getBoolean(5));
				s.setPName(resultSet.getString(6));
				s.setPId(resultSet.getInt(7));
				s.def_lang = resultSet.getString(8);
				s.o_id = resultSet.getInt(9);
				s.surveyClass = resultSet.getString(10);
				
			} 
			
			if(full && s != null) {
				populateSurvey(sd, s, basePath, user);
				if(getResults) {
					Form ff = s.getFirstForm();
					s.instance.results = getResults(ff, s.getFormIdx(ff.id), -1, 0,	cResults, instanceId, 0, s);
					ArrayList<Result> topForm = s.instance.results.get(0);
					// Get the user ident that submitted the survey
					for(Result r : topForm) {
						if(r.type.equals("user")) {
							s.instance.user = r.value;
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			throw e;
		} catch (Exception e) {
			throw e;
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e){};
		}
		return s;
		
	}
	
	/*
	 * Get all the surveys in the user's organisation that reference the passed in CSV file
	 *  Note even surveys that are in projects not enabled for the user should be returned
	 */
	public ArrayList<Survey> getByOrganisationAndExternalCSV(Connection sd, 
			String user, 
			String csvFileName			
			)  {
		
		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request
		
		int idx = csvFileName.lastIndexOf('.');
		String csvRoot = csvFileName;
		if(idx > 0) {
			csvRoot = csvFileName.substring(0, idx);
		}
		
		// Escape csvRoot
		csvRoot = csvRoot.replace("\'", "\'\'");
		
		ResultSet resultSet = null;
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident" +
				" from survey s, users u, user_project up, project p, question q, form f " +
				" where s.s_id = f.s_id " +
				" and f.f_id = q.f_id " +
				" and q.appearance like 'search(''" + csvRoot + "''%' " +
				" and s.p_id = p.id" +
				" and p.o_id = u.o_id" +
				" and u.ident = ? " +
				"order BY s.display_name;";
		
	
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			
			log.info("Get a survey by organisation id: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
	
			while (resultSet.next()) {								
	
				Survey s = new Survey();
				s.setId(resultSet.getInt(1));
				s.setName(resultSet.getString(2));
				s.setDisplayName(resultSet.getString(3));
				s.setDeleted(resultSet.getBoolean(4));
				s.setBlocked(resultSet.getBoolean(5));
				s.setIdent(resultSet.getString(6));
				
				surveys.add(s);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
		}
		
		return surveys;
		
	}

	/*
	 * Get a survey's details
	 */
	private void populateSurvey(Connection sd, Survey s, String basePath, String user) throws Exception {
		
		/*
		 * Prepared Statements
		 */
		
		// Get the forms belonging to this survey
		ResultSet rsGetForms = null;
		String sqlGetForms = "select f.f_id, f.name, f.parentform, f.parentquestion, f.table_name from form f where f.s_id = ?;";
		PreparedStatement pstmtGetForms = sd.prepareStatement(sqlGetForms);	
		
		// Get the questions belonging to a form
		ResultSet rsGetQuestions = null;
		String sqlGetQuestions = "select q.q_id, q.qname, q.qtype, q.qtext_id, q.list_name, q.infotext_id, "
				+ "q.source, q.calculate, "
				+ "q.seq, " 
				+ "q.defaultanswer, "
				+ "q.appearance " 
				+ "from question q "
				+ "where q.f_id = ? "
				//+ "and q.qname != '_instanceid' "
				+ "order by q.seq asc;";
		PreparedStatement pstmtGetQuestions = sd.prepareStatement(sqlGetQuestions);

		// Get the options belonging to a question		
		ResultSet rsGetOptions = null;
		String sqlGetOptions = "select o.o_id, o.ovalue as value, o.label_id, o.externalfile  from option o where q_id = ? order by seq";
		PreparedStatement pstmtGetOptions = sd.prepareStatement(sqlGetOptions);
		
		// Get the server side calculations
		ResultSet rsGetSSC = null;
		String sqlGetSSC = "SELECT ssc.id, ssc.name, ssc.function, ssc.parameters, ssc.units, f.name, f.f_id " +
				"FROM ssc ssc, form f WHERE ssc.s_id = ? AND ssc.f_id = f.f_id ORDER BY id";
		PreparedStatement pstmtGetSSC = sd.prepareStatement(sqlGetSSC);
		
		// Get the changes that have been made to this survey
		ResultSet rsGetChanges = null;
		String sqlGetChanges = "SELECT c.changes, c.c_id, c.version, u.name, c.updated_time " +
				"from survey_change c, users u " +
				"where c.s_id = ? " +
				"and c.user_id = u.id " +
				"order by c_id desc; ";
		PreparedStatement pstmtGetChanges = sd.prepareStatement(sqlGetChanges);
		
		// Get the available languages
		s.languages = GeneralUtilityMethods.getLanguagesForSurvey(sd, s.id);
		
		// Get the organisation id
		int oId = GeneralUtilityMethods.getOrganisationId(sd, user);
		
		// Set the default language if it has not previously been set
		if(s.def_lang == null) {
			s.def_lang = s.languages.get(0);
		}
		
		// Get the Forms
		pstmtGetForms.setInt(1, s.id);
		rsGetForms = pstmtGetForms.executeQuery();
		
		while (rsGetForms.next()) {								
			Form f = new Form();
			f.id = rsGetForms.getInt(1);
			f.name = rsGetForms.getString(2);
			f.parentform =rsGetForms.getInt(3); 
			f.parentQuestion = rsGetForms.getInt(4);
			f.tableName = rsGetForms.getString(5);
				
			/*
			 * Get the questions for this form
			 */
			pstmtGetQuestions.setInt(1, f.id);
			log.info("Get questions for form: " + pstmtGetQuestions.toString());
			rsGetQuestions = pstmtGetQuestions.executeQuery();
			
			boolean inMeta = false;				// Set true if the question is in the meta group
			while (rsGetQuestions.next()) {
				Question q = new Question();
				q.id = rsGetQuestions.getInt(1);
				q.name = rsGetQuestions.getString(2);
				q.type = rsGetQuestions.getString(3);
				q.text_id = rsGetQuestions.getString(4);
				q.list_name = rsGetQuestions.getString(5);
				q.hint_id = rsGetQuestions.getString(6);
				q.source = rsGetQuestions.getString(7);
				q.calculation = rsGetQuestions.getString(8);
				q.seq = rsGetQuestions.getInt(9);
				q.defaultanswer = rsGetQuestions.getString(10);
				q.appearance = rsGetQuestions.getString(11);
				
				// add column name (not currently in the database but it should be)
				q.colName = UtilityMethodsEmail.cleanName(q.name);
				
				// Track if this question is in the meta group
				if(q.name.equals("meta")) {
					inMeta = true;
				} else if(q.name.equals("meta_groupEnd")) {
					inMeta = false;
				}
				q.inMeta = inMeta;
				
				// If the survey was loaded from xls it will not have a list name
				if(q.list_name == null || q.list_name.trim().length() == 0) {
					q.list_name = String.valueOf(q.id);
				}
				
				// Get the language labels
				UtilityMethodsEmail.getLabels(sd, s, q.text_id, q.hint_id, q.labels, basePath, oId);
				//q.labels_orig = q.labels;		// Set the original label values
				
				/*
				 * If this is a select question get the options
				 */
				if(q.type.startsWith("select")) {
					// Only add the options if this option list has not already been added by another question
					ArrayList<Option> options = s.optionLists.get(q.list_name);
					if(options == null) {				
						options = new ArrayList<Option> ();
						
						pstmtGetOptions.setInt(1, q.id);
						rsGetOptions = pstmtGetOptions.executeQuery();
						while(rsGetOptions.next()) {
							Option o = new Option();
							o.id = rsGetOptions.getInt(1);
							o.value = rsGetOptions.getString(2);
							o.text_id = rsGetOptions.getString(3);
							o.externalFile = rsGetOptions.getBoolean(4);
							
							// Get the labels for the option
							UtilityMethodsEmail.getLabels(sd, s, o.text_id, null, o.labels, basePath, oId);
							//o.labels_orig = o.labels;
							options.add(o);
						}
						
						s.optionLists.put(q.list_name, options);
						s.optionLists_orig.put(q.list_name, options);
					}
				}
							
				f.questions.add(q);
			}
			
			s.forms.add(f);
			s.forms_orig.add(f);
			
		} 
		
		// Add the server side calculations
		pstmtGetSSC.setInt(1, s.getId());
		rsGetSSC= pstmtGetSSC.executeQuery();
		
		while (rsGetSSC.next()) {
			ServerSideCalculate ssc = new ServerSideCalculate();
			ssc.setId(rsGetSSC.getInt(1));
			ssc.setName(rsGetSSC.getString(2));
			ssc.setFunction(rsGetSSC.getString(3));
			ssc.setUnits(rsGetSSC.getString(5));
			ssc.setForm(rsGetSSC.getString(6));
			ssc.setFormId(rsGetSSC.getInt(7));
			s.sscList.add(ssc);
		}
		
		// Add the change log
		pstmtGetChanges.setInt(1, s.getId());
		rsGetChanges = pstmtGetChanges.executeQuery();
		
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		while (rsGetChanges.next()) {
			
			ChangeItem ci = gson.fromJson(rsGetChanges.getString(1), ChangeItem.class);
			
			ci.cId = rsGetChanges.getInt(2);
			ci.version = rsGetChanges.getInt(3);
			ci.userName = rsGetChanges.getString(4);
			ci.updatedTime = rsGetChanges.getTimestamp(5);
			s.changes.add(ci);
		}
		
		// Close statements
		try { if (pstmtGetForms != null) {pstmtGetForms.close();}} catch (SQLException e) {}
		try { if (pstmtGetQuestions != null) {pstmtGetQuestions.close();}} catch (SQLException e) {}
		try { if (pstmtGetOptions != null) {pstmtGetOptions.close();}} catch (SQLException e) {}
		try { if (pstmtGetSSC != null) {pstmtGetSSC.close();}} catch (SQLException e) {}
		try { if (pstmtGetChanges != null) {pstmtGetChanges.close();}} catch (SQLException e) {}
	}
	

	

	
	
	/*
	 * Get the project id and the block status of a survey given its ident
	 */
	public Survey getSurveyId(Connection sd, String key) {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.p_id, s.s_id, s.blocked, s.class " +
				" from survey s" +
				" where s.ident = ?; ";
		
		String sql2 = "select s.p_id, s.s_id, s.blocked, s.class " +		// Hack due to issue with upgrade of a server where ident not set to survey id by default
				" from survey s" +
				" where s.s_id = ?; ";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, key);			
			log.info("Get survey id: " + pstmt.toString());
	
			resultSet = pstmt.executeQuery();
	
			if (resultSet.next()) {								
				s = new Survey();
				s.setPId(resultSet.getInt(1));
				s.setId(resultSet.getInt(2));
				s.setBlocked(resultSet.getBoolean(3));
				s.surveyClass = resultSet.getString(4);
				
				
			} else {	// Attempt to find the survey assuming the ident is the survey id
				pstmt2 = sd.prepareStatement(sql2);	
				int sId = 0;
				try {
					sId = Integer.parseInt(key);
				} catch (Exception e) {
					
				}
				pstmt2.setInt(1, sId);
				
				log.info("Find survey: " + pstmt2.toString());
				
				resultSet = pstmt2.executeQuery();
				
				if (resultSet.next()) {								
					s = new Survey();
					s.setPId(resultSet.getInt(1));
					s.setId(resultSet.getInt(2));
					s.setBlocked(resultSet.getBoolean(3));
					s.surveyClass = resultSet.getString(4);
				} else {			
					log.info("Error: survey not found");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
				
			}
		}
		
		return s;
		
	}
	
	/*
	 * Apply an array of change sets to a survey
	 */
	public ChangeResponse applyChangeSetArray(Connection connectionSD, int sId, String ident, ArrayList<ChangeSet> changes) throws Exception {
		
		ChangeResponse resp = new ChangeResponse();	// Response object
		resp.changeSet = changes;
		
		int userId = -1;
		ResultSet rs = null;
		

		PreparedStatement pstmtChangeLog = null;
		PreparedStatement pstmt = null;
		
		try {
			
			String sqlChangeLog = "insert into survey_change " +
					"(s_id, version, changes, user_id, apply_results, updated_time) " +
					"values(?, ?, ?, ?, ?, ?)";
			pstmtChangeLog = connectionSD.prepareStatement(sqlChangeLog);
			
			/*
			 * Get the user id
			 * This should be saved rather than the ident as a user could be deleted
			 *  then a new user created with the same ident but its a different user
			 */
			String sqlGetUser = "select id from users where ident = ?";
			pstmt = connectionSD.prepareStatement(sqlGetUser);
			pstmt.setString(1, ident);
			rs = pstmt.executeQuery();
			if(rs.next()) {
				userId = rs.getInt(1);
			}
			pstmt.close();
			
			connectionSD.setAutoCommit(false);
			
			/*
			 * Lock the survey
			 * update version number of survey and get the new version
			 */
			String sqlUpdateVersion = "update survey set version = version + 1 where s_id = ?";
			String sqlGetVersion = "select version from survey where s_id = ?";
			pstmt = connectionSD.prepareStatement(sqlUpdateVersion);
			pstmt.setInt(1, sId);
			pstmt.execute();
			pstmt.close();
			
			pstmt = connectionSD.prepareStatement(sqlGetVersion);
			pstmt.setInt(1, sId);
			rs = pstmt.executeQuery();
			rs.next();
			resp.version = rs.getInt(1);
			pstmt.close();
			
			for(ChangeSet cs : changes) {			
				
				// Process each change set separately and roll back to a save point if it fails
				Savepoint sp = connectionSD.setSavepoint();
				try {
					
					log.info("SurveyManager, applyChanges. Change set type: " + cs.type);
					if(cs.type.equals("label") || cs.type.equals("media")) {
						
						applyLabel(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version);

					} else if(cs.type.equals("option_update")) {
						
						applyOptionUpdates(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version);
						
					} else {
						applyProperty(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.type);
					}
					
					
					// Success
					cs.updateFailed = false;
					resp.success++;
				} catch (Exception e) {
					
					// Failure
					connectionSD.rollback(sp);
					String msg = e.getMessage();
					log.info("Error: " + e.getMessage());
					cs.updateFailed = true;
					cs.errorMsg = e.getMessage();
					resp.failed++;
				}
				
			}
			
			if(resp.success > 0) {
				connectionSD.commit();
				log.info("Survey update to version: " + resp.version + ". " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			} else {
				connectionSD.rollback();
				log.info("Survey version not updated: " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtChangeLog != null) {pstmtChangeLog.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return resp;
	}
	

	
	// Get the timestamp
	public static Timestamp getTimeStamp() {
		 
		Date today = new Date();
		return new Timestamp(today.getTime());
	 
	}
	
	/*
	 * ========================= Type specific update functions
	 */
	
	/*
	 * Apply label changes
	 * These are just changes to question labels, commonly created when editing translations
	 */
	public void applyLabel(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version) throws Exception {
		
		String transType = null;
		PreparedStatement pstmtLangOldVal = null;
		PreparedStatement pstmtLangNew = null;
		PreparedStatement pstmtNewQuestionLabel = null;
		PreparedStatement pstmtDeleteLabel = null;
		PreparedStatement pstmtLanguages = null;
		
		try {
			
			// Create prepared statements, one for the case where an existing value is being updated
			String sqlLangOldVal = "update translation set value = ? " +
					"where s_id = ? and language = ? and text_id = ? and type = ? and value = ?;";
			pstmtLangOldVal = connectionSD.prepareStatement(sqlLangOldVal);
		
			String sqlLangNew = "insert into translation (value, s_id, language, text_id, type) values(?,?,?,?,?);";
			pstmtLangNew = connectionSD.prepareStatement(sqlLangNew);
			
			String sqlNewQLabel = "update question set qtext_id = ? where q_id = ?; ";
			pstmtNewQuestionLabel = connectionSD.prepareStatement(sqlNewQLabel);
			
			String sqlDeleteLabel = "delete from translation where s_id = ? and text_id = ? and type = ?;";
			pstmtDeleteLabel = connectionSD.prepareStatement(sqlDeleteLabel);
			
			 // Get the languages
		    String sqlLanguage = "select distinct language from translation where s_id = ?;";
			List<String> lang = new ArrayList<String>();
			pstmtLanguages = connectionSD.prepareStatement(sqlLanguage);
			pstmtLanguages.setInt(1, sId);
			ResultSet rs = pstmtLanguages.executeQuery();
			while(rs.next()) {
				lang.add(rs.getString(1));
			}
			if(lang.size() == 0) {
				lang.add("language");	// Default 
			}
			
			for(ChangeItem ci : changeItemList) {
			
				if(ci.oldVal != null && ci.newVal != null) {
					if(ci.element.equals("text")) {
						updateLabel(ci, ci.languageName, pstmtLangOldVal, sId);
					} else {
						// For media update all the languages
						for(int i = 0; i < lang.size(); i++) {
							updateLabel(ci, lang.get(i), pstmtLangOldVal, sId);
						}
					}
					
				} else {
					if(ci.element.equals("text")) {
						addLabel(ci, ci.languageName, pstmtLangNew, sId, pstmtDeleteLabel);

						// Add the new text id to the question
						pstmtNewQuestionLabel.setString(1, ci.key);
						pstmtNewQuestionLabel.setInt(2, ci.qId);
						log.info("Update question table with text_id: " + pstmtNewQuestionLabel.toString());
						pstmtNewQuestionLabel.executeUpdate();
						
					} else {
						// For media update all the languages
						for(int i = 0; i < lang.size(); i++) {
							addLabel(ci, lang.get(i), pstmtLangNew, sId, pstmtDeleteLabel);
						}
					}
					 

				}
				
				log.info("userevent: " + userId + " : modify survey label : " + ci.key + " to: " + ci.newVal + " survey: " + sId + " language: " + ci.languageName + " labelId: "  + transType);
				
				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(ci));
				pstmtChangeLog.setInt(4,userId);
				pstmtChangeLog.setBoolean(5,false);
				pstmtChangeLog.setTimestamp(6, getTimeStamp());
				pstmtChangeLog.execute();
			}
		} catch (Exception e) {
			
			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {
			try {if (pstmtLangOldVal != null) {pstmtLangOldVal.close();}} catch (SQLException e) {}
			try {if (pstmtLangNew != null) {pstmtLangNew.close();}} catch (SQLException e) {}
			try {if (pstmtNewQuestionLabel != null) {pstmtNewQuestionLabel.close();}} catch (SQLException e) {}
			try {if (pstmtDeleteLabel != null) {pstmtDeleteLabel.close();}} catch (SQLException e) {}
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}
	}
	
	/*
	 * Update a label
	 */
	public void updateLabel(ChangeItem ci, String language, PreparedStatement pstmtLangOldVal, int sId) throws SQLException, Exception {
		
		String transType = null;
		
		pstmtLangOldVal.setString(1, ci.newVal);
		pstmtLangOldVal.setInt(2, sId);
		pstmtLangOldVal.setString(3, language);
		pstmtLangOldVal.setString(4, ci.key);
		if(ci.element.equals("text")) {
			transType = "none";
		} else {
			transType = ci.element;
		}
		pstmtLangOldVal.setString(5,  transType);
		pstmtLangOldVal.setString(6, ci.oldVal);

		log.info("Update question translation: " + pstmtLangOldVal.toString());
		
		int count = pstmtLangOldVal.executeUpdate();
		if(count == 0) {
			log.info("Error: Element already modified");
			throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
		}
	}
	
	public void addLabel(ChangeItem ci, String language, PreparedStatement pstmtLangNew, int sId, PreparedStatement pstmtDeleteLabel) throws SQLException, Exception {
		
		String transType = null;
		
		if(ci.newVal != null) {
			
			pstmtLangNew.setString(1, ci.newVal);
			pstmtLangNew.setInt(2, sId);
			pstmtLangNew.setString(3, language);
			pstmtLangNew.setString(4, ci.key);
			if(ci.element.equals("text")) {
				transType = "none";
			} else {
				transType = ci.element;
			}
			pstmtLangNew.setString(5,  transType);
			
			log.info("Insert new question label: " + pstmtLangNew.toString());
			
			pstmtLangNew.executeUpdate();
		} else {
			// Only media labels can have new val set to null and hence be deleted hence delete for all languages
			pstmtDeleteLabel.setInt(1, sId);
			pstmtDeleteLabel.setString(2, ci.key);
			pstmtDeleteLabel.setString(3, ci.element);
			log.info("Delete media label: " + pstmtDeleteLabel.toString());
			pstmtDeleteLabel.executeUpdate();
			ci.key = null;		// Clear the key in the question table
		}
		
	}
	
	/*
	 * Apply changes to an option
	 *  1) Get the maximum sequence number for each question
	 *  2) Attempt to get the text_id for the passed in option
	 *     3a) If the text_id can't be found create a new option
	 *     3b) Else update the label value for the option 
	 */
	public void applyOptionUpdates(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version) throws Exception {
		
		PreparedStatement pstmtLangInsert = null;
		PreparedStatement pstmtLangUpdate = null;
		PreparedStatement pstmtOptionGet = null;
		PreparedStatement pstmtOptionInsert = null;
		PreparedStatement pstmtMaxSeq = null;
		
		try {
			// Create prepared statements
			String sqlOptionGet = "select label_id from option where q_id = ? and ovalue = ?;";
			pstmtOptionGet = connectionSD.prepareStatement(sqlOptionGet);
			
			String sqlOptionInsert = "insert into option  (o_id, q_id, seq, label_id, ovalue, externalfile) values(nextval('o_seq'), ?, ?, ?, ?, 'true');"; 			
			pstmtOptionInsert = connectionSD.prepareStatement(sqlOptionInsert);
			
			String sqlLangInsert = "insert into translation  (t_id, s_id, language, text_id, type, value) values(nextval('t_seq'), ?, ?, ?, ?, ?);"; 			
			pstmtLangInsert = connectionSD.prepareStatement(sqlLangInsert);
			
			String sqlLangUpdate = "update translation  set value = ? where s_id = ? and text_id = ? and value != ?;"; 			
			pstmtLangUpdate = connectionSD.prepareStatement(sqlLangUpdate);		// Assumes all languages and types TODO
			
			String sqlMaxSeq = "select max(seq) from option where q_id = ?;";
			pstmtMaxSeq = connectionSD.prepareStatement(sqlMaxSeq);
		
			ArrayList<String> languages = GeneralUtilityMethods.getLanguagesForSurvey(connectionSD, sId);
			int currentQId = -1;
			int maxSeq = -1;
			ResultSet rs = null;
			int totalCount = 0;		// Total changes for this change set
			
			for(ChangeItem ci : changeItemList) {
				
				int count = 0;		// Count of changes for this change item
			
				// Get the max sequence number
				if(currentQId != ci.qId) {
					// Get current maximum sequence
					pstmtMaxSeq.setInt(1, ci.qId);
					rs = pstmtMaxSeq.executeQuery();
					if(rs.next()) {
						maxSeq = rs.getInt(1);
					}			
				}
				
				// Get the text_id for this option
				pstmtOptionGet.setInt(1, ci.qId);
				pstmtOptionGet.setString(2, ci.key);
				log.info("Get text_id for option: " + pstmtOptionGet.toString());
				rs = pstmtOptionGet.executeQuery();
				if(rs.next()) {
					
					String text_id = rs.getString(1);
					
					pstmtLangUpdate.setString(1, ci.newVal);
					pstmtLangUpdate.setInt(2, sId);
					pstmtLangUpdate.setString(3, text_id);
					pstmtLangUpdate.setString(4, ci.newVal);
					log.info("Update existing option label: " + pstmtLangUpdate.toString());
					count = pstmtLangUpdate.executeUpdate();
					
				} else {
					
					// Create a new option
					
					// Set text id
					maxSeq++;
					String text_id = "external_" + ci.qId + "_" + maxSeq;
					// Insert new option		
					pstmtOptionInsert.setInt(1, ci.qId);
					pstmtOptionInsert.setInt(2, maxSeq);
					pstmtOptionInsert.setString(3, text_id);
					pstmtOptionInsert.setString(4, ci.key);
					count = pstmtOptionInsert.executeUpdate();
					
					// Set label
					pstmtLangInsert.setInt(1, sId);
					pstmtLangInsert.setString(3, text_id);
					pstmtLangInsert.setString(4, "none");
					pstmtLangInsert.setString(5, ci.newVal);
					for(String language : languages) {
						pstmtLangInsert.setString(2, language);
						count += pstmtLangInsert.executeUpdate();
					}	
					
				}			
				
				// Write the change log
				if(count > 0) {
					ci.changeType = "option_update";
					Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
					pstmtChangeLog.setInt(1, sId);
					pstmtChangeLog.setInt(2, version);
					pstmtChangeLog.setString(3, gson.toJson(ci));
					pstmtChangeLog.setInt(4,userId);
					if(ci.qType != null && ci.qType.equals("select")) {
						pstmtChangeLog.setBoolean(5, true);
					} else {
						pstmtChangeLog.setBoolean(5, false);
					}
					pstmtChangeLog.setTimestamp(6, getTimeStamp());
					pstmtChangeLog.execute();

				}
				totalCount += count;
			}
			
			if(totalCount == 0) {
				log.info("Info: No changes applied");
				throw new Exception("No changes applied");		
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLangInsert != null) {pstmtLangInsert.close();}} catch (SQLException e) {}
			try {if (pstmtLangUpdate != null) {pstmtLangUpdate.close();}} catch (SQLException e) {}
			try {if (pstmtOptionInsert != null) {pstmtOptionInsert.close();}} catch (SQLException e) {}
			try {if (pstmtOptionGet != null) {pstmtOptionGet.close();}} catch (SQLException e) {}
			try {if (pstmtMaxSeq != null) {pstmtMaxSeq.close();}} catch (SQLException e) {}
		}
	}
		
	/*
	 * Apply property changes
	 * This can be any simple property type such as relevance
	 */
	public void applyProperty(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String property) throws Exception {

		PreparedStatement pstmtProperty1 = null;
		PreparedStatement pstmtProperty2 = null;
		
		/*
		 * Validate the passed in property and make sure it is one that is ok to update
		 */
		if(property.equals("appearance")) {
			 
		
			try {
				
				// Create prepared statements, one for the case where an existing value is being updated
				String sqlProperty1 = "update question set " + property + " = ? " +
						"where q_id = ? and " + property + " = ?;";
				pstmtProperty1 = connectionSD.prepareStatement(sqlProperty1);
				
				String sqlProperty2 = "update question set " + property + " = ? " +
						"where q_id = ? and " + property + " is null;";
				pstmtProperty2 = connectionSD.prepareStatement(sqlProperty2);
				
				for(ChangeItem ci : changeItemList) {
					
					int count = 0;

					if(ci.oldVal != null && !ci.oldVal.equals("NULL")) {
						pstmtProperty1.setString(1, ci.newVal);
						pstmtProperty1.setInt(2, ci.qId);
						pstmtProperty1.setString(3, ci.oldVal);
						log.info("Update question property: " + pstmtProperty1.toString());
						count = pstmtProperty1.executeUpdate();
					} else {
						pstmtProperty2.setString(1, ci.newVal);
						pstmtProperty2.setInt(2, ci.qId);
						log.info("Update question property: " + pstmtProperty2.toString());
						count = pstmtProperty2.executeUpdate();
					}
					if(count == 0) {
						throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
					}
					
					
					log.info("userevent: " + userId + " : modify survey property : " + property + " to: " + ci.newVal + " survey: " + sId);
					
					// Write the change log
					Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
					pstmtChangeLog.setInt(1, sId);
					pstmtChangeLog.setInt(2, version);
					pstmtChangeLog.setString(3, gson.toJson(ci));
					pstmtChangeLog.setInt(4,userId);
					pstmtChangeLog.setBoolean(5,false);
					pstmtChangeLog.setTimestamp(6, getTimeStamp());
					pstmtChangeLog.execute();
				}
			} catch (Exception e) {
				
				String msg = e.getMessage();
				if(msg == null || !msg.startsWith("Already modified")) {
					log.log(Level.SEVERE,"Error", e);
				}
				throw e;
			} finally {
				try {if (pstmtProperty1 != null) {pstmtProperty1.close();}} catch (SQLException e) {}
				try {if (pstmtProperty2 != null) {pstmtProperty2.close();}} catch (SQLException e) {}
			
			}
		} else {
			throw new Exception("Error: Invalid property could not update: " + property);
		}
	}
	
    /*
     * Get the results
     * @param form
     * @param id
     * @param parentId
     */
    ArrayList<ArrayList<Result>> getResults(
    		Form form,
    		int fIdx,
    		int id, 
    		int parentId, 
    		Connection cResults,
    		String instanceId,
    		int parentKey,
    		Survey s) throws SQLException{
 
    	ArrayList<ArrayList<Result>> output = new ArrayList<ArrayList<Result>> ();
    	
    	/*
    	 * Retrieve the results record from the database (excluding select questions)
    	 *  Select questions are retrieved using a separate query as there are multiple 
    	 *  columns per question
    	 */
    	String sql = null;
    	boolean isTopLevel = false;
    	if(parentKey == 0) {
    		sql = "select prikey, _user ";		// Get user if this is a top level form
    		isTopLevel = true;
    	} else {
    		sql = "select prikey ";
    	}
    	ArrayList<Question> questions = form.questions;
    	PreparedStatement pstmt = null;
    	PreparedStatement pstmtSelect = null;
    	ResultSet resultSet = null;
    	
    	try {
    		
    		/*
    		 * Get the result set of data if an instanceID was passed or if 
    		 * this request is for a child form and real data is required
    		 */
    		if(instanceId != null || parentKey > 0) {
		    	for(Question q : questions) {
		    		String col = null;
		    				
		    		if(s.getSubForm(form, q) == null) {
		    			// This question is not a place holder for a subform
		    			if(q.source != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
				    		String qType = q.type;
				    		if(qType.equals("geopoint")) {
				    			col = "ST_AsText(" + q.colName + ")";
				    		} else if(qType.equals("select")){
				    			continue;	// Select data columns are retrieved separately as there are multiple columns per question
				    		} else {
				    			col = q.colName;
				    		}
				
				    		sql += "," + col;
		    			}
		    		}
		
		    	}
		    	sql += " from " + form.tableName;
		    	if(parentId == 0) {
		    		sql += " where instanceid = ?;";
		    	} else {
		    		sql += " where parkey = ?;";
		    	}
		
		    	pstmt = cResults.prepareStatement(sql);	 
		    	if(instanceId != null) {
		    		pstmt.setString(1, instanceId);;
		    	} else {
		    		pstmt.setInt(1, parentKey);
		    	}
		    	log.info("Retrieving results: " + pstmt.toString());
		    	resultSet = pstmt.executeQuery();
    		}
			
    		if (resultSet != null) {
		    	// For each record returned from the database add the data values to the instance
		    	while(resultSet.next()) {
	    		
		    		ArrayList<Result> record = new ArrayList<Result> ();
	    		
		    		String priKey = resultSet.getString(1);
		    		int newParentKey = resultSet.getInt(1);   		
		    		record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null));
		    		
		    		if(isTopLevel) {
		    			String user = resultSet.getString(2);
		    			record.add(new Result("user", "user", user, false, fIdx, -1, 0, null));
		    		}
	    		
		    		addDataForQuestions(
		    				cResults,
		    				resultSet, 
		    				record, 
		    				priKey,
		    				newParentKey, 
		    				s, 
		    				form, 
		    				questions, 
		    				fIdx, 
		    				id,
		    				pstmtSelect,
		    				isTopLevel);

		    		output.add(record);
		    	}
	    	} else {
	    		// Add dummy values for a blank form
	    		
	    		ArrayList<Result> record = new ArrayList<Result> ();
	    		
	    		String priKey = "";
	    		int newParentKey = 0;
	    		record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null)); 
	    		
	    		if(isTopLevel) {
	    			record.add(new Result("user", "user", null, false, fIdx, -1, 0, null)); 
	    		}
    		
	    		addDataForQuestions(
	    				cResults,
	    				resultSet, 
	    				record, 
	    				priKey,
	    				newParentKey, 
	    				s, 
	    				form, 
	    				questions, 
	    				fIdx, 
	    				id,
	    				pstmtSelect,
	    				isTopLevel);

	    		output.add(record);
	    	}
    	} catch (SQLException e) {
    		throw e;
    	} finally {
    		if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
    		if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
    	}
    	
		return output;
    }
    
    /*
     * Add the record containing the results for this form
     * If the resultSet is null then populate with blank data
     */
    private void addDataForQuestions(
    		Connection cResults,
    		ResultSet resultSet, 
    		ArrayList<Result> record, 
    		String priKey,
    		int newParentKey,
    		Survey s,
    		Form form,
    		ArrayList<Question> questions,
    		int fIdx,
    		int id,
    		PreparedStatement pstmtSelect,
    		boolean isTopLevel) throws SQLException {
		/*
		 * Add data for the remaining questions (prikey and user have already been extracted)
		 */
		int index = 2;
		if(isTopLevel) {
			index = 3;
		}
		
		int qIdx = -1;					// Index into question array for this form
		for(Question q : questions) {
			qIdx++;
			
			String qName = q.name;
			String qType = q.type; 
			String qSource = q.source;
			String listName = q.list_name;
			
			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
    			Form subForm = s.getSubForm(form, q);
    			
    			if(subForm != null) {	
    				Result nr = new Result(qName, "form", null, false, fIdx, qIdx, 0, null);

    				nr.subForm = getResults(subForm, 
    						s.getFormIdx(subForm.id),
    			    		subForm.id, 
    			    		id, 
    			    		cResults,
    			    		null,
    			    		newParentKey,
    			    		s);

            		record.add(nr);
    			}
    			
    			if(qType.equals("begin repeat")) {
    				index--;		// Decrement the index as the begin repeat was not in the SQL query
    			}
    			
    		} else if(qType.equals("begin group")) { 
    			
    			record.add(new Result(qName, qType, null, false, fIdx, qIdx, 0, null));
    			index--;		// Decrement the index as the begin group was not in the SQL query
    			
    		} else if(qType.equals("end group")) { 
    			
    			record.add(new Result(qName, qType, null, false, fIdx, qIdx, 0, null));
    			index--;		// Decrement the index as the end group was not in the SQL query
    			
    		} else if(qType.equals("select")) {		// Get the data from all the option columns
    				
				String sqlSelect = "select ";
				ArrayList<Option> options = new ArrayList<Option>(q.getValidChoices(s));

				boolean hasColumns = false;
				for(Option option : options) {
					if(hasColumns) {
						sqlSelect += ",";
					}
					sqlSelect += q.colName + "__" + UtilityMethodsEmail.cleanName(option.value); 
					hasColumns = true;
				}
				sqlSelect += " from " + form.tableName + " where prikey=" + priKey + ";";
	
				ResultSet resultSetOptions = null;
				if(resultSet != null) {
					if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
			    	pstmtSelect = cResults.prepareStatement(sqlSelect);	 			
			    	resultSetOptions = pstmtSelect.executeQuery();
			    	resultSetOptions.next();		// There will only be one record
				}
	    		
		    	Result nr = new Result(qName, qType, null, false, fIdx, qIdx, 0, listName);
		    	hasColumns = false;
		    	int oIdx = -1;
		    	for(Option option : options) {
		    		oIdx++;
		    		String opt = q.colName + "__" + UtilityMethodsEmail.cleanName(option.value);
		    		boolean optSet = false;
		    		if(resultSetOptions != null) {
		    			optSet = resultSetOptions.getBoolean(opt);
		    		}
			    	nr.choices.add(new Result(option.value, "choice", null, optSet, fIdx, qIdx, oIdx, listName)); 

		    		
				}
		    	record.add(nr);	
		    	
		    	index--;		// Decrement the index as the select multiple was not in the SQL query
			
			} else if(qType.equals("select1")) {		// Get the data from all the option columns
				
				ArrayList<Option> options = new ArrayList<Option>(q.getValidChoices(s));
				Result nr = new Result(qName, qType, null, false, fIdx, qIdx, 0, null);
				String value = "";
				if(resultSet != null) {
					value = resultSet.getString(index);
				}
				
				int oIdx = -1;
				for(Option option : options) {
					oIdx++;
		    		boolean optSet = option.value.equals(value) ? true : false;	
			    	nr.choices.add(new Result(option.value, "choice", null, optSet, fIdx, qIdx, oIdx, listName)); 
				}
		    	record.add(nr);	

	    		
			} else if(qSource != null) {

				String value = "";
				if(resultSet != null) {
					value = resultSet.getString(index);
				}
				
				if(value != null && qType.equals("geopoint")) {
					int idx1 = value.indexOf('(');
					int idx2 = value.indexOf(')');
					if(idx1 > 0 && (idx2 > idx1)) {
    					value = value.substring(idx1 + 1, idx2 );
    					// These values are in the order longitude latitude.  This needs to be reversed for the XForm
    					String [] coords = value.split(" ");
    					if(coords.length > 1) {
    						value = coords[1] + " " + coords[0] + " 0 0";
    					}
					} else {
						log.severe("Invalid value for geopoint: " + value);
						value = null;
					}
				} 
				
				// Ignore data not provided by user
				if(!qSource.equals("user")) {	
					value="";
				}

        		record.add(new Result(qName, qType, value, false, fIdx, qIdx, 0, null));

			}
			try {
				//System.out.println("Index: " + index + " : " + q.name + " : " + q.type + " ; " + resultSet.getString(index));
			} catch (Exception e) {
				
			}
			index++;
			
		}
    }
	
}
