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

import java.io.IOException;
import java.io.OutputStream;


//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Pulldata;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.RoleColumnFilter;
import org.smap.sdal.model.Survey;

import surveyKPI.UploadFiles;

public class XLSFormManager {

	private static Logger log =
			Logger.getLogger(UploadFiles.class.getName());
	
	private class Column {
		// Survey sheet columns
		public static final int COL_TYPE = 0;
		public static final int COL_NAME = 1;
		public static final int COL_LABEL = 2;
		public static final int COL_HINT = 3;
		public static final int COL_CHOICE_FILTER = 4;
		public static final int COL_CONSTRAINT = 5;
		public static final int COL_CONSTRAINT_MSG = 6;
		public static final int COL_RELEVANT = 7;
		public static final int COL_REPEAT_COUNT = 8;
		public static final int COL_READONLY = 9;
		public static final int COL_APPEARANCE = 10;
		public static final int COL_REQUIRED = 11;
		public static final int COL_REQUIRED_MSG = 12;
		public static final int COL_CALCULATION = 13;
		public static final int COL_IMAGE = 14;
		public static final int COL_VIDEO = 15;
		public static final int COL_AUDIO = 16;
		public static final int COL_AUTOPLAY = 17;
		public static final int COL_ACCURACY = 18;
		public static final int COL_PARAMETERS = 19;
		public static final int COL_ROLE = 20;
		public static final int COL_DISPLAY_NAME = 21;
		public static final int COL_INTENT = 22;
		public static final int COL_GUIDANCE_HINT = 23;

		// Choice sheet columns
		public static final int COL_LIST_NAME = 100;
		public static final int COL_CHOICE_NAME = 101;
		public static final int COL_CHOICE_LABEL = 102;
		public static final int COL_DEFAULT = 103;
		public static final int COL_CHOICE_DISPLAY_NAME = 104;

		// Settings sheet columns
		public static final int COL_DEFAULT_LANGUAGE = 200;
		public static final int COL_INSTANCE_NAME = 201;
		public static final int COL_STYLE = 202;
		public static final int COL_KEY = 203;
		public static final int COL_KEY_POLICY = 204;
		public static final int COL_ROLE_ROW = 205;
		public static final int COL_ALLOW_IMPORT = 206;
		public static final int COL_PULLDATA_REPEAT = 207;
		public static final int COL_HIDE_ON_DEVICE = 208;
		public static final int COL_TIMING_DATA = 209;
		public static final int COL_AUDIT_LOCATION_DATA = 210;
		public static final int COL_TRACK_CHANGES = 211;

		String name;
		private int type;
		private int labelIndex;		// Where there are multiple labels this records the label index
		private String typeString;
		int colNumber;

		public Column(int colNumber, String name, int type, int labelIndex, String typeString) {
			this.colNumber = colNumber;
			this.name = name;
			this.type = type;
			this.typeString = typeString;
			this.labelIndex = labelIndex;
		}

		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			if(type == COL_LABEL) {
				width = 256 * 40;
			}
			return width;
		}

		// Return the style for the cell
		public CellStyle getStyle(Map<String, CellStyle> styles, Question q) {
			CellStyle style = null;

			if(q.type.equals("begin repeat")) {
				style = styles.get("begin repeat");

			} else if(q.type.equals("begin group")) {
				style = styles.get("begin group");

			} else if(q.type.equals("end group") && type < 2) {
				style = styles.get("begin group");
			}

			return style;
		}


