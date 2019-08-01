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

import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.javarosa.xpath.XPathParseTool;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.ApplicationWarning;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.ManifestInfo;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.RoleColumnFilterRef;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.Survey;

public class XLSTemplateUploadManager {

	/*
	 * Globals
	 */
	Workbook wb = null;
	Sheet surveySheet = null;
	Sheet choicesSheet = null;
	Sheet settingsSheet = null;

	int rowNumSurvey = 0;			// Heading row is 0
	int rowNumChoices = 0;		
	int rowNumSettings = 0;		
	int lastRowNumSurvey = 0;
	int lastRowNumChoices = 0;
	int lastRowNumSettings = 0;

	HashMap<String, Integer> surveyHeader = null;
	HashMap<String, Integer> choicesHeader = null;
	HashMap<String, Integer> settingsHeader = null;
	HashMap<String, Integer> choiceFilterHeader = null;
	HashMap<String, Integer> columnRoleHeader = null;
	HashMap<String, Integer> rowRoleHeader = null;
	
	HashMap<String, String> questionNames;	// Mapping between original name and truncated name
	HashMap<String, String> optionNames;		// Mapping between original name and truncated name
	boolean merge;

	HashMap<String, Integer> qNameMap = new HashMap<> ();							// Use in question name validation
	HashMap<String, HashMap<String, Integer>> oNameMap = new HashMap<> ();		// Use in option name validation
	Pattern validQname = Pattern.compile("^[A-Za-z_][A-Za-z0-9_\\-\\.]*$");
	Pattern validChoiceName = Pattern.compile("^[A-Za-z0-9_@\\-\\.\\+%,():/]*$");

	HashMap<Integer, Stack<Question>> groupStackMap = new HashMap<>();			// Keep track of groups in forms
	boolean inFieldList = false;												// Only some questions are allowed inside a field list
	
	boolean useDefaultLanguage = false;
	
	boolean inTableListGroup = false;
	boolean foundSelectInTableListGroup = false;
	boolean justStartedTableListGroup = false;
	
	int metaId = -1000;

	Survey survey = null;

	private class FunctionCheck {
		String name;
		int args;
		String template;
		
		FunctionCheck(String n, int a, String t) {
			name = n;
			args = a;
			template = t;
		}
	}
	private ArrayList<FunctionCheck> functions = new ArrayList<> ();
	private ResourceBundle localisation = null;
	private ArrayList<ApplicationWarning> warnings = null;

	public XLSTemplateUploadManager(ResourceBundle l, ArrayList<ApplicationWarning> w) {
		
		localisation = l;
		warnings = w;
		
		// Initialise Function Check array
		functions.add(new FunctionCheck("count", 1, "count(nodeset)"));
		functions.add(new FunctionCheck("if", 3, "if(condition, a, b)"));
		
	}

	/*
	 * Get a survey definition from an XLS file
	 */
	public Survey getSurvey(
			Connection sd,
			int oId, 
			String type, 
			InputStream inputStream, 
			String displayName,
			int p_id,
			HashMap<String, String> questionNames,
			HashMap<String, String> optionNames,
			boolean merge,
			int existingVersion) throws Exception {

		this.questionNames = questionNames;
		this.optionNames = optionNames;
		this.merge = merge;

		wb = WorkbookFactory.create(inputStream);

		// Create survey and set defaults
		survey = new Survey();
		survey.displayName = displayName;
		survey.o_id = oId;
		survey.p_id = p_id;
		survey.version = merge ? existingVersion + 1 : 1;
		survey.loadedFromXLS = true;
		survey.deleted = false;
		survey.blocked = false;
		survey.meta.add(new MetaItem(metaId--, "string", "instanceID", null, "instanceid", null, false, null, null));
		survey.meta.add(new MetaItem(metaId--, "string", "instanceName", null, "instancename", null, false, null, null));

		surveySheet = wb.getSheet("survey");
		choicesSheet = wb.getSheet("choices");
		settingsSheet = wb.getSheet("settings");

		if(surveySheet == null) {
			throw XLSUtilities.getApplicationException(localisation, "tu_nw", -1, "survey", null, null, null);
		} else if(surveySheet.getPhysicalNumberOfRows() == 0) {
			throw XLSUtilities.getApplicationException(localisation, "tu_ew", -1, "survey", null, null, null);
		} else {

			lastRowNumSurvey = surveySheet.getLastRowNum();
			if(choicesSheet != null) {
				lastRowNumChoices = choicesSheet.getLastRowNum();
			}
			if(settingsSheet != null) {
				lastRowNumSettings = settingsSheet.getLastRowNum();
			}			

			getHeaders();	// get headers and set the languages from them
			
			/*
			 * 1. Process the choices sheet
			 */
			if(choicesSheet != null) {
				while(rowNumChoices <= lastRowNumChoices) {

					Row row = choicesSheet.getRow(rowNumChoices++);

					if(row != null) {
						int lastCellNum = row.getLastCellNum();	
						String listName = XLSUtilities.getTextColumn(row, "list name", choicesHeader, lastCellNum, null);
						if(listName == null) {
							listName = XLSUtilities.getTextColumn(row, "list_name", choicesHeader, lastCellNum, null);
						}
						
						if(listName != null) {
							OptionList ol = survey.optionLists.get(listName);
							if(ol == null) {
								ol = new OptionList();
								survey.optionLists.put(listName, ol);
							}
							ol.options.add(getOption(row, listName));
						}

					}
				}
			}

			/*
			 * 2. Process the survey sheet
			 */
			Form f = getForm("main", -1, -1, null);
			// Validate the top level form
			if(survey.forms.get(0).questions.size() == 0) {
				throw new ApplicationException(localisation.getString("tu_nq"));
			}
			validateForm(1, f);
			
			/*
			 * 3, Process the settings sheet
			 */
			if(settingsSheet != null && settingsHeader != null) {
				Row row = settingsSheet.getRow(rowNumSettings++);
				if(row != null) {
					int lastCellNum = row.getLastCellNum();
					
					// Default language
					survey.def_lang = XLSUtilities.getTextColumn(row, "default_language", settingsHeader, lastCellNum, null);
					if(survey.def_lang != null) {
						boolean validLanguage = false;
						for(Language l : survey.languages) {
							if(l.name.equals(survey.def_lang)) {
								validLanguage = true;
								break;
							}
						}
						if(!validLanguage) {
							throw new ApplicationException(localisation.getString("tu_idl"));
						}
					}
					
					survey.instanceNameDefn = XLSUtilities.getTextColumn(row, "instance_name", settingsHeader, lastCellNum, null);
					survey.surveyClass = XLSUtilities.getTextColumn(row, "style", settingsHeader, lastCellNum, null);
					survey.task_file = getBooleanColumn(row, "allow_import", settingsHeader, lastCellNum);
					survey.setHideOnDevice(getBooleanColumn(row, "hide_on_device", settingsHeader, lastCellNum));
					survey.timing_data = getBooleanColumn(row, "timing_data", settingsHeader, lastCellNum);
					survey.hrk = XLSUtilities.getTextColumn(row, "key", settingsHeader, lastCellNum, null);
					String pdRepeats = XLSUtilities.getTextColumn(row, "pulldata_repeat", settingsHeader, lastCellNum, null);
					if(pdRepeats != null) {
						String [] pdArray = pdRepeats.split(":");
						if(pdArray.length > 0) {
							for(String pd : pdArray) {
								pd = pd.trim();
								int idx = pd.indexOf("(");
								if(idx > 0) {
									String sName = pd.substring(0, idx);
									String key = pd.substring(idx + 1, pd.length() - 1);
									if(survey.pulldata == null) {
										survey.pulldata = new ArrayList<Pulldata> ();
									}
									survey.pulldata.add(new Pulldata(sName, key));
								}
								
							}
						}
					}
					survey.key_policy = XLSUtilities.getTextColumn(row, "key_policy", settingsHeader, lastCellNum, null);

					
					// Add row filters
					if(rowRoleHeader != null && rowRoleHeader.size() > 0) {
						for(String h : rowRoleHeader.keySet()) {
							String filter = XLSUtilities.getTextColumn(row, h, settingsHeader, lastCellNum, null);
							if(filter != null) {
								Role r = survey.roles.get(h);
								if(r != null) {
									SqlFrag sq = new SqlFrag();
									sq.addSqlFragment(filter, false, localisation);
									settingsQuestionInSurvey(sq.humanNames, h);		// validate question names
									r.row_filter = filter;
								}
							}
						}
					}
				}
			}

		}
		
		/*
		 * Add default preloads
		 */
		if(!hasMeta("start")) {
			survey.meta.add(new MetaItem(metaId--, "dateTime", "_start", "start", "_start", "timestamp", true, "start", null));
		}
		if(!hasMeta("end")) {
			survey.meta.add(new MetaItem(metaId--, "dateTime", "_end", "end", "_end", "timestamp", true, "end", null));
		}
		if(!hasMeta("deviceid")) {
			survey.meta.add(new MetaItem(metaId--, "string", "_device", "deviceid", "_device", "property", true, "device", null));
		}
		
		validateSurvey();	// 4. Final Validation

		return survey;

	}
	
