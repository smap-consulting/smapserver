package org.smap.sdal.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.RoleManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/*
 * Survey Class
 * Used for survey editing
 */
public class Survey {
	
	private static Logger log =Logger.getLogger(Survey.class.getName());
	
	public int id;
	public int e_id;
	public int o_id;
	public int p_id;
	public String ident;
	public String displayName;
	public String instanceNameDefn;
	public String def_lang;
	public boolean task_file;				// Set true if this data from a file can be pre-loaded into this survey
	public boolean timing_data;				// Set true if timing data is to be collected for this survey
	public boolean audit_location_data;		// Set true if location is to be recorded for each question
	public boolean track_changes;			// Set true if every change to a question is to be tracked
	public String surveyClass;
	public boolean deleted;
	public boolean blocked;
	public String manifest;
	public boolean hasManifest;
	public ArrayList<Form> forms = new ArrayList<Form> ();
	public HashMap<String, OptionList> optionLists = new HashMap<String, OptionList> ();
	public ArrayList<Language> languages = new ArrayList<Language> (); 
	public ArrayList<ServerSideCalculate> sscList  = new ArrayList<ServerSideCalculate> ();
	public ArrayList<ManifestValue> surveyManifest  = new ArrayList<ManifestValue> ();
	public HashMap<String, Boolean> filters = new HashMap<String, Boolean> ();
	public ArrayList<ChangeLog> changes  = new ArrayList<ChangeLog> ();
	public ArrayList<MetaItem> meta = new ArrayList<> ();
	public HashMap<String, Role> roles = new HashMap<> ();
	public InstanceResults instance = new InstanceResults();	// Data from an instance (a submitted survey)
	public String pdfTemplateName;
	public int managed_id;
	public int version;			// Default to 1
	public boolean loadedFromXLS;
	public ArrayList<Pulldata> pulldata;
	public String hrk;
	public String key_policy;
	//public ArrayList<LinkedSurvey> linkedSurveys = new ArrayList<LinkedSurvey> ();
	public String basedOn;
	public Timestamp created;
	public boolean exclude_empty;
	public String autoUpdates;
	public String projectName;
	private boolean projectTasksOnly;		// deprecated
	private boolean hideOnDevice;		// Replaces projectTasksOnly
	public int groupSurveyId;
	public String groupSurveyDetails;
	public String publicLink;
	
	// Getters
	public int getId() {return id;}; 
	public int getPId() {return p_id;};
	public String getProjectName() {return projectName;}; 
	public String getIdent() {return ident;};
	public String getDisplayName() {return displayName;}; 
	public boolean getDeleted() { return deleted;};
	public boolean getBlocked() { return blocked;};
	public boolean hasManifest() { return hasManifest;};
	public boolean getHideOnDevice() { 
		return hideOnDevice || projectTasksOnly;
	};
	
	public Form getFirstForm() {
		Form form = null;
		
		for(int i = 0; i < forms.size(); i++) {
			Form f = forms.get(i);
			if(f.parentform == 0) {
				form = f;
				break;
			}
		}
		return form;
	}
	
	public Form getSubForm(Form form, Question q) {
		Form subForm = null;

		for(int i = 0; i < forms.size(); i++) {
			Form f = forms.get(i);
			if(f.parentform == form.id && f.parentQuestion == q.id) {
				subForm = f;
				break;
			}
		}
		return subForm;
	}
	
	public Form getSubFormQId(Form form, int qId) {
		Form subForm = null;

		for(int i = 0; i < forms.size(); i++) {
			Form f = forms.get(i);
			if(f.parentform == form.id && f.parentQuestion == qId) {
				subForm = f;
				break;
			}
		}
		return subForm;
	}
	
	public int getFormIdx(int formId) {
		int idx = -1;
		for(int i = 0; i < forms.size(); i++) {
			Form f = forms.get(i);
			if(f.id == formId) {
				idx = i;
				break;
			}
		}
		return idx;
	}
	
	// Get the display name with any HTML reserved characters escaped
	public String getDisplayNameForHTML() {
		return GeneralUtilityMethods.esc(displayName);
	}
	