		// Return the question value for this column
		public String getValue(Question q) {
			String value = "";

			if(type == COL_TYPE) {

				if(q.type.equals("string") && !q.visible) {		// By this point the type should be calculate anyway
					value = "calculate";
				} else if(q.type.equals("string")) {
					value = "text";
				} else if(q.type.equals("select1")) {
					value = "select_one " + q.list_name;
				} else if(q.type.equals("select")) {
					value = "select_multiple " + q.list_name;
				}  else if(q.type.equals("rank")) {
					value = "rank " + q.list_name;
				} else if(q.propertyType && q.source_param != null) {
					value = q.source_param;
				} else {
					value = q.type;		// Everything else
				}

			} else if(type == COL_NAME) {	
				if(q.type.equals("end group")) {
					if(q.name != null) {
						int idx = q.name.indexOf("_groupEnd");
						if(idx >= 0) {
							value = q.name.substring(0, idx);
						}
					}
				} else {
					value = q.name;	
				}

			} else if(type == COL_DISPLAY_NAME) {	
				value = q.display_name;	
				
			} else if(type == COL_LABEL) {
				if(q.type.equals("calculate")) {
					value = "";
				} else {
					value = q.labels.get(labelIndex).text;
				}

			} else if(type == COL_HINT) {	
				if(q.type.equals("calculate")) {	
					value = "";
				} else {
					value = q.labels.get(labelIndex).hint;
				}

			} else if(type == COL_GUIDANCE_HINT) {	
				if(q.type.equals("calculate")) {	
					value = "";
				} else {
					value = q.labels.get(labelIndex).guidance_hint;
				}

			} else if(type == COL_CHOICE_FILTER) {				
				value = q.choice_filter;

			} else if(type == COL_CONSTRAINT) {				
				value = q.constraint;

			} else if(type == COL_CONSTRAINT_MSG) {				
				value = q.constraint_msg;

			} else if(type == COL_RELEVANT) {				
				value = q.relevant;

			} else if(type == COL_REPEAT_COUNT) {
				if(q.type.equals("begin repeat")) {
					value = q.calculation;	
				} else {
					value = "";
				}

			} else if(type == COL_CALCULATION) {
				if(!q.type.equals("begin repeat")) {
					value = q.calculation;	
				} else {
					value = "";
				}

			} else if(type == COL_DEFAULT) {				
				value = q.defaultanswer;		

			} else if(type == COL_READONLY) {				
				value = q.readonly ? "yes" : "no";		

			} else if(type == COL_APPEARANCE) {				
				value = q.appearance;		

			} else if(type == COL_PARAMETERS) {				
				value = GeneralUtilityMethods.convertParametersToString(q.paramArray);		

			} else if(type == COL_AUTOPLAY) {	
				if(q.autoplay != null && q.autoplay.equals("none")) {
					value = null;
				} else {
					value = q.autoplay;	
				}

			} else if(type == COL_ACCURACY) {				
				value = q.accuracy;		

			} else if(type == COL_INTENT) {				
				value = q.intent;		

			} else if(type == COL_REQUIRED) {				
				value = q.required ? "yes" : "";		

			} else if(type == COL_REQUIRED_MSG) {				
				value = q.required_msg;		

			} else if(type == COL_IMAGE) {				
				value = q.labels.get(labelIndex).image;

			} else if(type == COL_VIDEO) {				
				value = q.labels.get(labelIndex).video;

			} else if(type == COL_AUDIO) {				
				value = q.labels.get(labelIndex).audio;

			} else if(type == COL_ROLE) {				
				Role r = survey.roles.get(typeString);
				if(r != null) {
					ArrayList<RoleColumnFilter> colFilters = r.column_filter;
					if(colFilters != null) {
						for(RoleColumnFilter rcf : colFilters) {
							if(rcf.id == q.id) {
								value = "yes";
								break;
							}
						}
						
					}
				}

			} else {
				System.out.println("Unknown column type for survey: " + type);
			}

			return value;
		}

		// Return the choice value for this column
		public String getValue(Option o, String listName) {
			String value = "";

			if(type == COL_LIST_NAME) {			
				value = listName;

			} else if(type == COL_CHOICE_NAME) {				
				value = o.value;		

			} else if(type == COL_CHOICE_DISPLAY_NAME) {				
				value = o.display_name;		

			} else if(type == COL_CHOICE_LABEL) {				
				value = o.labels.get(labelIndex).text;	

			} else if(type == COL_IMAGE) {				
				value = o.labels.get(labelIndex).image;	

			} else if(type == COL_VIDEO) {				
				value = o.labels.get(labelIndex).video;	

			} else if(type == COL_AUDIO) {				
				value = o.labels.get(labelIndex).audio;	

			} else {
				System.out.println("Unknown option type: " + type);
			}

			return value;
		}

