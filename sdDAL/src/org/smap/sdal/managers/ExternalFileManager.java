package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.AssignFromSurvey;
import org.smap.sdal.model.Assignment;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Task;
import org.smap.sdal.model.TaskAssignment;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

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

/*
 * Manage creation of files
 */
public class ExternalFileManager {
	
	private static Logger log =
			 Logger.getLogger(ExternalFileManager.class.getName());
	
	/*
	 * Create a linked file
	 */
	public void createLinkedFile(Connection sd, 
			Connection cRel, 
			int sId, 			// The survey that contains the manifest item
			String filename, 
			String filepath) throws Exception {
		
		ResultSet rs = null;
		
		String sqlAppearance = "select q_id, appearance from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and appearance is not null;";
		PreparedStatement pstmtAppearance = null;
		
		String sqlCalculate = "select q_id, calculate from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and calculate is not null;";
		PreparedStatement pstmtCalculate = null;
		
		String sqlVersion = "update survey set version = version + 1 where s_id = ?";
		PreparedStatement pstmtVersion = null;
		try {
			
			ArrayList<String> uniqueColumns = new ArrayList<String> ();
			
			// 1. Get the survey ident that is going to provide the CSV data (That is the ident of the file being linked to)
			int idx = filename.indexOf('_');
			String sIdent = filename.substring(idx + 1);
			int linked_sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
			
			// 2. Determine whether or not the file needs to be regenerated
            boolean regenerate = regenerateFile(sd, cRel, linked_sId, sId);
			
			// 3.Get columns from appearance
            if(regenerate) {
				pstmtAppearance = sd.prepareStatement(sqlAppearance);
				pstmtAppearance.setInt(1, sId);
				rs = pstmtAppearance.executeQuery();
				while(rs.next()) {
					System.out.println("Appearance: " + rs.getString(2));
					int qId = rs.getInt(1);
					String appearance = rs.getString(2);
					ArrayList<String> columns = GeneralUtilityMethods.getManifestParams(sd, qId, appearance,  filename, true);
					if(columns != null) {
						for (String col : columns) {
							if(!uniqueColumns.contains(col)) {
								uniqueColumns.add(col);
							}
						}
					}
				}
				
				// 4. Get columns from calculate
				pstmtCalculate = sd.prepareStatement(sqlCalculate);
				pstmtCalculate.setInt(1, sId);
				rs = pstmtCalculate.executeQuery();
				while(rs.next()) {
					System.out.println("Calculate: " + rs.getString(2));
					int qId = rs.getInt(1);
					String calculate = rs.getString(2);
					ArrayList<String> columns = GeneralUtilityMethods.getManifestParams(sd, qId, calculate,  filename, false);
					if(columns != null) {
						for (String col : columns) {
							if(!uniqueColumns.contains(col)) {
								uniqueColumns.add(col);
							}
						}
					}
				}
	
				
				// 5. Get the sql
				String sql = getSql(sd, sIdent, uniqueColumns);		
				
				// 6. Create the file
				int code = 0;
				
				String [] cmd = {"/bin/sh", "-c", "/smap/bin/getshape.sh "
						+ "results linked "
						+ "\"" + sql + "\" "
						+ filepath
						+ " csvnozip"
						+ " >> /var/log/tomcat7/survey.log 2>&1"};
				log.info("Getting linked data: " + cmd[2]);
				Process proc = Runtime.getRuntime().exec(cmd);
				code = proc.waitFor();
				
	            log.info("Process exitValue: " + code);
	            
				// Update the version of the linker file
				pstmtVersion = sd.prepareStatement(sqlVersion);
				pstmtVersion.setInt(1, sId);
				pstmtVersion.executeUpdate();
				
            }
            
			
				
		} catch(Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception(e.getMessage());
		} finally {
			if(pstmtAppearance != null) try{pstmtAppearance.close();}catch(Exception e) {}
			if(pstmtCalculate != null) try{pstmtCalculate.close();}catch(Exception e) {}
			if(pstmtVersion != null) try{pstmtVersion.close();}catch(Exception e) {}

		}
	}
	
