package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.LanguageItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.SqlFrag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*****************************************************************************
 * 
 * This file is part of SMAP.
 * 
 * SMAP is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * SMAP is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SMAP. If not, see <http://www.gnu.org/licenses/>.
 * 
 ******************************************************************************/

/*
 * Manage the csvtable entries that point to survey data rather than data loaded directly from a CSV
 */
public class SurveyTableManager {

	private static Logger log = Logger.getLogger(SurveyTableManager.class.getName());
	
	/*
	 * Class to return SQL
	 */
	private class SqlDef {
		private String sql;
		private String order_by;
		private boolean hasWhere = false; 
		private ArrayList<String> colNames;
		private ArrayList<String> qnames;
		private boolean hasRbacFilter = false;
		private ArrayList<SqlFrag> rfArray = null;
	}
	
	LogManager lm = new LogManager(); // Application log
	private static String PD_IDENT = "linked_s_pd_";
	
	Connection sd = null;
	Connection cResults = null;
	ResourceBundle localisation = null;
	private int tableId = 0;
	ResultSet rs = null;
	
	private SqlDef sqlDef = null;
	private boolean non_unique_key = false;
	
	/*
	 * Constructor to create a table to hold the CSV data if it does not already exist
	 */
	public SurveyTableManager(Connection sd, Connection cResults, ResourceBundle l, int oId, int sId, String fileName, String user)
			throws Exception {
		
		this.sd = sd;
		this.cResults = cResults;
		this.localisation = l;
		
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		String sqlGetCsvTable = "select id, sqldef from csvtable "
				+ "where o_id = ? "
				+ "and s_id = ? "
				+ "and survey "
				+ "and filename = ? "
				+ "and user_ident = ?";
		PreparedStatement pstmtGetCsvTable = null;
		
		String sqlInsertCsvTable = "insert into csvtable (id, o_id, s_id, filename, survey, user_ident, ts_initialised) "
				+ "values(nextval('csv_seq'), ?, ?, ?, true, ?, now())";
		PreparedStatement pstmtInsertCsvTable = null;
		try {
			pstmtGetCsvTable = sd.prepareStatement(sqlGetCsvTable);
			pstmtGetCsvTable.setInt(1, oId);
			pstmtGetCsvTable.setInt(2, sId);
			pstmtGetCsvTable.setString(3, fileName);
			pstmtGetCsvTable.setString(4, user);
			log.info("Getting csv table id: " + pstmtGetCsvTable.toString());
			ResultSet rs = pstmtGetCsvTable.executeQuery();
			
			if(rs.next()) {
				tableId = rs.getInt(1);
				sqlDef = gson.fromJson(rs.getString(2), SqlDef.class);
			} else {
				/*
				 * Create the headers and sql
				 */				
				pstmtInsertCsvTable = sd.prepareStatement(sqlInsertCsvTable, Statement.RETURN_GENERATED_KEYS);
				pstmtInsertCsvTable.setInt(1, oId);
				pstmtInsertCsvTable.setInt(2, sId);
				pstmtInsertCsvTable.setString(3, fileName);
				pstmtInsertCsvTable.setString(4, user);
				log.info("Create a new csv file entry: " + pstmtInsertCsvTable.toString());
				pstmtInsertCsvTable.executeUpdate();
				ResultSet rsKeys = pstmtInsertCsvTable.getGeneratedKeys();
				if(rsKeys.next()) {
					tableId = rsKeys.getInt(1);
				} else {
					throw new Exception("Failed to create CSV Table entry");
				}
			}
			if(sqlDef == null) {
				getSqlAndHeaders(sd, cResults, sId, fileName, user);
			}

			
		} finally {
			try {pstmtGetCsvTable.close();} catch(Exception e) {}
			try {pstmtInsertCsvTable.close();} catch(Exception e) {}
		}
	}

	/*
	 * Constructor that does not attempt to connect to a table or create a new table
	 */
	public SurveyTableManager(Connection sd, ResourceBundle l)
			throws Exception {
		
		this.sd = sd;
		this.localisation = l;
		
	}
	
