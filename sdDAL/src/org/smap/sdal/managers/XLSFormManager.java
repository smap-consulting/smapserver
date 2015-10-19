package org.smap.sdal.managers;

import java.io.IOException;
import java.io.OutputStream;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;

public class XLSFormManager {
	
	private class Column {
		public static final int COL_TYPE = 0;
		public static final int COL_NAME = 1;
		public static final int COL_LABEL = 2;
		
		public static final int COL_LIST_NAME = 100;
		public static final int COL_CHOICE_NAME = 101;
		public static final int COL_CHOICE_LABEL = 102;
		
		
		String name;
		private int type;
		private int labelIndex;		// Where there are multiple labels this records the label index
		private String typeString;
		private int width;			// Column width in 1/256th of a character width
		
		public Column(String name, int type, int labelIndex, String typeString) {
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
				
			}
			
			return value;
		}
		
		// Return the question value for this column
		public String getValue(Option o) {
			String value = "";
			
			if(type == COL_LIST_NAME) {
				
				value = o.optionList;

			} else if(type == COL_CHOICE_NAME) {				
				value = o.value;		
				
			} else if(type == COL_CHOICE_LABEL) {				
				value = o.labels.get(labelIndex).text;				
			}
			
			return value;
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
		
		Map<String, CellStyle> styles = createStyles(wb);
		
		// Create Columns
		ArrayList<Column> colsSurvey = getColumnsSurvey(survey);
		ArrayList<Column> colsChoices = getColumnsChoices(survey);
		
		createHeader(colsSurvey, surveySheet, styles);
		createHeader(colsChoices, choicesSheet, styles);	
		
		// Write out questions
		Form ff = survey.getFirstForm();
		processFormForXLS(outputStream, ff, survey, surveySheet, choicesSheet, styles, colsSurvey, colsChoices);
		
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
			ArrayList<Column> colsChoices) throws IOException {
		
		ArrayList<Question> questions = form.questions;
		
		for(Question q : questions)  {
			
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
					System.out.println("Process sub form: " + subForm.name);
					processFormForXLS(outputStream, subForm, survey, surveySheet, choicesSheet, styles, colsSurvey, colsChoices);
					addEndGroup(surveySheet, "end repeat", q.name, typeStyle);
				} 
				
				// If this question has a list of choices then add these to the choices sheet
				if(q.list_name != null) {
					OptionList ol = survey.optionLists.get(q.list_name);
					if(ol != null) {		// option list is populated for questions that are not select TODO Fix
						addChoiceList(survey, choicesSheet, ol, colsChoices, styles);
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
			Map<String, CellStyle> styles) {
		
		// TODO check to see if we have already added this list
		
		ArrayList<Option> options = ol.options;
		
		sheet.createRow(rowNumberChoices++);		// blank row
		for(Option o : options) {
			Row row = sheet.createRow(rowNumberSurvey++);
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);
				CellStyle colStyle = styles.get(col.typeString);
				
				Cell cell = row.createCell(i);
				if(colStyle != null) {	cell.setCellStyle(colStyle); }		
				
				cell.setCellValue(col.getValue(o));
				
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
	private ArrayList<Column> getColumnsSurvey(org.smap.sdal.model.Survey survey) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		cols.add(new Column("type", Column.COL_TYPE, 0, "type"));
		cols.add(new Column("name", Column.COL_NAME, 0, "name"));
		
		// Add label columns
		int labelIndex = 0;
		for(String language : survey.languages) {
			cols.add(new Column("label::" + language, Column.COL_LABEL, labelIndex++, "label"));
		}
		return cols;
	}
	
	/*
	 * Get the columns for the choices sheet
	 */
	private ArrayList<Column> getColumnsChoices(org.smap.sdal.model.Survey survey) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		cols.add(new Column("list name", Column.COL_LIST_NAME, 0, "list name"));
		cols.add(new Column("name", Column.COL_CHOICE_NAME, 0, "choice_name"));
		
		// Add label columns
		int labelIndex = 0;
		for(String language : survey.languages) {
			cols.add(new Column("label::" + language, Column.COL_CHOICE_LABEL, labelIndex++, "choice_label"));
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

        return styles;
    }

}