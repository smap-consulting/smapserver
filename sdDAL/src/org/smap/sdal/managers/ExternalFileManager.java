package org.smap.sdal.managers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
	
	LogManager lm = new LogManager();		// Application log
	
	private static String PD_IDENT = "linked_s_pd_";
	
	/*
	 * Class to return SQL
	 */
	class SqlDef {
		String sql;
		ArrayList<String> colNames;
		boolean hasRbacFilter = false;
		ArrayList<SqlFrag> rfArray = null;
	}
	
	/*
	 * Call this method when a linker survey, that is a survey that links to another survey changes.
	 * This will result in regenerate being called next time the survey is downloaded
	 */
	public void linkerChanged(Connection sd, int sId) throws SQLException {
		String sql = "delete from linked_forms where linker_s_id = ?;";
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.executeUpdate();
		} finally {
			if(pstmt != null){try {pstmt.close();}catch (Exception e) {}};
		}
	}
	
	/*
	 * Create a linked file
	 */
	public boolean createLinkedFile(Connection sd, 
			Connection cRel, 
			int sId, 			// The survey that contains the manifest item
			String filename, 
			String filepath,
			String userName) throws Exception {
		
		ResultSet rs = null;
		boolean linked_pd = false;
		String sIdent = null;
		int linked_sId = 0;
		String data_key = null;
		boolean non_unique_key = false;
		File f = new File(filepath + ".csv");	// file path does not include the extension because getshape.sh adds it
		ArrayList<Pulldata> pdArray = null;
		boolean regenerate = true;
		
		String sqlPulldata = "select pulldata from survey where s_id = ?";
		PreparedStatement pstmtPulldata = null;
		
		String sqlAppearance = "select q_id, appearance from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and appearance is not null;";
		PreparedStatement pstmtAppearance = null;
		
		String sqlCalculate = "select q_id, calculate from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and calculate is not null;";
		PreparedStatement pstmtCalculate = null;
		
		PreparedStatement pstmtData = null;
		try {
			
			ArrayList<String> uniqueColumns = new ArrayList<String> ();
			/*
			 * Get parameters
			 * There are two types of linked CSV files generated
			 *  1. Parent child records where there can be many records from a sub form that match the key. Filename starts with "linked_s_pd_" (PD_IDENT)
			 *  2. Normal lookup where there is only one record that should match a key. Filename starts with "linked_"
			 */
			if(filename.startsWith(PD_IDENT)) {
				linked_pd = true;	
				
				sIdent = filename.substring(PD_IDENT.length());
				
				pstmtPulldata = sd.prepareStatement(sqlPulldata);
				pstmtPulldata.setInt(1, sId);
				log.info("Get pulldata key from survey: " + pstmtPulldata.toString());
				rs = pstmtPulldata.executeQuery();
				if(rs.next()) {
					Type type = new TypeToken<ArrayList<Pulldata>>(){}.getType();
					pdArray = new Gson().fromJson(rs.getString(1), type); 
					if(pdArray == null) {
						throw new Exception("Pulldata definition not found for survey: " + sId + " and file " + filename + 
								". Set the pulldata definition from the online editor file menu.");
					}
					for(int i = 0; i < pdArray.size(); i++) {
						if(pdArray.get(i).survey.equals(sIdent)) {
							data_key = pdArray.get(i).data_key;
							non_unique_key = pdArray.get(i).repeats;
							break;
						}
					}
				} else {
					throw new Exception("Puldata definition not found");
				}
				
				if(data_key == null) {
					throw new Exception("Pulldata definition not found");
				}
				
				
			} else {
				int idx = filename.indexOf('_');
				sIdent = filename.substring(idx + 1);
			}
			
			if(sIdent != null && sIdent.equals("self")) {
				linked_sId = sId;
			} else {
				linked_sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
			}
			
			// 2. Determine whether or not the file needs to be regenerated
			log.info("Test for regenerate of file: " + f.getAbsolutePath() + " File exists: " + f.exists());
			regenerate = regenerateFile(sd, cRel, linked_sId, sId, f.exists(), f.getAbsolutePath());
			
			// 3.Get columns from appearance
            if(regenerate) {
				pstmtAppearance = sd.prepareStatement(sqlAppearance);
				pstmtAppearance.setInt(1, sId);
				log.info("Appearance cols: " + pstmtAppearance.toString());
				rs = pstmtAppearance.executeQuery();
				while(rs.next()) {
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
				log.info("Calculate cols: " + pstmtAppearance.toString());
				rs = pstmtCalculate.executeQuery();
				while(rs.next()) {
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
				RoleManager rm = new RoleManager();
				SqlDef sqlDef = getSql(sd, linked_sId, uniqueColumns, linked_pd, data_key, userName, rm);		
				pstmtData = cRel.prepareStatement(sqlDef.sql);
				int paramCount = 1;
				if(sqlDef.hasRbacFilter) {
					paramCount = rm.setRbacParameters(pstmtData, sqlDef.rfArray, paramCount);
				}
				log.info("Get CSV data: " + pstmtData.toString());
				
				// 6. Create the file
				if(linked_pd && non_unique_key) {
					
					rs = pstmtData.executeQuery();
					
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream(f.getAbsoluteFile()), "UTF8"));
					
					// Write header
					bw.write("_data_key");
					if(non_unique_key) {
						bw.write(",_count");
					}
					for(int i = 0; i < sqlDef.colNames.size(); i++) {
						String col = sqlDef.colNames.get(i);
						bw.write(",");
						bw.write(col);
					}
					bw.newLine();
					
					/*
					 * Class to store a set of records for a single key when when non unique key has been specified
					 *  This allows us to count the number of duplicate keys before writing the data to the csv file
					 */
					ArrayList<StringBuilder> nonUniqueRecords = new ArrayList<StringBuilder> ();
					
					// Write data
					String currentDkv = null;		// Current value of the data key
					String dkv = null;
					while(rs.next()) {
						dkv = rs.getString("_data_key");
						System.out.println("Data key: " + dkv);
						if(dkv != null && !dkv.equals(currentDkv)) {
							// A new data key
							writeRecords(non_unique_key, nonUniqueRecords, bw, currentDkv);
							nonUniqueRecords = new ArrayList<StringBuilder> ();
							currentDkv = dkv;
						}
						
						// Store the record
						StringBuilder s = new StringBuilder("");
						// Don't write the key yet
						if(non_unique_key) {
							s.append(",");
						}
						for(int i = 0; i < sqlDef.colNames.size(); i++) {
							String col = sqlDef.colNames.get(i);
							s.append(",");
							String value = rs.getString(col);
							if(value == null) {
								value = "";
							}
							s.append(value);
						}
						nonUniqueRecords.add(s);
					}
					
					// Write the records for the final key
					writeRecords(non_unique_key, nonUniqueRecords, bw, currentDkv);
					
					bw.flush();
					bw.close();
				} else {
					// Use PSQL to generate the file as it is faster
					int code = 0;
					
					String [] cmd = {"/bin/sh", "-c", "/smap_bin/getshape.sh "
							+ "results linked "
							+ "\"" + pstmtData.toString() + "\" "
							+ filepath
							+ " csvnozip"
							+ " >> /var/log/tomcat7/survey.log 2>&1"};
					log.info("Getting linked data: " + cmd[2]);
					Process proc = Runtime.getRuntime().exec(cmd);
					code = proc.waitFor();
					
		            log.info("Process exitValue: " + code);
				}
	            			
            }
            
			
				
		} catch(Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			lm.writeLog(sd, sId, userName, "error", "Creating CSV file: " + e.getMessage());
			throw new Exception(e.getMessage());
		} finally {
			if(pstmtAppearance != null) try{pstmtAppearance.close();}catch(Exception e) {}
			if(pstmtCalculate != null) try{pstmtCalculate.close();}catch(Exception e) {}
			if(pstmtData != null) try{pstmtData.close();}catch(Exception e) {}
			if(pstmtPulldata != null) try{pstmtPulldata.close();}catch(Exception e) {}
		}
		
		return regenerate;
	}
	
	/*
	 * Write a set of records for a single data key
	 */
	private void writeRecords(boolean non_unique_key, ArrayList<StringBuilder> nonUniqueRecords, BufferedWriter bw, String dkv) throws IOException {
		
		if(non_unique_key && nonUniqueRecords.size() > 0) {
			// Write the number of records
			bw.write(dkv);
			bw.write(",");
			bw.write(String.valueOf(nonUniqueRecords.size()));
			bw.newLine();
		}
		
		// Write each record
		for(int i = 0; i < nonUniqueRecords.size(); i++) {
			bw.write(dkv);
			if(non_unique_key) {
				bw.write("_");
				bw.write(String.valueOf(i + 1));		// To confirm with position(..) which starts at 1
			}
			bw.write(nonUniqueRecords.get(i).toString());
			bw.newLine();
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
			int linker_sId,
			boolean fileExists,
			String filepath) throws SQLException {
        
		boolean regenerate = false;
		boolean tableExists = true;
		
		String sql = "select id, linked_table, number_records from linked_forms "
				+ "where linked_s_id = ? "
				+ "and linker_s_id = ? "
				+ "and link_file = ?;";
		PreparedStatement pstmt = null;
		
		PreparedStatement pstmtCount = null;
		
		String sqlInsert = "insert into linked_forms "
				+ "(Linked_s_id, linked_table, number_records,linker_s_id, link_file) "
				+ "values(?, ?, ?, ?, ?)";
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
			pstmt.setString(3, filepath);
			log.info("Get existing count info: " + pstmt.toString());
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				int id = rs.getInt(1);
				String table = rs.getString(2);
				int previousCount = rs.getInt(3);
				
				log.info("Existing count found: " + previousCount);
				
				String sqlCount = "select count(*) from " + table;
				int count = 0;
				try {
					pstmtCount = cRel.prepareStatement(sqlCount);
					log.info("Get current count info: " + pstmtCount.toString());
					ResultSet rsCount = pstmtCount.executeQuery();
					if(rsCount.next()) {
						count = rsCount.getInt(1);
					}
				} catch(Exception e) {
					// Table may not exist yet
					log.info("Exception getting current count: " + e.getMessage());
					tableExists = false;
				}
				
				log.info("New count: " + count);
				
				if(count != previousCount) {
					regenerate = true;
					log.info("Regenerating because the new count does not match the old count");
					
					pstmtUpdate = sd.prepareStatement(sqlUpdate);
					pstmtUpdate.setInt(1, count);
					pstmtUpdate.setInt(2, id);
					
					log.info("Regenerate: " + pstmtUpdate.toString());
					pstmtUpdate.executeUpdate();
					
				}
				
			} else {
				// Create a new entry
				
				String table = GeneralUtilityMethods.getMainResultsTable(sd, cRel, linked_sId);
				int count = 0;
				if(table != null) {
					log.info("Creating a new count entry");
					count = 0;
					pstmtInsert = sd.prepareStatement(sqlInsert);
					pstmtInsert.setInt(1, linked_sId);
					pstmtInsert.setString(2, table);
					pstmtInsert.setInt(3, count);
					pstmtInsert.setInt(4, linker_sId);
					pstmtInsert.setString(5, filepath);
							
					log.info("Create new count entry: " + pstmtInsert.toString());
					pstmtInsert.executeUpdate();
							
					regenerate = false;
					log.info("Not regenerating because we created a new count entry with a value of 0");
						
					
				} else {
					log.info("Table " + table + " not found. Probably no data has been submitted");
					tableExists = false;
				}		

			}
		} finally {
			try {pstmt.close();} catch (Exception e) {};
			try {pstmtCount.close();} catch (Exception e) {};
			if(pstmtInsert != null) {try {pstmtInsert.close();} catch(Exception e) {}}
			if(pstmtUpdate != null) {try {pstmtUpdate.close();} catch(Exception e) {}}
		}
		

		if(tableExists && !fileExists) {
			regenerate = true;		// Override regenerate if the file has been deleted
		}
		
		log.info("Result of regenerate question is: " + regenerate);
		
        return regenerate;
	}
	
	/*
	 * *******************************************************************
	 * private methods
	 */
	/*
	 * Get the SQL to retrieve dynamic CSV data
	 * TODO replace this with the query generator from SDAL
	 */
	private SqlDef getSql(
			Connection sd, 
			int sId, 
			ArrayList<String> qnames,
			boolean linked_pd,
			String data_key,
			String user,
			RoleManager rm) throws SQLException  {
		
		StringBuffer sql = new StringBuffer("select distinct ");
		StringBuffer where = new StringBuffer("");
		StringBuffer tabs = new StringBuffer("");
		String linked_pd_sel = null;
		SqlDef sqlDef = new SqlDef();
		ArrayList<String> colNames = new ArrayList<String> ();
		HashMap <Integer, Integer> forms = new HashMap <Integer, Integer> ();
		Form topForm = GeneralUtilityMethods.getTopLevelForm(sd, sId);
		
		ResultSet rs = null;
		String sqlGetCol = "select column_name, f_id from question "
				+ "where qname = ? "
				+ "and f_id in (select f_id from form where s_id = ?)";
		PreparedStatement pstmtGetCol = null;
		
		String sqlGetTable = "select f_id, table_name from form "
				+ "where s_id = ? "
				+ "and parentform = ?";
		PreparedStatement pstmtGetTable = null;
		
		try {
			int fId;	
			
			// 1. Add the columns
			pstmtGetCol = sd.prepareStatement(sqlGetCol);
			pstmtGetCol.setInt(2,  sId);
			
			if(linked_pd) {
				linked_pd_sel = GeneralUtilityMethods.convertAllxlsNamesToQuery(data_key, sId, sd);
				sql.append(linked_pd_sel);
				sql.append(" as _data_key");
			}
			// Always add top level form
			forms.put(topForm.id, topForm.id);
			for(int i = 0; i < qnames.size(); i++) {
				String name = qnames.get(i);
				if(name.equals("_data_key") || name.equals("_count")) {
					continue;						// Generated not extracted
				}
				String colName = null;
				pstmtGetCol.setString(1, name);
				rs = pstmtGetCol.executeQuery();
				if(rs.next()) {
					colName = rs.getString(1);
					fId = rs.getInt(2);
				} else {
					colName = name;		// For columns that are not questions such as _hrk, _device
					fId = topForm.id;
				}
				colNames.add(colName);
				forms.put(fId, fId);
				
				System.out.println("Adding form: " + fId + " for name: " + colName);
				
				if(i > 0 || linked_pd) {
					sql.append(",");
				}
				sql.append(colName);
				sql.append(" as ");
				sql.append(name);
				
			}
			
			// 2. Add the tables
			pstmtGetTable = sd.prepareStatement(sqlGetTable);
			pstmtGetTable.setInt(1,  sId);
			getTables(pstmtGetTable, 0, null, tabs, where, forms);
			sql.append(" from ");
			sql.append(tabs);
			
			// 3. Add the where clause
			if(where.length() > 0) {
				sql.append(" where ");
				sql.append(where);
			}
			
			// 4. Add the RBAC/Row filter
			// Add RBAC/Role Row Filter
			sqlDef.rfArray = null;
			sqlDef.hasRbacFilter = false;
			// Apply roles for super user as well
			sqlDef.rfArray = rm.getSurveyRowFilter(sd, sId, user);
			if(sqlDef.rfArray.size() > 0) {
				String rFilter = rm.convertSqlFragsToSql(sqlDef.rfArray);
				if(rFilter.length() > 0) {
					sql.append(" and ");
					sql.append(rFilter);
					sqlDef.hasRbacFilter = true;
				}
			}
			
			// If this is a pulldata linked file then order the data by _data_key
			if(linked_pd) {
				sql.append( " order by _data_key");
			}
			
		} finally {
			if(pstmtGetCol != null) try {pstmtGetCol.close();} catch(Exception e) {}
			if(pstmtGetTable != null) try {pstmtGetTable.close();} catch(Exception e) {}
		}
		
		sqlDef.sql = sql.toString();
		sqlDef.colNames = colNames; 
		return sqlDef;
	}
	
	/*
	 * Get table details
	 */
	private void getTables(PreparedStatement pstmt, 
			int parentId, 
			String parentTable,
			StringBuffer tabs, 
			StringBuffer where,
			HashMap<Integer, Integer> forms) throws SQLException {
		
		ArrayList<Integer> parents = new ArrayList<Integer> ();
		ArrayList<String> parentTables = new ArrayList<String> ();
		
		pstmt.setInt(2, parentId);
		log.info("Get tables: " + pstmt.toString());
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			int fId = rs.getInt(1);
			String table = rs.getString(2);
			
			/*
			 * Ignore forms that where no questions have been asked for
			 */
			if(forms.get(fId) != null) {
			
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
			
		}
		
		for(int i = 0; i < parents.size(); i++) {
			int fId = parents.get(i);
			String table = parentTables.get(i);
			getTables(pstmt, fId, table, tabs, where, forms);
		}
		
	}
}