		// Return the settings value for this column
		public String getValue() {
			String value = "";

			if(type == COL_DEFAULT_LANGUAGE) {			
				value = survey.def_lang;

			} else if(type == COL_STYLE) {			
				value = survey.surveyClass;

			} else if(type == COL_INSTANCE_NAME) {			
				value = survey.instanceNameDefn;

			} else if(type == COL_KEY) {			
				value = survey.hrk;

			} else if(type == COL_KEY_POLICY) {			
				value = survey.key_policy;

			} else if(type == COL_ROLE_ROW) {				
				Role r = survey.roles.get(typeString);
				if(r != null) {
					value = r.row_filter;
				}

			} else if(type == COL_ALLOW_IMPORT) {				
				value = survey.task_file ? "yes" : "no";

			} else if(type == COL_PULLDATA_REPEAT) {	
				ArrayList<Pulldata> pd = survey.pulldata;
				if(pd == null || pd.size() == 0) {
					value = "";
				} else {
					StringBuffer pdSB = new StringBuffer("");
					for(Pulldata p : pd) {
						if(pdSB.length() > 0) {
							pdSB.append(":");
						}
						pdSB.append(p.survey).append("(").append(p.data_key).append(")");
					}
					value = pdSB.toString();
				}

			} else if(type == COL_HIDE_ON_DEVICE) {				
				value = survey.getHideOnDevice() ? "yes" : "no";

			} else if(type == COL_TIMING_DATA) {				
				value = survey.timing_data ? "yes" : "no";

			} else if(type == COL_AUDIT_LOCATION_DATA) {				
				value = survey.audit_location_data ? "yes" : "no";

			} else if(type == COL_TRACK_CHANGES) {				
				value = survey.track_changes ? "yes" : "no";

			} else {
				log.info("Unknown settings type: " + type);
			}

			return value;
		}

	}

	/*
	 * Globals
	 */
	Workbook wb = null;
	int rowNumberSurvey = 1;		// Heading row is 0
	int rowNumberChoices = 1;		// Heading row is 0
	int rowNumberSettings = 1;		// Heading row is 0
	
	Survey survey = null;

	public XLSFormManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}

	/*
	 * Convert a Survey Definition into an XLS file
	 */
	public void createXLSForm(OutputStream outputStream, Survey survey) throws IOException {

		this.survey = survey;
		
		Sheet surveySheet = wb.createSheet("survey");
		Sheet choicesSheet = wb.createSheet("choices");
		Sheet settingsSheet = wb.createSheet("settings");

		// Freeze panes by default
		surveySheet.createFreezePane(2, 1);
		choicesSheet.createFreezePane(3, 1);

		Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);

		// Create Columns
		HashMap<String, Integer> filterIndexes = new HashMap<String, Integer> ();
		HashMap<String, Integer> namedColumnIndexes = new HashMap<String, Integer> ();
		HashMap<String, String> addedOptionLists = new HashMap<String, String> ();

		ArrayList<Column> colsSurvey = getColumnsSurvey(namedColumnIndexes);
		ArrayList<Column> colsChoices = getColumnsChoices();
		ArrayList<Column> colsSettings = getColumnsSettings();

		// Write out the column headings
		createHeader(colsSurvey, surveySheet, styles);
		createHeader(colsChoices, choicesSheet, styles);
		createHeader(colsSettings, settingsSheet, styles);

		// Write out questions
		Form ff = survey.getFirstForm();
		processFormForXLS(ff, surveySheet, choicesSheet, styles, colsSurvey, 
				colsChoices, 
				filterIndexes,
				addedOptionLists,
				namedColumnIndexes);

		// Write out survey settings
		processSurveyForXLS(settingsSheet, styles, colsSettings);

		wb.write(outputStream);
		outputStream.close();
	}

	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(ArrayList<Column> cols, Sheet sheet, Map<String, CellStyle> styles) {
		// Set column widths
		for(int i = 0; i < cols.size(); i++) {
			sheet.setColumnWidth(i, cols.get(i).getWidth());
		}

		// Create survey sheet header row
		Row headerRow = sheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);

			Cell cell = headerRow.createCell(i);
			cell.setCellStyle(headerStyle);
			cell.setCellValue(col.name);
		}
	}

	/*
	 * Convert a single form to XLS
	 */
	private void processFormForXLS(
			Form form, 
			Sheet surveySheet,
			Sheet choicesSheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> colsSurvey,
			ArrayList<Column> colsChoices,
			HashMap<String, Integer> filterIndexes,
			HashMap<String, String> addedOptionLists,
			HashMap<String, Integer> namedColumnIndexes) throws IOException {

		ArrayList<Question> questions = form.questions;
		boolean inMeta = false;

		/*
		 * Add preload questions
		 */
		if(form.parentform == 0 && survey.meta != null) {
			Column typeCol = colsSurvey.get(namedColumnIndexes.get("type"));
			Column nameCol = colsSurvey.get(namedColumnIndexes.get("name"));
			
			Row row = surveySheet.createRow(rowNumberSurvey++);		// blank row
			for(MetaItem mi : survey.meta) {
				if(mi.isPreload) {
					row = surveySheet.createRow(rowNumberSurvey++);
							
					Cell cell = row.createCell(typeCol.colNumber);
					cell.setCellValue(mi.sourceParam);
					
					cell = row.createCell(nameCol.colNumber);
					cell.setCellValue(mi.name);
				}

			}
			row = surveySheet.createRow(rowNumberSurvey++);		// blank row
		}
		
		for(Question q : questions)  {

			if(q.name.equals("meta")) {
				inMeta = true;
			} else if(q.name.equals("meta_groupEnd")) {
				inMeta = false;
			} 


			if(!inMeta && !q.name.equals("meta_groupEnd") && !q.soft_deleted) {

				if(isRow(q)) {
					Row row = surveySheet.createRow(rowNumberSurvey++);
					for(int i = 0; i < colsSurvey.size(); i++) {
						Column col = colsSurvey.get(i);			
						Cell cell = row.createCell(i);
						CellStyle style = col.getStyle(styles, q);
						if(style != null) {	cell.setCellStyle(style); }

						cell.setCellValue(col.getValue(q));
					}

					// If this is a sub form then process its questions now
					Form subForm = survey.getSubForm(form, q);
					if( subForm != null) {

						processFormForXLS(subForm, 
								surveySheet, choicesSheet, styles, 
								colsSurvey, 
								colsChoices,
								filterIndexes,
								addedOptionLists,
								namedColumnIndexes);

						addEndGroup(surveySheet, "end repeat", q.name, styles.get("begin repeat"));
					} 

					// If this question has a list of choices then add these to the choices sheet but only if they have not already been added
					if(q.list_name != null) {
						if(addedOptionLists.get(q.list_name) == null) {
							OptionList ol = survey.optionLists.get(q.list_name);
							if(ol != null) {		// option list is populated for questions that are not select TODO Fix
								addChoiceList(choicesSheet, ol, colsChoices, filterIndexes, styles, q.list_name);
							}
							addedOptionLists.put(q.list_name, q.list_name);	// Remember lists that have been added
						}
					}
				}
			}

		}
	}

	/*
	 * Add a choice list
	 */
	private void addChoiceList(Sheet sheet, 
			OptionList ol,
			ArrayList<Column> cols, 
			HashMap<String, Integer> filterIndexes,
			Map<String, CellStyle> styles,
			String listName) {

		ArrayList<Option> options = ol.options;

		sheet.createRow(rowNumberChoices++);		// blank row
		for(Option o : options) {
			Row row = sheet.createRow(rowNumberChoices++);
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);
				CellStyle colStyle = styles.get(col.typeString);

				Cell cell = row.createCell(i);
				if(colStyle != null) {	cell.setCellStyle(colStyle); }		

				cell.setCellValue(col.getValue(o, listName));

			}

			// Add any filter columns
			if(o.cascade_filters != null && o.cascade_filters.size() > 0) {
				List<String> keyList = new ArrayList<String>(o.cascade_filters.keySet());
				for(String k : keyList) {
					String v = o.cascade_filters.get(k);

					if(k.equals("display_name")) {	// Hack to minimise impact of wrongly including display_name as a filter
						continue;
					}
					
					Integer colIndex = filterIndexes.get(k);
					if(colIndex == null) {
						colIndex = new Integer(cols.size() + filterIndexes.size());
						filterIndexes.put(k, colIndex);
						Cell headerCell = sheet.getRow(0).createCell(colIndex.intValue());
						headerCell.setCellValue(k);
					}
					Cell cell = row.createCell(colIndex.intValue());
					cell.setCellValue(v);
				}
			}
		}

	}

	/*
	 * Add the end row of a "begin repeat" or "begin group"
	 */
	private void addEndGroup(Sheet sheet, String type, String name, CellStyle style) {
		// Add the end repeat row
		Row row = sheet.createRow(rowNumberSurvey++);

		// Type
		Cell cell = row.createCell(0);
		if(style != null) {		
			cell.setCellStyle(style);
		}
		cell.setCellValue(type);	

		// Value
		cell = row.createCell(1);
		if(style != null) {		
			cell.setCellStyle(style);
		}
		cell.setCellValue(name);	
	}

	/*
	 * Return true if this item should be included as a row in the XLSForm
	 */
	private boolean isRow(Question q) {
		boolean row = true;

		if(q.name.equals("prikey") || q.name.equals("_task_key") ||
				q.name.equals("_device") ||
				q.name.equals("_start") || q.name.equals("_end")
				) {
			row = false;
		} else if(q.type.equals("note") && !q.visible && (q.calculation == null || q.calculation.trim().length() == 0)) {
			row = false;		// Loading a survey from an xml file may result in an instanceName not in a meta group which should not be included in the XLS
		} else if((q.name.equals("_instanceid") 
				|| q.name.equals("meta")
				|| q.name.equals("instanceID")
				|| q.name.equals("instanceName")
				|| q.name.equals("meta_groupEnd")
				)) {
			row = false;
		}

		return row;
	}

	/*
	 * Get the columns for the survey sheet
	 */
	private ArrayList<Column> getColumnsSurvey(HashMap<String, Integer> namedColumnIndexes) {

		ArrayList<Column> cols = new ArrayList<Column> ();

		int colNumber = 0;
		// Add type and name columns
		cols.add(new Column(colNumber++, "type", Column.COL_TYPE, 0, "type"));
		namedColumnIndexes.put("type", new Integer(colNumber -1));
		
		cols.add(new Column(colNumber++, "name", Column.COL_NAME, 0, "name"));
		namedColumnIndexes.put("name", new Integer(colNumber -1));

		// Add label columns which vary according to the number of languages
		int labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++,"label::" + language.name, Column.COL_LABEL, labelIndex, "label"));
			cols.add(new Column(colNumber++,"hint::" + language.name, Column.COL_HINT, labelIndex, "label"));
			cols.add(new Column(colNumber++,"guidance_hint::" + language.name, Column.COL_GUIDANCE_HINT, labelIndex, "label"));
			labelIndex++;
		}

		// Add remaining columns
		cols.add(new Column(colNumber++,"display_name", Column.COL_DISPLAY_NAME, 0, "display_name"));
		cols.add(new Column(colNumber++,"choice_filter", Column.COL_CHOICE_FILTER, 0, "choice_filter"));
		cols.add(new Column(colNumber++,"constraint", Column.COL_CONSTRAINT, 0, "constraint"));
		cols.add(new Column(colNumber++,"constraint_message", Column.COL_CONSTRAINT_MSG, 0, "constraint_msg"));
		cols.add(new Column(colNumber++,"relevant", Column.COL_RELEVANT, 0, "relevant"));
		cols.add(new Column(colNumber++, "repeat_count", Column.COL_REPEAT_COUNT, 0, "repeat_count"));

		namedColumnIndexes.put("repeat_count", new Integer(colNumber -1));

		cols.add(new Column(colNumber++, "default", Column.COL_DEFAULT, 0, "default"));
		cols.add(new Column(colNumber++, "readonly", Column.COL_READONLY, 0, "readonly"));
		cols.add(new Column(colNumber++, "appearance", Column.COL_APPEARANCE, 0, "appearance"));
		cols.add(new Column(colNumber++, "parameters", Column.COL_PARAMETERS, 0, "parameters"));
		cols.add(new Column(colNumber++, "autoplay", Column.COL_AUTOPLAY, 0, "autoplay"));
		cols.add(new Column(colNumber++, "body::accuracyThreshold", Column.COL_ACCURACY, 0, "accuracy"));
		cols.add(new Column(colNumber++, "body::intent", Column.COL_INTENT, 0, "intent"));
		cols.add(new Column(colNumber++, "required", Column.COL_REQUIRED, 0, "required"));
		cols.add(new Column(colNumber++,"required_message", Column.COL_REQUIRED_MSG, 0, "required_msg"));
		cols.add(new Column(colNumber++, "calculation", Column.COL_CALCULATION, 0, "calculation"));

		// Add role columns
		for(String role : survey.roles.keySet()) {
			cols.add(new Column(colNumber++,"role::" + role, Column.COL_ROLE, 0, role));
		}
		
		// Add media columns (Do this as the last columns since these columns are less used
		// TODO only do this if there are media associated with choices
		labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++, "media::image::" + language.name, Column.COL_IMAGE, 0, "image"));
			cols.add(new Column(colNumber++, "media::video::" + language.name, Column.COL_VIDEO, 0, "video"));
			cols.add(new Column(colNumber++, "media::audio::" + language.name, Column.COL_AUDIO, 0, "audio"));
			labelIndex++;
		}
		return cols;
	}

	/*
	 * Get the columns for the choices sheet
	 */
	private ArrayList<Column> getColumnsChoices() {

		ArrayList<Column> cols = new ArrayList<Column> ();

		int colNumber = 0;

		cols.add(new Column(colNumber++, "list name", Column.COL_LIST_NAME, 0, "list name"));
		cols.add(new Column(colNumber++, "name", Column.COL_CHOICE_NAME, 0, "choice_name"));
		cols.add(new Column(colNumber++, "display_name", Column.COL_CHOICE_DISPLAY_NAME, 0, "choice_display_name"));

		// Add label columns
		int labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++, "label::" + language.name, Column.COL_CHOICE_LABEL, labelIndex++, "choice_label"));
		}

		// Add media
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++, "media::image::" + language.name, Column.COL_IMAGE, 0, "image"));
			cols.add(new Column(colNumber++, "media::video::" + language.name, Column.COL_VIDEO, 0, "video"));
			cols.add(new Column(colNumber++, "media::audio::" + language.name, Column.COL_AUDIO, 0, "audio"));
		}

		return cols;
	}

	/*
	 * Get the columns for the settings sheet
	 */
	private ArrayList<Column> getColumnsSettings() {

		ArrayList<Column> cols = new ArrayList<Column> ();

		int colNumber = 0;

		cols.add(new Column(colNumber++, "default_language", Column.COL_DEFAULT_LANGUAGE, 0, "default_language"));
		cols.add(new Column(colNumber++, "instance_name", Column.COL_INSTANCE_NAME, 0, "instance_name"));
		cols.add(new Column(colNumber++, "style", Column.COL_STYLE, 0, "style"));
		cols.add(new Column(colNumber++, "key", Column.COL_KEY, 0, "key"));
		cols.add(new Column(colNumber++, "key_policy", Column.COL_KEY_POLICY, 0, "key_policy"));
		cols.add(new Column(colNumber++, "allow_import", Column.COL_ALLOW_IMPORT, 0, "allow_import"));
		cols.add(new Column(colNumber++, "hide_on_device", Column.COL_HIDE_ON_DEVICE, 0, "hide_on_device"));
		cols.add(new Column(colNumber++, "timing_data", Column.COL_TIMING_DATA, 0, "timing_data"));
		cols.add(new Column(colNumber++, "audit_location_data", Column.COL_AUDIT_LOCATION_DATA, 0, "audit_location_data"));
		cols.add(new Column(colNumber++, "track_changes", Column.COL_TRACK_CHANGES, 0, "track_changes"));
		cols.add(new Column(colNumber++, "pulldata_repeat", Column.COL_PULLDATA_REPEAT, 0, "pulldata_repeat"));

		// Add role columns
		for(String role : survey.roles.keySet()) {
			cols.add(new Column(colNumber++,"role::" + role, Column.COL_ROLE_ROW, 0, role));
		}
		
		return cols;
	}

	/*
	 * write out the settings values
	 */
	private void processSurveyForXLS(Sheet settingsSheet, 
			Map<String, CellStyle> styles, 
			ArrayList<Column>  colsSettings) {

		Row row = settingsSheet.createRow(rowNumberSettings++);
		for(int i = 0; i < colsSettings.size(); i++) {
			Column col = colsSettings.get(i);			
			Cell cell = row.createCell(i);

			cell.setCellValue(col.getValue());
		}

	}


}
