package utilities;

import java.io.IOException;
import java.io.OutputStream;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Survey;
import org.w3c.dom.Element;

public class XLSFormManager {
	
	private class Column {
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
		public static final int COL_CALCULATION = 12;
		public static final int COL_IMAGE = 13;
		public static final int COL_VIDEO = 14;
		public static final int COL_AUDIO = 15;
		
		public static final int COL_LIST_NAME = 100;
		public static final int COL_CHOICE_NAME = 101;
		public static final int COL_CHOICE_LABEL = 102;
		public static final int COL_DEFAULT = 103;
		
		public static final int COL_DEFAULT_LANGUAGE = 200;
		public static final int COL_INSTANCE_NAME = 201;
		public static final int COL_STYLE = 202;
		
		
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
						
				if(q.type.equals("string") && q.calculation != null && q.calculation.trim().length() > 0) {
					value = "calculate";
				} else if(q.readonly && q.type.equals("string")) {
					value = "note";
				} else if(q.type.equals("string")) {
					value = "text";
				} else if(q.type.equals("select1")) {
					value = "select_one " + q.list_name;
				} else if(q.type.equals("select")) {
					value = "select_multiple " + q.list_name;
				} else {
					value = q.type;		// Everything else
				}

			} else if(type == COL_NAME) {				
				value = q.name;		
				
			} else if(type == COL_LABEL) {				
				value = q.labels.get(labelIndex).text;
				
			} else if(type == COL_HINT) {				
				value = q.labels.get(labelIndex).hint;
				
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
				
			} else if(type == COL_REQUIRED) {				
				value = q.required ? "yes" : "no";		
				
			} else if(type == COL_IMAGE) {				
				value = q.labels.get(labelIndex).image;
				
			} else if(type == COL_VIDEO) {				
				value = q.labels.get(labelIndex).video;
				
			} else if(type == COL_AUDIO) {				
				value = q.labels.get(labelIndex).audio;
				
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
				
			} else if(type == COL_CHOICE_LABEL) {				
				value = o.labels.get(labelIndex).text;	
				
			} else {
				System.out.println("Unknown option type: " + type);
			}
			