	private boolean hasMeta(String sourceParam) {
		for(MetaItem mi : survey.meta) {
			if(mi.isPreload && mi.sourceParam.equals(sourceParam)) {
				return true;
			}
		}
		return false;
	}

	private Option getOption(Row row, String listName) throws ApplicationException, Exception {

		Option o = new Option();
		int lastCellNum = row.getLastCellNum();
		o.optionList = listName;

		o.value = XLSUtilities.getTextColumn(row, "name", choicesHeader, lastCellNum, null);
		o.display_name = XLSUtilities.getTextColumn(row, "display_name", choicesHeader, lastCellNum, null);
		getLabels(row, lastCellNum, choicesHeader, o.labels, "choice");
		
		if(merge) {
			// Attempt to get existing column name
			String n = optionNames.get(listName + "__" + o.value);
			if(n != null) {
				o.columnName = n;
			} else {
				o.columnName = GeneralUtilityMethods.cleanName(o.value, false, false, false);
			}
		} else {
			o.columnName = GeneralUtilityMethods.cleanName(o.value, false, false, false);
		}
		o.cascade_filters = new HashMap<String, String> ();  
		for(String key : choiceFilterHeader.keySet()) {
			String value = XLSUtilities.getTextColumn(row, key, choicesHeader, lastCellNum, null);
			if (value != null) {
				o.cascade_filters.put(key, value);
			}
		}

		o.published = false;		// Default to unpublised TODO work out when this can be set to published
		validateOption(o, rowNumChoices);

		return o;
	}

	/*
	 * Get the survey header and the choices header so we can identify all the languages up front
	 * This should work gracefully with a badly designed forms where there are inconsistent language
	 *  names between the survey and choices sheet
	 */
	private void getHeaders() throws ApplicationException {

		choiceFilterHeader = new HashMap<String, Integer> ();		
		HashMap<String, String> langMap = new HashMap<String, String> ();

		// Get survey sheet headers
		while(rowNumSurvey <= lastRowNumSurvey) {
			Row row = surveySheet.getRow(rowNumSurvey++);
			if(row != null) {
				surveyHeader = XLSUtilities.getHeader(row, localisation, rowNumSurvey, "survey");

				// Add languages in order they exist in the header hence won't use keyset of surveyHeader
				int lastCellNum = row.getLastCellNum();							
				for(int i = 0; i <= lastCellNum; i++) {
					Cell cell = row.getCell(i);
					if(cell != null) {
						String name = cell.getStringCellValue();
						if(name.startsWith("label::")) {	 // Only check the question label for languages, any others will be assumed to be errors
							String [] sArray = name.split("::");
							if(sArray.length > 0) {
								String exists = langMap.get(sArray[1]);
								if(exists == null) {
									langMap.put(sArray[1], sArray[1]);
									survey.languages.add(new Language(0, sArray[1]));
								}
							}
						}
					}
				}
				
				// Get security roles
				for(String h : surveyHeader.keySet()) {
					if(h.startsWith("role::")) {
						if(columnRoleHeader == null) {
							columnRoleHeader = new HashMap<String, Integer> ();
						}
						columnRoleHeader.put(h, surveyHeader.get(h));
						String [] roleA = h.split("::");
						if(roleA.length > 1) {
							survey.roles.put(h, new Role(roleA[1]));
						}
					}
				}

				break;
			}
		}

		// Get choice sheet header
		if(choicesSheet != null) {
			while(rowNumChoices <= lastRowNumChoices) {
				Row row = choicesSheet.getRow(rowNumChoices++);
				if(row != null) {
					choicesHeader = XLSUtilities.getHeader(row, localisation, rowNumChoices, "choices");	
					
					// Get the headers for filters
					for(String h : choicesHeader.keySet()) {
						if(h.equals("list name")
								|| h.equals("name")
								|| h.equals("label")
								|| h.equals("display_name")
								|| h.startsWith("label::") 
								|| h.equals("image")
								|| h.startsWith("image::")  		// deprecate?
								|| h.startsWith("media::image") 
								|| h.equals("audio")
								|| h.startsWith("audio::") 		// deprecate?
								|| h.startsWith("media::audio") 
								|| h.equals("video") 
								|| h.startsWith("media::video")
								|| h.startsWith("video::")) { 	// deprecate?
							continue;
						}
						// The rest must be filter columns
						choiceFilterHeader.put(h, choicesHeader.get(h));
					}
				
					break;
				}
			}
		}

		// Add a default language if needed
		if(survey.languages.size() == 0) {
			survey.languages.add(new Language(0, "language"));
			useDefaultLanguage = true;
		}
		
		// Get Setting sheet headers
		if(settingsSheet != null) {
			while(rowNumSettings <= lastRowNumSettings) {
				Row row = settingsSheet.getRow(rowNumSettings++);
				if(row != null) {
					settingsHeader = XLSUtilities.getHeader(row, localisation, rowNumSettings, "settings");
					break;
				}
			}
			
			// Add security roles
			if(settingsHeader != null) {
				for(String h : settingsHeader.keySet()) {
					if(h.startsWith("role::")) {
						if(rowRoleHeader == null) {
							rowRoleHeader = new HashMap<String, Integer> ();
						}
						rowRoleHeader.put(h, settingsHeader.get(h));
						String [] roleA = h.split("::");
						if(roleA.length > 1) {
							survey.roles.put(h, new Role(roleA[1]));
						}
					}
				}
			}
		}

	}