	// Get a name for the survey instance
	public String getInstanceName() {
		String instanceName = "survey";
		
		ArrayList<Result> results = instance.results.get(0);
		
		for(Result r : results) {
			if(r.name.toLowerCase().equals("instancename")) {	
				if(r.value != null && r.value.trim().length() != 0) {
					instanceName = r.value;		
				}
				break;
			}
		}
		return instanceName;
	}
	
	// Get a name for the survey hrk
	public InstanceMeta getInstanceMeta() {
		InstanceMeta im = new InstanceMeta();
		im.surveyname = displayName;

		if(instance.results.size() > 0) {
			ArrayList<Result> results = instance.results.get(0);
			for(Result r : results) {
				if(r.name.toLowerCase().equals("_hrk")) {	
					if(r.value != null && r.value.trim().length() != 0) {
						im.hrk = r.value;		
					}
				} else if(r.name.toLowerCase().equals("instancename")) {	
					if(r.value != null && r.value.trim().length() != 0) {
						im.instancename = r.value;		
					}
				} else if(r.name.toLowerCase().equals("user")) {	
					if(r.value != null && r.value.trim().length() != 0) {
						im.username = r.value;		
					}
				} else if(r.name.toLowerCase().equals("_device")) {	
					if(r.value != null && r.value.trim().length() != 0) {
						im.device = r.value;		
					}
				}
			}
		}
		return im;
	}
	
	// Setters
	public void setId(int v) { id = v;};
	public void setIdent(String v) { ident = v;};
	public void setDisplayName(String v) { displayName = v;};
	public void setDeleted(boolean v) { deleted = v;};
	public void setBlocked(boolean v) { blocked = v;};
	public void setHasManifest(boolean v) { hasManifest = v;};
	public void setManagedId(int v) { managed_id = v;};
	public void setVersion(int v) { version = v;};
	public void setLoadedFromXLS(boolean v) { loadedFromXLS = v;};
	public void setProjectName(String v) { projectName = v;};
	public void setProjectId(int v) { p_id = v;};
	public void setProjectTasksOnly(boolean v) { projectTasksOnly = v;};		// Deprecated
	public void setHideOnDevice(boolean v) { hideOnDevice = v;};
	
	/*
	 * Write a survey to the database
	 * If this survey is to be attached to a group survey then
	 *   1. Get a list of form names and from ids to be used when available
	 *   2. Forms will only be created if they do not already exist
	 *   2. questions and choices will only be created if they do not already exist in the form
	 */
	public void write(Connection sd, Connection cRel, ResourceBundle localisation, 
			String userIdent, HashMap<String, String> groupForms, int existingSurveyId) throws Exception {
		
		try {
			log.info("Set autocommit false");
			sd.setAutoCommit(false);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			
			writeSurvey(sd, gson);
			GeneralUtilityMethods.setLanguages(sd, id, languages);
			writeLists(sd, gson);
			writeForms(sd, groupForms, existingSurveyId);	
			updateForms(sd);		// Set parent form id and parent question id for forms
			writeRoles(sd, localisation, gson, userIdent);
			
			// If this survey has been added on top of existing tables then mark columns published if they already exist
			GeneralUtilityMethods.setPublished(sd, cRel, id);
			
			// Notify devices
			MessagingManager mm = new MessagingManager();
			mm.surveyChange(sd, id, 0);
			// Update the form dependencies so that when new results are received it is simple to identify the impacted forms			
			GeneralUtilityMethods.updateFormDependencies(sd, id);
			
			sd.commit();
			
		} catch (Exception e) {
			try {sd.rollback();} catch (Exception ex) {}
			throw e;
		} finally {
			try {sd.setAutoCommit(true);} catch (Exception e) {}
		}
	}
	
