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
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.smap.model.IE;
import org.smap.model.SurveyInstance;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.entities.SubscriberEvent;
import org.smap.server.exceptions.SQLInsertException;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Document;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class SubRelationalDB extends Subscriber {

	/*
	 * Class to store information about geometry columns
	 * Needed to support two phase creation of geometry columns in tables
	 */
	private class GeometryColumn {
		public String tableName = null;
		public String columnName = null;
		public String srid = "4326";
		public String type = null;
		public String dimension = "2";
		
		public GeometryColumn(String tableName, String columnName, String type) {
			this.tableName = tableName;
			this.columnName = columnName;
			this.type = type;
		}
	}
	
	private class UpdateResults {
		public String sql;
		public boolean hasColumns;
	}
	
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
			String basePath, String filePath, String updateId, int ue_id)  {
		
		gBasePath = basePath;
		gFilePath = filePath;

		if(gBasePath == null || gBasePath.equals("/ebs1")) {
			gBasePath = "/ebs1/servers/" + server.toLowerCase();
		}
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
			Authorise a = new Authorise(null, Authorise.ENUM);
			SurveyManager sm = new SurveyManager();
			survey = sm.getSurveyId(connection, templateName);	// Get the survey from the templateName / ident
			boolean isAuthorised = a.isValidSurvey(connection, remoteUser, survey.id, false );
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
			if(!isAuthorised) {
				throw new Exception("The user " + remoteUser + 
						" was not allowed to submit this survey(" + templateName + ")");
			}	
			
		} catch (Exception e) {
			e.printStackTrace();
			se.setStatus("error");
			se.setReason("Configuration File:" + e.getMessage());
			return;
		}		

		try {
			
			writeAllTableContent(instance, remoteUser, server, device, formStatus, updateId, survey.id);
			applyNotifications(ue_id, remoteUser, server);
			se.setStatus("success");			
			
		} catch (SQLInsertException e) {
			
			se.setStatus("error");
			se.setReason(e.getMessage());
			
		}
			
		return;
	}
	
	/*
	 * Apply notifications
	 */
	private void applyNotifications(int ue_id, String remoteUser, String server) {
		
		PreparedStatement pstmtGetUploadEvent = null;
		PreparedStatement pstmtGetNotifications = null;
		PreparedStatement pstmtUpdateUploadEvent = null;
		PreparedStatement pstmtNotificationLog = null;
		
		Connection connectionSD = null;
		
		try {
			Class.forName(dbClass);	 
			connectionSD = DriverManager.getConnection(databaseMeta, user, password);
		
			NotificationManager fm = new NotificationManager();
			fm.notifyForSubmission(connectionSD, 
					pstmtGetUploadEvent, 
					pstmtGetNotifications, 
					pstmtUpdateUploadEvent, 
					pstmtNotificationLog, 
					ue_id, remoteUser, server);	
			
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
	 * Write the submission to the database
	 */
	private void writeAllTableContent(SurveyInstance instance, String remoteUser, 
			String server, String device, String formStatus, String updateId,
			int sId) throws SQLInsertException {
			
		String response = null;
		Connection cResults = null;
		Connection cMeta = null;
		Statement statement = null;
		
		try {
		    Class.forName(dbClass);	 
			cResults = DriverManager.getConnection(database, user, password);
			cMeta = DriverManager.getConnection(databaseMeta, user, password);
			statement = cResults.createStatement();
			
			applyTableChanges(cMeta, sId);			// Apply any updates that have been made to the table structure since the last submission
			
			cResults.setAutoCommit(false);
			IE topElement = instance.getTopElement();
			
			// Make sure the top element matched a form in the template
			if(topElement.getType() == null) {
				String msg = "Error: Top element name " + topElement.getName() + " in survey did not match a form in the template";
				throw new Exception(msg);
			}
			Keys keys = writeTableContent(topElement, statement, 0, instance.getTemplateName(), 
					remoteUser, server, device, instance.getUuid(), formStatus, instance.getVersion(), cResults);

			// 
			if(keys.duplicateKeys.size() > 0) {
				if(getDuplicatePolicy() == DUPLICATE_REPLACE) {
					System.out.println("Replacing existing record with " + keys.newKey);
					replaceExistingRecord(cResults, cMeta, topElement, keys.duplicateKeys, keys.newKey);		// Mark the existing record as being replaced
				} else {
					System.out.println("Dropping duplicate");
				}
			} else {
				// Check to see this submission was set to update an existing record with new data
				System.out.println("Check for existing key");
				
				String existingKey = null;
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
					replaceExistingRecord(cResults, cMeta, topElement,existingKeys , keys.newKey);		// Mark the existing record as being replaced
				}
				
			}
			cResults.commit();
			cResults.setAutoCommit(true);

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
			try { if (statement != null) { statement.close(); } } catch (Exception e) {}
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
	private  Keys writeTableContent(IE element, Statement statement, int parent_key, String sName, 
			String remoteUser, String server, String device, 
			String uuid, String formStatus, 
			int version,
			Connection cRel) throws SQLException, Exception {

		Keys keys = new Keys();
		
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
			 * If this is the top level survey then 
			 *   1) create all the tables for this survey if they do not already exist
			 *   2) Check if this survey is a duplicate
			 */
			keys.duplicateKeys = new ArrayList<Integer>();
			if(parent_key == 0) {	// top level survey has a parent key of 0
				createTable(statement, tableName, sName);
				keys.duplicateKeys = checkDuplicate(statement, tableName, uuid);
				if(keys.duplicateKeys.size() > 0 && getDuplicatePolicy() == DUPLICATE_DROP) {
					throw new Exception("Duplicate survey: " + uuid);
				}
			}
			
			boolean isBad = false;
			String bad_reason = null;
			if(formStatus != null && formStatus.equals("incomplete")) {
				isBad = true;
				bad_reason = "incomplete";
			}
			
			/*
			 * Write the record
			 */
			if(columns.size() > 0) {
				
				boolean hasVersion = hasVersion(cRel, tableName);
				sql = "INSERT INTO " + tableName + " (parkey";
				if(parent_key == 0) {
					sql += ",_user";	// Add remote user automatically (top level table only)
					if(hasVersion) {
						sql += ",_version";
					}
					if(isBad) {
						sql += ",_bad, _bad_reason";
					}
				}

				sql += addSqlColumns(columns);
				
				sql += ") VALUES (" + parent_key;
				if(parent_key == 0) {
					sql += ",'" + remoteUser + "'";	
					if(hasVersion) {
						sql += "," + version;
					}
					if(isBad) {
						sql += ",'true','" + bad_reason + "'";
					}
				}
				sql += addSqlValues(columns, sName, device, server);
				sql += ");";
				
				System.out.println("        SQL statement: " + sql);
				
				statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
				ResultSet rs = statement.getGeneratedKeys();
				if( rs.next()) {
					parent_key = rs.getInt(1);
					keys.newKey = parent_key;
				}
				
			}
		}
		
		//Write any child forms
		List<IE> childElements = element.getChildren();
		for(IE child : childElements) {
			writeTableContent(child, statement, parent_key, sName, remoteUser, server, device, 
					uuid, formStatus, version, cRel);
		}
		
		return keys;

	}
	
	/*
	 * Method to check for presence of a version column
	 */
	boolean hasVersion(Connection cRel, String tablename)  {
		
		boolean hasVersion = false;
		
		String sql = "select column_name " +
					"from information_schema.columns " +
					"where table_name = ? and column_name = '_version';";
		
		System.out.println("Sql: " + sql + " : " + tablename);
		try {
			PreparedStatement pstmt = cRel.prepareStatement(sql);
			pstmt = cRel.prepareStatement(sql);
			pstmt.setString(1, tablename);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				hasVersion = true;
			}
			pstmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return hasVersion;
	}
	/*
	 * Method to replace an existing record
	 */
	private  void replaceExistingRecord(Connection cRel, Connection cMeta, IE element, ArrayList<Integer> existingKeys, long newKey) throws SQLException, Exception {

		/*
		 * Set the record as bad with the reason being that it has been replaced
		 */		
		String tableName = element.getTableName();
		
		// Check that the new record is not bad
		String sql = "select _bad from " + tableName + " where prikey = ?;";
		boolean isGood = false;
		PreparedStatement pstmt = cRel.prepareStatement(sql);
		pstmt = cRel.prepareStatement(sql);
		pstmt.setLong(1, newKey);
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			isGood = !rs.getBoolean(1);
		}
		pstmt.close();
		
		if(isGood) {
			// Get the survey id and form id for this table
			String bad_reason = "Replaced by " + newKey;
			int s_id;
			int f_id;
			sql = "select f.s_id, f.f_id from form f where f.table_name = ?;";

			pstmt = cMeta.prepareStatement(sql);
			pstmt.setString(1, tableName);
			rs = pstmt.executeQuery();
			if(rs.next()) {
				s_id = rs.getInt(1);
				f_id = rs.getInt(2);
				
				// Mark the records replaced
				for(int i = 0; i < existingKeys.size(); i++) {	
					int dupKey = existingKeys.get(i);
					org.smap.sdal.Utilities.UtilityMethods.markRecord(cRel, cMeta, tableName, true, bad_reason, dupKey, s_id, f_id);
				}
			}
			pstmt.close();		

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
		
		// Check that the new record is not bad
		String sql = "select _bad, prikey from " + tableName + " where instanceid = ?;";
		boolean isGood = false;
		PreparedStatement pstmt = connection.prepareStatement(sql);
		pstmt = connection.prepareStatement(sql);
		pstmt.setString(1, instanceId);
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			isGood = !rs.getBoolean(1);
			key = rs.getString(2);
		}
		pstmt.close();
		
		return key;
	
	}

	/*
	 * Generate the sql for the column names
	 */
	String addSqlColumns(List<IE> columns) {
		String sql = "";
		
		for(IE col : columns) {
			String colType = col.getQType();
			if(colType.equals("select")) {
				List<IE> options = col.getChildren();
				UtilityMethods.sortElements(options);
				for(IE option : options) {
					sql += "," + UtilityMethods.cleanName(option.getName());
				}
			} else if(colType.equals("geopolygon") || colType.equals("geolinestring") || colType.equals("geopoint")
					|| colType.equals("geoshape") || colType.equals("geotrace")) {
				// All geospatial columns have the name "the_geom"
				sql += "," + "the_geom";
			} else if(colType.equals("begin group")) {
				// Non repeating group, process these child columns at the same level as the parent
				sql += addSqlColumns(col.getQuestions());
			} else {
				String colName = UtilityMethods.cleanName(col.getName());
				sql += "," + colName;
			}				
		}
		
		return sql;
	}
	
	String addSqlValues(List<IE> columns, String sName, String device, String server) {
		String sql = "";
		for(IE col : columns) {
			String colType = col.getQType();
			if(colType.equals("select")) {
				List<IE> options = col.getChildren();
				UtilityMethods.sortElements(options);
				for(IE option : options) {
					sql += "," + option.getValue();
				}
			} else if(colType.equals("begin group")) {
				// Non repeating group, process these child columns at the same level as the parent
				sql += addSqlValues(col.getQuestions(), sName, device, server);
			} else {
				sql += "," + getDbString(col, sName, device, server);
			}				
		}
		return sql;
	}
	
	/*
	 * Format the value into a string appropriate to its type
	 */
	String getDbString(IE col, String surveyName, String device, String server) {
		
		String qType = col.getQType();
		String value = col.getValue();	// Escape quotes and trim
		if(value != null) {
			value = col.getValue().replace("'", "''").trim();	// Escape quotes and trim

			if(qType.equals("string") || qType.equals("select1") || qType.equals("barcode")) {
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
				// Geo point parameters are separated by a space and in the order Y X
				// To store as a Point in the db this order needs to be reversed
				String params[] = value.split(" ");
				if(params.length > 1) {
					value = "ST_GeomFromText('POINT(" + params[1] + " " + params[0] + ")', 4326)";
				} else {
					System.out.println("Error: Invalid geometry point detected: " + value);
					value = "null";
				}
				
			} else if(qType.equals("audio") || qType.equals("video") || qType.equals("image")) {
			
				if(value == null || value.length() == 0) {
					value = "null";
				} else {
					/*
					 * If this is a new file then rename the attachment to use a UUID
					 * Where this is an update to an existing survey and the file has not been re-submitted then 
					 * leave its value unchanged
					 */
					String srcName = value;
					File srcXmlFile = new File(gFilePath);
					File srcXmlDirFile = srcXmlFile.getParentFile();
					File srcPathFile = new File(srcXmlDirFile.getAbsolutePath() + "/" + srcName);
					
					if(srcPathFile.exists()) {
					
						String srcExt = "";
						int idx = srcName.lastIndexOf('.');
						if(idx > 0) {
							srcExt = srcName.substring(idx+1);
						}
						String dstName = String.valueOf(UUID.randomUUID());
						String dstDir = gBasePath + "/attachments/" + surveyName;
						String dstThumbsPath = gBasePath + "/attachments/" + surveyName + "/thumbs";
						String dstFlvPath = gBasePath + "/attachments/" + surveyName + "/flv";
						File dstPathFile = new File(dstDir + "/" + dstName + "." + srcExt);
						File dstDirFile = new File(dstDir);
						File dstThumbsFile = new File(dstThumbsPath);
						File dstFlvFile = new File(dstFlvPath);
	
						String contentType = org.smap.sdal.Utilities.UtilityMethods.getContentType(srcName);
		
						try {
							System.out.println("Processing attachment: " + srcPathFile.getAbsolutePath() + " as " + dstPathFile);
							FileUtils.forceMkdir(dstDirFile);
							FileUtils.forceMkdir(dstThumbsFile);
							FileUtils.forceMkdir(dstFlvFile);
							FileUtils.copyFile(srcPathFile, dstPathFile);
							processAttachment(dstName, dstDir, contentType,srcExt);
							
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						// Create a URL that references the attachment (but without the hostname or scheme)
						value = "'attachments/" + surveyName + "/" + dstName + "." + srcExt + "'";
					} else {
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
				if(qType.equals("geoshape")) {
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
		
		return value;
	}
	
	
	/*
	 * Create the table if it does not already exits in the database
	 */
	void createTable(Statement statement, String tableName, String sName) {
		String sql = "select count(*) from information_schema.tables where table_name =\'" + tableName + "\';";
		System.out.println("SQL:" + sql);
		try {
			ResultSet res = statement.executeQuery(sql);
			int count = 0;
			
			while(res.next()) {
				count = res.getInt(1);
			}
			if(count > 0) {
				System.out.println("        Table Exists");
			} else {
				System.out.println("        Table does not exist");
				SurveyTemplate template = new SurveyTemplate();   
				template.readDatabase(sName);	
				writeAllTableStructures(template);
			}
		} catch (Exception e) {
			System.out.println("        Error checking for existence of table:" + e.getMessage());
		}
	}
	
	/*
	 * Create the tables for the survey
	 */
	private void writeAllTableStructures(SurveyTemplate template) {
			
		String response = null;
		Connection connection = null;
		try {
		    Class.forName(dbClass);	 
			connection = DriverManager.getConnection(database, user, password);
			Statement statement = connection.createStatement();
			
			List<Form> forms = template.getAllForms();	
			connection.setAutoCommit(false);
			for(Form form : forms) {
				// create the table for all form types including complex questions such as geopolygon etc
				//if(form.getType().equals("form")) {
					writeTableStructure(form, statement);
				//}
				connection.commit();
			}	
			connection.setAutoCommit(true);
	

		} catch (Exception e) {
			if(connection != null) {
				try {
					response = "Error: Rolling back: " + e.getMessage();	// TODO can't roll back within higher level transaction
					System.out.println(response);
					e.printStackTrace();
					connection.rollback();
					connection.setAutoCommit(true);
				} catch (SQLException ex) {
					System.out.println(ex.getMessage());
				}

			}
			
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
			    e.printStackTrace();
			}
		}		
	}
	
	private void writeTableStructure(Form form, Statement statement) {
		String tableName = form.getTableName();
		List<Question> columns = form.getQuestions();
		String sql = null;	
		List <GeometryColumn> geoms = new ArrayList<GeometryColumn> ();

		/*
		 * Attempt to create the table, ignore any exception as the table may already be created
		 * TODO implement as plpgsql with quote_ident() and quote_literal() protection
		 */
		if(columns.size() > 0) {
			sql = "CREATE TABLE " + tableName + " (" +
				"prikey SERIAL PRIMARY KEY, " +
				"parkey int ";
	
			/*
			 * Create default columns
			 * only add _user and _version to the top level form
			 */
			sql += ", _bad boolean DEFAULT FALSE, _bad_reason text";
			if(!form.hasParent()) {
				sql += ", _user text, _version text";
			}
							
			for(Question q : columns) {
				
				if(q.getSource() != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
					
					// Set column type for postgres
					String colType = q.getType();
					if(colType.equals("string")) {
						colType = "text";
					} else if(colType.equals("decimal")) {
						colType = "real";
					} else if(colType.equals("select1")) {
						colType = "text";
					} else if(colType.equals("barcode")) {
						colType = "text";
					} else if(colType.equals("geopoint")) {
						
						// Add geometry columns after the table is created using AddGeometryColumn()
						GeometryColumn gc = new GeometryColumn(tableName, UtilityMethods.cleanName(q.getName()), "POINT");
						geoms.add(gc);
						continue;
					
					} else if(colType.equals("geopolygon") || colType.equals("geoshape")) {
					
						// remove the automatically generated string _parentquestion from the question name
						String qName = UtilityMethods.cleanName(q.getName());
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, qName, "POLYGON");
						geoms.add(gc);
						continue;
					
					} else if(colType.equals("geolinestring") || colType.equals("geotrace")) {
						
						String qName = UtilityMethods.cleanName(q.getName());
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, qName, "LINESTRING");
						geoms.add(gc);
						continue;
					
					} else if(colType.equals("dateTime")) {
						colType = "timestamp with time zone";					
					} else if(colType.equals("audio") || colType.equals("image") || 
							colType.equals("video")) {
						colType = "text";					
					}
					
					if(colType.equals("select")) {
						// Create a column for each option
						// Values in this column can only be '0' or '1', not using boolean as it may be easier for analysis with an integer
						Collection<Option> options = q.getChoices();
						if(options != null) {
							List<Option> optionList = new ArrayList <Option> (options);
							UtilityMethods.sortOptions(optionList);	
							for(Option option : optionList) {
								String name = SurveyTemplate.getOptionName(option, q.getName());
								sql += ", " + UtilityMethods.cleanName(name) + " integer";
							}
						} else {
							System.out.println("Warning: No Options for Select:" + q.getName());
						}
					} else {
						String name = UtilityMethods.cleanName(q.getName());
						sql += ", " + name + " " + colType;
					}
				} else {
					System.out.println("Info: Ignoring question with no source:" + q.getName());
				}
			}
			sql += ");";
			
			try {
				System.out.println("Sql statement: " + sql);
				statement.execute(sql);
				// Add geometry columns
				for(GeometryColumn gc : geoms) {
					String gSql = "SELECT AddGeometryColumn('" + gc.tableName + 
						"', '" + gc.columnName + "', " + 
						gc.srid + ", '" + gc.type + "', " + gc.dimension + ");";
					System.out.println("Sql statement: " + gSql);
					statement.execute(gSql);
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}
			
		}

	}
	
	/*
	 * Check for duplicates specified using a column named instanceid or _instanceid
	 * Return true if this instance has already been uploaded
	 */
	private ArrayList<Integer> checkDuplicate(Statement statement, String tableName, String uuid) {
		
		ArrayList<Integer> duplicateKeys = new ArrayList<Integer> ();
		
		uuid = uuid.replace("'", "''");	// Escape apostrophes
		System.out.println("checkDuplicates: " + tableName + " : " + uuid);
		
		try {

			String colTest1 = "select column_name from information_schema.columns " +
					"where table_name = '" + tableName + "' and column_name = '_instanceid'";
			String sql1 = "select prikey from " + tableName + " where _instanceid = '" + uuid + "' " +
					"order by prikey asc;";
			
			// Check for duplicates with the old _instanceid
			ResultSet res = statement.executeQuery(colTest1);
			if(res.next()) {
				// Has _instanceid
				System.out.println("Has _instanceid");
				res = statement.executeQuery(sql1);
				while(res.next()) {
					duplicateKeys.add(res.getInt(1));
				}
			}
			

			String colTest2 = "select column_name from information_schema.columns " +
					"where table_name = '" + tableName + "' and column_name = 'instanceid'";
			String sql2 = "select prikey from " + tableName + " where instanceid = '" + uuid + "' " +
					"order by prikey asc;";
			
			// Check for duplicates with the new instanceid
			res = statement.executeQuery(colTest2);
			if(res.next()) {
				// Has instanceid
				System.out.println("Has instanceid");
				res = statement.executeQuery(sql2);
				while(res.next()) {
					duplicateKeys.add(res.getInt(1));
				}
			}

			
			if(duplicateKeys.size() > 0) {
				System.out.println("Submission has " + duplicateKeys.size() + " duplicates for uuid: " + uuid);
			} 
			
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		
		return duplicateKeys;
	}
	
	/*
	 * Create thumbnails, reformat video files etc
	 */
	private void processAttachment(String fileName, String destDir, String contentType, String ext) {

    	String cmd = "/usr/bin/smap/processAttachment.sh " + fileName + " " + destDir + " " + contentType +
    			" " + ext +
 				" >> /var/log/subscribers/attachments.log 2>&1";
		System.out.println("Exec: " + cmd);
		try {

			Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", cmd});
    		
    		int code = proc.waitFor();
    		System.out.println("Attachment processing finished with status:" + code);
    		if(code != 0) {
    			System.out.println("Error: Attachment processing failed");
    		}
    		
		} catch (Exception e) {
			e.printStackTrace();
    	}
		
	}
	
	/*
	 * Apply any table changes for this version
	 */
	private void applyTableChanges(Connection connectionSD, int sId) throws SQLException {
		
		String sqlGet = "select c_id, changes "
				+ "from survey_change "
				+ "where apply_results = 'true' "
				+ "and s_id = ? ";
		PreparedStatement pstmtGet = null;
		
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		System.out.println("######## Apply table changes");
		try {
			pstmtGet = connectionSD.prepareStatement(sqlGet);
			pstmtGet.setInt(1, sId);
			System.out.println("SQL: " + pstmtGet.toString());
			
			ResultSet rs = pstmtGet.executeQuery();
			while(rs.next()) {
				int c_id = rs.getInt(1);
				ChangeItem ci = gson.fromJson(rs.getString(2), ChangeItem.class);
				System.out.println("######## Updating: " + ci.name + "__" + ci.key);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtGet != null) {pstmtGet.close();}} catch (SQLException e) {}
		}
		
	}

}
