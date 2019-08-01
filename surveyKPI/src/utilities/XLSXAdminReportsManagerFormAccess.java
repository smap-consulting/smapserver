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
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.AR;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;


/*
 * Manage exporting of data posted from a data table
 */

public class XLSXAdminReportsManagerFormAccess {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	ResourceBundle localisation = null;
	
	CellStyle good = null;
	CellStyle bad = null;
	
	public XLSXAdminReportsManagerFormAccess(ResourceBundle l) {
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
			int oId,
			String formIdent) {
		
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
		int colNumber = 0;
		int hasAccessCol = 0;
		int hasNoAccessReasonCol = 0;

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
			 * Write the overview data
			 */
			SurveyManager sm = new SurveyManager(localisation, "UTC");
			int sId = GeneralUtilityMethods.getSurveyId(sd, formIdent);
			Survey survey = sm.getById(
					sd, 
					null, 		// cResults
					request.getRemoteUser(), 
					sId, 
					true,		// full details
					null, 		// basePath
					null, 		// instance id
					false, 		// get results
					false, 		// generate dummmy values
					false, 		// get property type 
					true, 		// get soft deleted
					false, 		// get hrk
					"internal",	// get external options 
					false, 		// get changed history
					true, 		// get roles
					true, 		// Pretend to be super user
					null	,		// geom format
					false,		// Child surveys
					false		// launched only
					);
			/*
			 * Write the form overview
			 */	
			Row row = dataSheet.createRow(rowNumber++);		
			
			Cell cell = row.createCell(0);	// Ident
			cell.setCellValue(localisation.getString("rep_form_ident"));
			cell = row.createCell(1);	
			cell.setCellValue(formIdent);
			
			row = dataSheet.createRow(rowNumber++);		
			cell = row.createCell(0);	// Found
			cell.setCellValue(localisation.getString("rep_found"));
			cell = row.createCell(1);	
			if(survey != null) {
				cell.setCellStyle(good);
				cell.setCellValue(localisation.getString("rep_yes"));
			} else {
				cell.setCellStyle(bad);
				cell.setCellValue(localisation.getString("rep_no"));
			}
			
			if(survey != null) {
				row = dataSheet.createRow(rowNumber++);		
				cell = row.createCell(0);	// Survey Name
				cell.setCellValue(localisation.getString("name"));
				cell = row.createCell(1);	
				cell.setCellValue(survey.displayName);
				
				row = dataSheet.createRow(rowNumber++);		
				cell = row.createCell(0);	// Project Name
				cell.setCellValue(localisation.getString("ar_project"));
				cell = row.createCell(1);	
				cell.setCellValue(survey.projectName);
			}
			
			/*
			 * Add the headings 
			 */
			colNumber = 0;
			Row preHeaderRow = dataSheet.createRow(rowNumber++);	
			row = dataSheet.createRow(rowNumber++);	
			
			cell = row.createCell(colNumber++);	// User Ident
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("ar_ident"));
			