	/*
	 * Private methods that support writing to the survey to the database
	 * 1. Write the survey definition
	 */
	private void writeSurvey(Connection sd, Gson gson) throws SQLException {
		
		String sql = "insert into survey ("
				+ "s_id, "
				+ "last_updated_time, "
				+ "display_name, "
				+ "p_id, "
				+ "def_lang, "
				+ "class,"
				+ "ident,"
				+ "version,"
				+ "manifest,"
				+ "instance_name,"
				+ "loaded_from_xls,"
				+ "meta,"
				+ "task_file,"
				+ "group_survey_id,"
				+ "hrk,"
				+ "key_policy,"
				+ "created,"
				+ "public_link,"
				+ "pulldata,"
				+ "hide_on_device,"
				+ "timing_data,"
				+ "audit_location_data,"
				+ "track_changes) "
				+ "values (nextval('s_seq'), now(), ?, ?,  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?);";		
		PreparedStatement pstmt = null;
		
		String sqlUpdate = "update survey set "
				+ "ident = ? "
				+ "where s_id = ?;";
		PreparedStatement pstmtUpdate = null;

		try {
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			
			pstmt.setString(1, displayName);		
			pstmt.setInt(2, p_id);				
			pstmt.setString(3, def_lang);
			pstmt.setString(4, surveyClass);	
			pstmt.setString(5, ident);
			pstmt.setInt(6, version);			
			pstmt.setString(7, manifest);
			pstmt.setString(8, instanceNameDefn);
			pstmt.setBoolean(9, loadedFromXLS);
			pstmt.setString(10, gson.toJson(meta));
			pstmt.setBoolean(11, task_file);
			pstmt.setInt(12, groupSurveyId);
			pstmt.setString(13, hrk);	// Key
			pstmt.setString(14, key_policy);
			pstmt.setString(15, publicLink);
			String pd = null;
			if(pulldata != null) {
				pd = gson.toJson(pulldata);
			}
			pstmt.setString(16, pd);
			pstmt.setBoolean(17, hideOnDevice);
			pstmt.setBoolean(18, timing_data);
			pstmt.setBoolean(19, audit_location_data);
			pstmt.setBoolean(20, track_changes);
			pstmt.executeUpdate();
			
			// If an ident was not provided then assign a new ident based on the survey id
			if(ident == null || ident.trim().length() == 0) {
				ResultSet rs = pstmt.getGeneratedKeys();
				if(rs.next()) {
					id = rs.getInt(1);
				
					String surveyName = "s" + p_id + "_" + id;
					ident = surveyName;
					
					pstmtUpdate = sd.prepareStatement(sqlUpdate);
					pstmtUpdate.setString(1, ident);
					pstmtUpdate.setInt(2, id);
					pstmtUpdate.executeUpdate();
				}
			}
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
		}
	}
	
	/*
	 * Write the lists
	 * Then get the list id to be used by the question
	 */
	private void writeLists(Connection sd, Gson gson) throws SQLException {
		
		String sql = "insert into listname (s_id, name) values(?, ?);";
		PreparedStatement pstmt = null;
		
		String sqlOption = "insert into option ("
				+ "o_id, "
				+ "seq, "
				+ "ovalue,"
				+ "cascade_filters, "
				+ "externalfile, "
				+ "column_name, "
				+ "display_name, "
				+ "l_id,"
				+ "published) "
				+ "values (nextval('o_seq'), ?, ?, ?, ?, ?, ?, ?, ?);";
		PreparedStatement pstmtOption = null;
		
		String sqlUpdateOption = "update option set label_id = ? where o_id = ?";
		PreparedStatement pstmtUpdateOption = null;
		
		PreparedStatement pstmtSetLabels = null;
		String sqlSetLabels = "insert into translation (s_id, language, text_id, type, value, external) " +
				"values (?, ?, ?, ?, ?, ?)";
		
		try {
			// Creating the option list
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setInt(1, id);
			
			// Inserting an option
			pstmtOption = sd.prepareStatement(sqlOption, Statement.RETURN_GENERATED_KEYS);
			
			// Setting the label ID
			pstmtUpdateOption = sd.prepareStatement(sqlUpdateOption);
			
			// Setting the labels
			pstmtSetLabels = sd.prepareStatement(sqlSetLabels);
			pstmtSetLabels.setInt(1, id);
			
			for(String listname : optionLists.keySet()) {
				
				OptionList ol = optionLists.get(listname);
				
				// 1. Create the list and get the list id
				pstmt.setString(2, listname);
				pstmt.executeUpdate();				
				ResultSet rs = pstmt.getGeneratedKeys();
				if(rs.next()) {
					ol.id = rs.getInt(1);
				}
				
				// 2. Insert each option with this list id
				int idx = 0;
				for(Option o : ol.options) {
					String transId = null;
					pstmtOption.setInt(1, idx++);
					pstmtOption.setString(2, o.value);
					pstmtOption.setString(3, gson.toJson(o.cascade_filters));
					pstmtOption.setBoolean(4, false);
					pstmtOption.setString(5, o.columnName);
					pstmtOption.setString(6, o.display_name);
					pstmtOption.setInt(7, ol.id);
					pstmtOption.setBoolean(8, o.published);
					pstmtOption.executeUpdate();
					
					
					rs = pstmtOption.getGeneratedKeys();
					if(rs.next()) {
						o.id = rs.getInt(1);
		
						transId = "option_" +  o.id;
						pstmtUpdateOption.setString(1, transId  + ":label");
						pstmtUpdateOption.setInt(2, o.id);
						pstmtUpdateOption.executeUpdate();
						
						// Write the labels
						UtilityMethodsEmail.setLabels(sd, id, transId, o.labels, pstmtSetLabels, o.externalFile);
					}
					
				}
			}
			
			
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
			if(pstmtOption != null) {try {pstmtOption.close();} catch(Exception e) {}}
			if(pstmtUpdateOption != null) {try {pstmtUpdateOption.close();} catch(Exception e) {}}
			if(pstmtSetLabels != null) {try {pstmtSetLabels.close();} catch(Exception e) {}}
		}
	}
	
