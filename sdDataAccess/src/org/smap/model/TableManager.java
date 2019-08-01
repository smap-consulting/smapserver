package org.smap.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableUpdateStatus;
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

	private ResourceBundle localisation;
	private String tz;
	
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

	public TableManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
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

			SurveyViewDefn svd = new SurveyViewDefn();
			SurveyViewManager qm = new SurveyViewManager(localisation, tz);
			qm.getDataProcessingConfig(sd, managedId, svd, null, GeneralUtilityMethods.getOrganisationIdForSurvey(sd, sId));

			org.smap.sdal.model.Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);	// Get the table name of the top level form

			for(int i = 0; i < svd.columns.size(); i++) {
				TableColumn tc = svd.columns.get(i);
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

					if(!GeneralUtilityMethods.hasColumn(cResults, f.tableName, tc.column_name)) {
						sqlAdd = "alter table " + f.tableName + " add column " + tc.column_name + " " + type;

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
					log.info("Error: managed column not added as type was null: " + tc.column_name);
				}
			}
		}
	}

	/*
	 * Mark all the questions and options in the form as published
	 * Mark as published any questions in other forms that share this results table
	 */
	public void markPublished(Connection sd, int fId, int sId) throws SQLException {

		class FormDetail {
			boolean isSubmitter;
			int fId;
			int submittingFormId;
		}
		ArrayList<FormDetail> forms = new ArrayList<FormDetail> ();

		String sqlGetSharingForms = "select f.f_id from form f, survey s "
				+ "where f.table_name in (select table_name from form where f_id = ? and not reference) "
				+ "and f.s_id = s.s_id "
				+ "and not s.deleted "
				+ "and not f.reference";

		String sqlSetPublishedThisForm = "update question set published = 'true' where f_id = ?";
		
		String sqlSetPublishedParentQuestion = "update question set published = 'true' "
				+ "where q_id = (select parentquestion from form where f_id = ?);";
		
		String sqlSetPublishedParentQuestionSharedForm = "update question set published = 'true' "
				+ "where q_id = (select parentquestion from form where f_id = ?)"
				+ "and column_name in (select column_name from question where f_id = ?);";

		String sqlSetPublishedSharedForm = "update question set published = 'true' "
				+ "where f_id = ? "
				+ "and column_name in (select column_name from question where f_id = ?);";


		PreparedStatement pstmtGetForms = null;
		PreparedStatement pstmtSetPublishedThisForm = null;
		PreparedStatement pstmtSetPublishedSharedForm = null;
		PreparedStatement pstmtSetPublishedParentQuestion = null;
		PreparedStatement pstmtSetPublishedParentQuestionSharedForm = null;

		try {

			pstmtGetForms = sd.prepareStatement(sqlGetSharingForms);
			pstmtSetPublishedThisForm = sd.prepareStatement(sqlSetPublishedThisForm);
			pstmtSetPublishedSharedForm = sd.prepareStatement(sqlSetPublishedSharedForm);
			pstmtSetPublishedParentQuestion = sd.prepareStatement(sqlSetPublishedParentQuestion);
			pstmtSetPublishedParentQuestionSharedForm = sd.prepareStatement(sqlSetPublishedParentQuestionSharedForm);

			// 1. Get all the affected forms
			pstmtGetForms.setInt(1, fId);
			log.info("Get sharing forms: " + pstmtGetForms.toString());
			ResultSet rs = pstmtGetForms.executeQuery();

			while(rs.next()) {

				FormDetail fd = new FormDetail();
				fd.fId = rs.getInt(1);
				fd.isSubmitter = (fId == fd.fId);
				fd.submittingFormId = fId;
				forms.add(fd);
			}

			// 2. Mark the forms published
			for(FormDetail fd : forms) {

				if(fd.isSubmitter) {

					log.info("--------------- submitter: " + fd.fId);
					// 3.1a Update questions in the submitting form
					pstmtSetPublishedThisForm.setInt(1, fd.fId);
					log.info("Mark published: " + pstmtSetPublishedThisForm.toString());
					pstmtSetPublishedThisForm.executeUpdate();
					
					// 3.3a Update parent question in the submitting form
					pstmtSetPublishedParentQuestion.setInt(1, fd.fId);
					log.info("Mark published: " + pstmtSetPublishedParentQuestion.toString());
					pstmtSetPublishedParentQuestion.executeUpdate();

				} else {

					log.info("+++++++++++++++ shared: " + fd.fId);
					// 3.1b Update questions in the shared form
					pstmtSetPublishedSharedForm.setInt(1, fd.fId);
					pstmtSetPublishedSharedForm.setInt(2, fd.submittingFormId);
					log.info("Mark shared questions published: " + pstmtSetPublishedSharedForm.toString());
					pstmtSetPublishedSharedForm.executeUpdate();

					// 3.2b Update options in the shared form
					//pstmtSetOptionsPublishedSharedForm.setInt(1, fd.fId);
					//pstmtSetOptionsPublishedSharedForm.setInt(2, fd.submittingFormId);
					//log.info("Mark shared options published: " + pstmtSetOptionsPublishedSharedForm.toString());
					//pstmtSetOptionsPublishedSharedForm.executeUpdate();
					
					// 3.3b Update parent question in the shared form
					pstmtSetPublishedParentQuestionSharedForm.setInt(1, fd.fId);
					pstmtSetPublishedParentQuestionSharedForm.setInt(2, fd.submittingFormId);
					log.info("Mark published: " + pstmtSetPublishedParentQuestionSharedForm.toString());
					pstmtSetPublishedParentQuestionSharedForm.executeUpdate();
				}

			}
			try {
				SurveyTableManager stm = new SurveyTableManager(sd, localisation);
				stm.delete(sId);			// Delete references to this survey in the csv table so that they get regenerated
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {if (pstmtGetForms != null) {pstmtGetForms.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedThisForm != null) {pstmtSetPublishedThisForm.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedSharedForm != null) {pstmtSetPublishedSharedForm.close();}} catch (Exception e) {}
			//try {if (pstmtSetOptionsPublishedThisForm != null) {pstmtSetOptionsPublishedThisForm.close();}} catch (Exception e) {}
			//try {if (pstmtSetOptionsPublishedSharedForm != null) {pstmtSetOptionsPublishedSharedForm.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedParentQuestion != null) {pstmtSetPublishedParentQuestion.close();}} catch (Exception e) {}
			try {if (pstmtSetPublishedParentQuestionSharedForm != null) {pstmtSetPublishedParentQuestionSharedForm.close();}} catch (Exception e) {}
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
	public void writeAllTableStructures(Connection sd, Connection cResults, int sId, SurveyTemplate template, int managedId) {

		String response = null;
		//boolean hasHrk = (template.getHrk() != null);
		boolean hasHrk = true;		// Always create the hrk column
		boolean resAutoCommitSetFalse = false;

		boolean tableCreated = false;
		String sql = "select count(*) from information_schema.tables where table_name =?";		
		PreparedStatement pstmt = null;
		try {
			//Class.forName(dbClass);	 

			List<Form> forms = template.getAllForms();	
			if(cResults.getAutoCommit()) {
				log.info("Set autocommit results false");
				resAutoCommitSetFalse = true;
				cResults.setAutoCommit(false);
			}
			
			for(Form form : forms) {
				if(!form.getReference()) {
					
					// Test to see if this table already exists
					try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {}
					pstmt = cResults.prepareStatement(sql);
					pstmt.setString(1, form.getTableName());
					log.info("SQL: " + pstmt.toString());
					ResultSet res = pstmt.executeQuery();
					int count = 0;
					if(res.next()) {
						count = res.getInt(1);
					}
					
					if(count > 0) {
						log.info("        Table Exists");
					} else {
						// Create the table
						log.info("        Table does not exist");
						writeTableStructure(form, sd, cResults, hasHrk, template);
						tableCreated = true;
					}
				}
				cResults.commit();
				
				if(tableCreated) {
					markPublished(sd, form.getId(), sId);

				}

				// Add managed columns if a top level form has been created or a mangedId was passed
				if((tableCreated && !form.hasParent()) || managedId > 0) {
					addManagementColumns(cResults, sd, sId, managedId);
				}

			}	
			

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
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {}
			if(resAutoCommitSetFalse) {
				log.info("Set autocommit results true");
				resAutoCommitSetFalse = false;
				try {cResults.setAutoCommit(true);} catch(Exception e) {}
			}
		}		
	}

	private void writeTableStructure(Form form, Connection sd, Connection cResults, boolean hasHrk, SurveyTemplate template) throws Exception {

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
			 * Create default columns in the top level form
			 */
			sql += ", _bad boolean DEFAULT FALSE, _bad_reason text, _audit text, _audit_raw text, _assigned text";
			if(!form.hasParent()) {
				sql += ", _user text, _version text, _survey_notes text, _location_trigger text,"
						+ "_complete boolean default true, "
						+ "_modified boolean default false,"
						+ SmapServerMeta.UPLOAD_TIME_NAME + " timestamp with time zone, "
						+ SmapServerMeta.SURVEY_ID_NAME + " integer,"
						+ "instanceid text, "
						+ "instancename text,"
						+ SmapServerMeta.SCHEDULED_START_NAME + " timestamp with time zone";

				if(hasHrk) {
					sql += ", _hrk text ";
				}
				
				// Add preloads
				ArrayList<MetaItem> meta = template.getSurvey().getMeta();
				if(meta != null) {
					for(MetaItem mi : meta) {
						if(mi.isPreload) {
							String type = " text";
							if(mi.dataType.equals("timestamp")) {
								type = " timestamp with time zone";
							} else if(mi.dataType.equals("date")) {
								type = " date";
							}
							sql += "," + mi.columnName + type;
						}
					}
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
				// Also ignore meta - these are now added separately and not from the question list
				if(source != null && !GeneralUtilityMethods.isMetaQuestion(q.getName())) {
					
					if(colType.equals("geopoint")) {

						// Add geometry columns after the table is created using AddGeometryColumn()
						GeometryColumn gc = new GeometryColumn(tableName, "the_geom", "POINT");
						geoms.add(gc);
						sql += ", the_geom_alt double precision, the_geom_acc double precision";
						continue;

					} else if(colType.equals("geopolygon") || colType.equals("geoshape")) {

						// remove the automatically generated string _parentquestion from the question name
						String qName = q.getColumnName(false);
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, "the_geom", "POLYGON");
						geoms.add(gc);
						continue;

					} else if(colType.equals("geolinestring") || colType.equals("geotrace")) {

						String qName = q.getColumnName(false);
						int idx = qName.lastIndexOf("_parentquestion");
						if(idx > 0) {
							qName = qName.substring(0, idx);
						}
						GeometryColumn gc = new GeometryColumn(tableName, "the_geom", "LINESTRING");
						geoms.add(gc);
						continue;

					} 
					
					
					if(colType.equals("select") && !q.isCompressed()) {
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

									String name = q.getColumnName(false) + "__" + option.getColumnName();
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
						colType = GeneralUtilityMethods.getPostgresColType(colType, q.isCompressed());
						sql += ", " + q.getColumnName(false) + " " + colType;
					}
				} else {
					// log.info("Info: Ignoring question with no source:" + q.getName());
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
					log.info("Add geometry columns: " + pstmtGeom.toString());
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
		boolean reference;
		boolean compressed;
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
				 * It is not altered if the question is in a reference form
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
								int qId = rsQuestions.getInt(1);		// Select questions are returned in the Result Set
								QuestionDetails qd = getQuestionDetails(connectionSD, qId);

								if(qd != null && !qd.reference && !qd.compressed) {
									if(qd.hasExternalOptions && externalFile || !qd.hasExternalOptions && !externalFile) {
										status = GeneralUtilityMethods.alterColumn(cResults, qd.table, "integer", qd.columnName + "__" + optionColumnName, qd.compressed);
										if(status.tableAltered) {
											tableChanged = true;
										}
									}
								}

							}
						} else {
							log.info("Option column name for list: " + listId + " was not found.");
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
							try {
								QuestionDetails qd = getQuestionDetails(connectionSD, qId);
								
								if(!qd.reference) {
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
	
										if (qd.type.equals("select") && !qd.compressed) {
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
											status = GeneralUtilityMethods.alterColumn(cResults, qd.table, qd.type, col, qd.compressed);
											tableChanged = true;
										}
									}
								}
							} catch (Exception e) {
								e.printStackTrace();
								// Continue even if there is an error
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

	public boolean addUnpublishedColumns(Connection connectionSD, Connection cResults, int sId, String tableName) throws Exception {

		boolean tablePublished = false;

		String sqlGetUnpublishedQuestions = "select "
				+ "q.q_id, q.qtype, q.column_name, q.l_id, q.appearance, f.table_name, q.compressed "
				+ "from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and (q.published = 'false' or (q.compressed = 'false' and q.qtype = 'select')) "
				+ "and f.reference = 'false' "
				+ "and q.soft_deleted = 'false' "
				+ "and f.s_id = ?";

		String sqlGetUnpublishedOptions = "select o_id, column_name, externalfile "
				+ "from option "
				+ "where published = 'false' "
				+ "and l_id = ?";

		PreparedStatement pstmtGetUnpublishedQuestions = null;
		PreparedStatement pstmtGetUnpublishedOptions = null;


		log.info("######## Apply unpublished questions");
		try {

			addUnpublishedPreloads(connectionSD, cResults, sId, tableName);
			
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
				boolean compressed = rs.getBoolean(7);

				columns.clear();

				if(qType.equals("begin group") || qType.equals("end group")) {
					// Ignore group changes
				} else if(qType.equals("begin repeat")) {

				} else if (qType.equals("select") && !compressed) {
					qType = "integer";

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
					// Apply each column
					for(String col : columns) {
						GeneralUtilityMethods.alterColumn(cResults, table_name, qType, col, compressed);
						tablePublished = true;
					}	
				} else {
					GeneralUtilityMethods.alterColumn(cResults, table_name, qType, columnName, compressed);
					tablePublished = true;
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
	
	public void addUnpublishedPreloads(Connection connectionSD, 
			Connection cResults, 
			int sId,
			String tableName) throws Exception {

		ArrayList<MetaItem> preloads = GeneralUtilityMethods.getPreloads(connectionSD, sId);

		log.info("######## Apply unpublished preloads");
		for(MetaItem mi : preloads) {
			if(mi.isPreload) {
			
				if(!GeneralUtilityMethods.hasColumn(cResults, tableName, mi.columnName)) {
					String type = mi.type;
					if(type.equals("string")) {
						type = "text";
					}
					GeneralUtilityMethods.alterColumn(cResults, tableName, type, mi.columnName, false);						
				} 
			}
		} 
	}



	private QuestionDetails getQuestionDetails(Connection sd, int qId) throws Exception {

		QuestionDetails qd = new QuestionDetails();
		PreparedStatement pstmt = null;;

		String sqlGetQuestionDetails = "select q.column_name, q.appearance, q.qtype, f.table_name, f.reference, q.compressed "
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
				qd.reference = rsDetails.getBoolean(5);
				qd.compressed = rsDetails.getBoolean(6);
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


