package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.smap.sdal.model.ChangeItem;
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
	
	public Survey getById(Connection sd, PreparedStatement pstmt,
			String user,
			int sId,
			boolean full	// Get the full details of the survey
			) throws SQLException {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.s_id, s.name, s.ident, s.display_name, s.deleted, p.name, p.id, s.def_lang" +
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
			
		} 
		
		if(full && s != null) {
			populateSurvey(sd, s);
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
		
		ResultSet resultSet = null;
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident" +
				" from survey s, users u, user_project up, project p" +
				" where s.appearance = 'search(''?'')' " +
				" and s.p_id = p.id" +
				" and p.o_id = u.o_id" +
				" and u.ident = ? " +
				"order BY s.display_name;";
		
	
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, csvRoot);
			pstmt.setString(2, user);
			
			log.info(sql + " : " + user);
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
	 * Add survey details
	 */
	private void populateSurvey(Connection sd, Survey s) throws SQLException {
		
		ResultSet resultSet1 = null;
		ResultSet resultSet2 = null;
		ResultSet resultSet3 = null;
		ResultSet resultSet5 = null;
		ResultSet resultSet6 = null;
		ResultSet resultSet7 = null;
		ResultSet resultSet8 = null;
		String sql = "select f.f_id, f.name from form f where f.s_id = ?;";
		String sql2 = "select q.q_id, q.qname, q.qtype, q.qtext_id, q.list_name, q.infotext_id from question q where f_id = ?";
		String sql3 = "select distinct t.language from translation t where s_id = ? order by t.language asc";
		String sql4 = "select t.type, t.value from translation t where t.s_id = ? and t.language = ? and t.text_id = ?";
		String sql5 = "select o.o_id, o.ovalue as value, o.label_id  from option o where q_id = ? order by seq";
		String sql6 = "SELECT ssc.id, ssc.name, ssc.function, ssc.parameters, ssc.units, f.name, f.f_id " +
				"FROM ssc ssc, form f WHERE ssc.s_id = ? AND ssc.f_id = f.f_id ORDER BY id";
		String sql7 = "SELECT t.value, t.text_id, t.type FROM translation t where t.s_id = ?" +
				" and t.type = 'csv' ";	
		String sql8 = "SELECT c.changes, c.c_id, c.version, u.name, c.updated_time " +
				"from survey_change c, users u " +
				"where c.s_id = ? " +
				"and c.user_id = u.id " +
				"order by c_id desc; ";
	
		PreparedStatement pstmt1 = sd.prepareStatement(sql);	
		PreparedStatement pstmt2 = sd.prepareStatement(sql2);
		PreparedStatement pstmt3 = sd.prepareStatement(sql3);
		PreparedStatement pstmt4 = sd.prepareStatement(sql4);
		PreparedStatement pstmt5 = sd.prepareStatement(sql5);
		PreparedStatement pstmt6 = sd.prepareStatement(sql6);
		PreparedStatement pstmt7 = sd.prepareStatement(sql7);
		PreparedStatement pstmt8 = sd.prepareStatement(sql8);
		
		// Add Languages	
		pstmt3.setInt(1, s.getId());
		resultSet3 = pstmt3.executeQuery();
		
		while (resultSet3.next()) {
			s.languages.add(resultSet3.getString(1));
		}
		
		// Set the default language if it has not previously been set
		if(s.def_lang == null) {
			s.def_lang = s.languages.get(0);
		}
		
		// Add Forms
		pstmt1.setInt(1, s.id);
		resultSet1 = pstmt1.executeQuery();
		
		while (resultSet1.next()) {								
			Form f = new Form();
			f.id = resultSet1.getInt(1);
			f.name = resultSet1.getString(2);
			
			/*
			 * Get the questions for this form
			 */
			System.out.println("SQL: " + sql2 + " : " + f.id);
			pstmt2.setInt(1, f.id);
			resultSet2 = pstmt2.executeQuery();
			
			while (resultSet2.next()) {
				Question q = new Question();
				q.id = resultSet2.getInt(1);
				q.name = resultSet2.getString(2);
				q.type = resultSet2.getString(3);
				q.text_id = resultSet2.getString(4);
				q.list_name = resultSet2.getString(5);
				q.hint_id = resultSet2.getString(6);
				
				// If the survey was loaded from xls it will not have a list name
				if(q.list_name == null || q.list_name.trim().length() == 0) {
					q.list_name = String.valueOf(q.id);
				}
				
				// Get the language labels
				getLabels(pstmt4, s, q.text_id, q.hint_id, q.labels);
				
				/*
				 * If this is a select question get the options
				 */
				if(q.type.startsWith("select")) {
					ArrayList<Option> options = s.optionLists.get(q.list_name);
					if(options == null) {
						// Ignore if this option list has already been added by another question
						options = new ArrayList<Option> ();
						
						pstmt5.setInt(1, q.id);
						resultSet5 = pstmt5.executeQuery();
						while(resultSet5.next()) {
							Option o = new Option();
							o.id = resultSet5.getInt(1);
							o.value = resultSet5.getString(2);
							o.text_id = resultSet5.getString(3);
							
							// Get the labels for the option
							getLabels(pstmt4, s, o.text_id, null, o.labels);
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
		pstmt6.setInt(1, s.getId());
		resultSet6= pstmt6.executeQuery();
		
		while (resultSet6.next()) {
			ServerSideCalculate ssc = new ServerSideCalculate();
			ssc.setId(resultSet6.getInt(1));
			ssc.setName(resultSet6.getString(2));
			ssc.setFunction(resultSet6.getString(3));
			ssc.setUnits(resultSet6.getString(5));
			ssc.setForm(resultSet6.getString(6));
			ssc.setFormId(resultSet6.getInt(7));
			s.sscList.add(ssc);
		}
		
		// Add the survey manifests
		pstmt7.setInt(1, s.getId());	
		resultSet7 = pstmt7.executeQuery();
		
		while (resultSet7.next()) {
			ManifestValue mv = new ManifestValue();
			mv.value = resultSet7.getString(1);
			mv.filename = resultSet7.getString(2);		// Set the filename to the ext id
			mv.type = resultSet7.getString(3);

			s.surveyManifest.add(mv);
		}
		
		// Add the change log
		pstmt8.setInt(1, s.getId());
		resultSet8 = pstmt8.executeQuery();
		
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		while (resultSet8.next()) {
			
			ChangeItem ci = gson.fromJson(resultSet8.getString(1), ChangeItem.class);
			
			ci.cId = resultSet8.getInt(2);
			ci.version = resultSet8.getInt(3);
			ci.userName = resultSet8.getString(4);
			ci.updatedTime = resultSet8.getTimestamp(5);
			s.changes.add(ci);
		}
		
		// Close statements
		try { if (pstmt1 != null) {pstmt1.close();}} catch (SQLException e) {}
		try { if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
		try { if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
		try { if (pstmt4 != null) {pstmt4.close();}} catch (SQLException e) {}
		try { if (pstmt5 != null) {pstmt5.close();}} catch (SQLException e) {}
		try { if (pstmt6 != null) {pstmt6.close();}} catch (SQLException e) {}
		try { if (pstmt7 != null) {pstmt7.close();}} catch (SQLException e) {}
		try { if (pstmt8 != null) {pstmt8.close();}} catch (SQLException e) {}
	}
	
	/*
	 * Get all the labels for the question or option
	 */
	private void getLabels(PreparedStatement pstmt, 
			Survey s, 
			String text_id, 
			String hint_id, 
			ArrayList<Label> labels) throws SQLException {
		for(int i = 0; i < s.languages.size(); i++) {
			
			Label l = new Label();
			
			// Get label and media
			pstmt.setInt(1, s.id);
			pstmt.setString(2, s.languages.get(i));
			pstmt.setString(3, text_id);			
			ResultSet resultSet = pstmt.executeQuery();		
			while(resultSet.next()) {

				String t = resultSet.getString(1).trim();
				String v = resultSet.getString(2);
				
				if(t.equals("none")) {
					l.text = v;
				} else if(t.equals("image")) {
					l.image = v;
				} else if(t.equals("audio")) {
					l.audio = v;
				} else if(t.equals("video")) {
					l.video = v;
				} 

			}
			
			// Get hint
			if(hint_id != null) {
				pstmt.setInt(1, s.id);
				pstmt.setString(2, s.languages.get(i));
				pstmt.setString(3, hint_id);
				
				resultSet = pstmt.executeQuery();
				
				if(resultSet.next()) {
					String t = resultSet.getString(1).trim();
					String v = resultSet.getString(2);
					
					if(t.equals("none")) {
						l.hint = v;
					} else {
						System.out.println("Error: Invalid type for hint: " + t);
					}
				}
			}
			
			labels.add(l);	
			
		}
	}
	
	/*
	 * Get all the manifest elements that are attached to the entire survey
	 */
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
	 * Apply a set of changes as sent by survey editor
	 * Returns the changes that were not applied
	 */
	public void applyChanges(PreparedStatement pstmtLang, 
			PreparedStatement pstmtChangeLog,
			int userId,
			int sId,
			String change_type,
			ArrayList<ChangeItem> changes,
			int version,
			Gson gson) throws SQLException, Exception {
		
		String transType = null;
		
		System.out.println("applyChanges: " + change_type);
		if(change_type.equals("label")) {
			for(ChangeItem change : changes) {
		
					pstmtLang.setString(1, change.newVal);
					pstmtLang.setInt(2, sId);
					pstmtLang.setString(3, change.languageName);
					pstmtLang.setString(4, change.transId);
					if(change.element.equals("text")) {
						transType = "none";
					} else {
						transType = change.element;
					}
					pstmtLang.setString(5,  transType);
					pstmtLang.setString(6, change.oldVal);
					
					System.out.println("Survey update: " + change.name + " from::" + change.oldVal + ":: to ::" + change.newVal);
					
					log.info("userevent: " + userId + " : modify survey label : " + change.transId + " to: " + change.newVal + " survey: " + sId + " language: " + change.languageName + " labelId: "  + transType);
					
					int count = pstmtLang.executeUpdate();
					if(count == 0) {
						System.out.println("Error: Element already modified");
						throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
					}
					
					// Write the change log
					pstmtChangeLog.setInt(1, sId);
					pstmtChangeLog.setInt(2, version);
					pstmtChangeLog.setString(3, gson.toJson(change));
					pstmtChangeLog.setInt(4,userId);
					pstmtChangeLog.setTimestamp(5, getTimeStamp());
					pstmtChangeLog.execute();
			}

		} else if(change_type.equals("new_multichoice_option")) {
			System.out.println("Changing multiple choice ");
		}

		
	}
	
	// Get the timestamp
	public static Timestamp getTimeStamp() {
		 
		Date today = new Date();
		return new Timestamp(today.getTime());
	 
	}
	
}
