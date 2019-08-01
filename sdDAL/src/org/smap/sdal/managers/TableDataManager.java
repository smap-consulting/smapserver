package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.model.AuditItem;
import org.smap.sdal.model.GeoPoint;
import org.smap.sdal.model.KeyFilter;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.RoleColumnFilter;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.SqlParam;
import org.smap.sdal.model.TableColumn;

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
 * Manage access to a single tables data as used by managed forms, browse
 * results and the kobo api
 */
public class TableDataManager {

	private static Logger log = Logger.getLogger(TableDataManager.class.getName());
	private static ResourceBundle localisation;
	private String tz;
	
	public TableDataManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
	}

	/*
	 * Get the prepared statment to retrieve the data
	 */
	public PreparedStatement getPreparedStatement(
			Connection sd, 
			Connection cResults, 
			ArrayList<TableColumn> columns,
			String urlprefix, 
			int sId, 
			String table_name, 
			int parkey, 
			String hrk, 
			String uIdent, 
			ArrayList<Role> roles,
			String sort,
			String dirn, 
			boolean mgmt, 
			boolean group, 
			boolean isDt, 
			int start, 
			boolean getParkey,
			int start_parkey, 
			boolean superUser, 
			boolean specificPrikey, 
			String include_bad,
			String customFilter,
			ArrayList<KeyFilter> keyFilters,
			String tz,
			String instanceId,
			String advanced_filter,
			String dateName,
			Date startDate,
			Date endDate)
			throws SQLException, Exception {

		StringBuffer columnSelect = new StringBuffer();
		boolean hasRbacFilter = false;
		ArrayList<SqlFrag> columnSqlFrags = new ArrayList<SqlFrag>();
		ArrayList<SqlParam> params = new ArrayList<> ();

		PreparedStatement pstmt = null;

		for (int i = 0; i < columns.size(); i++) {
			TableColumn c = columns.get(i);
			if (i > 0) {
				columnSelect.append(",");
			}
			columnSelect.append(c.getSqlSelect(urlprefix, tz, params));
			if (c.calculation != null && c.calculation.params != null) {
				columnSqlFrags.add(c.calculation);
			}
		}
		
		if (GeneralUtilityMethods.tableExists(cResults, table_name)) {
			StringBuffer sqlGetData = new StringBuffer("");
			sqlGetData.append("select ");
			sqlGetData.append(columnSelect);
			sqlGetData.append(" from ");
			sqlGetData.append(table_name);
			if (specificPrikey) {
				sqlGetData.append(" where prikey = ? ");
			} else {
				sqlGetData.append(" where prikey >= ? ");
			}
			if (getParkey) {
				sqlGetData.append(" and parkey >= ?");
			}

			if (include_bad.equals("none")) {
				sqlGetData.append(" and _bad = 'false' ");
			} else if (include_bad.equals("only")) {
				sqlGetData.append(" and _bad = 'true' ");
			}

			// Add row selection clause
			StringBuffer sqlSelect = new StringBuffer("");
			if (parkey > 0) {
				sqlSelect.append(" and parkey = ?");
			}
			if (hrk != null) {
				sqlSelect.append(" and _hrk = ?");
			}
			if (instanceId != null) {
				sqlSelect.append(" and instanceid = ?");
			}

			// RBAC filter
			RoleManager rm = new RoleManager(localisation);
			ArrayList<SqlFrag> rfArray = new ArrayList<SqlFrag> ();
			if (!superUser) {			
				
				if(uIdent != null) {
					rfArray = rm.getSurveyRowFilter(sd, sId, uIdent);
				} else if(roles != null) {
					rfArray = rm.getSurveyRowFilterRoleList(sd, sId, roles);
				}
				
				if (rfArray.size() > 0) {
					String rFilter = rm.convertSqlFragsToSql(rfArray);
					if (rFilter.length() > 0) {
						sqlSelect.append(" and ");
						sqlSelect.append(rFilter);
						hasRbacFilter = true;
					}
				}
			}
			
			// Add custom filter (No parameters not settable by users)
			if(customFilter != null) {
				sqlSelect.append(" and (").append(customFilter).append(")");
			}
			
			String sqlRestrictToDateRange = null;
			if(dateName != null && GeneralUtilityMethods.hasColumn(cResults, table_name, dateName)) {
				sqlRestrictToDateRange = GeneralUtilityMethods.getDateRange(startDate, endDate, dateName);
				if(sqlRestrictToDateRange.trim().length() > 0) {
					sqlSelect.append(" and ");
					sqlSelect.append(sqlRestrictToDateRange);
				}
			}
			
			/*
			 * Convert advanced filter into SQL and validate
			 */
			SqlFrag filterFrag = null;
			if(advanced_filter != null && advanced_filter.length() > 0) {
	
				filterFrag = new SqlFrag();
				filterFrag.addSqlFragment(advanced_filter, false, localisation);
	
	
				for(String filterCol : filterFrag.columns) {
					boolean valid = false;
					for(TableColumn tc : columns) {
						if(filterCol.equals(tc.column_name)) {
							valid = true;
							break;
						}
					}
					if(!valid) {
						String msg = localisation.getString("inv_qn_misc");
						msg = msg.replace("%s1", filterCol);
						throw new Exception(msg);
					}
				}
			}
			if(filterFrag != null) {
				sqlSelect.append(" and (").append(filterFrag.sql).append(")");
			}
			
			
			// Add key filters
			if(keyFilters != null) {
				for(KeyFilter kf : keyFilters) {
					kf.type = getColumnType(columns, kf.name);
					if(kf.type != null) {	// If type is null the column was not found
						sqlSelect.append(" and ").append(kf.name + " = ?");
					}
				}
			}

			StringBuffer sqlGetDataOrder = new StringBuffer("");
			if (sort != null) {
				// User has requested a specific sort order
				sqlGetDataOrder.append(" order by ");
				sqlGetDataOrder.append(getSortColumn(columns, sort));
				sqlGetDataOrder.append(" ");
				sqlGetDataOrder.append(dirn);
			} else {
				// Set default sort order
				if (mgmt) {
					sqlGetDataOrder.append(" order by prikey desc limit 10000");
				} else {
					sqlGetDataOrder.append(" order by prikey asc;");
				}
			}

			// Prepare statement
			StringBuffer sql = sqlGetData;
			sql.append(sqlSelect);
			sql.append(sqlGetDataOrder);
			pstmt = cResults.prepareStatement(sql.toString());

			// Set parameters
			int paramCount = 1;

			// Parameters in select clause
			paramCount = GeneralUtilityMethods.addSqlParams(pstmt, paramCount, params);
			
			// Add parameters in table column selections
			if (columnSqlFrags.size() > 0) {
				paramCount = GeneralUtilityMethods.setArrayFragParams(pstmt, columnSqlFrags, paramCount, tz);
			}
			pstmt.setInt(paramCount++, start);
			if (getParkey) {
				pstmt.setInt(paramCount++, start_parkey);
			}
			if (parkey > 0) {
				pstmt.setInt(paramCount++, parkey);
			}
			if (hrk != null) {
				pstmt.setString(paramCount++, hrk);
			}
			if (instanceId != null) {
				pstmt.setString(paramCount++, instanceId);
			}
			if (hasRbacFilter) {
				paramCount = GeneralUtilityMethods.setArrayFragParams(pstmt, rfArray, paramCount, tz);
			}
			
			// if date filter is set then add it
			if(sqlRestrictToDateRange != null && sqlRestrictToDateRange.trim().length() > 0) {
				if(startDate != null) {
					pstmt.setTimestamp(paramCount++, GeneralUtilityMethods.startOfDay(startDate, tz));
				}
				if(endDate != null) {
					pstmt.setTimestamp(paramCount++, GeneralUtilityMethods.endOfDay(endDate, tz));
				}
			}
						
			if(filterFrag != null) {
				paramCount = GeneralUtilityMethods.setFragParams(pstmt, filterFrag, paramCount, tz);
			}
			
			// Add key filter parameters
			if(keyFilters != null) {
				for(KeyFilter kf : keyFilters) {
					if(kf.type != null) {
						kf.setFilter(pstmt, paramCount++);
					}
				}
			}

		} else {
			// throw new ApplicationException("Table does not exist");
			// Its OK just return a null statement
		}
		return pstmt;

	}

	/*
	 * Get the data
	 */
	public JSONArray getData(
			PreparedStatement pstmt,
			ArrayList<TableColumn> columns, 
			String urlprefix, 
			boolean group, 
			boolean isDt, 
			int limit)
			throws SQLException, Exception {

		ResultSet rs = null;
		JSONArray ja = new JSONArray();

		rs = pstmt.executeQuery();
		int index = 0;
		while (rs.next()) {

			if (limit > 0 && index >= limit) {
				break;
			}
			index++;

			JSONObject jr = new JSONObject();
			if (group) {
				jr.put("_group", ""); // _group for duplicate queries
			}
			for (int i = 0; i < columns.size(); i++) {

				TableColumn c = columns.get(i);
				String name = null;
				String value = null;

				if (c.isGeometry()) {
					// Add Geometry (assume one geometry type per table)
					String geomValue = rs.getString(i + 1);
					if (geomValue == null) {
						geomValue = "{}";
					}
					name = "_geolocation";
					/*
					 * JSONArray coords = null; if(geomValue != null) { JSONObject jg = new
					 * JSONObject(geomValue); coords = jg.getJSONArray("coordinates"); } else {
					 * coords = new JSONArray(); }
					 */

					jr.put(name, new JSONObject(geomValue));

				} else {

					// String name = rsMetaData.getColumnName(i);
					// name = c.humanName;
					name = c.question_name;

					if (c.type != null && c.type.equals("decimal")) {
						Double dValue = rs.getDouble(i + 1);
						dValue = Math.round(dValue * 10000.0) / 10000.0;
						value = String.valueOf(dValue);
					} else if (c.type.equals("dateTime")) {
						value = rs.getString(i + 1);
						if (value != null) {
							value = value.replaceAll("\\.[0-9]+", ""); // Remove milliseconds
						}
					} else if (c.type != null && c.type.equals("calculate")) {
						// This calculation may be a decimal - give it a go
						String v = rs.getString(i + 1);
						if (v != null && v.indexOf('.') > -1) {
							try {
								Double dValue = rs.getDouble(i + 1);
								dValue = Math.round(dValue * 10000.0) / 10000.0;
								value = String.valueOf(dValue);
							} catch (Exception e) {
								value = rs.getString(i + 1); // Assume text
							}
						} else {
							value = rs.getString(i + 1); // Assume text
						}

					} else {
						value = rs.getString(i + 1);
					}

					if (value == null) {
						value = "";
					}

					if (name != null) {
						if (!isDt) {
							name = GeneralUtilityMethods.translateToKobo(name);
						}
						jr.put(name, value);
					}
				}

			}

			ja.put(jr);
		}

		return ja;

	}
	
	/*
	 * Get the next record of data
	 */
	public JSONObject getNextRecord(
			Connection sd,
			ResultSet rs,
			ArrayList<TableColumn> columns, 
			String urlprefix, 
			boolean group, 
			boolean isDt, 
			int limit,
			boolean mergeSelectMultiple,
			boolean isGeoJson,
			boolean links,
			String sIdent)
			throws SQLException, Exception {

		JSONObject jr = null;
		JSONObject jp = null;
		JSONObject jf = null;
		JSONObject jl = null;	// links
		JSONObject jGeom = null;
		String id = null;
		
		String uuid = null;
		
		if (rs.next()) {

			jr = new JSONObject();
			if (group) {
				jr.put("_group", ""); // _group for duplicate queries
			}
			if(isGeoJson) {
				jr.put("type", "Feature");
				jp = new JSONObject();
				jr.put("properties", jp);
				jf = jp;
			} else {
				jf = jr;
			}
			for (int i = 0; i < columns.size(); i++) {

				TableColumn c = columns.get(i);
				String name = null;
				String value = null;
				JSONObject jsonAudit = null;

				if (c.isGeometry()) {
					// Add Geometry (assume one geometry type per table)
					String geomValue = rs.getString(i + 1);
					if (geomValue == null) {
						geomValue = "{}";
					}
					if(isGeoJson) {
						jGeom = new JSONObject(geomValue);
					} else {
						name = "_geolocation";
						jf.put(name, new JSONObject(geomValue));
					}

				} else if(c.type != null && c.type.equals("select") && c.compressed && !mergeSelectMultiple) {
					// Split the select multiple into its choices
					name = c.displayName;
					value = rs.getString(i + 1);
					if (value == null) {
						value = "";
					}
					String[] selected = {""};
					selected = value.split(" ");
					for(KeyValue kv: c.choices) {
						String choiceName = null;
						if(c.selectDisplayNames) {
							choiceName = kv.v;
						} else {
							choiceName = name + " - " + kv.v;
						}
						boolean addChoice = false;
						for(String selValue : selected) {
							if(selValue.equals(kv.k)) {
								addChoice = true;
								break;
							}	
						}
						String choiceValue = addChoice ? "1" : "0";
						jf.put(choiceName, choiceValue);
					}
				} else {

					name = c.displayName;

					if (c.type != null && c.type.equals("select1") && c.selectDisplayNames) {
						// Convert value to display name
						value = rs.getString(i + 1);
						for(KeyValue kv: c.choices) {
							if(kv.k.equals(value)) {
								value = kv.v;
								break;
							}
						}
					} else if (c.type != null && c.type.equals("decimal")) {
						Double dValue = rs.getDouble(i + 1);
						dValue = Math.round(dValue * 10000.0) / 10000.0;
						value = String.valueOf(dValue);
					} else if (c.type.equals("dateTime")) {
						value = rs.getString(i + 1);
						if (value != null) {
							value = value.replaceAll("\\.[0-9]+", ""); // Remove milliseconds
						}
					} else if (c.type != null && c.type.equals("calculate")) {
						// This calculation may be a decimal - give it a go
						String v = rs.getString(i + 1);
						if (v != null && v.indexOf('.') > -1) {
							try {
								Double dValue = rs.getDouble(i + 1);
								dValue = Math.round(dValue * 10000.0) / 10000.0;
								value = String.valueOf(dValue);
							} catch (Exception e) {
								value = rs.getString(i + 1); // Assume text
							}
						} else {
							value = rs.getString(i + 1); // Assume text
						}

					} else if(c.type.equals(SmapQuestionTypes.AUDIT)) {
						value = "{\"" + name + "\":" + rs.getString(i + 1) + "}";
						jsonAudit = new JSONObject(value);
					} else {
						
						if(c.column_name.equals(SmapServerMeta.SURVEY_ID_NAME)) {
							value = GeneralUtilityMethods.getSurveyName(sd,  rs.getInt(i + 1));	// Convert survey id into survey name
						} else {
							value = rs.getString(i + 1);
						}
					}

					
					if (value == null) {
						value = "";
					}

					if (name != null) {
						if (!isDt) {
							name = GeneralUtilityMethods.translateToKobo(name);
							if(name.equals("uuid")) {
								// remember this to generate links
								uuid = value;
							}
						}
						if(isGeoJson && name.equals("prikey")) {
							id = value;
						} else {
							if(c.type.equals(SmapQuestionTypes.AUDIT)) {
								jf.put(name, jsonAudit.get(name));
							} else {
								jf.put(name, value);
							}
						}
					}
				}

			}
			
			if(links) {
				jl = new JSONObject();
				
				// Link to data structure used by tasks
				jl.put("data", GeneralUtilityMethods.getInitialDataLink(
						urlprefix, 
						sIdent, 
						"survey",
						0,		// task id - not needed for survey data
						uuid));
				
				// Link to pdf
				jl.put("pdf", GeneralUtilityMethods.getPdfLink(
						urlprefix, 
						sIdent, 
						uuid,
						tz));
				
				// Link to webform
				jl.put("webform", GeneralUtilityMethods.getWebformLink(
						urlprefix, 
						sIdent, 
						uuid));
				
				// Link to audit form
				jl.put("audit_log", GeneralUtilityMethods.getAuditLogLink(
						urlprefix, 
						sIdent, 
						uuid));
				
				jf.put("links", jl);
			}

		}

		if(jGeom != null) {
			jr.put("geometry", jGeom);
		}
		if(id != null) {
			jr.put("id", id);
		}
		return jr;

	}
	
	/*
	 * Return the audit records for this submission record
	 */
	public ArrayList<JSONObject> getNextAuditRecords(
			Connection sd,
			int sId,
			ResultSet rs,
			ArrayList<TableColumn> columns, 
			String urlprefix, 
			int limit,
			boolean mergeSelectMultiple,
			boolean isGeoJson)
			throws SQLException, Exception {

		HashMap<String, AuditItem> auditData = null;
		Type auditItemType = new TypeToken<HashMap<String, AuditItem>>() {}.getType();
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		
		ArrayList<JSONObject> auditRecords = null;
		JSONObject jr = null;
		JSONObject jp = null;
		JSONObject jf = null;
		JSONObject jGeom = null;
		String id = null;
		
		if (rs.next()) {

			auditData = gson.fromJson(rs.getString("_audit"), auditItemType);
			auditRecords = new ArrayList<JSONObject> ();		// Always return non null if there is a recod
			
			if(auditData != null) {
				
				for(String question : auditData.keySet()) {
					
					jr = new JSONObject();
					jr.put("type", "Feature");
					jp = new JSONObject();
					
					String columnName = GeneralUtilityMethods.getColumnName(sd, sId, question);	
					String value = "";
					if(columnName != null) {
						value = rs.getString(columnName);		// TODO need to get column name	

					
						AuditItem ai = auditData.get(question);
						
						// Set geometry
						GeoPoint gp = ai.location;
						StringBuffer geomValue = new StringBuffer("");
						if(gp == null) {
							gp = new GeoPoint(0.0, 0.0);
						} 
						geomValue.append("{")
								.append("\"type\": \"Point\",")
								.append("\"coordinates\":[")
								.append(gp.lon)
								.append(", ")
								.append(gp.lat)
								.append("]")
								.append("}");
	
						jGeom = new JSONObject(geomValue.toString());
						
						// Add properties
						jp.put("question", question);
						jp.put("value", value);
						jp.put("time_spent", ai.time);
						jp.put("prikey", rs.getInt("prikey"));
						jp.put("user", rs.getString("_user"));
						jp.put("start", rs.getString("_start"));
						jp.put("end", rs.getString("_end"));
						jp.put("device", rs.getString("_device"));
						
						jr.put("properties", jp);
						jr.put("geometry", jGeom);
						
						auditRecords.add(jr);
					}
				}
				
			} 

		}
		
		return auditRecords;

	}

	/*
	 * Convert the human name for the sort column into sql
	 */
	private String getSortColumn(ArrayList<TableColumn> columns, String sort) {
		String col = "prikey"; // default to prikey
		sort = sort.trim();
		for (int i = 0; i < columns.size(); i++) {
			String name = columns.get(i).question_name;
			if (name.equals(sort)) {
				TableColumn c = columns.get(i);

				if (c.isCalculate()) {
					col = c.calculation.sql.toString();
				} else {
					col = c.column_name;
				}
				break;
			}
		}
		return col;
	}
	
	/*
	 * Convert the question name for the sort column into sql
	 */
	private String getColumnType(ArrayList<TableColumn> columns, String name) {
		String type = null; 
		name = name.trim();
		if(name.equals("prikey") || name.equals("parkey")) {
			type = "int";
		} else {
			for (int i = 0; i < columns.size(); i++) {
				if (columns.get(i).question_name.equals(name)) {
					TableColumn c = columns.get(i);
					type = c.type;
					break;
				}
			}
		}
		return type;
	}

}