	/*
	 * 2. Write the forms
	 * This creates an initial entry for a form and then gets the resultant form ID
	 */
	private void writeForms(Connection sd, HashMap<String, String> groupForms, int existingSurveyId) throws Exception {
		
		String sql = "insert into form ("
				+ "f_id, "
				+ "s_id, "
				+ "name, "
				+ "table_name,"
				+ "reference,"
				+ "merge,"
				+ "replace) "
				+ "values (nextval('f_seq'), ?, ?, ?, ?, ?, ?);";
		PreparedStatement pstmt = null;
		
		PreparedStatement pstmtSetLabels = null;
		String sqlSetLabels = "insert into translation (s_id, language, text_id, type, value, external) " +
				"values (?, ?, ?, ?, ?, ?)";
		
		try {
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		
			pstmt.setInt(1, id);		// Survey Id
		
			pstmtSetLabels = sd.prepareStatement(sqlSetLabels);
			pstmtSetLabels.setInt(1, id);
			
			for(Form f : forms) {
				
				String formName = null;
				String cleanName = null;
				if(f.reference) {
					formName = f.referenceName;
				} else {
					formName = f.name;				
				}	
				cleanName = GeneralUtilityMethods.cleanName(formName, true, false, false);
				
				String tableName = null;
				if(groupForms != null) {
					tableName = groupForms.get(formName);
				}
				
				if(tableName == null) {
					tableName = "s" + id + "_" + cleanName;		
				}
				
				pstmt.setString(2, f.name);
				pstmt.setString(3, tableName);
				pstmt.setBoolean(4, f.reference);
				pstmt.setBoolean(5, f.merge);
				pstmt.setBoolean(6, f.replace);
				pstmt.executeUpdate();

				ResultSet rs = pstmt.getGeneratedKeys();
				if(rs.next()) {
					f.id = rs.getInt(1);
				}
				
				// Write Form questions
				int idx = 0;
				for(Question q : f.questions) {
					if(existingSurveyId > 0 && q.type != null && q.type.equals("select")) {
						// If replacing a survey then set the compress flag to the same value as the existing select question
						q.compressed = getExistingCompressedFlag(sd, tableName,existingSurveyId, q.name);
					}
					writeQuestion(sd, q, f.id, idx++, pstmtSetLabels);
				}
				
			}
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
			if(pstmtSetLabels != null) {try {pstmtSetLabels.close();} catch(Exception e) {}}
		}	
	}
	
