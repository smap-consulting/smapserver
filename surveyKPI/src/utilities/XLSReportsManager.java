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
import org.smap.sdal.model.KeyValue;
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
import org.smap.sdal.model.ManagedFormConfig;
import org.w3c.dom.Element;

import com.google.gson.JsonElement;


/*
 * Manage exporting of data posted from a data table
 */

public class XLSReportsManager {
	
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

	public XLSReportsManager() {

	}
	
	public XLSReportsManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
		} else {
			wb = new XSSFWorkbook();
		}
	}
	
	/*
	 * Write a data array to an XLS file
	 */
	public void createXLSReportsFile(OutputStream outputStream, 
			ArrayList<ArrayList<KeyValue>> dArray, 
			ManagedFormConfig mfc,
			ResourceBundle localisation, 
			String tz) throws IOException {
		
		Sheet taskListSheet = wb.createSheet("tasks");
		Sheet taskSettingsSheet = wb.createSheet("settings");
		//taskListSheet.createFreezePane(3, 1);	// Freeze header row and first 3 columns
		
		Map<String, CellStyle> styles = createStyles(wb);

		ArrayList<Column> cols = getColumnList(localisation);
		//createHeader(cols, taskListSheet, styles);	
		//processDataListForXLS(tl, taskListSheet, taskSettingsSheet, styles, cols, tz);
		
		System.out.println("Writing XLS");
		wb.write(outputStream);
		outputStream.close();
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
		cols.add(new Column(localisation, colNumber++, "email"));
		cols.add(new Column(localisation, colNumber++, "address"));
		cols.add(new Column(localisation, colNumber++, "lon"));
		cols.add(new Column(localisation, colNumber++, "lat"));
		
		
		return cols;
	}
	
	/*
	 * Get the columns for the settings sheet
	 */
	private ArrayList<Column> getLocationColumnList(ResourceBundle localisation) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		
		int colNumber = 0;
	
		cols.add(new Column(localisation, colNumber++, "UID"));
		cols.add(new Column(localisation, colNumber++, "tagName"));
		
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
	private void processDataListForXLS(
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
			
			/*
			for(int i = 0; i < dArray.size(); i++) {
				ArrayList<KeyValue> record = dArray.get(i);
				for(int j = 0; j < record.size(); j++) {
					KeyValue col = record.get(j);

				}
			}
			*/
			
			Row row = sheet.createRow(rowNumber++);
			for(int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);			
				Cell cell = row.createCell(i);

				if(col.name.equals("from") || col.name.equals("to")) {
					cell.setCellStyle(styleTimestamp);
					
					if(col.getDateValue(props) != null) {
						LocalDateTime gmtDate = col.getDateValue(props).toLocalDateTime();
						ZonedDateTime gmtZoned = ZonedDateTime.of(gmtDate, gmtZoneId);
						ZonedDateTime localZoned = gmtZoned.withZoneSameInstant(timeZoneId);
						LocalDateTime localDate = localZoned.toLocalDateTime();
						Timestamp ts2 = Timestamp.valueOf(localDate);
						cell.setCellValue(ts2);
					}

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
	
	/*
	 * add a location to XLS
	 */
	private void addLocation(
			
		Location l, 
		Sheet sheet,
		Map<String, CellStyle> styles,
		Map<String, Integer> rowMap) throws IOException {
		
		int groupRow = rowMap.get(l.group);
		
		Row row = sheet.createRow(groupRow++);
		rowMap.put(l.group, groupRow);
		
		Cell cell = row.createCell(0);
		cell.setCellStyle(styles.get("default"));	
		cell.setCellValue(l.uid);
	    
		cell = row.createCell(1);
		cell.setCellStyle(styles.get("default"));	
		cell.setCellValue(l.name);

	
	}

}