	/*
	 * Process the question rows to create a form
	 */
	private Form getForm(String name, int parentFormIndex, int parentQuestionIndex, ArrayList<KeyValueSimp>  parameters) throws Exception {

		Form f = new Form(name, parentFormIndex, parentQuestionIndex);
		setFormReference(parameters, f);
		setFormMerge(parameters, f);
		survey.forms.add(f);
		
		int thisFormIndex = survey.forms.size() - 1;

		while(rowNumSurvey <= lastRowNumSurvey) {

			Row row = surveySheet.getRow(rowNumSurvey++);

			if(row != null) {
				Question q = getQuestion(row, thisFormIndex, f.questions.size());				
				if(q != null) {
					MetaItem item = GeneralUtilityMethods.getPreloadItem(q.type, q.name, q.display_name, metaId, q.appearance);
					if(item != null) {
						metaId--;
						validateQuestion(q, rowNumSurvey, thisFormIndex);
						survey.meta.add(item);
					} else {
						if(q.type.equals("end repeat")) {
							if(parentFormIndex < 0) {
								throw XLSUtilities.getApplicationException(localisation, "tu_eer", rowNumSurvey, "survey", null, null, null);
							}
							return f; 
						}
							
						// Update the survey manifest if csv files are referenced from the appearance and/or the calculation
						ManifestInfo mi = GeneralUtilityMethods.addManifestFromAppearance(q.appearance, survey.manifest);
						mi = GeneralUtilityMethods.addManifestFromCalculate(q.calculation, mi.manifest);
						survey.manifest = mi.manifest;
						
						validateQuestion(q, rowNumSurvey, thisFormIndex);
						f.questions.add(q);
						
						if(q.type.equals("begin repeat")) {
							int repeatRowNumber = rowNumSurvey;
							Form subForm = getForm(q.name, thisFormIndex, f.questions.size() - 1, q.paramArray);
							validateForm(repeatRowNumber, subForm);
						}
						
					}
				}
						
			}
			
		}
		return f;

	}

