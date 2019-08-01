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

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.AR;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;


/*
 * Manage exporting of data posted from a data table
 */

public class XLSXAdminReportsNotifications {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	ResourceBundle localisation = null;
	
	CellStyle good = null;
	CellStyle bad = null;
	
	public XLSXAdminReportsNotifications(ResourceBundle l) {
		localisation = l;
	}

	/*
	 * Create the new style XLSX report
	 */
	public Response getNewReport(
			Connection sd,
			HttpServletRequest request,
			HttpServletResponse response,
			String filename,
			int oId) {
		
		Response responseVal = null;

		String escapedFileName = null;
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes

		Workbook wb = null;
		Sheet dataSheet = null;
		CellStyle errorStyle = null;
		int rowNumber = 0;
		int colNumber;

		PreparedStatement pstmt = null;
		
		try {
				
			/*
			 * Create XLSX File
			 */
			GeneralUtilityMethods.setFilenameInResponse(filename + "." + "xlsx", response); // Set file name
			wb = new SXSSFWorkbook(10);		// Serialised output
			dataSheet = wb.createSheet("data");
			
			Map<String, CellStyle> styles = XLSUtilities.createStyles(wb);
			CellStyle headerStyle = styles.get("header");
			errorStyle = styles.get("errorStyle");
			good = styles.get("good");
			bad = styles.get("bad");
			
			/*
			 * Get the data
			 */
			NotificationManager nm = new NotificationManager(localisation);
			ArrayList<Notification> nList = nm.getAllNotifications(sd, pstmt, oId);
								
			/*
			 * Add the headings 
			 */
			colNumber = 0;
			Row row = dataSheet.createRow(rowNumber++);		
			Cell cell = null;
			
			cell = row.createCell(colNumber++);	// Project
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("ar_project"));
			
			cell = row.createCell(colNumber++);	// Notification name
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("name"));
			
			cell = row.createCell(colNumber++);	// Survey
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("a_name"));
			
			cell = row.createCell(colNumber++);	// Trigger
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("a_trigger"));
			
			cell = row.createCell(colNumber++);	// Target
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("a_target"));
			
			cell = row.createCell(colNumber++);	// Details
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("a_details"));
			
			/*
			 * Add the data
			 */
			for(Notification n : nList) {
				colNumber = 0;
				
				row = dataSheet.createRow(rowNumber++);	
				
				cell = row.createCell(colNumber++);	// Project
				cell.setCellValue(n.project);
				
				cell = row.createCell(colNumber++);	// Notification name
				cell.setCellValue(n.name);
				
				cell = row.createCell(colNumber++);	// Survey
				cell.setCellValue(n.s_name);
				
				cell = row.createCell(colNumber++);	// Trigger
				cell.setCellValue(n.trigger);
				
				cell = row.createCell(colNumber++);	// Target
				cell.setCellValue(n.target);

				cell = row.createCell(colNumber++);	// Details
				StringBuffer details = new StringBuffer("");
				if(n.target.equals("email") && n.notifyDetails != null) {
					if(n.notifyDetails.emails != null && n.notifyDetails.emails.size() > 0) {
						for(String e : n.notifyDetails.emails) {
							if(details.length() > 0) {
								details.append(", ");
							}
							details.append(e);
						}
					}
					if(n.notifyDetails.emailQuestionName != null
							&& !n.notifyDetails.emailQuestionName.equals("-1")) {
						if(details.length() > 0) {
							details.append(", ");
						}
						details.append(localisation.getString("a_eq"));
					}
					if(n.notifyDetails.emailMeta != null
							&& !n.notifyDetails.emailMeta.equals("-1")) {
						if(details.length() > 0) {
							details.append(", ");
						}
						details.append(localisation.getString("a_eq2"));
					}
				} else if(n.target.equals("forward")) {
					details.append(n.remote_host).append(" : ").append(n.remote_s_name);
				} else if(n.target.equals("sms") && n.notifyDetails != null) {
					if(n.notifyDetails.emails != null && n.notifyDetails.emails.size() > 0) {
						details.append(localisation.getString("a_sms_1"));
						int count = 0;
						for(String e : n.notifyDetails.emails) {
							if(count++ > 0) {
								details.append(", ");
							}
							details.append(e);
						}
					}
					if(n.notifyDetails.emailQuestionName != null
							&& !n.notifyDetails.emailQuestionName.equals("-1")) {
						if(details.length() > 0) {
							details.append(", ");
						}
						details.append(localisation.getString("a_sms_2"));
					}
				}

				cell.setCellValue(details.toString());			
			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
				
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				msg = localisation.getString("msg_no_data");
			}
			Row dataRow = dataSheet.createRow(rowNumber + 1);	
			Cell cell = dataRow.createCell(0);
			cell.setCellStyle(errorStyle);
			cell.setCellValue(msg);
			
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {	

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				OutputStream outputStream = response.getOutputStream();
				wb.write(outputStream);
				wb.close();
				outputStream.close();
				((SXSSFWorkbook) wb).dispose();		// Dispose of temporary files
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Error", ex);
			}
		}

		return responseVal;
	}

	private boolean setCellGood(Cell cell) {
		cell.setCellStyle(good);
		cell.setCellValue(localisation.getString("rep_yes"));
		return true;
	}
	
	private boolean setCellBad(Cell cell) {
		cell.setCellStyle(bad);
		cell.setCellValue(localisation.getString("rep_no"));
		return false;
	}
}
