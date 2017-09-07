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
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.TableColumn;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.UtilityMethods;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
				template.readDatabase(sd, sName, false);	
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
			SurveyViewManager qm = new SurveyViewManager();
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
				"parkey int default 0";
	
			/*
			 * Create default columns
			 * only add _user and _version, _complete, _modified to the top level form
			 */
			sql += ", _bad boolean DEFAULT FALSE, _bad_reason text, _audit text";
			if(!form.hasParent()) {
				sql += ", _user text, _version text, _survey_notes text, _location_trigger text,"
						+ "_complete boolean default true, "
						+ "_modified boolean default false"
						+ ", _upload_time timestamp with time zone, _s_id integer";
				
				if(hasHrk) {
					sql += ", _hrk text ";
				}
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
						sql += ", the_geom_alt double precision, the_geom_acc double precision";
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
	
	/*
	 * Apply changes to results table due to changes in the form
	 * Results tables will have to be updated if:
	 *   1.  A new question is added, then either
	 *       - a) Add the new question column to the table that the question is in (that is one form maps to 1 table)
	 *       - b) For a "select" question, add all of the choice columns to the form's table
	 *   2. A new choice is added to a choice list for a select multiple question
	 *   	- Add the new column to all the questions that reference the choice list
	 *       
	 */
	private class QuestionDetails {
		String columnName;
		boolean hasExternalOptions;
		String type;
		String table;
	}
	private class TableUpdateStatus {
		String msg;
		boolean tableAltered;
	}
	
	public boolean applyTableChanges(Connection connectionSD, Connection cResults, int sId) throws Exception {
		
		boolean tableChanged = false;
		
		String sqlGet = "select c_id, changes "
				+ "from survey_change "
				+ "where apply_results = 'true' "
				+ "and s_id = ? "
				+ "order by c_id asc";
		
		String sqlGetListQuestions = "select q.q_id from question q, listname l " +
				" where q.l_id = l.l_id " +
				" and l.s_id = ? " +
				" and l.l_id = ? " +
				" and q.qtype = 'select'";
		
		String sqlGetOptions = "select column_name, externalfile from option where l_id = (select l_id from question where q_id = ?) order by seq asc";
		String sqlGetAnOption = "select column_name, externalfile from option where l_id = ? and ovalue = ?;";
		
		PreparedStatement pstmtGet = null;
		PreparedStatement pstmtGetListQuestions = null;
		PreparedStatement pstmtGetOptions = null;
		PreparedStatement pstmtGetAnOption = null;
		PreparedStatement pstmtGetTableName = null;
		PreparedStatement pstmtCreateTable = null;
		
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		log.info("######## Apply table changes");
		try {
			
			pstmtGet = connectionSD.prepareStatement(sqlGet);
			pstmtGetListQuestions = connectionSD.prepareStatement(sqlGetListQuestions);
			pstmtGetOptions = connectionSD.prepareStatement(sqlGetOptions);
			pstmtGetAnOption = connectionSD.prepareStatement(sqlGetAnOption);
			
			pstmtGet.setInt(1, sId);
			log.info("SQL: " + pstmtGet.toString());
			
			ResultSet rs = pstmtGet.executeQuery();
			while(rs.next()) {
				int cId = rs.getInt(1);
				String ciJson = rs.getString(2);
				log.info("Apply table change: " + ciJson);
				ChangeItem ci = gson.fromJson(ciJson, ChangeItem.class);
				
				/*
				 * Table is altered for 
				 * 		new questions
				 * 		new select multiple options 
				 * 		questions that have been moved to a new table
				 * 		questions whose column_name has been changed
				 */
				if(ci.type != null && ci.action != null && 
						(ci.type.equals("question") || ci.type.equals("option") &&
						(ci.action.equals("add") || ci.action.equals("external option")
						|| (ci.action.equals("move") && 
								ci.question != null && 
								ci.question.formIndex != ci.question.sourceFormIndex)
						|| (ci.action.equals("update") && 
								ci.property != null && 
								ci.property.prop != null && 
								(ci.property.prop.equals("name") || ci.property.prop.equals("value"))
								)))) {
						
					log.info("table is altered");
					
					ArrayList<String> columns = new ArrayList<String> ();	// Column names in results table
					int l_id = 0;											// List ID
					TableUpdateStatus status = null;
					
					// Check for a new option or updating an existing option
					if(ci.option != null || 
							(ci.type.equals("option") && ci.action.equals("update") && ci.property.prop.equals("value"))
							) {
						
						/*
						 * Apply this option to every question that references the option list
						 */
						int listId = 0;
						String listName = null;
						String value = null;
						if(ci.option != null) {
							listId = ci.option.l_id;
							listName = ci.option.optionList;
							value = ci.option.value;
						} else {
							listId = ci.property.l_id;
							listName = ci.property.optionList;
							value = ci.property.newVal;
						}
						
						if(listId == 0) {
							listId = GeneralUtilityMethods.getListId(connectionSD, sId, listName);
						}
						String optionColumnName = null;
						boolean externalFile = false;
						
						// Get the option details
						pstmtGetAnOption.setInt(1, listId);
						pstmtGetAnOption.setString(2, value);
						
						log.info("Get option details: " + pstmtGetAnOption);
						ResultSet rsOption = pstmtGetAnOption.executeQuery();
						if(rsOption.next()) {
							optionColumnName = rsOption.getString(1);
							externalFile = rsOption.getBoolean(2);
						}
						
						if(optionColumnName != null) { // Will be null if name changed prior to being published
							// Get the questions that use this option list
							pstmtGetListQuestions.setInt(1, sId);
							pstmtGetListQuestions.setInt(2, listId);
							
							log.info("Get list of questions that refer to an option: " + pstmtGetListQuestions);
							ResultSet rsQuestions = pstmtGetListQuestions.executeQuery();
							
							while(rsQuestions.next()) {
								// Get the question details
								int qId = rsQuestions.getInt(1);
								QuestionDetails qd = getQuestionDetails(connectionSD, qId);
								
								if(qd != null) {
									if(qd.hasExternalOptions && externalFile || !qd.hasExternalOptions && !externalFile) {
										status = alterColumn(cResults, qd.table, "integer", qd.columnName + "__" + optionColumnName);
										if(status.tableAltered) {
											tableChanged = true;
										}
									}
								}
	
							}
						} else {
							log.info("Option column name for list: " + listId + " and value: " + ci.option.value + " was not found.");
						}
						
					
					} else if(ci.question != null || (ci.property != null && ci.property.prop.equals("name"))) {
						// Don't rely on any parameters in the change item, they may have been changed again after the question was added
						int qId = 0;
						if(ci.question != null) {
							qId = GeneralUtilityMethods.getQuestionId(connectionSD, ci.question.fId, sId, ci.question.id, ci.question.name);
						} else {
							qId = ci.property.qId;
						}
						
						if(qId != 0) {
							QuestionDetails qd = getQuestionDetails(connectionSD, qId);
	
							if(qd.type.equals("begin group") || qd.type.equals("end group")) {
								// Ignore group changes
							} else if(qd.type.equals("begin repeat")) {
								// Get the table name
								String sqlGetTable = "select table_name from form where s_id = ? and parentquestion = ?;";
								pstmtGetTableName = connectionSD.prepareStatement(sqlGetTable);
								pstmtGetTableName.setInt(1, sId);
								pstmtGetTableName.setInt(2, qId);
								ResultSet rsTableName = pstmtGetTableName.executeQuery();
								if(rsTableName.next()) {
									String tableName = rsTableName.getString(1);
									
									String sqlCreateTable = "create table " + tableName + " ("
											+ "prikey SERIAL PRIMARY KEY, "
											+ "parkey int,"
											+ "_bad boolean DEFAULT FALSE, _bad_reason text)";
									pstmtCreateTable = cResults.prepareStatement(sqlCreateTable);
									pstmtCreateTable.executeUpdate();
								}
								
							} else {
								columns.add(qd.columnName);		// Usually this is the case unless the question is a select multiple
								
								if(qd.type.equals("string")) {
									qd.type = "text";
								} else if(qd.type.equals("dateTime")) {
									qd.type = "timestamp with time zone";					
								} else if(qd.type.equals("audio") || qd.type.equals("image") || qd.type.equals("video")) {
									qd.type = "text";					
								} else if(qd.type.equals("decimal")) {
									qd.type = "double precision";
								} else if(qd.type.equals("barcode")) {
									qd.type = "text";
								} else if(qd.type.equals("note")) {
									qd.type = "text";
								} else if(qd.type.equals("select1")) {
									qd.type = "text";
								} else if (qd.type.equals("select")) {
									qd.type = "integer";
									
									columns.clear();
									pstmtGetOptions.setInt(1, qId);
									
									log.info("Get options to add: "+ pstmtGetOptions.toString());
									ResultSet rsOptions = pstmtGetOptions.executeQuery();
									while(rsOptions.next()) {			
										// Create if its an external choice and this question uses external choices
										//  or its not an external choice and this question does not use external choices
										String o_col_name = rsOptions.getString(1);
										boolean externalFile = rsOptions.getBoolean(2);
		
										if(qd.hasExternalOptions && externalFile || !qd.hasExternalOptions && !externalFile) {
											String column =  qd.columnName + "__" + o_col_name;
											columns.add(column);
										}
									} 
								}
								
								// Apply each column
								for(String col : columns) {
									status = alterColumn(cResults, qd.table, qd.type, col);
									tableChanged = true;
								}
							}
						}
						
					}
					
					// Record the application of the change and the status
					String msg = status != null ? status.msg : "";
					boolean tableAltered = status != null ? status.tableAltered : false;
					markChangeApplied(connectionSD, cId, tableAltered, msg);

						
	
				} else {
					// Record that this change has been processed
					markChangeApplied(connectionSD, cId, true, "");
				}

			}
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtGet != null) {pstmtGet.close();}} catch (Exception e) {}
			try {if (pstmtGetOptions != null) {pstmtGetOptions.close();}} catch (Exception e) {}
			try {if (pstmtGetAnOption != null) {pstmtGetAnOption.close();}} catch (Exception e) {}
			try {if (pstmtGetListQuestions != null) {pstmtGetListQuestions.close();}} catch (Exception e) {}
			try {if (pstmtGetTableName != null) {pstmtGetTableName.close();}} catch (Exception e) {}
		}
		
		return tableChanged;
		
	}
	
	public boolean addUnpublishedColumns(Connection connectionSD, Connection cResults, int sId) throws Exception {
		
		boolean tablePublished = false;
		
		String sqlGetUnpublishedQuestions = "select q.q_id, q.qtype, q.column_name, q.l_id, q.appearance, f.table_name "
				+ "from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and q.published = 'false' "
				+ "and f.s_id = ?";
		
		String sqlGetUnpublishedOptions = "select o_id, column_name, externalfile "
				+ "from option "
				+ "where published = 'false' "
				+ "and l_id = ?";
		
		PreparedStatement pstmtGetUnpublishedQuestions = null;
		PreparedStatement pstmtGetUnpublishedOptions = null;
		
		
		log.info("######## Apply unpublished questions");
		try {
			
			pstmtGetUnpublishedQuestions = connectionSD.prepareStatement(sqlGetUnpublishedQuestions);
			pstmtGetUnpublishedOptions = connectionSD.prepareStatement(sqlGetUnpublishedOptions);
			
			pstmtGetUnpublishedQuestions.setInt(1, sId);
			log.info("Get unpublished questions: " + pstmtGetUnpublishedQuestions.toString());
			
			ArrayList<String> columns = new ArrayList<String> ();	// Column names in results table
			
			ResultSet rs = pstmtGetUnpublishedQuestions.executeQuery();
			while(rs.next()) {
				String qType = rs.getString(2);
				String columnName = rs.getString(3);
				int l_id = rs.getInt(4);				// List Id
				boolean hasExternalOptions = GeneralUtilityMethods.isAppearanceExternalFile(rs.getString(5));
				String table_name = rs.getString(6);
				
				columns.clear();
				
				if(qType.equals("begin group") || qType.equals("end group")) {
						// Ignore group changes
				} else if(qType.equals("begin repeat")) {
					// TODO
						
				} else {
					columns.add(columnName);		// Usually this is the case unless the question is a select multiple
						
					if(qType.equals("string")) {
						qType = "text";
					} else if(qType.equals("dateTime")) {
						qType = "timestamp with time zone";					
					} else if(qType.equals("audio") || qType.equals("image") || qType.equals("video")) {
						qType = "text";					
					} else if(qType.equals("decimal")) {
						qType = "real";
					} else if(qType.equals("barcode")) {
						qType = "text";
					} else if(qType.equals("note")) {
						qType = "text";
					} else if(qType.equals("select1")) {
						qType = "text";
					} else if(qType.equals("acknowledge")) {
						qType = "text";
					} else if (qType.equals("select")) {
						qType = "integer";
								
						columns.clear();
						pstmtGetUnpublishedOptions.setInt(1, l_id);
								
						log.info("Get unpublished options to add: "+ pstmtGetUnpublishedOptions.toString());
						ResultSet rsOptions = pstmtGetUnpublishedOptions.executeQuery();
							while(rsOptions.next()) {			
							// Create if its an external choice and this question uses external choices
							//  or its not an external choice and this question does not use external choices
							String o_col_name = rsOptions.getString(2);
							boolean externalFile = rsOptions.getBoolean(3);
		
							if(hasExternalOptions && externalFile || !hasExternalOptions && !externalFile) {
								String column =  columnName + "__" + o_col_name;
								columns.add(column);
							}
						}
					}
							
					// Apply each column
					for(String col : columns) {
						alterColumn(cResults, table_name, qType, col);
						tablePublished = true;
					}					
				} 

			}
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtGetUnpublishedQuestions != null) {pstmtGetUnpublishedQuestions.close();}} catch (Exception e) {}
			try {if (pstmtGetUnpublishedOptions != null) {pstmtGetUnpublishedOptions.close();}} catch (Exception e) {}
		}
		
		return tablePublished;
		
	}
	
	/*
	 * Alter the table
	 */
	private TableUpdateStatus alterColumn(Connection cResults, String table, String type, String column) {
		
		PreparedStatement pstmtAlterTable = null;
		PreparedStatement pstmtApplyGeometryChange = null;
		
		TableUpdateStatus status = new TableUpdateStatus();
		status.tableAltered = true;
		status.msg = "";
		
		try {
			if(type.equals("geopoint") || type.equals("geotrace") || type.equals("geoshape")) {
				
				String geoType = null;
				
				if(type.equals("geopoint")) {
					geoType = "POINT";
				} else if (type.equals("geotrace")) {
					geoType = "LINESTRING";
				} else if (type.equals("geoshape")) {
					geoType = "POLYGON";
				}
				String gSql = "SELECT AddGeometryColumn('" + table + 
						"', 'the_geom', 4326, '" + geoType + "', 2);";
					log.info("Add geometry column: " + gSql);
					
					pstmtApplyGeometryChange = cResults.prepareStatement(gSql);
					try { 
						pstmtApplyGeometryChange.executeQuery();
					} catch (Exception e) {
						// Allow this to fail where an older version added a geometry, which was then deleted, then a new 
						//  geometry with altitude was added we need to go on and add the altitude and accuracy
						log.info("Error altering table -- continuing: " + e.getMessage());
						try {cResults.rollback();} catch(Exception ex) {}
					}
					
					// Add altitude and accuracy
					if(type.equals("geopoint")) {
						String sqlAlterTable = "alter table " + table + " add column the_geom_alt double precision";
						pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
						log.info("Alter table: " + pstmtAlterTable.toString());					
						pstmtAlterTable.executeUpdate();
						
						try {if (pstmtAlterTable != null) {pstmtAlterTable.close();}} catch (Exception e) {}
						sqlAlterTable = "alter table " + table + " add column the_geom_acc double precision";
						pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
						log.info("Alter table: " + pstmtAlterTable.toString());					
						pstmtAlterTable.executeUpdate();
					}
					
					// Commit this change to the database
					try { cResults.commit();	} catch(Exception ex) {}
			} else {
				
				String sqlAlterTable = "alter table " + table + " add column " + column + " " + type + ";";
				pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
				log.info("Alter table: " + pstmtAlterTable.toString());
			
				pstmtAlterTable.executeUpdate();
				
				// Commit this change to the database
				try {cResults.commit();} catch(Exception ex) {}
			} 
		} catch (Exception e) {
			// Report but otherwise ignore any errors
			log.info("Error altering table -- continuing: " + e.getMessage());
			
			// Rollback this change
			try {cResults.rollback();} catch(Exception ex) {}
			
			// Only record the update as failed if the problem was not due to the column already existing
			status.msg = e.getMessage();
			if(status.msg == null || !status.msg.contains("already exists")) {
				status.tableAltered = false;
			}
		} finally {
			try {if (pstmtAlterTable != null) {pstmtAlterTable.close();}} catch (Exception e) {}
			try {if (pstmtApplyGeometryChange != null) {pstmtApplyGeometryChange.close();}} catch (Exception e) {}
		}
		return status;
	}
	
	private QuestionDetails getQuestionDetails(Connection sd, int qId) throws Exception {
		
		QuestionDetails qd = new QuestionDetails();
		PreparedStatement pstmt = null;;
		
		String sqlGetQuestionDetails = "select q.column_name, q.appearance, q.qtype, f.table_name "
				+ "from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and q_id = ?;";
		
		try {
			pstmt = sd.prepareStatement(sqlGetQuestionDetails);
			
			pstmt.setInt(1, qId);
			
			log.info("Get question details: " + pstmt.toString());
			ResultSet rsDetails = pstmt.executeQuery();
			if(rsDetails.next()) {
				qd.columnName = rsDetails.getString(1);
				qd.hasExternalOptions = GeneralUtilityMethods.isAppearanceExternalFile(rsDetails.getString(2));
				qd.type = rsDetails.getString(3);
				qd.table = rsDetails.getString(4);
			} else {
				throw new Exception("Can't find question details: " + qId);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}
		
		return qd;
	}
	
	private void markChangeApplied(Connection sd, int cId, boolean success, String msg) throws SQLException {
		
		String sqlUpdateChange = "update survey_change "
				+ "set apply_results = 'false', "
				+ "success = ?, "
				+ "msg = ? "
				+ "where c_id = ? ";
		
		PreparedStatement pstmtUpdateChange = null;
		try {
			pstmtUpdateChange = sd.prepareStatement(sqlUpdateChange);
			
			pstmtUpdateChange.setBoolean(1, success);
			pstmtUpdateChange.setString(2, msg);
			pstmtUpdateChange.setInt(3, cId);
			pstmtUpdateChange.executeUpdate();
			
		}catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtUpdateChange != null) {pstmtUpdateChange.close();}} catch (Exception e) {}
		}
		
	}

}