	/*
	 * Get a question from the excel sheet
	 */
	private Question getQuestion(Row row, int formIndex, int questionIndex) throws ApplicationException, Exception {

		Question q = new Question();
		int lastCellNum = row.getLastCellNum();

		// 1. Question type
		String type = XLSUtilities.getTextColumn(row, "type", surveyHeader, lastCellNum, null);	
		
		// 2. Question name
		q.name = XLSUtilities.getTextColumn(row, "name", surveyHeader, lastCellNum, null);  
		
		// Check type is not null
		if(type == null && q.name != null ) {
			throw XLSUtilities.getApplicationException(localisation, "tu_mt", rowNumSurvey, "survey", null, null, null);
		} else if(type == null && q.name == null) {
			return null;		// blank row
		}
		
		q.type = convertType(type, q);			
		if(q.type.equals("geopoint") || q.type.equals("geotrace") || q.type.equals("geoshape")) {
			q.name = "the_geom";
		}	
		
		// 3. Labels
		getLabels(row, lastCellNum, surveyHeader, q.labels, q.type);	
		
		if(merge) {
			String n = questionNames.get(q.name);
			if(n != null) {
				q.columnName = n;
			} else {
				q.columnName = GeneralUtilityMethods.cleanName(q.name, true, true, true);
			}
		} else {
			q.columnName = GeneralUtilityMethods.cleanName(q.name, true, true, true);
		}	

		// display name
		q.display_name = XLSUtilities.getTextColumn(row, "display_name", surveyHeader, lastCellNum, null); 
		
		// 4. choice filter
		q.choice_filter = XLSUtilities.getTextColumn(row, "choice_filter", surveyHeader, lastCellNum, null);
		q.choice_filter = GeneralUtilityMethods.cleanXlsNames(q.choice_filter);
		
		// 5. Constraint
		q.constraint = XLSUtilities.getTextColumn(row, "constraint", surveyHeader, lastCellNum, null);  
		q.constraint = GeneralUtilityMethods.cleanXlsNames(q.constraint);
		
		// 6. Constraint message
		q.constraint_msg = XLSUtilities.getTextColumn(row, "constraint_message", surveyHeader, lastCellNum, null); 
		if(q.constraint_msg == null) {
			q.constraint_msg = XLSUtilities.getTextColumn(row, "constraint-msg", surveyHeader, lastCellNum, null);   // as used by enketo
		}
		
		// 7. Relevant
		q.relevant = XLSUtilities.getTextColumn(row, "relevant", surveyHeader, lastCellNum, null);  
		q.relevant = GeneralUtilityMethods.cleanXlsNames(q.relevant);

		// 7. Repeat count
		if(q.type.equals("begin repeat")) {
			q.repeatCount = XLSUtilities.getTextColumn(row, "repeat_count", surveyHeader, lastCellNum, null);  
		}
		
		// 8. Default
		q.defaultanswer = XLSUtilities.getTextColumn(row, "default", surveyHeader, lastCellNum, null); 
		
		// 9. Readonly
		q.readonly = getBooleanColumn(row, "readonly", surveyHeader, lastCellNum);
		
		// 10. Appearance
		q.appearance = XLSUtilities.getTextColumn(row, "appearance", surveyHeader, lastCellNum, null); 
		q.appearance = GeneralUtilityMethods.cleanXlsNames(q.appearance);
		
		// 11. Parameters
		String paramString = XLSUtilities.getTextColumn(row, "parameters", surveyHeader, lastCellNum, null);
		q.paramArray = GeneralUtilityMethods.convertParametersToArray(paramString);
		
		// 12. autoplay
		q.autoplay = XLSUtilities.getTextColumn(row, "autoplay", surveyHeader, lastCellNum, null);
		
		// 13. body::accuracyThreshold
		q.accuracy = XLSUtilities.getTextColumn(row, "body::accuracyThreshold", surveyHeader, lastCellNum, null);
		
		// 14. Required
		q.required = getBooleanColumn(row, "required", surveyHeader, lastCellNum);		
		
		// 15. Required Message
		q.required_msg = XLSUtilities.getTextColumn(row, "required_message", surveyHeader, lastCellNum, null); 
		
		// 16. Calculation
		q.calculation = XLSUtilities.getTextColumn(row, "calculation", surveyHeader, lastCellNum, null); 
		q.calculation = GeneralUtilityMethods.cleanXlsNames(q.calculation);
		
		// 17. Display Name
		q.display_name = XLSUtilities.getTextColumn(row, "display_name", surveyHeader, lastCellNum, null); 
		
		// 18. Compressed
		if(q.type.equals("select")) {
			q.compressed = true;
		} 
		
		// 19. body::intent
		q.intent = XLSUtilities.getTextColumn(row, "body::intent", surveyHeader, lastCellNum, null);
		
		// Add Column Roles
		if(columnRoleHeader != null && columnRoleHeader.size() > 0) {
			for(String h : columnRoleHeader.keySet()) {
				if(getBooleanColumn(row, h, surveyHeader, lastCellNum)) {
					Role r = survey.roles.get(h);
					if(r != null) {
						if(r.column_filter_ref == null) {
							r.column_filter_ref = new ArrayList<RoleColumnFilterRef> ();
						}
						r.column_filter_ref.add(new RoleColumnFilterRef(formIndex, questionIndex));
					}
				}
			}
		}
			
		/*
		 * Handle Groups
		 */
		if(q.type.equals("begin group")) {
			Stack<Question> groupStack = getGroupStack(formIndex);
			groupStack.push(q);
			if(q.appearance != null && q.appearance.contains("table-list")) {
				inTableListGroup = true;
				foundSelectInTableListGroup = false;
				justStartedTableListGroup = true;
			} else {
				inTableListGroup = false;
			}
		}
		if(q.type.equals("end group")) {
			Stack<Question> groupStack = getGroupStack(formIndex);
			if(groupStack.isEmpty()) { 
				Form f = survey.forms.get(formIndex);
				throw XLSUtilities.getApplicationException(localisation, "tu_eegm", rowNumSurvey, "survey", f.name, null, null);
			}
			
			Question currentGroupQuestion = groupStack.pop();
			
			if(inTableListGroup && !foundSelectInTableListGroup) {
				throw XLSUtilities.getApplicationException(localisation, "tu_need_s", rowNumSurvey, "survey", currentGroupQuestion.name, null, null);
			}
			inTableListGroup = false;
			
			if(q.name != null && q.name.trim().length() > 0 && !q.name.endsWith("_groupEnd")) {  // ignore end groups that end with _groupEnd as they were generated by old xls exports
				// Validate the provided group name against the current group
				if(!q.name.equals(currentGroupQuestion.name)) {
					throw XLSUtilities.getApplicationException(localisation, "tu_eeg", rowNumSurvey, "survey", q.name, currentGroupQuestion.name, null);
				}			
			} else {
				// Set the name of the end group to its group
				q.name = currentGroupQuestion.name;
			}
		}
		
		/*
		 * Validate questions inside table list group
		 */
		if(inTableListGroup) {
			if(!justStartedTableListGroup) {
				if(!q.type.startsWith("select")) {
					throw XLSUtilities.getApplicationException(localisation, "tu_ns", rowNumSurvey, "survey", q.type, null, null);
				} else {
					foundSelectInTableListGroup = true;
				}
			}
			justStartedTableListGroup = false;
		}
		
		/*
		 * Derived Values
		 */
		// 1. Source
		if(q.type.equals("begin group") || q.type.equals("end group") || q.type.equals("begin repeat")) {
			q.source = null;
		} else {
			q.source = "user";
		}
		
		// 2. Visibility
		q.visible = convertVisible(type);

		return q;
	}

