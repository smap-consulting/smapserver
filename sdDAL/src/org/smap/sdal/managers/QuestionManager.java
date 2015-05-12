package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.UtilityMethodsEmail;
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

public class QuestionManager {
	
	private static Logger log =
			 Logger.getLogger(QuestionManager.class.getName());

	/*
	 * These functions are used when adding CSV files. 
	 * They will add the choices from the CSV files to the survey definition
	 */
	public ArrayList<Question> getByCSV(Connection sd, 
			int sId,
			String csvFileName			
			)  {
		
		ArrayList<Question> questions = new ArrayList<Question>();	// Results of request
		
		
		Survey survey = new Survey();
		survey.setId(sId);
		
		int idx = csvFileName.lastIndexOf('.');
		String csvRoot = csvFileName;
		if(idx > 0) {
			csvRoot = csvFileName.substring(0, idx);
		}
		
		// Escape csvRoot
		csvRoot = csvRoot.replace("\'", "\'\'");
		
		ResultSet resultSet = null;
		String sql = "select q.q_id, q.qname, q.qtype, q.appearance " +
				" from question q, form f " +
				" where f.f_id = q.f_id " +
				" and q.appearance like 'search(''" + csvRoot + "''%' " +
				" and q.qtype like 'select%' " + 
				" and f.s_id = ?";
	
		String sqlOption = "select o_id, seq, label_id, ovalue " +
				" from option " +
				" where q_id = ? " +
				" and externalfile = 'true';";
		
		String sqlLanguages = "select distinct t.language from translation t " +
				"where s_id = ? order by t.language asc";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtOption = null;
		PreparedStatement pstmtLanguages = null;
		
		try {
			pstmtOption = sd.prepareStatement(sqlOption);
			
			pstmtLanguages = sd.prepareStatement(sqlLanguages);
			pstmtLanguages.setInt(1, sId);
			ResultSet rsLanguages = pstmtLanguages.executeQuery();
			while(rsLanguages.next()) {
				survey.languages.add(rsLanguages.getString(1));
			}
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			
			log.info("Get questions: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
	
			while (resultSet.next()) {								
	
				Question q = new Question();
				q.id = resultSet.getInt(1);
				q.name = resultSet.getString(2);
				q.type = resultSet.getString(3);
				q.appearance = resultSet.getString(4);
				
			
				pstmtOption.setInt(1, q.id);
				ResultSet rsOptions = pstmtOption.executeQuery();
				while (rsOptions.next()) {
					Option o = new Option();
					o.id = rsOptions.getInt("o_id");
					o.seq = rsOptions.getInt("seq");
					o.text_id = rsOptions.getString("label_id");
					
					UtilityMethodsEmail.getLabels( sd, survey, o.text_id, null, o.labels, null, 0);
				}
				
				questions.add(q);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
			if(pstmtOption != null) try{pstmtOption.close();}catch(Exception e){}
			if(pstmtLanguages != null) try{pstmtLanguages.close();}catch(Exception e){}
		}
		
		return questions;
		
	}
	
	/*
	 * Save to the database
	 */
	public void save(Connection sd, int sId, ArrayList<Question> questions) throws SQLException {
		
		PreparedStatement pstmt = null;
		String sql = "insert into question (q_id, f_id, seq, qname, qtype, qtext_id, list_name, infotext_id, "
				+ "source, calculate, "
				+ "defaultanswer, "
				+ "appearance) " 
				+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?,? ,?, ?);";

		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update question set seq = seq + 1 where f_id = ? and seq >= ?;";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmt = sd.prepareStatement(sql);
			
			for(Question q : questions) {
				
				// Update sequence numbers of questions after the question to be inserted
				pstmtUpdateSeq.setInt(1, q.fId);
				pstmtUpdateSeq.setInt(2, q.seq);
				
				log.info("Update sequences: " + pstmtUpdateSeq.toString());
				pstmtUpdateSeq.executeUpdate();
				
				// Insert the question
				pstmt.setInt(1, q.fId );
				pstmt.setInt(2, q.seq );
				pstmt.setString(3, q.name );
				pstmt.setString(4, q.type );
				pstmt.setString(5, q.text_id );
				pstmt.setString(6, q.list_name );
				pstmt.setString(7, q.hint_id );
				pstmt.setString(8, q.source );
				pstmt.setString(9, q.calculation );
				pstmt.setString(10, q.defaultanswer );
				pstmt.setString(11, q.appearance);
				
				log.info("Insert question: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Save to the database
	 */
	public void delete(Connection sd, int sId, ArrayList<Question> questions) throws SQLException {
		
		PreparedStatement pstmt = null;
		String sql = "delete from question q where q_id = ? and q.q_id in " +
				" (select q_id from question q, form f where q.q_id = f.f_id and f.s_id = ?);";	// Ensure user is authorised to access this question

		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update question set seq = seq - 1 where f_id = ? and seq >= ? and f_id in " +
				"(select f_id from form where s_id = ?)";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmt = sd.prepareStatement(sql);
			
			for(Question q : questions) {
				
				// Insert the question
				pstmt.setInt(1, q.id );
				pstmt.setInt(2, sId );
				
				log.info("Delete question: " + pstmt.toString());
				pstmt.executeUpdate();
				
				// Update sequence numbers of questions after the question that has been deleted
				pstmtUpdateSeq.setInt(1, q.fId);
				pstmtUpdateSeq.setInt(2, q.seq);
				pstmtUpdateSeq.setInt(3, sId);
				
				log.info("Update sequences: " + pstmtUpdateSeq.toString());
				pstmtUpdateSeq.executeUpdate();
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Save options
	 */
	public void saveOptions(Connection sd, ArrayList<Option> options) throws SQLException {
		
		PreparedStatement pstmtGetQuestions = null;
		String sqlGetQuestions = "select q.q_id " +
				"from question q, survey s, form f " +
				"where q.f_id = f.f_id " + 
				"and f.f_id = s.f_id " +
				"and s.s_id = ?" +
				"and q.list_name = ?";
		
		PreparedStatement pstmt = null;
		String sql = "insert into option (o_id, q_id, seq, label_id, ovalue, cascade_filters, externalfile) " +
				"values (nextval('o_seq'), ?, ?, ?, ?, ?, 'false');";

		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update option set seq = seq + 1 where q_id = ? and seq >= ?;";
		
		try {
			pstmtGetQuestions = sd.prepareStatement(sqlGetQuestions);
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmt = sd.prepareStatement(sql);
			
			for(Option o : options) {
				
				// Get the questions from the form that use this option list
				pstmtGetQuestions.setInt(1, o.sId);
				pstmtGetQuestions.setString(2,  o.optionList);
				log.info("Get questions that use the list: " + pstmtGetQuestions.toString());
				ResultSet rs = pstmtGetQuestions.executeQuery();
				
				while (rs.next()) {
				
					int qId = rs.getInt(1);
					
					// Update sequence numbers of options after the option to be inserted
					pstmtUpdateSeq.setInt(1, qId);
					pstmtUpdateSeq.setInt(2, o.seq);
					
					log.info("Update sequences: " + pstmtUpdateSeq.toString());
					pstmtUpdateSeq.executeUpdate();
					
					// Insert the option
					pstmt.setInt(1, qId );
					pstmt.setInt(2, o.seq );
					pstmt.setString(3, o.text_id );
					pstmt.setString(4, o.value );
					pstmt.setString(5, o.cascadeFilters );			
					
					log.info("Insert question: " + pstmt.toString());
					pstmt.executeUpdate();
				}
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestions != null) {pstmtGetQuestions.close();}} catch (SQLException e) {}
		}	
		
	}
	
}
