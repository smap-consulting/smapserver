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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.ChangeElement;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeLog;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.ManifestInfo;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class SurveyManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyManager.class.getName());

	public ArrayList<Survey> getSurveys(Connection sd, PreparedStatement pstmt,
			String user, 
			boolean getDeleted, 
			boolean getBlocked,
			int projectId			// Set to 0 to get all surveys regardless of project
			) throws SQLException {
		
		ArrayList<Survey> surveys = new ArrayList<Survey>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident, s.managed_id, s.version" +
				" from survey s, users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and p.o_id = u.o_id" +
				" and u.ident = ? ";
		
		// only return surveys in the users organisation unit + assigned project id 
		// If a specific valid project id was passed then restrict surveys to that project as well
		
		if(projectId != 0) {
			sql += "and s.p_id = ? ";
		}
		if(!getDeleted) {
			sql += "and s.deleted = 'false'";
		} 
		if(!getBlocked) {
			sql += "and s.blocked = 'false'";
		}
		sql += "order BY s.display_name;";
	
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setString(1, user);
		if(projectId != 0) {
			pstmt.setInt(2, projectId);
		}
		log.info("Get surveys: " + pstmt.toString());
		resultSet = pstmt.executeQuery();

		while (resultSet.next()) {								

			Survey s = new Survey();
			s.setId(resultSet.getInt(1));
			s.setName(resultSet.getString(2));
			s.setDisplayName(resultSet.getString(3));
			s.setDeleted(resultSet.getBoolean(4));
			s.setBlocked(resultSet.getBoolean(5));
			s.setIdent(resultSet.getString(6));
			s.setManagedId(resultSet.getInt(7));
			s.setVersion(resultSet.getInt(8));
			
			surveys.add(s);
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
			boolean full,		// Get the full details of the survey
			String basePath,
			String instanceId,	// If set get the results for this instance
			boolean getResults,	// Set to true to get results, if set and instanceId is null then blank data will be added
			boolean generateDummyValues,		// Set to true when getting results to fill a form with dummy values if there are no results
			boolean getPropertyTypeQuestions,	// Set to true to get property questions such as _device
			boolean getSoftDeleted				// Set to true to get soft deleted questions
			) throws SQLException, Exception {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.s_id, s.name, s.ident, s.display_name, s.deleted, s.blocked, p.name, p.id, " +
				" s.def_lang, s.task_file, u.o_id, s.class," +
				" s.instance_name " +
				" from survey s, users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and u.ident = ? " +
				" and s.s_id = ?; ";
	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, user);
			pstmt.setInt(2, sId);
	
			log.info("Get Survey info: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();	
			if (resultSet.next()) {								
	
				s = new Survey();
				s.setId(resultSet.getInt(1));
				s.setName(resultSet.getString(2));
				s.setIdent(resultSet.getString(3));
				s.setDisplayName(resultSet.getString(4));
				s.setDeleted(resultSet.getBoolean(5));
				s.blocked = resultSet.getBoolean(6);
				s.setPName(resultSet.getString(7));
				s.setPId(resultSet.getInt(8));
				s.def_lang = resultSet.getString(9);
				s.task_file = resultSet.getBoolean(10);
				s.o_id = resultSet.getInt(11);
				s.surveyClass = resultSet.getString(12);
				s.instanceNameDefn = GeneralUtilityMethods.convertAllXpathNames(resultSet.getString(13), true);
				
				// Get the pdf template
				File templateFile = GeneralUtilityMethods.getPdfTemplate(basePath, s.displayName, s.p_id);
				if(templateFile.exists()) {
					s.pdfTemplateName = templateFile.getName();
				}
			} 
			
			if(full && s != null) {
				
				populateSurvey(sd, s, basePath, user, getPropertyTypeQuestions);			// Add forms, questions, options
				
				if(getResults) {								// Add results
					
					Form ff = s.getFirstForm();
					s.instance.results = getResults(ff, s.getFormIdx(ff.id), -1, 0,	cResults, instanceId, 0, s, generateDummyValues);
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
		} catch (SQLException e) {
			throw e;
		} catch (Exception e) {
			throw e;
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
			int existingFormId,
			boolean sharedResults
			) throws SQLException, Exception {
		
		int sId;
		int fId;
		String ident = null;
		String tablename = null;
		
		String sql1 = "insert into survey ( s_id, display_name, deleted, p_id, version, last_updated_time)" +
				" values (nextval('s_seq'), ?, 'false', ?, 1, now());";
		
		String sql2 = "update survey set name = ?, ident = ? where s_id = ?;";
	
		String sql3 = "insert into form ( f_id, s_id, name, table_name, parentform, repeats, path) " +
				" values (nextval('f_seq'), ?, 'main', ?, 0, 'false', '/main');";
		
		String sql4 = "insert into question (q_id, f_id, qtype, qname, path, column_name, seq, visible, source, source_param, calculate) "
				+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, 'false', ?, ?, ?);";
		
		PreparedStatement pstmt = null;
		
		try {
			
			sd.setAutoCommit(false);
			
			// 1 Create basic survey
			pstmt = sd.prepareStatement(sql1, Statement.RETURN_GENERATED_KEYS);		
			pstmt.setString(1, name);
			pstmt.setInt(2, projectId);
			
			log.info("Create new survey: " + pstmt.toString());
			pstmt.execute();
			ResultSet rs = pstmt.getGeneratedKeys();
			rs.next();
		
			// 2 Update values dependent on the sId
			sId = rs.getInt(1);
			ident = "s" + projectId +"_" + sId;
			
			pstmt.close();
			pstmt = sd.prepareStatement(sql2);
			pstmt.setString(1, ident);
			pstmt.setString(2,  ident);
			pstmt.setInt(3,  sId);
			
			log.info("Create new survey part 2: " + pstmt.toString());
			pstmt.execute();
			
			/*
			 * 3. Create forms
			 */
			if(existing) {
				System.out.println("Copying existing form");
				QuestionManager qm = new QuestionManager();
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
				
				pstmt.close();
				pstmt = sd.prepareStatement(sql3, Statement.RETURN_GENERATED_KEYS);
				pstmt.setInt(1,  sId);
				pstmt.setString(2,  tablename);
				
				log.info("Create new form: " + pstmt.toString());
				pstmt.execute();
				
				rs = pstmt.getGeneratedKeys();
				rs.next();
				fId = rs.getInt(1);
				
				// 6. Add questions
				pstmt.close();
				pstmt = sd.prepareStatement(sql4);
				
				pstmt.setInt(1, fId);			// Form Id		
				
				// Device ID
				pstmt.setString(2,  "string");			// Type
				pstmt.setString(3, "_device");			// Name
				pstmt.setString(4,  "/main/_device");	// Path
				pstmt.setString(5, "_device");			// Column Name
				pstmt.setInt(6, -10);					// Sequence
				pstmt.setString(7, "property");			// Source
				pstmt.setString(8, "deviceid");			// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
				
				// start time
				pstmt.setString(2,  "dateTime");		// Type
				pstmt.setString(3, "_start");			// Name
				pstmt.setString(4,  "/main/_start");	// Path
				pstmt.setString(5, "_start");			// Column Name
				pstmt.setInt(6, -9);					// Sequence
				pstmt.setString(7, "timestamp");		// Source
				pstmt.setString(8, "start");			// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
				
				// Device ID
				pstmt.setString(2,  "dateTime");		// Type
				pstmt.setString(3, "_end");				// Name
				pstmt.setString(4,  "/main/_end");		// Path
				pstmt.setString(5, "_end");				// Column Name
				pstmt.setInt(6, -8);					// Sequence
				pstmt.setString(7, "timestamp");		// Source
				pstmt.setString(8, "end");				// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
				
				// Meta Group
				pstmt.setString(2,  "begin group");		// Type
				pstmt.setString(3, "meta");				// Name
				pstmt.setString(4,  "/main/meta");		// Path
				pstmt.setString(5, "meta");				// Column Name
				pstmt.setInt(6, -7);					// Sequence
				pstmt.setString(7, null);				// Source
				pstmt.setString(8, null);				// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
				
				// instance id
				pstmt.setString(2,  "string");			// Type
				pstmt.setString(3, "instanceID");		// Name
				pstmt.setString(4,  "/main/meta/instanceID");		// Path
				pstmt.setString(5, "instanceid");		// Column Name
				pstmt.setInt(6, -6);					// Sequence
				pstmt.setString(7, "user");				// Source
				pstmt.setString(8, null);				// Source Param
				pstmt.setString(9, "concat('uuid:', uuid())");	// Calculation
				pstmt.execute();
				
				// instance name
				pstmt.setString(2,  "string");			// Type
				pstmt.setString(3, "instanceName");		// Name
				pstmt.setString(4,  "/main/meta/instanceName");		// Path
				pstmt.setString(5, "instancename");		// Column Name
				pstmt.setInt(6, -5);					// Sequence
				pstmt.setString(7, "user");				// Source
				pstmt.setString(8, null);				// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
				
				// Meta Group End
				pstmt.setString(2,  "end group");		// Type
				pstmt.setString(3, "meta_groupEnd");	// Name
				pstmt.setString(4,  "/main/meta_groupEnd");		// Path
				pstmt.setString(5, "meta_groupend");	// Column Name
				pstmt.setInt(6, -4);					// Sequence
				pstmt.setString(7, null);				// Source
				pstmt.setString(8, null);				// Source Param
				pstmt.setString(9, null);				// Calculation
				pstmt.execute();
			}
			
			sd.commit();
			sd.setAutoCommit(true);

		} catch (SQLException e) {
			try{sd.rollback();} catch(Exception ex) {};
			try{sd.setAutoCommit(true);} catch(Exception ex) {};
			throw e;
		} catch (Exception e) {
			try{sd.rollback();} catch(Exception ex) {};
			try{sd.setAutoCommit(true);} catch(Exception ex) {};
			throw e;
		} finally {
			
			if(pstmt != null) try {pstmt.close();} catch(Exception e){};
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
		String sql = "select distinct s.s_id, s.name, s.display_name, s.deleted, s.blocked, s.ident" +
				" from survey s, users u, user_project up, project p, question q, form f " +
				" where s.s_id = f.s_id " +
				" and f.f_id = q.f_id " +
				" and q.appearance like '%search(''" + csvRoot + "''%' " +
				" and s.p_id = p.id" +
				" and s.deleted = 'false'" +
				" and s.blocked = 'false'" +
				" and p.o_id = u.o_id" +
				" and u.ident = ? " +
				"order BY s.display_name;";
		
	
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			
			log.info("Get a survey by organisation id: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
	
			while (resultSet.next()) {								
	
				Survey s = new Survey();
				s.setId(resultSet.getInt(1));
				s.setName(resultSet.getString(2));
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
	private void populateSurvey(Connection sd, Survey s, String basePath, String user, boolean getPropertyTypeQuestions) throws Exception {
		
		/*
		 * Prepared Statements
		 */
		
		// SQL to get the forms belonging to this survey
		ResultSet rsGetForms = null;
		String sqlGetForms = "select f.f_id, "
				+ "f.name, "
				+ "f.parentform, "
				+ "f.parentquestion, "
				+ "f.table_name "
				+ "from form f where f.s_id = ?;";
		PreparedStatement pstmtGetForms = sd.prepareStatement(sqlGetForms);	
		
		// SQL to get the questions belonging to a form
		ResultSet rsGetQuestions = null;
		String sqlGetQuestions = "select q.q_id, q.qname, q.qtype, q.qtext_id, l.name, q.infotext_id, "
				+ "q.source, " 
				+ "q.calculate, "
				+ "q.seq, " 
				+ "q.defaultanswer, "
				+ "q.appearance, "
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
				+ "q.path, "
				+ "q.soft_deleted, "
				+ "q.autoplay "
				+ "from question q "
				+ "left outer join listname l on q.l_id = l.l_id "
				+ "where q.f_id = ? "
				+ "order by q.seq asc;";
		PreparedStatement pstmtGetQuestions = sd.prepareStatement(sqlGetQuestions);

		// SQL to get the choice lists in this survey
		ResultSet rsGetRepeatValue = null;
		String sqlGetRepeatValue = "select repeats "
				+ "from form "
				+ "where s_id = ? "
				+ "and parentquestion = ?;";
		PreparedStatement pstmtGetRepeatValue = sd.prepareStatement(sqlGetRepeatValue);
		
		// SQL to get the choice lists in this survey
		ResultSet rsGetLists = null;
		String sqlGetLists = "select l_id, "
				+ "name "
				+ "from listname "
				+ "where s_id = ?;";
		PreparedStatement pstmtGetLists = sd.prepareStatement(sqlGetLists);
		
		// SQL to get the options belonging to a choice list		
		ResultSet rsGetOptions = null;
		String sqlGetOptions = "select o.o_id, "
				+ "o.ovalue as value, "
				+ "o.label_id, "
				+ "o.externalfile, "
				+ "o.cascade_filters, "
				+ "o.column_name, "
				+ "o.published "
				+ "from option o "
				+ "where o.l_id = ? "
				+ "order by o.seq";
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
				+ "c.updated_time, "
				+ "c.apply_results, "
				+ "c.success, "
				+ "c.msg " 
				+ "from survey_change c, users u "
				+ "where c.s_id = ? "
				+ "and c.user_id = u.id "
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
		
		
		// Get the Forms
		pstmtGetForms.setInt(1, s.id);
		rsGetForms = pstmtGetForms.executeQuery();
		
		while (rsGetForms.next()) {								
			Form f = new Form();
			f.id = rsGetForms.getInt(1);
			f.name = rsGetForms.getString(2);
			f.parentform =rsGetForms.getInt(3); 
			f.parentQuestion = rsGetForms.getInt(4);
			f.tableName = rsGetForms.getString(5);
				
			/*
			 * Get the questions for this form
			 */
			pstmtGetQuestions.setInt(1, f.id);
			log.info("Get questions for form: " + pstmtGetQuestions.toString());
			rsGetQuestions = pstmtGetQuestions.executeQuery();
			
			boolean inMeta = false;				// Set true if the question is in the meta group
			while (rsGetQuestions.next()) {
				Question q = new Question();
				
				q.id = rsGetQuestions.getInt(1);
				q.name = rsGetQuestions.getString(2);
				q.type = rsGetQuestions.getString(3);
				q.text_id = rsGetQuestions.getString(4);
				q.list_name = rsGetQuestions.getString(5);
				q.hint_id = rsGetQuestions.getString(6);
				q.source = rsGetQuestions.getString(7);
				q.calculation = GeneralUtilityMethods.convertAllXpathNames(rsGetQuestions.getString(8), true);
				q.seq = rsGetQuestions.getInt(9);
				q.defaultanswer = rsGetQuestions.getString(10);
				q.appearance = rsGetQuestions.getString(11);
				
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
				q.path = rsGetQuestions.getString(23);
				q.soft_deleted = rsGetQuestions.getBoolean(24);
				q.autoplay = rsGetQuestions.getString(25);
				if(q.autoplay == null) {
					q.autoplay = "none";
				}
				
				// Set an indicator if this is a property type question (_device etc)
				q.propertyType = GeneralUtilityMethods.isPropertyType(q.source_param, q.name, q.path);
				
				// Discard property type questions if they have not been asked for
				if(q.propertyType && !getPropertyTypeQuestions) {
					continue;
				}
				
				// If this is a begin repeat set the calculation from its form
				if(q.type.equals("begin repeat") || q.type.equals("geopolygon") || q.type.equals("geolinestring")) {
					pstmtGetRepeatValue.setInt(1, s.id);
					pstmtGetRepeatValue.setInt(2, q.id);
					
					log.info("Get repeat from form: " + pstmtGetRepeatValue.toString());
					rsGetRepeatValue = pstmtGetRepeatValue.executeQuery();
					if(rsGetRepeatValue.next()) {
						q.calculation = GeneralUtilityMethods.convertAllXpathNames(rsGetRepeatValue.getString(1), true);
					}
					
				}
				
				// Translate type name to "note" if it is a read only string
				q.type = GeneralUtilityMethods.translateTypeFromDB(q.type, q.readonly, q.visible);
				
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
				
				// Get the language labels
				UtilityMethodsEmail.getLabels(sd, s, q.text_id, q.hint_id, q.labels, basePath, oId);
				//q.labels_orig = q.labels;		// Set the original label values
							
				f.questions.add(q);
			}
			
			s.forms.add(f);
			s.forms_orig.add(f);
			
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
		
		while(rsGetLists.next()) {
			
			int listId = rsGetLists.getInt(1);
			String listName = rsGetLists.getString(2);
			
			OptionList optionList = new OptionList ();
			optionList.options = new ArrayList<Option> ();
				
			pstmtGetOptions.setInt(1, listId);
			log.info("SQL Get options: " + pstmtGetOptions.toString());
			rsGetOptions = pstmtGetOptions.executeQuery();
				
			Type hmType = new TypeToken<HashMap<String, String>>(){}.getType();		// Used to translate cascade filters json
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				
			while(rsGetOptions.next()) {
				Option o = new Option();
				o.id = rsGetOptions.getInt(1);
				o.value = rsGetOptions.getString(2);
				o.text_id = rsGetOptions.getString(3);
				o.externalFile = rsGetOptions.getBoolean(4);
				String cascade_filters = rsGetOptions.getString(5);
				if(cascade_filters != null) {
					o.cascadeKeyValues = gson.fromJson(cascade_filters, hmType);
				}
				o.columnName = rsGetOptions.getString(6);
				o.published = rsGetOptions.getBoolean(7);
					
				// Get the labels for the option
				UtilityMethodsEmail.getLabels(sd, s, o.text_id, null, o.labels, basePath, oId);
				optionList.options.add(o);
			}
				
			s.optionLists.put(listName, optionList);
			s.optionLists_orig.put(listName, optionList);
			
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
		pstmtGetChanges.setInt(1, s.getId());
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
			cl.success = rsGetChanges.getBoolean(7);
			cl.msg = rsGetChanges.getString(8);

			s.changes.add(cl);
		}
		
		// Close statements
		try { if (pstmtGetForms != null) {pstmtGetForms.close();}} catch (SQLException e) {}
		try { if (pstmtGetQuestions != null) {pstmtGetQuestions.close();}} catch (SQLException e) {}
		try { if (pstmtGetOptions != null) {pstmtGetOptions.close();}} catch (SQLException e) {}
		try { if (pstmtGetSSC != null) {pstmtGetSSC.close();}} catch (SQLException e) {}
		try { if (pstmtGetChanges != null) {pstmtGetChanges.close();}} catch (SQLException e) {}
		try { if (pstmtGetRepeatValue != null) {pstmtGetRepeatValue.close();}} catch (SQLException e) {}
	}
	

	

	
	
	/*
	 * Get the project id and the block status of a survey given its ident
	 */
	public Survey getSurveyId(Connection sd, String key) {
		
		Survey s = null;	// Survey to return
		ResultSet resultSet = null;
		String sql = "select s.p_id, s.s_id, s.blocked, s.class, s.deleted, s.display_name " +
				" from survey s" +
				" where s.ident = ?; ";
		
		String sql2 = "select s.p_id, s.s_id, s.blocked, s.class, s.deleted, s.display_name " +		// Hack due to issue with upgrade of a server where ident not set to survey id by default
				" from survey s" +
				" where s.s_id = ?; ";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		try {
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setString(1, key);			
			log.info("Get survey id: " + pstmt.toString());
	
			resultSet = pstmt.executeQuery();
	
			if (resultSet.next()) {								
				s = new Survey();
				s.setPId(resultSet.getInt(1));
				s.setId(resultSet.getInt(2));
				s.setBlocked(resultSet.getBoolean(3));
				s.surveyClass = resultSet.getString(4);
				s.deleted = resultSet.getBoolean(5);
				s.displayName = resultSet.getString(6);
				
				
			} else {	// Attempt to find the survey assuming the ident is the survey id
				pstmt2 = sd.prepareStatement(sql2);	
				int sId = 0;
				try {
					sId = Integer.parseInt(key);
				} catch (Exception e) {
					
				}
				pstmt2.setInt(1, sId);
				
				log.info("Find survey: " + pstmt2.toString());
				
				resultSet = pstmt2.executeQuery();
				
				if (resultSet.next()) {								
					s = new Survey();
					s.setPId(resultSet.getInt(1));
					s.setId(resultSet.getInt(2));
					s.setBlocked(resultSet.getBoolean(3));
					s.surveyClass = resultSet.getString(4);
					s.deleted = resultSet.getBoolean(5);
					s.displayName = resultSet.getString(6);
				} else {			
					log.info("Error: survey not found");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
				
			}
		}
		
		return s;
		
	}
	
	/*
	 * Apply an array of change sets to a survey
	 * Apply updates
	 */
	public ChangeResponse applyChangeSetArray(Connection connectionSD, 
			Connection cResults,
			int sId, String ident, ArrayList<ChangeSet> changes) throws Exception {
		
		ChangeResponse resp = new ChangeResponse();	// Response object
		resp.changeSet = changes;
		
		int userId = -1;
		ResultSet rs = null;
		

		PreparedStatement pstmtChangeLog = null;
		PreparedStatement pstmt = null;
		
		try {
			
			String sqlChangeLog = "insert into survey_change " +
					"(s_id, version, changes, user_id, apply_results, updated_time) " +
					"values(?, ?, ?, ?, 'true', ?)";
			pstmtChangeLog = connectionSD.prepareStatement(sqlChangeLog);
			
			/*
			 * Get the user id
			 * This should be saved rather than the ident as a user could be deleted
			 *  then a new user created with the same ident but its a different user
			 */
			userId = GeneralUtilityMethods.getUserId(connectionSD, ident);
			
			connectionSD.setAutoCommit(false);
			
			/*
			 * Lock the survey
			 * update version number of survey and get the new version
			 */
			String sqlUpdateVersion = "update survey set version = version + 1 where s_id = ?";
			String sqlGetVersion = "select version from survey where s_id = ?";
			pstmt = connectionSD.prepareStatement(sqlUpdateVersion);
			pstmt.setInt(1, sId);
			pstmt.execute();
			pstmt.close();
			
			pstmt = connectionSD.prepareStatement(sqlGetVersion);
			pstmt.setInt(1, sId);
			rs = pstmt.executeQuery();
			rs.next();
			resp.version = rs.getInt(1);
			pstmt.close();
			
			for(ChangeSet cs : changes) {			
				
				// Process each change set separately and roll back to a save point if it fails
				Savepoint sp = connectionSD.setSavepoint();
				try {
					
					log.info("SurveyManager, applyChanges. Change set type: " + cs.changeType);
					if(cs.changeType.equals("label")) {
						
						applyLabel(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version);

					} else if(cs.changeType.equals("option") && cs.source != null && cs.source.equals("file")) {
						
						// Apply changes to options loaded from a csv file
						applyOptionUpdates(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.source);
						
					} else if(cs.changeType.equals("property") && !cs.type.equals("option")) {
						
						// Update a property
						applyQuestionProperty(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType);
						
					} else if(cs.changeType.equals("question")) {
						
						// Add/delete/move questions
						applyQuestion(connectionSD, cResults, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action);
						
					} else if(cs.changeType.equals("option") || (cs.changeType.equals("property") && cs.type.equals("option"))) {
						
						// Add/delete options changed by the editor
						applyOptionFromEditor(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action);
						
					} else if(cs.changeType.equals("optionlist")) {
						
						// Add/delete/move questions
						applyOptionList(connectionSD, pstmtChangeLog, cs.items, sId, userId, resp.version, cs.changeType, cs.action);
						
					} else {
						log.info("Error: unknown changeset type: " + cs.changeType);
						throw new Exception("Error: unknown changeset type: " + cs.changeType);
					}
									
					// Success
					cs.updateFailed = false;
					resp.success++;
				} catch (Exception e) {
					
					// Failure
					connectionSD.rollback(sp);
					log.info("Error: " + e.getMessage());
					cs.updateFailed = true;
					cs.errorMsg = e.getMessage();
					resp.failed++;
				}
				
			}
			
			if(resp.success > 0) {
				connectionSD.commit();
				log.info("Survey update to version: " + resp.version + ". " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			} else {
				connectionSD.rollback();
				log.info("Survey version not updated: " + 
						resp.success + " successful changes and " + 
						resp.failed + " failed changes");
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			connectionSD.setAutoCommit(true);
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
			int version) throws Exception {
		
		String transType = null;
		PreparedStatement pstmtLangOldVal = null;
		PreparedStatement pstmtLangNew = null;
		PreparedStatement pstmtNewQuestionLabel = null;
		PreparedStatement pstmtNewQuestionHint = null;
		PreparedStatement pstmtDeleteLabel = null;
		PreparedStatement pstmtGetOptionTextId = null;
		
		try {
			
			// Get the text id for an option update
			String sqlGetOptionTextId = "select label_id from option where l_id = ? and ovalue = ?; ";
			pstmtGetOptionTextId = connectionSD.prepareStatement(sqlGetOptionTextId);
			
			// Create prepared statements, one for the case where an existing value is being updated
			String sqlLangOldVal = "update translation set value = ? " +
					"where s_id = ? and language = ? and text_id = ? and type = ? and value = ?;";
			pstmtLangOldVal = connectionSD.prepareStatement(sqlLangOldVal);
		
			String sqlLangNew = "insert into translation (value, s_id, language, text_id, type) values(?,?,?,?,?);";
			pstmtLangNew = connectionSD.prepareStatement(sqlLangNew);
			
			String sqlNewQLabel = "update question set qtext_id = ? where q_id = ?; ";
			pstmtNewQuestionLabel = connectionSD.prepareStatement(sqlNewQLabel);
			
			String sqlNewQHint = "update question set infotext_id = ? where q_id = ?; ";
			pstmtNewQuestionHint = connectionSD.prepareStatement(sqlNewQHint);
			
			String sqlDeleteLabel = "delete from translation where s_id = ? and text_id = ? and type = ?;";
			pstmtDeleteLabel = connectionSD.prepareStatement(sqlDeleteLabel);
			
			 // Get the languages
			List<Language> lang = GeneralUtilityMethods.getLanguages(connectionSD, sId);
			
			for(ChangeItem ci : changeItemList) {
			
				boolean isQuestion = ci.property.type.equals("question");
				String text_id = null;
				if(!isQuestion) {
					// Get the text id for an option
					// Don't rely on the key as the text id may have been changed by a name change
					int listId = GeneralUtilityMethods.getListId(connectionSD, sId, ci.property.optionList);
					pstmtGetOptionTextId.setInt(1, listId);
					pstmtGetOptionTextId.setString(2, ci.property.name);
					
					log.info("Getting text id for option: " + pstmtGetOptionTextId.toString());
					ResultSet rs = pstmtGetOptionTextId.executeQuery();
					if(rs.next()) {
						text_id = rs.getString(1);
					}
					
				} else {
					text_id = ci.property.key;		// For question we can rely on the key?
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
							log.info("Update question table with text_id: " + pstmtNewQuestionLabel.toString());
							pstmtNewQuestionLabel.executeUpdate();
						}
						
					} else if(ci.property.propType.equals("hint")) {
						addLabel(connectionSD, ci, ci.property.languageName, pstmtLangNew, sId, pstmtDeleteLabel, text_id);

						// Add the new text id to the question
						if(isQuestion) {
							pstmtNewQuestionHint.setString(1, ci.property.key);
							pstmtNewQuestionHint.setInt(2, ci.property.qId);
							log.info("Update question table with hint_id: " + pstmtNewQuestionHint.toString());
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
				pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
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
			try {if (pstmtNewQuestionHint != null) {pstmtNewQuestionHint.close();}} catch (SQLException e) {}
			try {if (pstmtDeleteLabel != null) {pstmtDeleteLabel.close();}} catch (SQLException e) {}
			try {if (pstmtGetOptionTextId != null) {pstmtGetOptionTextId.close();}} catch (SQLException e) {}
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
		
		pstmtLangOldVal.setString(1, GeneralUtilityMethods.convertAllxlsNames(ci.property.newVal, sId, sd, true));
		pstmtLangOldVal.setInt(2, sId);
		pstmtLangOldVal.setString(3, language);
		pstmtLangOldVal.setString(4, text_id);
		if(ci.property.propType.equals("text") || ci.property.propType.equals("hint")) {
			transType = "none";
		} else {
			transType = ci.property.propType;
		}
		pstmtLangOldVal.setString(5,  transType);
		pstmtLangOldVal.setString(6, GeneralUtilityMethods.convertAllxlsNames(ci.property.oldVal, sId, sd, true));

		log.info("Update question translation: " + pstmtLangOldVal.toString());
		
		int count = pstmtLangOldVal.executeUpdate();
		if(count == 0) {
			String msg = "Warning: label not updated to " + ci.property.newVal
					+ " for language " + language 
					+ ". It may have already been updated by someone else.";
			log.info(msg);
			throw new Exception(msg);		// No matching value assume it has already been modified
		}
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
			
			pstmtLangNew.setString(1, GeneralUtilityMethods.convertAllxlsNames(ci.property.newVal, sId, sd, true));
			pstmtLangNew.setInt(2, sId);
			pstmtLangNew.setString(3, language);
			pstmtLangNew.setString(4, text_id);
			if(ci.property.propType.equals("text") || ci.property.propType.equals("hint")) {
				transType = "none";
			} else {
				transType = ci.property.propType;
			}
			pstmtLangNew.setString(5,  transType);
			
			log.info("Insert new question label: " + pstmtLangNew.toString());
			
			pstmtLangNew.executeUpdate();
		} else {
			// Only media labels can have new val set to null and hence be deleted hence delete for all languages
			pstmtDeleteLabel.setInt(1, sId);
			pstmtDeleteLabel.setString(2, ci.property.key);
			pstmtDeleteLabel.setString(3, ci.property.propType);
			log.info("Delete media label: " + pstmtDeleteLabel.toString());
			pstmtDeleteLabel.executeUpdate();
			ci.property.key = null;		// Clear the key in the question table
		}  
		
	}
	
	/*
	 * Apply changes to an option from an external file
	 *  1) Get the maximum sequence number for each question
	 *  2) Attempt to get the text_id for the passed in option
	 *     3a) If the text_id can't be found create a new option
	 *     3b) Else update the label value for the option 
	 */
	public void applyOptionUpdates(Connection connectionSD,
			PreparedStatement pstmtChangeLog, 
			ArrayList<ChangeItem> changeItemList, 
			int sId, 
			int userId,
			int version,
			String changeType,
			String source) throws Exception {
		
		PreparedStatement pstmtLangInsert = null;
		PreparedStatement pstmtLangUpdate = null;
		PreparedStatement pstmtOptionGet = null;
		PreparedStatement pstmtOptionInsert = null;
		PreparedStatement pstmtMaxSeq = null;
		
		try {
			
			// Create prepared statements
			String sqlOptionGet = "select o.label_id from option o "
					+ "where o.l_id = ? "
					+ "and o.ovalue = ?;";
			pstmtOptionGet = connectionSD.prepareStatement(sqlOptionGet);
			
			String sqlOptionInsert = "insert into option  (o_id, l_id, seq, label_id, ovalue, externalfile, column_name) "
					+ "values(nextval('o_seq'), ?, ?, ?, ?, 'true', ?);"; 			
			pstmtOptionInsert = connectionSD.prepareStatement(sqlOptionInsert);
			
			String sqlLangInsert = "insert into translation  (t_id, s_id, language, text_id, type, value) values(nextval('t_seq'), ?, ?, ?, ?, ?);"; 			
			pstmtLangInsert = connectionSD.prepareStatement(sqlLangInsert);
			
			String sqlLangUpdate = "update translation  set value = ? where s_id = ? and text_id = ? and value != ?;"; 			
			pstmtLangUpdate = connectionSD.prepareStatement(sqlLangUpdate);		// Assumes all languages and types TODO
			
			String sqlMaxSeq = "select max(seq) from option where l_id = ?;";
			pstmtMaxSeq = connectionSD.prepareStatement(sqlMaxSeq);
		
			ArrayList<String> languages = GeneralUtilityMethods.getLanguagesUsedInSurvey(connectionSD, sId);
			int maxSeq = -1;
			ResultSet rs = null;
			int totalCount = 0;		// Total changes for this change set
			
			for(ChangeItem ci : changeItemList) {
				
				int count = 0;		// Count of changes for this change item
			
				// Get current maximum sequence
				pstmtMaxSeq.setInt(1, ci.option.l_id);
				rs = pstmtMaxSeq.executeQuery();
				if(rs.next()) {
					maxSeq = rs.getInt(1);
				}			
				
				// Get the text_id for this option
				pstmtOptionGet.setInt(1, ci.option.l_id);
				pstmtOptionGet.setString(2, ci.option.value);
				
				log.info("Get text_id for option: " + pstmtOptionGet.toString());
				rs = pstmtOptionGet.executeQuery();
				if(rs.next()) {
					
					String text_id = rs.getString(1);
					
					pstmtLangUpdate.setString(1, ci.option.externalLabel);
					pstmtLangUpdate.setInt(2, sId);
					pstmtLangUpdate.setString(3, text_id);
					pstmtLangUpdate.setString(4, ci.option.value);
					log.info("Update existing option label: " + pstmtLangUpdate.toString());
					count = pstmtLangUpdate.executeUpdate();
					
				} else {
					
					// Create a new option
					
					// Set text id
					maxSeq++;
					String text_id = "external_" + ci.option.l_id + "_" + maxSeq;
					// Insert new option		
					pstmtOptionInsert.setInt(1, ci.option.l_id);
					pstmtOptionInsert.setInt(2, maxSeq);
					pstmtOptionInsert.setString(3, text_id);
					pstmtOptionInsert.setString(4, ci.option.value);
					pstmtOptionInsert.setString(5, GeneralUtilityMethods.cleanName(ci.option.value, false) );
					
					log.info("===================== Insert new option from file: " + pstmtOptionInsert.toString());
					count = pstmtOptionInsert.executeUpdate();
					
					// Set label
					pstmtLangInsert.setInt(1, sId);
					pstmtLangInsert.setString(3, text_id);
					pstmtLangInsert.setString(4, "none");
					pstmtLangInsert.setString(5, ci.option.externalLabel);
					for(String language : languages) {
						pstmtLangInsert.setString(2, language);
						log.info("----------------------------- Insert new translation for option from file: " + pstmtLangInsert.toString());
						count += pstmtLangInsert.executeUpdate();
					}	
					
				}			
				
				// Write the change log
				if(count > 0) {
					ci.changeType = "option";
					ci.source = source;
					Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
					pstmtChangeLog.setInt(1, sId);
					pstmtChangeLog.setInt(2, version);
					pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, "external option")));
					pstmtChangeLog.setInt(4,userId);
					pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
					pstmtChangeLog.execute();

				}
				totalCount += count;
			}
			
			if(totalCount == 0) {
				log.info("Info: No changes applied");
				//throw new Exception("No changes applied");		
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLangInsert != null) {pstmtLangInsert.close();}} catch (SQLException e) {}
			try {if (pstmtLangUpdate != null) {pstmtLangUpdate.close();}} catch (SQLException e) {}
			try {if (pstmtOptionInsert != null) {pstmtOptionInsert.close();}} catch (SQLException e) {}
			try {if (pstmtOptionGet != null) {pstmtOptionGet.close();}} catch (SQLException e) {}
			try {if (pstmtMaxSeq != null) {pstmtMaxSeq.close();}} catch (SQLException e) {}
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
			String type) throws Exception {

		boolean setReadonly = false;
		boolean onlyIfNotPublished = false;
		
		PreparedStatement pstmtProperty1 = null;
		PreparedStatement pstmtProperty2 = null;
		PreparedStatement pstmtProperty3 = null;
		PreparedStatement pstmtDependent = null;
		PreparedStatement pstmtReadonly = null;
		PreparedStatement pstmtGetQuestionId = null;
		PreparedStatement pstmtGetQuestionDetails = null;
		PreparedStatement pstmtGetListId = null;
		PreparedStatement pstmtListname = null;
		PreparedStatement pstmtUpdateRepeat = null;
		PreparedStatement pstmtUpdatePath = null;
		PreparedStatement pstmtUpdateForm = null;
		PreparedStatement pstmtUpdateColumnName = null;
		PreparedStatement pstmtUpdateLabelRef = null;
		PreparedStatement pstmtUpdateHintRef = null;
		PreparedStatement pstmtUpdateTranslations = null;
		
		try {
		
			for(ChangeItem ci : changeItemList) {
				
				/*
				 * If the question type is a begin repeat and the property is "calculate" then 
				 *  the repeat count attribute of the sub form needs to be updated
				 */
				if(ci.property.qType != null && 
						(ci.property.qType.equals("begin repeat") || ci.property.qType.equals("geopolygon") || ci.property.qType.equals("geolinestring")) && 
						ci.property.prop.equals("calculation")) {
					
					String newVal = GeneralUtilityMethods.convertAllxlsNames(ci.property.newVal, sId, sd, false);
					String oldVal = GeneralUtilityMethods.convertAllxlsNames(ci.property.oldVal, sId, sd, false);
					
					String sqlUpdateRepeat = "update form set repeats = ? "
							+ "where s_id = ? "
							+ "and parentquestion = ? "
							+ "and trim(both from repeats) = ?;";
					pstmtUpdateRepeat = sd.prepareStatement(sqlUpdateRepeat);
					
					pstmtUpdateRepeat.setString(1, newVal);
					pstmtUpdateRepeat.setInt(2, sId);
					pstmtUpdateRepeat.setInt(3, ci.property.qId);
					pstmtUpdateRepeat.setString(4, oldVal);
					
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
					
					// Convert "note" type to a read only string
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
						onlyIfNotPublished = true;
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
					
					// Convert from $ syntax to paths
					if(ci.property.prop.equals("relevant") || ci.property.prop.equals("constraint") 
							|| ci.property.prop.equals("calculation")) {
						ci.property.newVal = GeneralUtilityMethods.convertAllxlsNames(ci.property.newVal, sId, sd, false);
						ci.property.oldVal = GeneralUtilityMethods.convertAllxlsNames(ci.property.oldVal, sId, sd, false);
						
						if(ci.property.oldVal != null && ci.property.oldVal.contains("null")) {
							// The property must have referred to a question that no longer exists - just set to null
							ci.property.oldVal = "_force_update";
						}
					}
					
					if((propertyType = GeneralUtilityMethods.columnType(sd, "question", property)) != null) {
				
						// Create prepared statements, one for the case where an existing value is being updated
						String sqlProperty1 = "update question set " + property + " = ? " +
								"where q_id = ? and (" + property + " = ? " +
								"or " + property + " is null or ? = '_force_update') ";
						
						if(onlyIfNotPublished) {
							sqlProperty1 += " and published = 'false';";
						} else {
							sqlProperty1 += ";";
						}
						pstmtProperty1 = sd.prepareStatement(sqlProperty1);
						
						// One for the case where the property has not been set before
						String sqlProperty2 = "update question set " + property + " = ? " +
								"where q_id = ? and " + property + " is null ";
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
						
						// Update listname
						String sqlListname = "update listname set name = ? where l_id = ? and s_id = ?;";
						pstmtListname = sd.prepareStatement(sqlListname);
						
						// Update for dependent properties
						String sqlDependent = "update question set visible = ?, source = ? " +
								"where q_id = ?;";
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
							pstmtProperty3.setInt(1, Integer.parseInt(ci.property.newVal));
							pstmtProperty3.setInt(2, ci.property.qId);
							
							log.info("Update list name property: " + pstmtProperty3.toString());
							count = pstmtProperty3.executeUpdate();
							
						} else if (ci.property.type.equals("optionlist")) {
							if( ci.property.l_id > 0) {
								pstmtListname.setString(1, ci.property.newVal);
								pstmtListname.setInt(2, ci.property.l_id);
								pstmtListname.setInt(3, sId);
								
								log.info("Update name of list : " + pstmtListname.toString());
								count = pstmtListname.executeUpdate();
							} else {
								count = 1;		// Report as success
								ci.property.newVal = originalNewValue;	// Restore the original new value for logging
							}
							
						} else if(ci.property.oldVal != null && !ci.property.oldVal.equals("NULL")) {
							
							pstmtProperty1.setInt(2, ci.property.qId);
							
							if(propertyType.equals("boolean")) {
								pstmtProperty1.setBoolean(1, Boolean.parseBoolean(ci.property.newVal));
								pstmtProperty1.setBoolean(3, Boolean.parseBoolean(ci.property.oldVal));
								pstmtProperty1.setString(4, ci.property.oldVal);
							} else if(propertyType.equals("integer")) {
								pstmtProperty1.setInt(1, Integer.parseInt(ci.property.newVal));
								pstmtProperty1.setInt(3, Integer.parseInt(ci.property.oldVal));
								pstmtProperty1.setString(4, ci.property.oldVal);
							} else {
								pstmtProperty1.setString(1, ci.property.newVal);
								pstmtProperty1.setString(3, ci.property.oldVal);
								pstmtProperty1.setString(4, ci.property.oldVal);
							}
							log.info("Update existing question property: " + pstmtProperty1.toString());
							count = pstmtProperty1.executeUpdate();
						} else {
							
							pstmtProperty2.setInt(2, ci.property.qId);
							
							if(propertyType.equals("boolean")) {
								pstmtProperty2.setBoolean(1, Boolean.parseBoolean(ci.property.newVal));
							} else if(propertyType.equals("integer")) {
								pstmtProperty2.setInt(1, Integer.parseInt(ci.property.newVal));
							} else {
								pstmtProperty2.setString(1, ci.property.newVal);
							}
	
							log.info("Update question property: " + pstmtProperty2.toString());
							count = pstmtProperty2.executeUpdate();
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
									+ ". It may have already been updated by someone else";
							log.info(msg);
							throw new Exception(msg);		// No matching value assume it has already been modified
						}
							
						// Update the survey manifest if this question references CSV files
						if(ci.property.prop.equals("calculation")) {
							updateSurveyManifest(sd, sId, null, ci.property.newVal);
						} else if(ci.property.prop.equals("appearance")) {
							updateSurveyManifest(sd, sId, ci.property.newVal, null);
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
							String currentPath = null;
							String newPath = null;
							String sqlGetQuestionDetails = "select f_id, path, qtype, qtext_id, infotext_id from question where q_id = ?";
							String sqlUpdateColumnName = "update question set column_name = ? where q_id = ?;";
							String sqlUpdateLabelRef = "update question set qtext_id = ? where q_id = ?;";
							String sqlUpdateHintRef = "update question set infotext_id = ? where q_id = ?;";
							String sqlUpdateTranslations = "update translation set text_id = ? where text_id = ? and s_id = ?";
							

							// 1. Get question details
							pstmtUpdatePath = sd.prepareStatement(sqlGetQuestionDetails);
							pstmtUpdatePath.setInt(1, ci.property.qId);
							ResultSet rsPath = pstmtUpdatePath.executeQuery();

							if(rsPath.next()) {
								fId = rsPath.getInt(1);
								currentPath = rsPath.getString(2);
								qType = rsPath.getString(3);
								qTextId = rsPath.getString(4);
								infoTextId = rsPath.getString(5);
								if(currentPath != null) {
									newPath = currentPath.substring(0, currentPath.lastIndexOf('/')) + "/" + ci.property.newVal;
								}
							}
							
							// 2. Update column name
							pstmtUpdateColumnName = sd.prepareStatement(sqlUpdateColumnName);
							pstmtUpdateColumnName.setString(1, 
									GeneralUtilityMethods.cleanName(ci.property.newVal, true));
							pstmtUpdateColumnName.setInt(2, ci.property.qId);
							pstmtUpdateColumnName.executeUpdate();
							
							// 3. Update reference to label
							pstmtUpdateTranslations = sd.prepareStatement(sqlUpdateTranslations);
							if(qTextId != null) {
								pstmtUpdateLabelRef = sd.prepareStatement(sqlUpdateLabelRef);
								pstmtUpdateLabelRef.setString(1, newPath + ":label");
								pstmtUpdateLabelRef.setInt(2, ci.property.qId);
								pstmtUpdateLabelRef.executeUpdate();		
					
								pstmtUpdateTranslations.setString(1, newPath + ":label");
								pstmtUpdateTranslations.setString(2, currentPath + ":label");
								pstmtUpdateTranslations.setInt(3, sId);
								pstmtUpdateTranslations.executeUpdate();
							}
							
							// 4. Update reference to hint
							if(infoTextId != null) {
								pstmtUpdateHintRef = sd.prepareStatement(sqlUpdateHintRef);
								pstmtUpdateHintRef.setString(1, newPath + ":hint");
								pstmtUpdateHintRef.setInt(2, ci.property.qId);
								pstmtUpdateHintRef.executeUpdate();
								
								pstmtUpdateTranslations.setString(1, newPath + ":hint");
								pstmtUpdateTranslations.setString(2, currentPath + ":hint");
								pstmtUpdateTranslations.setInt(3, sId);
								pstmtUpdateTranslations.executeUpdate();
							}
							
							// 5. If this is a begin group then update the end group
							if(qType != null && qType.equals("begin group")) {
								String sqlUpdateEndGroup = "update question set qname = ? "
										+ "where f_id = ? "
										+ "and qtype = 'end group' " 
										+ "and qname = ?";
								try {if (pstmtUpdatePath != null) {pstmtUpdatePath.close();}} catch (SQLException e) {}
								pstmtUpdatePath = sd.prepareStatement(sqlUpdateEndGroup);
								pstmtUpdatePath.setString(1,  ci.property.newVal + "_groupEnd");
								pstmtUpdatePath.setInt(2,  fId);
								pstmtUpdatePath.setString(3,  ci.property.oldVal + "_groupEnd");
								
								log.info("Updating group end name: " + pstmtUpdatePath.toString());
								pstmtUpdatePath.executeUpdate();
							}
							
							// 6. If this is a begin repeat, geopolygon or geolinestring (that is a form) then update the form specification
							if(qType != null && (qType.equals("begin repeat") || qType.equals("geopolygon") || qType.equals("geolinestring"))){
					
								String sqlUpdateForm = "update form set name = ?, "
										+ "table_name = ?, "
										+ "path = replace(path, ?, ?) "
										+ "where s_id = ? "
										+ "and name = ?;";
								try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
								pstmtUpdateForm = sd.prepareStatement(sqlUpdateForm);
								pstmtUpdateForm.setString(1,  ci.property.newVal);
								pstmtUpdateForm.setString(2,  "s" + sId + "_" + ci.property.newVal);
								pstmtUpdateForm.setString(3,  currentPath);
								pstmtUpdateForm.setString(4,  newPath);
								pstmtUpdateForm.setInt(5,  sId);
								pstmtUpdateForm.setString(6,  ci.property.oldVal);
								
								log.info("Updating form name: " + pstmtUpdateForm.toString());
								pstmtUpdateForm.executeUpdate();
							}
							
							log.info("Current Path to be updated: " + currentPath + " to : " + newPath);
							
							if(currentPath != null && newPath != null) {
								
								// 7. Update all question paths with this path in them
								String sqlUpdateQuestionPaths = "update question "
									+ "set path = replace(path, ?, ?) "
									+ "where q_id in "
									+ "(select q_id from question where f_id in (select f_id from form where s_id = ?));";
								
								try {if (pstmtUpdatePath != null) {pstmtUpdatePath.close();}} catch (SQLException e) {}
								pstmtUpdatePath = sd.prepareStatement(sqlUpdateQuestionPaths);
								pstmtUpdatePath.setString(1,  currentPath);
								pstmtUpdatePath.setString(2,  newPath);
								pstmtUpdatePath.setInt(3, sId);
								
								log.info("Updating question paths: " + pstmtUpdatePath.toString());
								pstmtUpdatePath.executeUpdate();
								

							}
							
						}
						log.info("userevent: " + userId + " : modify survey property : " + property + " to: " + ci.property.newVal + " survey: " + sId);
						
						// Write the change log
						Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
						pstmtChangeLog.setInt(1, sId);
						pstmtChangeLog.setInt(2, version);
						pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, "update")));
						pstmtChangeLog.setInt(4,userId);	
						pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
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
			try {if (pstmtProperty1 != null) {pstmtProperty1.close();}} catch (SQLException e) {}
			try {if (pstmtProperty2 != null) {pstmtProperty2.close();}} catch (SQLException e) {}
			try {if (pstmtProperty3 != null) {pstmtProperty3.close();}} catch (SQLException e) {}
			try {if (pstmtDependent != null) {pstmtDependent.close();}} catch (SQLException e) {}
			try {if (pstmtReadonly != null) {pstmtReadonly.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionId != null) {pstmtGetQuestionId.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestionDetails != null) {pstmtGetQuestionDetails.close();}} catch (SQLException e) {}
			try {if (pstmtGetListId != null) {pstmtGetListId.close();}} catch (SQLException e) {}
			try {if (pstmtListname != null) {pstmtListname.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateRepeat != null) {pstmtUpdateRepeat.close();}} catch (SQLException e) {}
			try {if (pstmtUpdatePath != null) {pstmtUpdatePath.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateForm != null) {pstmtUpdateForm.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateColumnName != null) {pstmtUpdateColumnName.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateLabelRef != null) {pstmtUpdateLabelRef.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateHintRef != null) {pstmtUpdateHintRef.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateTranslations != null) {pstmtUpdateTranslations.close();}} catch (SQLException e) {}
		
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
			String action) throws Exception {
		
		QuestionManager qm = new QuestionManager();
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
				pstmtChangeLog.setInt(4,userId);
				pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
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
			String action) throws Exception {
		
		OptionListManager olm = new OptionListManager();
		 
		try {
		
			for(ChangeItem ci : changeItemList) {

				log.info("userevent: " + userId + " : " + action + " optionlist : " + ci.name + " survey: " + sId);
				
				// Write the change log
				Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				pstmtChangeLog.setInt(1, sId);
				pstmtChangeLog.setInt(2, version);
				pstmtChangeLog.setString(3, gson.toJson(new ChangeElement(ci, action)));
				pstmtChangeLog.setInt(4,userId);
				pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
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
			String action) throws Exception {
		
		QuestionManager qm = new QuestionManager();
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
				pstmtChangeLog.setInt(4,userId);
				pstmtChangeLog.setTimestamp(5, GeneralUtilityMethods.getTimeStamp());
				pstmtChangeLog.execute();
			} 
			
			if(action.equals("add")) {
				qm.saveOptions(connectionSD, sId, options, true);
			} else if(action.equals("delete")) {
				qm.deleteOptions(connectionSD, sId, options, true);
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
    		int id, 
    		int parentId, 
    		Connection cResults,
    		String instanceId,
    		int parentKey,
    		Survey s,
    		boolean generateDummyValues) throws SQLException{
 
    	ArrayList<ArrayList<Result>> output = new ArrayList<ArrayList<Result>> ();
    	
    	/*
    	 * Retrieve the results record from the database (excluding select questions)
    	 *  Select questions are retrieved using a separate query as there are multiple 
    	 *  columns per question
    	 */
    	String sql = null;
    	boolean isTopLevel = false;
    	if(parentKey == 0) {
    		sql = "select prikey, _user ";		// Get user if this is a top level form
    		isTopLevel = true;
    	} else {
    		sql = "select prikey ";
    	}
    	ArrayList<Question> questions = form.questions;
    	PreparedStatement pstmt = null;
    	PreparedStatement pstmtSelect = null;
    	ResultSet resultSet = null;
    	
    	/*
    	 * Hack: Remove questions that have not been published.
    	 * This code should be modified to reuse the function to get columns used by results export
    	 */
    	for(int i = questions.size() - 1; i >= 0; i--) {
    		if(!questions.get(i).published) {
    			questions.remove(i);
    		}
    	}
    	
    	try {
    		
    		/*
    		 * Get the result set of data if an instanceID was passed or if 
    		 * this request is for a child form and real data is required
    		 */
    		if(instanceId != null || parentKey > 0) {
    			String instanceName = null;
		    	for(Question q : questions) {
		    		String col = null;
		    				
		    		if(s.getSubForm(form, q) == null) {
		    			// This question is not a place holder for a subform
		    			if(q.source != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
				    		String qType = q.type;
				    		if(qType.equals("geopoint")) {
				    			col = "ST_AsText(" + q.columnName + ")";
				    		} else if(qType.equals("select")){
				    			continue;	// Select data columns are retrieved separately as there are multiple columns per question
				    		} else {
				    			col = q.columnName;
				    		}
				
				    		// _instanceid is the legacy name for the instanceid column
				    		// instanceid the standard name adopted by odk
				    		if (col.equals("_instanceid") || col.equals("instanceid")) {
				    			instanceName = col;
				    		}
				    		sql += "," + col;
		    			}
		    		}
		
		    	}
		    	sql += " from " + form.tableName;
		    	if(parentId == 0) {
		    		sql += " where " + instanceName + " = ?;";
		    	} else {
		    		sql += " where parkey = ?;";
		    	}
		
		    	pstmt = cResults.prepareStatement(sql);	 
		    	if(instanceId != null) {
		    		pstmt.setString(1, instanceId);;
		    	} else {
		    		pstmt.setInt(1, parentKey);
		    	}
		    	log.info("Retrieving results: " + pstmt.toString());
		    	resultSet = pstmt.executeQuery();
    		}
			
    		if (resultSet != null) {
		    	// For each record returned from the database add the data values to the instance
		    	while(resultSet.next()) {
	    		
		    		ArrayList<Result> record = new ArrayList<Result> ();
	    		
		    		String priKey = resultSet.getString(1);
		    		int newParentKey = resultSet.getInt(1);   		
		    		record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null, null));
		    		
		    		if(isTopLevel) {
		    			String user = resultSet.getString(2);
		    			record.add(new Result("user", "user", user, false, fIdx, -1, 0, null, null));
		    		}
	    		
		    		addDataForQuestions(
		    				cResults,
		    				resultSet, 
		    				record, 
		    				priKey,
		    				newParentKey, 
		    				s, 
		    				form, 
		    				questions, 
		    				fIdx, 
		    				id,
		    				pstmtSelect,
		    				isTopLevel,
		    				generateDummyValues);

		    		output.add(record);
		    	}
	    	} else if(generateDummyValues){
	    		// Add dummy values for a blank form
	    		System.out.println("Adding dummy values for a blank form");
	    		
	    		ArrayList<Result> record = new ArrayList<Result> ();
	    		
	    		String priKey = "";
	    		int newParentKey = 0;
	    		record.add(new Result("prikey", "key", priKey, false, fIdx, -1, 0, null, null)); 
	    		
	    		if(isTopLevel) {
	    			record.add(new Result("user", "user", null, false, fIdx, -1, 0, null, null)); 
	    		}
    		
	    		addDataForQuestions(
	    				cResults,
	    				resultSet, 
	    				record, 
	    				priKey,
	    				newParentKey, 
	    				s, 
	    				form, 
	    				questions, 
	    				fIdx, 
	    				id,
	    				pstmtSelect,
	    				isTopLevel,
	    				generateDummyValues);

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
    		Connection cResults,
    		ResultSet resultSet, 
    		ArrayList<Result> record, 
    		String priKey,
    		int newParentKey,
    		Survey s,
    		Form form,
    		ArrayList<Question> questions,
    		int fIdx,
    		int id,
    		PreparedStatement pstmtSelect,
    		boolean isTopLevel,
    		boolean generateDummyValues) throws SQLException {
		/*
		 * Add data for the remaining questions (prikey and user have already been extracted)
		 */
		int index = 2;
		if(isTopLevel) {
			index = 3;
		}
		
		int qIdx = -1;					// Index into question array for this form
		for(Question q : questions) {
			qIdx++;
			
			String qName = q.name;
			String qType = q.type; 
			String qSource = q.source;
			String listName = q.list_name;
			String appearance = q.appearance;
			
			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
    			Form subForm = s.getSubForm(form, q);
    			
    			if(subForm != null) {	
    				Result nr = new Result(qName, "form", null, false, fIdx, qIdx, 0, null, appearance);

    				nr.subForm = getResults(subForm, 
    						s.getFormIdx(subForm.id),
    			    		subForm.id, 
    			    		id, 
    			    		cResults,
    			    		null,
    			    		newParentKey,
    			    		s,
    			    		generateDummyValues);

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
    			
    		} else if(qType.equals("select")) {		// Get the data from all the option columns
    				
				String sqlSelect = "select ";
				ArrayList<Option> options = new ArrayList<Option>(q.getValidChoices(s));

				boolean hasColumns = false;
				for(Option option : options) {
					if(hasColumns) {
						sqlSelect += ",";
					}
					sqlSelect += q.columnName + "__" + option.columnName; 
					hasColumns = true;
				}
				sqlSelect += " from " + form.tableName + " where prikey=" + priKey + ";";
	
				ResultSet resultSetOptions = null;
				if(resultSet != null) {
					if(pstmtSelect != null) try {pstmtSelect.close();} catch(Exception e) {};
			    	pstmtSelect = cResults.prepareStatement(sqlSelect);	 
			    	
			    	log.info("Get data from option columns: " + pstmtSelect.toString());
			    	resultSetOptions = pstmtSelect.executeQuery();
			    	resultSetOptions.next();		// There will only be one record
				}
	    		
		    	Result nr = new Result(qName, qType, null, false, fIdx, qIdx, 0, listName, appearance);
		    	hasColumns = false;
		    	int oIdx = -1;
		    	for(Option option : options) {
		    		oIdx++;
		    		String opt = q.columnName + "__" + option.columnName;
		    		boolean optSet = false;
		    		if(resultSetOptions != null) {
		    			optSet = resultSetOptions.getBoolean(opt);
		    		}
			    	nr.choices.add(new Result(option.value, "choice", null, optSet, fIdx, qIdx, oIdx, listName, appearance)); 

		    		
				}
		    	record.add(nr);	
		    	
		    	index--;		// Decrement the index as the select multiple was not in the SQL query
			
			} else if(qType.equals("select1")) {		// Get the data from all the option columns
				
				ArrayList<Option> options = new ArrayList<Option>(q.getValidChoices(s));
				Result nr = new Result(qName, qType, null, false, fIdx, qIdx, 0, null, appearance);
				String value = "";
				if(resultSet != null) {
					value = resultSet.getString(index);
				}
				
				int oIdx = -1;
				for(Option option : options) {
					oIdx++;
		    		boolean optSet = option.value.equals(value) ? true : false;	
			    	nr.choices.add(new Result(option.value, "choice", null, optSet, fIdx, qIdx, oIdx, listName, appearance)); 
				}
		    	record.add(nr);	

	    		
			} else if(qSource != null) {

				String value = "";
				if(resultSet != null) {
					value = resultSet.getString(index);
				}
				
				if(value != null && qType.equals("geopoint")) {
					int idx1 = value.indexOf('(');
					int idx2 = value.indexOf(')');
					if(idx1 > 0 && (idx2 > idx1)) {
    					value = value.substring(idx1 + 1, idx2 );
    					// These values are in the order longitude latitude.  This needs to be reversed for the XForm
    					String [] coords = value.split(" ");
    					if(coords.length > 1) {
    						value = coords[1] + " " + coords[0] + " 0 0";
    					}
					} else {
						log.severe("Invalid value for geopoint: " + value);
						value = null;
					}
				} 

        		record.add(new Result(qName, qType, value, false, fIdx, qIdx, 0, null, appearance));

			}
			try {
				//System.out.println("Index: " + index + " : " + q.name + " : " + q.type + " ; " + resultSet.getString(index));
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
		QuestionManager qm = new QuestionManager();
		
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
			
			
		} catch(Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtGet != null) {pstmtGet.close();}} catch (SQLException e) {}
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (SQLException e) {}
		
		}	
		
	}
	
}