	/*
	 * For media try under the default column heading if the language specific is null
	 */
	private void getLabels(Row row, int lastCellNum, HashMap<String, Integer> header, ArrayList<Label> labels, String type) throws ApplicationException, Exception {
		
		// Get the label language values
		String defaultLabel = getDefaultLabel(type);
		if(useDefaultLanguage) {
			Label lab = new Label();
			lab.text = XLSUtilities.getTextColumn(row, "label", header, lastCellNum, defaultLabel);
			lab.hint = XLSUtilities.getTextColumn(row, "hint", header, lastCellNum, null);
			lab.guidance_hint = XLSUtilities.getTextColumn(row, "guidance_hint", header, lastCellNum, null);
			
			lab.image = XLSUtilities.getTextColumn(row, "image", header, lastCellNum, null);
			if(lab.image == null) {
				lab.image = XLSUtilities.getTextColumn(row, "media::image", header, lastCellNum, null);
			}
			lab.video = XLSUtilities.getTextColumn(row, "video", header, lastCellNum, null);
			if(lab.video == null) {
				lab.video = XLSUtilities.getTextColumn(row, "media::video", header, lastCellNum, null);
			}
			lab.audio = XLSUtilities.getTextColumn(row, "audio", header, lastCellNum, null);
			if(lab.audio == null) {
				lab.audio = XLSUtilities.getTextColumn(row, "media::audio", header, lastCellNum, null);
			}
			
			lab.text = GeneralUtilityMethods.cleanXlsNames(lab.text);
			labels.add(lab);
		} else {
			
			// Find out if any language has a hint or label.  If so make sure every language does
			boolean hintSet = false;
			boolean guidanceHintSet = false;
			boolean labelSet = false;
			for(int i = 0; i < survey.languages.size(); i++) {
				String lang = survey.languages.get(i).name;
				if(XLSUtilities.getTextColumn(row, "hint::" + lang, header, lastCellNum, null) != null) {
					hintSet = true;
				}
				if(XLSUtilities.getTextColumn(row, "guidance_hint::" + lang, header, lastCellNum, null) != null) {
					guidanceHintSet = true;
				}
				if(XLSUtilities.getTextColumn(row, "label::" + lang, header, lastCellNum, null) != null) {
					labelSet = true;
				}
				
			}
			for(int i = 0; i < survey.languages.size(); i++) {
				String lang = survey.languages.get(i).name;
				
				Label lab = new Label();
				if(labelSet) {
					lab.text = XLSUtilities.getTextColumn(row, "label::" + lang, header, lastCellNum, "-");
				} else {
					lab.text = XLSUtilities.getTextColumn(row, "label::" + lang, header, lastCellNum, null);
				}
				
				if(hintSet) {
					lab.hint = XLSUtilities.getTextColumn(row, "hint::" + lang, header, lastCellNum, "-");
				} else {
					lab.hint = XLSUtilities.getTextColumn(row, "hint::" + lang, header, lastCellNum, null);
				}
				
				if(guidanceHintSet) {
					lab.guidance_hint = XLSUtilities.getTextColumn(row, "guidance_hint::" + lang, header, lastCellNum, "-");
				} else {
					lab.guidance_hint = XLSUtilities.getTextColumn(row, "guidance_hint::" + lang, header, lastCellNum, null);
				}
				
				// image - try various combination of headers
				lab.image = XLSUtilities.getTextColumn(row, "media::image::" + lang, header, lastCellNum, null);
				if(lab.image == null) {
					lab.image = XLSUtilities.getTextColumn(row, "image::" + lang, header, lastCellNum, null);
				}
				if(lab.image == null) {
					lab.image = XLSUtilities.getTextColumn(row, "media::image", header, lastCellNum, null);
				}
				if(lab.image == null) {
					lab.image = XLSUtilities.getTextColumn(row, "image", header, lastCellNum, null);
				}
				
				// video - try various combination of headers
				lab.video = XLSUtilities.getTextColumn(row, "media::video::" + lang, header, lastCellNum, null);
				if(lab.video == null) {
					lab.video = XLSUtilities.getTextColumn(row, "video::" + lang, header, lastCellNum, null);
				}
				if(lab.video == null) {
					lab.video = XLSUtilities.getTextColumn(row, "media::video", header, lastCellNum, null);
				}
				if(lab.video == null) {
					lab.video = XLSUtilities.getTextColumn(row, "video", header, lastCellNum, null);
				}
				
				// video - try various combination of headers
				lab.audio = XLSUtilities.getTextColumn(row, "media::audio::" + lang, header, lastCellNum, null);
				if(lab.audio == null) {
					lab.audio = XLSUtilities.getTextColumn(row, "audio::" + lang, header, lastCellNum, null);
				}
				if(lab.audio == null) {
					lab.audio = XLSUtilities.getTextColumn(row, "media::audio", header, lastCellNum, null);
				}
				if(lab.audio == null) {
					lab.audio = XLSUtilities.getTextColumn(row, "audio", header, lastCellNum, null);
				}
				
				lab.text = GeneralUtilityMethods.cleanXlsNames(lab.text);
				labels.add(lab);
			}
		}

	}

	private String getDefaultLabel(String type) {
		String def = "-";
		if (type.equals("begin group") || type.equals("end group")
				|| type.equals("begin repeat") || type.equals("end repeat")) {
			def = null;
		}
		return def;
	}
	
	private String convertType(String in, Question q) throws ApplicationException {

		String type = getValidQuestionType(in);		
		
		// Validate and normalise input
		if(type == null) {
			throw XLSUtilities.getApplicationException(localisation, "tu_ut", rowNumSurvey, "survey", in, null, null);
		}

		// Do type conversions
		if (type.equals("text")) {
			type = "string";
		} else if(type.startsWith("select_one") || type.startsWith("select_multiple") || type.startsWith("rank")) {
			
			String [] array = type.split("\\s+");
			if(array.length <= 1) {
				throw XLSUtilities.getApplicationException(localisation, "tu_mln", rowNumSurvey, "survey", in.trim(), null, null);
			}
			q.list_name = array[1].trim();
			if(q.list_name.length() == 0) {
				q.list_name = null;
			}
			
			if(type.startsWith("select_one")) {
				type = "select1";
			} else if(type.startsWith("select_multiple")) {
				type = "select";
			} else if(type.startsWith("rank")) {
				type = "rank";
			}
		} 

		return type;
	}

	private boolean convertVisible(String type) throws Exception {
		boolean visible = true;
		if(type.equals("calculate")) {
			visible = false;
		} else if(type.equals("end group")) {
			visible = false;
		} else if(GeneralUtilityMethods.getPreloadItem(type, "", "", -2000, null) != null) {
			visible = false;
		}

		return visible;
	}

	private void validateForm(int rowNumber, Form f) throws Exception {
		
		if(f.questions.size() == 0) {
			// Form must have at least one question
			throw XLSUtilities.getApplicationException(localisation, "tu_er", rowNumber, "survey", null, null, null);
		} else {
			// Questions must be visible
			boolean hasVisibleQuestion = false;
			for(Question qx : f.questions) {
				if(!qx.type.equals("calculate")) {
					hasVisibleQuestion = true;
					break;
				}
			}
			
			if(!hasVisibleQuestion) {
				ApplicationException e = XLSUtilities.getApplicationException(localisation, "tu_er", rowNumber, "survey", null, null, null);
				warnings.add(new ApplicationWarning(e.getMessage()));
			}
		}
		
		/*
		 * Validate groups
		 */
		for(int i = 0; i < f.questions.size(); i++) {
			Question q = f.questions.get(i);
			if(q.type.equals("begin group")) {
				validateGroup(f.questions, q, i);
			}
		}
	}
	
	private int validateGroup(ArrayList<Question> questions, Question groupQuestion, int start) throws ApplicationException {
		
		Question q;
		String name = groupQuestion.name;
		int i;
		boolean hasVisibleQuestion = false;
		for(i = start + 1; i < questions.size(); i++) {
			q = questions.get(i);
			if(q.type.equals("begin group")) {
				hasVisibleQuestion = true;		// Count another group as a visible question, as long as this embedded group has a visible question then all is good
				validateGroup(questions, q, i);	// recursive validation
			} else if(q.type.equals("end group")) {
				break;
			} else if(!q.type.equals("calculate")) {
				hasVisibleQuestion = true;
			}
		}
		
		if(!hasVisibleQuestion) {
			Integer rowNumber = qNameMap.get(name.toLowerCase());
			ApplicationException e = XLSUtilities.getApplicationException(localisation, "tu_er", rowNumber, "survey", null, null, null);
			warnings.add(new ApplicationWarning(e.getMessage()));
		}
		return i + 1;
	}
	
