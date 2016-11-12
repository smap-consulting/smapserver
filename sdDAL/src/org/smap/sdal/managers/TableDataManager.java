package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.SqlFragParam;
import org.smap.sdal.model.TableColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Manage access to a single tables data as used by managed forms, browse results and the kobo api
 */
public class TableDataManager {
	
	private static Logger log =
			 Logger.getLogger(TableDataManager.class.getName());
	
	/*
	 * Get the current columns
	 */
	public JSONArray getData(
			Connection sd, 
			Connection cResults,
			ArrayList<TableColumn> columns,
			String urlprefix,
			int sId,
			String table_name,
			int parkey,
			String hrk,
			String uIdent,
			String sort,
			String dirn,
			boolean mgmt,
			boolean group,
			boolean isDt,
			int start,
			int limit,
			boolean superUser) throws SQLException, Exception  {
		
		StringBuffer columnSelect = new StringBuffer();
		boolean hasRbacFilter = false;
		ResultSet rs = null;
		JSONArray ja = new JSONArray();
		ArrayList<SqlFrag> columnSqlFrags = new ArrayList<SqlFrag> ();
		
		PreparedStatement pstmtGetData = null;
		
		try {
			for(int i = 0; i < columns.size(); i++) {
				TableColumn c = columns.get(i);
				if(i > 0) {
					columnSelect.append(",");
				}
				columnSelect.append(c.getSqlSelect(urlprefix));
				if(c.calculation != null && c.calculation.params != null) {
					columnSqlFrags.add(c.calculation);
				}
			}
			
			if(GeneralUtilityMethods.tableExists(cResults, table_name)) {
				StringBuffer sqlGetData = new StringBuffer("");
				sqlGetData.append("select ");
				sqlGetData.append(columnSelect);
				sqlGetData.append(" from ");
				sqlGetData.append(table_name);
				sqlGetData.append(" where prikey >= ? ");
				sqlGetData.append("and _bad = 'false'");
				
				// Add row selection clause
				StringBuffer sqlSelect = new StringBuffer("");
				if(parkey > 0) {
					sqlSelect.append(" and parkey = ?");
				}
				if(hrk != null) {
					sqlSelect.append(" and _hrk = ?");
				}
				
				// RBAC filter
				RoleManager rm = new RoleManager();
				ArrayList<SqlFrag> rfArray = null;
				if(!superUser) {
					rfArray = rm.getSurveyRowFilter(sd, sId, uIdent);
					if(rfArray.size() > 0) {
						String rFilter = rm.convertSqlFragsToSql(rfArray);
						if(rFilter.length() > 0) {
							sqlSelect.append(" and ");
							sqlSelect.append(rFilter);
							hasRbacFilter = true;
						}
					}
				}
				
				StringBuffer sqlGetDataOrder = new StringBuffer("");
				if(sort != null) {
					// User has requested a specific sort order
					sqlGetDataOrder.append(" order by "); 
					sqlGetDataOrder.append(getSortColumn(columns, sort));
					sqlGetDataOrder.append(" ");
					sqlGetDataOrder.append(dirn);
				} else {
					// Set default sort order
					if(mgmt) {
						sqlGetDataOrder.append(" order by prikey desc limit 10000");
					} else {
						sqlGetDataOrder.append(" order by prikey asc;");
					}
				}
				
				// Prepare statement
				StringBuffer sql = sqlGetData;
				sql.append(sqlSelect);
				sql.append(sqlGetDataOrder);
				pstmtGetData = cResults.prepareStatement(sql.toString());
				
				// Set parameters
				int paramCount = 1;
				
				// Add parameters in table column selections
				if(columnSqlFrags.size() > 0) {
					paramCount = rm.setRbacParameters(pstmtGetData, columnSqlFrags, paramCount);
				}
				pstmtGetData.setInt(paramCount++, start);
				if(parkey > 0) {
					pstmtGetData.setInt(paramCount++, parkey);
				}
				if(hrk != null) {
					pstmtGetData.setString(paramCount++, hrk);
				}
				if(hasRbacFilter) {
					paramCount = rm.setRbacParameters(pstmtGetData, rfArray, paramCount);
				}
				
				log.info("Get data: " + pstmtGetData.toString());
				rs = pstmtGetData.executeQuery();
				
				int index = 0;
				while (rs.next()) {
					
					if(limit > 0 && index >= limit) {
						break;
					}
					index++;
					
					JSONObject jr = new JSONObject();
					if(group) {
						jr.put("_group", "");				// _group for duplicate queries
					}
					for(int i = 0; i < columns.size(); i++) {	
						
						TableColumn c = columns.get(i);
						String name = null;
						String value = null;
						
						if(c.isGeometry()) {							
							// Add Geometry (assume one geometry type per table)
							String geomValue = rs.getString(i + 1);	
							name = "_geolocation";
							JSONArray coords = null;
							if(geomValue != null) {
								JSONObject jg = new JSONObject(geomValue);									
								coords = jg.getJSONArray("coordinates");
							} else {
								coords = new JSONArray();
							}
							jr.put(name, coords);
				
						} else {
							
							//String name = rsMetaData.getColumnName(i);	
							name = c.humanName;
								
							if(c.type != null && c.type.equals("decimal")) {
								Double dValue = rs.getDouble(i + 1);
								dValue = Math.round(dValue * 10000.0) / 10000.0; 
								value = String.valueOf(dValue);
							} else if(c.type != null && c.type.equals("calculate")) {
								// This calculation may be a decimal - give it a go
								String v = rs.getString(i + 1);
								if(v != null && v.indexOf('.') > -1) {
									try {
										Double dValue = rs.getDouble(i + 1);
										dValue = Math.round(dValue * 10000.0) / 10000.0; 
										value = String.valueOf(dValue);
									} catch(Exception e) {
										value = rs.getString(i + 1);	// Assume text
									}
								} else {
									value = rs.getString(i + 1);	// Assume text
								}
								
							} else {
								value = rs.getString(i + 1);
							}
							
							if(value == null) {
								value = "";
							}
							
							if(name != null ) {
								if(!isDt) {
									name = GeneralUtilityMethods.translateToKobo(name);
								}
								jr.put(name, value);
							}
						}
						
				
					}
						
					ja.put(jr);
				}
						
			} 
		} finally {
			try {if (pstmtGetData != null) {pstmtGetData.close();	}} catch (SQLException e) {	}
		}
		
		return ja;

	}
	
	/*
	 * Convert the human name for the sort column into sql
	 */
	private String getSortColumn(ArrayList<TableColumn> columns, String sort) {
		String col = "prikey";	// default to prikey
		sort = sort.trim();
		for(int i = 0; i < columns.size(); i++) {
			if(columns.get(i).humanName.equals(sort)) {
				TableColumn c = columns.get(i);

				if(c.isCalculate()) {
					col = c.calculation.sql.toString();
				} else {
					col = c.name;
				}
				break;
			}
		}
		return col;
	}
	

	
}


