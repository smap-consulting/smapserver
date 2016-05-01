package org.smap.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.UtilityMethods;

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
 * Manage results tables
 */
public class TableManager {
	
	private static Logger log =
			 Logger.getLogger(TableManager.class.getName());
	
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
	
	/*
	 * Create the table if it does not already exit in the database
	 */
	public boolean createTable(Connection cResults, Connection sd, String tableName, String sName) {
		boolean tableCreated = false;
		String sql = "select count(*) from information_schema.tables where table_name =?;";
		
		PreparedStatement pstmt = null;
		try {
			pstmt = cResults.prepareStatement(sql);
			pstmt.setString(1, tableName);
			log.info("SQL: " + pstmt.toString());
			ResultSet res = pstmt.executeQuery();
			int count = 0;
			
			if(res.next()) {
				count = res.getInt(1);
			}
			if(count > 0) {
				log.info("        Table Exists");
			} else {
				log.info("        Table does not exist");
				SurveyTemplate template = new SurveyTemplate();   
				template.readDatabase(sd, sName);	
				writeAllTableStructures(sd, cResults, template);
				tableCreated = true;
			}
		} catch (Exception e) {
			log.info("        Error checking for existence of table:" + e.getMessage());
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		return tableCreated;
	}
	
	/*
	 * Create the tables for the survey
	 */
	private void writeAllTableStructures(Connection sd, Connection cResults, SurveyTemplate template) {
			
		String response = null;
		
		try {
		    //Class.forName(dbClass);	 
			
			List<Form> forms = template.getAllForms();	
			cResults.setAutoCommit(false);
			for(Form form : forms) {		
				writeTableStructure(form, sd, cResults);
				cResults.commit();
			}	
			cResults.setAutoCommit(true);
	


		} catch (Exception e) {
			if(cResults != null) {
				try {
					response = "Error: Rolling back: " + e.getMessage();	// TODO can't roll back within higher level transaction
					log.info(response);
					log.log(Level.SEVERE, "Error", e);
					cResults.rollback();
				} catch (SQLException ex) {
					log.info(ex.getMessage());
				}

			}
			
		} finally {
			try {if (cResults != null) {cResults.setAutoCommit(true);}} catch (SQLException e) {}
		}		
	}
	
	private void writeTableStructure(Form form, Connection sd, Connection cResults) throws SQLException {
		
		String tableName = form.getTableName();
		List<Question> columns = form.getQuestions(sd);
		String sql = null;	
		List <GeometryColumn> geoms = new ArrayList<GeometryColumn> ();

		/*
		 * Attempt to create the table, ignore any exception as the table may already be created
		 */
		if(columns.size() > 0) {
			sql = "CREATE TABLE " + tableName + " (" +
				"prikey SERIAL PRIMARY KEY, " +
				"parkey int ";
	
			/*
			 * Create default columns
			 * only add _user and _version, _complete, _modified to the top level form
			 */
			sql += ", _bad boolean DEFAULT FALSE, _bad_reason text";
			if(!form.hasParent()) {
				sql += ", _user text, _version text, _survey_notes text, _location_trigger text,"
						+ "_complete boolean default true, "
						+ "_modified boolean default false"
						+ ", _upload_time timestamp with time zone, _s_id integer ";
			}
							
			for(Question q : columns) {
				
				boolean hasExternalOptions = GeneralUtilityMethods.isAppearanceExternalFile(q.getAppearance());
				
				String source = q.getSource();
				
				// Handle geopolygon and geolinestring
				String colType = q.getType();
				if(colType.equals("begin repeat")) {
					if(q.getName().startsWith("geopolygon")) {
						colType = "geopolygon";
						source = "user";
					} else if(q.getName().startsWith("geolinestring")) {
						colType = "geolinestring";
						source = "user";
					}
				}
				
				// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
				
				if(source != null) {
					
					// Set column type for postgres
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
						GeometryColumn gc = new GeometryColumn(tableName, q.getColumnName(), "POINT");
						geoms.add(gc);
						continue;
					
					} else if(colType.equals("geopolygon") || colType.equals("geoshape")) {
					
						// remove the automatically generated string _parentquestion from the question name
						String qName = q.getColumnName();
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, "the_geom", "POLYGON");
						geoms.add(gc);
						continue;
					
					} else if(colType.equals("geolinestring") || colType.equals("geotrace")) {
						
						String qName = q.getColumnName();
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, "the_geom", "LINESTRING");
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
						Collection<Option> options = q.getValidChoices(sd);
						if(options != null) {
							List<Option> optionList = new ArrayList <Option> (options);
							HashMap<String, String> uniqueColumns = new HashMap<String, String> (); 
							UtilityMethods.sortOptions(optionList);	
							for(Option option : optionList) {
								
								// Create if its an external choice and this question uses external choices
								//  or its not an external choice and this question does not use external choices
								if(hasExternalOptions && option.getExternalFile() || !hasExternalOptions && !option.getExternalFile()) {
									
									String name = q.getColumnName() + "__" + option.getColumnName();
									if(uniqueColumns.get(name) == null) {
										uniqueColumns.put(name, name);
										sql += ", " + name + " integer";
									}
								}
							}
						} else {
							log.info("Warning: No Options for Select:" + q.getName());
						}
					} else {
						sql += ", " + q.getColumnName() + " " + colType;
					}
				} else {
					log.info("Info: Ignoring question with no source:" + q.getName());
				}
			}
			sql += ");";
			
			PreparedStatement pstmt = null;
			PreparedStatement pstmtGeom = null;
			try {
				pstmt = cResults.prepareStatement(sql);
				log.info("Sql statement: " + pstmt.toString());
				pstmt.executeUpdate();
				// Add geometry columns
				for(GeometryColumn gc : geoms) {
					String gSql = "SELECT AddGeometryColumn('" + gc.tableName + 
						"', '" + gc.columnName + "', " + 
						gc.srid + ", '" + gc.type + "', " + gc.dimension + ");";
					
					if(pstmtGeom != null) try{pstmtGeom.close();}catch(Exception e) {}
					pstmtGeom = cResults.prepareStatement(gSql);
					log.info("Sql statement: " + pstmtGeom.toString());
					pstmtGeom.executeUpdate();
				}
			} catch (SQLException e) {
				log.info(e.getMessage());
			} finally {
				if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
				if(pstmtGeom != null) try{pstmtGeom.close();}catch(Exception e) {}
			}
			
		}

	}

}


