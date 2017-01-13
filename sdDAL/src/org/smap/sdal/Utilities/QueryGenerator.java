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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.ExportForm;
import org.smap.sdal.model.OptionDesc;
import org.smap.sdal.model.SqlDesc;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.TableColumn;

/*
 * Create a query to retrieve results data
 */
public class QueryGenerator {
	
	private static Logger log =
			 Logger.getLogger(QueryGenerator.class.getName());

	public static SqlDesc gen(
			Connection connectionSD, 
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
			ArrayList<String> requiredColumns,
			ArrayList<String> namedQuestions,
			String user,
			Date startDate,
			Date endDate,
			int dateId,
			boolean superUser,
			ArrayList<ExportForm> formList,
			int formListIdx) throws Exception {
		
		SqlDesc sqlDesc = new SqlDesc();
		

		PreparedStatement pstmtCols = null;
		PreparedStatement pstmtGeom = null;
		PreparedStatement pstmtQType = null;
	
		PreparedStatement pstmtQLabel = null;
		PreparedStatement pstmtListLabels = null;
		try {

			ExportForm form = formList.get(formListIdx);
			
			
			sqlDesc.target_table = form.table;
			
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
			String sqlQType = "select q.qtype, q.readonly, q.qtext_id, q.q_id, l.name from question q " +
					" left outer join listname l " +
					" on q.l_id = l.l_id " +
					" join form f " +
					" on q.f_id = f.f_id " +
					" where f.table_name = ? " +
					" and q.column_name = ?;";
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
			getSqlDesc(
					sqlDesc, 
					sId,
					0, 
					language,
					format,
					pstmtCols,
					pstmtGeom,
					pstmtQType,
					pstmtQLabel,
					pstmtListLabels,
					urlprefix,
					wantUrl,
					exp_ro,
					labelListMap,
					connectionSD, 
					connectionResults,
					requiredColumns,
					namedQuestions,
					user,
					startDate,
					endDate,
					dateId,
					superUser,
					formList,
					formListIdx
					);
		}  finally {
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

		/*
		 * Add the tables
		 */
		for(int i = 0; i < formList.size(); i++) {
			ExportForm ef = formList.get(i);
			if(i > 0) {
				shpSqlBuf.append(",");
			}
			shpSqlBuf.append(ef.table);
		}
		
		shpSqlBuf.append(" where ");
		
		/*
		 * Exclude "bad" records
		 */
		for(int i = 0; i < formList.size(); i++) {
			ExportForm ef = formList.get(i);
			if(i > 0) {
				shpSqlBuf.append(" and ");
			}
			shpSqlBuf.append(ef.table);
			shpSqlBuf.append("._bad='false'");
		}
		
		
		if(format.equals("shape") && sqlDesc.geometry_type != null) {
			shpSqlBuf.append(" and " + sqlDesc.target_table + ".the_geom is not null");
		}
		
		/*
		 * The form list is in order of Parent to child forms
		 */
		if(formList.size() > 1) {
			for(int i = 1; i < formList.size(); i++) {
				
				shpSqlBuf.append(" and ");
				
				ExportForm form = formList.get(i);
				ExportForm prevForm = formList.get(i - 1);

				shpSqlBuf.append(prevForm.table);
				if(form.fromQuestionId > 0) {
					shpSqlBuf.append("._hrk = ");
				} else {
					shpSqlBuf.append(".prikey = ");
				}
				shpSqlBuf.append(form.table);
				if(form.fromQuestionId > 0) {
					shpSqlBuf.append(".");
					shpSqlBuf.append(GeneralUtilityMethods.getColumnNameFromId(connectionSD, form.sId, form.fromQuestionId));
				} else {
					shpSqlBuf.append(".parkey");
				}
			}
		}
		
		String sqlRestrictToDateRange = null;
		if(dateId != 0) {
			String dateName = GeneralUtilityMethods.getColumnNameFromId(connectionSD, sId, dateId);
			sqlRestrictToDateRange = GeneralUtilityMethods.getDateRange(startDate, endDate, dateName);
			if(sqlRestrictToDateRange.trim().length() > 0) {
				shpSqlBuf.append(" and ");
				shpSqlBuf.append(sqlRestrictToDateRange);
			}
		}
		
		// Add Rbac Row Filer
		boolean hasRbacFilter = false;
		ArrayList<SqlFrag> rfArray = null;
		RoleManager rm = new RoleManager();
		if(!superUser) {
			rfArray = rm.getSurveyRowFilter(connectionSD, sId, user);
			if(rfArray.size() > 0) {
				String rFilter = rm.convertSqlFragsToSql(rfArray);
				if(rFilter.length() > 0) {
					shpSqlBuf.append(" and ");
					shpSqlBuf.append(rFilter);
					hasRbacFilter = true;
				}
			}
		}
		
		/*
		 * Prepare the sql string
		 * Add the parameters to the prepared statement
		 * Convert back to sql
		 */
		PreparedStatement pstmtConvert = connectionResults.prepareStatement(shpSqlBuf.toString());
		int paramCount = 1;
		
		// if date filter is set then add it
		if(sqlRestrictToDateRange != null && sqlRestrictToDateRange.trim().length() > 0) {
			if(startDate != null) {
				pstmtConvert.setDate(paramCount++, startDate);
			}
			if(endDate != null) {
				pstmtConvert.setTimestamp(paramCount++, GeneralUtilityMethods.endOfDay(endDate));
			}
		}
		
		if(hasRbacFilter) {
			paramCount = rm.setRbacParameters(pstmtConvert, rfArray, paramCount);
		}
		
		sqlDesc.sql = pstmtConvert.toString();
		
		log.info("Generated SQL: " + shpSqlBuf);
		
		return sqlDesc;
	}
	
	/*
	 * Returns SQL to retrieve the selected form and all of its parents for inclusion in a shape file
	 *  A geometry is only returned for the level 0 form.
	 *  The order of fields is from highest form to lowest form
	 * 
	 */

	private  static void getSqlDesc(
			SqlDesc sqlDesc, 
			int sId, 
			int level, 
			String language,
			String format,
			PreparedStatement pstmtCols,
			PreparedStatement pstmtGeom,
			PreparedStatement pstmtQType,
			PreparedStatement pstmtQLabel,
			PreparedStatement pstmtListLabels,
			String urlprefix,
			boolean wantUrl,
			boolean exp_ro,
			HashMap<ArrayList<OptionDesc>, String> labelListMap,
			Connection connectionSD,
			Connection connectionResults,
			ArrayList<String> requiredColumns,
			ArrayList<String> namedQuestions,
			String user,
			Date startDate,
			Date endDate,
			int dateId,
			boolean superUser,
			ArrayList<ExportForm> formList,
			int formListIdx
			) throws SQLException {
		
		int colLimit = 10000;
		if(format.equals("shape")) {	// Shape files limited to 244 columns plus the geometry column
			colLimit = 244;
		}
		
		ExportForm form = formList.get(formListIdx);
		
		if(formListIdx > 0) {
			
			getSqlDesc(
					sqlDesc, 
					sId, 
					level + 1,
					language,
					format,
					pstmtCols, 
					pstmtGeom,
					pstmtQType,
					pstmtQLabel,
					pstmtListLabels,
					urlprefix,
					wantUrl,
					exp_ro,
					labelListMap,
					connectionSD,
					connectionResults,
					requiredColumns,
					namedQuestions,
					user,
					startDate,
					endDate,
					dateId,
					superUser,
					formList,
					formListIdx - 1
					);
		}

		ArrayList<TableColumn> cols = GeneralUtilityMethods.getColumnsInForm(
				connectionSD,
				connectionResults,
				sId,
				user,
				form.parent,
				form.fId,
				form.table,
				exp_ro,
				false,				// Don't include parent key
				false,				// Don't include "bad" columns
				false,				// Don't include instance id
				(formListIdx == 0),	// Include other meta data
				(formListIdx == 0),	// Include preloads
				(formListIdx == 0),	// Include Instance Name
				superUser,
				false		// HXL only include with XLS exports
				);
		
		StringBuffer colBuf = new StringBuffer();
		int idx = 0;
		for(TableColumn col : cols) {
			
			String name = null;
			String type = null;
			String qType = null;
			String label = null;
			String text_id = null;
			String list_name = null;
			boolean needsReplace = false;
			ArrayList<OptionDesc> optionListLabels = null;
			int qId = 0;
			
			name = col.name;
			type = col.type;
			if(GeneralUtilityMethods.isGeometry(type)) {
				type = "geometry";
			}
			
			if(name.equals("parkey") ||	name.equals("_bad") ||	name.equals("_bad_reason")
					||	name.equals("_task_key") ||	name.equals("_task_replace") ||	name.equals("_modified")
					||	name.equals("_instanceid") ||	name.equals("instanceid")) {
				continue;
			}
			
			if(type.equals("geometry") && level > 0 && !format.equals("csv")) {	// Ignore geometries in parent forms for shape, VRT, KML, Stata exports (needs to be a unique geometry)
				continue;
			}
			
			if(type.equals("geometry")) {
				String sqlGeom = "SELECT GeometryType(" + name + ") FROM " + form.table + ";";

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
						for(String namedQ : namedQuestions) {
							if(namedQ.equals(name)) {
								sqlDesc.availableColumns.add(name);
							}
						}
						break;
					} else if(name.equals("prikey") && requiredColumns.get(j).equals("_prikey_lowest")
							&& sqlDesc.gotPriKey == false && level == 0) {
						wantThisOne = true;	// Don't include in the available columns list as prikey as processed separately
						break;
					}
				}
				if(!wantThisOne) {
					continue;
				}
			} else if(name.equals("prikey") && (formListIdx != 0)) {	// Only return the primary key of the top level form
				continue;
			}
			
			if(sqlDesc.numberFields <= colLimit || type.equals("geometry")) {
				
				// Get the question type
				pstmtQType.setString(1, form.table);
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
					if(sqlDesc.geometry_type.equals("wkbPoint") && (format.equals("csv") || format.equals("stata") || format.equals("spss")) ) {		// Split location into Lon, Lat
						colBuf.append("ST_Y(" + form.table + "." + name + ") as lat, ST_X(" + form.table + "." + name + ") as lon");
						sqlDesc.colNames.add(new ColDesc("lat", type, qType, label, null, false));
						sqlDesc.colNames.add(new ColDesc("lon", type, qType, label, null, false));
					} else {																								// Use well known text
						colBuf.append("ST_AsText(" + form.table + "." + name + ") as the_geom");
						sqlDesc.colNames.add(new ColDesc("the_geom", type, qType, label, null, false));
					}
				} else {
				
					if(qType != null && (qType.equals("date") || qType.equals("dateTime"))) {
						colBuf.append("to_char(");
					} else if(type.equals("timestamptz")) {	// Return all timestamps at UTC with no time zone
						colBuf.append("timezone('UTC', "); 
					}
				
					if(isAttachment && wantUrl) {	// Add the url prefix to the file
						colBuf.append("'" + urlprefix + "' || " + form.table + "." + name);
					} else {
						colBuf.append(form.table + "." + name);
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
					if(form.surveyLevel > 0) {
						colBuf.append("_");
						colBuf.append(form.surveyLevel);	// Differentiate questions from different surveys
					}
					
					sqlDesc.colNames.add(new ColDesc(name, type, qType, label, optionListLabels, needsReplace));
				}
				
				// Add the option labels to a hashmap to ensure they are unique
				if(optionListLabels != null) {
					labelListMap.put(optionListLabels, list_name);
				}
				
				idx++;
				sqlDesc.numberFields++;
			} else {
				log.info("Warning: Field dropped during shapefile generation: " + form.table + "." + name);
			}
		}
		
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
