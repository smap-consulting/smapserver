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

import org.smap.sdal.Utilities.MediaUtilities;
import org.smap.sdal.Utilities.UtilityMethods;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Question;
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
		log.info(sql + " : " + user + " : " + projectId);
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

			log.info(pstmt.toString());
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
	
	public Survey getById(Connection sd, PreparedStatement pstmt,
			String user,
			int sId,
			boolean full,	// Get the full details of the survey
			String basePath
			) throws Exception {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.s_id, s.name, s.ident, s.display_name, s.deleted, p.name, p.id, s.def_lang, u.o_id" +
				" from survey s, users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and u.ident = ? " +
				" and s.s_id = ?; ";
	
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setString(1, user);
		pstmt.setInt(2, sId);

		log.info(sql + " : " + user + " : " + sId);
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
			
		} 
		
		if(full && s != null) {
			populateSurvey(sd, s, basePath, user);
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
			
			System.out.println(pstmt.toString() + " : " + user);
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
		String sqlGetForms = "select f.f_id, f.name, f.parentform from form f where f.s_id = ?;";
		PreparedStatement pstmtGetForms = sd.prepareStatement(sqlGetForms);	
		
		// Get the questions belonging to a form
		ResultSet rsGetQuestions = null;
		String sqlGetQuestions = "select q.q_id, q.qname, q.qtype, q.qtext_id, q.list_name, q.infotext_id, q.source from question q "
				+ "where q.f_id = ? "
				+ "and q.qname != '_instanceid' "
				+ "order by q.seq asc;";
		PreparedStatement pstmtGetQuestions = sd.prepareStatement(sqlGetQuestions);

		// Get the options belonging to a question		
		ResultSet rsGetOptions = null;
		String sqlGetOptions = "select o.o_id, o.ovalue as value, o.label_id  from option o where q_id = ? order by seq";
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
		s.languages = MediaUtilities.getLanguagesForSurvey(sd, s.id);
		
		// Get the organisation id
		int oId = MediaUtilities.getOrganisationId(sd, user);
		
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
			
			
			/*
			 * Get the questions for this form
			 */
			System.out.println("SQL: " + sqlGetQuestions + " : " + f.id);
			pstmtGetQuestions.setInt(1, f.id);
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
				UtilityMethods.getLabels(sd, s, q.text_id, q.hint_id, q.labels, basePath, oId);
				
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
							
							// Get the labels for the option
							UtilityMethods.getLabels(sd, s, o.text_id, null, o.labels, basePath, oId);
							options.add(o);
						}
						
						s.optionLists.put(q.list_name, options);
					}
				}
							
				f.questions.add(q);
			}
			
			s.forms.add(f);
			
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
		
		// Add the survey manifests
		/*
		 * No longer needed survey level manifests are now in the survey table
		pstmt7.setInt(1, s.getId());	
		resultSet7 = pstmt7.executeQuery();
		
		while (resultSet7.next()) {
			ManifestValue mv = new ManifestValue();
			mv.value = resultSet7.getString(1);
			mv.filename = resultSet7.getString(2);		// Set the filename to the ext id
			mv.type = resultSet7.getString(3);

			s.surveyManifest.add(mv);
		}
		*/
		
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
	 * Save a survey level or organisation level manifest file
	 *
	 *Deprecated now set on template load
	 *
	public void saveSurveyManifest(Connection connection, 
			PreparedStatement pstmtLanguages, 
			PreparedStatement pstmtQuestions,
			PreparedStatement pstmtOptions,
			PreparedStatement pstmtDel,
			PreparedStatement pstmtInsert,
			PreparedStatement pstmtGetIdent,
			int sId,
			ManifestValue mv) throws SQLException {

		List<String> lang = new ArrayList<String>();
	    String text_id = null;
		
	    if(mv.filename != null) {
	    	mv.filename = mv.filename.replaceAll(" ", "_");		// remove spaces
	    }
		pstmtLanguages.setInt(1, sId);
		ResultSet rs = pstmtLanguages.executeQuery();
		while(rs.next()) {
			lang.add(rs.getString(1));
		}
		if(lang.size() == 0) {
			lang.add("eng");	// Default to english
		}
		
		// Get the survey ident
		String survey_ident = null;
		pstmtGetIdent.setInt(1, sId);
		ResultSet resultSet = pstmtGetIdent.executeQuery();
		if(resultSet.next()) {
			survey_ident = resultSet.getString(1);
		} 
		
		// 2) Get the text_id
	    if(mv.qId == -1) {
	    	System.out.println("Survey");
	    	text_id = mv.filename;
	    } else if(mv.oId == -1) {
	    	System.out.println("Question");
	    	

	    	pstmtQuestions.setInt(1, mv.qId);
	    	rs = pstmtQuestions.executeQuery();
	    	if(rs.next()) {
				text_id = rs.getString(1);
			}
	    } else {
	    	System.out.println("Option");
	    	

	    	pstmtOptions.setInt(1, mv.oId);
	    	rs = pstmtOptions.executeQuery();
	    	if(rs.next()) {
				text_id = rs.getString(1);
			}
	    }
	    System.out.println("Text id:" + text_id);

	    if(text_id != null) {
	    	
	    	// 3) Delete existing media file
	    	pstmtDel.setInt(1, sId);
	    	pstmtDel.setString(2, text_id);
	    	pstmtDel.setString(3, mv.type);
	    	pstmtDel.execute();
	    	
	    	// 4) Insert new media file for each language
	    	for(int i = 0; i < lang.size(); i++) {
	    		String language = lang.get(i);

		    	pstmtInsert.setInt(1, sId);
		    	pstmtInsert.setString(2, text_id);
		    	pstmtInsert.setString(3, mv.type);
		    	pstmtInsert.setString(4, survey_ident + "/" + mv.filename);
		    	pstmtInsert.setString(5, language);
		    	pstmtInsert.execute();
	    	}
	    }
	}
	*/
	
	
	/*
	 * Get a Survey containing project and the block status 
	 */
	public Survey getSurveyId(Connection sd, String key) {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.p_id, s.s_id, s.blocked " +
				" from survey s" +
				" where s.ident = ?; ";
		
		String sql2 = "select s.p_id, s.s_id, s.blocked " +		// Hack due to issue with upgrade of a server where ident not set to survey id by default
				" from survey s" +
				" where s.s_id = ?; ";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, key);
			
			System.out.println("Sql: " + sql + " : " + key);
	
			resultSet = pstmt.executeQuery();
	
			if (resultSet.next()) {								
				s = new Survey();
				s.setPId(resultSet.getInt(1));
				s.setId(resultSet.getInt(2));
				s.setBlocked(resultSet.getBoolean(3));
				
			} else {	// Attempt to find the survey assuming the ident is the survey id
				pstmt2 = sd.prepareStatement(sql2);	
				int sId = Integer.parseInt(key);
				pstmt2.setInt(1, sId);
				
				System.out.println("Sql2: " + sql2 + " : " + sId);
				
				resultSet = pstmt2.executeQuery();
				
				if (resultSet.next()) {								
					s = new Survey();
					s.setPId(resultSet.getInt(1));
					s.setId(resultSet.getInt(2));
					s.setBlocked(resultSet.getBoolean(3));
				} else {			
					System.out.println("Error: survey not found");
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
					
					System.out.println("applyChanges: " + cs.type);
					if(cs.type.equals("label")) {
						
						applyLabel(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version);

					} else if(cs.type.equals("option_update")) {
						
						applyOptionUpdates(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version);
						
					}
					
					
					// Success
					cs.updateFailed = false;
					resp.success++;
				} catch (Exception e) {
					
					// Failure
					connectionSD.rollback(sp);
					String msg = e.getMessage();
					System.out.println(e.getMessage());
					cs.updateFailed = true;
					cs.errorMsg = e.getMessage();
					resp.failed++;
				}
				
			}
			
			if(resp.success > 0) {
				connectionSD.commit();
				System.out.println("Survey update to version: " + resp.version + ". " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			} else {
				connectionSD.rollback();
				System.out.println("Survey version not updated: " + 
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
	 * ========================= Type specific update fuction
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
		
		try {
			
			// Create prepared statements, one for the case where an existing value is being updated
			String sqlLangOldVal = "update translation set value = ? " +
					"where s_id = ? and language = ? and text_id = ? and type = ? and value = ?;";
			pstmtLangOldVal = connectionSD.prepareStatement(sqlLangOldVal);
		
			String sqlLangNew = "insert into translation (value, s_id, language, text_id, type) values(?,?,?,?,?);";
			pstmtLangNew = connectionSD.prepareStatement(sqlLangNew);
			
			for(ChangeItem ci : changeItemList) {
			
				if(ci.oldVal != null) {
					pstmtLangOldVal.setString(1, ci.newVal);
					pstmtLangOldVal.setInt(2, sId);
					pstmtLangOldVal.setString(3, ci.languageName);
					pstmtLangOldVal.setString(4, ci.key);
					if(ci.element.equals("text")) {
						transType = "none";
					} else {
						transType = ci.element;
					}
					pstmtLangOldVal.setString(5,  transType);
					pstmtLangOldVal.setString(6, ci.oldVal);

					log.info("SQL: " + pstmtLangOldVal.toString());
					
					int count = pstmtLangOldVal.executeUpdate();
					if(count == 0) {
						System.out.println("Error: Element already modified");
						throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
					}
				} else {
					pstmtLangNew.setString(1, ci.newVal);
					pstmtLangNew.setInt(2, sId);
					pstmtLangNew.setString(3, ci.languageName);
					pstmtLangNew.setString(4, ci.key);
					if(ci.element.equals("text")) {
						transType = "none";
					} else {
						transType = ci.element;
					}
					pstmtLangNew.setString(5,  transType);
					
					log.info("SQL: " + pstmtLangNew.toString());
					
					 pstmtLangNew.executeUpdate();
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
		
			ArrayList<String> languages = MediaUtilities.getLanguagesForSurvey(connectionSD, sId);
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
				
				System.out.println("Maximum sequence number: " + maxSeq);
				
				// Get the text_id for this option
				pstmtOptionGet.setInt(1, ci.qId);
				pstmtOptionGet.setString(2, ci.key);
				rs = pstmtOptionGet.executeQuery();
				if(rs.next()) {
					
					String text_id = rs.getString(1);
					// Update the label for all languages
					System.out.println("Updating label");	
					
					pstmtLangUpdate.setString(1, ci.newVal);
					pstmtLangUpdate.setInt(2, sId);
					pstmtLangUpdate.setString(3, text_id);
					pstmtLangUpdate.setString(4, ci.newVal);
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
				System.out.println("Info: No changes applied");
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
	
}
