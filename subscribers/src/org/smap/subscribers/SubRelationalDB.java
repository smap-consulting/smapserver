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

package org.smap.subscribers;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.smap.model.IE;
import org.smap.model.SurveyInstance;
import org.smap.model.TableManager;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.SubscriberEvent;
import org.smap.server.exceptions.SQLInsertException;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Document;


public class SubRelationalDB extends Subscriber {

	private static Logger log =
			 Logger.getLogger(Subscriber.class.getName());
	
	private class Keys {
		ArrayList<Integer> duplicateKeys = new ArrayList<Integer>();
		int newKey = 0;
	}

	// Details of survey definitions database
	String dbClassMeta = null;
	String databaseMeta = null;
	String userMeta = null;
	String passwordMeta = null;
	
	// Details for results database
	String dbClass = null;
	String database = null;
	String user = null;
	String password = null;
	
	String gBasePath = null;
	String gFilePath = null;

	/**
	 * @param args
	 */
	public SubRelationalDB() {
		super();

	}
	
	@Override
	public String getDest() {
		return "upload";
		
	}
	
	@Override
	public void upload(SurveyInstance instance, InputStream is, String remoteUser, 
			String server, String device, SubscriberEvent se, String confFilePath, String formStatus,
			String basePath, String filePath, String updateId, int ue_id, Date uploadTime,
			String surveyNotes, String locationTrigger)  {
		
		gBasePath = basePath;
		gFilePath = filePath;

		if(gBasePath == null || gBasePath.equals("/ebs1")) {
			gBasePath = "/ebs1/servers/" + server.toLowerCase();
		}
		formStatus = (formStatus == null) ? "complete" : formStatus;
		
		System.out.println("base path: " + gBasePath);
		// Open the configuration file
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document xmlConf = null;		
		Connection connection = null;
		Survey survey = null;
		try {
				
			// Get the connection details for the database with survey definitions
			db = dbf.newDocumentBuilder();
			xmlConf = db.parse(new File(confFilePath + "/metaDataModel.xml"));
			dbClassMeta = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			databaseMeta = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			userMeta = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			passwordMeta = xmlConf.getElementsByTagName("password").item(0).getTextContent();
			
			// Get the connection details for the target results database
			xmlConf = db.parse(new File(confFilePath + "/" + getSubscriberName() + ".xml"));
			dbClass = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			database = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			user = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			password = xmlConf.getElementsByTagName("password").item(0).getTextContent();
			
			/*
			 * Verify that the survey is valid for the submitting user
			 *  Only do this if the survey id is numeric otherwise it is an old style
			 *  survey and it will be ignored as eventually there will be no surveys identified by name
			 */
			
			String templateName = instance.getTemplateName();

			// Authorisation - Access
		    Class.forName(dbClassMeta);		 
			connection = DriverManager.getConnection(databaseMeta, userMeta, passwordMeta);
			//Authorise a = new Authorise(null, Authorise.ENUM);
			SurveyManager sm = new SurveyManager();
			survey = sm.getSurveyId(connection, templateName);	// Get the survey from the templateName / ident

			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		
			
		} catch (Exception e) {
			e.printStackTrace();
			se.setStatus("error");
			se.setReason("Configuration File:" + e.getMessage());
			return;
		}		

		try {
			
			writeAllTableContent(instance, remoteUser, server, device, 
					formStatus, updateId, survey.id, uploadTime, surveyNotes, locationTrigger, survey.key_policy);
			applyNotifications(ue_id, remoteUser, server, survey.id);
			applyAssignmentStatus(ue_id, remoteUser);
			se.setStatus("success");			
			
		} catch (SQLInsertException e) {
			
			se.setStatus("error");
			se.setReason(e.getMessage());
			
		}
			
		return;
	}
	
	/*
	 * Apply any changes to assignment status
	 */
	private void applyAssignmentStatus(int ue_id, String remoteUser) {
		
		Connection connectionSD = null;
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetUploadEvent = null;
		PreparedStatement pstmtRepeats = null;
		
		String sqlGetUploadEvent = "select ue.assignment_id " +
				" from upload_event ue " +
				" where ue.ue_id = ? and ue.assignment_id is not null;";
		

		String sql = "UPDATE assignments a SET status = 'submitted' " +
				"where a.id = ? " + 
				"and a.assignee IN (SELECT id FROM users u " +
					"where u.ident = ?);";
		
		String sqlRepeats = "UPDATE tasks SET repeat_count = repeat_count + 1 " +
				"where id = (select task_id from assignments where id = ?);";
		
		
		try {
			connectionSD = DriverManager.getConnection(databaseMeta, user, password);
			
			pstmtGetUploadEvent = connectionSD.prepareStatement(sqlGetUploadEvent);
			pstmt = connectionSD.prepareStatement(sql);
			pstmtRepeats = connectionSD.prepareStatement(sqlRepeats);
			pstmtGetUploadEvent.setInt(1, ue_id);
			ResultSet rs = pstmtGetUploadEvent.executeQuery();
			
			if(rs.next()) {
				int assignment_id = rs.getInt(1);
				if(assignment_id > 0) {
					pstmt.setInt(1, assignment_id);
					pstmt.setString(2, remoteUser);
					System.out.println("Updating assignment status: " + pstmt.toString());
					pstmt.executeUpdate();
					
					pstmtRepeats.setInt(1, assignment_id);
					System.out.println("Updating task repeats: " + pstmtRepeats.toString());
					pstmtRepeats.executeUpdate();
				}
				

			}
			
			
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtGetUploadEvent != null) {pstmtGetUploadEvent.close();}} catch (SQLException e) {}
			try {if (pstmtRepeats != null) {pstmtRepeats.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		
		}
	}
	
