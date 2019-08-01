package org.smap.sdal.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.model.FileDescription;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.QueryForm;
import org.smap.sdal.model.SqlDesc;
import org.smap.sdal.model.Survey;

/*****************************************************************************

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

 ******************************************************************************/

public class PDFReportsManager {

	private static Logger log =
			Logger.getLogger(PDFReportsManager.class.getName());

	LogManager lm = new LogManager();		// Application log

	// Global values set in constructor
	private ResourceBundle localisation;
	private Survey survey;
	
	// Other global values
	int languageIdx = 0;

	boolean mExcludeEmpty = false;
	

	
	public PDFReportsManager(ResourceBundle l) {
		localisation = l;
	}
	
	/*
	 * Create the PDF Report
	 */
	public Response getReport(
			Connection sd,
			Connection cResults,
			String username,
			HttpServletRequest request,
			HttpServletResponse response,
			int sId, 
			String filename, 
			boolean landscape, 
			String language,
			Date startDate,
			Date endDate,
			int dateId,
			String filter) throws Exception {
		
		Response responseVal = null;
		String basePath = GeneralUtilityMethods.getBasePath(request);
		SurveyManager sm = new SurveyManager(localisation, "UTC");
		PreparedStatement pstmt = null;
		ZipOutputStream zos = null;
		
		String tz = "UTC";		// Default to UTC
		File folder = null;
		ArrayList<FileDescription> files = new ArrayList<> ();
		
		try {
					
			/*
			 * Get the sql
			 */
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			QueryManager qm = new QueryManager();	
			ArrayList<QueryForm> queryList = null;
			queryList = qm.getFormList(sd, sId, f.id);		// Get a form list for this survey / form combo

			QueryForm startingForm = qm.getQueryTree(sd, queryList);	// Convert the query list into a tree
			String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";	
			SqlDesc sqlDesc = QueryGenerator.gen(
					sd, 
					cResults,
					localisation,
					sId,
					f.id,
					language, 
					"pdf", 
					urlprefix,
					true,
					true,
					false,
					null,
					false,
					false,			// suid
					request.getServerName().toLowerCase(),
					null,
					null,
					username,
					startDate,
					endDate,
					dateId,
					false,			// superUser - Always apply filters
					startingForm,
					filter,
					null,			// transform
					true,
					true,
					tz);		// Include the keys instanceid, instancename, prikey, hrk
			
			pstmt = cResults.prepareStatement(sqlDesc.sql);
			log.info("Get records to convert to PDF's: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());
			folder = new File(filePath);
			folder.mkdir();
		
			while(rs.next()) {
				String instanceId = rs.getString("instanceid");
				
				// Get a name for the pdf file
				String name = null;
				try {
					name = rs.getString("instancename");		// Try the instance name
				} catch(Exception e) {}
				if(name == null || name.trim().length() == 0) {
					try {
						name = rs.getString("_hrk");					// Then try the HRK
					} catch(Exception e) {}
				}
				if(name == null || name.trim().length() == 0) {
					name = "r";									// Then, if there is still no name, Use the primary key
				}
				String escapedName = null;
				try {
					escapedName = URLDecoder.decode(name, "UTF-8");
					escapedName = URLEncoder.encode(escapedName, "UTF-8");
				} catch (Exception e) {
					log.log(Level.SEVERE, "Encoding pdf name Error", e);
				}
				escapedName = escapedName.replace("+", " "); // Spaces ok for file name within quotes
				escapedName = escapedName.replace("%2C", ","); // Commas ok for file name within quotes
				
				name = escapedName + rs.getString("prikey") + ".pdf";					// Add the primary key to guarantee uniqueness

				// Write the pdf to a temporary file
	 			Survey survey = sm.getById(sd, cResults, username, sId, true, basePath, 
						instanceId, true, false, true, false, true, "real", 
						false, false, true, "geojson",
						false,			// Don't get child surveys
						false			// launched only
						);				
				PDFSurveyManager pm = new PDFSurveyManager(localisation, sd, cResults, survey, username, tz);
				
				String tempFilePath = filePath + "/" + name;
				File tempFile = new File(tempFilePath);
				FileOutputStream tempFileStream = new FileOutputStream(tempFile);
				pm.createPdf(tempFileStream, 
						basePath, 
						urlprefix, 
						username, 
						language, 
						false, 
						filename, 
						landscape, 
						response);
				tempFileStream.close();
				files.add(new FileDescription(name, tempFilePath));
			}
			
			GeneralUtilityMethods.setFilenameInResponse(filename + ".zip", response);
			response.setHeader("Content-type",  "application/octet-stream; charset=UTF-8");
			
			zos = new ZipOutputStream(response.getOutputStream());
			GeneralUtilityMethods.writeFilesToZipOutputStream(zos, files);		// zos clased in call
			
		} finally {
			if(pstmt != null) {try {pstmt.close();} catch(Exception e) {}}
			
			// Clean up files
			for (FileDescription fd : files) {
				File f = new File(fd.path);
				try{f.delete();} catch(Exception e) {}
			}
			if(folder != null) {try {folder.delete();} catch(Exception e) {}}
		}
		
		return responseVal;
	}


}


