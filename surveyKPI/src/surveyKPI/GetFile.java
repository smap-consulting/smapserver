package surveyKPI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.PDFManager;

/*
 * Downloads a file
 */

@Path("/file/{filename}")
public class GetFile extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(GetFile.class.getName());

	public GetFile() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		a = new Authorise(authorisations, null);	
	}
	
	@GET
	@Path("/organisation/{oId}")
	@Produces("application/x-download")
	public Response getOrganisationFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("oId") int oId) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Get File: " + filename + " for organisation: " + oId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("getFile");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidOrganisation(connectionSD, request.getRemoteUser(), oId);
		// End Authorisation 
		
		try {
			String basepath = GeneralUtilityMethods.getBasePath(request);
			String filepath = basepath + "/media/organisation/" + oId + "/" + filename;
			System.out.println("Getting file: " + filepath);
			getFile(response, filepath, filename);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {	
			SDDataSource.closeConnection("getFile", connectionSD);	
		}
		return Response.ok("").build();
	}
	
	private void getFile(HttpServletResponse response, String filepath, String filename) throws IOException {
		
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
		fis.close();
	}

}