	/*
	 * Get columns
	 */
	public ArrayList<String> getColumns() {
		ArrayList<String> cols = null;
		if(sqlDef != null) {
			cols = sqlDef.colNames;
		}
		return cols;
	}

	
	/*
	 * Get a result set of data for a lookup
	 * type = lookup || choices
	 */
	public void initData(
			PreparedStatement pstmt, 
			String type, 
			String key_column, 
			String key_value,
			String selection, 
			ArrayList<String> arguments, 
			ArrayList<String> whereColumns,
			String tz
			) throws Exception {
		
		if(sqlDef != null && sqlDef.qnames != null && sqlDef.qnames.size() > 0) {
			StringBuilder sql = new StringBuilder(sqlDef.sql);
			
			// Add filter
			String filter = null;
			if(type.equals("lookup")) {
				filter = getFilter(key_column, key_value);
				if(filter.length() > 0) {
					if(sqlDef.hasWhere) {
						sql.append(" and ");
					} else {
						sql.append(" where ");
					}
					sql.append(filter);
				}
			} else if (type.equals("choices")) {
				// Check the where questions
				if(whereColumns != null) {
					for(String col : whereColumns) {
						boolean foundCol = false;
						for(String h : sqlDef.qnames) {
							if(h.equals(col)) {
								foundCol = true;
								break;
							}
						}
						if(!foundCol) {
							throw new ApplicationException("Question " + col + " not found in table ");
						}
					}
				}
				if(selection != null) {
					if(sqlDef.hasWhere) {
						sql.append(" and ");
					} else {
						sql.append(" where ");
					}
					sql.append(selection);
				}
			}
			sql.append(sqlDef.order_by);
			pstmt = cResults.prepareStatement(sql.toString());
			int paramCount = 1;
			if (sqlDef.hasRbacFilter) {
				paramCount = GeneralUtilityMethods.setArrayFragParams(pstmt, sqlDef.rfArray, paramCount, tz);
			}
			if(type.equals("lookup")) {
				if(filter.length() > 0) {
					pstmt.setString(paramCount, key_value);
				}
			} else {
				int paramIndex = 1;
				if(arguments != null) {
					for(String arg : arguments) {
						pstmt.setString(paramIndex++, arg);
					}
				}
			}
			
			log.info("Init data: " + pstmt.toString());
			try {
				rs = pstmt.executeQuery();
			} catch (Exception e) {
				String msg = e.getMessage();
				if(msg != null && e.getMessage().contains("does not exist")) {
					log.info("Attempting to get data from a survey that has had no data submitted");
				} else {
					log.log(Level.SEVERE, msg, e);
				}
				rs = null;
			}
		} else {
			rs = null;
		}
	}
	
	/*
	 * Get a line of data
	 */
	public ArrayList<KeyValueSimp> getLine() throws SQLException {
		ArrayList<KeyValueSimp> line = null;
		
		if(rs != null && rs.next()) {
			line = new ArrayList<KeyValueSimp> ();
			for (int i = 0; i < sqlDef.colNames.size(); i++) {
				String qname = sqlDef.qnames.get(i);
				// support labels separated by commas
				String [] multQuestions = qname.split(",");
				String value = "";
				if(multQuestions.length > 1) {
					for(String q : multQuestions) {
						String v = rs.getString(q.trim());
						if(v != null) {
							if(value.length() > 0) {
								value += ", ";
							}
							value += v;
						}
					}
				} else {
					value = rs.getString(qname);
				}
				if (value == null) {
					value = "";
				}
				line.add(new KeyValueSimp(qname, value));
			}
		}
		return line;
	}
	
	/*
	 * Get a data hashmap
	 */
	public HashMap<String, String> getLineAsHash() throws SQLException {
		
		HashMap<String, String> line = null;
		
		if(rs != null && rs.next()) {
			line = new HashMap<String, String> ();
			for (int i = 0; i < sqlDef.colNames.size(); i++) {
				String col = sqlDef.colNames.get(i);
				String value = rs.getString(col);
				if (value == null) {
					value = "";
				}
				line.put(col, value);
			}
		}
		return line;
	}
	