	/*
	 * Apply notifications
	 */
	private void applyNotifications(int ueId, String remoteUser, String server, int sId) {
		
		PreparedStatement pstmtGetUploadEvent = null;
		PreparedStatement pstmtGetNotifications = null;
		PreparedStatement pstmtUpdateUploadEvent = null;
		PreparedStatement pstmtNotificationLog = null;
		
		Connection sd = null;
		Connection cResults = null;
		
		String ident = null;		// The survey ident
		String instanceId = null;	// The submitted instance identifier
		int pId = 0;				// The project containing the survey
		
		try {
			Class.forName(dbClass);	 
			sd = DriverManager.getConnection(databaseMeta, user, password);
			cResults = DriverManager.getConnection(database, user, password);
		
			/*
			 * Get details from the upload event
			 */
			String sqlGetUploadEvent = "select ue.ident, ue.instanceid, ue.p_id " +
					" from upload_event ue " +
					" where ue.ue_id = ?;";
			pstmtGetUploadEvent = sd.prepareStatement(sqlGetUploadEvent);
			pstmtGetUploadEvent.setInt(1, ueId);
			ResultSet rs = pstmtGetUploadEvent.executeQuery();
			if(rs.next()) {
				ident = rs.getString(1);
				instanceId = rs.getString(2);
				pId = rs.getInt(3);
			
			
				// Apply notifications
				NotificationManager fm = new NotificationManager();
				fm.notifyForSubmission(
						sd, 
						cResults,
						pstmtGetNotifications, 
						pstmtUpdateUploadEvent, 
						pstmtNotificationLog, 
						ueId, 
						remoteUser, 
						"https",
						server,
						gBasePath,
						sId,
						ident,
						instanceId,
						pId);	
				
				// Apply Tasks
				TaskManager tm = new TaskManager();
				tm.updateTasksForSubmission(
						sd,
						cResults,
						sId,
						server,
						instanceId,
						pId
						);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			try {if (pstmtGetUploadEvent != null) {pstmtGetUploadEvent.close();}} catch (SQLException e) {}
			try {if (pstmtGetNotifications != null) {pstmtGetNotifications.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateUploadEvent != null) {pstmtUpdateUploadEvent.close();}} catch (SQLException e) {}
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * Write the submission to the database
	 */
	private void writeAllTableContent(SurveyInstance instance, String remoteUser, 
			String server, String device, String formStatus, String updateId,
			int sId, Date uploadTime, String surveyNotes, String locationTrigger,
			String keyPolicy) throws SQLInsertException {
			
		String response = null;
		Connection cResults = null;
		Connection cMeta = null;
		PreparedStatement pstmtHrk = null;
		PreparedStatement pstmtAddHrk = null;
		
		System.out.println("UploadTime: " + uploadTime.toGMTString());
		try {
		    Class.forName(dbClass);	 
			cResults = DriverManager.getConnection(database, user, password);
			cMeta = DriverManager.getConnection(databaseMeta, user, password);
			
			cResults.setAutoCommit(false);
			//statement = cResults.createStatement();
			IE topElement = instance.getTopElement();
			
			// Make sure the top element matched a form in the template
			if(topElement.getType() == null) {
				String msg = "Error: Top element name " + topElement.getName() + " in survey did not match a form in the template";
				throw new Exception(msg);
			}
			String hrk = GeneralUtilityMethods.getHrk(cMeta, sId);
			boolean hasHrk = (hrk != null);
			Keys keys = writeTableContent(
					topElement, 
					0, 
					instance.getTemplateName(), 
					remoteUser, 
					server, 
					device, 
					instance.getUuid(), 
					formStatus, 
					instance.getVersion(), 
					surveyNotes,
					locationTrigger,
					cResults,
					cMeta,
					sId,
					uploadTime);

			/*
			 * Update existing records
			 */
			String existingKey = null;
			if(keys.duplicateKeys.size() > 0) {
					System.out.println("Dropping duplicate");
			} else {
				// Check to see this submission was set to update an existing record with new data
				
				
				if(updateId != null) {
					System.out.println("Existing unique id:" + updateId);
					existingKey = getKeyFromId(cResults, topElement, updateId);
				} else {		
					existingKey = topElement.getKey(); 	// Old way of checking for updates - deprecate
				}
				
				if(existingKey != null) {
					System.out.println("Existing key:" + existingKey);
					ArrayList<Integer> existingKeys = new ArrayList<Integer>();
					existingKeys.add(Integer.parseInt(existingKey));
					replaceExistingRecord(cResults, 	// Mark the existing record as being replaced
							cMeta, 
							topElement,
							existingKeys , 
							keys.newKey, 
							hasHrk,
							sId);		
				}
			}
			
			/*
			 * Update any Human readable keys if this survey has them
			 */
			String topLevelTable = null;
			if(hasHrk) {
				topLevelTable = GeneralUtilityMethods.getMainResultsTable(cMeta, cResults, sId);
				if(!GeneralUtilityMethods.hasColumn(cResults, topLevelTable, "_hrk")) {
					// This should not be needed as the _hrk column should be in the table if an hrk has been specified for the survey
					log.info("Error:  _hrk being created for table " + topLevelTable + " this column should already be there");
					String sqlAddHrk = "alter table " + topLevelTable + " add column _hrk text;";
					pstmtAddHrk = cResults.prepareStatement(sqlAddHrk);
					pstmtAddHrk.executeUpdate();
				}
				
				String sql = "update " + topLevelTable + " set _hrk = "
						+ GeneralUtilityMethods.convertAllxlsNamesToQuery(hrk, sId, cMeta);
				
				sql += " where _hrk is null;";
				pstmtHrk = cResults.prepareStatement(sql);
				System.out.println("Adding HRK: " + pstmtHrk.toString());
				pstmtHrk.executeUpdate();
			}
			
			/*
			 * Apply the key policy
			 */
			if(hasHrk && existingKey == null && keyPolicy != null) {
				if(keyPolicy.equals("add")) {
					System.out.println("Apply add policy - no action");
				} else if(keyPolicy.equals("merge")) {
					System.out.println("Apply merge policy");
					mergeTableContent(cMeta, cResults, sId, topLevelTable, keys.newKey);
				} else if(keyPolicy.equals("discard")) {
					System.out.println("Apply discard policy");
					discardTableContent(cResults, topLevelTable, keys.newKey);
				}
			}
			
			cResults.commit();
			cResults.setAutoCommit(true);
			
			/*
			 * Clear any entries in linked_forms for this survey - The CSV files will need to be refreshed
			 */
			clearLinkedForms(cMeta, sId);

		} catch (Exception e) {
			if(cResults != null) {
				try {
					
					response = "Error: Rolling back: " + e.getMessage();
					e.printStackTrace();
					System.out.println("        " + response);
					cResults.rollback();
					cResults.setAutoCommit(true);
					throw new SQLInsertException(e.getMessage());
					
				} catch (SQLException ex) {
					
					System.out.println(ex.getMessage());
					throw new SQLInsertException(e.getMessage());
					
				}

			} else {
				
				String mesg = "Error: Connection to the database is null";
				System.out.println("        " + mesg);
				throw new SQLInsertException(mesg);
				
			}
			
		} finally {
			
			if(pstmtHrk != null) try{pstmtHrk.close();}catch(Exception e) {};
			try {
				if (cResults != null) {
					cResults.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
			try {
				if (cMeta != null) {
					cMeta.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		}		
	}

	/*
	 * Method to write the table content
	 */
	private  Keys writeTableContent(
			IE element, 
			int parent_key, 
			String sName, 
			String remoteUser, 
			String server, 
			String device, 
			String uuid, 
			String formStatus, 
			int version,
			String surveyNotes,
			String locationTrigger,
			Connection cRel,
			Connection cMeta,
			int sId,
			Date uploadTime) throws SQLException, Exception {

		Keys keys = new Keys();
		PreparedStatement pstmt = null;
		
		try {
			/*
			 * Write the Instance element to a table if it is a form type
			 * The sub elements of a complex question will be written to their own table
			 *  as well as being handled as a composite/complex question by the parent form
			 */
			if(element.getType() != null && (element.getType().equals("form") 
					|| (element.getQType() != null && element.getQType().equals("geopolygon"))
					|| (element.getQType() != null && element.getQType().equals("geolinestring"))
					)) {
				
				// Write form
				String tableName = element.getTableName();
				List<IE> columns = element.getQuestions();
				String sql = null;	
				
				/*
				 * If this is the top level form then 
				 *   1) create all the tables for this survey if they do not already exist
				 *   2) Check if this survey is a duplicate
				 */
				keys.duplicateKeys = new ArrayList<Integer>();
				TableManager tm = new TableManager();
				if(parent_key == 0) {	// top level survey has a parent key of 0
					boolean tableCreated = tm.createTable(cRel, cMeta, tableName, sName, sId, 0);
					boolean tableChanged = false;
					boolean tablePublished = false;
					keys.duplicateKeys = checkDuplicate(cRel, tableName, uuid);
				
					if(keys.duplicateKeys.size() > 0 && 
							getDuplicatePolicy() == DUPLICATE_DROP) {
						throw new Exception("Duplicate survey: " + uuid);
					}
					// Apply any updates that have been made to the table structure since the last submission
					if(!tableCreated) {
						tableChanged = tm.applyTableChanges(cMeta, cRel, sId);
						
						// Add any previously unpublished columns not in a changeset (Occurs if this is a new survey sharing an existing table)
						tablePublished = tm.addUnpublishedColumns(cMeta, cRel, sId);
						
						if(tableChanged || tablePublished) {
							tm.markPublished(cMeta, sId);		// only mark published if there have been changes made
						}
					}
					
				}
				
				boolean isBad = false;
				boolean complete = true;
				String bad_reason = null;
				if(formStatus != null && (formStatus.equals("incomplete") || formStatus.equals("draft"))) {
					isBad = true;
					bad_reason = "incomplete";
					complete = false;
				}
				
				/*
				 * Write the record
				 */
				if(columns.size() > 0 || parent_key == 0) {
					
					boolean hasAltitude = GeneralUtilityMethods.hasColumn(cRel, tableName, "the_geom_alt");				// Latest meta column added
					boolean hasUploadTime = hasAltitude || GeneralUtilityMethods.hasColumn(cRel, tableName, "_upload_time");		
					boolean hasVersion = hasUploadTime || GeneralUtilityMethods.hasColumn(cRel, tableName, "_version");
					boolean hasSurveyNotes = GeneralUtilityMethods.hasColumn(cRel, tableName, "_survey_notes");
					sql = "INSERT INTO " + tableName + " (parkey";
					if(parent_key == 0) {
						sql += ",_user, _complete";	// Add remote user, _complete automatically (top level table only)
						if(hasUploadTime) {
							sql += ",_upload_time,_s_id";
						}
						if(hasVersion) {
							sql += ",_version";
						}
						if(hasSurveyNotes) {
							sql += ",_survey_notes, _location_trigger";
						}
						if(isBad) {
							sql += ",_bad, _bad_reason";
						}
					}
	
					sql += addSqlColumns(columns, hasAltitude);
					
					
					sql += ") VALUES (?";		// parent key
					if(parent_key == 0) {
						sql += ", ?, ?";		// remote user, complete	
						if(hasUploadTime) {
							sql += ", ?, ?";	// upload time, survey id
						}
						if(hasVersion) {
							sql += ", ?";		// Version
						}
						if(hasSurveyNotes) {
							sql += ", ?, ?";		// Survey Notes and Location Trigger
						}
						if(isBad) {
							sql += ", ?, ?";	// _bad, _bad_reason
						}
					}
					
					sql += addSqlValues(columns, sName, device, server, false, hasAltitude);
					sql += ");";
					
					pstmt = cRel.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					int stmtIndex = 1;
					pstmt.setInt(stmtIndex++, parent_key);
					if(parent_key == 0) {
						pstmt.setString(stmtIndex++, remoteUser);
						pstmt.setBoolean(stmtIndex++, complete);
						if(hasUploadTime) {
							pstmt.setTimestamp(stmtIndex++, new Timestamp(uploadTime.getTime()));
							pstmt.setInt(stmtIndex++, sId);
						}
						if(hasVersion) {
							pstmt.setInt(stmtIndex++, version);
						}
						if(hasSurveyNotes) {
							pstmt.setString(stmtIndex++, surveyNotes);
							pstmt.setString(stmtIndex++, locationTrigger);
						}
						if(isBad) {
							pstmt.setBoolean(stmtIndex++, true);
							pstmt.setString(stmtIndex++, bad_reason);
						}
					}
					
					System.out.println("        SQL statement: " + pstmt.toString());	
					pstmt.executeUpdate();
					
					ResultSet rs = pstmt.getGeneratedKeys();
					if( rs.next()) {
						parent_key = rs.getInt(1);
						keys.newKey = parent_key;
					}
	
					
				}
			}
			
			//Write any child forms
			List<IE> childElements = element.getChildren();
			for(IE child : childElements) {
				writeTableContent(child, parent_key, sName, remoteUser, server, device, 
						uuid, formStatus, version, surveyNotes, locationTrigger, 
						cRel, cMeta, sId, uploadTime);
			}
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
		}
		
		return keys;

	}
	
	/*
	 * Clear entries for linked forms to force reload
	 */
	private void clearLinkedForms(
			Connection cMeta,
			int sId
			) throws SQLException {
		
		String sql = "delete from linked_forms where linked_s_id = ?";
		PreparedStatement pstmt = null;
		
		try {
			pstmt = cMeta.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("Clear entries in linked_forms: " + pstmt.toString());
			pstmt.executeUpdate();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
		}
	}
	
	/*
	 * Method to merge a previous records content into this new record
	 */
	private void mergeTableContent(
			Connection cMeta,
			Connection cRel,
			int sId,
			String table,
			int prikey) throws SQLException, Exception {

		String sqlHrk = "select _hrk from " + table + " where prikey = ?";
		PreparedStatement pstmtHrk = null;
		
		String sqlSource = "select prikey from " + table + " where _hrk = ? "
				+ "and prikey != ? "
				+ "and _bad = 'false' "
				+ "order by prikey desc limit 1";
		PreparedStatement pstmtSource = null;
		
		String sqlCols = "select column_name from information_schema.columns where table_name = ? "
				+ "and column_name not like '\\_%' "
				+ "and column_name != 'prikey' "
				+ "and column_name != 'parkey' "
				+ "and column_name != 'instancename' "
				+ "and column_name != 'instanceid'";
		PreparedStatement pstmtCols = null;
		
		PreparedStatement pstmtGetTarget = null;
		PreparedStatement pstmtUpdateTarget = null;
		
		String sqlCloseSource = "update " + table + " set _bad = 'true', _bad_reason = ? "
				+ "where _hrk = ? "
				+ "and _bad = 'false' "
				+ "and prikey != ?";
		PreparedStatement pstmtCloseSource = null;
			
		String sqlChildTables = "select table_name from form where parentform = (select f_id from form where parentform = 0 and s_id = ?)";
		PreparedStatement pstmtChildTables = null;
		
		PreparedStatement pstmtChildUpdate = null;
		
		try {
	
			// Get the HRK that identifies duplicates
			String hrk = null;
			pstmtHrk = cRel.prepareStatement(sqlHrk);
			pstmtHrk.setInt(1, prikey);
			ResultSet rs = pstmtHrk.executeQuery();
			if(rs.next()) {
				hrk = rs.getString(1);
				System.out.println("===== Hrk: " + hrk);
			}
			
			// Get the prikey of the source table
			int sourceKey = 0;
			pstmtSource = cRel.prepareStatement(sqlSource);
			pstmtSource.setString(1, hrk);
			pstmtSource.setInt(2, prikey);
			rs = pstmtSource.executeQuery();
			if(rs.next()) {
				sourceKey = rs.getInt(1);
				System.out.println("===== Source Prikey: " + sourceKey);
			
				// Get the columns to merge
				pstmtCols = cRel.prepareStatement(sqlCols);
				pstmtCols.setString(1, table);
				ResultSet rsCols = pstmtCols.executeQuery();
				while(rsCols.next()) {
					String col = rsCols.getString(1);
					System.out.println("    col: " + col);
					
					String sqlGetTarget = "select " + col + " from " + table + " where prikey = ?";
					
					if(pstmtGetTarget != null) try{pstmtGetTarget.close();}catch(Exception e) {}
					pstmtGetTarget = cRel.prepareStatement(sqlGetTarget);
					pstmtGetTarget.setInt(1, prikey);
					ResultSet rsGetTarget = pstmtGetTarget.executeQuery();
					if(rsGetTarget.next()) {
						String val = rsGetTarget.getString(1);
						System.out.println("         " + val);
						
						if( val == null || val.trim().length() == 0) {
							System.out.println("        --------- updating");
							
							String sqlUpdateTarget = "update " + table + " set " + col + " = (select " + col + " from " + table + " where prikey = ?) "
									+ "where prikey = ?";
							if(pstmtUpdateTarget != null) try{pstmtUpdateTarget.close();}catch(Exception e) {}
							pstmtUpdateTarget = cRel.prepareStatement(sqlUpdateTarget);
							pstmtUpdateTarget.setInt(1, sourceKey);
							pstmtUpdateTarget.setInt(2, prikey);
							System.out.println(("Merging col: " + pstmtUpdateTarget.toString()));
							pstmtUpdateTarget.executeUpdate();
						}
					}
					
				}
				
				// Add the child records from the merged survey to the new survey (TODO) possibly these should be replicated
				pstmtChildTables = cMeta.prepareStatement(sqlChildTables);
				pstmtChildTables.setInt(1,  sId);
				ResultSet rsChildTables = pstmtChildTables.executeQuery();
				
				
				
				while(rsChildTables.next()) {
					System.out.println("Child Table: " + rsChildTables.getString(1));
					
					String sqlChildUpdate = "update " + rsChildTables.getString(1) + " set parkey = ? where parkey = ?;";
					pstmtChildUpdate = cRel.prepareStatement(sqlChildUpdate);
					
					pstmtChildUpdate.setInt(1, prikey);
					pstmtChildUpdate.setInt(2, sourceKey);
					log.info("Updating parent keys: " + pstmtChildUpdate.toString());
					pstmtChildUpdate.executeUpdate();
				}
				
			}
			
			pstmtCloseSource = cRel.prepareStatement(sqlCloseSource);
			pstmtCloseSource.setString(1, "Merged with " + prikey);
			pstmtCloseSource.setString(2, hrk);
			pstmtCloseSource.setInt(3, prikey);
			System.out.println(("Closing Source: " + pstmtCloseSource.toString()));
			pstmtCloseSource.executeUpdate();

			
		
		} finally {
			if(pstmtCols != null) try{pstmtCols.close();}catch(Exception e) {}
			if(pstmtHrk != null) try{pstmtHrk.close();}catch(Exception e) {}
			if(pstmtSource != null) try{pstmtSource.close();}catch(Exception e) {}
			if(pstmtGetTarget != null) try{pstmtGetTarget.close();}catch(Exception e) {}
			if(pstmtUpdateTarget != null) try{pstmtUpdateTarget.close();}catch(Exception e) {}
			if(pstmtCloseSource != null) try{pstmtCloseSource.close();}catch(Exception e) {}
			if(pstmtChildTables != null) try{pstmtChildTables.close();}catch(Exception e) {}
			if(pstmtChildUpdate != null) try{pstmtChildUpdate.close();}catch(Exception e) {}
		}

	}
	
	/*
	 * Method to merge a previous records content into this new record
	 */
	private void discardTableContent(
			Connection cRel,
			String table,
			int prikey) throws SQLException, Exception {

		String sqlHrk = "select _hrk from " + table + " where prikey = ?";
		PreparedStatement pstmtHrk = null;
		
		String sqlSource = "select prikey from " + table + " where _hrk = ? "
				+ "and prikey != ? "
				+ "and _bad = 'false' "
				+ "order by prikey desc limit 1";
		PreparedStatement pstmtSource = null;
		
		String sqlCloseNew = "update " + table + " set _bad = 'true', _bad_reason = ? "
				+ "where prikey = ? ";
		PreparedStatement pstmtCloseNew = null;
			
		try {
			boolean exists = false;
			
			// Get the HRK that identifies duplicates
			String hrk = null;
			pstmtHrk = cRel.prepareStatement(sqlHrk);
			pstmtHrk.setInt(1, prikey);
			ResultSet rs = pstmtHrk.executeQuery();
			if(rs.next()) {
				hrk = rs.getString(1);
			}
			
			// Get the prikey of existing data
			int sourceKey = 0;
			pstmtSource = cRel.prepareStatement(sqlSource);
			pstmtSource.setString(1, hrk);
			pstmtSource.setInt(2, prikey);
			rs = pstmtSource.executeQuery();
			if(rs.next()) {
				sourceKey = rs.getInt(1);
				exists = true;
				
			}
			
			if(exists) {
				pstmtCloseNew = cRel.prepareStatement(sqlCloseNew);
				pstmtCloseNew.setString(1, "Discarded in favour of " + sourceKey);
				pstmtCloseNew.setInt(2, prikey);
				System.out.println(("Discarding new: " + pstmtCloseNew.toString()));
				pstmtCloseNew.executeUpdate();
			}
			
		
		} finally {
			if(pstmtHrk != null) try{pstmtHrk.close();}catch(Exception e) {}
			if(pstmtSource != null) try{pstmtSource.close();}catch(Exception e) {}
			if(pstmtCloseNew != null) try{pstmtCloseNew.close();}catch(Exception e) {}
		}

	}
	
	
	/*
	 * Method to replace an existing record
	 */
	private  void replaceExistingRecord(Connection cRel, Connection cMeta, 
			IE element, 
			ArrayList<Integer> existingKeys, 
			long newKey,
			boolean hasHrk,
			int sId) throws SQLException, Exception {

		/*
		 * Set the record as bad with the reason being that it has been replaced
		 */		
		String tableName = element.getTableName();
		PreparedStatement pstmt = null;
		PreparedStatement pstmtAddHrk = null;
		PreparedStatement pstmtHrk = null;
		
		try {
			// Check that the new record is not bad
			String sql = "select _bad from " + tableName + " where prikey = ?;";
			boolean isGood = false;
			pstmt = cRel.prepareStatement(sql);
			pstmt = cRel.prepareStatement(sql);
			pstmt.setLong(1, newKey);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				isGood = !rs.getBoolean(1);
			}
			pstmt.close();
			
			if(isGood) {
				// Get the form id for this table
				String bad_reason = "Replaced by " + newKey;
				int f_id;
				sql = "select f.f_id from form f where f.table_name = ? and f.s_id = ?;";
	
				pstmt = cMeta.prepareStatement(sql);
				pstmt.setString(1, tableName);
				pstmt.setInt(2, sId);
				rs = pstmt.executeQuery();
				if(rs.next()) {
					f_id = rs.getInt(1);
					
					// Mark the records replaced
					for(int i = 0; i < existingKeys.size(); i++) {	
						int dupKey = existingKeys.get(i);
						org.smap.sdal.Utilities.UtilityMethodsEmail.markRecord(cRel, cMeta, tableName, 
								true, bad_reason, dupKey, sId, f_id, true, false);
						
						// Set the hrk of the new record to the hrk of the old record
						// This can only be done for one old record, possibly there is never more than 1
						if(hasHrk && i == 0) {
							if(!GeneralUtilityMethods.hasColumn(cRel, tableName, "_hrk")) {
								// This should not be needed as the _hrk column should be in the table if an hrk has been specified for the survey
								log.info("Error:  _hrk being created for table " + tableName + " this column should already be there");
								String sqlAddHrk = "alter table " + tableName + " add column _hrk text;";
								pstmtAddHrk = cRel.prepareStatement(sqlAddHrk);
								pstmtAddHrk.executeUpdate();
							}
							String sqlHrk = "update " + tableName + " set _hrk = (select t2._hrk from "
									+ tableName
									+ " t2 where t2.prikey = ?) "
									+ "where prikey = ?;";
							pstmtHrk = cRel.prepareStatement(sqlHrk);
							pstmtHrk.setInt(1, dupKey);
							pstmtHrk.setLong(2, newKey);
							log.info("Updating hrk with original value: " + pstmtHrk.toString());
							pstmtHrk.executeUpdate();
						}
					}
				}	
	
			}
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e) {};
			if(pstmtHrk != null) try{pstmtHrk.close();}catch(Exception e) {};
		}
	
	}

	/*
	 * Get the primary key from the unique instance id
	 */
	private  String getKeyFromId(Connection connection, IE element, String instanceId) throws SQLException {

		String key = null;
		/*
		 * Set the record as bad with the reason being that it has been replaced
		 */		
		String tableName = element.getTableName();
		
		String sql = "select prikey from " + tableName + " where instanceid = ?;";
		PreparedStatement pstmt = connection.prepareStatement(sql);
		pstmt = connection.prepareStatement(sql);
		pstmt.setString(1, instanceId);
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			key = rs.getString(1);
		}
		pstmt.close();
		
		return key;
	
	}

	/*
	 * Generate the sql for the column names
	 */
	String addSqlColumns(List<IE> columns, boolean hasAltitude) {
		String sql = "";
		
		for(IE col : columns) {
			String colType = col.getQType();
			if(colType.equals("select")) {
				List<IE> options = col.getChildren();
				UtilityMethods.sortElements(options);
				HashMap<String, String> uniqueColumns = new HashMap<String, String> (); 
				for(IE option : options) {
					if(uniqueColumns.get(option.getColumnName()) == null) {
						uniqueColumns.put(option.getColumnName(), option.getColumnName());
						sql += "," + option.getColumnName();
					}		
				}
			} else if(colType.equals("geopolygon") || colType.equals("geolinestring") || colType.equals("geopoint")
					|| colType.equals("geoshape") || colType.equals("geotrace")) {
				// All geospatial columns have the name "the_geom"
				sql += ",the_geom";
				if(colType.equals("geopoint") && hasAltitude) {
					// Geopoint also has altitude and accuracy
					sql += ",the_geom_alt, the_geom_acc";
				}
			} else if(colType.equals("begin group")) {
				// Non repeating group, process these child columns at the same level as the parent
				sql += addSqlColumns(col.getQuestions(), hasAltitude);
			} else {
				String colName = col.getColumnName();
				sql += "," + colName;
			}				
		}
		
		return sql;
	}
	
	String addSqlValues(List<IE> columns, String sName, String device, String server, boolean phoneOnly, boolean hasAltitude) {
		String sql = "";
		for(IE col : columns) {
			boolean colPhoneOnly = phoneOnly || col.isPhoneOnly();	// Set phone only if the group is phone only or just this column
			String colType = col.getQType();
			
			if(colType.equals("select")) {
				List<IE> options = col.getChildren();
				UtilityMethods.sortElements(options);
				HashMap<String, String> uniqueColumns = new HashMap<String, String> (); 
				for(IE option : options) {
					if(uniqueColumns.get(option.getColumnName()) == null) {
						uniqueColumns.put(option.getColumnName(), option.getColumnName());
						sql += "," + (colPhoneOnly ? "" : option.getValue());
					}			
				}
			} else if(colType.equals("begin group")) {
				// Non repeating group, process these child columns at the same level as the parent
				sql += addSqlValues(col.getQuestions(), sName, device, server, colPhoneOnly, hasAltitude);
			} else {
				sql += "," + getDbString(col, sName, device, server, colPhoneOnly, hasAltitude);
			}				
		}
		return sql;
	}
	
	/*
	 * Format the value into a string appropriate to its type
	 */
	String getDbString(IE col, String surveyName, String device, String server, boolean phoneOnly, boolean hasAltitude) {
		
		String qType = col.getQType();
		String value = col.getValue();	
		String colName = col.getName();
		
		/*
		 * Debug utf-8 print
		 *
		try {
			new PrintStream(System.out, true, "UTF-8").println("value: " + value);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		
		// If the deviceId is not in the form results add it from the form meta data
		if(colName != null && colName.equals("_device")) {
			if(value == null || value.trim().length() == 0) {
				value = device;
			}
		}
		
		if(phoneOnly) {
			if(qType.equals("string")) {
				value = "'xxxx'";			// Provide feedback to user that this value was withheld
			} else {
				value = "null";				// For non string types will just set to null
			}
		} else {
		
			if(value != null) {				
	
				value = value.replace("'", "''").trim();
						
				if(qType.equals("string") || qType.equals("select1") || qType.equals("barcode") || qType.equals("acknowledge")) {
					value = "'" + value + "'";
					
				} else if(qType.equals("int") || qType.equals("decimal")) {
					if(value.length() == 0) {
						value = "null";
					}
					
				} else if(qType.equals("date") || qType.equals("dateTime") || qType.equals("time") ) {
					if(value.length() > 0) {
						value = "'" + col.getValue() + "'";
					} else {
						value = "null";
					}
					
				} else if(qType.equals("geopoint")) {
					// Geo point parameters are separated by a space and in the order Y X Altitude Accuracy
					// To store as a Point in the db this order needs to be reversed
					System.out.println("Location: " + value);
					String params[] = value.split(" ");
					if(params.length > 1) {
						//value = "ST_GeomFromText('POINT(" + params[1] + " " + params[0] + ")', 4326)";
						value = "ST_SetSRID(ST_MakePoint(" 
								+ String.valueOf(Double.parseDouble(params[1])) + ","
								+ String.valueOf(Double.parseDouble(params[0]))
								+ "), 4326)";
						
						// Add altitude and accuracy
						if(hasAltitude) {
							if(params.length > 3) {
								value += "," + String.valueOf(Double.parseDouble(params[2])) + "," 
										+ String.valueOf(Double.parseDouble(params[3]));
							} else {
								value += ",null, null";
							}
						}

					} else {
						System.out.println("Error: Invalid geometry point detected: " + value);
						if(hasAltitude) {
							value = "null, null, null";
						} else {
							value = "null";
						}
					}
					
					
					
					
				} else if(qType.equals("audio") || qType.equals("video") || qType.equals("image")) {
				
					System.out.println("Processing media. Value: " + value);
					if(value == null || value.length() == 0) {
						value = "null";
					} else {
						/*
						 * If this is a new file then rename the attachment to use a UUID
						 * Where this is an update to an existing survey and the file has not been re-submitted then 
						 * leave its value unchanged
						 */
						String srcName = value;
						
						try {
							new PrintStream(System.out, true, "UTF-8").println("Creating file: " + srcName);
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						File srcXmlFile = new File(gFilePath);
						File srcXmlDirFile = srcXmlFile.getParentFile();
						File srcPathFile = new File(srcXmlDirFile.getAbsolutePath() + "/" + srcName);
						
						if(srcPathFile.exists()) {
							value = "'" + GeneralUtilityMethods.createAttachments(
									srcName, 
									srcPathFile, 
									gBasePath, 
									surveyName) + "'";
	
						} else {
							try {
								new PrintStream(System.out, true, "UTF-8").println("Source file does not exist: " + srcPathFile.getAbsolutePath());
							} catch (UnsupportedEncodingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							value = "'" + value + "'";
						}
						
					}
				} else if(qType.equals("geoshape") || qType.equals("geotrace")) {
					/*
					 * ODK polygon / linestring
					 * The coordinates are in a String separated by ;
					 * Each coordinate consists of space separated lat lon height accuracy
					 *   Actually I'm not sure about the last two but they will be ignored anyway
					 *   To store as a Point in the db this order needs to be reversed to (lon lat)
					 */
					int min_points = 3;
					StringBuffer ptString = null;
					if(qType.equals("geotrace")) {
						min_points = 2;
					}
					
					String coords[] = value.split(";");
					if(coords.length >= min_points) {
						if(qType.equals("geoshape")) {
								ptString = new StringBuffer("ST_GeomFromText('POLYGON((");
						} else {
							ptString = new StringBuffer("ST_GeomFromText('LINESTRING(");
						}
						for(int i = 0; i < coords.length; i++) {
							String [] points = coords[i].split(" ");
							if(points.length > 1) {
								if(i > 0) {
									ptString.append(",");
								}
								ptString.append(points[1]);
								ptString.append(" ");
								ptString.append(points[0]);
							} else {
								System.out.println("Error: " + qType + " Badly formed point." + coords[i]);
							}
						}
						if(qType.equals("geoshape")) {
							ptString.append("))', 4326)");
						} else {
							ptString.append(")', 4326)");
						}
						value = ptString.toString();
						
					} else {
						value = "null";
						System.out.println("Error: " + qType + " Insufficient points for " + qType + ": " + coords.length);
					}
					System.out.println("Value for geoshape: " + value);
					
				} else if(qType.equals("geopolygon") || qType.equals("geolinestring")) {
					// Complex types
					IE firstPoint = null;
					String ptString = "";
					List<IE> points = col.getChildren();
					int number_points = points.size();
					if(number_points < 3 && qType.equals("geopolygon")) {
						value = "null";
						System.out.println("Error: Insufficient points for polygon." + number_points);
					} else if(number_points < 2 && qType.equals("geolinestring")) {
						value = "null";
						System.out.println("Error: Insufficient points for line." + number_points);
					} else {
						for(IE point : points) {
							
							String params[] = point.getValue().split(" ");
							if(params.length > 1) {
								if(firstPoint == null) {
									firstPoint = point;		// Used to loop back for a polygon
								} else {
									ptString += ",";
								}
								ptString += params[1] + " " + params[0];
							}
						}
						if(ptString.length() > 0) {
							if(qType.equals("geopolygon")) {
								if(firstPoint != null) {
									String params[] = firstPoint.getValue().split(" ");
									if(params.length > 1) {
										ptString += "," + params[1] + " " + params[0];
									}
								}
								value = "ST_GeomFromText('POLYGON((" + ptString + "))', 4326)";
							} else if(qType.equals("geolinestring")) {
								value = "ST_GeomFromText('LINESTRING(" + ptString + ")', 4326)";
							}
						} else {
							value = "null";
						}
					}
					
				}
			} else {
				value = "null";
			}
		}
		
		return value;
	}
	
	/*
	 * Check for duplicates specified using a column named instanceid or _instanceid
	 * Return true if this instance has already been uploaded
	 */
	private ArrayList<Integer> checkDuplicate(Connection cResults, String tableName, String uuid) {
		
		ArrayList<Integer> duplicateKeys = new ArrayList<Integer> ();
		PreparedStatement pstmt = null;
		uuid = uuid.replace("'", "''");	// Escape apostrophes
		System.out.println("checkDuplicates: " + tableName + " : " + uuid);
		
		if(uuid != null && uuid.trim().length() > 0) {
			try {
	
				String colTest1 = "select column_name from information_schema.columns " +
						"where table_name = '" + tableName + "' and column_name = '_instanceid'";
				String sql1 = "select prikey from " + tableName + " where _instanceid = '" + uuid + "' " +
						"order by prikey asc;";
				
				pstmt = cResults.prepareStatement(colTest1);
				// Check for duplicates with the old _instanceid
				ResultSet res = pstmt.executeQuery();
				if(res.next()) {
					// Has _instanceid
					System.out.println("Has _instanceid");
					try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
					pstmt = cResults.prepareStatement(sql1);
					res = pstmt.executeQuery();
					while(res.next()) {
						duplicateKeys.add(res.getInt(1));
					}
				}
				
	
				String colTest2 = "select column_name from information_schema.columns " +
						"where table_name = '" + tableName + "' and column_name = 'instanceid'";
				String sql2 = "select prikey from " + tableName + " where instanceid = '" + uuid + "' " +
						"order by prikey asc;";
				
				// Check for duplicates with the new instanceid
				try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
				pstmt = cResults.prepareStatement(colTest2);
				res = pstmt.executeQuery();
				if(res.next()) {
					// Has instanceid
					try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
					pstmt = cResults.prepareStatement(sql2);
					res = pstmt.executeQuery();
					while(res.next()) {
						duplicateKeys.add(res.getInt(1));
					}
				}
	
				
				if(duplicateKeys.size() > 0) {
					System.out.println("Submission has " + duplicateKeys.size() + " duplicates for uuid: " + uuid);
				} 
				
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			} finally {
				try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			}
		}
		
		return duplicateKeys;
	}
	

}
