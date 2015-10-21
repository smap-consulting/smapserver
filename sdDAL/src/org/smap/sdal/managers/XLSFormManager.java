package org.smap.sdal.managers;

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
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
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
		
		public static final int COL_LIST_NAME = 100;
		public static final int COL_CHOICE_NAME = 101;
		public static final int COL_CHOICE_LABEL = 102;
		public static final int COL_DEFAULT = 103;
		
		
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
		
			
		// Return the question value for this column
		public String getValue(Question q) {
			String value = "";
			
			if(type == COL_TYPE) {
				
				value = q.type;
				
				if(value.equals("string")) {
					value = "text";
				} else if(value.equals("select1")) {
					value = "select_one " + q.list_name;
				} else if(value.equals("select")) {
					value = "select_multiple " + q.list_name;
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
				value = "";		// Not a begin repeat hence the repeat count is empty
				
			} else if(type == COL_CALCULATION) {				
				value = q.calculation;		
				
			} else if(type == COL_DEFAULT) {				
				value = q.defaultanswer;		
				
			} else if(type == COL_READONLY) {				
				value = q.readonly ? "yes" : "no";		
				
			} else if(type == COL_APPEARANCE) {				
				value = q.appearance;		
				
			} else if(type == COL_REQUIRED) {				
				value = q.required ? "yes" : "no";		
				
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
		
		// Get the column number
		public int getColNumber() {
			return colNumber;
		}
		
	}
	
	
	Workbook wb = null;
	int rowNumberSurvey = 1;		// Heading row is 0
	int rowNumberChoices = 1;		// Heading row is 0

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
		
		ArrayList<Column> colsSurvey = getColumnsSurvey(survey, namedColumnIndexes);
		ArrayList<Column> colsChoices = getColumnsChoices(survey);

		
		createHeader(colsSurvey, surveySheet, styles);
		createHeader(colsChoices, choicesSheet, styles);	
		
		// Write out questions
		Form ff = survey.getFirstForm();
		processFormForXLS(outputStream, ff, survey, surveySheet, choicesSheet, styles, colsSurvey, 
				colsChoices, 
				filterIndexes,
				namedColumnIndexes);
		
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
			
			if(!q.visible) {
				// Save this "invisible question" it may contain the calculation for a repeat
				savedCalculation = q.calculation;
			} else {
				if(!inMeta && !q.name.equals("meta_groupEnd")) {
					System.out.println(q.name);
					
					if(isRow(q)) {
						Row row = surveySheet.createRow(rowNumberSurvey++);
						CellStyle typeStyle = styles.get(q.type);
						for(int i = 0; i < colsSurvey.size(); i++) {
							Column col = colsSurvey.get(i);
							CellStyle colStyle = styles.get(col.typeString);	
							Cell cell = row.createCell(i);
							if(typeStyle != null) {	cell.setCellStyle(typeStyle); }
							if(colStyle != null) {	cell.setCellStyle(colStyle); }					
							cell.setCellValue(col.getValue(q));
				        }
						
						// If this is a sub form then process its questions now
						Form subForm = survey.getSubForm(form, q);
						if( subForm != null) {
							// Add the repeat count using the saved question
							Column col = colsSurvey.get(namedColumnIndexes.get("repeat_count").intValue());
							CellStyle colStyle = styles.get(col.typeString);	
							Cell cell = row.createCell(col.getColNumber());
							if(typeStyle != null) {	cell.setCellStyle(typeStyle); }
							if(colStyle != null) {	cell.setCellStyle(colStyle); }	
							cell.setCellValue(savedCalculation);
							
							System.out.println("Process sub form: " + subForm.name);
							processFormForXLS(outputStream, subForm, survey, surveySheet, choicesSheet, styles, 
									colsSurvey, 
									colsChoices,
									filterIndexes,
									namedColumnIndexes);
							addEndGroup(surveySheet, "end repeat", q.name, typeStyle);
						} 
						
						// If this question has a list of choices then add these to the choices sheet
						if(q.list_name != null) {
							OptionList ol = survey.optionLists.get(q.list_name);
							if(ol != null) {		// option list is populated for questions that are not select TODO Fix
								addChoiceList(survey, choicesSheet, ol, colsChoices, filterIndexes, styles, q.list_name);
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
		
		// TODO check to see if we have already added this list
		
		ArrayList<Option> options = ol.options;
		
		System.out.println("Add choice list: " + listName);
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
			if(o.cascadeKeyValues.size() > 0) {
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
		for(String language : survey.languages) {
			cols.add(new Column(colNumber++,"label::" + language, Column.COL_LABEL, labelIndex, "label"));
			cols.add(new Column(colNumber++,"hint::" + language, Column.COL_HINT, labelIndex, "label"));
			labelIndex++;
		}
		
		// Add remaining columns
		cols.add(new Column(colNumber++,"choice_filter", Column.COL_CHOICE_FILTER, 0, "choice_filter"));
		cols.add(new Column(colNumber++,"constraint", Column.COL_CONSTRAINT, 0, "constraint"));
		cols.add(new Column(colNumber++,"constraint_msg", Column.COL_CONSTRAINT_MSG, 0, "constraint_msg"));
		cols.add(new Column(colNumber++,"relevant", Column.COL_RELEVANT, 0, "relevant"));
		cols.add(new Column(colNumber++, "repeat_count", Column.COL_REPEAT_COUNT, 0, "repeat_count"));
		
		namedColumnIndexes.put("repeat_count", new Integer(colNumber -1));
		
		cols.add(new Column(colNumber++, "default", Column.COL_DEFAULT, 0, "default"));
		cols.add(new Column(colNumber++, "readonly", Column.COL_READONLY, 0, "readonly"));
		cols.add(new Column(colNumber++, "appearance", Column.COL_APPEARANCE, 0, "appearance"));
		cols.add(new Column(colNumber++, "required", Column.COL_REQUIRED, 0, "required"));
		cols.add(new Column(colNumber++, "calculation", Column.COL_CALCULATION, 0, "calculation"));
		
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
		for(String language : survey.languages) {
			cols.add(new Column(colNumber++, "label::" + language, Column.COL_CHOICE_LABEL, labelIndex++, "choice_label"));
		}
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
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        styles.put("is_required", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        styles.put("not_required", style);

        return styles;
    }

}
