package org.smap.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import org.smap.server.entities.Form;
import org.smap.server.entities.Group;
import org.smap.server.entities.MissingSurveyException;
import org.smap.server.entities.MissingTemplateException;
import org.smap.server.entities.Option;
import org.smap.server.entities.Project;
import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;
import org.smap.server.entities.Translation;
import org.smap.server.managers.FormManager;
import org.smap.server.managers.Groups;
import org.smap.server.managers.OptionManager;
import org.smap.server.managers.PersistenceContext;
import org.smap.server.managers.ProjectManager;
import org.smap.server.managers.QuestionManager;
import org.smap.server.managers.SurveyManager;
import org.smap.server.managers.TranslationManager;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Element;

public class SurveyTemplate {
	//private Connection connection;
	PersistenceContext pc = null;
	FormManager fPersist = null;
	ProjectManager pPersist = null;
	QuestionManager qPersist = null;
	Groups groupPersist = null;
	OptionManager oPersist = null;
	TranslationManager tPersist = null;

	// The model data
	int surveyId;
	private HashMap<String, Question> questions = new HashMap<String, Question>();
	private HashMap<String, Option> options = new HashMap<String, Option>();
	private HashMap<String, Option> cascade_options = new HashMap<String, Option>();
	private ArrayList<CascadeInstance> cascadeInstances = new ArrayList<CascadeInstance> ();
	private HashMap<String, Form> forms = new HashMap<String, Form>();
	private HashMap<String, HashMap<String, HashMap<String, Translation>>> translations = 
		new HashMap<String, HashMap<String, HashMap<String, Translation>>>();
	private Vector<Translation> dummyTranslations = new Vector<Translation>();
	private HashMap<String, String> defaults = new HashMap<String, String>();
	private Survey survey = null;
	private Project project = null;
	private String firstFormRef = null;
	private String firstFormName = null;
	private int nextOptionSeq = 0;
	private int nextQuestionSeq = 0;
	private int MAX_COLUMNS = 1600 - 10;		// Max number of columns in Postgres is 1600, allow for automcatically generated columns
	
	// Manage creation of groups
	//private boolean inGroup = false;

	/*
	 * Constructor
	 */
	public SurveyTemplate() {

		pc = new PersistenceContext("pgsql_jpa");
		pPersist = new ProjectManager(pc);
		fPersist = new FormManager(pc);
		qPersist = new QuestionManager(pc);
		groupPersist = new Groups(pc);
		oPersist = new OptionManager(pc);
		tPersist = new TranslationManager(pc);
	}

	public HashMap<String, HashMap<String, HashMap<String, Translation>>> getTranslations() {
		return translations;
	}
	
	public void resetNextQuestionSeq() {
		nextQuestionSeq = 0;
	}
	
	public int getNextQuestionSeq() {
		nextQuestionSeq++;
		return nextQuestionSeq-1;
	}
	
	public void setDefaultLanguage(String v) {
		survey.setDefLang(v);
	}
	
	public String getDefaultLanguage() {
		if(survey != null) {
			return survey.getDefLang();
		} else {
			return null;
		}
	}
	
	public void setSurveyClass(String v) {
		survey.setSurveyClass(v);
	}
	
	public String getSurveyClass() {
		return survey.getSurveyClass();
	}
	public void setNextOptionSeq(int seq) {
		nextOptionSeq = seq;
	}
	
	public int getNextOptionSeq() {
		return nextOptionSeq;
	}
	
	public boolean hasCascade() {
		if(cascadeInstances.size() == 0) {
			return false;
		} else {
			return true;
		}
	}
	
