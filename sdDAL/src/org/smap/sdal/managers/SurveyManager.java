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

package org.smap.sdal.managers;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.ChangeElement;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeLog;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Geometry;
import org.smap.sdal.model.GroupDetails;
import org.smap.sdal.model.Instance;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.InstanceMeta;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Line;
import org.smap.sdal.model.ManifestInfo;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Point;
import org.smap.sdal.model.Polygon;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class SurveyManager {

	private static Logger log =
			Logger.getLogger(SurveyManager.class.getName());

	LogManager lm = new LogManager();		// Application log
	String tz;

	private ResourceBundle localisation;
	private String gPrimaryKey;			// Set to primary key of top form in top level survey when getting results
	private String gHRK;					// Set to the HRK of the top level survey when getting results
	
	public SurveyManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
	}
	
	private String sqlGetOptions = "select o.o_id as o_id, "
			+ "o.ovalue as value, "
			+ "o.label_id, "
			+ "o.externalfile, "
			+ "o.cascade_filters, "
			+ "o.column_name, "
			+ "o.display_name, "
			+ "o.published,"
			+ "o.seq as seq "
			+ "from option o "
			+ "where o.l_id = ? "
			+ "order by o.seq";
	
	public ArrayList<Survey> getSurveys(Connection sd, PreparedStatement pstmt,
			String user, 
			boolean getDeleted, 
			boolean getBlocked,
			int projectId,			// Set to 0 to get all surveys regardless of project
			boolean superUser,
			boolean onlyGroup,		// Only get surveys that are available to be used as groups
			boolean getGroupDetails
			) throws SQLException {

		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request


		StringBuffer sqlGetGroupDetails = new StringBuffer("select p.name, s.display_name "
				+ "from survey s, project p "
				+ "where s.p_id = p.id "
				+ "and s.s_id = ?");
		PreparedStatement pstmtGetGroupDetails = null;
		
		ResultSet resultSet = null;
		StringBuffer sql = new StringBuffer("");
		sql.append("select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, "
				+ "s.ident, s.managed_id, s.version, s.loaded_from_xls, p.name, p.id, p.tasks_only,"
				+ "s.group_survey_id, s.public_link, o.can_submit, s.hide_on_device "
				+ "from survey s, users u, user_project up, project p, organisation o "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and p.o_id = u.o_id "
				+ "and u.o_id = o.id "
				+ "and u.ident = ? "
				+ "and s.hidden = 'false' ");

		if(!superUser) {					// Add RBAC
			sql.append(GeneralUtilityMethods.getSurveyRBAC());
		}
		
		// only return surveys in the users organisation unit + assigned project id 
		// If a specific valid project id was passed then restrict surveys to that project as well

		if(projectId != 0) {
			sql.append("and s.p_id = ? ");
		}
		if(!getDeleted) {
			sql.append("and s.deleted = 'false' ");
		} 
		if(!getBlocked) {
			sql.append("and s.blocked = 'false' ");
		}
		if(onlyGroup) {
			sql.append("and s.group_survey_id = 0 ");
		}
		sql.append("order BY s.display_name ");

		pstmt = sd.prepareStatement(sql.toString());	
		int idx = 1;
		pstmt.setString(idx++, user);
		if(!superUser) {
			pstmt.setString(idx++, user);	// Second user entry for RBAC
		}
		if(projectId != 0) {
			pstmt.setInt(idx++, projectId);
		}
		log.info("Get surveys: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		try {
			pstmtGetGroupDetails = sd.prepareStatement(sqlGetGroupDetails.toString());
			
			while (resultSet.next()) {						
	
				Survey s = new Survey();
				s.setId(resultSet.getInt(1));
				//s.setName(resultSet.getString(2));
				s.setDisplayName(resultSet.getString(3));
				s.setDeleted(resultSet.getBoolean(4));
				
				boolean surveyBlocked = resultSet.getBoolean(5);
				boolean orgCanSubmit = resultSet.getBoolean("can_submit");				
				s.setBlocked(surveyBlocked || !orgCanSubmit);
				
				s.setIdent(resultSet.getString(6));
				s.setManagedId(resultSet.getInt(7));
				s.setVersion(resultSet.getInt(8));
				s.setLoadedFromXLS(resultSet.getBoolean(9));
				s.setProjectName(resultSet.getString(10));
				s.setProjectId(resultSet.getInt(11));
				s.setProjectTasksOnly(resultSet.getBoolean(12));
				s.groupSurveyId = resultSet.getInt(13);
				s.publicLink = resultSet.getString(14);
				s.setHideOnDevice(resultSet.getBoolean("hide_on_device"));
				
				if(getGroupDetails && s.groupSurveyId > 0) {
					pstmtGetGroupDetails.setInt(1, s.groupSurveyId);
					ResultSet rsGroup = pstmtGetGroupDetails.executeQuery();
					if(rsGroup.next()) {
						s.groupSurveyDetails = rsGroup.getString(1) + " : " + rsGroup.getString(2);
					}
				}
	
				surveys.add(s);
			} 
		} finally {
			if(pstmtGetGroupDetails != null) {try{pstmtGetGroupDetails.close();}catch(Exception r) {}}
		}
		return surveys;

	}

	public ArrayList<Survey> getSurveysAndForms(Connection sd, 
			String user, 
			boolean superUser
			) throws SQLException {

		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request

		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		StringBuffer sql = new StringBuffer("");
		sql.append("select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, "
				+ "s.ident, s.managed_id, s.version, s.loaded_from_xls, o.can_submit "
				+ "from survey s, users u, user_project up, project p, organisation o "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and p.o_id = u.o_id "
				+ "and u.o_id = o.id "
				+ "and u.ident = ? "
				+ "and s.deleted = 'false' ");

		if(!superUser) {
			// Add RBAC
			sql.append(" ");
			sql.append(GeneralUtilityMethods.getSurveyRBAC());
		}

		sql.append(" order BY s.display_name");

		// Get subforms only
		String sqlForms = "select f_id, name from form where s_id = ? and parentform != 0";
		PreparedStatement pstmtGetForms = null;

		try {
			pstmt = sd.prepareStatement(sql.toString());	
			pstmtGetForms = sd.prepareStatement(sqlForms);
			int idx = 1;
			pstmt.setString(idx++, user);
			if(!superUser) {
				pstmt.setString(idx++, user);	// Second user entry for RBAC
			}

			log.info("Get surveys and forms: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {						

				Survey s = new Survey();
				s.setId(resultSet.getInt(1));
				//s.setName(resultSet.getString(2));
				s.setDisplayName(resultSet.getString(3));
				s.setDeleted(resultSet.getBoolean(4));
				
				boolean surveyBlocked = resultSet.getBoolean(5);
				boolean orgCanSubmit = resultSet.getBoolean("can_submit");				
				s.setBlocked(surveyBlocked || !orgCanSubmit);
				
				s.setIdent(resultSet.getString(6));
				s.setManagedId(resultSet.getInt(7));
				s.setVersion(resultSet.getInt(8));
				s.setLoadedFromXLS(resultSet.getBoolean(9));

				pstmtGetForms.setInt(1, s.id);
				ResultSet rsForms = pstmtGetForms.executeQuery();
				while(rsForms.next()) {
					if(s.forms == null) {
						s.forms = new ArrayList<Form> ();
					}
					Form f = new Form();
					f.id = rsForms.getInt(1);
					f.name = rsForms.getString(2);
					s.forms.add(f);
				}
				surveys.add(s);
			} 
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForms != null) {pstmtGetForms.close();	}} catch (SQLException e) {	}
		}
		return surveys;

	}

	/*
	 * Return true if there is already a survey with the supplied display name and project id
	 */
	public boolean surveyExists(Connection sd, String displayName, int projectId) {
		boolean exists = false;

		ResultSet resultSet = null;
		String sql = "select count(*) from survey s "
				+ " where s.display_name = ? "
				+ " and s.p_id = ?;";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, displayName);
			pstmt.setInt(2, projectId);

			log.info("Check for existence of survey: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {		
				int count = resultSet.getInt(1);
				if(count > 0) {
					exists = true;
				}
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage());
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){};
		}
		return exists;
	}


	/*
	 * Get a survey definition
	 */
	public Survey getById(
			Connection sd, 
			Connection cResults,
			String user,
			int sId,
			boolean full,						// Get the full details of the survey
			String basePath,
			String instanceId,					// If set get the results for this instance
			boolean getResults,					// Set to true to get results, if set and instanceId is null then blank data will be added
			boolean generateDummyValues,			// Set to true when getting results to fill a form with dummy values if there are no results
			boolean getPropertyTypeQuestions,	// Set to true to get property questions such as _device
			boolean getSoftDeleted,				// Set to true to get soft deleted questions
			boolean getHrk,						// Set to true to return HRK as a question if it exists in the survey
			String getExternalOptions,			// external || internal || real (get external if they exist else get internal)
			boolean getChangeHistory,	
			boolean getRoles,					// Only applies if "full" has been specified
			boolean superUser,
			String geomFormat,
			boolean referenceSurveys,					// follow links to child surveys
			boolean onlyGetLaunched		// Use with referenceSuveys to restrict results to those created by launching another form
			) throws SQLException, Exception {

		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		StringBuffer sql = new StringBuffer();
		sql.append("select s.s_id, s.name, s.ident, s.display_name, s.deleted, s.blocked, p.name, p.id,"
				+ "s.def_lang, s.task_file, s.timing_data, u.o_id, s.class,"
				+ "s.instance_name, s.hrk, s.based_on, s.created, s.loaded_from_xls,"
				+ "s.pulldata, "
				+ "s.version, "
				+ "s.key_policy, "
				+ "s.exclude_empty,"
				+ "s.meta,"
				+ "s.group_survey_id,"
				+ "s.public_link, "
				+ "o.e_id,"
				+ "s.hide_on_device, "
				+ "s.audit_location_data, "
				+ "s.track_changes "
				+ "from survey s, users u, user_project up, project p, organisation o "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and u.o_id = o.id "
				+ "and u.ident = ? "
				+ "and s.s_id = ? ");

		if(!superUser) {
			sql.append(GeneralUtilityMethods.getSurveyRBAC());	// Add RBAC
		}

		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql.toString());	 			
			pstmt.setString(1, user);
			pstmt.setInt(2, sId);
			if(!superUser) {
				pstmt.setString(3, user);		// RBAC check
			}

			log.info("Get Survey info: " + pstmt.toString());

			resultSet = pstmt.executeQuery();	
			if (resultSet.next()) {							

				s = new Survey();
				s.setId(resultSet.getInt(1));
				//s.setName(resultSet.getString(2));
				s.setIdent(resultSet.getString(3));
				s.setDisplayName(resultSet.getString(4));
				s.setDeleted(resultSet.getBoolean(5));
				s.blocked = resultSet.getBoolean(6);
				s.setProjectName(resultSet.getString(7));
				s.setProjectId(resultSet.getInt(8));
				s.def_lang = resultSet.getString(9);
				s.task_file = resultSet.getBoolean(10);
				s.timing_data = resultSet.getBoolean(11);
				s.o_id = resultSet.getInt(12);
				s.surveyClass = resultSet.getString(13);
				s.instanceNameDefn = GeneralUtilityMethods.convertAllXpathNames(resultSet.getString(14), true);
				s.hrk = resultSet.getString(15);
				s.basedOn = resultSet.getString(16);
				s.created = resultSet.getTimestamp(17);
				s.loadedFromXLS = resultSet.getBoolean(18);

				Type type = new TypeToken<ArrayList<Pulldata>>(){}.getType();
				s.pulldata = new Gson().fromJson(resultSet.getString(19), type); 

				s.version = resultSet.getInt(20);
				s.key_policy = resultSet.getString(21);
				s.exclude_empty = resultSet.getBoolean(22);
				String meta = resultSet.getString(23);
				if(meta != null) {
					s.meta = new Gson().fromJson(meta, 
							new TypeToken<ArrayList<MetaItem>>(){}.getType()); 
				} else {
					getLegacyMeta();
				}
				s.groupSurveyId = resultSet.getInt(24);
				s.publicLink = resultSet.getString(25);
				s.e_id = resultSet.getInt(26);
				s.setHideOnDevice(resultSet.getBoolean(27));
				s.audit_location_data = resultSet.getBoolean(28);
				s.track_changes = resultSet.getBoolean(29);
				// Get the pdf template
				File templateFile = GeneralUtilityMethods.getPdfTemplate(basePath, s.displayName, s.p_id);
				if(templateFile.exists()) {
					s.pdfTemplateName = templateFile.getName();
				}
			} else {
				log.info("Error: survey not found");
			}

			if(full && s != null) {

				populateSurvey(sd, cResults, s, basePath, user, superUser, getPropertyTypeQuestions, getExternalOptions, 
						getSoftDeleted,
						getHrk,
						getChangeHistory,
						getRoles);			// Add forms, questions, options
				
				
				if(getResults) {								// Add results

					Form ff = s.getFirstForm();
					s.instance.results = getResults(
							ff, 
							s.getFormIdx(ff.id), 
							0,	
							sd,
							cResults, 
							user,							
							basePath,
							getPropertyTypeQuestions,	
							getHrk,		
							getExternalOptions,
							superUser,
							instanceId, 		// top form
							0, 				// parent key (child forms)
							null,			// key question name (child surveys)
							null,			// key question value (child surveys)
							s, 
							generateDummyValues,
							geomFormat,
							s.o_id,
							true,
							referenceSurveys	,	// Whether or not to get referenced surveys
							onlyGetLaunched		// Use with referenceSuveys to restrict results to those created by launching another form
							);
					if(s.instance.results.size() > 0) {
						ArrayList<Result> topForm = s.instance.results.get(0);
						// Get the user ident that submitted the survey
						for(Result r : topForm) {
							if(r.type.equals("user")) {
								s.instance.user = r.value;
								break;
							}
						}
					}

				}
			}
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e){};
		}
		return s;

	}

	/*
	 * Create a new survey
	 */
	public int createNewSurvey(
			Connection sd, 
			String name,
			int projectId,
			boolean existing,
			int existingSurveyId,
			boolean sharedResults,
			String user

			) throws SQLException, Exception {

		int sId;
		int fId;
		String ident = null;
		String tablename = null;
		String existingSurvey = null;
		String existingMeta = null;
		int existingFormId = 0;
		boolean sdAutoCommitSetFalse = false;
		ArrayList<MetaItem> meta = new ArrayList<> ();
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		String sqlCreateSurvey = "insert into survey ( s_id, display_name, deleted, p_id, version, last_updated_time, "
				+ "based_on, group_survey_id, meta, created)" +
				" values (nextval('s_seq'), ?, 'false', ?, 1, now(), ?, ?, ?, now())";
		PreparedStatement pstmtCreateSurvey = null;

		String sqlUpdateSurvey = "update survey set name = ?, ident = ? where s_id = ?";
		PreparedStatement pstmtUpdateSurvey = null;

		String sqlCreateForm = "insert into form ( f_id, s_id, name, table_name, parentform, repeats, path) " +
				" values (nextval('f_seq'), ?, 'main', ?, 0, null, '/main');";
		PreparedStatement pstmtCreateForm = null;

		String sqlGetSource = "select s.display_name, f.f_id, s.meta from survey s, form f "
				+ "where s.s_id = f.s_id "
				+ "and s.s_id = ? "
				+ "and f.parentform = 0";
		PreparedStatement pstmtGetSource = null;

		try {

			if(existing) {
				pstmtGetSource = sd.prepareStatement(sqlGetSource);
				pstmtGetSource.setInt(1, existingSurveyId);
				ResultSet rsGetSource = pstmtGetSource.executeQuery();
				if(rsGetSource.next()) {
					existingSurvey = rsGetSource.getString(1);
					existingFormId = rsGetSource.getInt(2);
					existingMeta = rsGetSource.getString(3);
				}
			}
			if(sd.getAutoCommit()) {
				sdAutoCommitSetFalse = true;
				log.info("Set autocommit sd false");
				sd.setAutoCommit(false);
			}			

			// 1 Create basic survey
			pstmtCreateSurvey = sd.prepareStatement(sqlCreateSurvey, Statement.RETURN_GENERATED_KEYS);		
			pstmtCreateSurvey.setString(1, name);
			pstmtCreateSurvey.setInt(2, projectId);
			if(existing) {
				pstmtCreateSurvey.setString(3, existingSurvey);
			} else {
				pstmtCreateSurvey.setString(3, null);
			}
			if(sharedResults) {
				pstmtCreateSurvey.setInt(4, existingSurveyId);
			} else {
				pstmtCreateSurvey.setInt(4, 0);
			}
			if(existing) {
				pstmtCreateSurvey.setString(5,  existingMeta);
			} else {
				int metaId = -1000;
				meta.add(new MetaItem(metaId--, "string", "instanceID", null, "instanceid", null, false, null, null));
				meta.add(new MetaItem(metaId--, "string", "instanceName", null, "instancename", null, false, null, null));
				meta.add(new MetaItem(metaId--, "dateTime", "_start", "start", "_start", "timestamp", true, "start", null));
				meta.add(new MetaItem(metaId--, "dateTime", "_end", "end", "_end", "timestamp", true, "end", null));
				meta.add(new MetaItem(metaId--, "string", "_device", "deviceid", "_device", "property", true, "device", null));
				pstmtCreateSurvey.setString(5,  gson.toJson(meta));
			}

			log.info("Create new survey: " + pstmtCreateSurvey.toString());
			pstmtCreateSurvey.execute();
			ResultSet rs = pstmtCreateSurvey.getGeneratedKeys();
			rs.next();

			// 2 Update values dependent on the sId
			sId = rs.getInt(1);
			ident = "s" + projectId +"_" + sId;

			pstmtUpdateSurvey = sd.prepareStatement(sqlUpdateSurvey);
			pstmtUpdateSurvey.setString(1, ident);
			pstmtUpdateSurvey.setString(2,  ident);
			pstmtUpdateSurvey.setInt(3,  sId);

			log.info("Create new survey part 2: " + pstmtUpdateSurvey.toString());
			pstmtUpdateSurvey.execute();

			/*
			 * 3. Create forms
			 */
			if(existing) {
				QuestionManager qm = new QuestionManager(localisation);
				qm.duplicateLanguages(sd, sId, existingSurveyId);
				qm.duplicateForm(sd, sId, existingSurveyId, 
						"main", existingFormId, "", 0, 0, sharedResults, null, null);	// note: top level form cannot have repeats

			} else {

				// 4. Create default language
				ArrayList<Language> languages = new ArrayList<Language> ();
				languages.add(new Language(-1, "language"));
				GeneralUtilityMethods.setLanguages(sd, sId, languages);

				// 5 Create a new empty form (except for default questions)
				tablename = "s" + sId + "_main";

				pstmtCreateForm = sd.prepareStatement(sqlCreateForm, Statement.RETURN_GENERATED_KEYS);
				pstmtCreateForm.setInt(1,  sId);
				pstmtCreateForm.setString(2,  tablename);

				log.info("Create new form: " + pstmtCreateForm.toString());
				pstmtCreateForm.execute();
			}

			lm.writeLog(sd, sId, user, "create survey", "Survey created in online editor");
			sd.commit();

		} catch (SQLException e) {
			try{sd.rollback();} catch(Exception ex) {};
			throw e;
		} catch (Exception e) {
			try{sd.rollback();} catch(Exception ex) {};
			throw e;
		} finally {

			if(sdAutoCommitSetFalse) {
				log.info("Set autocommit sd true");
				sdAutoCommitSetFalse = false;
				try{sd.setAutoCommit(true);} catch(Exception ex) {};
			}

			if(pstmtCreateSurvey != null) try {pstmtCreateSurvey.close();} catch(Exception e){};
			if(pstmtUpdateSurvey != null) try {pstmtUpdateSurvey.close();} catch(Exception e){};
			if(pstmtCreateForm != null) try {pstmtCreateForm.close();} catch(Exception e){};
			if(pstmtGetSource != null) try {pstmtGetSource.close();} catch(Exception e){};
		}

		return sId;

	}

	/*
	 * Get all the surveys in the user's organisation that reference the passed in CSV file
	 *  Note even surveys that are in projects not enabled for the user should be returned
	 */
	public ArrayList<Survey> getByOrganisationAndExternalCSV(Connection sd, 
			String user, 
			String csvFileName			
			)  {

		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request

		int idx = csvFileName.lastIndexOf('.');
		String csvRoot = csvFileName;
		if(idx > 0) {
			csvRoot = csvFileName.substring(0, idx);
		}

		// Escape csvRoot
		csvRoot = csvRoot.replace("\'", "\'\'");

		ResultSet resultSet = null;
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident "
				+ "from survey s, users u, project p, question q, form f "
				+ "where s.s_id = f.s_id "
				+ "and f.f_id = q.f_id "
				+ "and (q.appearance like '%search(''" + csvRoot + "''%' or "
				+ "q.calculate like '%pulldata(''" + csvRoot + "''%' ) "
				+ "and s.p_id = p.id "
				+ "and s.deleted = 'false' "
				+ "and s.blocked = 'false' "
				+ "and p.o_id = u.o_id "
				+ "and u.ident = ? "
				+ "order BY s.display_name;";


		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);

			log.info("Get surveys that use the uploaded CSV: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {								

				Survey s = new Survey();
				s.setId(resultSet.getInt(1));
				//s.setName(resultSet.getString(2));
				s.setDisplayName(resultSet.getString(3));
				s.setDeleted(resultSet.getBoolean(4));
				s.setBlocked(resultSet.getBoolean(5));
				s.setIdent(resultSet.getString(6));

				surveys.add(s);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
		}

		return surveys;

	}

	/*
	 * Get a survey's details
	 */
	private void populateSurvey(Connection sd, Connection cResults, Survey s, String basePath, 
			String user,
			boolean superUser,
			boolean getPropertyTypeQuestions,
			String getExternalOptions,
			boolean getSoftDeleted,
			boolean getHrk,
			boolean getChangeHistory,
			boolean getRoles) throws Exception {

		/*
		 * Prepared Statements
		 */

		// SQL to get the forms belonging to this survey
		ResultSet rsGetForms = null;
		String sqlGetForms = "select f.f_id, "
				+ "f.name, "
				+ "f.parentform, "
				+ "f.parentquestion, "
				+ "f.table_name, "
				+ "f.reference, "
				+ "f.merge,"
				+ "f.replace  "
				+ "from form f where f.s_id = ?;";
		PreparedStatement pstmtGetForms = sd.prepareStatement(sqlGetForms);	

		// SQL to get the choice lists in this survey
		ResultSet rsGetLists = null;
		String sqlGetLists = "select l_id, "
				+ "name "
				+ "from listname "
				+ "where s_id = ?;";
		PreparedStatement pstmtGetLists = sd.prepareStatement(sqlGetLists);

		// SQL to get the options belonging to a choice list		
		ResultSet rsGetOptions = null;
		PreparedStatement pstmtGetOptions = sd.prepareStatement(sqlGetOptions);

		// Get the server side calculations
		ResultSet rsGetSSC = null;
		String sqlGetSSC = "SELECT ssc.id, ssc.name, ssc.function, ssc.parameters, ssc.units, f.name, f.f_id " +
				"FROM ssc ssc, form f WHERE ssc.s_id = ? AND ssc.f_id = f.f_id ORDER BY id";
		PreparedStatement pstmtGetSSC = sd.prepareStatement(sqlGetSSC);

		// Get the changes that have been made to this survey
		ResultSet rsGetChanges = null;
		String sqlGetChanges = "SELECT c.changes, "
				+ "c.c_id, "
				+ "c.version, "
				+ "u.name, "
				+ "c.updated_time at time zone '" + tz + "',"
				+ "c.apply_results, "
				+ "c.success, "
				+ "c.msg " 
				+ "from survey_change c, users u "
				+ "where c.s_id = ? "
				+ "and c.user_id = u.id "
				+ "and c.visible = true "
				+ "order by c_id desc; ";
		PreparedStatement pstmtGetChanges = sd.prepareStatement(sqlGetChanges);
		
		// Get the available languages
		s.languages = GeneralUtilityMethods.getLanguages(sd, s.id);

		// Get the organisation id
		int oId = GeneralUtilityMethods.getOrganisationId(sd, user);

		// Set the default language if it has not previously been set	
		if(s.def_lang == null) {
			if(s.languages != null && s.languages.size() > 0) {
				s.def_lang = s.languages.get(0).name;
			} else {
				s.def_lang = "language";
			}
		}

		QuestionManager qm = new QuestionManager(localisation);

		// Get the Forms
		pstmtGetForms.setInt(1, s.id);
		log.info("Get forms: " + pstmtGetForms.toString());
		rsGetForms = pstmtGetForms.executeQuery();

		while (rsGetForms.next()) {								
			Form f = new Form();
			f.id = rsGetForms.getInt(1);
			f.name = rsGetForms.getString(2);
			f.parentform =rsGetForms.getInt(3); 
			f.parentQuestion = rsGetForms.getInt(4);
			f.tableName = rsGetForms.getString(5);
			f.reference = rsGetForms.getBoolean(6);
			f.merge = rsGetForms.getBoolean(7);
			f.replace = rsGetForms.getBoolean(8);

			f.questions = qm.getQuestionsInForm(sd, 
					cResults,
					s.id,
					f.id, 
					getSoftDeleted, 
					getPropertyTypeQuestions, getHrk, 
					f.parentform,
					s.hrk,
					s.languages.size(),
					f.tableName,
					basePath,
					oId,
					s);

			s.forms.add(f);
		} 

		// Add the parentFormIndex and parent question index to sub forms
		for(Form f : s.forms) {
			if(f.parentform > 0) {
				for(int i = 0; i < s.forms.size(); i++) {
					Form aForm = s.forms.get(i);
					if(aForm.id == f.parentform) {
						f.parentFormIndex = i;
						for(int j = 0; j < aForm.questions.size(); j++) {
							Question q = aForm.questions.get(j);
							if(q.id == f.parentQuestion) {
								f.parentQuestionIndex = j;
								break;
							}
						}
						break;
					}
				}
			} else {
				f.parentFormIndex = -1;
				f.parentQuestionIndex = -1;
			}
		}

		/*
		 * Get the option lists
		 */
		pstmtGetLists.setInt(1, s.id);
		log.info("Get lists for survey: " + pstmtGetLists.toString());
		rsGetLists = pstmtGetLists.executeQuery();

		int idx = 0;
		while(rsGetLists.next()) {

			int listId = rsGetLists.getInt(1);
			String listName = rsGetLists.getString(2);

			OptionList optionList = new OptionList ();

			boolean external = false;
			if(getExternalOptions.equals("external")) {
				external = true;
			} else if(getExternalOptions.equals("internal")) {
				external = false;
			} else if(getExternalOptions.equals("real")) {
				external = GeneralUtilityMethods.listHasExternalChoices(sd, s.id, listId);
			}

			// Get external options if required
			ArrayList<Option> externalOptions = new ArrayList<> ();
			if(external) {
				int qId = GeneralUtilityMethods.getQuestionFromList(sd, s.id, listId);
				externalOptions = GeneralUtilityMethods.getExternalChoices(sd, 
						cResults, localisation, user, oId, s.id, qId, null, s.ident, tz);
			} 
			
			// Get options from meta definition - insert external if required when not a numeric option
			optionList.options = new ArrayList<Option> ();
			pstmtGetOptions.setInt(1, listId);
			
			if(idx++ == 0) {
				log.info("SQL Get options: " + pstmtGetOptions.toString());
			}
			rsGetOptions = pstmtGetOptions.executeQuery();
	
			Type hmType = new TypeToken<HashMap<String, String>>(){}.getType();		// Used to translate cascade filters json
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			boolean externalAdded = false;
			while(rsGetOptions.next()) {
				Option o = new Option();
				o.id = rsGetOptions.getInt(1);
				o.value = rsGetOptions.getString(2);
				o.text_id = rsGetOptions.getString(3);
				o.externalFile = rsGetOptions.getBoolean(4);
				String cascade_filters = rsGetOptions.getString(5);
				if(cascade_filters != null && !cascade_filters.equals("null")) {
					try {
						o.cascade_filters = gson.fromJson(cascade_filters, hmType);
						for (String key : o.cascade_filters.keySet()) {
							s.filters.put(key, true);
						}
	
					} catch (Exception e) {
						log.log(Level.SEVERE, e.getMessage(), e);		// Ignore errors as this service does not support the old non json cascade format
					}
				} else {
					o.cascade_filters = new HashMap<String, String> ();	// An empty object
				}
				o.columnName = rsGetOptions.getString(6);
				o.display_name = rsGetOptions.getString(7);
				o.published = rsGetOptions.getBoolean(8);
	
				// Get the labels for the option
				UtilityMethodsEmail.getLabels(sd, s, o.text_id, null, o.labels, basePath, oId);
				
				// Check for numeric value - if external options are required then a numeric value indicates a static choice
				boolean isInteger = false;
				if(external) {
					try {
						Integer.parseInt(o.value);
						isInteger = true;
					} catch (Exception e) {
						
					}
				}
				
				if(!external || isInteger) {
					optionList.options.add(o);
				} else if(!externalAdded) {
					externalAdded = true;		// Don't double up if someone uses a non numeric static by mistake
					optionList.options.addAll(externalOptions);
				}
			}

			s.optionLists.put(listName, optionList);

		}

		// Add the server side calculations
		pstmtGetSSC.setInt(1, s.getId());
		rsGetSSC= pstmtGetSSC.executeQuery();

		while (rsGetSSC.next()) {
			ServerSideCalculate ssc = new ServerSideCalculate();
			ssc.setId(rsGetSSC.getInt(1));
			ssc.setName(rsGetSSC.getString(2));
			ssc.setFunction(rsGetSSC.getString(3));
			ssc.setUnits(rsGetSSC.getString(5));
			ssc.setForm(rsGetSSC.getString(6));
			ssc.setFormId(rsGetSSC.getInt(7));
			s.sscList.add(ssc);
		}

		// Add the change log
		if(getChangeHistory) {
			pstmtGetChanges.setInt(1, s.getId());
			log.info("Get change log: " + pstmtGetChanges.toString());
			rsGetChanges = pstmtGetChanges.executeQuery();

			Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();

			while (rsGetChanges.next()) {

				ChangeLog cl = new ChangeLog();

				cl.change = gson.fromJson(rsGetChanges.getString(1), ChangeElement.class);

				cl.cId = rsGetChanges.getInt(2);
				cl.version = rsGetChanges.getInt(3);
				cl.userName = rsGetChanges.getString(4);
				cl.updatedTime = rsGetChanges.getTimestamp(5);
				cl.apply_results = rsGetChanges.getBoolean(6);
				cl.success = rsGetChanges.getBoolean(7) || !cl.apply_results;	// Set the update of the results database to success automatically if a change does not need to be applied
				cl.msg = rsGetChanges.getString(8);

				s.changes.add(cl);
			}
		}
		
		// Get the roles
		if(getRoles) {
			RoleManager rm = new RoleManager(localisation);
			ArrayList<Role> roles = rm.getSurveyRoles(sd, s.id, oId, true, user, superUser);	// Get enabled roles
			for(Role r : roles) {
				s.roles.put(r.name, r);
			}
		}
		
		// Get the Meta Items
		if(getPropertyTypeQuestions) {
			s.meta = GeneralUtilityMethods.getPreloads(sd, s.id);
		}


		// Close statements
		try { if (pstmtGetForms != null) {pstmtGetForms.close();}} catch (SQLException e) {}
		try { if (pstmtGetOptions != null) {pstmtGetOptions.close();}} catch (SQLException e) {}
		try { if (pstmtGetSSC != null) {pstmtGetSSC.close();}} catch (SQLException e) {}
		try { if (pstmtGetChanges != null) {pstmtGetChanges.close();}} catch (SQLException e) {}
		//try { if (pstmtGetLinkable != null) {pstmtGetLinkable.close();}} catch (SQLException e) {}
	}


	/*
	 * Get the project id and the block status of a survey given its ident
	 */
	public Survey getSurveyId(Connection sd, String key) {

		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.p_id, s.s_id, s.blocked, s.class, s.deleted, "
				+ "s.display_name, s.key_policy, s.auto_updates, "
				+ "s.managed_id,"
				+ "s.ident,"
				+ "s.version,"
				+ "s.meta,"
				+ "p.o_id,"
				+ "o.e_id,"
				+ "o.can_submit "
				+ "from survey s,"
				+ "project p,"
				+ "organisation o "
				+ "where s.ident = ? "
				+ "and s.p_id = p.id "
				+ "and p.o_id = o.id";


		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, key);			
			log.info("Get survey id: " + pstmt.toString());

			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {						
				s = new Survey();
				s.setProjectId(resultSet.getInt(1));
				s.setId(resultSet.getInt(2));
				
				boolean surveyBlocked = resultSet.getBoolean(3);
				boolean orgCanSubmit = resultSet.getBoolean("can_submit");				
				s.setBlocked(surveyBlocked || !orgCanSubmit);
				
				s.surveyClass = resultSet.getString(4);
				s.deleted = resultSet.getBoolean(5);
				s.displayName = resultSet.getString(6);
				s.key_policy = resultSet.getString(7);
				s.autoUpdates = resultSet.getString(8);
				s.managed_id = resultSet.getInt(9);
				s.ident = resultSet.getString(10);
				s.version = resultSet.getInt(11);
				String meta = resultSet.getString(12);
				if(meta != null) {
					s.meta = new Gson().fromJson(meta, 
							new TypeToken<ArrayList<MetaItem>>(){}.getType()); 
				} else {
					getLegacyMeta();
				}
				s.o_id = resultSet.getInt(13);
				s.e_id = resultSet.getInt(14);
				
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch (Exception e) {}};
		}

		return s;

	}

	/*
	 * Apply an array of change sets to a survey
	 * Apply updates
	 */
	public ChangeResponse applyChangeSetArray(Connection sd, 
			Connection cResults,
			int sId, String ident, ArrayList<ChangeSet> changes,
			boolean logIndividualChangeSets) throws Exception {

		ChangeResponse resp = new ChangeResponse();	// Response object
		resp.changeSet = changes;

		int userId = -1;
		ResultSet rs = null;
		boolean sdAutoCommitSetFalse = false;

		PreparedStatement pstmtChangeLog = null;
		PreparedStatement pstmt = null;

		try {

			String sqlChangeLog = "insert into survey_change " +
					"(s_id, version, changes, user_id, apply_results, visible, updated_time) " +
					"values(?, ?, ?, ?, 'true', ?, ?)";
			pstmtChangeLog = sd.prepareStatement(sqlChangeLog);

			/*
			 * Get the user id
			 * This should be saved rather than the ident as a user could be deleted
			 *  then a new user created with the same ident but its a different user
			 */
			userId = GeneralUtilityMethods.getUserId(sd, ident);

			if(sd.getAutoCommit()) {
				log.info("Set autocommit sd false");
				sdAutoCommitSetFalse = true;
				sd.setAutoCommit(false);
			}

			/*
			 * Lock the survey
			 * update version number of survey and get the new version
			 */
			String sqlUpdateVersion = "update survey set version = version + 1 where s_id = ?";
			String sqlGetVersion = "select version from survey where s_id = ?";
			pstmt = sd.prepareStatement(sqlUpdateVersion);
			pstmt.setInt(1, sId);
			pstmt.execute();
			pstmt.close();

			pstmt = sd.prepareStatement(sqlGetVersion);
			pstmt.setInt(1, sId);
			rs = pstmt.executeQuery();
			rs.next();
			resp.version = rs.getInt(1);
			pstmt.close();


			for(ChangeSet cs : changes) {			

				// Process each change set separately and roll back to a save point if it fails
				Savepoint sp = sd.setSavepoint();
				try {

					log.info("SurveyManager, applyChanges. Change set type: " + cs.changeType);
					if(cs.changeType.equals("label")) {

						applyLabel(sd, pstmtChangeLog, cs.items, sId, userId, resp.version, logIndividualChangeSets);

					} else if(cs.changeType.equals("property") && !cs.type.equals("option")) {

						// Update a property
						applyQuestionProperty(sd, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, logIndividualChangeSets);

					} else if(cs.changeType.equals("question")) {

						// Add/delete/move questions
						applyQuestion(sd, cResults, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action, logIndividualChangeSets);

					} else if(cs.changeType.equals("option") || (cs.changeType.equals("property") && cs.type.equals("option"))) {

						// Add/delete options changed by the editor
						applyOptionFromEditor(sd, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action, logIndividualChangeSets);

					} else if(cs.changeType.equals("optionlist")) {

						// Add/delete/move questions
						applyOptionList(sd, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action, logIndividualChangeSets);

					} else {
						log.info("Error: unknown changeset type: " + cs.changeType);
						throw new Exception("Error: unknown changeset type: " + cs.changeType);
					}

					// Success
					cs.updateFailed = false;
					resp.success++;
				} catch (Exception e) {

					// Failure
					sd.rollback(sp);
					log.info("Error: " + e.getMessage());
					cs.updateFailed = true;
					cs.errorMsg = e.getMessage();
					resp.failed++;
				}

			}

			// Record the message so that devices can be notified
			MessagingManager mm = new MessagingManager();
			mm.surveyChange(sd, sId, 0);
			// Update the form dependencies so that when new results are received it is simple to identify the impacted forms			
			GeneralUtilityMethods.updateFormDependencies(sd, sId);

			if(resp.success > 0) {
				sd.commit();
				log.info("Survey update to version: " + resp.version + ". " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			} else {
				sd.rollback();
				log.info("Survey version not updated: " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			}

		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if(sdAutoCommitSetFalse) {
				log.info("Set autocommit sd true");
				sdAutoCommitSetFalse = false;
				sd.setAutoCommit(true);
			}
			try {if (pstmtChangeLog != null) {pstmtChangeLog.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return resp;
	}

	/*
	 * ========================= Type specific update functions
	 */

	/*
	 * Apply label changes
	 * These are just changes to question labels, commonly created when editing translations
	 */
	public void applyLabel(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			boolean logIndividualChangeSets) throws Exception {

		String transType = null;
		PreparedStatement pstmtLangOldVal = null;
		PreparedStatement pstmtLangNew = null;
		PreparedStatement pstmtNewQuestionLabel = null;
		PreparedStatement pstmtNewOptionLabel = null;
		PreparedStatement pstmtNewQuestionHint = null;
		PreparedStatement pstmtDeleteLabel = null;
		PreparedStatement pstmtGetOptionTextId = null;
		PreparedStatement pstmtGetQuestionTextId = null;

		try {

			// Get the text id for an option update
			String sqlGetOptionTextId = "select label_id from option where l_id = ? and ovalue = ?; ";
			pstmtGetOptionTextId = connectionSD.prepareStatement(sqlGetOptionTextId);

			// Get the text id for a question update
			String sqlGetQuestionTextId = "select qtext_id, infotext_id from question where q_id = ?; ";
			pstmtGetQuestionTextId = connectionSD.prepareStatement(sqlGetQuestionTextId);

			// Create prepared statements, one for the case where an existing value is being updated
			String sqlLangOldVal = "update translation set value = ? " +
					"where s_id = ? and language = ? and text_id = ? and type = ?;";
			pstmtLangOldVal = connectionSD.prepareStatement(sqlLangOldVal);

			String sqlLangNew = "insert into translation (value, s_id, language, text_id, type) values(?,?,?,?,?);";
			pstmtLangNew = connectionSD.prepareStatement(sqlLangNew);

			String sqlNewQLabel = "update question set qtext_id = ? where q_id = ?; ";
			pstmtNewQuestionLabel = connectionSD.prepareStatement(sqlNewQLabel);

			String sqlNewOptionLabel = "update option set label_id = ? where l_id = ? and ovalue = ?; ";
			pstmtNewOptionLabel = connectionSD.prepareStatement(sqlNewOptionLabel);

			String sqlNewQHint = "update question set infotext_id = ? where q_id = ?; ";
			pstmtNewQuestionHint = connectionSD.prepareStatement(sqlNewQHint);

			String sqlDeleteLabel = "delete from translation where s_id = ? and text_id = ? and type = ?;";
			pstmtDeleteLabel = connectionSD.prepareStatement(sqlDeleteLabel);

			// Get the languages
			List<Language> lang = GeneralUtilityMethods.getLanguages(connectionSD, sId);
			int listId = -1;

			for(ChangeItem ci : changeItemList) {

				boolean isQuestion = ci.property.type.equals("question");
				boolean isOption = ci.property.type.equals("option");
				String text_id = null;
				if(!isQuestion) {
					// Get the text id for an option
					// Don't rely on the key as the text id may have been changed by a name change
					listId = GeneralUtilityMethods.getListId(connectionSD, sId, ci.property.optionList);
					pstmtGetOptionTextId.setInt(1, listId);
					pstmtGetOptionTextId.setString(2, ci.property.name);

					// (debug) log.info("Getting text id for option: " + pstmtGetOptionTextId.toString());
					ResultSet rs = pstmtGetOptionTextId.executeQuery();
					if(rs.next()) {
						text_id = rs.getString(1);
					}

					if(text_id == null) {
						text_id = "option_" + listId + "_" + ci.property.name + ":label";
					}

				} else {
					pstmtGetQuestionTextId.setInt(1, ci.property.qId);
					ResultSet rs = pstmtGetQuestionTextId.executeQuery();
					if(rs.next()) {
						if(ci.property.propType.equals("text")) {
							text_id = rs.getString(1);
						} else {
							text_id = rs.getString(2);
						}
						if(text_id == null || text_id.trim().length() == 0) {
							text_id = ci.property.key;
						}
					} else {
						text_id = ci.property.key;		// For question we can rely on the key?
					}
				}

				if(ci.property.oldVal != null && ci.property.newVal != null) {
					if(ci.property.propType.equals("text")) {
						updateLabel(connectionSD, ci, ci.property.languageName, pstmtLangOldVal, sId, text_id);
					} else {
						// For media update all the languages
						for(int i = 0; i < lang.size(); i++) {
							updateLabel(connectionSD, ci, lang.get(i).name, pstmtLangOldVal, sId, text_id);
						}
					}

				} else {
					if(ci.property.propType.equals("text")) {
						addLabel(connectionSD, ci, ci.property.languageName, pstmtLangNew, sId, pstmtDeleteLabel, text_id);

						// Add the new text id to the question - Is this needed ?????
						if(isQuestion) {
							pstmtNewQuestionLabel.setString(1, ci.property.key);
							pstmtNewQuestionLabel.setInt(2, ci.property.qId);
							// (debug) log.info("Update question table with text_id: " + pstmtNewQuestionLabel.toString());
							pstmtNewQuestionLabel.executeUpdate();
						} else if(isOption) {
							pstmtNewOptionLabel.setString(1, text_id);
							pstmtNewOptionLabel.setInt(2, listId);
							pstmtNewOptionLabel.setString(3, ci.property.name);
							// (debug) log.info("Update option label with label_id: " + pstmtNewOptionLabel.toString());
							pstmtNewOptionLabel.executeUpdate();
						}

					} else if(ci.property.propType.equals("hint")) {
						addLabel(connectionSD, ci, ci.property.languageName, pstmtLangNew, sId, pstmtDeleteLabel, text_id);

						// Add the new text id to the question
						if(isQuestion) {
							pstmtNewQuestionHint.setString(1, ci.property.key);
							pstmtNewQuestionHint.setInt(2, ci.property.qId);
							// (debug) log.info("Update question table with hint_id: " + pstmtNewQuestionHint.toString());
							pstmtNewQuestionHint.executeUpdate();
						}

					} else {
						// For media update all the languages
						for(int i = 0; i < lang.size(); i++) {
							addLabel(connectionSD, ci, lang.get(i).name, pstmtLangNew, sId, pstmtDeleteLabel, text_id);
						}
					}
				}

				log.info("userevent: " + userId + " : modify survey label : " + ci.property.key + " to: " + ci.property.newVal + " survey: " + sId + " language: " + ci.property.languageName + " labelId: "  + transType);

				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, "update")));
				pstmtChangeLog.setInt(4,userId);
				pstmtChangeLog.setBoolean(5, logIndividualChangeSets);
				pstmtChangeLog.setTimestamp(6, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();

			}
		} catch (Exception e) {

			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {
			try {if (pstmtLangOldVal != null) {pstmtLangOldVal.close();}} catch (SQLException e) {}
			try {if (pstmtLangNew != null) {pstmtLangNew.close();}} catch (SQLException e) {}
			try {if (pstmtNewQuestionLabel != null) {pstmtNewQuestionLabel.close();}} catch (SQLException e) {}
			try {if (pstmtNewOptionLabel != null) {pstmtNewOptionLabel.close();}} catch (SQLException e) {}
			try {if (pstmtNewQuestionHint != null) {pstmtNewQuestionHint.close();}} catch (SQLException e) {}
			try {if (pstmtDeleteLabel != null) {pstmtDeleteLabel.close();}} catch (SQLException e) {}
			try {if (pstmtGetOptionTextId != null) {pstmtGetOptionTextId.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionTextId != null) {pstmtGetQuestionTextId.close();}} catch (SQLException e) {}
		}
	}

	/*
	 * Update a label
	 */
	public void updateLabel(Connection sd, ChangeItem ci, String language, 
			PreparedStatement pstmtLangOldVal, 
			int sId,
			String text_id) throws SQLException, Exception {

		String transType = null;

		pstmtLangOldVal.setString(1, ci.property.newVal);	// rmpath
		pstmtLangOldVal.setInt(2, sId);
		pstmtLangOldVal.setString(3, language);
		pstmtLangOldVal.setString(4, text_id);
		if(ci.property.propType.equals("text") || ci.property.propType.equals("hint")) {
			transType = "none";
		} else {
			transType = ci.property.propType;
		}
		pstmtLangOldVal.setString(5,  transType);

		// (debug) log.info("Update question translation: " + pstmtLangOldVal.toString());

		pstmtLangOldVal.executeUpdate();

	}

	public void addLabel(Connection sd, 
			ChangeItem ci, 
			String language, 
			PreparedStatement pstmtLangNew, 
			int sId, 
			PreparedStatement pstmtDeleteLabel,
			String text_id) throws SQLException, Exception {

		String transType = null;

		if(ci.property.newVal != null) {

			// pstmtLangNew.setString(1, GeneralUtilityMethods.convertAllxlsNames(ci.property.newVal, sId, sd, true));
			pstmtLangNew.setString(1, ci.property.newVal);
			pstmtLangNew.setInt(2, sId);
			pstmtLangNew.setString(3, language);
			pstmtLangNew.setString(4, text_id);
			if(ci.property.propType.equals("text") || ci.property.propType.equals("hint")) {
				transType = "none";
			} else {
				transType = ci.property.propType;
			}
			pstmtLangNew.setString(5,  transType);

			// (debug) log.info("Insert new question label: " + pstmtLangNew.toString());

			pstmtLangNew.executeUpdate();
		} else {
			// Only media labels can have new val set to null and hence be deleted hence delete for all languages
			pstmtDeleteLabel.setInt(1, sId);
			pstmtDeleteLabel.setString(2, ci.property.key);
			pstmtDeleteLabel.setString(3, ci.property.propType);
			// (debug) log.info("Delete media label: " + pstmtDeleteLabel.toString());
			pstmtDeleteLabel.executeUpdate();
			ci.property.key = null;		// Clear the key in the question table
		}  

	}
	
	/*
	 * Apply question property changes
	 * This can be any simple property type such as relevance
	 */
	public void applyQuestionProperty(Connection sd,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String type,
			boolean logIndividualChangeSets) throws Exception {

		boolean setReadonly = false;
		boolean onlyIfNotPublished = false;

		PreparedStatement pstmt = null;
		PreparedStatement pstmtProperty2 = null;
		PreparedStatement pstmtProperty3 = null;
		PreparedStatement pstmtDependent = null;
		PreparedStatement pstmtReadonly = null;
		PreparedStatement pstmtSource = null;
		PreparedStatement pstmtGetQuestionId = null;
		PreparedStatement pstmtGetQuestionDetails = null;
		PreparedStatement pstmtGetListId = null;
		PreparedStatement pstmtGetListname = null;
		PreparedStatement pstmtListname = null;
		PreparedStatement pstmtUpdateRepeat = null;
		PreparedStatement pstmtUpdateEndGroup = null;
		PreparedStatement pstmtUpdateForm = null;
		PreparedStatement pstmtUpdateColumnName = null;
		PreparedStatement pstmtUpdateLabelRef = null;
		PreparedStatement pstmtUpdateHintRef = null;
		PreparedStatement pstmtUpdateTranslations = null;
		PreparedStatement pstmtUpdateQuestion = null;
		PreparedStatement pstmtUpdateNodeset = null;
		PreparedStatement pstmtUpdateQuestionNodeset = null;
		PreparedStatement pstmtAddNodeset = null;
		PreparedStatement pstmtClearNodeset = null;

		PreparedStatement pstmtForm = null;
		String sqlForm = "insert into form(f_id, s_id, name, label, table_name, "
				+ "parentform, parentquestion, repeats, path, form_index) " +
				"values(nextval('f_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?);";

		String sql = "delete from question q where f_id = ? and qname = ? and q.q_id in " +
				" (select q_id from question q, form f where q.f_id = f.f_id and f.s_id = ?);";	// Ensure user is authorised to access this question

		try {

			for(ChangeItem ci : changeItemList) {

				/*
				 * If the question type is a begin repeat and the property is "calculate" then 
				 *  the repeat count attribute of the sub form needs to be updated
				 */
				if(ci.property.qType != null && 
						(ci.property.qType.equals("begin repeat") || ci.property.qType.equals("geopolygon") || ci.property.qType.equals("geolinestring")) && 
						ci.property.prop.equals("calculation")) {

					String sqlUpdateRepeat = "update form set repeats = ? "
							+ "where s_id = ? "
							+ "and parentquestion = ?";
					//+ "and (trim(both from repeats) = ? or repeats is null);";
					pstmtUpdateRepeat = sd.prepareStatement(sqlUpdateRepeat);

					pstmtUpdateRepeat.setString(1, ci.property.newVal);
					pstmtUpdateRepeat.setInt(2, sId);
					pstmtUpdateRepeat.setInt(3, ci.property.qId);

					log.info("Updating repeat count: " + pstmtUpdateRepeat.toString());
					int count = pstmtUpdateRepeat.executeUpdate();
					if(count == 0) {
						String msg = "Warning: property \"" + ci.property.prop 
								+ "\" for question "
								+ ci.property.name
								+ " was not updated to "
								+ ci.property.newVal
								+ ". It may have already been updated by someone else";
						log.info(msg);
						throw new Exception(msg);		// No matching value assume it has already been modified
					}

				} else {

					/*
					 * Update the question
					 */
					String property = translateProperty(ci.property.prop);
					String propertyType = null;
					String originalNewValue = ci.property.newVal;		// Save for logging

					// Add constraint that name and type properties can only be updated if the form has not been published
					if(ci.property.prop.equals("type")) {
						if(ci.property.newVal.equals("note")) {
							setReadonly = true;
							ci.property.newVal = "string";
						} else if(ci.property.newVal.equals("calculate")) {
							ci.property.newVal = "string";
							ci.property.setVisible = true;
							ci.property.visibleValue = false;
						}
						//onlyIfNotPublished = true;	allow type changes
					} else if(ci.property.prop.equals("name") && !ci.property.type.equals("optionlist")) {
						onlyIfNotPublished = true;
					} else if(ci.property.prop.equals("list_name")) {
						// Convert the passed in list name to the list id that needs to be updated
						String sqlGetListId = "select l_id from listname where s_id = ? and name = ?;";
						pstmtGetListId = sd.prepareStatement(sqlGetListId);
						pstmtGetListId.setInt(1, sId);

						// Get the new list id
						pstmtGetListId.setString(2, ci.property.newVal);
						ResultSet rs = pstmtGetListId.executeQuery();
						if(rs.next()) {
							ci.property.newVal = rs.getString(1);
						} else {
							ci.property.newVal = "0";
						}

						// Get the original list id
						pstmtGetListId.setString(2, ci.property.oldVal);
						rs = pstmtGetListId.executeQuery();
						if(rs.next()) {
							ci.property.oldVal = rs.getString(1);
						} else {
							ci.property.oldVal = "0";
						}

					} else if(ci.property.prop.equals("choice_filter")) {
						// Convert the passed in filter to a nodeset
						String listname = GeneralUtilityMethods.getListNameForQuestion(sd, ci.property.qId);
						ci.property.newVal = GeneralUtilityMethods.getNodesetFromChoiceFilter(ci.property.newVal, listname);

					} else if(ci.property.type.equals("optionlist")) {
						// Get the list id for this option list
						String sqlGetListId = "select l_id from listname where s_id = ? and name = ?;";
						pstmtGetListId = sd.prepareStatement(sqlGetListId);
						pstmtGetListId.setInt(1, sId);

						pstmtGetListId.setString(2, ci.property.oldVal);
						ResultSet rs = pstmtGetListId.executeQuery();
						if(rs.next()) {
							ci.property.l_id = rs.getInt(1);
						} else {
							ci.property.newVal = "0";
						}

					}

					if(ci.property.prop.equals("relevant") || ci.property.prop.equals("constraint") 
							|| ci.property.prop.equals("calculation") || ci.property.prop.equals("appearance")
							|| ci.property.prop.equals("parameters")) {
						if(ci.property.oldVal != null && ci.property.oldVal.contains("null")) {
							ci.property.oldVal = "_force_update";
						}
					}
					
					if(property.equals("choice_filter")) {	// If the property is choice_filter this is saved into the nodeset column in the question table
						property = "nodeset";
					} 
					
					/*
					 * If the property is app_choices then the values are not stored in q property column in the database
					 *  instead they are stored in the choices list
					 * otherwise check to ensure that the properties column exists for this property
					 */
					if(property.equals("app_choices")) {
						updateSearchChoices(sd, pstmt, ci.property.newVal, sId, ci.property.qId);
						
					} else if((propertyType = GeneralUtilityMethods.columnType(sd, "question", property)) != null) {

						// One for the case where the property has not been set before
						String sqlProperty2 = "update question set " + property + " = ? " +
								"where q_id = ? ";
						if(onlyIfNotPublished) {
							sqlProperty2 += " and published = 'false';";
						} else {
							sqlProperty2 += ";";
						}
						pstmtProperty2 = sd.prepareStatement(sqlProperty2);

						// Special case for list name (no integrity checking)
						String sqlProperty3 = "update question set l_id = ? " +
								"where q_id = ?";
						pstmtProperty3 = sd.prepareStatement(sqlProperty3);

						// Update listname - Get existing listname
						String sqlGetListname = "select name from listname where l_id = ? and s_id = ?";
						pstmtGetListname = sd.prepareStatement(sqlGetListname);

						// Update listname
						String sqlListname = "update listname set name = ? where l_id = ? and s_id = ?";
						pstmtListname = sd.prepareStatement(sqlListname);

						// Update nodeset
						String sqlUpdateNodeset = "update question set nodeset = replace(nodeset, '(''' || ? || ''')', '(''' || ? || ''')') "
								+ "where l_id = ? "
								+ "and f_id in (select f_id from form where s_id = ?)";
						pstmtUpdateNodeset = sd.prepareStatement(sqlUpdateNodeset);	

						// Update nodeset for a single question
						String sqlUpdateQuestionNodeset = "update question set nodeset = replace(nodeset, '(''' || ? || ''')', '(''' || ? || ''')') "
								+ "where q_id = ? ";
						pstmtUpdateQuestionNodeset = sd.prepareStatement(sqlUpdateQuestionNodeset);		

						// Add nodeset values if question type is being converted to a select question
						// Note we can use the question name as the itemset name and assume no filters as this change of type has to happen first
						String sqlAddNodeset = "update question "
								+ "set nodeset = 'instance(''' || qname || ''')/root/item',"
								+ "nodeset_value = 'name', "
								+ "nodeset_label = 'jr:itext(itextId)',"
								+ "l_id = ? "
								+ "where q_id = ? "
								+ "and q_id in (select q_id from question q, form f where f.f_id = q.f_id and f.s_id = ?)";
						pstmtAddNodeset = sd.prepareStatement(sqlAddNodeset);
						
						String sqlClearNodeset = "update question "
								+ "set nodeset = null,"
								+ "nodeset_value = null, "
								+ "nodeset_label = null,"
								+ "l_id = 0 "
								+ "where q_id = ?"
								+ "and q_id in (select q_id from question q, form f where f.f_id = q.f_id and f.s_id = ?)";
						pstmtClearNodeset = sd.prepareStatement(sqlClearNodeset);

						// Update for dependent properties
						String sqlDependent = "update question set visible = ?, source = ? " +
								"where q_id = ?";
						pstmtDependent = sd.prepareStatement(sqlDependent);

						// Update for readonly status if this is a type change to note
						String sqlReadonly = "update question set readonly = 'true' " +
								"where q_id = ?;";
						pstmtReadonly = sd.prepareStatement(sqlReadonly);

						int count = 0;

						/*
						 * Special case for change of list name in a question - don't try to check the integrity of the update
						 * In fact any change to the list name is difficult to manage for integrity as it can be updated
						 *  both in a question property change and as a list name change
						 *  hence the integrity checking is relaxed at the moment 
						 */

						if(ci.property.prop.equals("list_name")) {

							// Get the existing list name
							String originalListname = null;
							pstmtGetListname.setInt(1, Integer.parseInt(ci.property.oldVal));
							pstmtGetListname.setInt(2, sId);
							ResultSet rs = pstmtGetListname.executeQuery();
							if(rs.next()) {
								originalListname = rs.getString(1);
							}

							// Get the new list name
							String newListname = null;
							pstmtGetListname.setInt(1, Integer.parseInt(ci.property.newVal));
							pstmtGetListname.setInt(2, sId);
							rs = pstmtGetListname.executeQuery();
							if(rs.next()) {
								newListname = rs.getString(1);
							}

							// Update the list id property of the question
							pstmtProperty3.setInt(1, Integer.parseInt(ci.property.newVal));
							pstmtProperty3.setInt(2, ci.property.qId);				
							log.info("Update list name property: " + pstmtProperty3.toString());
							count = pstmtProperty3.executeUpdate();

							// Update the nodeset for the question
							if(originalListname != null && originalListname.trim().length() > 0) {
								pstmtUpdateQuestionNodeset.setString(1, originalListname);
								pstmtUpdateQuestionNodeset.setString(2, newListname);
								pstmtUpdateQuestionNodeset.setInt(3, ci.property.qId);
								log.info("Update nodeset for question: " + pstmtUpdateQuestionNodeset.toString());
								pstmtUpdateQuestionNodeset.executeUpdate();
							}

						} else if (ci.property.type.equals("optionlist")) {
							if( ci.property.l_id > 0) {

								String originalListname = null;
								// Get the existing list name
								pstmtGetListname.setInt(1, ci.property.l_id);
								pstmtGetListname.setInt(2, sId);
								ResultSet rs = pstmtGetListname.executeQuery();
								if(rs.next()) {
									originalListname = rs.getString(1);
								}

								// Write the new list name
								String cleanName = GeneralUtilityMethods.cleanName(ci.property.newVal, true, false, false);
								pstmtListname.setString(1, cleanName);
								pstmtListname.setInt(2, ci.property.l_id);
								pstmtListname.setInt(3, sId);

								log.info("Update name of list : " + pstmtListname.toString());
								count = pstmtListname.executeUpdate();

								// Update the nodeset for any questions that references this list
								if(originalListname != null && originalListname.trim().length() > 0) {
									pstmtUpdateNodeset.setString(1, originalListname);
									pstmtUpdateNodeset.setString(2, cleanName);
									pstmtUpdateNodeset.setInt(3, ci.property.l_id);
									pstmtUpdateNodeset.setInt(4, sId);
									log.info("Update nodeset : " + pstmtUpdateNodeset.toString());
									pstmtUpdateNodeset.executeUpdate();
								}

								/*
								 * Update any questions that have (remembered) this list name but the list id is null
								 */
								String sqlUpdateQuestion = "update question set l_id = ? "
										+ "where f_id in (select f_id from form where s_id = ?) "
										+ "and l_id is null "
										+ "and list_name = ?";

								pstmtUpdateQuestion = sd.prepareStatement(sqlUpdateQuestion);
								pstmtUpdateQuestion.setInt(1, ci.property.l_id);
								pstmtUpdateQuestion.setInt(2, sId);
								pstmtUpdateQuestion.setString(3, ci.property.newVal);
								log.info("SQL: Update any matching questions: " + pstmtUpdateQuestion.toString());
								pstmtUpdateQuestion.executeUpdate();

							} else {
								count = 1;		// Report as success
								ci.property.newVal = originalNewValue;	// Restore the original new value for logging
							}

						} else {

							pstmtProperty2.setInt(2, ci.property.qId);

							if(propertyType.equals("boolean")) {
								pstmtProperty2.setBoolean(1, Boolean.parseBoolean(ci.property.newVal));
							} else if(propertyType.equals("integer")) {
								if(ci.property.newVal == null) {
									ci.property.newVal = "0";
								}
								pstmtProperty2.setInt(1, Integer.parseInt(ci.property.newVal));
							} else {
								pstmtProperty2.setString(1, ci.property.newVal);
							}

							log.info("Update question property: " + pstmtProperty2.toString());
							count = pstmtProperty2.executeUpdate();

							// Type dependent changes
							if(count > 0 && property.equals("qtype")) {
								// If this question is being converted to q select then add the list id and nodeset
								if((ci.property.newVal.startsWith("select") || ci.property.newVal.equals("rank")) 
										&& (ci.property.oldVal == null || !(ci.property.oldVal.startsWith("select") || ci.property.oldVal.equals("rank")))) {
									String listName = GeneralUtilityMethods.getNameForQuestion(sd, ci.property.qId);
									int l_id = GeneralUtilityMethods.getListId(sd, sId, listName);
									pstmtAddNodeset.setInt(1, l_id);
									pstmtAddNodeset.setInt(2, ci.property.qId);
									pstmtAddNodeset.setInt(3, sId);
									log.info("Add nodeset: " + pstmtAddNodeset.toString());
									pstmtAddNodeset.executeUpdate();
								} else if(!ci.property.newVal.startsWith("select") && !ci.property.newVal.equals("rank")) {
									pstmtClearNodeset.setInt(1, ci.property.qId);
									pstmtClearNodeset.setInt(2, sId);
									log.info("Clear nodeset: " + pstmtClearNodeset.toString());
									pstmtClearNodeset.executeUpdate();
								} 
								
								if(ci.property.newVal.equals("begin group")) {
									ci.property.setVisible = true;
									ci.property.visibleValue = true;
									ci.property.sourceValue = null;
								}
							}
						}

						if(ci.property.setVisible) {
							pstmtDependent.setBoolean(1, ci.property.visibleValue);
							pstmtDependent.setString(2, ci.property.sourceValue);
							pstmtDependent.setInt(3, ci.property.qId);
							log.info("Update dependent properties: " + pstmtDependent.toString());
							pstmtDependent.executeUpdate();
						}

						if(setReadonly) {
							pstmtReadonly.setInt(1, ci.property.qId);
							log.info("Update readonly status for note type: " + pstmtReadonly.toString());
							pstmtReadonly.executeUpdate();
						}

						if(count == 0) {
							String msg = "Warning: property \"" + ci.property.prop 
									+ "\" for question "
									+ ci.property.name
									+ " was not updated to "
									+ originalNewValue
									+ ". It may have already been updated by someone else "
									+ "or the question may have been published while you were editing it.";
							log.info(msg);
							throw new Exception(msg);		// No matching value assume it has already been modified
						}

						// If question is being converted to a begin repeat then create a new form
						boolean isRepeatType = false;
						if(ci.property.newVal.equals("begin repeat") || ci.property.newVal.equals("geopolygon") || ci.property.newVal.equals("geolinestring")) {
							isRepeatType = true;
						}
						if(isRepeatType) {

							String columnName = GeneralUtilityMethods.cleanName(ci.property.name, true, true, true);
							// Create the sub form
							String tableName = "s" + sId + "_" + columnName;


							pstmtForm = sd.prepareStatement(sqlForm);
							pstmtForm.setInt(1, sId);
							pstmtForm.setString(2, ci.property.name);
							pstmtForm.setString(3, ci.property.fId + "_question_" + columnName + ":label");
							pstmtForm.setString(4, tableName);
							pstmtForm.setInt(5, ci.property.fId);
							pstmtForm.setInt(6, ci.property.qId);		// parent question id
							pstmtForm.setString(7, ci.property.calculation);
							pstmtForm.setString(8, "");	// path is no longer used
							pstmtForm.setInt(9, ci.property.childFormIndex);

							log.info("SQL: Insert new form: " + pstmtForm.toString());
							pstmtForm.executeUpdate();

						}

						// if the old question type was a begin group then delete the end group
						if(ci.property.oldVal != null && ci.property.oldVal.equals("begin group")) {
							String endGroupName = ci.property.name + "_groupEnd";

							// Delete the end group
							try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
							pstmt = sd.prepareStatement(sql);
							pstmt.setInt(1, ci.property.fId);
							pstmt.setString(2, endGroupName);
							pstmt.setInt(3, sId );

							log.info("Delete End group of question: " + pstmt.toString());
							pstmt.executeUpdate();
						}

						// Source is set to "user" for questions that can be completed by a user
						boolean setSource = false;
						String newSource = null;

						if(isRepeatType) {
							setSource = true;
							newSource = null;
						}

						if(setSource) {
							String sqlSource = "update question set source = ? " +
									"where q_id = ?";
							pstmtSource = sd.prepareStatement(sqlSource);
							pstmtSource.setString(1, newSource);
							pstmtSource.setInt(2, ci.property.qId);
							log.info("Update source: " + pstmtSource.toString());
							pstmtSource.executeUpdate();
						}

						// Update the survey manifest if this question references CSV files
						if(ci.property.prop.equals("calculation")) {
							updateSurveyManifest(sd, sId, null, ci.property.newVal);
							removeUnusedSurveyManifests(sd, sId);
							// Update any calculations that reference the survey itself
							//GeneralUtilityMethods.updateSelfCalcsQuestion(sd, ci.property.qId);
						} else if(ci.property.prop.equals("appearance")) {
							updateSurveyManifest(sd, sId, ci.property.newVal, null);
							removeUnusedSurveyManifests(sd, sId);
						}

						/*
						 * If this is a name change then
						 *   If it is a begin group then update the end group name
						 *   update paths
						 */
						if(ci.property.prop.equals("name") && !ci.property.type.equals("optionlist")) {
							String qType = null;
							String qTextId = null;
							String infoTextId = null;
							int fId = 0;
							//String currentPath = null;
							//String newPath = null;
							String newTextId = null;
							String newHintId = null;
							String sqlGetQuestionDetails = "select f_id, path, qtype, qtext_id, infotext_id from question where q_id = ?";
							String sqlUpdateColumnName = "update question set column_name = ? where q_id = ?;";
							String sqlUpdateLabelRef = "update question set qtext_id = ? where q_id = ?;";
							String sqlUpdateHintRef = "update question set infotext_id = ? where q_id = ?;";
							String sqlUpdateTranslations = "update translation set text_id = ? where text_id = ? and s_id = ?";


							// 1. Get question details
							pstmtGetQuestionDetails = sd.prepareStatement(sqlGetQuestionDetails);
							pstmtGetQuestionDetails.setInt(1, ci.property.qId);
							ResultSet rsQuestionDetails = pstmtGetQuestionDetails.executeQuery();

							if(rsQuestionDetails.next()) {
								fId = rsQuestionDetails.getInt(1);

								qType = rsQuestionDetails.getString(3);
								qTextId = rsQuestionDetails.getString(4);
								infoTextId = rsQuestionDetails.getString(5);
								newTextId = fId + "_question_" + ci.property.newVal + ":label";
								newHintId = fId + "_question_" + ci.property.newVal + ":hint";

							}

							// 2. Update column name
							pstmtUpdateColumnName = sd.prepareStatement(sqlUpdateColumnName);
							pstmtUpdateColumnName.setString(1, 
									GeneralUtilityMethods.cleanName(ci.property.newVal, true, true, true));
							pstmtUpdateColumnName.setInt(2, ci.property.qId);
							pstmtUpdateColumnName.executeUpdate();

							// 3. Update reference to label
							pstmtUpdateTranslations = sd.prepareStatement(sqlUpdateTranslations);
							if(qTextId != null) {
								pstmtUpdateLabelRef = sd.prepareStatement(sqlUpdateLabelRef);
								pstmtUpdateLabelRef.setString(1, newTextId);
								pstmtUpdateLabelRef.setInt(2, ci.property.qId);
								pstmtUpdateLabelRef.executeUpdate();		

								pstmtUpdateTranslations.setString(1, newTextId);
								pstmtUpdateTranslations.setString(2, qTextId);
								pstmtUpdateTranslations.setInt(3, sId);
								pstmtUpdateTranslations.executeUpdate();
							}

							// 4. Update reference to hint
							if(infoTextId != null) {
								pstmtUpdateHintRef = sd.prepareStatement(sqlUpdateHintRef);
								pstmtUpdateHintRef.setString(1, newHintId);
								pstmtUpdateHintRef.setInt(2, ci.property.qId);
								pstmtUpdateHintRef.executeUpdate();

								pstmtUpdateTranslations.setString(1, newHintId);
								pstmtUpdateTranslations.setString(2, infoTextId);
								pstmtUpdateTranslations.setInt(3, sId);
								pstmtUpdateTranslations.executeUpdate();
							}

							// 5. If this is a begin group then update the end group
							if(qType != null && qType.equals("begin group")) {
								String sqlUpdateEndGroup = "update question set qname = ? "
										+ "where f_id = ? "
										+ "and qtype = 'end group' " 
										+ "and qname = ?";

								pstmtUpdateEndGroup = sd.prepareStatement(sqlUpdateEndGroup);
								pstmtUpdateEndGroup.setString(1,  ci.property.newVal + "_groupEnd");
								pstmtUpdateEndGroup.setInt(2,  fId);
								pstmtUpdateEndGroup.setString(3,  ci.property.oldVal + "_groupEnd");

								log.info("Updating group end name: " + pstmtUpdateEndGroup.toString());
								pstmtUpdateEndGroup.executeUpdate();
							}

							// 6. If this is a begin repeat, geopolygon or geolinestring (that is a form) then update the form specification
							if(qType != null && (qType.equals("begin repeat") || qType.equals("geopolygon") || qType.equals("geolinestring"))){

								String sqlUpdateForm = "update form set name = ?, "
										+ "table_name = ? "
										+ "where s_id = ? "
										+ "and name = ?;";
								try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
								pstmtUpdateForm = sd.prepareStatement(sqlUpdateForm);
								pstmtUpdateForm.setString(1,  ci.property.newVal);
								pstmtUpdateForm.setString(2,  "s" + sId + "_" + ci.property.newVal);
								pstmtUpdateForm.setInt(3,  sId);
								pstmtUpdateForm.setString(4,  ci.property.oldVal);

								log.info("Updating form name: " + pstmtUpdateForm.toString());
								pstmtUpdateForm.executeUpdate();
							}

						}
						
						/*
						 * If this is a parameter change on a begin repeat then update the form
						 */
						if(ci.property.qType != null && 
								ci.property.qType.equals("begin repeat") &&
								ci.property.prop.equals("parameters")) {
							
							ArrayList<KeyValueSimp> props = GeneralUtilityMethods.convertParametersToArray(ci.property.newVal);
							
							boolean ref= false;
							boolean merge = false;
							boolean replace = false;
							String refForm = null;
							for(KeyValueSimp k : props) {
								if(k.k.equals("ref")) {
									ref = true;
									refForm = k.v;
								} else if(k.k.equals("merge") && k.v.equals("yes")) {
									merge = true;		// Deprecate legacy setting
								} else if(k.k.equals("key_policy")) {
									if(k.v.equals("merge")) {
										merge = true;
									} else if(k.v.equals("replace")) {
										replace = true;
									} else if(k.v.equals("append")) {
										// default
									} else {
										log.info("Error: unknown key policy: " + k.v);
									}
								}
							}

							String sqlUpdateForm = "update form set reference = ?, merge = ?, replace = ? "
									+ "where s_id = ? "
									+ "and parentquestion = ?";
							try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
							pstmtUpdateForm = sd.prepareStatement(sqlUpdateForm);
							pstmtUpdateForm.setBoolean(1,  ref);
							pstmtUpdateForm.setBoolean(2,  merge);
							pstmtUpdateForm.setBoolean(3,  replace);
							pstmtUpdateForm.setInt(4,  sId);
							pstmtUpdateForm.setInt(5, ci.property.qId);
	
							log.info("Updating form properties: " + pstmtUpdateForm.toString());
							pstmtUpdateForm.executeUpdate();	
							
							// If the reference was set then set the table name to the name of the reference form
							if(ref) {
								String columnName = GeneralUtilityMethods.cleanName(refForm, true, true, true);
								String tableName = "s" + sId + "_" + columnName;
								
								String sqlUpdateForm2 = "update form set table_name = ? "
										+ "where s_id = ? "
										+ "and parentquestion = ?";
								try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
								pstmtUpdateForm = sd.prepareStatement(sqlUpdateForm2);
								pstmtUpdateForm.setString(1,  tableName);
								pstmtUpdateForm.setInt(2,  sId);
								pstmtUpdateForm.setInt(3, ci.property.qId);
								
								log.info("Updating form properties2: " + pstmtUpdateForm.toString());
								pstmtUpdateForm.executeUpdate();	
							} else {
								// Set the table name to the default non reference name
								
								String sqlUpdateForm2 = "update form set table_name = 's' || s_id || '_' || name "
										+ "where s_id = ? "
										+ "and parentquestion = ?";
								try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
								pstmtUpdateForm = sd.prepareStatement(sqlUpdateForm2);
								pstmtUpdateForm.setInt(1,  sId);
								pstmtUpdateForm.setInt(2, ci.property.qId);
								
								log.info("Updating form properties2: " + pstmtUpdateForm.toString());
								pstmtUpdateForm.executeUpdate();	
							}
							
							
						}
						log.info("userevent: " + userId + " : modify survey property : " + property + " to: " + ci.property.newVal + " survey: " + sId);

						// Write the change log
						Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
						pstmtChangeLog.setInt(1, sId);
						pstmtChangeLog.setInt(2, version);
						pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, "update")));
						pstmtChangeLog.setInt(4, userId);	
						pstmtChangeLog.setBoolean(5,logIndividualChangeSets);	
						pstmtChangeLog.setTimestamp(6, GeneralUtilityMethods.getTimeStamp());
						pstmtChangeLog.execute();

					} else {
						throw new Exception("Unknown property: " + property);
					}
				}
			}

		} catch (Exception e) {

			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Warning")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {
			try {if (pstmtProperty2 != null) {pstmtProperty2.close();}} catch (SQLException e) {}
			try {if (pstmtProperty3 != null) {pstmtProperty3.close();}} catch (SQLException e) {}
			try {if (pstmtDependent != null) {pstmtDependent.close();}} catch (SQLException e) {}
			try {if (pstmtReadonly != null) {pstmtReadonly.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionId != null) {pstmtGetQuestionId.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionDetails != null) {pstmtGetQuestionDetails.close();}} catch (SQLException e) {}
			try {if (pstmtGetListId != null) {pstmtGetListId.close();}} catch (SQLException e) {}
			try {if (pstmtListname != null) {pstmtListname.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateRepeat != null) {pstmtUpdateRepeat.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionDetails != null) {pstmtGetQuestionDetails.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateColumnName != null) {pstmtUpdateColumnName.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateLabelRef != null) {pstmtUpdateLabelRef.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateHintRef != null) {pstmtUpdateHintRef.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateTranslations != null) {pstmtUpdateTranslations.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateQuestion != null) {pstmtUpdateQuestion.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateEndGroup != null) {pstmtUpdateEndGroup.close();}} catch (SQLException e) {}
			try {if (pstmtGetListname != null) {pstmtGetListname.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateNodeset != null) {pstmtUpdateNodeset.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateQuestionNodeset != null) {pstmtUpdateQuestionNodeset.close();}} catch (SQLException e) {}
			try {if (pstmtAddNodeset != null) {pstmtAddNodeset.close();}} catch (SQLException e) {}
			try {if (pstmtClearNodeset != null) {pstmtClearNodeset.close();}} catch (SQLException e) {}
			try {if (pstmtForm != null) {pstmtForm.close();}} catch (SQLException e) {}
			try {if (pstmtSource != null) {pstmtSource.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}

		}

	}



	/*
	 * The names of question properties in the table don't exactly match the names in the survey model
	 * translate them here
	 */
	private String translateProperty(String in) {
		String out = in;

		if(in.equals("name")) {
			out = "qname";
		} else if(in.equals("type")) {
			out = "qtype";
		} else if(in.equals("text_id")) {
			out = "qtext_id";
		} else if(in.equals("hint_id")) {
			out = "infotext_id";
		} else if(in.equals("required")) {
			out = "mandatory";
		} else if(in.equals("calculation")) {
			out = "calculate";
		} else if(in.equals("constraint")) {
			out = "qconstraint";
		} else if(in.equals("list_name")) {
			out = "l_id";
		}
		return out;
	}

	/*
	 * Apply add / delete questions
	 */
	public void applyQuestion(Connection connectionSD,
			Connection cResults,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String type,
			String action,
			boolean logIndividualChangeSets) throws Exception {

		QuestionManager qm = new QuestionManager(localisation);
		ArrayList<Question> questions = new ArrayList<Question> ();

		try {

			for(ChangeItem ci : changeItemList) {

				questions.add(ci.question);

				log.info("userevent: " + userId + " : " + action + " question : " + ci.question.name + " survey: " + sId);

				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, action)));
				pstmtChangeLog.setInt(4, userId);
				pstmtChangeLog.setBoolean(5, logIndividualChangeSets);
				pstmtChangeLog.setTimestamp(6, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();
			} 

			if(action.equals("add")) {
				qm.save(connectionSD, cResults, sId, questions);
			} else if(action.equals("delete")) {
				qm.delete(connectionSD, cResults, sId, questions, false, true);
			} else if(action.equals("move")) {
				qm.moveQuestions(connectionSD, sId, questions);
			} else {
				log.info("Unkown action: " + action);
			}

		} catch (Exception e) {

			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {

		}
	}

	/*
	 * Apply add / delete option lists
	 */
	public void applyOptionList(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String type,
			String action,
			boolean logIndividualChangeSets) throws Exception {

		OptionListManager olm = new OptionListManager();

		try {

			for(ChangeItem ci : changeItemList) {

				log.info("userevent: " + userId + " : " + action + " optionlist : " + ci.name + " survey: " + sId);

				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, action)));
				pstmtChangeLog.setInt(4, userId);
				pstmtChangeLog.setBoolean(5, logIndividualChangeSets);
				pstmtChangeLog.setTimestamp(6, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();


				if(action.equals("add")) {
					olm.add(connectionSD, sId, ci.name);
				} else if(action.equals("delete")) {
					olm.delete(connectionSD, sId, ci.name);
				} else {
					log.info("Unkown action: " + action);
				}
			}

		} catch (Exception e) {

			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {

		}
	}

	/*
	 * Apply add / delete choices from the editor
	 * Update properties
	 */
	public void applyOptionFromEditor(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String changeType,
			String action,
			boolean logIndividualChangeSets) throws Exception {

		QuestionManager qm = new QuestionManager(localisation);
		ArrayList<Option> options = new ArrayList<Option> ();
		ArrayList<PropertyChange> properties = new ArrayList<PropertyChange> ();

		try {

			for(ChangeItem ci : changeItemList) {

				if(changeType.equals("property")) {
					properties.add(ci.property);
					log.info("userevent: " + userId + " modify option " + ci.property.newVal + " survey: " + sId);
				} else {
					options.add(ci.option);		
					log.info("userevent: " + userId + (action.equals("add") ? " : add option : " : " : delete option : ") + ci.option.value + " survey: " + sId);
				}

				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, action)));
				pstmtChangeLog.setInt(4, userId);
				pstmtChangeLog.setBoolean(5, logIndividualChangeSets);				
				pstmtChangeLog.setTimestamp(6, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();
			} 

			if(action.equals("add")) {
				qm.saveOptions(connectionSD, sId, options, true, -1);
			} else if(action.equals("delete")) {
				qm.deleteOptions(connectionSD, sId, options, true, -1);
			} else if(action.equals("update")) {
				qm.updateOptions(connectionSD, sId, properties);
			} else if(action.equals("move")) {
				qm.moveOptions(connectionSD, sId, options);
			}

		} catch (Exception e) {

			String msg = e.getMessage();
			if(msg == null || !msg.startsWith("Already modified")) {
				log.log(Level.SEVERE,"Error", e);
			}
			throw e;
		} finally {

		}
	}

	/*
	 * Get the results
	 * @param form
	 * @param id
	 * @param parentId
	 */
	ArrayList<ArrayList<Result>> getResults(
			Form form,
			int fIdx,
			int parentId, 
			Connection sd,
			Connection cResults,
			
			// The following parameters are used when getting date from referenced surveys
			String remoteUser,							// The user making the request
			String basePath,
			boolean getPropertyTypeQuestions,	
			boolean getHrk,		
			String getExternalOptions,
			boolean superUser,

			String instanceId,
			int parentKey,
			String keyQuestionName,			// Used with child forms
			String keyQuestionValue,			// Used with child forms
			Survey s,
			boolean generateDummyValues,
			String geomFormat,
			int oId,
			boolean isTopLevel,				// Set true fist time this recursive function is called
			boolean referencedSurveys,		// Set to get reference surveys
			boolean onlyGetLaunched			// If referenceSurveys is also set only data creating by launching a form will be retrieved
			) throws Exception{

		ArrayList<ArrayList<Result>> output = new ArrayList<ArrayList<Result>> ();

		/*
		 * Retrieve the results record from the database (excluding select questions)
		 */
		StringBuffer sql = new StringBuffer("");
		if(isTopLevel) {
			sql.append("select prikey, _user ");		// Get user if this is a top level form
		} else {
			sql.append("select prikey ");
		}
		
		ArrayList<Question> questions = form.questions;
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSelect = null;
		ResultSet resultSet = null;

		/*
		 * Hack: Remove questions that have not been published.
		 * This code should be modified to reuse the function to get columns used by results export
		 * xxxx
		 */
		if(!generateDummyValues) {
			for(int i = questions.size() - 1; i >= 0; i--) {
				Question q = questions.get(i);
				if(!q.published || q.columnName == null) {
					if(q.type != null && q.type.equals("begin repeat")) {
						if(GeneralUtilityMethods.subFormTableExists(sd, cResults, q.id)) {
							continue;		// Keep begin repeats even if not published since the table exists
											// This would not be needed if there were not issues with the publishing record
						}
					}
					questions.remove(i);
				}
			}
		}

		try {

			/*
			 * Get the result set of data if an instanceID was passed or if 
			 * this request is for a child form and real data is required
			 */
			ArrayList<MetaItem> preloads = null;
			if(instanceId != null || parentKey > 0 || keyQuestionName != null) {
				
				for(Question q : questions) {
					String col = null;

					if(s.getSubForm(form, q) == null || q.name.startsWith("geopolygon_") || q.name.startsWith("geolinestring_")) {
						// This question is not a place holder for a subform
						if(q.source != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
							String qType = q.type;
							if(qType.equals("geopoint") || qType.equals("geoshape") || qType.equals("geotrace") || q.name.startsWith("geopolygon_") || q.name.startsWith("geolinestring_")) {

								col = "ST_AsGeoJSON(" + q.columnName + ", 5)";

							} else if(qType.equals("select") && !q.compressed){
								continue;	// Select data columns are retrieved separately as there are multiple columns per question
							} else {
								col = q.columnName;
							}

							sql.append(",").append(col);
						}
					}

				}
				
				/*
				 * Add questions from meta
				 */
				if(isTopLevel) {
					
					preloads = GeneralUtilityMethods.getPreloads(sd, s.id);
					for(MetaItem mi : preloads) {
						if(mi.isPreload) {			
							if(GeneralUtilityMethods.hasColumn(cResults, form.tableName, mi.columnName)) {
								mi.published = true;
								sql.append(",").append(mi.columnName);				
							} 
						}
					}
					// Add instancename which is not in meta
					sql.append(",instancename");
				}
				
				sql.append(" from ").append(form.tableName);
				if(instanceId != null) {
					sql.append(" where instanceId = ?");
				} else if(parentKey > 0) {
					sql.append(" where parkey = ?");
				} else if(keyQuestionName != null) {
					keyQuestionName = keyQuestionName.replace("'", "''");	// Escape apostrophes
					sql.append(" where ").append(keyQuestionName).append(" = ?");
				}
				// Do not include records marked as bad - in particular some child records may have been marked as bad and these should be excluded from pdf reports
				sql.append(" and not _bad");
				sql.append(" order by prikey asc");
				
				pstmt = cResults.prepareStatement(sql.toString());	 
				if(instanceId != null) {
					pstmt.setString(1, instanceId);
				} else if(parentKey > 0) {
					pstmt.setInt(1, parentKey);
				} else if(keyQuestionName != null) {
					pstmt.setString(1, keyQuestionValue);
				}

				log.info("Get results: " + pstmt.toString());
				if(GeneralUtilityMethods.tableExists(cResults, form.tableName)) {
					resultSet = pstmt.executeQuery();
				} else {
					log.info("Table does not exist");
				}
			}

			int index;
			if (resultSet != null) {
				// For each record returned from the database add the data values to the instance
				while(resultSet.next()) {
					index = 1;
					
					ArrayList<Result> record = new ArrayList<Result> ();

					String priKey = resultSet.getString(index);
					int newParentKey = resultSet.getInt(index++);   		
					record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null, null));     // SERVERMETA

					if(isTopLevel) {
						String user = resultSet.getString(index++);
						record.add(new Result("user", "user", user, false, fIdx, -1, 0, null, null));		// SERVERMETA
						gPrimaryKey = priKey;		// The primary key of the top level form
						for(MetaItem mi : preloads) {
							if(mi.isPreload && mi.published) {			
								record.add(new Result(mi.name, mi.dataType, resultSet.getString(mi.columnName), false, fIdx, mi.id, 0, null, null));
							}
						}
						record.add(new Result("instancename", "instancename", resultSet.getString("instancename"), false, fIdx, -1, 0, null, null));
						
					}

					addDataForQuestions(
							sd,
							cResults,
							resultSet, 
							remoteUser,							// The user making the request
							basePath,
							getPropertyTypeQuestions,	
							getHrk,		
							getExternalOptions,
							superUser,
							record, 
							priKey,
							newParentKey, 
							s, 
							form, 
							questions, 
							fIdx, 
							pstmtSelect,
							isTopLevel,
							generateDummyValues,
							geomFormat,
							oId,
							referencedSurveys,
							onlyGetLaunched,
							index);

					output.add(record);
				}
			} else if(generateDummyValues){
				// Add dummy values for a blank form
				index = 1;
				
				ArrayList<Result> record = new ArrayList<Result> ();

				String priKey = "";
				int newParentKey = 0;
				record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null, null)); 

				if(isTopLevel) {
					record.add(new Result("user", "user", null, false, fIdx, -1, 0, null, null)); 
				}

				addDataForQuestions(
						sd,
						cResults,
						resultSet, 					
						remoteUser,							// The user making the request
						basePath,
						getPropertyTypeQuestions,	
						getHrk,		
						getExternalOptions,
						superUser,
						record, 
						priKey,
						newParentKey, 
						s, 
						form, 
						questions, 
						fIdx, 
						pstmtSelect,
						isTopLevel,
						generateDummyValues,
						geomFormat,
						oId,
						referencedSurveys,
						onlyGetLaunched,
						index);

				output.add(record);
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
		}

		return output;
	}

	/*
	 * Add the record containing the results for this form
	 * If the resultSet is null then populate with blank data
	 */
	private void addDataForQuestions(
			Connection sd,
			Connection cResults,
			ResultSet resultSet, 
												// The following parameters are used when getting date from referenced surveys
			String remoteUser,							// The user making the request
			String basePath,
			boolean getPropertyTypeQuestions,	
			boolean getHrk,		
			String getExternalOptions,
			boolean superUser,
			
			ArrayList<Result> record, 
			String priKey,
			int newParentKey,
			Survey s,
			Form form,
			ArrayList<Question> questions,
			int fIdx,
			PreparedStatement pstmtSelect,
			boolean isTopLevel,
			boolean generateDummyValues,
			String geomFormat,
			int oId,
			boolean referencedSurveys,
			boolean onlyGetLaunched,
			int index) throws Exception {
		
		/*
		 * Add data for the remaining questions (prikey and user have already been extracted)
		 */

		int qIdx = -1;					// Index into question array for this form
		for(Question q : questions) {
			qIdx++;

			String qName = q.name;
			String qType = q.type; 
			String qSource = q.source;
			String listName = q.list_name;
			String appearance = q.appearance;
			boolean compressed = q.compressed;
			
			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
				Form subForm = s.getSubForm(form, q);

				if(subForm != null) {	
					Result nr = new Result(qName, "form", null, false, fIdx, qIdx, 0, null, appearance);		// Result entry for this question

					nr.subForm = getResults(subForm, 
							s.getFormIdx(subForm.id),
							subForm.id, 
							sd,
							cResults,						
							remoteUser,							
							basePath,
							getPropertyTypeQuestions,	
							getHrk,		
							getExternalOptions,
							superUser,
							null,
							newParentKey,
							null,
							null,
							s,
							generateDummyValues,
							geomFormat,
							oId,
							false,
							referencedSurveys,
							onlyGetLaunched);

					record.add(nr);
				}

				if(qType.equals("begin repeat")) {
					index--;		// Decrement the index as the begin repeat was not in the SQL query
				}

			} else if(referencedSurveys && qType.equals(SmapQuestionTypes.CHILD_FORM)) {		// Child survey
				
				String ref = getReferenceSurveyIdentifier(q);
				
				// Lets try and get this survey definition
				int refId = GeneralUtilityMethods.getSurveyId(sd, ref);
				Survey refSurvey = getById(
						sd, 
						cResults,
						remoteUser,
						refId,
						true,						// Get the full details of the survey
						basePath,
						null,						// Instance Id - not needed as we are not getting results
						false,						// Don't get results yet(See below)
						generateDummyValues,			// Set to true when getting results to fill a form with dummy values if there are no results
						getPropertyTypeQuestions,	// Set to true to get property questions such as _device
						false,						// Set to true to get soft deleted questions
						getHrk,						// Set to true to return HRK as a question if it exists in the survey
						getExternalOptions,			// external || internal || real (get external if they exist else get internal)
						false,						// Don't want change history for this sub survey	
						false,						// Don't get roles
						superUser,
						geomFormat,
						referencedSurveys,					// follow links to child surveys
						onlyGetLaunched		// Use with referenceSuveys to restrict results to those created by launching another form
					);
				
				
				if(refSurvey != null) {

					// Get the forms for this Survey and add them to the list of forms for the main survey
					s.forms.addAll(refSurvey.forms);
					
					Form mainSubForm = refSurvey.getFirstForm();					
					Result nr = new Result(qName, "form", null, false, fIdx, qIdx, 0, null, appearance);		// Result entry for this question
					
					String instanceId = null;
					String keyQuestionName = null;
					String keyQuestionValue = null;
					if(!generateDummyValues) {
						if(onlyGetLaunched) {
							String value = resultSet.getString(index);
							if(value != null && value.length() > 5) {
								instanceId = value.substring(5);
							} else {
								instanceId = "";
							}
						} else {
							keyQuestionName = getKeyQuestionName(q);		// The question in the child form that will hold the key value
							keyQuestionValue = getKeyQuestionValue();		// Either the primary key or hrk of this surveys submission
						}
					}
					
					nr.subForm = getResults(
							mainSubForm, 
							s.getFormIdx(mainSubForm.id),
							mainSubForm.id, 
							sd,
							cResults,
							remoteUser,							
							basePath,
							getPropertyTypeQuestions,	
							getHrk,		
							getExternalOptions,
							superUser,
							instanceId,
							0,								// parent key
							keyQuestionName,
							keyQuestionValue,
							s,
							generateDummyValues,
							geomFormat,
							oId,
							false,
							referencedSurveys,
							onlyGetLaunched
							);

					record.add(nr);
				}

				if(qType.equals("begin repeat")) {
					index--;		// Decrement the index as the begin repeat was not in the SQL query
				}

			} else if(qType.equals("begin group")) {

				record.add(new Result(qName, qType, null, false, fIdx, qIdx, 0, null, appearance));
				index--;		// Decrement the index as the begin group was not in the SQL query

			} else if(qType.equals("end group")) {

				record.add(new Result(qName, qType, null, false, fIdx, qIdx, 0, null, appearance));
				index--;		// Decrement the index as the end group was not in the SQL query

			} else if(qType.equals("select") && !compressed) {		// Get the data from all the option columns

				String sqlSelect = "select ";
				ArrayList<Option> options = new ArrayList<Option>(q.getValidChoices(s));

				boolean hasColumns = false;
				for(Option option : options) {
					if(option.published) {
						if(hasColumns) {
							sqlSelect += ",";
						}
						sqlSelect += q.columnName + "__" + option.columnName; 
						hasColumns = true;
					}
				}
				
				ResultSet resultSetOptions = null;
				if(hasColumns) {
					sqlSelect += " from " + form.tableName + " where prikey=" + priKey + ";";
						
					if(resultSet != null) {
						if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
						pstmtSelect = cResults.prepareStatement(sqlSelect);	 
	
						log.info("Get data from option columns: " + pstmtSelect.toString());
						resultSetOptions = pstmtSelect.executeQuery();
						resultSetOptions.next();		// There will only be one record
					}
				} else {
					log.info("No published options for question: " + q.name);
				}

				//Result nr = new Result(qName, qType, null, false, fIdx, qIdx, 0, listName, appearance);
				hasColumns = false;
				//int oIdx = -1;
				StringBuffer vBuffer = new StringBuffer("");
				for(Option option : options) {
					//oIdx++;
					boolean optSet = false;
					if(option.published) {
						String opt = q.columnName + "__" + option.columnName;
						if(resultSetOptions != null) {
							optSet = resultSetOptions.getBoolean(opt);
						}
					} 
					if(optSet) {		// Store as compressed
						if(vBuffer.length() > 0) {
							vBuffer.append(" ");
						}
						vBuffer.append(option.columnName);
					}
					//nr.choices.add(new Result(option.value, "choice", null, optSet, fIdx, qIdx, oIdx, listName, appearance)); 

				}
				//record.add(nr);	
				record.add(new Result(qName, qType, vBuffer.toString(), false, fIdx, qIdx, 0, listName, appearance));

				index--;		// Decrement the index as the select multiple was not in the SQL query

			} else if(qSource != null) {

				String value = "";
				if(resultSet != null) {
					value = resultSet.getString(index);
				}

				if(isTopLevel && qName.equals("_hrk")) {		// Save the HRK for use linking child surveys
					gHRK = value;
				}
				
				/*
				 * Leave the geometry in geoJson unless the geometry format needs to be comaptible with an xForm
				 */
				if(value != null && qType.equals("geopoint") && geomFormat != null && geomFormat.equals("xform")) {
					int idx1 = value.indexOf('[');
					int idx2 = value.indexOf(']');
					if(idx1 > 0 && (idx2 > idx1)) {
						value = value.substring(idx1 + 1, idx2 );
						// These values are in the order longitude latitude.  This needs to be reversed for the XForm
						String [] coords = value.split(",");
						if(coords.length > 1) {
							value = coords[1] + " " + coords[0] + " 0 0";
						}
					} else {
						log.severe("Invalid value for geopoint: " + value);
					}
				} 

				record.add(new Result(qName, qType, value, false, fIdx, qIdx, 0, null, appearance));

			}
			try {

			} catch (Exception e) {

			}
			index++;

		}

	}

	/*
	 * Update survey manifest
	 */
	public void updateSurveyManifest(Connection sd, int sId, String appearance, String calculation) throws Exception {

		String manifest = null;
		boolean changed = false;
		QuestionManager qm = new QuestionManager(localisation);

		PreparedStatement pstmtGet = null;
		String sqlGet = "select manifest from survey "
				+ "where s_id = ?; ";

		PreparedStatement pstmtUpdate = null;
		String sqlUpdate = "update survey set manifest = ? "
				+ "where s_id = ?;";	

		try {

			pstmtGet = sd.prepareStatement(sqlGet);
			pstmtGet.setInt(1, sId);
			ResultSet rs = pstmtGet.executeQuery();
			if(rs.next()) {
				manifest = rs.getString(1);
			}

			if(appearance != null) {
				ManifestInfo mi = GeneralUtilityMethods.addManifestFromAppearance(appearance, manifest);
				manifest = mi.manifest;
				if(mi.changed) {
					changed = true;
				}
			}

			if(calculation != null) {
				ManifestInfo mi = GeneralUtilityMethods.addManifestFromCalculate(calculation, manifest);
				manifest = mi.manifest;
				if(mi.changed) {
					changed = true;
				}
			}

			// Update the manifest
			if(changed) {
				pstmtUpdate = sd.prepareStatement(sqlUpdate);
				pstmtUpdate.setString(1, manifest);
				pstmtUpdate.setInt(2,sId);
				log.info("Updating manifest:" + pstmtUpdate.toString());
				pstmtUpdate.executeUpdate();
			}

			//GeneralUtilityMethods.updateSelfCalcsManifest(sd, sId);



		} catch(Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtGet != null) {pstmtGet.close();}} catch (SQLException e) {}
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (SQLException e) {}

		}	

	}

	/*
	 * Clean up the survey manifests removing any that are no longer used
	 */
	void removeUnusedSurveyManifests(Connection sd, int sId) throws SQLException, Exception {

		String sql = "select appearance, calculate from question "
				+ "where f_id in (select f_id from form where s_id = ?) "
				+ "and (appearance is not null or calculate is not null);";
		PreparedStatement pstmt = null;

		String sqlClear = "update survey set manifest = null where s_id = ?;";
		PreparedStatement pstmtClear = null;

		try {
			pstmtClear = sd.prepareStatement(sqlClear);
			pstmtClear.setInt(1, sId);
			log.info("Cleaning up manifest: " + pstmtClear.toString());
			pstmtClear.executeUpdate();

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("Cleaning up manifest. Getting questions that may affect manifest: " + pstmt.toString());

			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				updateSurveyManifest(sd, sId, rs.getString("appearance"), rs.getString("calculate"));
			}
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
			if(pstmtClear != null) try {pstmtClear.close();} catch(Exception e) {}
		}
	}

	/*
	 * Get the questions for a survey
	 */
	public ArrayList<Question> getQuestionsForSurvey(Connection sd, int sId, 
			boolean getPropertyTypeQuestions) throws Exception {

		ArrayList<Question> questions = new ArrayList<Question> ();

		// SQL to get the questions belonging to a form
		ResultSet rsGetQuestions = null;
		String sqlGetQuestions = "select q.q_id, "
				+ "q.qname, "
				+ "q.qtype, "
				+ "q.qtext_id, "
				+ "q.infotext_id, "
				+ "q.source, " 
				+ "q.calculate, "
				+ "q.seq, " 
				+ "q.defaultanswer, "
				+ "q.appearance, "
				+ "q.parameters, "
				+ "q.qconstraint, "
				+ "q.constraint_msg, "
				+ "q.required_msg, "
				+ "q.nodeset, "
				+ "q.relevant, "
				+ "q.visible, "
				+ "q.readonly, "
				+ "q.mandatory, "
				+ "q.published, "
				+ "q.column_name, "
				+ "q.source_param, "
				+ "q.soft_deleted, "
				+ "q.autoplay,"
				+ "q.accuracy,"
				+ "q.l_id,"
				+ "q.intent "
				+ "from question q,form f "
				+ "where q.f_id = f.f_id "
				+ "and f.s_id = ? "
				+ "order by q.f_id, q.seq asc;";
		PreparedStatement pstmtGetQuestions = sd.prepareStatement(sqlGetQuestions);

		/*
		 * Get the questions for this form
		 */
		try {
			pstmtGetQuestions.setInt(1, sId);
			log.info("Get questions for survey: " + pstmtGetQuestions.toString());
			rsGetQuestions = pstmtGetQuestions.executeQuery();

			boolean inMeta = false;				// Set true if the question is in the meta group
			while (rsGetQuestions.next()) {
				Question q = new Question();

				q.id = rsGetQuestions.getInt(1);
				q.name = rsGetQuestions.getString(2);
				q.type = rsGetQuestions.getString(3);
				q.text_id = rsGetQuestions.getString(4);
				q.hint_id = rsGetQuestions.getString(5);
				q.source = rsGetQuestions.getString(6);
				q.calculation = GeneralUtilityMethods.convertAllXpathNames(rsGetQuestions.getString(7), true);
				q.seq = rsGetQuestions.getInt(8);
				q.defaultanswer = rsGetQuestions.getString(9);
				q.appearance = GeneralUtilityMethods.convertAllXpathNames(rsGetQuestions.getString(10), true);
				q.paramArray = GeneralUtilityMethods.convertParametersToArray(rsGetQuestions.getString(11));
				q.parameters = rsGetQuestions.getString(11);		// For online editor - deprecate

				q.constraint = GeneralUtilityMethods.convertAllXpathNames(rsGetQuestions.getString(12), true);
				q.constraint_msg = rsGetQuestions.getString(13);
				q.required_msg = rsGetQuestions.getString(14);
				q.choice_filter = GeneralUtilityMethods.getChoiceFilterFromNodeset(rsGetQuestions.getString(15), true);

				q.relevant = GeneralUtilityMethods.convertAllXpathNames(rsGetQuestions.getString(16), true);
				q.visible = rsGetQuestions.getBoolean(17);
				q.readonly = rsGetQuestions.getBoolean(18);
				q.required = rsGetQuestions.getBoolean(19);
				q.published = rsGetQuestions.getBoolean(20);
				q.columnName = rsGetQuestions.getString(21);
				q.source_param = rsGetQuestions.getString(22);
				q.soft_deleted = rsGetQuestions.getBoolean(23);
				q.autoplay = rsGetQuestions.getString(24);
				q.accuracy = rsGetQuestions.getString(25);
				q.l_id = rsGetQuestions.getInt(26);
				q.intent = rsGetQuestions.getString(27);
				if(q.autoplay == null) {
					q.autoplay = "none";
				}

				// Set an indicator if this is a property type question (_device etc)
				q.propertyType = GeneralUtilityMethods.isPropertyType(q.source_param, q.name);

				// Discard property type questions if they have not been asked for
				if(q.propertyType && !getPropertyTypeQuestions) {
					continue;
				}


				// Track if this question is in the meta group
				if(q.name.equals("meta")) {
					inMeta = true;
				} else if(q.name.equals("meta_groupEnd")) {
					inMeta = false;
				}
				q.inMeta = inMeta;

				// If the survey was loaded from xls it will not have a list name
				if(q.list_name == null || q.list_name.trim().length() == 0) {
					q.list_name = q.name;
				}


				questions.add(q);
			}
		} finally {
			// Close statements
			try { if (pstmtGetQuestions != null) {pstmtGetQuestions.close();}} catch (SQLException e) {}
		}

		return questions;
	}

	public String fillStringTemplate(Survey s, String in) {
		String out = in;

		if(out != null && s != null) {
			InstanceMeta im = s.getInstanceMeta();
			out = out.replaceAll("\\$\\{instancename\\}", im.instancename);
			out = out.replaceAll("\\$\\{surveyname\\}", im.surveyname);
			out = out.replaceAll("\\$\\{hrk\\}", im.hrk);
			out = out.replaceAll("\\$\\{username\\}", im.username);
			out = out.replaceAll("\\$\\{device\\}", im.device);
		} else {
			log.info("Could not fill template details for: " + out + " : " + ((s == null) ? "survey is null" : "survey not null" ));
		}


		return out;
	}
	
	/*
	 * Get the group surveys
	 * Always add the survey corresponding to sId to the group
	 */
	public ArrayList<GroupDetails> getGroupDetails(Connection sd, int groupSurveyId, String user, int sId) throws SQLException {
		
		ArrayList<GroupDetails> groupSurveys = new ArrayList<> ();
		
		String sql = "select distinct s.s_id, s.display_name, s.ident "
				+ "from survey s, users u, user_project up "
				+ "where s.p_id = up.p_id "
				+ "and up.u_id = u.id "
				+ "and u.ident = ? "
				+ "and ((group_survey_id = ? and group_survey_id > 0) or s_id = ? or s_id = ?)";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, groupSurveyId);
			pstmt.setInt(3,  groupSurveyId);
			pstmt.setInt(4,  sId);
				
			log.info("Get group surveys: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				groupSurveys.add(new GroupDetails(rs.getInt(1), rs.getString(2), rs.getString(3)));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return groupSurveys;
	}
	
	/*
	 * Get the group forms
	 * Get the forms for the passed in group surveyId
	 */
	public HashMap<String, String> getGroupForms(Connection sd, int groupSurveyId) throws SQLException {
		
		HashMap<String, String> groupForms = new HashMap<> ();
		
		String sql = "select name, table_name from form where s_id in "
				+ "(select s_id from survey where group_survey_id = ? and group_survey_id > 0) or s_id = ?";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, groupSurveyId);
			pstmt.setInt(2, groupSurveyId);
			log.info("Get group forms: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				groupForms.put(rs.getString(1), rs.getString(2));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return groupForms;
	}
	
	/*
	 * Get the group tables
	 * Get all the tables that are part of the passed in group surveyId
	 */
	public ArrayList<String> getGroupTables(Connection sd, int groupSurveyId, int oId, String user, int sId) throws SQLException {
		
		ArrayList<String> groupForms = new ArrayList<> ();
		
		String sql = "select distinct f.table_name "
				+ "from form f, project p, survey s, users u, user_project up  "
				+ "where f.s_id = s.s_id "
				+ "and s.p_id = p.id "
				+ "and p.o_id = ? "
				+ "and s.p_id = up.p_id "
				+ "and u.id = up.u_id "
				+ "and u.ident = ? "
				+ "and (f.s_id in "
				+ "(select s_id from survey where group_survey_id > 0 and group_survey_id = ?) or f.s_id = ? or f.s_id = ?)";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setString(2, user);
			pstmt.setInt(3, groupSurveyId);
			pstmt.setInt(4, groupSurveyId);
			pstmt.setInt(5, sId);
			log.info("Get group forms: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				groupForms.add(rs.getString(1));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return groupForms;
	}
	
	/*
	 * Get the surveys and the table names that are shared with the passed in table name
	 */
	public HashMap<String, ArrayList<String>> getSharedTables(Connection sd, int sId) throws SQLException {
		
		HashMap<String, ArrayList<String>> sharedTables = new HashMap<> ();
		
		String sql = "select f.table_name, s.display_name "
				+ "from survey s, form f "
				+ "where s.s_id = f.s_id "
				+ "and s.s_id != ? "
				+ "and f.table_name in (select table_name from form where s_id = ?)";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, sId);
			log.info("Get shared tables: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String table = rs.getString(1);
				ArrayList<String> surveys = sharedTables.get(table);
				if(surveys == null) {
					surveys = new ArrayList<String> ();
					sharedTables.put(table, surveys);
				}
				surveys.add(rs.getString(2));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return sharedTables;
	}
	
	/*
	 * Get the group Questions
	 */
	public HashMap<String, String> getGroupQuestions(Connection sd, int groupSurveyId) throws SQLException {
		
		HashMap<String, String> groupQuestions = new HashMap<> ();
		
		String sql = "select qname, column_name from question where f_id in "
				+ "(select f_id from form where s_id in (select s_id from survey where group_survey_id = ? and deleted = 'false')) or "
				+ "f_id in (select f_id from form where s_id = ?)";

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, groupSurveyId);
			pstmt.setInt(2, groupSurveyId);
			log.info("Getting group questions: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				groupQuestions.put(rs.getString(1), rs.getString(2));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return groupQuestions;
	}
	
	/*
	 * Get the group Options
	 */
	public HashMap<String, String> getGroupOptions(Connection sd, int groupSurveyId) throws SQLException {
		
		HashMap<String, String> groupOptions = new HashMap<> ();
		
		String sql = "select o.ovalue, o.column_name, l.name from option o, listname l "
				+ "where o.l_id = l.l_id "
				+ "and l.s_id in "
				+ "(select s_id from survey where group_survey_id = ? union all select ? ) "
				+ "order by o.o_id desc";	// newest first

		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, groupSurveyId);
			pstmt.setInt(2, groupSurveyId);
			log.info("Getting group options: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				String key = rs.getString(3) + "__" + rs.getString(1);
				if(groupOptions.get(key) == null) {
					groupOptions.put(key, rs.getString(2));
				}
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		
		return groupOptions;
	}
	
	public void restore(Connection sd, int sId, String user) throws SQLException {
		
		String sql = "update survey set deleted='false', last_updated_time = now() where s_id = ?;";	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info(pstmt.toString());
			pstmt.executeUpdate();
			lm.writeLog(sd, sId, user, "restore", "Restore survey ");
			log.info("userevent: " + user + " : un delete survey : " + sId);
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
	}
	
	public void delete(Connection sd, 
			Connection cRel, 
			int sId, 
			boolean hard, 
			boolean delData, 
			String user, 
			String basePath,
			String tables,
			int newSurveyId		// If greater than 0 then this survey is being replaced
			) throws Exception {
		
		// Get the survey ident and name of the original survey
		String surveyIdent = null;			// Original survey ident
		String surveyName = null;			// Original survey name
		String surveyDisplayName = null;
		int projectId = 0;
		boolean hidden = false;
		
		String sql = "select s.name, s.ident, s.display_name, s.p_id, s.hidden "
				+ "from survey s "
				+ "where s.s_id = ?";
		PreparedStatement pstmtIdent = null;
		
		String sqlreplaced = "select s.s_id, s.name, s.ident, s.display_name, s.p_id "
				+ "from survey s "
				+ "where s.original_ident = ? "
				+ "and s.hidden = true";
		PreparedStatement pstmtReplaced = null;
		
		PreparedStatement pstmt = null;
		
		String sqlUpdateIdent = "update survey "
				+ "set ident = ?, "
				+ "original_ident = ?, "
				+ "hidden = ? "
				+ "where s_id = ?";
		PreparedStatement pstmtUpdateIdent = null;		
		
		try {
			pstmtIdent = sd.prepareStatement(sql);
			pstmtIdent.setInt(1, sId);
			ResultSet resultSet = pstmtIdent.executeQuery();
	
			if (resultSet.next()) {		
				surveyName = resultSet.getString("name");
				surveyIdent = resultSet.getString("ident");
				surveyDisplayName = resultSet.getString("display_name");
				projectId = resultSet.getInt("p_id");
			}
	
			/*
			 * Delete the survey. Either a soft or a hard delete
			 */
			if(hard) {
	
				// Only do a hard delete if a survey is not hidden
				if(!hidden) {
					ServerManager sm = new ServerManager();
					
					// Get the surveys that were replaced by this one
					pstmtReplaced = sd.prepareStatement(sqlreplaced);
					pstmtReplaced.setString(1, surveyIdent);
					log.info("Get replaced surveys: " + pstmtReplaced);
					ResultSet rs = pstmtReplaced.executeQuery();
					while (rs.next()) {
						int rSId = rs.getInt("s_id");
						String rSurveyIdent = rs.getString("ident");
						String rSurveyDisplayName = rs.getString("display_name");
						int rProjectId = rs.getInt("p_id");
						
						sm.deleteSurvey(		// Delete the replaced survey
								sd, 
								cRel,
								user,
								rProjectId,
								rSId,
								rSurveyIdent,
								rSurveyDisplayName,
								basePath,
								delData,
								tables);
						
					}
					
					sm.deleteSurvey(
							sd, 
							cRel,
							user,
							projectId,
							sId,
							surveyIdent,
							surveyDisplayName,
							basePath,
							delData,
							tables);
				}
	
			} else {
	
				// Add date and time to the display name
				DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd HH:mm:ss");
				dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
				Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));		// Store all dates in UTC
				String newDisplayName = surveyDisplayName + " (" + dateFormat.format(cal.getTime()) + ")";
	
				// Update the "name"
				String newName = null;
				if(surveyName != null) {
					int idx = surveyName.lastIndexOf('/');
					newName = surveyName;
					if(idx > 0) {
						newName = surveyName.substring(0, idx + 1) + GeneralUtilityMethods.convertDisplayNameToFileName(newDisplayName) + ".xml";
					}
				}
	
				// Update the survey definition to indicate that the survey has been deleted
				// Add the current date and time to the name and display name to ensure the deleted survey has a unique name 
				sql = "update survey set " +
						" deleted='true', " +
						" last_updated_time = now(), " +
						" name = ?, " +
						" display_name = ? " +
						"where s_id = ?;";	
	
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, newName);
				pstmt.setString(2, newDisplayName);
				pstmt.setInt(3, sId);
				log.info("Soft delete survey: " + pstmt.toString());
				pstmt.executeUpdate();
	
				lm.writeLog(sd, sId, user, "delete", "Soft Delete survey " + surveyDisplayName);
				log.info("userevent: " + user + " : soft delete survey : " + sId);
	
				// Rename files
				GeneralUtilityMethods.renameTemplateFiles(surveyDisplayName, newDisplayName, basePath, projectId, projectId);
			}
	
			/*
			 * Delete or update any panels that reference this survey
			 */
			if(newSurveyId == 0) {
				sql = "delete from dashboard_settings where ds_s_id = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, sId);
				log.info("Delete dashboard panels: " + pstmt.toString());
				pstmt.executeUpdate();
			} else {
				sql = "update dashboard_settings set ds_s_id = ? where ds_s_id = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, newSurveyId);
				pstmt.setInt(2, sId);
				log.info("Update dashboard panels: " + pstmt.toString());
				pstmt.executeUpdate();
			}
	
			/*
			 * Delete any survey views that reference this survey
			 */
			if(newSurveyId == 0) {
				sql = "delete from survey_settings where s_ident = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, surveyIdent);
				log.info("Delete survey views: " + pstmt.toString());
				pstmt.executeUpdate();
			}
	
			/*
			 * Delete or update any tasks that are to update this survey (Only do this if the survey is not being replaced)
			 */
			if(newSurveyId == 0) {
				// tasks
				sql = "update tasks set deleted = 'true', deleted_at = now() where survey_ident = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, surveyIdent);
				log.info("Delete tasks: " + pstmt.toString());
				pstmt.executeUpdate();
				
				// assignments
				sql = "update assignments set status = 'cancelled', cancelled_date = now() where task_id in "
						+ "(select id from tasks where survey_ident = ?)";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, surveyIdent);
				log.info("Delete assignments: " + pstmt.toString());
				pstmt.executeUpdate();
			} 
			
			/*
			 * Delete or update any notifications that are sent for this survey
			 */
			if(newSurveyId == 0) {
				sql = "delete from forward where s_id = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, sId);
				log.info("Delete notifications: " + pstmt.toString());
				pstmt.executeUpdate();
			} else {
				sql = "update forward set s_id = ? where s_id = ?;";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, newSurveyId);
				pstmt.setInt(2, sId);
				log.info("Update notifications: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
			/*
			 * Delete or update any task group rules that are to update this survey
			 */
			if(newSurveyId == 0) {
				sql = "update task_group "
						+ "set rule = null, source_s_id = 0, target_s_id = 0 "
						+ "where target_s_id = ? or source_s_id = ?";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, sId);
				pstmt.setInt(2, sId);
				log.info("Update task groups 1: " + pstmt.toString());
				pstmt.executeUpdate();
			} else {
				
				sql = "update task_group "
						+ "set source_s_id = ? "
						+ "where source_s_id = ?";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, newSurveyId);
				pstmt.setInt(2, sId);
				log.info("Update task groups 2: " + pstmt.toString());
				pstmt.executeUpdate();
				
				sql = "update task_group "
						+ "set target_s_id = ? "
						+ "where target_s_id = ?";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, newSurveyId);
				pstmt.setInt(2, sId);
				log.info("Update task groups 3: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
			/*
			 * Update group ids
			 */
			if(newSurveyId == 0) {
				sql = "update survey "
						+ "set group_survey_id = 0 "
						+ "where group_survey_id = ?";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, sId);
				log.info("Update group survey ids: " + pstmt.toString());
				pstmt.executeUpdate();
			} else {
				
				sql = "update survey "
						+ "set group_survey_id = ? "
						+ "where group_survey_id = ?";	
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, newSurveyId);
				pstmt.setInt(2, sId);
				log.info("Update group survey ids: " + pstmt.toString());
				pstmt.executeUpdate();
			}
			
			// Delete or update any reports for this survey
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			ActionManager am = new ActionManager(localisation, tz);
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, user);
			ArrayList<User> usersToDelete = am.getTemporaryUsers(sd, o_id, null, sId, 0);
			if(newSurveyId == 0) {
				sql = "delete from users where temporary is true and ident = ?";	
				if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
				pstmt = sd.prepareStatement(sql);
				for(User u : usersToDelete) {
					pstmt.setString(1, u.ident);
					log.info("Delete temporary user: " + pstmt.toString());
					pstmt.execute();
				}
			} else {
				sql = "update users set action_details = ? where temporary is true and ident = ?";	
				if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
				pstmt = sd.prepareStatement(sql);
				for(User u : usersToDelete) {
					replaceFormInAction(sd, u.action_details, u.action_details.sId, newSurveyId);
					u.action_details.sId = newSurveyId;
					pstmt.setString(1, gson.toJson(u.action_details));
					pstmt.setString(2, u.ident);
					log.info("update temporary user: " + pstmt.toString());
					pstmt.execute();
				}
			}
			
			
			/*
			 * Update the replacement table
			 *  Set the new survey ident to be the same as the old one
			 *  Extend the old survey ident in the now deleted survey
			 */			
			if(newSurveyId > 0) {
				pstmtUpdateIdent = sd.prepareStatement(sqlUpdateIdent);
				sd.setAutoCommit(false);
				
				// Modify the ident of the old survey
				pstmtUpdateIdent.setString(1, surveyIdent + "_" + newSurveyId);
				pstmtUpdateIdent.setString(2, surveyIdent);	// Original Ident
				pstmtUpdateIdent.setBoolean(3, true);		// Set hidden
				pstmtUpdateIdent.setInt(4, sId);
				pstmtUpdateIdent.executeUpdate();
				
				// Set the ident of the new survey to be the same as the old
				pstmtUpdateIdent.setString(1, surveyIdent);			
				pstmtUpdateIdent.setString(2, null);			// Original Ident (set to null as the current ident is the original)
				pstmtUpdateIdent.setBoolean(3, false);		// Visible
				pstmtUpdateIdent.setInt(4, newSurveyId);
				pstmtUpdateIdent.executeUpdate();
				
				sd.commit();				
			}
			
			/*
			 * Delete any entries in the csvtable
			 */
			SurveyTableManager stm = new SurveyTableManager(sd, localisation);
			stm.delete(sId);			// Delete references to this survey in the csv table so that they get regenerated
			
			/*
			 * The following tables reference the survey ident
			 * Delete any entries that reference the deleted survey ident
			 */
			sql = "delete from form_downloads where form_ident = ?;";	
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, surveyIdent);
			log.info("Delete form downloads: " + pstmt.toString());
			pstmt.executeUpdate();
		
			sql = "delete from task_completion where form_ident = ?;";	
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, surveyIdent);
			log.info("Delete task completion downloads: " + pstmt.toString());
			pstmt.executeUpdate();
				
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			try {sd.rollback();} catch (Exception ex) {}
			throw e;
			
		} finally {
			try {if (pstmtIdent != null) {pstmtIdent.close();}} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateIdent != null) {pstmtUpdateIdent.close();}} catch (SQLException e) {}
			try {if (pstmtReplaced != null) {pstmtReplaced.close();}} catch (SQLException e) {}
		}
	}
	
	private ArrayList<MetaItem> getLegacyMeta() {
		ArrayList<MetaItem> meta = new ArrayList<MetaItem>();
		
		// TODO get legacy meta
		// TODO write legacy meta to the survey defn
		
		return meta;
	}
	
	/*
	 * If a form is replaced the form will need to be replaced in the report
	 */
	private void replaceFormInAction(Connection sd, Action action, int origSurveyId, int newSurveyId) throws SQLException {
		
		String sql = "select f_id from form where s_id = ? and table_name in "
				+ "(select table_name from form where s_id = ? and f_id = ?)";
		PreparedStatement pstmt = null;
		int originalfId = 0;
		int newFid;
		try {
			if(action.parameters != null) {
				for(KeyValueSimp p : action.parameters) {
					if(p.k.equals("form")) {
						originalfId = Integer.parseInt(p.v);
						break;
					}
				}
				if(originalfId > 0) {
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1, newSurveyId);
					pstmt.setInt(2, origSurveyId);
					pstmt.setInt(3, originalfId);
					log.info("Get new form id for report: " + pstmt.toString());
					ResultSet rs = pstmt.executeQuery();
					if(rs.next()) {
						newFid = rs.getInt(1);
						for(KeyValueSimp p : action.parameters) {
							if(p.k.equals("form")) {
								p.v = String.valueOf(newFid);
								break;
							}
						}
					}
				}
			}
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}};
		}
	}
	
	public void updateSearchChoices(Connection sd, PreparedStatement pstmt, String pVal, int sId, int qId) throws Exception {
		int listId = -1;
		Option o = new Option();
		o.labels = new ArrayList<Label> ();
		
		if(pVal.length() > 0) {
			if(pVal != null && pVal.trim().length() > 0) {
				String [] vArray = pVal.split("\\s+");
				ArrayList<String> elems = new ArrayList<String>();
				Collections.addAll(elems, vArray);
				for(String e : elems) {
					String [] eArray = e.split("::");
					if(eArray.length > 1) {
						if(eArray[0].equals("_sv")) {
							o.value = eArray[1];
						} else if(eArray.length > 2) {
							if(eArray[0].equals("_sl")) {
								Label l = new Label();
								l.text = eArray[2];
								o.labels.add(l);
							}
						}
					}
				}
			}
			
			if(o.value != null) {
				
				/*
				 * Get the list id
				 */
				String sqlListId = "select l_id from question where q_id = ?";
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sqlListId);
				pstmt.setInt(1,  qId);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					listId = rs.getInt(1);

					/*
					 * Get the existing choices for this list
					 * All those choices that have a non numeric value will need to be deleted
					 */
					try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
					pstmt = sd.prepareStatement(sqlGetOptions);
					pstmt.setInt(1, listId);
					ResultSet rsGetOptions = pstmt.executeQuery();
					ArrayList<Option> optionsToDelete = new ArrayList<Option> ();
					int sequenceNumber = -1;
					while(rsGetOptions.next()) {
					
						int id = rsGetOptions.getInt("o_id");
						String exVal = rsGetOptions.getString("value");
						int seq = rsGetOptions.getInt("seq");
						
						boolean isInteger = false;
						try {
							int x = Integer.parseInt(exVal);
							isInteger = true;
						} catch (Exception e) {
							
						}
						if(isInteger) {
							continue;		// Ignore numeric options
						} else {
							if(sequenceNumber == -1) {
								sequenceNumber = seq;		// Remember this as the sequence number to use
							}
							// Delete non numeric values
							Option oToDelete = new Option();
							oToDelete.id = id;
							oToDelete.value = exVal;
							optionsToDelete.add(oToDelete);
						}
					}
					
					QuestionManager qm = new QuestionManager(localisation);	
					
					/*
					 * Delete the existing text choices
					 */
					qm.deleteOptions(sd, sId, optionsToDelete, true, listId);							
					
					/*
					 * Insert the new choice
					 */
					if(sequenceNumber == -1) {
						sequenceNumber = 0;
					}
					o.seq = sequenceNumber;								
					ArrayList<Option> options = new ArrayList<Option> ();
					options.add(o);
					qm.saveOptions(sd, sId, options, true, listId);
				}
				
			} else {
				throw new Exception("Error: value in search appearance choice was null: ");
			}
		}
	}
	
	/*
	 * Get the key question name from the question parameters
	 */
	String getKeyQuestionName(Question q) {		
		return GeneralUtilityMethods.getSurveyParameter("key_question", q.paramArray);
	}
	
	/*
	 * Get the reference survey identifier from the question parameters
	 */
	public String getReferenceSurveyIdentifier(Question q) {
		return GeneralUtilityMethods.getSurveyParameter("form_identifier", q.paramArray);	
	}
	
	/*
	 * Get the key question value
	 * When this function has been called the key question value should already have been set either as
	 * the primary key of the parent survey or its HRK
	 */
	String getKeyQuestionValue() {
		if(gHRK != null) {
			return gHRK;
		} else {
			return gPrimaryKey;
		}
	}
	
	/*
	 * Get the instance data for a record in a survey
	 */
	public ArrayList<Instance> getInstances(
			Connection sd,
			Connection cResults, 
			Survey s, 
			Form form, 
			int parkey,
			String hrk,				// Usually either hrk or instanceId would be used to identify the instance
			String instanceId,
			SurveyManager sm
			) throws Exception {

		ArrayList<TableColumn> columns = null;
		ArrayList<Instance> instances = new ArrayList<Instance> ();
		
		StringBuffer sql = new StringBuffer("");
		sql.append("select prikey ");
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSelect = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();

		try {
			
			TableDataManager tdm = new TableDataManager(localisation, tz);
			
			String serverName = GeneralUtilityMethods.getSubmissionServer(sd);
			String urlprefix = "https://" + serverName + "/";	
			
			if(!GeneralUtilityMethods.tableExists(cResults, form.tableName)) {
				throw new ApplicationException(localisation.getString("imp_no_file"));
			}
			columns = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					localisation,
					"none",
					s.id,
					s.ident,
					null,
					null,		// roles for column filtering TODO add support
					0,			// parent form id
					form.id,
					form.tableName,
					true,		// Read Only
					false,		// Parent key
					false,
					true,		// include instance id
					true,		// include prikey
					false,		// include other meta data
					false,		// include preloads
					true,		// include instancename
					false,		// include survey duration
					false,
					false,		// include HXL
					false,
					tz,
					false		// mgmt
					);

			pstmt = tdm.getPreparedStatement(
					sd, 
					cResults,
					columns,
					urlprefix,
					s.id,
					form.tableName,
					parkey,
					hrk,
					null,
					null,		// roles for row filtering TODO add support
					null,		// sort
					null,		// sort direction
					false,		// mgmt
					false,		// group
					false,		// prepare for data tables
					0,			// start
					false,		// get parkey
					0,			// start parkey
					false,		// super user
					false,		// Return records greater than or equal to primary key
					"none",		// include bad
					null	,		// no custom filter
					null,		// key filter
					tz,
					instanceId,
					null	,			// advanced filter
					null,			// Date filter name
					null,			// Start date
					null				// End date
					);
			
			if(pstmt != null) {
				ResultSet rs = pstmt.executeQuery();
				
				while(rs.next()) {
					Instance instance = new Instance();
					int prikey = 0;
					for (int i = 0; i < columns.size(); i++) {
						TableColumn c = columns.get(i);
						String name = null;
						String value = null;
						
						name = c.displayName;
						if(name.equals("prikey")) {
							prikey = rs.getInt(i + 1);
						} else if (c.type.equals("geopoint")) {
							// Add Geometry (assume one geometry type per table)
							//instance.geometry = parser.parse(rs.getString(i + 1)).getAsJsonObject();
							instance.point_geometry = gson.fromJson(rs.getString(i + 1), Point.class);
						} else if (c.type.equals("geoshape")) {
							instance.polygon_geometry = gson.fromJson(rs.getString(i + 1), Polygon.class);
						} else if (c.type.equals("geotrace")) {
							instance.line_geometry = gson.fromJson(rs.getString(i + 1), Line.class);
						} else if (c.type.equals("select1") && c.selectDisplayNames) {
							// Convert value to display name
							value = rs.getString(i + 1);
							for(KeyValue kv: c.choices) {
								if(kv.k.equals(value)) {
									value = kv.v;
									break;
								}
							}
						} else if (c.type.equals("decimal")) {
							Double dValue = rs.getDouble(i + 1);
							dValue = Math.round(dValue * 10000.0) / 10000.0;
							value = String.valueOf(dValue);
						} else if (c.type.equals("dateTime")) {
							value = rs.getString(i + 1);
							if (value != null) {
								value = value.replaceAll("\\.[0-9]+", ""); // Remove milliseconds
							}
						} else if (c.type.equals("calculate")) {
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
							
						if(!name.equals("prikey") && !c.type.equals("begin repeat")) {
							instance.values.put(name, value);
						}
		
								
					}
					
					/*
					 * Check for repeats
					 */
					for(Form f : s.forms) {
						if(f.parentform == form.id) {
							if(instance.repeats == null) {
								instance.repeats = new HashMap<String, ArrayList<Instance>> ();
							}
							int parentQuestion = f.parentQuestionIndex;
							Question q = form.questions.get(parentQuestion);
							String qName = q.name;
							if(q.display_name != null) {
								qName = q.display_name;
							}
							if(instance.repeats.get(qName) == null) {
								instance.repeats.put(qName, new ArrayList<Instance> ());
							}
							
							ArrayList<Instance> repeats = instance.repeats.get(qName);
							repeats.addAll(sm.getInstances(
									sd,
									cResults,
									s,
									s.getSubFormQId(form, q.id),
									prikey,
									null,
									null,
									sm));
						}
					}
						
					instances.add(instance);
	
				}
			}
			
			
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
		}

		return instances;
	}
}
