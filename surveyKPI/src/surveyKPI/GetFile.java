package surveyKPI;

import java.io.File;
import java.io.FileInputStream;
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
import java.util.ArrayList;
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
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;

/*
 * Authorises the user and then
 * Downloads a file
 */

@Path("/file/{filename}")
public class GetFile extends Application {
	
	Authorise a = null;
	Authorise aOrg = new Authorise(null, Authorise.ORG);
	
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
	@Path("/organisation")
	@Produces("application/x-download")
	public Response getOrganisationFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@QueryParam("settings") boolean settings,
			@QueryParam("org") int requestedOrgId) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		int oId = 0;
		Response r = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("getFile");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		try {		
			oId = GeneralUtilityMethods.getOrganisationId(connectionSD, request.getRemoteUser());
		} catch(Exception e) {
			// ignore error
		}
		if(requestedOrgId > 0 && requestedOrgId != oId) {
			aOrg.isAuthorised(connectionSD, request.getRemoteUser());	// Must be org admin to work on another organisations data
			oId = requestedOrgId;
		}
		// End Authorisation 
		
		log.info("Get File: " + filename + " for organisation: " + oId);
		try {
			String basepath = GeneralUtilityMethods.getBasePath(request);
			String filepath = basepath + "/media/organisation/" + oId + (settings ? "/settings/" : "/") + filename;
			System.out.println("Getting file: " + filepath);
			getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.info("Error getting file:" + e.getMessage());
			r = Response.serverError().build();
		} finally {	
			SDDataSource.closeConnection("getFile", connectionSD);	
		}
		
		return r;
	}
	
	@GET
	@Path("/users")
	@Produces("application/x-download")
	public Response getUsersFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@QueryParam("type") String type) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		int uId = 0;
		Response r = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("getFile");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		try {		
			uId = GeneralUtilityMethods.getUserId(connectionSD, request.getRemoteUser());
		} catch(Exception e) {
			// ignore error
		}
		// End Authorisation 
		
		// Only allow valid categories of files
		if(type != null) {
			if(!type.equals("sig")) {
				type = null;
			}
		}
		
		log.info("Get File: " + filename + " for user: " + uId);
		try {
			String basepath = GeneralUtilityMethods.getBasePath(request);
			String filepath = basepath + "/media/users/" + uId + "/" + (type != null ? (type + "/") : "") + filename;
			log.info("Getting user file: " + filepath);
			getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.info("Error getting file:" + e.getMessage());
			r = Response.serverError().build();
		} finally {	
			SDDataSource.closeConnection("getFile", connectionSD);	
		}
		
		return r;
	}
	
	@GET
	@Path("/survey/{sId}")
	@Produces("application/x-download")
	public Response getSurveyFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("sId") int sId,
			@QueryParam("linked") boolean linked) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Get File: " + filename + " for survey: " + sId);
		
		Response r = null;

		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("getFile");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation 
		
		try {
			String basepath = GeneralUtilityMethods.getBasePath(request);
			String sIdent = GeneralUtilityMethods.getSurveyIdent(connectionSD, sId);
			String filepath = basepath + "/media/" + sIdent+ "/" + filename;
			System.out.println("Getting file: " + filepath + " linked is: " + linked);
			
			getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Error getting file", e);
			r = Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {	
			SDDataSource.closeConnection("getFile", connectionSD);	
		}
		
		return r;
	}
	
	
	/*
	 * Add the file to the response stream
	 */
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
