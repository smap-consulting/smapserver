package surveyKPI;



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

import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.FileManager;

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
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		a = new Authorise(authorisations, null);	
	}
	
	@GET
	@Path("/organisation")
	@Produces("application/x-download")
	public Response getOrganisationFileUser (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@QueryParam("settings") boolean settings,
			@QueryParam("org") int requestedOrgId) throws Exception {
		
		return getOrganisationFile(request, response, request.getRemoteUser(), requestedOrgId, filename, settings, false);
	}
	
	/*
	 * Get file authenticated with a key
	 */
	@GET
	@Produces("application/x-download")
	@Path("/organisation/key/{key}")
	public Response getOrganisationFilekey(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("key") String key,
			@QueryParam("settings") boolean settings,
			@QueryParam("org") int requestedOrgId) throws SQLException {
		
		String user = null;		
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Get File Key");
		
		log.info("Getting file authenticated with a key");
		try {
			user = GeneralUtilityMethods.getDynamicUser(connectionSD, key);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection("surveyKPI-Get File Key", connectionSD);
		}
		
		if (user == null) {
			log.info("User not found for key");
			throw new AuthorisationException();
		}
		return getOrganisationFile(request, response, user, requestedOrgId, filename, settings, false);
	}
	
	/*
	 * Get file for anonymous user
	 */
	@GET
	@Produces("application/x-download")
	@Path("/organisation/user/{ident}")
	public Response getOrganisationFileAnon(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("ident") String user) throws SQLException {
				
		log.info("Getting file authenticated with a key");
		return getOrganisationFile(request, response, user, 0, filename, false, true);
	}
	
	@GET
	@Path("/users")
	@Produces("application/x-download")
	public Response getUsersFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@QueryParam("type") String type) throws Exception {
		
		int uId = 0;
		Response r = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("Get Users File");	
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
			FileManager fm = new FileManager();
			fm.getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.info("Error getting file:" + e.getMessage());
			r = Response.serverError().build();
		} finally {	
			SDDataSource.closeConnection("Get Users File", connectionSD);	
		}
		
		return r;
	}
	
	/*
	 * Get template pdf file
	 */
	@GET
	@Path("/surveyPdfTemplate/{sId}")
	@Produces("application/x-download")
	public Response getPdfTemplateFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,			
			@PathParam("filename") String filename,
			@PathParam("sId") int sId,
			@QueryParam("archive") boolean archive,
			@QueryParam("recovery") boolean recovery) throws Exception {
		
		log.info("Get PDF Template File:  for survey: " + sId);
		
		Response r = null;
	
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("Get Survey File");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidDelSurvey(sd, request.getRemoteUser(), sId, superUser);
		// End Authorisation 
		
		try {
			String basepath = GeneralUtilityMethods.getBasePath(request);
			
			if(!archive) {
				// Ignore the provided filename, get the filename from the survey details
				String displayName = GeneralUtilityMethods.getSurveyName(sd, sId);
				filename = GeneralUtilityMethods.getSafeTemplateName(displayName);
				if(recovery) {
					filename += "__prev___template.pdf";
				} else {
					filename += "_template.pdf";
				}
			}
			
			int pId = GeneralUtilityMethods.getProjectId(sd, sId);
			String folderPath = basepath + "/templates/" + pId ;						
			String filepath = folderPath + "/" + filename;
			
			FileManager fm = new FileManager();
			fm.getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Error getting file", e);
			r = Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {	
			SDDataSource.closeConnection("Get Survey File", sd);	
		}
		
		return r;
	}
	
	/*
	 * Get survey level resource file
	 */
	@GET
	@Path("/survey/{sId}")
	@Produces("application/x-download")
	public Response getSurveyFile (
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("sId") int sId,
			@QueryParam("linked") boolean linked) throws Exception {
		
		log.info("Get File: " + filename + " for survey: " + sId);
		
		Response r = null;

		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("Get Survey File");
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
			String filepath = basepath + "/media/" + sIdent+ "/";
			if(filename.startsWith("linked_s") || filename.startsWith("linked_s_pd_s") || filename.startsWith("chart_s")) {
				filepath += request.getRemoteUser() + "/";
			}
			filepath += filename;
			
			FileManager fm = new FileManager();
			fm.getFile(response, filepath, filename);
			
			r = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Error getting file", e);
			r = Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {	
			SDDataSource.closeConnection("Get Survey File", connectionSD);	
		}
		
		return r;
	}
	

	

	
	/*
	 * Get the file at the organisation level
	 */
	private Response getOrganisationFile(
			HttpServletRequest request, 
			HttpServletResponse response, 
			String user, 
			int requestedOrgId, 
			String filename, boolean 
			settings,
			boolean isTemporaryUser) {
		
		int oId = 0;
		Response r = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("Get Organisation File");	
		if (isTemporaryUser) {
			a.isValidTemporaryUser(connectionSD, user);
		}
		a.isAuthorised(connectionSD, user);		
		try {		
			oId = GeneralUtilityMethods.getOrganisationId(connectionSD, user);
		} catch(Exception e) {
			// ignore error
		}
		if(requestedOrgId > 0 && requestedOrgId != oId) {
			aOrg.isAuthorised(connectionSD, user);	// Must be org admin to work on another organisations data
			oId = requestedOrgId;
		}
		// End Authorisation 
		
		
		log.info("Get File: " + filename + " for organisation: " + oId);
		try {
			
			FileManager fm = new FileManager();
			r = fm.getOrganisationFile(connectionSD, request, response, user, oId, filename, settings, isTemporaryUser);
			
		}  catch (Exception e) {
			log.info("Error getting file:" + e.getMessage());
			r = Response.status(Status.NOT_FOUND).build();
		} finally {	
			SDDataSource.closeConnection("Get Organisation File", connectionSD);	
		}
		
		return r;
	}

}
