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
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;

public class XLSFormManager {
	
	private class Column {
		public static final int COL_TYPE = 0;
		public static final int COL_NAME = 1;
		public static final int COL_LABEL = 2;
		
		String name;
		int type;
		int labelIndex;		// Where there are multiple labels this records the label index
		
		public Column(String name, int type, int labelIndex) {
			this.name = name;
			this.type = type;
			this.labelIndex = labelIndex;
		}
		
		// Return the value for this column
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
		
	}
	
	
	Workbook wb = null;
	int rowNumber = 1;		// Heading row is 0

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
		
		// Create header Row
		ArrayList<Column> cols = getColumns(survey);
		
		Row headerRow = surveySheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(col.name);
        }
		
		
		// Write out questions
		Form ff = survey.getFirstForm();
		processFormForXLS(outputStream, ff, survey, surveySheet, styles, cols);
		
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Convert a single form to XLS
	 */
	private void processFormForXLS(OutputStream outputStream,
			Form form, 
			org.smap.sdal.model.Survey survey,
			Sheet surveySheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> cols) throws IOException {
		
		ArrayList<Question> questions = form.questions;
		
		for(Question q : questions)  {
			
			System.out.println(q.name);
			
			if(isRow(q)) {
				Row row = surveySheet.createRow(rowNumber++);
				CellStyle style = styles.get(q.type);
				for(int i = 0; i < cols.size(); i++) {
					Column col = cols.get(i);
					
					Cell cell = row.createCell(i);
					if(style != null) {		
						cell.setCellStyle(style);
					}
					
					cell.setCellValue(col.getValue(q));
		        }
				
				// If this is a sub form then process its questions now
				Form subForm = survey.getSubForm(form, q);
				if( subForm != null) {
					System.out.println("Process sub form: " + subForm.name);
					processFormForXLS(outputStream, subForm, survey, surveySheet, styles, cols);
					addEndGroup(surveySheet, "end repeat", q.name, style);
				}
			}
			
		}
	}
	
	/*
	 * Add the end row of a "begin repeat" or "begin group"
	 */
	private void addEndGroup(Sheet sheet, String type, String name, CellStyle style) {
		// Add the end repeat row
		Row row = sheet.createRow(rowNumber++);
		
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
	 * Get the columns for the survey
	 */
	private ArrayList<Column> getColumns(org.smap.sdal.model.Survey survey) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		cols.add(new Column("type", Column.COL_TYPE, 0));
		cols.add(new Column("name", Column.COL_NAME, 0));
		
		// Add label columns
		int labelIndex = 0;
		for(String language : survey.languages) {
			cols.add(new Column("label::" + language, Column.COL_LABEL, labelIndex++));
		}
		return cols;
	}
	
   /**
     * create a library of cell styles
     */
    private static Map<String, CellStyle> createStyles(Workbook wb){
        Map<String, CellStyle> styles = new HashMap<String, CellStyle>();

        CellStyle style;
        Font headerFont = wb.createFont();
        headerFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        style = wb.createCellStyle();
        style.setFont(headerFont);
        styles.put("header", style);

        return styles;
    }

}
