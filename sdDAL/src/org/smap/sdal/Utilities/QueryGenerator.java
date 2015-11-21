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

package org.smap.sdal.Utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.SqlDesc;

/*
 * Create a query to retrieve results data
 */
public class QueryGenerator {
	
	private static Logger log =
			 Logger.getLogger(QueryGenerator.class.getName());

	public static SqlDesc gen(Connection connectionSD, 
			Connection connectionResults, 
			int sId, 
			int fId, 
			String language, 
			String format,
			String urlprefix,
			boolean wantUrl,
			boolean exp_ro,
			boolean excludeParents,
			HashMap<ArrayList<OptionDesc>, String> labelListMap,
			boolean add_record_uuid,
			boolean add_record_suid,
			String hostname,
			ArrayList<String> requiredColumns) throws Exception {
		
		SqlDesc sqlDesc = new SqlDesc();
		

		
		PreparedStatement  pstmt = null;
		PreparedStatement pstmtCols = null;
		PreparedStatement pstmtGeom = null;
		PreparedStatement pstmtQType = null;
	
		PreparedStatement pstmtQLabel = null;
		PreparedStatement pstmtListLabels = null;
		try {

			/*
			 * Get the tables / forms in this survey 
			 */
			String sql = "SELECT f_id, table_name, parentform FROM form" +
					" WHERE s_id = ? " +
					" AND f_id = ?;";	

			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, fId);
			ResultSet resultSet = pstmt.executeQuery();
			if(!resultSet.next()) {
				String msg = "Exporting survey to " + format + ", Form not found:" + sId + ":" + fId;
				log.info(msg);
				throw new Exception(msg);
			}
			
			sqlDesc.target_table = resultSet.getString("table_name");
			int parentForm = resultSet.getInt("parentform");
			
			/*
			 * Prepare the statement to get the list labels
			 */
			String sqlListLabels = "SELECT o.ovalue, t.value " +
					"FROM option o, translation t, question q " +  		
					"WHERE o.label_id = t.text_id " +
					"AND t.s_id =  ? " + 
					"AND q.q_id = ? " +
					"AND q.l_id = o.l_id " +
					"AND t.language = ? " +
					"ORDER BY o.seq;";		
			pstmtListLabels = connectionSD.prepareStatement(sqlListLabels);
			
			/*
			 *  Prepare the statement to get the question type and read only status
			 */
			String sqlQType = "select q.qtype, q.readonly, q.qtext_id, q.q_id, q.list_name from question q, form f " +
					" where q.f_id = f.f_id " +
					" and f.table_name = ? " +
					" and q.qname = ?;";
			pstmtQType = connectionSD.prepareStatement(sqlQType);
			
			/*
			 * Prepare the statement to get the question label
			 */
			String sqlQLabel = "select value from translation " +
					" where s_id = ? " +
					" and text_id = ? " +
					" and language = ? " +
					" and type = 'none'";
			pstmtQLabel = connectionSD.prepareStatement(sqlQLabel);
			
			/*
			 * Create an object describing the sql query recursively from the target table
			 */
			getSqlDesc(sqlDesc, sId,
					sqlDesc.target_table,
					parentForm,
					0, 
					language,
					format,
					pstmt, 
					pstmtCols,
					pstmtGeom,
					pstmtQType,
					pstmtQLabel,
					pstmtListLabels,
					urlprefix,
					wantUrl,
					exp_ro,
					excludeParents,
					labelListMap,
					connectionSD, 
					connectionResults,
					requiredColumns);
		} catch (Exception e) {
			throw new Exception(e.getMessage()); 
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtCols != null) {pstmtCols.close();}} catch (SQLException e) {}
			try {if (pstmtGeom != null) {pstmtGeom.close();}} catch (SQLException e) {}
			try {if (pstmtQType != null) {pstmtQType.close();}} catch (SQLException e) {}
			try {if (pstmtQLabel != null) {pstmtQLabel.close();}} catch (SQLException e) {}
			try {if (pstmtListLabels != null) {pstmtListLabels.close();}} catch (SQLException e) {}
		}
		
		StringBuffer shpSqlBuf = new StringBuffer();
		shpSqlBuf.append("select ");
		if(add_record_uuid) {
			shpSqlBuf.append("uuid_generate_v4() as _record_uuid, ");
		}
		if(add_record_suid) {		// Smap unique id, globally unique (hopefully) but same value each time query is run
			shpSqlBuf.append("'" + hostname + "_" + sqlDesc.target_table + "_' || " + sqlDesc.target_table + ".prikey as _record_suid, ");
		}
		shpSqlBuf.append(sqlDesc.cols);
		shpSqlBuf.append(" from ");

		int numTables = sqlDesc.tables.size();
		for(int i = 0; i < numTables; i++) {
			if(i > 0) {
				shpSqlBuf.append(",");
			}
			shpSqlBuf.append(sqlDesc.tables.get(i));
		}
		
		shpSqlBuf.append(" where ");
		shpSqlBuf.append(sqlDesc.tables.get(0));
		shpSqlBuf.append("._bad='false'");
		if(numTables > 1) {
			for(int i = 0; i < numTables - 1; i++) {
				
				shpSqlBuf.append(" and ");
				
				shpSqlBuf.append(sqlDesc.tables.get(i));
				shpSqlBuf.append(".prikey = ");
				shpSqlBuf.append(sqlDesc.tables.get(i+1));
				shpSqlBuf.append(".parkey");
			}
		}
		//shpSqlBuf.append(";");
		sqlDesc.sql = shpSqlBuf.toString();
		
		log.info("Generated SQL: " + shpSqlBuf);
		
		return sqlDesc;
	}
	
	/*
	 * Returns SQL to retrieve the selected form and all of its parents for inclusion in a shape file
	 *  A geometry is only returned for the level 0 form.
	 *  The order of fields is from highest form to lowest form
	 * 
	 */
	private  static void getSqlDesc(SqlDesc sqlDesc, 
			int sId, 
			String tName, 
			int parentForm, 
			int level, 
			String language,
			String format,
			PreparedStatement pstmt, 
			PreparedStatement pstmtCols,
			PreparedStatement pstmtGeom,
			PreparedStatement pstmtQType,
			PreparedStatement pstmtQLabel,
			PreparedStatement pstmtListLabels,
			String urlprefix,
			boolean wantUrl,
			boolean exp_ro,
			boolean excludeParents,
			HashMap<ArrayList<OptionDesc>, String> labelListMap,
			Connection connectionSD,
			Connection connectionResults,
			ArrayList<String> requiredColumns) throws SQLException {
		
		int colLimit = 10000;
		if(format.equals("shape")) {	// Shape files limited to 244 columns plus the geometry column
			colLimit = 244;
		}
		if(parentForm > 0 && !excludeParents) {
			pstmt.setInt(1, sId);
			pstmt.setInt(2, parentForm);
			ResultSet resultSet = pstmt.executeQuery();
			
			if (resultSet.next()) {
	
				String nextTableName = resultSet.getString("table_name");
				int nextParentForm = resultSet.getInt("parentform");
				getSqlDesc(sqlDesc, sId, nextTableName, nextParentForm, 
						level + 1,
						language,
						format,
						pstmt,
						pstmtCols, 
						pstmtGeom,
						pstmtQType,
						pstmtQLabel,
						pstmtListLabels,
						urlprefix,
						wantUrl,
						exp_ro,
						excludeParents,
						labelListMap,
						connectionSD,
						connectionResults,
						requiredColumns);
			}
		}
			
		String sql = "SELECT * FROM " + tName + " LIMIT 1;";
		
		try {if (pstmtCols != null) {pstmtCols.close();}} catch (SQLException e) {}
		pstmtCols = connectionResults.prepareStatement(sql);	 			
		ResultSet resultSet = pstmtCols.executeQuery();
		ResultSetMetaData rsMetaData = resultSet.getMetaData();
		
		StringBuffer colBuf = new StringBuffer();
		int idx = 0;
		
		for(int i = 1; i <= rsMetaData.getColumnCount(); i++) {
			
			String name = null;
			String type = null;
			String qType = null;
			String label = null;
			String text_id = null;
			String list_name = null;
			boolean needsReplace = false;
			ArrayList<OptionDesc> optionListLabels = null;
			int qId = 0;
			
			name = rsMetaData.getColumnName(i);
			type = rsMetaData.getColumnTypeName(i);
			
			if(name.equals("parkey") ||	name.equals("_bad") ||	name.equals("_bad_reason")
					||	name.equals("_task_key") ||	name.equals("_task_replace") ||	name.equals("_modified")
					||	name.equals("_instanceid") ||	name.equals("instanceid")) {
				continue;
			}
			
			if(type.equals("geometry") && level > 0 && !format.equals("csv")) {	// Ignore geometries in parent forms for shape, VRT, KML, Stata exports (needs to be a unique geometry)
				continue;
			}
			
			if(type.equals("geometry")) {
				String sqlGeom = "SELECT GeometryType(" + name + ") FROM " + tName + ";";

				pstmtGeom = connectionResults.prepareStatement(sqlGeom);
				log.info("Get geometry type: " + pstmtGeom.toString());
				ResultSet rsGeom = pstmtGeom.executeQuery();
				
				/*
				 *  The GeometryType function will return null if a valid geometry was not created
				 *  Hence continue to the first valid geometry
				 */
				while (rsGeom.next()) {
					String geomName = rsGeom.getString(1);
					if(geomName == null) {
						continue;
					} else {
						if(geomName.equals("POLYGON")) {
							sqlDesc.geometry_type = "wkbPolygon";
						} else if(geomName.startsWith("LINESTRING")) {
							sqlDesc.geometry_type = "wkbLineString";
						} else if(geomName.startsWith("POINT")) {
							sqlDesc.geometry_type = "wkbPoint";
						} else {
							log.info("Error unknown geometry:  " + geomName);
						}
						break;
					}
				}
			} 
			
			if(requiredColumns != null) {
				boolean wantThisOne = false;
				for(int j = 0; j < requiredColumns.size(); j++) {
					if(requiredColumns.get(j).equals(name)) {
						wantThisOne = true;
						break;
					} else if(name.equals("prikey") && requiredColumns.get(j).equals("_prikey_highest")
							&& sqlDesc.gotPriKey == false) {
						wantThisOne = true;
						break;
					}
				}
				if(!wantThisOne) {
					continue;
				}
			} else if(name.equals("prikey") && parentForm > 0) {	// Only return the primary key of the top level survey form
				continue;
			}
			
			if(sqlDesc.numberFields <= colLimit || type.equals("geometry")) {
				
				// Get the question type
				pstmtQType.setString(1, tName);
				if(name.contains("__")) {
					// Select multiple question
					String [] mNames = name.split("__");
					pstmtQType.setString(2, mNames[0]);
				} else {
					pstmtQType.setString(2, name);
				}
				ResultSet rsType = pstmtQType.executeQuery();
				
				boolean isAttachment = false;
				if(rsType.next()) {
					qType = rsType.getString(1);
					boolean ro = rsType.getBoolean(2);
					text_id = rsType.getString(3);
					qId = rsType.getInt(4);
					list_name = rsType.getString(5);
					if(list_name == null) {
						list_name = name;		// Default list name to question name if it has not been set
					}
					list_name = validStataName(list_name);	// Make sure the list name is a valid stata name
					if(!exp_ro && ro) {
						log.info("Dropping readonly: " + name);
						continue;			// Drop read only columns if they are not selected to be exported				
					}
					
					// Set flag if this question has an attachment
					if(qType.equals("image") || qType.equals("audio") || qType.equals("video")) {
						isAttachment = true;
					}
				}
				
				/*
				 * Get the labels if language has been specified
				 */
				if(!language.equals("none")) {
					label = getQuestionLabel(pstmtQLabel, sId, text_id, language);
					// Get the list labels if this is a select question
					if(qType != null && qType.equals("select1")) {
						optionListLabels = new ArrayList<OptionDesc> ();
						needsReplace = getListLabels(pstmtListLabels, sId, qId, language, optionListLabels);
					}
				}
				
				if(idx > 0) {
					colBuf.append(",");
				}
				
				/*
				 * Specify SQL functions
				 */
				if(sqlDesc.geometry_type != null && type.equals("geometry") && (format.equals("vrt") || format.equals("csv") || format.equals("stata") || format.equals("thingsat"))) {
					//colBuf.append("ST_AsText(");
					if(sqlDesc.geometry_type.equals("wkbPoint") && (format.equals("csv") || format.equals("stata")) ) {		// Split location into Lon, Lat
						colBuf.append("ST_Y(" + tName + "." + name + ") as lat, ST_X(" + tName + "." + name + ") as lon");
						sqlDesc.colNames.add(new ColDesc("lat", type, qType, label, null, false));
						sqlDesc.colNames.add(new ColDesc("lon", type, qType, label, null, false));
					} else {																								// Use well known text
						colBuf.append("ST_AsText(" + tName + "." + name + ") as the_geom");
						sqlDesc.colNames.add(new ColDesc("the_geom", type, qType, label, null, false));
					}
				} else {
				
					if(qType != null && (qType.equals("date") || qType.equals("dateTime"))) {
						colBuf.append("to_char(");
					} else if(type.equals("timestamptz")) {	// Return all timestamps at UTC with no time zone
						colBuf.append("timezone('UTC', "); 
					}
				
					if(isAttachment && wantUrl) {	// Add the url prefix to the file
						colBuf.append("'" + urlprefix + "' || " + tName + "." + name);
					} else {
						colBuf.append(tName + "." + name);
					}
				
					if(qType != null && qType.equals("date")) {
						colBuf.append(", 'YYYY-MM-DD')");
					} else if(qType != null && qType.equals("dateTime")) {
						colBuf.append(", 'YYYY-MM-DD HH24:MI:SS')");
					} else if(type.equals("timestamptz")) { 
						colBuf.append(")");
					}

					/*
					 * Specify the name of the returned variable
					 */
					colBuf.append(" as ");
					
					// Now any modifications to name will only apply to the output name not the column name
					if(format.equals("shape") && name.startsWith("_")) {
						name = name.replaceFirst("_", "x");	// Shape files must start with alpha's
					}
					colBuf.append(name);
					
					sqlDesc.colNames.add(new ColDesc(name, type, qType, label, optionListLabels, needsReplace));
				}
				

				
				// Add the option labels to a hashmap to ensure they are unique
				if(optionListLabels != null) {
					labelListMap.put(optionListLabels, list_name);
				}
				
				idx++;
				sqlDesc.numberFields++;
			} else {
				System.out.println("Warning: Field dropped during shapefile generation: " + tName + "." + name);
			}
		}
		
		sqlDesc.tables.add(tName);
		if(sqlDesc.cols == null) {
			sqlDesc.cols = colBuf.toString();
		} else {
			if(colBuf.toString().trim().length() != 0) {	// Ignore tables with no columns
				sqlDesc.cols += "," + colBuf.toString();
			}
		}
		
	}
	
	/*
	 * Make sure the name is valid for Stata export
	 */
	private static String validStataName(String in) {
		String out = in;
		// Verify that the name does not start with a number or underscore
		if(Character.isDigit(out.charAt(0)) || out.charAt(0) == '_' ) {
			out = "a" + out;
		}
		return out;
	}
	
	/*
	 * Get the label for a question
	 */
	private static String getQuestionLabel(PreparedStatement pstmt,int sId, String text_id, String language) throws SQLException {
		String label = null;
		
		pstmt.setInt(1, sId);
		pstmt.setString(2, text_id);
		pstmt.setString(3, language);
		
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			label = rs.getString(1);
		}
		return label;
	}
	
	/*
	 * For Stata get the labels that apply to a list 
	 */
	private static boolean getListLabels(PreparedStatement pstmt, int sId, int qId, 
			String language, ArrayList<OptionDesc> optionLabels) throws SQLException {
		
		pstmt.setInt(1, sId);
		pstmt.setInt(2, qId);
		pstmt.setString(3, language);
		
		boolean replacementsRequired = false;
		int maxValue = Integer.MIN_VALUE;
		
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			OptionDesc od = new OptionDesc();
			od.value = rs.getString(1);
			od.label = rs.getString(2);
			od.replace = false;
			
			// Attempt to set a numeric value for the option
			try {
				od.num_value = Integer.parseInt(od.value);
				if(maxValue < od.num_value) {
					maxValue = od.num_value;
				}
			} catch (NumberFormatException e) {
				replacementsRequired = true;
				od.replace = true;
			}
			
			optionLabels.add(od);
		}
		
		/*
		 * If we need to replace some non integer values lets start from the maximum existing integer value
		 */
		if(replacementsRequired) {
			if(maxValue < 0) {
				maxValue = 0;	// Start from a minimum of 1
			}
			for(int i = 0; i  < optionLabels.size(); i++) {
				OptionDesc od = optionLabels.get(i);
				if(od.replace) {
					od.num_value = ++maxValue;
				}
			}
		}
		
		return replacementsRequired;
	}
}
