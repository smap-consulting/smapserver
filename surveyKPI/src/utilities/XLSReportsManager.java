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
import org.apache.poi.hssf.usermodel.HSSFHyperlink;
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
import org.smap.sdal.model.TableColumn;
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
	boolean isXLSX = false;
	int rowNumber = 1;		// Heading row is 0
	
	private class Column {
		String name;
		String human_name;
		int dataIndex;
		int colIndex;
		String type;
		
		public Column(ResourceBundle localisation, int dataIndex, String n, String type, int colIndex) {
			this.dataIndex = dataIndex;
			this.colIndex = colIndex;
			this.type = type;
			name = n;
			//human_name = localisation.getString(n);
			human_name = n;		// Need to work out how to use translations when the file needs to be imported again
		}
		
		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			return width;
		}
	}

	public XLSReportsManager() {

	}
	
	public XLSReportsManager(String type) {
		if(type != null && type.equals("xls")) {
			wb = new HSSFWorkbook();
			isXLSX = false;
		} else {
			wb = new XSSFWorkbook();
			isXLSX = true;
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
		
		Sheet dataSheet = wb.createSheet("data");
		Sheet taskSettingsSheet = wb.createSheet("settings");
		//taskListSheet.createFreezePane(3, 1);	// Freeze header row and first 3 columns
		
		Map<String, CellStyle> styles = createStyles(wb);

		ArrayList<Column> cols = getColumnList(mfc, dArray, localisation);
		createHeader(cols, dataSheet, styles);	
		processDataListForXLS(dArray, dataSheet, taskSettingsSheet, styles, cols, tz);
		
		System.out.println("Writing XLS");
		wb.write(outputStream);
		outputStream.close();
	}
	
	
	/*
	 * Get the columns for the data sheet
	 */
	private ArrayList<Column> getColumnList(ManagedFormConfig mfc, 
			ArrayList<ArrayList<KeyValue>> dArray, 
			ResourceBundle localisation) {
		
		ArrayList<Column> cols = new ArrayList<Column> ();
		ArrayList<KeyValue> record = null;
		
		if(dArray.size() > 0) {
			 record = dArray.get(0);
		}
		
		int colIndex = 0;
		for(int i = 0; i < mfc.columns.size(); i++) {
			TableColumn tc = mfc.columns.get(i);
			if(!tc.hide && tc.include) {
				int dataIndex = -1;
				if(record != null) {
					dataIndex = getDataIndex(record, tc.humanName);
				}
				cols.add(new Column(localisation, dataIndex, tc.humanName, tc.type, colIndex++));
			}
		}
	
		
		return cols;
	}
	
	/*
	 * Get the index into the data set for a column
	 */
	private int getDataIndex(ArrayList<KeyValue> record, String name) {
		int idx = -1;
		
		for(int i = 0; i < record.size(); i++) {
			if(record.get(i).k.equals(name)) {
				idx = i;
				break;
			}
		}
		return idx;
	}
	
	
	/*
     * create a library of cell styles
     */
    private static Map<String, CellStyle> createStyles(Workbook wb){
        
    	Map<String, CellStyle> styles = new HashMap<String, CellStyle>();
		DataFormat format = wb.createDataFormat();

        CellStyle style = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        style.setFont(headerFont);
        styles.put("header", style);

        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("default", style);

		style = wb.createCellStyle();
		style.setDataFormat(format.getFormat("yyyy-mm-dd h:mm"));	
		styles.put("datetime", style);
        
		style = wb.createCellStyle();
		Font linkFont = wb.createFont();
		linkFont.setUnderline(Font.U_SINGLE);
	    linkFont.setColor(IndexedColors.BLUE.getIndex());
	    style.setFont(linkFont);
	    styles.put("link", style);
	      
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
			ArrayList<ArrayList<KeyValue>> dArray, 
			Sheet sheet,
			Sheet settingsSheet,
			Map<String, CellStyle> styles,
			ArrayList<Column> cols,
			String tz) throws IOException {

		ZoneId timeZoneId = ZoneId.of(tz);
		ZoneId gmtZoneId = ZoneId.of("GMT");	
		
		CreationHelper createHelper = wb.getCreationHelper();
		
		for(int index = 0; index < dArray.size(); index++) {
			
			Row row = sheet.createRow(rowNumber++);
			ArrayList<KeyValue> record = dArray.get(index);
			for(Column col : cols) {
				Cell cell = row.createCell(col.colIndex);
				String value = record.get(col.dataIndex).v;
				
				cell.setCellStyle(styles.get("default"));	
				
				if(value != null && (value.startsWith("https://") || value.startsWith("http://"))) {
					cell.setCellStyle(styles.get("link"));
					if(isXLSX) {
						XSSFHyperlink url = (XSSFHyperlink)createHelper.createHyperlink(Hyperlink.LINK_URL);
						url.setAddress(value);
						cell.setHyperlink(url);
					} else {
						HSSFHyperlink url = new HSSFHyperlink(HSSFHyperlink.LINK_URL);
						url.setAddress(value);
						cell.setHyperlink(url);
					}
					
				}
				
				cell.setCellValue(value);
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
