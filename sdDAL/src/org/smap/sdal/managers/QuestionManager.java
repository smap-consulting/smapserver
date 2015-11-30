package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Question;
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
		String sql = "select q.q_id, q.qname, q.qtype, q.appearance, q.l_id " +
				" from question q, form f " +
				" where f.f_id = q.f_id " +
				" and q.appearance like '%search(''" + csvRoot + "''%' " +
				" and q.qtype like 'select%' " + 
				" and f.s_id = ?";
	
		String sqlOption = "select o.o_id, o.seq, o.label_id, o.ovalue " +
				" from option o, question q" +
				" where q.q_id = ? " +
				" and o.l_id = 1.l_id" +
				" and externalfile = 'true';";

		PreparedStatement pstmt = null;
		PreparedStatement pstmtOption = null;
		
		try {
			pstmtOption = sd.prepareStatement(sqlOption);
			
			survey.languages = GeneralUtilityMethods.getLanguages(sd, sId);
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			
			log.info("Get questions for CSV: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
	
			while (resultSet.next()) {								
	
				Question q = new Question();
				q.id = resultSet.getInt(1);
				q.name = resultSet.getString(2);
				q.type = resultSet.getString(3);
				q.appearance = resultSet.getString(4);
				q.l_id = resultSet.getInt(5);
				
			
				// TODO following code looks like it does nothing
				/*
				pstmtOption.setInt(1, q.id);
				log.info("Get options for CSV: " + pstmtOption.toString());
				ResultSet rsOptions = pstmtOption.executeQuery();
				while (rsOptions.next()) {
					Option o = new Option();
					o.id = rsOptions.getInt("o_id");
					o.seq = rsOptions.getInt("seq");
					o.text_id = rsOptions.getString("label_id");
					
					UtilityMethodsEmail.getLabels( sd, survey, o.text_id, null, o.labels, null, 0);
				}
				*/
				
				questions.add(q);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
			if(pstmtOption != null) try{pstmtOption.close();}catch(Exception e){}
		}
		
		return questions;
		
	}
	

	/*
	 * Save a new question
	 */
	public void save(Connection sd, int sId, ArrayList<Question> questions) throws Exception {
		
		PreparedStatement pstmtInsertQuestion = null;
		String sql = "insert into question (q_id, f_id, l_id, seq, qname, column_name, qtype, qtext_id, "
				+ "infotext_id, "
				+ "source, calculate, "
				+ "defaultanswer, "
				+ "appearance, "
				+ "visible, "
				+ "path, "
				+ "readonly"
				+ ") " 
				+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ,?, ?, ?, ?, ?);";
		
		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update question set seq = seq + 1 where f_id = ? and seq >= ?;";
		
		PreparedStatement pstmtForm = null;
		String sqlForm = "insert into form(f_id, s_id, name, table_name, parentform, parentquestion, repeats, path, form_index) " +
				"values(nextval('f_seq'), ?, ?, ?, ?, ?, ?, ?, ?);";
		
		PreparedStatement pstmtGetFormId = null;
		String sqlGetFormId = "select f_id from form where s_id = ? and form_index = ?;";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmtInsertQuestion = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			
			for(Question q : questions) {
				
				if(q.fId <= 0) {	// New Form, the formIndex can be used to retrieve the formId of this new form
					
					pstmtGetFormId = sd.prepareStatement(sqlGetFormId);
					pstmtGetFormId.setInt(1, sId);
					pstmtGetFormId.setInt(2, q.formIndex);
					
					log.info("SQL: Get form id: " + pstmtGetFormId.toString());
					ResultSet rs = pstmtGetFormId.executeQuery();
					rs.next();
					q.fId = rs.getInt(1);
					
				}
				
				if(q.type.startsWith("select")) {	// Get the list id
					q.l_id = getListId(sd, sId, q.list_name);
				}
				
				// Update sequence numbers of questions after the question to be inserted
				pstmtUpdateSeq.setInt(1, q.fId);
				pstmtUpdateSeq.setInt(2, q.seq);
				
				log.info("Update sequences: " + pstmtUpdateSeq.toString());
				pstmtUpdateSeq.executeUpdate();
				
				String type = GeneralUtilityMethods.translateTypeToDB(q.type);
				boolean readonly = GeneralUtilityMethods.translateReadonlyToDB(q.type, q.readonly);
			
				// Insert the question
				pstmtInsertQuestion.setInt(1, q.fId );
				pstmtInsertQuestion.setInt(2, q.l_id);
				pstmtInsertQuestion.setInt(3, q.seq );
				pstmtInsertQuestion.setString(4, q.name );
				pstmtInsertQuestion.setString(5, GeneralUtilityMethods.cleanName(q.name, true));
				pstmtInsertQuestion.setString(6, type );
				pstmtInsertQuestion.setString(7, q.path + ":label" );
				pstmtInsertQuestion.setString(8, q.path + ":hint" );
				pstmtInsertQuestion.setString(9, q.source );
				pstmtInsertQuestion.setString(10, q.calculation );
				pstmtInsertQuestion.setString(11, q.defaultanswer );
				pstmtInsertQuestion.setString(12, q.appearance);
				pstmtInsertQuestion.setBoolean(13, q.visible);
				pstmtInsertQuestion.setString(14, q.path);
				pstmtInsertQuestion.setBoolean(15, readonly);
				
				log.info("Insert question: " + pstmtInsertQuestion.toString());
				pstmtInsertQuestion.executeUpdate();
				
				// Set the labels
				UtilityMethodsEmail.setLabels(sd, sId, q.path, q.labels, "");
				
				// If this is a begin repeat then create a new form
				if(q.type.equals("begin repeat")) {
					
					ResultSet rs = pstmtInsertQuestion.getGeneratedKeys();
					rs.next();
					int qId = rs.getInt(1);
					String repeatsPath = null;
					
					if(q.repeats != null && q.repeats.trim().length() > 0) {
						// Create a question to hold the repeat count calculation

						repeatsPath = q.path + "_count";
						
						pstmtInsertQuestion.setInt(1, q.fId );
						pstmtInsertQuestion.setInt(2, q.seq );
						pstmtInsertQuestion.setString(3, q.name + "_count" );
						pstmtInsertQuestion.setString(4, "calculate" );
						pstmtInsertQuestion.setString(5, null );
						pstmtInsertQuestion.setString(6, null );
						pstmtInsertQuestion.setString(7, null );
						pstmtInsertQuestion.setString(8, "user" );
						pstmtInsertQuestion.setString(9, GeneralUtilityMethods.convertAllxlsNames(q.repeats, sId, sd));
						pstmtInsertQuestion.setString(10, null );
						pstmtInsertQuestion.setString(11, null);
						pstmtInsertQuestion.setBoolean(12, false);
						pstmtInsertQuestion.setString(13, q.path + "_count");
						
						log.info("Insert repeat count question: " + pstmtInsertQuestion.toString());
						pstmtInsertQuestion.executeUpdate();
					}
					
					
					// Create the sub form
					String tableName = "s" + sId + "_" + q.name;
			
					pstmtForm = sd.prepareStatement(sqlForm);
					pstmtForm.setInt(1, sId);
					pstmtForm.setString(2, q.name);
					pstmtForm.setString(3, tableName);
					pstmtForm.setInt(4, q.fId);
					pstmtForm.setInt(5, qId);		// parent question id
					pstmtForm.setString(6, repeatsPath);
					pstmtForm.setString(7, q.path);
					pstmtForm.setInt(8, q.childFormIndex);
					
					log.info("SQL: Insert new form: " + pstmtForm.toString());
					pstmtForm.executeUpdate();
					
				}
				
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmtInsertQuestion != null) {pstmtInsertQuestion.close();}} catch (SQLException e) {}
			try {if (pstmtForm != null) {pstmtForm.close();}} catch (SQLException e) {}
			try {if (pstmtGetFormId != null) {pstmtGetFormId.close();}} catch (SQLException e) {}
		}
		
	}
	
	/*
	 * Move a question
	 * This can only be called for questions that are already in the database as otherwise the move is merely added to the
	 *  question creation
	 */
	public void move(Connection sd, int sId, ArrayList<Question> questions) throws Exception {
		
		PreparedStatement pstmt = null;
		String sql = "update question set "
						+ "f_id = ?, "
						+ "seq = ? "
					+ "where qname = ? "
						+ "and seq = ? "	// Ensure question has not been moved by someone else
						+ "and f_id = ?"	// Ensure question has not been moved by someone else
						+ "and q_id in " 
							// Ensure user is authorised to access this question;";
							+ " (select q_id from question q, form f where q.f_id = f.f_id and f.s_id = ?);";	

		try {
	
			pstmt = sd.prepareStatement(sql);
			
			for(Question q : questions) {
				
				
				// Update the question details
				pstmt.setInt(1, q.fId );
				pstmt.setInt(2, q.seq );
				pstmt.setString(3, q.name );
				pstmt.setInt(4, q.sourceSeq);
				pstmt.setInt(5, q.sourceFormId);
				pstmt.setInt(6, sId);
				
				log.info("Move question: " + pstmt.toString());
				int count = pstmt.executeUpdate();
				if(count == 0) {
					log.info("Error: Question already modified");
					throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
				}
				
			}
			
			
		} catch(SQLException e) {
			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Delete
	 */
	public void delete(Connection sd, int sId, ArrayList<Question> questions) throws SQLException {
		
		PreparedStatement pstmt = null;
		String sql = "delete from question q where f_id = ? and qname = ? and q.q_id in " +
				" (select q_id from question q, form f where q.f_id = f.f_id and f.s_id = ?);";	// Ensure user is authorised to access this question

		PreparedStatement pstmtDelLabels = null;
		String sqlDelLabels = "delete from translation t where t.s_id = ? and " +
				"t.text_id in (select qtext_id  from question where qname = ? and f_id = ? and q_id in " +
				" (select q.q_id from question q, form f where q.f_id = f.f_id and f.s_id = ?));";
		
		PreparedStatement pstmtDelHints = null;
		String sqlDelHints = "delete from translation t where t.s_id = ? and " +
				"t.text_id in (select infotext_id  from question where qname = ? and f_id = ? and q_id in " +
				" (select q.q_id from question q, form f where q.f_id = f.f_id and f.s_id = ?));";
		
		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update question set seq = seq - 1 where f_id = ? and seq >= ? and f_id in " +
				"(select f_id from form where s_id = ?)";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmtDelLabels = sd.prepareStatement(sqlDelLabels);
			pstmtDelHints = sd.prepareStatement(sqlDelHints);
			pstmt = sd.prepareStatement(sql);
			
			for(Question q : questions) {
				
		
				// Delete the labels (
				pstmtDelLabels.setInt(1, sId);
				pstmtDelLabels.setString(2, q.name );
				pstmtDelLabels.setInt(3, q.fId);
				pstmtDelLabels.setInt(4, sId );
				
				log.info("Delete question labels: " + pstmtDelLabels.toString());
				pstmtDelLabels.executeUpdate();
				
				// Delete the labels (
				pstmtDelHints.setInt(1, sId);
				pstmtDelHints.setString(2, q.name );
				pstmtDelHints.setInt(3, q.fId);
				pstmtDelHints.setInt(4, sId );
				
				log.info("Delete question hints: " + pstmtDelHints.toString());
				pstmtDelHints.executeUpdate();
				
				// Delete the question
				pstmt.setInt(1, q.fId);
				pstmt.setString(2, q.name );
				pstmt.setInt(3, sId );
				
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
			try {if (pstmtDelLabels != null) {pstmtDelLabels.close();}} catch (SQLException e) {}
			try {if (pstmtDelHints != null) {pstmtDelHints.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Save options
	 * Called by editor
	 * Add new options
	 */
	public void saveOptions(Connection sd, int sId, ArrayList<Option> options, boolean updateLabels) throws SQLException {
		
		PreparedStatement pstmtInsertOption = null;
		String sql = "insert into option (o_id, l_id, seq, label_id, ovalue, column_name, cascade_filters, externalfile) " +
				"values (nextval('o_seq'), ?, ?, ?, ?, ?, ?, 'false');";

		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update option set seq = seq + 1 where l_id = ? and seq >= ?;";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmtInsertOption = sd.prepareStatement(sql);
			
			for(Option o : options) {
				
				// Get the list id for this option
				int listId = getListId(sd, sId, o.optionList);
				Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
				
				
				// Update sequence numbers of options after the option to be inserted
				pstmtUpdateSeq.setInt(1, listId);
				pstmtUpdateSeq.setInt(2, o.seq);
				
				log.info("Update sequences: " + pstmtUpdateSeq.toString());
				pstmtUpdateSeq.executeUpdate();
				
				String path = "option_" + listId + "_" + o.value;
				// Insert the option
				pstmtInsertOption.setInt(1, listId );
				pstmtInsertOption.setInt(2, o.seq );
				pstmtInsertOption.setString(3, path + ":label" );
				pstmtInsertOption.setString(4, o.value );
				pstmtInsertOption.setString(5, GeneralUtilityMethods.cleanName(o.value, false) );
				pstmtInsertOption.setString(6, gson.toJson(o.cascadeKeyValues));			
				
				log.info("Insert option: " + pstmtInsertOption.toString());
				pstmtInsertOption.executeUpdate();
				
				// Set the labels 
				if (updateLabels) {
					UtilityMethodsEmail.setLabels(sd, sId, path, o.labels, "");
				}
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmtInsertOption != null) {pstmtInsertOption.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Delete options
	 */
	public void deleteOptions(Connection sd, int sId, ArrayList<Option> options, boolean updateLabels) throws SQLException {
		
		PreparedStatement pstmtDelLabels = null;
		String sqlDelLabels = "delete from translation t where t.s_id = ? and " +
				"t.text_id in (select label_id  from option where l_id = ? and ovalue = ?); ";
		
		PreparedStatement pstmt = null;
		String sql = "delete from option " +
				" where l_id = ? " +
				" and ovalue = ?;";	

		PreparedStatement pstmtUpdateSeq = null;
		String sqlUpdateSeq = "update option set seq = seq - 1 where l_id = ? and seq >= ?;";
		
		try {
			pstmtUpdateSeq = sd.prepareStatement(sqlUpdateSeq);
			pstmtDelLabels = sd.prepareStatement(sqlDelLabels);
			pstmt = sd.prepareStatement(sql);
			
			for(Option o : options) {
				
				// Get the list id for this option
				int listId = getListId(sd, sId, o.optionList);
					
				// Delete the option labels
				if(updateLabels) {
					pstmtDelLabels.setInt(1, sId );
					pstmtDelLabels.setInt(2, listId );
					pstmtDelLabels.setString(3, o.value );
					
					log.info("Delete option labels: " + pstmtDelLabels.toString());
					pstmtDelLabels.executeUpdate();
				}
				
				// Delete the option
				pstmt.setInt(1, listId );
				pstmt.setString(2, o.value );
				
				log.info("Delete option: " + pstmt.toString());
				pstmt.executeUpdate();
				
				// Update sequence numbers of options after the option to be inserted
				pstmtUpdateSeq.setInt(1, listId);
				pstmtUpdateSeq.setInt(2, o.seq);
				
				log.info("Update sequences: " + pstmtUpdateSeq.toString());
				pstmtUpdateSeq.executeUpdate();
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtUpdateSeq != null) {pstmtUpdateSeq.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtDelLabels != null) {pstmtDelLabels.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Move options
	 */
	public void moveOptions(Connection sd, int sId, ArrayList<Option> options) throws Exception {
		
		PreparedStatement pstmtMoveWithin = null;
		String sqlMoveWithin = "update option set "
						+ "seq = ? "
					+ "where l_id = ? "
					+ "and ovalue = ? "
					+ "and seq = ?;";
		
		PreparedStatement pstmtMovedBack = null;
		String sqlMovedBack = "update option set seq = seq + 1 where l_id = ? and seq >= ? and seq < ?;";
		
		PreparedStatement pstmtMovedForward = null;
		String sqlMovedForward = "update option set seq = seq - 1 where l_id = ? and seq > ? and seq <= ?;";
		
		try {
			pstmtMoveWithin = sd.prepareStatement(sqlMoveWithin);
			
			for(Option o : options) {
				
				boolean moveWithinList = o.optionList.equals(o.sourceOptionList);
			
				// Get the target list id for this option
				int listId = getListId(sd, sId, o.optionList);

					
				if(moveWithinList) {
					// Update sequence numbers of other options
					if(o.seq > o.sourceSeq) { // Moved forward in list
						
						pstmtMovedForward = sd.prepareStatement(sqlMovedForward);
						pstmtMovedForward.setInt(1,listId);
						pstmtMovedForward.setInt(2, o.sourceSeq);
						pstmtMovedForward.setInt(3, o.seq);
						
						log.info("Moving forward: " + pstmtMovedForward.toString());
						pstmtMovedForward.executeUpdate();
					} else {	// Moved backward in list
						
						pstmtMovedBack = sd.prepareStatement(sqlMovedBack);
						pstmtMovedBack.setInt(1,listId);
						pstmtMovedBack.setInt(2, o.seq);
						pstmtMovedBack.setInt(3, o.sourceSeq);
						
						log.info("Moving back: " + pstmtMovedBack.toString());
						pstmtMovedBack.executeUpdate();
						
					}
					
					// Move the option
					pstmtMoveWithin.setInt(1, o.seq );
					pstmtMoveWithin.setInt(2, listId );
					pstmtMoveWithin.setString(3, o.value);
					pstmtMoveWithin.setInt(4, o.sourceSeq );
					
					log.info("Move option within same list: " + pstmtMoveWithin.toString());
					int count = pstmtMoveWithin.executeUpdate();
					if(count == 0) {
						log.info("Error: Question already modified");
						throw new Exception("Already modified, refresh your view");		// No matching value assume it has already been modified
					}
					

				} else {
					// Insert into the target list
					ArrayList<Option> targetOptions = new ArrayList<Option> ();
					targetOptions.add(o);
					saveOptions(sd, sId, targetOptions, false);
					
					// Remove from the source questions
					ArrayList<Option> sourceOptions = new ArrayList<Option> ();
					o.optionList = o.sourceOptionList;
					sourceOptions.add(o);
					deleteOptions(sd, sId, options, false);
				}
					
			}
			
			
		} catch(SQLException e) {
			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {
			try {if (pstmtMoveWithin != null) {pstmtMoveWithin.close();}} catch (SQLException e) {}
			try {if (pstmtMovedBack != null) {pstmtMovedBack.close();}} catch (SQLException e) {}
			try {if (pstmtMovedForward != null) {pstmtMovedForward.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Update properties for options
	 */
	public void updateOptions(Connection sd, int sId, ArrayList<PropertyChange> properties) throws SQLException {
		
		PreparedStatement pstmtOtherProperties = null;

		PreparedStatement pstmtUpdateValue = null;
		String sqlUpdateValue = "update option set ovalue = ?, label_id = ? "
				+ "where l_id = ? "
				+ "and ovalue = ?";
		
		// If the option value changes then its label id needs to be updated
		PreparedStatement pstmtUpdateLabelId = null;
		String sqlUpdateLabelId = "update translation t set text_id = ? where text_id = ?; ";
		
		try {
			
			for(PropertyChange p : properties) {
				
				String property = p.prop;	
				int listId = getListId(sd, sId, p.optionList);		// Get the list id for this option
				
				if(property.equals("value")) {
					String newPath = "option_" + listId + "_" + p.newVal + ":label";
					String oldPath = "option_" + listId + "_" + p.oldVal + ":label";
					
					pstmtUpdateValue = sd.prepareStatement(sqlUpdateValue);
					pstmtUpdateValue.setString(1, p.newVal);
					pstmtUpdateValue.setString(2, newPath);
					pstmtUpdateValue.setInt(3, listId);
					pstmtUpdateValue.setString(4, p.oldVal);
					
					log.info("Update option value: " + pstmtUpdateValue.toString());
					pstmtUpdateValue.executeUpdate();
					
					// Update the label id
					pstmtUpdateLabelId = sd.prepareStatement(sqlUpdateLabelId);
					pstmtUpdateLabelId.setString(1, newPath);
					pstmtUpdateLabelId.setString(2, oldPath);
					
					log.info("Update option label id: " + pstmtUpdateLabelId.toString());
					pstmtUpdateLabelId.executeUpdate();
					
				} else {
					if(GeneralUtilityMethods.columnType(sd, "option", property) != null) {
					
						String sql = "update option set  " + property + " = ? "
								+ " where l_id = ? "
								+ " and ovalue = ?;";
						
						pstmtOtherProperties = sd.prepareStatement(sql);
							
						// Update the option
						pstmtOtherProperties.setString(1, p.newVal );
						pstmtOtherProperties.setInt(2, listId );
						pstmtOtherProperties.setString(3, p.oldVal );
						
						log.info("Update option: " + pstmtOtherProperties.toString());
						pstmtOtherProperties.executeUpdate();
							
						
					}
				}
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtOtherProperties != null) {pstmtOtherProperties.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateValue != null) {pstmtUpdateValue.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateLabelId != null) {pstmtUpdateLabelId.close();}} catch (SQLException e) {}
		}	
		
	}
	
	/*
	 * Get the id from the list name and survey Id
	 * If the list does not exist then create it
	 */
	private int getListId(Connection sd, int sId, String name) throws SQLException {
		int listId = 0;
		
		PreparedStatement pstmtGetListId = null;
		String sqlGetListId = "select l_id from listname where s_id = ? and name = ?;";

		PreparedStatement pstmtListName = null;
		String sqlListName = "insert into listname (s_id, name) values (?, ?);";
		
		try {
			pstmtGetListId = sd.prepareStatement(sqlGetListId);
			pstmtGetListId.setInt(1, sId);
			pstmtGetListId.setString(2, name);
			
			log.info("SQL: Get list id: " + pstmtGetListId.toString());
			ResultSet rs = pstmtGetListId.executeQuery();
			if(rs.next()) {
				listId = rs.getInt(1);
			} else {	// Create listname
				pstmtListName = sd.prepareStatement(sqlListName, Statement.RETURN_GENERATED_KEYS);
				pstmtListName.setInt(1, sId);
				pstmtListName.setString(2, name);
				
				log.info("SQL: Create list name: " + pstmtListName.toString());
				
				pstmtListName.executeUpdate();
				
				rs = pstmtListName.getGeneratedKeys();
				rs.next();
				listId = rs.getInt(1);
				
			}
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {			
			try {if (pstmtGetListId != null) {pstmtGetListId.close();}} catch (SQLException e) {}
			try {if (pstmtListName != null) {pstmtListName.close();}} catch (SQLException e) {}
		}	
			
		
		return listId;
	}
	
}