	/*
	 * Return true if the file needs to be regenerated
	 * If regeneration is required then also increment the version of the linking form so that it
	 * will get the new version
	 */
	private boolean regenerateFile(Connection sd, 
			Connection cRel,
			int linked_sId, 
			int linker_sId) throws SQLException {
        
		boolean regenerate = false;
		
		String sql = "select id, linked_table, number_records from linked_forms "
				+ "where linked_s_id = ? "
				+ "and linker_s_id = ?;";
		PreparedStatement pstmt = null;
		
		PreparedStatement pstmtCount = null;
		
		String sqlInsert = "insert into linked_forms "
				+ "(Linked_s_id, linked_table, number_records,linker_s_id) "
				+ "values(?, ?, ?, ?)";
		PreparedStatement pstmtInsert = null;
		
		String sqlUpdate = "update linked_forms "
				+ "set number_records = ? "
				+ "where id = ?";
		PreparedStatement pstmtUpdate = null;
		
		try {
		// Get data on the link between the two surveys
        
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, linked_sId);
			pstmt.setInt(2, linker_sId);
			log.info("Get link info: " + pstmt.toString());
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				int id = rs.getInt(1);
				String table = rs.getString(2);
				int previousCount = rs.getInt(3);
				
				String sqlCount = "select count(*) from " + table;
				int count = 0;
				try {
					pstmtCount = cRel.prepareStatement(sqlCount);
					ResultSet rsCount = pstmtCount.executeQuery();
					if(rsCount.next()) {
						count = rsCount.getInt(1);
					}
				} catch(Exception e) {
					// Table may not exist yet
				}
				
				if(count != previousCount) {
					regenerate = true;
					
					pstmtUpdate = sd.prepareStatement(sqlUpdate);
					pstmtUpdate.setInt(1, count);
					pstmtUpdate.setInt(2, id);
					
					log.info("Regenerate: " + pstmtUpdate.toString());
					pstmtUpdate.executeUpdate();
					
				}
				
			} else {
				// Create a new entry
				String table = GeneralUtilityMethods.getMainResultsTable(sd, cRel, linked_sId);
				String sqlCount = "select count(*) from " + table;
				int count = 0;
				try {
					pstmtCount = cRel.prepareStatement(sqlCount);
					log.info("Regenerate: " + pstmtCount.toString());
					ResultSet rsCount = pstmtCount.executeQuery();
					if(rsCount.next()) {
						count = rsCount.getInt(1);
					}
				} catch(Exception e) {
					// Table may not exist yet
				}
				
				if(count > 0) {
					regenerate = true;
				}
				
				pstmtInsert = sd.prepareStatement(sqlInsert);
				pstmtInsert.setInt(1, linked_sId);
				pstmtInsert.setString(2, table);
				pstmtInsert.setInt(3, count);
				pstmtInsert.setInt(4, linker_sId);
				
				log.info("Regenerate: " + pstmtInsert.toString());
				pstmtInsert.executeUpdate();
				

			}
		} finally {
			try {pstmt.close();} catch (Exception e) {};
			try {pstmtCount.close();} catch (Exception e) {};
			if(pstmtInsert != null) {try {pstmtInsert.close();} catch(Exception e) {}}
			if(pstmtUpdate != null) {try {pstmtUpdate.close();} catch(Exception e) {}}
		}
		
		
		System.out.println("Result of regenerate question is: " + regenerate);
        
        return regenerate;
	}
	
	/*
	 * *******************************************************************
	 * private methods
	 */
	/*
	 * Get the SQL to retrieve dynamic CSV data
	 */
	private String getSql(Connection sd, String sIdent, ArrayList<String> qnames) throws SQLException  {
		
		StringBuffer sql = new StringBuffer("select distinct ");
		StringBuffer where = new StringBuffer("");
		StringBuffer tabs = new StringBuffer("");
		int sId = 0;
		
		ResultSet rs = null;
		String sqlGetCol = "select column_name from question "
				+ "where qname = ? "
				+ "and f_id in (select f_id from form where s_id = ?)";
		PreparedStatement pstmtGetCol = null;
		
		String sqlGetTable = "select f_id, table_name from form "
				+ "where s_id = ? "
				+ "and parentform = ?";
		PreparedStatement pstmtGetTable = null;
		
		try {
			// 1. Get the survey id
			sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
			
			// 2. Add the columns
			pstmtGetCol = sd.prepareStatement(sqlGetCol);
			pstmtGetCol.setInt(2,  sId);
			
			for(int i = 0; i < qnames.size(); i++) {
				String name = qnames.get(i);
				String colName = null;
				pstmtGetCol.setString(1, name);
				rs = pstmtGetCol.executeQuery();
				if(rs.next()) {
					colName = rs.getString(1);
				} else {
					colName = name;		// For columns that are not questions such as _hrk, _device
				}
				if(i > 0) {
					sql.append(",");
				}
				sql.append(colName);
				sql.append(" as ");
				sql.append(name);
				
			}
			
			// 3. Add the tables
			pstmtGetTable = sd.prepareStatement(sqlGetTable);
			pstmtGetTable.setInt(1,  sId);
			getTables(pstmtGetTable, 0, null, tabs, where);
			sql.append(" from ");
			sql.append(tabs);
			if(where.length() > 0) {
				sql.append(" where ");
				sql.append(where);
			}
			
		} finally {
			if(pstmtGetCol != null) try {pstmtGetCol.close();} catch(Exception e) {}
			if(pstmtGetTable != null) try {pstmtGetTable.close();} catch(Exception e) {}
		}
		return sql.toString();
	}
	
	/*
	 * Get table details
	 */
	private void getTables(PreparedStatement pstmt, 
			int parentId, 
			String parentTable,
			StringBuffer tabs, 
			StringBuffer where) throws SQLException {
		
		ArrayList<Integer> parents = new ArrayList<Integer> ();
		ArrayList<String> parentTables = new ArrayList<String> ();
		
		pstmt.setInt(2, parentId);
		System.out.println("Get tables: " + pstmt.toString());
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			int fId = rs.getInt(1);
			String table = rs.getString(2);
			
			// Update table list
			if(tabs.length() > 0) {
				tabs.append(",");
			}
			tabs.append(table);
			
			// update where statement
			if(where.length() > 0) {
				where.append(" and ");
			}
			if(parentId == 0) {
				where.append(table);
				where.append("._bad = 'false'");
			} else {
				where.append(table);
				where.append(".parkey = ");
				where.append(parentTable);
				where.append(".prikey");
			}
			parents.add(fId);
			parentTables.add(table);
			
		}
		
		for(int i = 0; i < parents.size(); i++) {
			int fId = parents.get(i);
			String table = parentTables.get(i);
			getTables(pstmt, fId, table, tabs, where);
		}
		
	}
}


