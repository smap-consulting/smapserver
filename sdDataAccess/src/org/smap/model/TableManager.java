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
import org.smap.sdal.managers.ManagedFormsManager;
import org.smap.sdal.model.TableColumn;
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
	public boolean createTable(Connection cResults, Connection sd, String tableName, String sName, 
			int sId,
			int managedId) throws Exception {
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
		
		if(tableCreated) {
			markPublished(sd, sId);
			markAllChangesApplied(sd, sId);
		}
		
		if(tableCreated || managedId > 0) {
			addManagementColumns(cResults, sd, sId, managedId);
		}
		
		return tableCreated;
	}
	
	public void addManagementColumns(Connection cResults, Connection sd, int sId, int managedId) throws Exception {
		
		String sql = "select managed_id from survey where s_id = ?;";
		PreparedStatement pstmt = null;
		
		/*
		 * Get the managed Id if it is not already known
		 */
		if(managedId == 0) {
			try {
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1,  sId);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					managedId = rs.getInt(1);
				}
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
			}
		}
		
		/*
		 * Add the columns if this survey is managed
		 */
		if(managedId > 0) {
			String sqlAdd = null;
			PreparedStatement pstmtAdd = null;
			
			ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
			ManagedFormsManager qm = new ManagedFormsManager();
			qm.getDataProcessingConfig(sd, managedId, columns, null, GeneralUtilityMethods.getOrganisationIdForSurvey(sd, sId));
			
			org.smap.sdal.model.Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);	// Get the table name of the top level form
			
			for(int i = 0; i < columns.size(); i++) {
				TableColumn tc = columns.get(i);
				if(tc.type != null) {
					
					if(tc.type.equals("calculate")) {
						continue;		// Calculated types are not stored in the database
					}
					
					String type;
					if(tc.type.equals("select_one")) {
						type = "text";
					} else {
						type = tc.type;
					}
					
					if(!GeneralUtilityMethods.hasColumn(cResults, f.tableName, tc.name)) {
						sqlAdd = "alter table " + f.tableName + " add column " + tc.name + " " + type;
						
						pstmtAdd = cResults.prepareStatement(sqlAdd);
						log.info("Adding management column: " + pstmtAdd.toString());
						try {
							pstmtAdd.executeUpdate();
						} catch (Exception e) {
							String msg = e.getMessage();
							if(msg.contains("already exists")) {
								log.info("Management column already exists");
							} else {
								throw e;
							}
						} finally {
							try {if (pstmtAdd != null) {pstmtAdd.close();}} catch (Exception e) {}
						}
					}
				} else {
					log.info("Error: managed column not added as type was null: " + tc.name);
				}
			}
		}
	}
	
	/*
	 * Mark all the questions and options in the survey as published
	 * Mark as published any questions in other surveys that share this results table
	 */
	public void markPublished(Connection sd, int sId) throws SQLException {
		
		class FormDetail {
			boolean isSubmitter;
			int fId;
			int submittingFormId;
			String table_name;
		}
		ArrayList<FormDetail> forms = new ArrayList<FormDetail> ();
		

		String sqlGetSharingForms = "select f.s_id, f.f_id, f.table_name from form f, survey s "
				+ "where s.s_id = f.s_id "
				+ "and s.deleted = 'false' "
				+ "and f.table_name in (select table_name from form where s_id = ?);";
		
		String sqlSetPublishedThisForm = "update question set published = 'true' where f_id = ?;";
		
		String sqlSetOptionsPublishedThisForm = "update option set published = 'true' "
				+ "where l_id in (select l_id from question q where f_id = ?);";
		
		String sqlSetPublishedSharedForm = "update question set published = 'true' "
				+ "where f_id = ? "
				+ "and column_name in (select column_name from question where f_id = ?);";
		
		String sqlSetOptionsPublishedSharedForm = "update option set published = 'true' "
				+ "where l_id in (select l_id from question q where f_id = ? "
				+ "and column_name in (select column_name from question where f_id = ?));";
		
		PreparedStatement pstmtGetForms = null;
		PreparedStatement pstmtSetPublishedThisForm = null;
		PreparedStatement pstmtSetPublishedSharedForm = null;
		PreparedStatement pstmtSetOptionsPublishedThisForm = null;
		PreparedStatement pstmtSetOptionsPublishedSharedForm = null;
		
		try {
			
			pstmtGetForms = sd.prepareStatement(sqlGetSharingForms);
			pstmtSetPublishedThisForm = sd.prepareStatement(sqlSetPublishedThisForm);
			pstmtSetPublishedSharedForm = sd.prepareStatement(sqlSetPublishedSharedForm);
			pstmtSetOptionsPublishedThisForm = sd.prepareStatement(sqlSetOptionsPublishedThisForm);
			pstmtSetOptionsPublishedSharedForm = sd.prepareStatement(sqlSetOptionsPublishedSharedForm);
			
			// 1. Get all the affected forms
			pstmtGetForms.setInt(1, sId);
			
			log.info("Get sharing forms: " + pstmtGetForms.toString());
			ResultSet rs = pstmtGetForms.executeQuery();
			
			while(rs.next()) {
				
				FormDetail fd = new FormDetail();
				fd.isSubmitter = (sId == rs.getInt(1));
				fd.fId = rs.getInt(2);
				fd.table_name = rs.getString(3);
				forms.add(fd);
			}
			
			// 2. For all shared forms record the matching formId of the submitting form
			for(FormDetail fd : forms) {
				if(!fd.isSubmitter) {
					for(FormDetail fd2 : forms) {
						if(fd2.isSubmitter && fd.table_name.equals(fd2.table_name)) {
							fd.submittingFormId = fd2.fId;
							break;
						}
					}
				}
			}
			
			// 3. Mark the forms published
			for(FormDetail fd : forms) {
				
				if(fd.isSubmitter) {
					
					// 3.1a Update questions in the submitting form
					pstmtSetPublishedThisForm.setInt(1, fd.fId);
					log.info("Mark published: " + pstmtSetPublishedThisForm.toString());
					pstmtSetPublishedThisForm.executeUpdate();
					
					// 3.2a Update Options in the submitting form
					pstmtSetOptionsPublishedThisForm.setInt(1, fd.fId);
					log.info("Mark published: " + pstmtSetOptionsPublishedThisForm.toString());
					pstmtSetOptionsPublishedThisForm.executeUpdate();
					
				} else {
					
					// 3.1b Update questions in the shared form
					pstmtSetPublishedSharedForm.setInt(1, fd.fId);
					pstmtSetPublishedSharedForm.setInt(2, fd.submittingFormId);
					log.info("Mark published: " + pstmtSetPublishedSharedForm.toString());
					pstmtSetPublishedSharedForm.executeUpdate();
					
					// 3.1b Update options in the shared form
					pstmtSetOptionsPublishedSharedForm.setInt(1, fd.fId);
					pstmtSetOptionsPublishedSharedForm.setInt(2, fd.submittingFormId);
					log.info("Mark published: " + pstmtSetOptionsPublishedSharedForm.toString());
					pstmtSetOptionsPublishedSharedForm.executeUpdate();
				}
			
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtGetForms != null) {pstmtGetForms.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedThisForm != null) {pstmtSetPublishedThisForm.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedSharedForm != null) {pstmtSetPublishedSharedForm.close();}} catch (Exception e) {}
			try {if (pstmtSetOptionsPublishedThisForm != null) {pstmtSetOptionsPublishedThisForm.close();}} catch (Exception e) {}
			try {if (pstmtSetOptionsPublishedSharedForm != null) {pstmtSetOptionsPublishedSharedForm.close();}} catch (Exception e) {}
		}
		
	}
	
	/*
	 * Mark all the columns in the table as published been applied
	 */
	private void markAllChangesApplied(Connection sd, int sId) throws SQLException {
		
		String sqlUpdateChange = "update survey_change "
				+ "set apply_results = 'false', "
				+ "success = 'true' "
				+ "where s_id = ? ";
		
		PreparedStatement pstmtUpdateChange = null;
		try {
			pstmtUpdateChange = sd.prepareStatement(sqlUpdateChange);
			
			pstmtUpdateChange.setInt(1, sId);
			pstmtUpdateChange.executeUpdate();
			
		}catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtUpdateChange != null) {pstmtUpdateChange.close();}} catch (Exception e) {}
		}
		
	}
	
	/*
	 * Create the tables for the survey
	 */
	private void writeAllTableStructures(Connection sd, Connection cResults, SurveyTemplate template) {
			
		String response = null;
		boolean hasHrk = (template.getHrk() != null);
		
		try {
		    //Class.forName(dbClass);	 
			
			List<Form> forms = template.getAllForms();	
			cResults.setAutoCommit(false);
			for(Form form : forms) {		
				writeTableStructure(form, sd, cResults, hasHrk);
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
	
	private void writeTableStructure(Form form, Connection sd, Connection cResults, boolean hasHrk) throws Exception {
		
		String tableName = form.getTableName();
		List<Question> columns = form.getQuestions(sd, form.getPath(null));
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
			
			if(hasHrk) {
				sql += ", _hrk text ";
			}
							
			for(Question q : columns) {
				
				boolean hasExternalOptions = GeneralUtilityMethods.isAppearanceExternalFile(q.getAppearance(false, null));
				
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
						colType = "double precision";
					} else if(colType.equals("select1")) {
						colType = "text";
					} else if(colType.equals("barcode")) {
						colType = "text";
					} else if(colType.equals("acknowledge")) {
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