	/*
	 * If replacing a form we don't want to change an uncompressed select to a compressed select
	 */
	private boolean getExistingCompressedFlag(Connection sd, String tableName, int existingSurveyId, String qName) throws SQLException {
		boolean compressed = true;
		String sql = "select compressed, qtype from question where qName = ? and f_id = "
				+ "(select f_id from form where s_id = ? and table_name = ? and not reference)";
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, qName);
			pstmt.setInt(2,  existingSurveyId);
			pstmt.setString(3,  tableName);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {				
				String qType = rs.getString(2);
				if(qType != null && qType.equals("select")) {
					compressed = rs.getBoolean(1);
				} else {
					compressed = true;		// default to true if we are not updating an existing uncompressed select
				}
			}	
		} finally {
			if(pstmt != null) {try {pstmt.close();}catch(Exception e) {}}
		}
		return compressed;
	}
	
	/*
	 * Update the forms with
	 *  parent form
	 *  parent question
	 */
	private void updateForms(Connection sd) throws SQLException {
		
		String sql = "update form set "
				+ "parentform = ?, "
				+ "parentquestion = ?, "
				+ "repeats = ? "
				+ "where f_id = ?";
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
		
			for(Form f : forms) {
				if(f.parentFormIndex >= 0) {
					Form parentForm = forms.get(f.parentFormIndex);	
					Question parentQuestion = parentForm.questions.get(f.parentQuestionIndex);
					pstmt.setInt(1, parentForm.id);
					pstmt.setInt(2,  parentQuestion.id);
					pstmt.setString(3, parentQuestion.repeatCount);
					pstmt.setInt(4,  f.id);
				
					pstmt.executeUpdate();
				}
			}

		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
		}	
	}

	/*
	 * 2. Write the roles
	 */
	private void writeRoles(Connection sd, ResourceBundle localisation, Gson gson, String userIdent) throws Exception {
		
		String sqlGetRole = "select id from role "
				+ "where o_id = ? "
				+ "and name = ?";
		PreparedStatement pstmtGetRole = null;
		
		String sqlAssociateSurvey = "insert into survey_role (s_id, r_id, column_filter, row_filter, enabled) "
				+ "values (?, ?, ?, ?, 'true')";
		PreparedStatement pstmtAssociateSurvey = null;
		
		try {
			RoleManager rm = new RoleManager(localisation);
			
			pstmtGetRole = sd.prepareStatement(sqlGetRole);		
			pstmtGetRole.setInt(1, o_id);
			
			for(String h : roles.keySet()) {
				Role r = roles.get(h);
				int rId;
			
				pstmtGetRole.setString(2, r.name);
				
				ResultSet rs = pstmtGetRole.executeQuery();
				if(rs.next()) {
					rId = rs.getInt(1);
				} else {
					// Create a new role
					r.desc = localisation.getString("tu_cb");
					r.desc = r.desc.replace("%s1", displayName);
					rId = rm.createRole(sd, r, o_id, userIdent);
				}
				
				// Add the column filter
				if(r.column_filter_ref != null) {
					for(RoleColumnFilterRef ref : r.column_filter_ref) {
						Question q = forms.get(ref.formIndex).questions.get(ref.questionIndex);
						if(q != null) {
							RoleColumnFilter rcf = new RoleColumnFilter(q.id);
							r.column_filter.add(rcf);
						}		
					}
				}
				
				// Sort the column filters in order of increasing id to improve speed of matchint with questions
				if(r.column_filter != null) {
					r.column_filter.sort(null);
				}
				
				// Associate the survey to the roles
				pstmtAssociateSurvey = sd.prepareStatement(sqlAssociateSurvey);
				pstmtAssociateSurvey.setInt(1, id);
				pstmtAssociateSurvey.setInt(2, rId);
				pstmtAssociateSurvey.setString(3, gson.toJson(r.column_filter));
				pstmtAssociateSurvey.setString(4, r.row_filter);	
				
				pstmtAssociateSurvey.executeUpdate();
			
			}
		} finally {
			if(pstmtGetRole != null) {try {pstmtGetRole.close();} catch(Exception e) {}}
			if(pstmtAssociateSurvey != null) {try {pstmtAssociateSurvey.close();} catch(Exception e) {}}
		}	
	}
	
	/*
	 * 3. Write a Question
	 */
	private void writeQuestion(Connection sd, Question q, int f_id, int seq, PreparedStatement pstmtSetLabels) throws Exception {
		
		PreparedStatement pstmt = null;
		String sql = "insert into question ("
				+ "q_id, "
				+ "f_id, "
				+ "seq, "
				+ "qname, "
				+ "qtype, "
				+ "qtext_id, "
				+ "defaultanswer, "
				+ "infotext_id,"
				+ "visible,"
				+ "source,"
				+ "source_param,"
				+ "readonly,"
				+ "mandatory,"
				+ "relevant,"
				+ "calculate,"
				+ "qconstraint,"
				+ "constraint_msg,"
				+ "required_msg,"
				+ "appearance,"
				+ "parameters,"
				+ "nodeset,"
				+ "nodeset_value,"
				+ "nodeset_label,"
				+ "column_name,"
				+ "published,"
				+ "l_id,"
				+ "autoplay,"
				+ "accuracy,"
				+ "dataType,"
				+ "compressed,"
				+ "display_name,"
				+ "intent"
				+ ") "
				+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?"
					+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
					+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
		
		try {
			
			// label reference
			String transId = null;
			String labelId = null;
			for(Label l : q.labels) {
				if(l.hasLabels()) {
					transId = f_id + "_question_" + q.columnName;
					labelId = transId + ":label";
					break;
				}
			}			 
			
			// Hint reference
			String infotextId = null;
			for(Label l : q.labels) {
				if(l.hint != null && !l.hint.isEmpty() || l.guidance_hint != null && !l.guidance_hint.isEmpty()) {
					infotextId = transId + ":hint";
					break;
				}
			}	
			
			// Set list id
			q.l_id = 0;	
			if(q.list_name != null) {
				OptionList ol = optionLists.get(q.list_name);
				if(ol == null) {
					throw new Exception("List name " + q.list_name + " not found");
				}
				q.l_id = ol.id;
			}
			
			// Set name
			String name = q.name;
			if(q.type.equals("end group")) {
				name += "_groupEnd";
				infotextId = null;
				transId = null;
			} 
			
			/*
			 * Write the data
			 */
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setInt(1, f_id);
			pstmt.setInt(2, seq);
			pstmt.setString(3, name);
			pstmt.setString(4, q.type);
			pstmt.setString(5, labelId);					
			pstmt.setString(6, q.defaultanswer);
			pstmt.setString(7, infotextId);
			pstmt.setBoolean(8, q.visible);
			pstmt.setString(9, q.source);
			pstmt.setString(10, q.source_param);
			pstmt.setBoolean(11, q.readonly); 
			pstmt.setBoolean(12, q.required);
			pstmt.setString(13, q.relevant);	
			pstmt.setString(14, q.calculation);
			pstmt.setString(15, q.constraint);
			pstmt.setString(16, q.constraint_msg);
			pstmt.setString(17, q.required_msg);
			pstmt.setString(18, q.appearance);
			pstmt.setString(19, GeneralUtilityMethods.convertParametersToString(q.paramArray));
			
			String nodeset = null;
			String nodeset_value = null;
			String nodeset_label = null;
			String cascade_instance = null;
			
			if(q.type.startsWith("select") || q.type.equals("rank")) {
				cascade_instance = GeneralUtilityMethods.cleanName(q.list_name, true, false, false);
				nodeset = GeneralUtilityMethods.getNodesetFromChoiceFilter(q.choice_filter, cascade_instance);
				nodeset_value = "name";
				nodeset_label = "jr:itext(itextId)";
			}
			
			pstmt.setString(20, nodeset);		
			pstmt.setString(21, nodeset_value);
			pstmt.setString(22, nodeset_label);
			
			pstmt.setString(23,  q.columnName);
			pstmt.setBoolean(24,  false);   				// published		
			pstmt.setInt(25, q.l_id);
			pstmt.setString(26, q.autoplay); 
			pstmt.setString(27, q.accuracy);
			pstmt.setString(28, q.dataType);
			
			if(q.type.equals("select")) {
				pstmt.setBoolean(29, q.compressed);
			} else {
				pstmt.setBoolean(29, false);
			}
			pstmt.setString(30,  q.display_name);
			pstmt.setString(31,  q.intent);

			pstmt.executeUpdate();
			
			ResultSet rs = pstmt.getGeneratedKeys();
			if(rs.next()) {
				q.id = rs.getInt(1);
			}
			
			// Write the labels
			if(transId != null) {
				UtilityMethodsEmail.setLabels(sd, id, transId, q.labels, pstmtSetLabels, false);
			}
			
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
		}
	}
}