	/*
	 * Get a choice
	 */
	public Option getLineAsOption(String oValue, ArrayList<LanguageItem> items) throws SQLException {
		Option o = null;
		
		if(rs != null && rs.next()) {
			o = new Option ();
			o.value = rs.getString(oValue);
			for(LanguageItem item : items) {
				Label l = new Label();
				if(item.text.contains(",")) {
					String[] comp = item.text.split(",");
					l.text = "";
					for(int i = 0; i < comp.length; i++) {
						if(i > 0) {
							l.text += ", ";
						}
						l.text += rs.getString(comp[i].trim());
					}
				} else {
					l.text = rs.getString(item.text);
				}
				
				o.labels.add(l);
			}
		}
		return o;
	}
	
	/*
	 * Get an sql filter clause
	 */
	public String getFilter(String key_column, String key_value) throws Exception {
		
		String filter = "";
		if(sqlDef != null && key_column != null && key_value != null) {
			for(String colName : sqlDef.colNames) {
				if(key_column.equals(colName)) {
					filter = key_column + "::text = ?";  
					break;
				}
			}	
		}
		return filter;
	}
	
	/*
	 * Delete entries in csv table
	 */
	public void delete(int sId) throws SQLException {
		
		String sqlDelete = "delete from csvtable where s_id = ? and survey";
		PreparedStatement pstmtDelete = null;
				
		try {
			pstmtDelete = sd.prepareStatement(sqlDelete);
			pstmtDelete.setInt(1, sId);
			pstmtDelete.executeUpdate();
					
		} catch(Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		} finally {
			try {pstmtDelete.close();} catch(Exception e) {}
		}
	}
	
	/*
	 * Delete entries in csv table for a use
	 */
	public void deleteForUsers(String user) throws SQLException {
		
		String sqlDelete = "delete from csvtable where user_ident = ? and survey";
		PreparedStatement pstmtDelete = null;
				
		try {
			pstmtDelete = sd.prepareStatement(sqlDelete);
			pstmtDelete.setString(1, user);
			pstmtDelete.executeUpdate();
					
		} catch(Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		} finally {
			try {pstmtDelete.close();} catch(Exception e) {}
		}
	}
	

