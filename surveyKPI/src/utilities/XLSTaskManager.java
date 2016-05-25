package utilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;
import org.smap.sdal.model.Location;
import org.w3c.dom.Element;

import com.google.gson.JsonElement;



public class XLSTaskManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	Workbook wb = null;
	int rowNumber = 1;		// Heading row is 0
	
	private class Column {
		String name;
		String human_name;
		int colNumber;
		
		public Column(ResourceBundle localisation, int col, String n) {
			colNumber = col;
			name = n;
			//human_name = localisation.getString(n);
			human_name = n;		// Need to work out how to use translations when the file needs to be imported again
		}
		
		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			return width;
		}
		
		// Get a value for this column from the provided properties object
		public String getValue(TaskProperties props) {
			String value = null;
			
			if(name.equals("form")) {
				value = props.form_name;
			} else if(name.equals("name")) {
				value = props.name;
			} else if(name.equals("status")) {
				value = props.status;
			} else if(name.equals("assignee_ident")) {
				value = props.assignee_ident;
			} else if(name.equals("location_trigger")) {
				value = props.location_trigger;
			} else if(name.equals("from")) {
				if(props.from == null) {
					value = null;
				} else {
					value = String.valueOf(props.from);
				}
			} else if(name.equals("to")) {
				if(props.to == null) {
					value = null;
				} else {
					value = String.valueOf(props.to);
				}
			} else if(name.equals("guidance")) {
				value = props.guidance;		
			} else if(name.equals("repeat")) {
				value = String.valueOf(props.repeat);
			} else if(name.equals("email")) {
				value = props.email;
			} else if(name.equals("lon")) {
				value = String.valueOf(GeneralUtilityMethods.wktToLatLng(props.location, "lng"));
			} else if(name.equals("lat")) {
				value = String.valueOf(GeneralUtilityMethods.wktToLatLng(props.location, "lat"));
			} else if(name.equals("address")) {
				value = props.address;
			}
			
			if(value == null) {
				value = "";
			}
			return value;
		}
	
		// Get a date value for this column from the provided properties object
		public Timestamp getDateValue(TaskProperties props) {
			Timestamp value = null;
			
			if(name.equals("from")) {
				value = props.from;
			} else if(name.equals("to")) {
				value = props.to;
			} 
			
			return value;
		}
	}

	public XLSTaskManager() {

	}
	
	public XLSTaskManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	/*
	 * Create a task list from an XLS file
	 */
	public TaskListGeoJson getXLSTaskList(String type, InputStream inputStream) throws IOException {
		
		Sheet sheet = null;
		Sheet settingsSheet = null;
        Row row = null;
        int lastRowNum = 0;
        TaskListGeoJson tl = new TaskListGeoJson();
        tl.features = new ArrayList<TaskFeature> ();
        HashMap<String, Integer> header = null;
        String sv= null;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
		String tz = "GMT";
        
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook(inputStream);
		} else {
			wb = new XSSFWorkbook(inputStream);
		}
		
		/*
		 * Get the task sheet settings
		 */
		settingsSheet = wb.getSheet("settings");
		if(settingsSheet.getPhysicalNumberOfRows() > 0) {
			int lastSettingsRow = settingsSheet.getLastRowNum();
			for(int j = 0; j <= lastSettingsRow; j++) {
				row = settingsSheet.getRow(j);
                
                if(row != null) {         	
                    int lastCellNum = row.getLastCellNum();
                    if(lastCellNum > 0) {
                    	Cell c = row.getCell(0);
                    	String k = c.getStringCellValue();
                    	if(k != null && k.trim().toLowerCase().equals("time zone:")) {
                    		c = row.getCell(1);
                    		tz = c.getStringCellValue();
                    		break;
                    	}
                    }
                }
			}
		}
		
		System.out.println("Getting tasks worksheet with timezone: " + tz);
		ZoneId timeZoneId = ZoneId.of(tz);
		ZoneId gmtZoneId = ZoneId.of("GMT");
		
		sheet = wb.getSheet("tasks");
		if(sheet.getPhysicalNumberOfRows() > 0) {
			
			lastRowNum = sheet.getLastRowNum();
			boolean needHeader = true;
			
            for(int j = 0; j <= lastRowNum; j++) {
                
            	row = sheet.getRow(j);
                
                if(row != null) {
                	
                    int lastCellNum = row.getLastCellNum();
                    
                	if(needHeader) {
                		header = getHeader(row, lastCellNum);
                		needHeader = false;
                	} else {
                		TaskFeature tf = new TaskFeature();
                		TaskProperties tp = new TaskProperties();
                		tf.properties = tp;

                		
                		try {
                			tp.id = 0;
                			tp.form_name = getColumn(row, "form", header, lastCellNum, null);
                			tp.name = getColumn(row, "name", header, lastCellNum, "");
                			tp.status = getColumn(row, "status", header, lastCellNum, "new");
                			tp.location_trigger = getColumn(row, "location_trigger", header, lastCellNum, null);
                			tp.assignee_ident = getColumn(row, "assignee_ident", header, lastCellNum, null);
                			tp.location = "POINT(" + getColumn(row, "lon", header, lastCellNum, "0") + " " + 
                					getColumn(row, "lat", header, lastCellNum, "0") + ")";
                			
                			// Get from value
                			tp.from = getGmtDate(row, "from", header, lastCellNum, timeZoneId, gmtZoneId);
                			tp.to = getGmtDate(row, "to", header, lastCellNum, timeZoneId, gmtZoneId);
                		    
                			tl.features.add(tf);
                		} catch (Exception e) {
                			log.log(Level.SEVERE, e.getMessage(), e);
                		}
                	}
                	
                }
                
            }
		}
	
		return tl;
		
		
	}
	
	/*
	 * Get a GMT date from the spreadsheet
	 */
	Timestamp getGmtDate(Row row, String name, HashMap<String, Integer> header, int lastCellNum, ZoneId timeZoneId, ZoneId gmtZoneId) throws Exception {
		LocalDateTime localDate = getDateColumn(row, name, header, lastCellNum, null).toLocalDateTime();
		ZonedDateTime localZoned = ZonedDateTime.of(localDate, timeZoneId);
		ZonedDateTime gmtZoned = localZoned.withZoneSameInstant(gmtZoneId);
		
		return Timestamp.valueOf(gmtZoned.toLocalDateTime());
	}
	
	/*
	 * Write a task list to an XLS file
	 */
	public void createXLSTaskFile(OutputStream outputStream, TaskListGeoJson tl, ResourceBundle localisation, 
			String tz) throws IOException {
		
		Sheet taskListSheet = wb.createSheet("tasks");
		Sheet taskSettingsSheet = wb.createSheet("settings");
		taskListSheet.createFreezePane(3, 1);	// Freeze header row and first 3 columns
		
		Map<String, CellStyle> styles = createStyles(wb);

		ArrayList<Column> cols = getColumnList(localisation);
		createHeader(cols, taskListSheet, styles);	
		processTaskListForXLS(tl, taskListSheet, taskSettingsSheet, styles, cols, tz);
		
		wb.write(outputStream);
		outputStream.close();
	}
	
	/*
	 * Get an array of locations from an XLS file
	 */
	public ArrayList<Location> convertWorksheetToTagArray(InputStream inputStream, String type) throws IOException {
		
		Sheet sheet = null;
        Row row = null;
        int lastRowNum = 0;
        String group = null;
        ArrayList<Location> tags = new ArrayList<Location> ();
        HashMap<String, Integer> header = null;
        
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook(inputStream);
		} else {
			wb = new XSSFWorkbook(inputStream);
		}
		
		int numSheets = wb.getNumberOfSheets();
		
		for(int i = 0; i < numSheets; i++) {
			sheet = wb.getSheetAt(i);
			if(sheet.getPhysicalNumberOfRows() > 0) {
				
				group = sheet.getSheetName();
				lastRowNum = sheet.getLastRowNum();
				boolean needHeader = true;
				
                for(int j = 0; j <= lastRowNum; j++) {
                    
                	row = sheet.getRow(j);
                    
                    if(row != null) {
                    	
                        int lastCellNum = row.getLastCellNum();
                        
                    	if(needHeader) {
                    		header = getHeader(row, lastCellNum);
                    		needHeader = false;
                    	} else {
                    		Location t = new Location();
                    		t.group = group;
                    		t.type = "nfc";
                    		try {
                    			t.uid = getColumn(row, "uid", header, lastCellNum, null);
                    			t.name = getColumn(row, "tagname", header, lastCellNum, null);
                    			tags.add(t);
                    		} catch (Exception e) {
                    			log.info("Error getting nfc column" + e.getMessage());
                    		}
                    	}
                    	
                    }
                    
                }
			}
		}
		
		return tags;

	}
	
	/*
	 * Create an XLS file from an array of tag locations
	 */
	/*
	 * Write a task list to an XLS file
	 */
	public void createXLSLocationsFile(OutputStream outputStream, ArrayList<Location> locations, ResourceBundle localisation) throws IOException {
		
		// TODO
	}

	
	/*
	 * Get a hashmap of column name and column index
	 */
	private HashMap<String, Integer> getHeader(Row row, int lastCellNum) {
		HashMap<String, Integer> header = new HashMap<String, Integer> ();
		
		Cell cell = null;
		String name = null;
		
        for(int i = 0; i <= lastCellNum; i++) {
            cell = row.getCell(i);
            if(cell != null) {
                name = cell.getStringCellValue();
                if(name != null && name.trim().length() > 0) {
                	name = name.toLowerCase();
                    header.put(name, i);
                }
            }
        }
            
		return header;
	}
	
	/*
	 * Get the value of a cell at the specified column
	 */
	private String getColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum, String def) throws Exception {
		
		Integer cellIndex;
		int idx;
		String value = null;
		double dValue = 0.0;
		Date dateValue = null;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
	
		cellIndex = header.get(name);
		if(cellIndex != null) {
			idx = cellIndex;
			if(idx <= lastCellNum) {
				Cell c = row.getCell(idx);
				if(c != null) {
					log.info("Get column: " + name);
					if(c.getCellType() == Cell.CELL_TYPE_NUMERIC) {
						if (HSSFDateUtil.isCellDateFormatted(c)) {
							dateValue = c.getDateCellValue();
							value = dateFormat.format(dateValue);
						} else {
							dValue = c.getNumericCellValue();
							value = String.valueOf(dValue);
							if(value != null && value.endsWith(".0")) {
								value = value.substring(0, value.lastIndexOf('.'));
							}
						}
					} else if(c.getCellType() == Cell.CELL_TYPE_STRING) {
						value = c.getStringCellValue();
					} else {
						value = null;
					}

				}
			}
		} else {
			throw new Exception("Column " + name + " not found");
		}

		if(value == null) {		// Set to default value if null
			value = def;
		}
		
		return value;
	}
	
	/*
	 * Get the timestamp value of a cell at the specified column
	 */
	private Timestamp getDateColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum, String def) throws Exception {
		
		Integer cellIndex;
		int idx;
		Timestamp tsValue = null;
	
		cellIndex = header.get(name);
		if(cellIndex != null) {
			idx = cellIndex;
			if(idx <= lastCellNum) {
				Cell c = row.getCell(idx);
				if(c != null) {
					log.info("Get date column: " + name);
					if(c.getCellType() == Cell.CELL_TYPE_NUMERIC) {
						if (HSSFDateUtil.isCellDateFormatted(c)) {
							tsValue = new Timestamp(c.getDateCellValue().getTime());
						} 
					} 
				}
			}
		} else {
			throw new Exception("Column " + name + " not found");
		}
		
		return tsValue;
	}
	
	/*
	 * Get the columns for the settings sheet
	 */
	private ArrayList<Column> getColumnList(ResourceBundle localisation) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
	
		cols.add(new Column(localisation, colNumber++, "form"));
		cols.add(new Column(localisation, colNumber++, "name"));
		cols.add(new Column(localisation, colNumber++, "status"));
		cols.add(new Column(localisation, colNumber++, "assignee_ident"));
		cols.add(new Column(localisation, colNumber++, "location_trigger"));
		cols.add(new Column(localisation, colNumber++, "from"));
		cols.add(new Column(localisation, colNumber++, "to"));
		cols.add(new Column(localisation, colNumber++, "guidance"));
		cols.add(new Column(localisation, colNumber++, "repeat"));
		cols.add(new Column(localisation, colNumber++, "duration"));
		cols.add(new Column(localisation, colNumber++, "email"));
		cols.add(new Column(localisation, colNumber++, "address"));
		cols.add(new Column(localisation, colNumber++, "lon"));
		cols.add(new Column(localisation, colNumber++, "lat"));
		
		
		return cols;
	}
	
	/*
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
        styles.put("default", style);

        return styles;
    }
    
	/*
	 * Create a header row and set column widths
	 */
	private void createHeader(ArrayList<Column> cols, Sheet sheet, Map<String, CellStyle> styles) {
		// Set column widths
		for(int i = 0; i < cols.size(); i++) {
			sheet.setColumnWidth(i, cols.get(i).getWidth());
		}
				
		Row headerRow = sheet.createRow(0);
		CellStyle headerStyle = styles.get("header");
		for(int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(col.human_name);
        }
	}
	
	/*
	 * Convert a task list array to XLS
	 */
	private void processTaskListForXLS(
			TaskListGeoJson tl, 
			Sheet sheet,
			Sheet settingsSheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> cols,
			String tz) throws IOException {
		
		DataFormat format = wb.createDataFormat();
		CellStyle styleTimestamp = wb.createCellStyle();
		ZoneId timeZoneId = ZoneId.of(tz);
		ZoneId gmtZoneId = ZoneId.of("GMT");
		
		styleTimestamp.setDataFormat(format.getFormat("yyyy-mm-dd h:mm"));	
		
		for(TaskFeature feature : tl.features)  {
			
			TaskProperties props = feature.properties;
				
			Row row = sheet.createRow(rowNumber++);
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);			
				Cell cell = row.createCell(i);

				if(col.name.equals("from") || col.name.equals("to")) {
					cell.setCellStyle(styleTimestamp);
					LocalDateTime gmtDate = col.getDateValue(props).toLocalDateTime();
					ZonedDateTime gmtZoned = ZonedDateTime.of(gmtDate, gmtZoneId);
					ZonedDateTime localZoned = gmtZoned.withZoneSameInstant(timeZoneId);
					Timestamp ts = new Timestamp(localZoned.getLong(ChronoField.INSTANT_SECONDS) * 1000L);
					cell.setCellValue(ts);
				} else {
					cell.setCellStyle(styles.get("default"));	
					cell.setCellValue(col.getValue(props));
				}
	        }	
		}
		
		// Populate settings sheet
		Row settingsRow = settingsSheet.createRow(0);
		Cell k = null;
		Cell v = null;
		k = settingsRow.createCell(0);
		k.setCellValue("Time Zone:");
		v = settingsRow.createCell(1);
		v.setCellValue(tz);
	}

}