	private void validateQuestion(Question q, int rowNumber, int formIndex) throws Exception {

		/*
		 * Check Name
		 */
		if (q.name == null || q.name.trim().length() == 0) {
			// Check for a missing name
			throw XLSUtilities.getApplicationException(localisation, "tu_mn", rowNumber, "survey", null, null, null);

		} else if(!validQname.matcher(q.name).matches()) {
			// Check for a valid name
			throw XLSUtilities.getApplicationException(localisation, "tu_qn", rowNumber, "survey", q.name, null, null);

		} else if(!q.type.equals("end group") && qNameMap.get(q.name.toLowerCase()) != null && !q.name.equals("the_geom")) {
			// Check for a duplicate name
			throw XLSUtilities.getApplicationException(localisation, "tu_dq", rowNumber, "survey", q.name, null, null);

		}
		if(!q.type.equals("end group")) {		
			qNameMap.put(q.name.toLowerCase(), rowNumber);
		}
		
		// check relevance
		if(q.relevant != null) {
			ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.relevant);
			if(refs.contains(q.name)) {		// Circular references
				throw XLSUtilities.getApplicationException(localisation, "tu_cr", rowNumber, "survey", "relevant", q.name, null);
			}
			
			checkParentheses(localisation, q.relevant, rowNumber, "survey", "relevant", q.name);
			try {
				XPathParseTool.parseXPath(GeneralUtilityMethods.convertAllxlsNamesToPseudoXPath(q.relevant));
			} catch (Exception e) {
				throw XLSUtilities.getApplicationException(localisation, "tu_jr", rowNumber, "survey", "relevant", e.getMessage(), null);
			}
			testXExprFunctions(q.relevant, localisation, true, rowNumber, "relevant");
		}
		
		// check constraint
		if(q.constraint != null) {
			checkParentheses(localisation, q.constraint, rowNumber, "survey", "constraint", q.name);
			try {
				XPathParseTool.parseXPath(GeneralUtilityMethods.convertAllxlsNamesToPseudoXPath(q.constraint));
			} catch (Exception e) {
				throw XLSUtilities.getApplicationException(localisation, "tu_jr", rowNumber, "survey", "constraint", e.getMessage(), null);
			}
			testXExprFunctions(q.constraint, localisation, true, rowNumber, "constraint");
		}
		
		// check calculate
		if(q.calculation != null) {
			checkParentheses(localisation, q.calculation, rowNumber, "survey", "calculation", q.name);
			try {
				XPathParseTool.parseXPath(GeneralUtilityMethods.convertAllxlsNamesToPseudoXPath(q.calculation));
			} catch (Exception e) {
				throw XLSUtilities.getApplicationException(localisation, "tu_jr", rowNumber, "survey", "calculation", e.getMessage(), null);
			}
			testXExprFunctions(q.calculation, localisation, true, rowNumber, "calculation");
		}
		
		// check appearance
		if(q.appearance != null) {
			checkParentheses(localisation, q.appearance, rowNumber, "survey", "appearance", q.name);
			testXExprFunctions(q.appearance, localisation, true, rowNumber, "appearance");
			
		}
		
		// Check choice filter
		if(q.choice_filter != null) {
			checkParentheses(localisation, q.choice_filter, rowNumber, "survey", "choice_filter", q.name);
			try {
				XPathParseTool.parseXPath(GeneralUtilityMethods.convertAllxlsNamesToPseudoXPath(q.choice_filter));
			} catch (Exception e) {
				throw XLSUtilities.getApplicationException(localisation, "tu_jr", rowNumber, "survey", "choice_filter", e.getMessage(), null);
			}
			testXExprFunctions(q.choice_filter, localisation, true, rowNumber, "choice_filter");
		}
		
		// Check intent
		if(q.intent != null ) {
			boolean valid = false;
			if(q.type.equals("begin group")) {
				if(q.appearance != null && q.appearance.contains("field-list")) {
					valid = true;
				}
			}
			if(!valid) {
				throw XLSUtilities.getApplicationException(localisation, "tu_int", rowNumber, "survey", null, null, null);
			}
		}
		
		// invalid question in field-list
		if(inFieldList) {
			if(q.type.equals("end group")) {
				inFieldList = false;
			} else if(q.type.equals("begin group") || q.type.equals("begin repeat")) {
				Stack<Question> groupStack = getGroupStack(formIndex);
				String groupName = groupStack.pop().name;
				throw XLSUtilities.getApplicationException(localisation, "tu_fl", rowNumber, "survey", q.type, groupName, null);
			}
		} else {
			if(q.type.equals("begin group")) {
				if(q.appearance != null && q.appearance.contains("field-list")) {
					inFieldList = true;
				}
			}
		}
		
		// List name not in choices
		if(q.list_name != null) {
			if(survey.optionLists.get(q.list_name) == null) {
				throw XLSUtilities.getApplicationException(localisation, "tu_lnf", rowNumber, "survey", q.list_name, null, null);
			}
		}
		
		// check parameters
		if(q.paramArray != null) {
			ArrayList<KeyValueSimp> noDups = new ArrayList<KeyValueSimp> ();
			HashMap<String, String> paramHashMap = new HashMap<> ();
			for(KeyValueSimp kv : q.paramArray) {
				String existing = paramHashMap.get(kv.k);
				if(existing == null) {
					noDups.add(kv);
					paramHashMap.put(kv.k, kv.v);
				} else {
					if(existing.equals(kv.v)) {
						// Its a duplicate just discard by not adding to the noDups output
					} else {
						// Conflicting values
						throw XLSUtilities.getApplicationException(localisation, "tu_cf", rowNumber, "survey", kv.k, null, null);
					}
				}
			}
			q.paramArray = noDups;
			
		}
		