	/*
	 * Get the SQL needed to retrieve the data as well as the headers
	 */
	public boolean getSqlAndHeaders(Connection sd, Connection cRel, int sId, // The survey that contains the manifest item
			String filename, String user) throws Exception {

		ResultSet rs = null;
		boolean linked_s_pd = false;
		boolean chart = false;
		String chart_key = null;
		String sIdent = null;
		int linked_sId = 0;
		String data_key = null;
		ArrayList<Pulldata> pdArray = null;
		boolean regenerate = true;

		String sqlPulldata = "select pulldata from survey where s_id = ?";
		PreparedStatement pstmtPulldata = null;

		String sqlAppearance = "select q_id, appearance from question "
				+ "where f_id in (select f_id from form where s_id = ?) " + "and appearance is not null;";
		PreparedStatement pstmtAppearance = null;

		String sqlCalculate = "select q_id, calculate from question "
				+ "where f_id in (select f_id from form where s_id = ?) " + "and calculate is not null;";
		PreparedStatement pstmtCalculate = null;

		String sqlUpdate = "update csvtable set "
				+ "chart = ?,"
				+ "non_unique_key = ?,"
				+ "sqldef = ? "
				+ "where id = ?";
		PreparedStatement pstmtUpdate = null;
		try {

			ArrayList<String> uniqueColumns = new ArrayList<String>();
			/*
			 * Get parameters There are two types of linked CSV files generated 
			 * 1. Parent child records where there can be many records from a sub form that match the
			 *  key. Filename starts with "linked_s_pd_" (PD_IDENT) 
			 * 2. Normal lookup where there is only one record that should match a key. Filename starts with
			 *  "linked_"
			 * 3. Time series data.  Filename starts with "chart_s"
			 */
			if (filename.startsWith(PD_IDENT)) {
				linked_s_pd = true;

				sIdent = filename.substring(PD_IDENT.length());

				pstmtPulldata = sd.prepareStatement(sqlPulldata);
				pstmtPulldata.setInt(1, sId);
				log.info("Get pulldata key from survey: " + pstmtPulldata.toString());
				rs = pstmtPulldata.executeQuery();
				if (rs.next()) {
					Type type = new TypeToken<ArrayList<Pulldata>>() {}.getType();
					pdArray = new Gson().fromJson(rs.getString(1), type);
					if (pdArray == null) {
						throw new Exception("Pulldata definition not found for survey: " + sId + " and file " + filename
								+ ". Set the pulldata definition from the online editor file menu.");
					}
					for (int i = 0; i < pdArray.size(); i++) {
						String pulldataIdent = pdArray.get(i).survey;

						if (pulldataIdent.equals("self")) {
							pulldataIdent = sIdent;
						}
						log.info("PulldataIdent: " + pulldataIdent);

						if (pulldataIdent.equals(sIdent)) {
							data_key = pdArray.get(i).data_key;
							non_unique_key = true;
							break;
						}
					}
				} else {
					throw new Exception("No record found for pull data");
				}

				if (data_key == null) {
					throw new Exception("Pulldata data_key not found");
				}

			} else if (filename.startsWith("linked_s")){
				int idx = filename.indexOf('_');
				sIdent = filename.substring(idx + 1);
			} else if (filename.startsWith("chart_s")) {  // Form: chart_sxx_yyyy_keyname we want sxx_yyyy
				chart = true;
				int idx1 = filename.indexOf('_');
				int idx2 = filename.indexOf('_', idx1 + 1);
				idx2 = filename.indexOf('_', idx2 + 1);
				sIdent = filename.substring(idx1 + 1, idx2);
				chart_key = filename.substring(idx2 + 1);
			}

			if (sIdent != null && sIdent.equals("self")) {
				linked_sId = sId;
				sIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			} else {
				linked_sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);
			}
			if(linked_sId == 0) {
				throw new ApplicationException("Error: Survey with identifier " + sIdent + " was not found");
			}

			// 3.Get columns from appearance
			pstmtAppearance = sd.prepareStatement(sqlAppearance);
			pstmtAppearance.setInt(1, sId);
			log.info("Appearance cols: " + pstmtAppearance.toString());
			rs = pstmtAppearance.executeQuery();
			while (rs.next()) {
				int qId = rs.getInt(1);
				String appearance = rs.getString(2);
				ArrayList<String> columns = GeneralUtilityMethods.getManifestParams(sd, qId, appearance, 
						filename, true, sIdent);
				if (columns != null) {
					for (String col : columns) {
						if (!uniqueColumns.contains(col)) {
							uniqueColumns.add(col);
						}
					}
				}
			}

			// 4. Get columns from calculate
			pstmtCalculate = sd.prepareStatement(sqlCalculate);
			pstmtCalculate.setInt(1, sId);
			log.info("Calculate cols: " + pstmtCalculate.toString());
			rs = pstmtCalculate.executeQuery();
			while (rs.next()) {
				int qId = rs.getInt(1);
				String calculate = rs.getString(2);
				ArrayList<String> columns = GeneralUtilityMethods.getManifestParams(sd, qId, calculate, filename,
						false, sIdent);
				if (columns != null) {
					for (String col : columns) {
						if (!uniqueColumns.contains(col)) {
							uniqueColumns.add(col);
							log.info("Adding unique column: " + col);
						}
					}
				}
			}

			log.info("Unique Columns: " + uniqueColumns.size());
			// 5. Get the sql as long as there is data to retrieve
			
			if(uniqueColumns.size() > 0) {
				RoleManager rm = new RoleManager(localisation);
				sqlDef = getSql(sd, linked_sId, uniqueColumns, linked_s_pd, data_key, user, rm, chart_key);
				
				if(sqlDef.colNames.size() > 0) {
					pstmtUpdate = sd.prepareStatement(sqlUpdate);
					pstmtUpdate.setBoolean(1, chart);
					pstmtUpdate.setBoolean(2, non_unique_key);
					pstmtUpdate.setString(3, new Gson().toJson(sqlDef));
					pstmtUpdate.setInt(4, tableId);
					log.info("Add sql info: " + pstmtUpdate.toString());
					pstmtUpdate.executeUpdate();
				}
			}
			
			

		} finally {
			if (pstmtAppearance != null)	{try {pstmtAppearance.close();} catch (Exception e) {}}
			if (pstmtCalculate != null) {try {pstmtCalculate.close();	} catch (Exception e) {}}
			if (pstmtUpdate != null) {try {pstmtUpdate.close();} catch (Exception e) {}}
			if (pstmtPulldata != null) {	try {pstmtPulldata.close();} catch (Exception e) {}}
		}

