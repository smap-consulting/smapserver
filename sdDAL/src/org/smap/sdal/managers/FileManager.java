package org.smap.sdal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;

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

public class FileManager {
	
	private static Logger log =
			 Logger.getLogger(FileManager.class.getName());
	
	public FileManager() {
	}
	
	
	/*
	 * Get the file at the organisation level
	 */
	public Response getOrganisationFile(
			Connection sd,
			HttpServletRequest request, 
			HttpServletResponse response, 
			String user, 
			int requestedOrgId, 
			String filename, boolean 
			settings,
			boolean isTemporaryUser) throws IOException {
		
		Response r = null;
		
		// Authorisation - Access
		
		log.info("Get File: " + filename + " for organisation: " + requestedOrgId);

		String basepath = GeneralUtilityMethods.getBasePath(request);
		String filepath = basepath + "/media/organisation/" + requestedOrgId + (settings ? "/settings/" : "/") + filename;
		getFile(response, filepath, filename);
			
		r = Response.ok("").build();
		
		return r;
	}
	
	/*
	 * Add the file to the response stream
	 */
	public void getFile(HttpServletResponse response, String filepath, String filename) throws IOException {
		
		File f = new File(filepath);
		response.setContentType(UtilityMethodsEmail.getContentType(filename));
			
		response.addHeader("Content-Disposition", "attachment; filename=" + filename);
		response.setContentLength((int) f.length());
			
		FileInputStream fis = new FileInputStream(f);
		OutputStream responseOutputStream = response.getOutputStream();
			
		int bytes;
		while ((bytes = fis.read()) != -1) {
			responseOutputStream.write(bytes);
		}
		responseOutputStream.flush();
		responseOutputStream.close();
		fis.close();


	}

}