		// Check that parent and child forms have the form_identifier parameter
		if(q.type.equals("parent_form") || q.type.equals(SmapQuestionTypes.CHILD_FORM)) {
			boolean hasFormIdentifier = false;
			for(KeyValueSimp kv : q.paramArray) {
				if(kv.k.equals("form_identifier")) {
					hasFormIdentifier = true;
					break;
				}
			}
			if(!hasFormIdentifier) {
				throw XLSUtilities.getApplicationException(localisation, "tu_form_launch", rowNumber, "survey", null, null, null);
			}
		}
		
	}
	
	private void checkParentheses(ResourceBundle localisation, String expression, int rowNumber, String sheet, String column, String name) throws ApplicationException {
		checkMatchedParenthesies(localisation, '{', '}', expression, rowNumber, sheet, column);
		checkMatchedParenthesies(localisation, '(', ')', expression, rowNumber, sheet, column);
	}
	
	private void checkMatchedParenthesies(ResourceBundle localisation, char p1, char p2, String expression, int rowNumber, String sheet, String column) throws ApplicationException {
		int depth = 0; 
		int locn = 0;
		if(expression != null) {
             for(int i = 0; i < expression.length(); i++) {
                 char c = expression.charAt(i);
                 if( c == p1) {
                     depth++;
                     locn = i;
                 } else if( c == p2) {
                     depth--;
                     locn = i;
                 }
                 if(depth < 0) {
                     break;
                 }
             }

             if(depth != 0) {
            	 	if(p1 == '(') {
            	 		throw XLSUtilities.getApplicationException(localisation, "tu_mbs", rowNumber, sheet, column, String.valueOf(locn), null);
            	 	} else if(p1 == '{') {
            	 		throw XLSUtilities.getApplicationException(localisation, "tu_mbc", rowNumber, sheet, column, String.valueOf(locn), null);
            	 	}
                
             }
         }
	}
	
	private void validateSurvey() throws Exception {
		
		// Validate forms and questions
		for(Form f : survey.forms) {
			if(f.reference) {
				Integer rowNumber = qNameMap.get(f.name.toLowerCase());
				if(f.name.equals(f.referenceName)) {
					throw XLSUtilities.getApplicationException(localisation, "tu_ref_self", rowNumber, "survey", f.name, null, null);
				} else {
					boolean validRef = false;
					for(Form refForm : survey.forms) {
						if(refForm.name.equals(f.referenceName)) {
							if(refForm.reference) {
								throw XLSUtilities.getApplicationException(localisation, "tu_ref_ref", rowNumber, "survey", f.name, refForm.name, null);
							} else {
								validRef = true;
								break;
							}
						}
					}
					if(!validRef) {
						throw XLSUtilities.getApplicationException(localisation, "tu_ref_nf", rowNumber, "survey", f.referenceName, f.name, null);
					}
				}
			}
			for(Question q : f.questions) {
				if(q.relevant != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.relevant);
					if(refs.size() > 0) {
						questionInSurvey(refs, "relevant", q);
					}
				}
				if(q.constraint != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.constraint);
					if(refs.size() > 0) {
						questionInSurvey(refs, "constraint", q);
					}
				}
				if(q.repeatCount != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.repeatCount);
					if(refs.size() > 0) {
						questionInSurvey(refs, "repeat_count", q);
					}
					// Make sure there is not a question with a name that will clash with the automatically generated repeat count name
					repeatCountClash(q);
				}
				if(q.choice_filter != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.choice_filter);
					if(refs.size() > 0) {
						questionInSurvey(refs, "choice_filter", q);
					}
				}
				if(q.labels != null) {
					int idx = 0;
					for(Label l : q.labels) {
						ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(l.text);
						if(refs.size() > 0) {
							questionInSurvey(refs, "label::" + survey.languages.get(idx).name, q);
							
							if(refs.contains(q.name)) {		// Check for self reference
								Integer rowNumber = qNameMap.get(q.name.toLowerCase());
								throw XLSUtilities.getApplicationException(localisation, "tu_cr", rowNumber, "survey", 
										"label::" + survey.languages.get(idx).name, q.name, null);
							}
						}
						idx++;
					}
				}
				if(q.calculation != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.calculation);
					if(refs.size() > 0) {
						questionInSurvey(refs, "calculation", q);
					}
				}
				if(q.appearance != null) {
					ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(q.appearance);
					if(refs.size() > 0) {
						questionInSurvey(refs, "appearance", q);
					}
				}
			}
		}
		
		// Validate Settings
		if(survey.instanceNameDefn != null) {
			ArrayList<String> refs = GeneralUtilityMethods.getXlsNames(survey.instanceNameDefn );
			if(refs.size() > 0) {
				settingsQuestionInSurvey(refs, "instance_name");
			}
		}
		
		// Validate groups
		for(Integer formIndex : groupStackMap.keySet()) {
			Stack<Question> groupStack = groupStackMap.get(formIndex);
		
			if(!groupStack.isEmpty()) {
				String groupName = groupStack.pop().name;
				Integer rowNumber = qNameMap.get(groupName.toLowerCase());
				throw XLSUtilities.getApplicationException(localisation, "tu_meg", rowNumber, "survey", groupName, null, null);
			}
		}
	}

	private void validateOption(Option o, int rowNumber) throws ApplicationException {

		HashMap<String, Integer> listMap = oNameMap.get(o.optionList);
		if(listMap == null) {
			listMap = new HashMap<String, Integer> ();
			oNameMap.put(o.optionList, listMap);
		}

		if(o.value == null || o.value.trim().length() == 0) {
			// Check for a missing value
			throw XLSUtilities.getApplicationException(localisation, "tu_vr", rowNumber, "choices", o.optionList, null, null);

		} else if(!validChoiceName.matcher(o.value).matches()) {
			// Check for a valid value
			throw XLSUtilities.getApplicationException(localisation, "tu_cn",rowNumber, "choices", o.value, null, null);

		} else if(listMap.get(o.value) != null) {
			// Check for a duplicate value
			ApplicationException e = XLSUtilities.getApplicationException(localisation, "tu_do", rowNumber, "choices", o.value, o.optionList, null);
			warnings.add(new ApplicationWarning(e.getMessage()));
		}

		listMap.put(o.value, rowNumber);

	}
	
	private void questionInSurvey(ArrayList<String> names, String context, Question q) throws ApplicationException {
		for(String name : names) {
			if(qNameMap.get(name.toLowerCase()) == null) {
				Integer rowNumber = qNameMap.get(q.name.toLowerCase());
				throw XLSUtilities.getApplicationException(localisation, "tu_mq", rowNumber, "survey", context, name, null);
			}
		}
	}
	
	private void repeatCountClash(Question q) throws ApplicationException {
		String name = q.name.toLowerCase() + "_count";
		if(qNameMap.get(name) != null) {
			Integer rowNumber = qNameMap.get(name);
			throw XLSUtilities.getApplicationException(localisation, "tu_rc", rowNumber, "survey", name, q.name, null);
		}
	}
	
	private void settingsQuestionInSurvey(ArrayList<String> names, String colname) throws ApplicationException {
		for(String name : names) {
			if(qNameMap.get(name.toLowerCase()) == null) {
				String msg = localisation.getString("tu_mq");
				msg = msg.replace("%s1", colname);
				msg = msg.replace("%s2", "settings");
				msg = msg.replaceAll("%s3", name);
				throw new ApplicationException(msg);
			}
		}
	}
	
	private String getValidQuestionType(String in) {
		
		String out = null;	
		in = in.trim();
		String type = in.toLowerCase();
		
		if(type.equals("text")) {
			out = "text";
		} else if(type.equals("integer") || type.equals("int")) {
			out = "int";
		} else if (type.equals("decimal")) {
			out = "decimal";
		} else if (type.startsWith("select_one") || type.startsWith("select one")) {
			int idx = type.indexOf("one");
			out = "select_one " + in.substring(idx + 3).trim();
		} else if (type.startsWith("select_multiple") || type.startsWith("select multiple")) {
			int idx = type.indexOf("multiple");
			out = "select_multiple " + in.substring(idx + 8).trim();
		} else if (type.startsWith("rank") || type.startsWith("odk:rank")) {
			int idx = type.indexOf("rank");
			out = "rank " + in.substring(idx + 4).trim();
		} else if (type.equals("note")) {
			out = "note";
		} else if (type.equals("geopoint") || type.equals("location")) {
			out = "geopoint";
		} else if (type.equals("geotrace")) {
			out = "geotrace";
		} else if (type.equals("geoshape")) {
			out = "geoshape";
		} else if (type.equals("date")) {
			out = "date";
		} else if (type.equals("datetime")) {
			out = "dateTime";
		} else if (type.equals("time")) {
			out = "time";
		} else if (type.equals("image")) {
			out = "image";
		} else if (type.equals("audio")) {
			out = "audio";
		} else if (type.equals("video")) {
			out = "video";
		} else if (type.equals("file")) {
			out = "file";
		} else if (type.equals("barcode")) {
			out = "barcode";
		} else if (type.equals("calculate") || type.equals("calculation")) {
			out = "calculate";
		} else if (type.equals("acknowledge")) {
			out = "acknowledge";
		} else if (type.equals("chart")) {
			out = "chart";
		} else if (type.equals("parent_form")) {
			out = "parent_form";
		} else if (type.equals(SmapQuestionTypes.CHILD_FORM)) {
			out = SmapQuestionTypes.CHILD_FORM;
		} else if (type.equals("range")) {
			out = "range";
		} else if (type.equals("begin repeat") || type.equals("begin_repeat")) {
			out = "begin repeat";
		} else if (type.equals("end repeat") || type.equals("end_repeat")) {
			out = "end repeat";
		} else if (type.equals("begin group") || type.equals("begin_group")) {
			out = "begin group";
		} else if (type.equals("end group") || type.equals("end_group")) {
			out = "end group";
		} else if (type.equals("start")) {
			out = "start";
		} else if (type.equals("end")) {
			out = "end";
		} else if (type.equals("today")) {
			out = "today";
		} else if (type.equals("deviceid")) {
			out = "deviceid";
		} else if (type.equals("subscriberid")) {
			out = "subscriberid";
		} else if (type.equals("simserial")) {
			out = "simserial";
		} else if (type.equals("phonenumber")) {
			out = "phonenumber";
		} else if (type.equals("username")) {
			out = "username";
		} else if (type.equals("email")) {
			out = "email";
		} else if (type.equals("hidden value")) {	// Commcare 
			out = "calculate";
		} else if (type.equals("label")) {			// Commcare 
			out = "note";
		} else if (type.equals("trigger")) {			// Commcare 
			out = "trigger";
		} 
				
		return out;
	}
	
	private boolean getBooleanColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum) throws ApplicationException {
		String v = XLSUtilities.getTextColumn(row, name, header, lastCellNum, null); 
		
		if(v != null) {
			v = v.trim();
		} else {
			return false;
		}
		
		if(v.equalsIgnoreCase("yes") 
				|| v.equalsIgnoreCase("true") 
				|| v.equalsIgnoreCase("y")) {
			return true;
		} else {
			return false;
		}
	}
	
	private void setFormReference(ArrayList<KeyValueSimp> parameters, Form f) throws ApplicationException {
		if(parameters != null) {
			String ref = GeneralUtilityMethods.getSurveyParameter("ref", parameters);
			if(ref != null) {
				f.reference = true;			
				f.referenceName = ref;
			}
			
		}
	}
	
	private void setFormMerge(ArrayList<KeyValueSimp>  parameters, Form f) throws ApplicationException {
		if(parameters != null) {
			String ref = GeneralUtilityMethods.getSurveyParameter("merge", parameters);	// deprecate
			if(ref != null) {
				f.merge = true;			
			}
			ref = GeneralUtilityMethods.getSurveyParameter("key_policy", parameters);
			if(ref != null) {
				if(ref.equals("replace")) {
					f.replace = true;
				} else if(ref.equals("merge")) {
					f.merge = true;
				}
			}
			
		}
	}
	
	/*
	 * Test for valid java rosa functions in an XPath expression
	 * If the call is for an appearance then only the search function is valid and "search" without parameters is also valid
	 */
	private void testXExprFunctions(String in, ResourceBundle localisation, 
			boolean isAppearance,
			int rowNumber,
			String column) throws Exception {
		
		// 1. remove any text inside quotes
		boolean inside = false;
		StringBuffer noText = new StringBuffer("");
		for(int i = 0; i < in.length(); i++) {
			if(in.charAt(i) == '\'') {
				inside = !inside;
			} else if(!inside) {
				noText.append(in.charAt(i));
			}
		}
		
		if(noText.length() > 0) {
			String process = noText.toString();
			for(FunctionCheck f : functions) {
				Pattern pattern = Pattern.compile(f.name + "[\\s]*\\(");
				java.util.regex.Matcher matcher = pattern.matcher(process);
	
				while (matcher.find()) {
	
					StringBuffer toTest = new StringBuffer("");
					String matched = matcher.group();			
					
					// remove sub functions
					int depth = 0;
					boolean end = false;
					for(int i = matcher.start(); i < noText.length(); i++) {
						if(noText.charAt(i) == '(') {
							depth++;
						} else if(noText.charAt(i) == ')') {
							depth--;
							if(depth == 0) {
								end = true;
							}
						}
						if(depth <= 1) {
							toTest.append(noText.charAt(i));
						}
						if(end) {
							break;
						}
					}
					
					String[] args = toTest.toString().split(",");
					if(args.length != f.args) {
						throw XLSUtilities.getApplicationException(localisation, "tu_args", rowNumber, "survey", column, 
								f.name, f.template);
					}
				}
			}
		}
	}
	
	private Stack<Question> getGroupStack(Integer formIdx) {
		Stack<Question> gs = null;
		gs = groupStackMap.get(formIdx);
		if(gs == null) {
			gs = new Stack<Question> ();
			groupStackMap.put(formIdx, gs);
		}
		return gs;
	}

}
