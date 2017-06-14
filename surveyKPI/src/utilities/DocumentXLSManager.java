package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

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
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

//import org.apache.poi.hssf.usermodel.HSSFWorkbook;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFHyperlink;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.TableColumn;
import surveyKPI.ExportSurveyXls;

public class DocumentXLSManager {
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyXls.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	XSSFWorkbook wb = null;

	
	
	public DocumentXLSManager() {
	
	}
	
	HashMap<String, String> surveyNames = null;
	
	public void create(
			Connection sd,
			String remoteUser,
			ArrayList<KeyValue> data,
			OutputStream outputStream,
			String basePath,
			int oId
			) throws Exception {
		
		FileInputStream templateIS = null;
		String templateName = "ewarn_report_template.xlsx";
		File templateFile = GeneralUtilityMethods.getDocumentTemplate(basePath, "ewarn_report_template.xlsx", oId);
		try {
			lm.writeLog(sd, 0, remoteUser, "error", "Failed to open template: " + templateName);
			templateIS = new FileInputStream(templateFile);
		} catch (Exception e) {
			throw e;
		}
		wb = new XSSFWorkbook(templateIS);
		templateIS.close();
		
		Sheet sheet = wb.getSheetAt(0);
		for(KeyValue kv : data) {
			int namedCellIdx = wb.getNameIndex(kv.k);
		    Name aNamedCell = wb.getNameAt(namedCellIdx);

		    AreaReference aref = new AreaReference(aNamedCell.getRefersToFormula());
		    CellReference[] crefs = aref.getAllReferencedCells();
		    System.out.println("    xxxxxxx: " + crefs.length + " " + kv.k + " : " + kv.v);
		    for (int i = 0; i < crefs.length; i++) {
		        Sheet s = wb.getSheet(crefs[i].getSheetName());
		        Row r = sheet.getRow(crefs[i].getRow());
		        Cell cell = r.getCell(crefs[i].getCol());
		        // extract the cell contents based on cell type etc.
		        cell.setCellValue(new Double(kv.v));
		    }
			
		}
		XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
		wb.write(outputStream);
		wb.write(outputStream);
		outputStream.close();
	
	}
	


}
