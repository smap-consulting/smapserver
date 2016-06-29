package utilities;

/*
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

*/

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;

public class QuestionInfo {
	
	private static Logger log =
			 Logger.getLogger(QuestionInfo.class.getName());
	
	private boolean isGeom;
	private int sId;
	private int qId;
	private String tableName;
	private int fId;
	private int parentFId;	// parent form id
	private String qName;
	private String columnName;
	private String qLabel;
	private String qType;
	private String qCalculate = null;
	private String qAppearance = null;
	private boolean qExternalChoices = false;
	private boolean isCalc;
	private String fn = null;
	private String units = null;
	private String urlprefix = null;		// Added to attachments to complete url
	private ArrayList<OptionInfo> o = null;	// Option array if this is a select / select1 question
	
	/*
	 * Normal complete constructor
	 */
	public QuestionInfo(int surveyId, 
			int questionId, 
			Connection connection, 
			boolean isGeomDeprecated, 
			String lang, 
			String urlprefix) throws SQLException {	
		
		//this.isGeom = isGeom;			Don't rely on isGeom parameter
		this.urlprefix = urlprefix;
		qId = questionId;
		sId = surveyId;
		
		
		String sql = "SELECT f.table_name, f.f_id, f.parentform, q.qname, q.column_name, q.qtype, t.value, q.calculate, q.appearance" + 
				" FROM survey s " +
				" INNER JOIN form f " +
				" ON s.s_id = f.s_id" +
				" INNER JOIN question q " +
				" ON f.f_id = q.f_id " + 
				" LEFT OUTER JOIN translation t " +
				" ON q.qtext_id = t.text_id " +
				" AND t.language = ? " +
				" AND t.s_id = s.s_id" +
				" WHERE s.s_id = ?" +
				" AND q.q_id = ?";		

		PreparedStatement pstmt = null;
		
		try {
			pstmt = connection.prepareStatement(sql);	
			pstmt.setString(1, lang);
			pstmt.setInt(2, sId);
			pstmt.setInt(3, qId);
	
			log.info("SQL: Normal Complete Constructor for QuestionInfo: " + pstmt.toString());
			ResultSet resultSet = pstmt.executeQuery();
			
			if(resultSet.next()) {
				tableName = resultSet.getString(1);
				fId = resultSet.getInt(2);
				parentFId = resultSet.getInt(3);
				qName = resultSet.getString(4);
				columnName = resultSet.getString(5);
				qType = resultSet.getString(6);
				qLabel = resultSet.getString(7);
				qCalculate = resultSet.getString(8);
				qAppearance = resultSet.getString(9);		// Used to determine if choices come from external file
				
				log.info("Table:"  + tableName + ":" + fId + ":" + parentFId + ":" + qName + ":" + qType + " : " + 
						qLabel + ":" + qCalculate + ":" + qAppearance );
				
				/*
				 * Polygon and linestring questions have "_parentquestion" removed from their name
				 */
				if(qType.equals("geolinestring") || qType.equals("geopolygon")) {
					int idx = columnName.toLowerCase().lastIndexOf("_parentquestion");
					if(idx > 0) {
						columnName = columnName.substring(0, idx);
					}
					isGeom = true;
				} else if(qType.equals("geopoint") || qType.equals("geoshape") || qType.equals("geotrace")) {
					isGeom = true;
				}
				
				/*
				 * Get the question's option information
				 */
				if(qType.startsWith("select")) {
					o = new ArrayList<OptionInfo> ();
					
					qExternalChoices = GeneralUtilityMethods.isAppearanceExternalFile(qAppearance);
					
					sql = "SELECT o.oValue, t.value, t.type, o.column_name" + 
							" FROM option o, question q, translation t" +
							" WHERE q.q_id = ?" + 
							" AND o.label_id = t.text_id" +
							" AND t.language = ? " +
							" AND t.type != 'image' AND t.type != 'video' AND t.type != 'audio' " +	// Temporarily ignore 
							" AND t.s_id = ?" +
							" AND o.l_id = q.l_id" +
							" AND o.externalfile = ?" +
							" ORDER BY o.seq";
					
					if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
					pstmt = connection.prepareStatement(sql);
					pstmt.setInt(1,  qId);
					pstmt.setString(2, lang);
					pstmt.setInt(3, sId);
					pstmt.setBoolean(4,  qExternalChoices);
					
					log.info("Getting options for question: " + pstmt.toString());
					resultSet = pstmt.executeQuery();
					
					boolean select = false;
					if(qType.equals("select")) {
						select = true;
					}
					while(resultSet.next()) {
						String value = resultSet.getString(1);
						String label = resultSet.getString(2);
						String type = resultSet.getString(3);
						String oColumnName = resultSet.getString(4);
						if(select) {
							oColumnName = columnName + "__" + oColumnName;
						} 
						o.add(new OptionInfo(value, label, type, oColumnName));
					}
				}
				
			} else {
				log.info("Error (QuetionInfo.java) retrieving question data for survey: " + surveyId + " and question: " + questionId);
			}	
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
		}

	}
	