	// Return the list of cascade options that match the passed in cascade instance id
	public ArrayList <Option> getCascadeOptionList(String cascadeInstance) {
		ArrayList <Option> opts = new ArrayList <Option> ();
		Collection<Option> c = null;
		Iterator<Option> itr = null;

		c = cascade_options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = itr.next();
			if(o.getCascadeInstanceId().equals(cascadeInstance)) {
				opts.add(o);
			}
		}
		return opts;
	}
	
	// Return the list of cascade options that match the passed in cascade instance id
	public ArrayList <CascadeInstance> getCascadeInstances() {
		return cascadeInstances;
	}
	
	/*
	 * Method to create a new unnamed survey
	 * 
	 * @param surveyName the name of the survey
	 */
	public void createSurvey() {
		survey = new Survey();
	}

	/*
	 * Method to get the survey object
	 * 
	 * @return Survey
	 */
	public Survey getSurvey() {
		return survey;
	}
	
	public Project getProject() {
		return project;
	}
	

	/*
	 * Method to create a new form
	 * 
	 * @param formRef the reference for the form to be created
	 * @param formName The name of the form
	 */
	public void createForm(String formRef, String formName) {
		Form f = new Form();
		f.setName(formName);
		f.setPath(formRef);
		forms.put(formRef, f);
	}


	public Group createGroup() {
		Group g = new Group();
		return g;
	}

	/*
	 * Method to get an existing form
	 * 
	 * @param formRef the reference for the form to be retrieved
	 * 
	 * @return Form
	 */
	public Form getForm(String formRef) {
		return forms.get(formRef);
	}
	
	public List <Form> getAllForms() {
		return new ArrayList<Form>(forms.values());
	}
	
    /*
     * Get a sub-form, if it exists, for the designated form and question else return null
     * 
     * @param parentForm the potential parent form
     * @param parentQuestion the potential parent question
     * @return the sub form
     */
    public Form getSubForm(Form parentForm, Question parentQuestion) {
    	Form subForm = null;
    	List <Form> forms = getAllForms();
    	for(Form f : forms) {
    		if(f.getParentForm() == parentForm && f.getParentQuestion() == parentQuestion) {
    			subForm = f;
    			break;   			
    		}
    	}
    	return subForm;
    }
	
	public String getTableName(String formName) {
		String table = null;
		List<Form> formList = new ArrayList<Form>(forms.values());
		for(Form f : formList) {
			if(f.getName().equals(formName)) {
				table = f.getTableName();
				break;
			}		
		}
		return table;
	}

	/*
	 * Method to set the reference for the first form
	 * 
	 * @param formRef the reference for the form to be set
	 */
	public void setFirstFormRef(String formRef) {
		firstFormRef = formRef;
	}
	
	public void setFirstFormName(String formName) {
		firstFormName = formName;
	}

	/*
	 * Method to get the reference for the first form
	 * 
	 * @return String the reference for the form to be set
	 */
	public String getFirstFormRef() {
		return firstFormRef;
	}
	
	public String getFirstFormName() {
		return firstFormName;
	}

	public void addIText(String lCode, String id, String type, String value) {
		
	
		HashMap <String, HashMap<String, Translation>> l = translations.get(lCode);
		
		if (l == null) {	// Create new language
			l = new HashMap <String, HashMap<String, Translation>> ();
			translations.put(lCode, l);
		}
		
		HashMap <String, Translation> types = l.get(id);	// Get the types for this string identifier
		if(types == null) {	// Create a new Hashmap of translation types
			types = new HashMap<String, Translation> ();
			l.put(id, types);
		}
		
		Translation t = new Translation();
		t.setLanguage(lCode);
		t.setTextId(id);
		t.setType(type);
		t.setValue(value);
		types.put(type, t);
	}
	
	public void addDummyTranslation(String id, String value) {
		
		Translation t = new Translation();
		t.setTextId(id);
		t.setValue(value);
		dummyTranslations.add(t);
	}
	
	public void addDefault(String ref, String value) {
		defaults.put(ref, value);
	}
	
	public String getDefault(String ref) {
		return defaults.get(ref);
	}
	
	/*
	 * Method to create a new question
	 * 
	 * @param questionRef the reference for the question to be created
	 * 
	 * @param questionName The name of the question
	 */
	public void createQuestion(String questionRef, String questionName) {
		Question q = new Question();
		q.setName(questionName);
		//q.setReference(questionRef);
		q.setPath(questionRef);
		q.setSeq(-1);	// Default to -1 until actual sequence is known
		questions.put(questionRef, q);
	}

	/*
	 * Method to get an existing question
	 * 
	 * @param questionRef the reference for the question to be retrieved
	 * 
	 * @return Question
	 */
	public Question getQuestion(String questionRef) {
		return questions.get(questionRef);
	}
	
	/*
	 * Method to delete a question
	 * 
	 * @param questionRef the reference for the question to be deleted
	 * 
	 */
	public void removeQuestion(String questionRef) {
		questions.remove(questionRef);
	}

	/*
	 * Method to create a new option
	 * 
	 * @param optionRef the reference for the option to be created
	 * @param label The label of the option
	 */
	public void createOption(String optionRef, String questionRef) {
		Option o = new Option();
		o.setQuestionRef(questionRef);
		options.put(optionRef, o);
	}
	
	/*
	 * Method to create a cascade option
	 *  These options have a different reference than other options and hence
	 *  are stored in a different hashmap, however their attributes are more or less 
	 *  the same as standard options and they are stored in an Option object
	 */
	public void createCascadeOption(String optionRef, String cascadeInstanceId) {
		Option o = new Option();
		o.setCascadeInstanceId(cascadeInstanceId);
		cascade_options.put(optionRef, o);
	}
	
	/*
	 * Create real options for this question by copying the cascade options that match the passed in instance id
	 */
	public void createCascadeOptions(String cascadeInstanceId, String questionRef) {
		Collection<Option> c = null;
		Iterator<Option> itr = null;

		c = cascade_options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = itr.next();
			if(o.getCascadeInstanceId().equals(cascadeInstanceId)) {
				Option oNew = new Option(o);
				oNew.setQuestionRef(questionRef);
				String optionRef = questionRef + "/" + oNew.getSeq();
				options.put(optionRef, oNew);
			}
		}
	}
	
	/*
	 * Set the value on all cascade options that match the passed in instance id and the value key
	 */
	public void setCascadeValue(String questionRef, String valueKey) {
		Collection<Option> c = null;
		Iterator<Option> itr = null;

		// Cascade option has already been copied to options list
		c = options.values();		
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = itr.next();
			if(o.getQuestionRef().equals(questionRef)) {
				HashMap<String, String> kv_pairs = o.getCascadeKeyValues();
				String value = kv_pairs.get(valueKey);
				o.setValue(value);
				kv_pairs.remove(valueKey);	// Remove this kv pair as its been identified as a value
			}
		}
	}
	
	/*
	 * Set the label_id on all cascade options that match the passed in instance id and the label key
	 */
	public void setCascadeLabel(String questionRef, String labelKey) {
		Collection<Option> c = null;
		Iterator<Option> itr = null;

		boolean itext = false;
		if(labelKey.startsWith("jr:itext")); {
			// Text id reference, set the label_id
			itext = true;
			int idx1 = labelKey.indexOf('(');
			int idx2 = labelKey.indexOf(')', idx1 + 1);
			labelKey = labelKey.substring(idx1 + 1, idx2);
		}

		c = options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = itr.next();
			if(o.getQuestionRef().equals(questionRef)) {
				HashMap<String, String> kv_pairs = o.getCascadeKeyValues();
				String label = kv_pairs.get(labelKey);

				if(itext) {
					o.setLabelId(label);
				} else {
					o.setLabel(label);
				}
				kv_pairs.remove(labelKey);	// Remove this kv pair as its been identified as a label
			}
		}
	}


	/*
	 * Method to add a new option
	 * 
	 * @param optionRef the reference for the option to be added
	 * 
	 * @param o the option
	 */
	public void addOption(String optionRef, Option o) {
		options.put(optionRef, o);
	}

	/*
	 * Method to get an existing option
	 * 
	 * @param optoionRef the reference for the question to be retrieved
	 * 
	 * @return Option
	 */
	public Option getOption(String optionRef) {
		return options.get(optionRef);
	}
	
	// Get an option from the cascade option bucket
	public Option getCascadeOption(String optionRef) {
		return cascade_options.get(optionRef);
	}

	/*
	 * Remove any obsolete data from the OSM file
	 */
	public void cleanseOSM() {
		Collection<Question> c = null;
		Iterator<Question> itr = null;

		c = questions.values(); // Cleanse the question objects
		itr = c.iterator();
		while (itr.hasNext()) {
			Question q = itr.next();
			q.cleanseOSM();
		}
	}

	/*
	 * Method to print out the model for debug purposes
	 */
	public void printModel() {

		Collection c = null;
		Iterator itr = null;

		// Survey
		System.out.println("Survey: " + survey.getName());
		System.out.println("     Id: " + survey.getId());

		// Forms
		c = forms.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Form f = (Form) itr.next();
			System.out.println("Form: " + f.getId());
			System.out.println("	Name: " + f.getName());
			if(f.getParentForm() != null) {
				System.out.println("	Parent Form: " + f.getParentForm().getName());
			} else {
				System.out.println("	Parent Form: None");
			}
			System.out.println("	Parent Question: " + f.getParentQuestion());
		}

		// Questions
		c = questions.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Question q = (Question) itr.next();
			System.out.println("Question: " + q.getName());
			System.out.println("	Form: " + q.getFormRef());
			System.out.println("	Type: " + q.getType());
			System.out.println("	ReadOnly: " + q.isReadOnly());
			System.out.println("	Mandatory: " + q.isMandatory());
			System.out.println("	Default: " + q.getDefaultAnswer());
			System.out.println("	QuestionId: " + q.getQTextId());
			System.out.println("	Relevance: " + q.getRelevant());
			System.out.println("	Question Sequence: " + q.getSeq());
		}

		// Options
		c = options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = (Option) itr.next();
			System.out.println("Option: ");
			System.out.println("	Instance: " + o.getCascadeInstanceId());
			System.out.println("	Question: " + o.getQuestionRef());
			System.out.println("	Value: " + o.getValue());
			System.out.println("	Label: " + o.getLabel());
			System.out.println("	Label Id: " + o.getLabelId());
			System.out.println("	Seq: " + o.getSeq());
		}
		
		// Cascade Options
		c = cascade_options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = (Option) itr.next();
			System.out.println("Cascade Option: ");
			System.out.println("	Instance: " + o.getCascadeInstanceId());
			System.out.println("	Question: " + o.getQuestionRef());
			System.out.println("	Value: " + o.getValue());
			System.out.println("	Label: " + o.getLabel());
			System.out.println("	Label Id: " + o.getLabelId());
			System.out.println("	Seq: " + o.getSeq());
		}
	}

	/*
	 * Method to count total questions per form and  table columns per form
	 * Geometry questions are limited to 1 per form
	 * Total table colimns are limited by Postgres to 1,600
	 */
	private class FormDesc {
		int geoms = 0;
		int questions = 0;
		int options = 0;
	}
	
	public ArrayList multipleGeoms() {
		HashMap <String, FormDesc> forms = new HashMap <String, FormDesc> ();
		ArrayList<String> badForms = new ArrayList<String> ();
		
		List<Question> questionList = new ArrayList<Question>(questions.values());
		for (Question q : questionList) {
			Form f = getForm(q.getFormRef());
			String fName = null;
			if(f != null) {
				fName = f.getName();
			} else {
				fName = "_topLevelForm";
			}
			FormDesc fd = forms.get(fName);
			if(fd == null) {
				fd = new FormDesc();
				forms.put(fName, fd);					
			}
			
			String qName = q.getName();
			if(qName.equals("the_geom")) {	
				fd.geoms++;
			}
			
			// Count the questions ignoring select multiple as the number of columns created by a select multiple is
			//  determined by the number of its columns
			String qType = q.getType();
			if(!qType.equals("select") && !qType.equals("end group") && !qType.equals("begin group")) {
				fd.questions++;
			}
			
		}
		
		// Each select multiple creates a column for every options, count these
		List<Option> optionList = new ArrayList<Option>(options.values());
		for (Option o : optionList) {

			Question q = questions.get(o.getQuestionRef());
			Form f = getForm(q.getFormRef());
			String fName = null;
			if(f != null) {
				fName = f.getName();
			} else {
				fName = "_topLevelForm";
			}
			FormDesc fd = forms.get(fName);
			if(fd == null) {
				fd = new FormDesc();
				forms.put(fName, fd);					
			}
			if(q != null) {
				String qType = q.getType();
				if(qType != null && qType.equals("select")) {
					fd.options++;
				}
			} 
			
		}
	
		Iterator itr = forms.keySet().iterator();
		while(itr.hasNext()) {
			String k = itr.next().toString();
			FormDesc fd = forms.get(k);
			if(fd.geoms > 1) {
				badForms.add("Error: Multiple Geometries have been specified in the form:" + k);
			}
			if((fd.questions + fd.options) > MAX_COLUMNS) {
				badForms.add("Error: There are " + fd.questions + " questions and " + fd.options + 
						" multiple choice options in form:" + k +
						". Each of these create a data base column (in total " + (fd.questions + fd.options) + ")" +
						" and there is a maximum of " + MAX_COLUMNS +
						" allowed. You will need to put some questions in a sub form. " +
						"Refer to http://blog.smap.com.au/wp-admin/post.php?post=517&action=edit for details.");
			}

		}
		return badForms;
	}
	
	/*
	 * Method to check for duplicate names in each form
	 */
	public ArrayList <String> duplicateNames() {
		// Hashmap of form names each of which contains a hashmap of question in that form
		HashMap <String, HashMap<String, String>> forms = new HashMap <String, HashMap<String, String>> ();
		ArrayList<String> badNames = new ArrayList<String> ();
		
		List<Question> questionList = new ArrayList<Question>(questions.values());
		for (Question q : questionList) {
			String qName = q.getName();
			String cleanName = UtilityMethods.cleanName(qName);
			Form f = getForm(q.getFormRef());
			String fName = null;
			if(f != null) {
				fName = f.getName();
			} else {
				fName = "_topLevelForm";
			}
			
			HashMap<String, String> questionsInForm = forms.get(fName);
			if(questionsInForm == null) {
				questionsInForm = new HashMap<String, String> ();
				forms.put(fName, questionsInForm);
			}
			String existingQuestion = questionsInForm.get(cleanName);
			if(existingQuestion != null) {
				badNames.add(qName);
				badNames.add(existingQuestion);
				System.out.println("Duplicate Question:" + qName + " in form:" + fName);
			} else {
				questionsInForm.put(cleanName, qName);
			}
			
		}
		
		return badNames;
	}
	
	/*
	 * Method to check for duplicate option values in each question
	 */
	public ArrayList <String> duplicateOptionValues() {
		// Hashmap of question/form references each of which contains a hashmap of options in that question
		HashMap <String, HashMap<String, String>> questionList = new HashMap <String, HashMap<String, String>> ();
		ArrayList<String> badNames = new ArrayList<String> ();
		
		List<Option> optionList = new ArrayList<Option>(options.values());
		for (Option o : optionList) {
			
			String oName = o.getValue();
			String cleanOption = UtilityMethods.cleanName(oName);
			
			HashMap<String, String> cascacde_key_values = o.getCascadeKeyValues();
			if(cascacde_key_values != null) {
				for (String key : cascacde_key_values.keySet()) {
					// Add the filter to the option name, only needs to be unique for a filter combination
					cleanOption += "_" + cascacde_key_values.get(key);
				}
			}
			
			Question q = questions.get(o.getQuestionRef());
			Form f = getForm(q.getFormRef());
			String fName = null;
			if(f != null) {
				fName = f.getName();
			} else {
				fName = "_topLevelForm";
			}
			String qName = null;
			if(q != null) {
				qName = fName + "__" + q.getName();		// Make question unique across forms
				String cleanQuestion = UtilityMethods.cleanName(qName);
				HashMap<String, String> optionsInQuestion = questionList.get(cleanQuestion);
				if(optionsInQuestion == null) {
					optionsInQuestion = new HashMap<String, String> ();
					questionList.put(cleanQuestion, optionsInQuestion);
				}
				String existingOption = optionsInQuestion.get(cleanOption);
				if(existingOption != null) {
					String dupMsg = "Duplicate values:" + oName + " in choices list for question:" + q.getName();
					badNames.add(dupMsg);
					System.out.println(dupMsg);
				} else {
					optionsInQuestion.put(cleanOption, oName);
				}
			} 
			
		}
		
		return badNames;
	}
	
	/*
	 * Method to write the model to the database
	 */
	public void writeDatabase() throws Exception {
		Collection c = null;
		Iterator itr = null;

		System.out.println("Forms values length: " + forms.values().size());
		if(forms.values().size() == 0) {
			System.out.println("No forms in this survey");
			throw new Exception("No forms in this survey");
		}
		SurveyManager surveys = new SurveyManager(pc);
		surveys.persist(survey);

 		HashMap<Form, Question> formQuestionRel = new HashMap<Form, Question>();	// Used to track parent questions 
		List<Form> formList = new ArrayList<Form>(forms.values());
		for(Form f : formList) {
			f.setSurvey(survey);
			f.setQuestions(new ArrayList<Question>());
			
			// null out the parent question reference as the current JPA solution cannot do a cascade write
			if (f.getParentQuestion() != null)
				formQuestionRel.put(f, f.getParentQuestion());
			f.setParentQuestion(null);	
		}
		
		Form [] formArray = formList.toArray(new Form[formList.size()]);
		fPersist.persist(formArray);

		/*
		 * Load questions into database
		 */
		List<Question> questionList = new ArrayList<Question>(questions.values());
		
		/*
		 * Hack
		 * Add four preload questions that arn't currently supported by odkBuild editor
		 * These questions will be added to the top level form as the form_ref is null
		 */
		boolean alreadyHas_device = false;
		boolean alreadyHas_start = false;
		boolean alreadyHas_end = false;
		boolean alreadyHas_instanceid = false;
		boolean alreadyHas_task_key = false;
		for (Question q : questionList) {
			if(q.getSourceParam() != null) {
				if(q.getSourceParam().equals("deviceid")) {
					alreadyHas_device = true;
				} else if(q.getSourceParam().equals("start")) {
					alreadyHas_start = true;
				} else if(q.getSourceParam().equals("end")) {
					alreadyHas_end = true;
				} 
			}

			if(q.getName().equals("_instanceid") ||
					q.getPath().endsWith("/meta/instanceID")) {
					alreadyHas_instanceid = true;
			} else if(q.getName().equals("_task_key")) {
					alreadyHas_task_key = true;
			}

		}
		System.out.println("Has instance:" + alreadyHas_instanceid);
		if(!alreadyHas_device) {
			Question q = new Question();	// Device id
			q.setName("_device");
			q.setSeq(-3);
			q.setVisible(false);
			q.setSource("property");
			q.setSourceParam("deviceid");
			questionList.add(q);
		}
		
		if(!alreadyHas_start) {
			Question q = new Question();	// Start time
			q.setName("_start");
			q.setSeq(-2);
			q.setVisible(false);
			q.setSource("timestamp");
			q.setSourceParam("start");
			q.setType("dateTime");
			questionList.add(q);
		}
		
		if(!alreadyHas_end) {
			Question q = new Question();	// End time
			q.setName("_end");
			q.setSeq(-1);
			q.setVisible(false);
			q.setSource("timestamp");
			q.setSourceParam("end");
			q.setType("dateTime");
			questionList.add(q);
		}
		
		if(!alreadyHas_instanceid) {
			Question q = new Question();	// Instance Id
			q.setName("_instanceid");
			q.setSeq(-1);
			q.setVisible(false);
			q.setSource("user");
			q.setCalculate("concat('uuid:', uuid())");
			q.setReadOnly(true);
			q.setType("string");
			questionList.add(q);
		}
		
		if(!alreadyHas_task_key) {
			Question q = new Question();	// Task Key
			q.setName("_task_key");
			q.setSeq(-1);
			q.setVisible(false);
			q.setSource("tasks");
			q.setType("string");
			questionList.add(q);
		}
		
		/*
		 * Set the sequence number of any questions that have the default sequence "-1" but
		 * need to be placed within a non repeat group
		 */
		for (Question q : questionList) {
			if(q.getSeq() == -1) {
				String ref = q.getPath();
				String group = UtilityMethods.getGroupFromPath(ref);
				for(Question q2 : questionList) {
					
					if(q2.getPath() != null) {
						if(q2.getPath().equals(group)) {
							q.setSeq(q2.getSeq() + 1);

						}
					}
				}
			}
		}
		
		/*
		 * Sort the list
		 * The question sequence number is determined by the order of
		 * Questions in the forms question array list
		 */
		java.util.Collections.sort(questionList, new Comparator<Question>() {
			@Override
			public int compare(Question object1, Question object2) {
				if (object1.getSeq() < object2.getSeq())
					return -1;
				else if (object1.getSeq() == object2.getSeq())
					return 0;
				else
					return 1;
			}
		});

		for (Question q : questionList) {
			Form f = getForm(q.getFormRef());
			if(f == null) {
				
				/*
				 * Try and get the formRef from the question path
				 */
				String qPath = q.getPath();
				if(qPath != null) {
					String formRef = getFormFromQuestionPath(qPath);
					f = getForm(formRef);
				}
				
				if(f == null) {
					// Still no form then allocate this question to the top level form
					f = getForm(firstFormRef);
					q.setPath(f.getPath() + "/" + q.getName()); 
				}
				
				q.setForm(f);


			}

			if(f != null) {
				f.getQuestions().add(q);
				qPersist.persist(q);
			} 			
		}
		
		// Update the forms with the link to parent questions (if any)
		Form relForm = null;
		for (Entry<Form, Question> rel : formQuestionRel.entrySet()) {
			relForm = rel.getKey();
			relForm.setParentQuestion(rel.getValue());
			fPersist.persist(relForm);
		}

		c = options.values(); // Write the option objects
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = (Option) itr.next();
			Question q = getQuestion(o.getQuestionRef()); // Get the question id
			o.setQuestion(q);
			o.setCascadeFilters();	// Set the filter value based on the key value pairs
			oPersist.persist(o);
		}
		
		// Write the translation objects
		Set<String> langs = translations.keySet();
		String[] languages = (String[]) langs.toArray(new String[0]);
		if(languages.length > 0) {
			for(int langIndex = 0; langIndex < languages.length; langIndex++) {
				HashMap<?, HashMap<String, Translation>> aLanguageTranslation = translations.get(languages[langIndex]);	// A single language
				Collection<HashMap<String, Translation>> l = aLanguageTranslation.values();
				
				tPersist.persistBatch(l, survey);
				
				for(int i = 0; i < dummyTranslations.size(); i++) {
					// Need to copy the translation as there will be one Translation object for every language and we don't want to overwrite
					Translation trans = new Translation(dummyTranslations.elementAt(i));
					trans.setSurvey(survey);
					trans.setLanguage(languages[langIndex]);
					trans.setType("none");	// Default dummy translations to a type of "none"
					System.out.println("Dummy Trans: " + trans.getSurvey().getId() + " : " + trans.getLanguage() + " : " + trans.getTextId() + " : " + trans.getType());
					tPersist.persist(trans);
				}
			}
		} else {
			// No languages specified, just create the dummy elements with a language of "default"
			for(int i = 0; i < dummyTranslations.size(); i++) {
				Translation trans = dummyTranslations.elementAt(i);
				trans.setSurvey(survey);
				trans.setLanguage("default");
				trans.setType("none");	// Default dummy translations to a type of "none"
				tPersist.persist(trans);
			}
		}

	}

	/*
	 * Get the form name for the question path by finding the longest form that
	 *  can be the base of the question path
	 *  The path to the question cannot be used as this may include a group
	 */
	private String getFormFromQuestionPath(String qPath) {

		List<String> kList = new ArrayList<String>(forms.keySet());
		String longestPath = null;
		int maxLength = -1;
		for(String formPath : kList) {
			String exPath;
			if(formPath.endsWith("/")) {
				exPath = formPath;
			} else {
				exPath= formPath + "/";    // Make sure path does not match part of a question name
			}
			if(qPath.startsWith(exPath)) {
				int l = formPath.length();
				if(l > maxLength) {
					maxLength = l;
					longestPath = formPath;
				}
			}
		}

		return longestPath;
		
		/*
		String formPath = null;
		int idx = qPath.lastIndexOf('/');
		if(idx > 0) {
			formPath = qPath.substring(0, idx);
		}
		return formPath;
		*/
	}
	/*
	 * Method to read the survey template from the database when passed the key of the survey
	 * 
	 * @param surveyIdent the ident of the survey
	 */
	public void readDatabase(String surveyIdent) throws MissingTemplateException {

		// Locate the survey object
		SurveyManager surveys = new SurveyManager(pc);
		survey = surveys.getByIdent(surveyIdent);

		if(survey != null) {
			readDatabase(survey);	// Get the rest of the survey
		} else {
			System.out.println("Error: Survey Template not found: " + surveyId);
			throw new MissingTemplateException("Error: Survey Template not found: " + surveyId);
		}

	}
	
	/*
	 * Method to read the survey template from the database when passed the survey id
	 * 
	 * @param surveyId the primary key of the survey
	 */
	public void readDatabase(int surveyId) throws MissingTemplateException {

		// Locate the survey object
		SurveyManager surveys = new SurveyManager(pc);
		survey = surveys.getById(surveyId);

		if(survey != null) {
			readDatabase(survey);	// Get the rest of the survey
		} else {
			System.out.println("Error: Survey Template not found: " + surveyId);
			throw new MissingTemplateException("Error: Survey Template not found: " + surveyId);
		}

	}
	
	
	/*
	 * Method to read the remaining survey template from the database once the 
	 *  survey to be read has been identified
	 */
	private void readDatabase(Survey survey) {

		/*
		 * Get the project name
		 */
		project = pPersist.getById(survey.getProjectId());
		
		/*
		 * Get the forms
		 */	
		List <Form> formList = fPersist.getBySurvey(survey);
		System.out.println("Getting forms: " + survey.getId() + " : " + formList.size());
		for(int i= 0; i < formList.size(); i++) {
			String ref = formList.get(i).getPath();
			if(ref != null) {
				forms.put(ref, formList.get(i));
			}
			if(formList.get(i).getParentForm() == null) {
				firstFormRef = ref;
			}
		}
		
		/*
		 * Get questions
		 */
		List <Question> qList = qPersist.getBySurvey(survey);
		for(int i= 0; i < qList.size(); i++) {
			Question q = qList.get(i);
			String formRef = q.getForm().getPath();
			String qRef = q.getPath();
			if(qRef != null) {
				q.setFormRef(formRef);
				questions.put(qRef, q);
				
				boolean cascade = false;
				String cascadeInstanceId = q.getCascadeInstance();
				if(cascadeInstanceId != null) {
					CascadeInstance ci = new CascadeInstance();
					ci.name = cascadeInstanceId;
					ci.valueKey = q.getNodesetValue();
					String label = q.getNodesetLabel();
					if(label.startsWith("jr:itext")) {
						// Text id reference, set the label_id
						int idx1 = label.indexOf('(');
						int idx2 = label.indexOf(')', idx1 + 1);
						ci.labelKey = label.substring(idx1 + 1, idx2);
					} else {
						ci.labelKey = label;
					}

					// Cascade options are shared, check that this instance has not been added already by another question
					
					if(!cascadeInstanceLoaded(cascadeInstanceId)) {
						cascadeInstances.add(ci);
					}
					cascade = true;
				}
		
				/*
				 * Get options for this question
				 */
				List <Option> oList = oPersist.getByQuestionId(q.getId());
				for(int j= 0; j < oList.size(); j++) {
					Option o = oList.get(j);
					o.setQuestionRef(qRef);
					String oRef = qRef + "/" + o.getId();
					if(oRef != null) {
						if(cascade) {
							o.setCascadeInstanceId(cascadeInstanceId);
							// Cascade options are shared, check that this option has not been added already by another question
							if(!cascadeOptionLoaded(cascadeInstanceId, o.getValue())) {
								cascade_options.put(oRef, o);
							}
						} else {
							options.put(oRef, o);
						}
					}
				}
			}
		}	
		
		/*
		 * Get translations
		 */
		List <Translation> tList = tPersist.getBySurvey(survey);
		for(Translation t : tList) {
			HashMap<String, HashMap<String, Translation>> languageMap = translations.get(t.getLanguage());
			if(languageMap == null) {
				languageMap = new HashMap<String, HashMap <String, Translation>> ();
				translations.put(t.getLanguage(), languageMap);
			}
			
			HashMap<String, Translation> types = languageMap.get(t.getTextId());
			if(types == null) {
				types = new HashMap<String, Translation> ();
				languageMap.put(t.getTextId(), types);
			}
			
			types.put(t.getType(), t);			
		}


	}
	
	/*
	 * Method to check to see whether the cascade option has already been loaded
	 */
	public boolean cascadeOptionLoaded(String cascadeInstanceId, String value) {
		boolean loaded = false;
		
		Collection<Option> c = null;
		Iterator<Option> itr = null;
		
		c = cascade_options.values();
		itr = c.iterator();
		while (itr.hasNext()) {
			Option o = itr.next();
			if(o.getCascadeInstanceId().equals(cascadeInstanceId) && o.getValue().equals(value)) {
				loaded = true;
				break;
			}
		}
		return loaded;
	}
	
	/*
	 * Method to check to see whether the cascade instance has already been loaded
	 */
	public boolean cascadeInstanceLoaded(String cascadeInstanceId) {
		boolean loaded = false;
		

		
		for(int i = 0; i < cascadeInstances.size(); i++) {
		
			CascadeInstance ci = cascadeInstances.get(i);
			if(ci.name.equals(cascadeInstanceId)) {
				loaded = true;
				break;
			}
		}

		return loaded;
	}
	
	/*
	 * Method to extend a survey instance with information from the template
	 */
	public void extendInstance(SurveyInstance instance) {
		List<Form> formList  = getAllForms(); 
		
		// Set the display name
		instance.setDisplayName(survey.getDisplayName());
		/*
		 * Extend the forms
		 */
		for(Form f : formList) {
			instance.setForm(f.getPath(), f.getTableName(), f.getType());
			List <Question> questionList = f.getQuestions();
			extendQuestions(instance, questionList, f.getPath());
		}
	}
	
	
	public void extendQuestions(SurveyInstance instance, List <Question> questionList, String formPath) {
		
		for(Question q : questionList) {
			
			String questionPath = q.getPath();
			
			// Set the question type for "begin group" questions
			if(q.getType() != null && q.getType().equals("begin group")) {
				
				instance.setQuestion(questionPath, q.getType(), q.getName());
				
			}
			
			if(q.getSource() != null) {
				// Extend any other questions that have a source (ie not meta data)
				
				instance.setQuestion(questionPath, q.getType(), q.getName());
				
				// Set the overall survey location to the last geopoint type found in the survey				
				if(q.getType().equals("geopoint") || q.getType().equals("geoshape") || q.getType().equals("geotrace")) {
					//instance.setOverallLocation(referencePath);
					instance.setOverallLocation(questionPath);
				}
				
				if(q.getType().equals("select")) {
					// Add the options to this multi choice question
					Collection <Option> optionList = q.getChoices();

					for(Option o : optionList) {
						
						// Create the option (name limited to 63 characters in postgresql)
						// Use the value of the option rather than its label to construct a name
						// This value must be populated for multi select questions

						String qName = q.getName();
						String optionName = getOptionName(o, qName);
																			
						instance.setOption(questionPath, optionName, o.getValue(), o.getSeq());
					}
				}
			}
		}
	}
	
	public static String getOptionName(Option o, String qName) {
		String name = null;
		String value = o.getValue();
		if(value == null || (qName.length() + value.length() > 63)) {		// Max value length 63 chars
			value = String.valueOf(o.getSeq() + 1);		// If the value is too long, use the sequence # (starting from 1)
		}
		name = qName + "__" + value;	// Use double underscore to minimise chance of user specifying this as a question name
		return name;
	}
}