			return value;
		}
		
		// Return the settings value for this column
		public String getValue(Survey s) {
			String value = "";
			
			if(type == COL_DEFAULT_LANGUAGE) {			
				value = s.def_lang;

			} else if(type == COL_STYLE) {			
				value = s.surveyClass;

			} else if(type == COL_INSTANCE_NAME) {			
				value = s.instanceNameDefn;

			}else {
				System.out.println("Unknown option type: " + type);
			}
			
			return value;
		}
		
		// Get the column number
		public int getColNumber() {
			return colNumber;
		}
		
	}
	
	
	Workbook wb = null;
	int rowNumberSurvey = 1;		// Heading row is 0
	int rowNumberChoices = 1;		// Heading row is 0
	int rowNumberSettings = 1;		// Heading row is 0

	public XLSFormManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	public void createXLSForm(OutputStream outputStream, org.smap.sdal.model.Survey survey) throws IOException {
		
		Sheet surveySheet = wb.createSheet("survey");
		Sheet choicesSheet = wb.createSheet("choices");
		Sheet settingsSheet = wb.createSheet("settings");
		
		// Freeze panes by default
		surveySheet.createFreezePane(2, 1);
		choicesSheet.createFreezePane(3, 1);
		
		Map<String, CellStyle> styles = createStyles(wb);
		
		// Create Columns
		HashMap<String, Integer> filterIndexes = new HashMap<String, Integer> ();
		HashMap<String, Integer> namedColumnIndexes = new HashMap<String, Integer> ();
		HashMap<String, String> addedOptionLists = new HashMap<String, String> ();
		
		ArrayList<Column> colsSurvey = getColumnsSurvey(survey, namedColumnIndexes);
		ArrayList<Column> colsChoices = getColumnsChoices(survey);
		ArrayList<Column> colsSettings = getColumnsSettings();

		// Write out the column headings
		createHeader(colsSurvey, surveySheet, styles);
		createHeader(colsChoices, choicesSheet, styles);
		createHeader(colsSettings, settingsSheet, styles);
		
		// Write out questions
		Form ff = survey.getFirstForm();
		processFormForXLS(outputStream, ff, survey, surveySheet, choicesSheet, styles, colsSurvey, 
				colsChoices, 
				filterIndexes,
				addedOptionLists,
				namedColumnIndexes);
		
		// Write out survey settings
		processSurveyForXLS(outputStream, survey, settingsSheet, styles, colsSettings);
		
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
	private void processFormForXLS(OutputStream outputStream,
			Form form, 
			org.smap.sdal.model.Survey survey,
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
		String savedCalculation = null;		// Contains the repeat count for a sub form
		
		for(Question q : questions)  {
			
			if(q.name.equals("meta")) {
				inMeta = true;
			} else if(q.name.equals("meta_groupEnd")) {
				inMeta = false;
			} 
			
			/*
			 * Determine if this question is a repeat count
			 *  For forms loaded after this code only q.repeatCount needs to be checked
			 *  For backward compatability we record any calcualtion question as well and assume the calculate immediately
			 *   before a begin repeat count is the correct one
			 */
			if(q.repeatCount) {
				// Save the calculation as it contain the calculation for a repeat which needs to be placed in the begin repeat row
				// Otherwise this "dummy" question is ignored
				//savedCalculation = q.calculation;
			} else {
				
				if(!inMeta && !q.name.equals("meta_groupEnd")) {
					
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
							// Add the repeat count using the saved calculation and the column index of the repeat count column
							//Column col = colsSurvey.get(namedColumnIndexes.get("repeat_count").intValue());	
							//Cell cell = row.createCell(col.getColNumber());
							//CellStyle style = col.getStyle(styles, q);
							//if(style != null) {	cell.setCellStyle(style); }
							//cell.setCellValue(savedCalculation);
							//savedCalculation = null;
							
							processFormForXLS(outputStream, subForm, survey, surveySheet, choicesSheet, styles, 
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
									addChoiceList(survey, choicesSheet, ol, colsChoices, filterIndexes, styles, q.list_name);
								}
								addedOptionLists.put(q.list_name, q.list_name);	// Remember lists that have been added
							}
						}
					}
				}
			}
			
		}
	}
	
	/*
	 * Add a choice list
	 */
	private void addChoiceList(org.smap.sdal.model.Survey survey, 
			Sheet sheet, 
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
			if(o.cascadeKeyValues != null && o.cascadeKeyValues.size() > 0) {
				List<String> keyList = new ArrayList<String>(o.cascadeKeyValues.keySet());
	        	for(String k : keyList) {
	        		String v = o.cascadeKeyValues.get(k);
	        		
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
		
		if(q.name.equals("prikey")) {
			row = false;
		} else if(q.name.startsWith("_")) {
			row = false;
		} else if(q.type.equals("note") && !q.visible && (q.calculation == null || q.calculation.trim().length() == 0)) {
			row = false;		// Loading a survey from an xml file may result in an instanceName not in a meta group which should not be included in the XLS
		}
		
		return row;
	}
	
	/*
	 * Get the columns for the survey sheet
	 */
	private ArrayList<Column> getColumnsSurvey(org.smap.sdal.model.Survey survey,
			HashMap<String, Integer> namedColumnIndexes) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
		// Add type and name columns
		cols.add(new Column(colNumber++, "type", Column.COL_TYPE, 0, "type"));
		cols.add(new Column(colNumber++, "name", Column.COL_NAME, 0, "name"));
		
		// Add label columns which vary according to the number of languages
		int labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++,"label::" + language.name, Column.COL_LABEL, labelIndex, "label"));
			cols.add(new Column(colNumber++,"hint::" + language.name, Column.COL_HINT, labelIndex, "label"));
			labelIndex++;
		}
		
		// Add remaining columns
		cols.add(new Column(colNumber++,"choice_filter", Column.COL_CHOICE_FILTER, 0, "choice_filter"));
		cols.add(new Column(colNumber++,"constraint", Column.COL_CONSTRAINT, 0, "constraint"));
		cols.add(new Column(colNumber++,"constraint_msg", Column.COL_CONSTRAINT_MSG, 0, "constraint_message"));
		cols.add(new Column(colNumber++,"relevant", Column.COL_RELEVANT, 0, "relevant"));
		cols.add(new Column(colNumber++, "repeat_count", Column.COL_REPEAT_COUNT, 0, "repeat_count"));
		
		namedColumnIndexes.put("repeat_count", new Integer(colNumber -1));
		
		cols.add(new Column(colNumber++, "default", Column.COL_DEFAULT, 0, "default"));
		cols.add(new Column(colNumber++, "readonly", Column.COL_READONLY, 0, "readonly"));
		cols.add(new Column(colNumber++, "appearance", Column.COL_APPEARANCE, 0, "appearance"));
		cols.add(new Column(colNumber++, "required", Column.COL_REQUIRED, 0, "required"));
		cols.add(new Column(colNumber++, "calculation", Column.COL_CALCULATION, 0, "calculation"));
		
		// Add media columns (Do this as the last columns since these columns are less used
		labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++, "image::" + language.name, Column.COL_IMAGE, 0, "image"));
			cols.add(new Column(colNumber++, "video::" + language.name, Column.COL_VIDEO, 0, "video"));
			cols.add(new Column(colNumber++, "audio::" + language.name, Column.COL_AUDIO, 0, "audio"));
		labelIndex++;
	}
		return cols;
	}
	
	/*
	 * Get the columns for the choices sheet
	 */
	private ArrayList<Column> getColumnsChoices(org.smap.sdal.model.Survey survey) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
		
		cols.add(new Column(colNumber++, "list name", Column.COL_LIST_NAME, 0, "list name"));
		cols.add(new Column(colNumber++, "name", Column.COL_CHOICE_NAME, 0, "choice_name"));
		
		// Add label columns
		int labelIndex = 0;
		for(Language language : survey.languages) {
			cols.add(new Column(colNumber++, "label::" + language.name, Column.COL_CHOICE_LABEL, labelIndex++, "choice_label"));
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
		
		return cols;
	}
	
   /**
     * create a library of cell styles
     */
    private static Map<String, CellStyle> createStyles(Workbook wb){
        Map<String, CellStyle> styles = new HashMap<String, CellStyle>();

        CellStyle style = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        style.setFont(headerFont);
        styles.put("header", style);

        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("label", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin repeat", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_YELLOW.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin group", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("is_required", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("not_required", style);

        return styles;
    }
    
    /*
     * write out the settings values
     */

    private void processSurveyForXLS(OutputStream outputStream, 
    		org.smap.sdal.model.Survey survey, Sheet settingsSheet, 
    		Map<String, CellStyle> styles, 
    		ArrayList<Column>  colsSettings) {
    	
    	Row row = settingsSheet.createRow(rowNumberSettings++);
    	for(int i = 0; i < colsSettings.size(); i++) {
			Column col = colsSettings.get(i);			
			Cell cell = row.createCell(i);
							
			cell.setCellValue(col.getValue(survey));
        }
    	
    }

}