	/*
	 * Constructor where language is not known
	 */
	public QuestionInfo(int surveyId, int questionId, Connection connection) throws SQLException {	

		qId = questionId;
		sId = surveyId;
		
		
		String sql = "SELECT f.table_name, f.f_id, f.parentform, q.qname, q.qtype, q.calculate, q.column_name" + 
				" FROM survey s " +
				" INNER JOIN form f " +
				" ON s.s_id = f.s_id" +
				" INNER JOIN question q " +
				" ON f.f_id = q.f_id " + 
				" WHERE s.s_id = ?" +
				" AND q.q_id = ?";
			
		log.info(sql);
		PreparedStatement pstmt = connection.prepareStatement(sql);	
		pstmt.setInt(1, sId);
		pstmt.setInt(2, qId);

		ResultSet resultSet = pstmt.executeQuery();
		
		if(resultSet.next()) {
			tableName = resultSet.getString(1);
			fId = resultSet.getInt(2);
			parentFId = resultSet.getInt(3);
			qName = resultSet.getString(4);
			qType = resultSet.getString(5);
			qCalculate = resultSet.getString(6);
			columnName = resultSet.getString(7);
			
			log.info("Table:"  + tableName + ":" + fId + ":" + parentFId + ":" + qName + ":" + qType);
			
			/*
			 * Polygon and linestring questions have "_parentquestion" removed from their name
			 */
			if(qType.equals("geolinestring") || qType.equals("geopolygon")) {
				int idx = columnName.toLowerCase().lastIndexOf("_parentquestion");
				if(idx > 0) {
					columnName = columnName.substring(0, idx);
				}
			}
			
			/*
			 * Get the question's option information
			 */
			if(qType.startsWith("select")) {
				o = new ArrayList<OptionInfo> ();
				
				sql = "SELECT o.oValue, o.column_name" + 
						" FROM option o, question q" +
						" WHERE q.q_id = ?" + 
						" AND o.l_id = q.l_id" +
						" ORDER BY o.seq";
				
				pstmt = connection.prepareStatement(sql);
				pstmt.setInt(1,  qId);
				log.info("Getting options for question " + pstmt.toString());
				
				resultSet = pstmt.executeQuery();
				
				boolean select = false;
				if(qType.equals("select")) {
					select = true;
				}
				while(resultSet.next()) {
					String value = resultSet.getString(1);
					String oColumnName = resultSet.getString(2);
					if(select) {
						oColumnName = columnName + "__" + oColumnName;
					} 
					o.add(new OptionInfo(value, null, null, oColumnName));
				}
			}
			
		} else {
			log.info("Error (QuetionInfo.java) retrieving question data for survey: " + surveyId + " and question: " + questionId);
		}		

	}
	