		return regenerate;
	}
	
	/*
	 * Get the SQL to retrieve dynamic CSV data TODO replace this with the query
	 * generator from SDAL
	 */
	private SqlDef getSql(Connection sd, int sId, ArrayList<String> qnames, boolean linked_s_pd, String data_key,
			String user, RoleManager rm, String chart_key) throws Exception {

		StringBuffer sql = new StringBuffer("select ");
		if(chart_key == null) {		// Time series data should not be made distinct
			sql.append("distinct ");
		}
		StringBuffer where = new StringBuffer("");
		StringBuffer tabs = new StringBuffer("");
		StringBuffer order_cols = new StringBuffer("");
		String linked_s_pd_sel = null;
		SqlDef sqlDef = new SqlDef();
		ArrayList<String> colNames = new ArrayList<>();
		ArrayList<String> validatedQuestionNames = new ArrayList<>();
		ArrayList<String> subTables = new ArrayList<>();
		HashMap<Integer, Integer> forms = new HashMap<>();
		Form topForm = GeneralUtilityMethods.getTopLevelForm(sd, sId);

		ResultSet rs = null;
		String sqlGetCol = "select column_name, f_id from question " 
				+ "where qname = ? " 
				+ "and published "
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
			pstmtGetCol.setInt(2, sId);

			boolean first = true;
			if (linked_s_pd) {
				linked_s_pd_sel = GeneralUtilityMethods.convertAllxlsNamesToQuery(data_key, sId, sd);
				sql.append(linked_s_pd_sel);
				sql.append(" as _data_key");
				first = false;
			}

			forms.put(topForm.id, topForm.id); // Always add top level form
			for (int i = 0; i < qnames.size(); i++) {
				String name = qnames.get(i);
				if (name.equals("_data_key") || name.equals("_count")) {
					continue; // Generated not extracted
				}
				String colName = null;
				String[] multNames = name.split(",");		// Allow for comma separated labels
				for(String n : multNames) {
					n = n.trim();
					pstmtGetCol.setString(1, n);
					log.info("%%%%%%%%%%%%%%%%%%%%%%% Check presence of col name:" + pstmtGetCol.toString());
					rs = pstmtGetCol.executeQuery();
					if (rs.next()) {
						colName = rs.getString(1);
						fId = rs.getInt(2);
					} else if (SmapServerMeta.isServerReferenceMeta(n)) {
						colName = n; // For columns that are not questions such as _hrk, _device
						fId = topForm.id;
					} else {
						continue; // Name not found
					}
					colNames.add(colName);
					validatedQuestionNames.add(n);
					forms.put(fId, fId);
	
					if (!first) {
						sql.append(",");
						order_cols.append(",");
					}
					sql.append(colName);
					sql.append(" as ");
					sql.append(n);
					first = false;
	
					order_cols.append(colName);
				}
			}

			// 2. Add the tables
			pstmtGetTable = sd.prepareStatement(sqlGetTable);
			pstmtGetTable.setInt(1, sId);
			getTables(pstmtGetTable, 0, null, tabs, where, forms, subTables);

			// 2.5 Add the primary keys of sub tables so they can be sorted on
			if (linked_s_pd && subTables.size() > 0) {
				for (String subTable : subTables) {
					sql.append(",");
					sql.append(subTable);
					sql.append(".prikey");
				}
			}

			sql.append(" from ");
			sql.append(tabs);

			// 3. Add the where clause
			if (where.length() > 0) {
				sql.append(" where ");
				sqlDef.hasWhere = true;
				sql.append(where);
			}

			// 4. Add the RBAC/Row filter
			// Add RBAC/Role Row Filter
			sqlDef.rfArray = null;
			sqlDef.hasRbacFilter = false;
			// Apply roles for super user as well
			sqlDef.rfArray = rm.getSurveyRowFilter(sd, sId, user);
			if (sqlDef.rfArray.size() > 0) {
				String rFilter = rm.convertSqlFragsToSql(sqlDef.rfArray);
				if (rFilter.length() > 0) {
					if(where.length() > 0) {
						sql.append(" where ");
						sqlDef.hasWhere = true;
					} else {
						sql.append(" and ");
					}
					sql.append(rFilter);
					sqlDef.hasRbacFilter = true;
				}
			}

			// If this is a pulldata linked file then order the data by _data_key and then
			// the primary keys of sub forms
			StringBuffer orderBy = new StringBuffer("");
			if(chart_key != null) {
				orderBy.append(" order by ");
				orderBy.append(chart_key);
				orderBy.append(" asc");
			} else if (linked_s_pd) {
				orderBy.append(" order by _data_key");
				if (subTables.size() > 0) {
					for (String subTable : subTables) {
						orderBy.append(",");
						orderBy.append(subTable);
						orderBy.append(".prikey asc");
					}
				} else {
					orderBy.append(" asc");
				}
			} else if(order_cols != null) {
				// order by the columns
				orderBy.append(" order by ");
				orderBy.append(order_cols);
				orderBy.append(" asc");
			}
			sqlDef.order_by = orderBy.toString();

		} finally {
			if (pstmtGetCol != null)
				try {
					pstmtGetCol.close();
				} catch (Exception e) {
				}
			if (pstmtGetTable != null)
				try {
					pstmtGetTable.close();
				} catch (Exception e) {
				}
		}

		sqlDef.sql = sql.toString();
		sqlDef.colNames = colNames;
		sqlDef.qnames = validatedQuestionNames;
		return sqlDef;
	}
	
	/*
	 * Get table details
	 */
	private void getTables(PreparedStatement pstmt, int parentId, String parentTable, StringBuffer tabs,
			StringBuffer where, HashMap<Integer, Integer> forms, ArrayList<String> subTables) throws SQLException {

		ArrayList<Integer> parents = new ArrayList<>();
		ArrayList<String> parentTables = new ArrayList<>();

		pstmt.setInt(2, parentId);
		log.info("Get tables: " + pstmt.toString());
		ResultSet rs = pstmt.executeQuery();
		while (rs.next()) {
			int fId = rs.getInt(1);
			String table = rs.getString(2);

			/*
			 * Ignore forms that where no questions have been asked for
			 */
			if (forms.get(fId) != null) {

				// Update table list
				if (tabs.length() > 0) {
					tabs.append(",");
				}
				tabs.append(table);

				// update where statement
				if (where.length() > 0) {
					where.append(" and ");
				}
				if (parentId == 0) {
					where.append(table);
					where.append("._bad = 'false'");
				} else {
					where.append(table);
					where.append(".parkey = ");
					where.append(parentTable);
					where.append(".prikey");
					subTables.add(table);
				}
				parents.add(fId);
				parentTables.add(table);
			}

		}

		for (int i = 0; i < parents.size(); i++) {
			int fId = parents.get(i);
			String table = parentTables.get(i);
			getTables(pstmt, fId, table, tabs, where, forms, subTables);
		}

	}


}