			cell = row.createCell(colNumber++);	// User Name
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("ar_user_name"));
			
			cell = row.createCell(colNumber++);	// Has Access
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_has_access"));
			
			cell = row.createCell(colNumber++);	// No Access reason
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_reason"));
			
			cell = row.createCell(colNumber++);	// In Organisation
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_current_org"));
			
			cell = row.createCell(colNumber++);	// Has Project
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_has_project"));
			
			// Add a marker that security groups have stated
			cell = preHeaderRow.createCell(colNumber);	
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_sec_groups"));
			
			cell = row.createCell(colNumber++);	// Admin
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_admin"));
			
			cell = row.createCell(colNumber++);	// Analyst
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_analyst"));
			
			cell = row.createCell(colNumber++);	// Enum
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_enum"));
			
			cell = row.createCell(colNumber++);	// View Data
			cell.setCellStyle(headerStyle);
			cell.setCellValue(localisation.getString("rep_view"));
			
			int idx = 0;
			for(String roleName : survey.roles.keySet()) {	// Role
				if(idx++ == 0) {
					// Add a marker that roles have stated
					cell = preHeaderRow.createCell(colNumber);	
					cell.setCellStyle(headerStyle);
					cell.setCellValue(localisation.getString("rep_roles"));
				}
				cell = row.createCell(colNumber++);	
				cell.setCellStyle(headerStyle);
				cell.setCellValue(roleName);
			}
			
			/*
			 * Process the users
			 */
			UserManager um = new UserManager(localisation);
			ArrayList<User> users = um.getUserList(sd, oId, true, true);	
			for(User u : users) {
				colNumber = 0;
				boolean hasProject = false;
				boolean isInOrg = false;
				boolean hasAdmin = false;
				boolean hasAnalyst = false;
				boolean hasEnum = false;
				boolean hasView = false;
				
				row = dataSheet.createRow(rowNumber++);	
				
				cell = row.createCell(colNumber++);	// User Ident
				cell.setCellValue(u.ident);
				
				cell = row.createCell(colNumber++);	// User Name
				cell.setCellValue(u.name);
				
				hasAccessCol = colNumber++;					// Come back to the overall yes/no has access
				hasNoAccessReasonCol = colNumber++;			// Come back to the reason for no access
				
				cell = row.createCell(colNumber++);	// Current Organisation
				isInOrg = u.current_org_id == survey.o_id;
				if(isInOrg ? setCellGood(cell) : setCellBad(cell));
				
				cell = row.createCell(colNumber++);	// Has Project
				for(Project p : u.projects) {
					if(p.id == survey.p_id) {
						hasProject = true;
						break;
					}
				}				
				if(hasProject ? setCellGood(cell) : setCellBad(cell));
				
				/*
				 * Add security group cells
				 */
				for(UserGroup ug : u.groups) {
					if(ug.id == Authorise.ADMIN_ID) {
						hasAdmin = true;
					} else if(ug.id == Authorise.ANALYST_ID) {
						hasAnalyst = true;
					} else if(ug.id == Authorise.ENUM_ID) {
						hasEnum = true;
					} else if(ug.id == Authorise.VIEW_DATA_ID) {
						hasView = true;
					}
				}	
				cell = row.createCell(colNumber++);	// Has Admin
				if(hasAdmin ? setCellGood(cell) : setCellBad(cell));
				cell = row.createCell(colNumber++);	// Has Analyst
				if(hasAnalyst ? setCellGood(cell) : setCellBad(cell));
				cell = row.createCell(colNumber++);	// Has Enum
				if(hasEnum ? setCellGood(cell) : setCellBad(cell));
				cell = row.createCell(colNumber++);	// Has View
				if(hasView ? setCellGood(cell) : setCellBad(cell));
				
				boolean hasRole = false;
				if(survey.roles.size() == 0) {
					hasRole = true;		// No roles to worry about
				} else {
					for(String roleName : survey.roles.keySet()) {	// Role
						boolean hasThisRole = false;
						for(Role r : u.roles) {
							if(r.name.equals(roleName)) {
								hasThisRole = true;
								hasRole = true;
								break;
							}
						}
						cell = row.createCell(colNumber++);	// Role
						if(hasThisRole ? setCellGood(cell) : setCellBad(cell));
					}
				}
				
				/*
				 * Set the overall assessment of whether or not the user has access
				 */
				cell = row.createCell(hasAccessCol);
				boolean hasAccess = 
						isInOrg && 
						hasProject &&
						(hasAdmin || hasAnalyst || hasEnum || hasView) &&
						hasRole;
				if(hasAccess ? setCellGood(cell) : setCellBad(cell));
				
				/*
				 * Set the reason for no access
				 */
				if(!hasAccess) {
					StringBuffer reason = new StringBuffer("");
					if(!isInOrg) {
						reason.append(localisation.getString("rep_reason_org")).append(". ");
					}
					if(!hasProject) {
						reason.append(localisation.getString("rep_reason_project")).append(". ");
					}
					if(!hasAdmin && !hasAnalyst && !hasEnum && !hasView) {
						reason.append(localisation.getString("rep_reason_sec_group")).append(". ");
					}
					if(!hasRole) {
						reason.append(localisation.getString("rep_reason_role")).append(". ");
					}
					cell = row.createCell(hasNoAccessReasonCol);
					cell.setCellValue(reason.toString());
				}
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