	/*
	 * Constructor for server side calculate questions
	 */
	public QuestionInfo(int surveyId, int questionId, Connection connection, 
			boolean isGeom, String lang, boolean isCalc, String urlprefix) throws SQLException {	
		this.isGeom = isGeom;
		this.urlprefix = urlprefix;
		this.isCalc = isCalc;
		qId = questionId;
		sId = surveyId;
		
		String sql = "SELECT f.table_name, f.f_id, f.parentform, ssc.name, ssc.function, ssc.units, ssc.column_name" + 
				" FROM form f, ssc ssc " +
				" where ssc.f_id = f.f_id " +
				" and ssc.s_id = ?" +
				" and ssc.id = ?";
			
		log.info(sql);
		PreparedStatement pstmt = connection.prepareStatement(sql);	
		pstmt.setInt(1, sId);
		pstmt.setInt(2, qId);

		log.info("SQL: Question Info Constructor for SSC: " + pstmt.toString());
		ResultSet resultSet = pstmt.executeQuery();
		
		if(resultSet.next()) {
			tableName = resultSet.getString(1);
			fId = resultSet.getInt(2);
			parentFId = resultSet.getInt(3);
			qName = resultSet.getString(4);
			qType = "ssc";
			fn = resultSet.getString(5);
			units = resultSet.getString(6);
			columnName = resultSet.getString(7);
			
			log.info("Table:"  + tableName + ":" + fId + ":" + parentFId + ":" + qName + ":" + qType + " : " + fn + " : " + units);
			
			/*
			 * TODO future calculates might return polygons
			 * Polygon and linestring questions have "_parentquestion" removed from their name
			 */
			if(qType.equals("geolinestring") || qType.equals("geopolygon")) {
				int idx = columnName.toLowerCase().lastIndexOf("_parentquestion");
				if(idx > 0) {
					columnName = columnName.substring(0, idx);
				}
			}
			
			/*
			 * TODO future calculates might return selects
			 * Get the question's option information
			 */
			if(qType.startsWith("select")) {
				o = new ArrayList<OptionInfo> ();
				
				sql = "SELECT o.oValue, t.value, t.type, o.column_name" + 
						" FROM option o, question q, translation t" +
						" WHERE q.q_id = ?" + 
						" AND o.label_id = t.text_id" +
						" AND t.language = ? " +
						" AND t.type != 'image' AND t.type != 'video' AND t.type != 'audio' " +	// Temporarily ignore 
						" AND t.s_id = ?" +
						" AND o.l_id = q.l_id" +
						" ORDER BY o.seq";
				
				pstmt = connection.prepareStatement(sql);
				pstmt.setInt(1,  qId);
				pstmt.setString(2, lang);
				pstmt.setInt(3, sId);
				log.info("SQL: " + pstmt.toString());
				
				resultSet = pstmt.executeQuery();
				
				boolean select = false;
				if(qType.equals("select")) {
					select = true;
				}
				while(resultSet.next()) {
					String value = resultSet.getString(1);
					String label = resultSet.getString(2);
					String type = resultSet.getString(3);
					String oColumnName = resultSet.getString(4);
					if(select) {
						oColumnName = columnName + "__" + oColumnName;
					} 
					o.add(new OptionInfo(value, label, type, oColumnName));
				}
			}
			
		} else {
			log.info("Error (QuetionInfo.java) retrieving question data for survey: " + surveyId + " and question: " + questionId);
		}		

	}
	
	public QuestionInfo(String tableName, String qName, boolean isGeom, String columnName) {
		this.isGeom = isGeom;
		this.tableName = tableName;
		this.qName = qName;
		this.qType = "not_select";
		this.columnName = columnName;
	}
	
	/*
	 * Getters
	 */
	public int getQId() {
		return qId;
	}
	
	public int getFId() {
		return fId;
	}
	
	public int getParentFId() {
		return parentFId;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public String getName() {
		return qName;
	}
	
	public String getColumnName() {
		return columnName;
	}
	
	public String getLabel() {
		return qLabel;
	}
	
	public String getUnits() {
		return units;
	}
	
	/*
	 * Get the option label for the passed in value
	 */
	public String getOptionLabelForName(String name) {
		if(o != null){
			for(int i = 0; i < o.size(); i++) {
				OptionInfo aO = o.get(i);
				if(aO.getColumnName().equals(name)) {
					return aO.getLabel();
				}
			}
		} else {
			return getLabel();
		}
		return null;
	}
	
	public String getType() {
		if(qCalculate == null) {
			return qType;
		} else {
			return "calculate";
		}
	}
	
	public ArrayList<OptionInfo> getOptions() {
		return o;
	}
	
	public boolean isGeom() {
		return isGeom;
	}
	
	public String getSelectExpression() {
		if(isCalc) {
			int divBy = 1;
			if(fn.equals("area")) {
				if(units != null && units.equals("hectares")) {
					divBy = 10000;
				} else if(units != null && units.equals("square meters")) {
					divBy = 1;
				} else {
					log.log(Level.SEVERE, "Unkown units: " + units + " setting to square meters");
					units = "square meters";
					divBy = 1;
				}
				return "ST_Area(geography(" + tableName + ".the_geom), true) / " + divBy;
			} else if(fn.equals("length")) {
				if(units != null && units.equals("km")) {
					divBy = 1000;
				} else if(units != null && units.equals("meters")) {
					divBy = 1;
				} else {
					log.log(Level.SEVERE, "Unkown units: " + units + " setting to meters");
					units = "meters";
					divBy = 1;
				}
				return "ST_Length(geography(" + tableName + ".the_geom), true) / " + divBy;
			} else {
				log.info("getSelectExpression: Unknown function: " + fn);
				return tableName + "." + columnName;
			}
		} else {
			if(qType != null && (qType.equals("image") || qType.equals("audio") || qType.equals("video"))) {
				return "'" + urlprefix + "' || " + tableName + "." + columnName;
			}
			return tableName + "." + columnName;
		}
	}
	
	/*
	 * Return an SQL expression that can be used to filter on this question and the passed in option
	 */
	public String getFilterExpression(String value1, String value2) {
		String filter = "";
		if(qType != null) {
			if(qType.equals("select")) {
				
				for(int i = 0; i < o.size(); i++) {
					OptionInfo aO = o.get(i);
					if(aO.getValue().equals(value1)) {
						String oName = aO.getColumnName();
						filter = tableName + "." + oName + " = 1 ";
						break;
					}
				}
			} else if(qType.equals("select1") || qType.equals("string")) {
				filter = tableName + "." + columnName + " =  '" + value1 + "' ";
			} else if(value2 != null && (qType.equals("date") || qType.equals("dateTime"))) {
				filter = tableName + "." + columnName + " between  '" + value1 + "' and  '" + value2 + "' ";
			} else {
				filter = tableName + "." + columnName + " =  " + value1 + " ";
			}
		}
		
		log.info("Filter: " + filter);
		return filter;
	}
	
	public String getSelect() {
		String sqlFrag = "";
		log.info("get select: " + tableName + ":" + columnName + ":" + qType + " : " + qType);
		if(qType != null && qType.equals("select")) {
			// Add primary key with each question, assume only one question per query
			sqlFrag = tableName + ".prikey as prikey";
			for(int i = 0; i < o.size(); i++) {
				OptionInfo aO = o.get(i);
				String oName = aO.getColumnName();
				sqlFrag += "," + tableName + "." + oName + " as " + oName;
			}
		} else if(isGeom && columnName.equals("the_geom")) {
			sqlFrag = "ST_AsGeoJSON(" + tableName + ".the_geom) as the_geom"; 
		} else if (isGeom) {
			// Include the table name in name of result set item as otherwise geometry col name may clash with q question name
			sqlFrag = tableName + "." + columnName  + " as " + tableName + "_" + columnName;
		} else {
			// Add primary key with each question, assume only one question per query
			sqlFrag = tableName + ".prikey as prikey," + getSelectExpression()  + " as " + columnName;
		}
		return sqlFrag;
	}
	
}
