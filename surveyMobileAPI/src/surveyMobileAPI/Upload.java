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

package surveyMobileAPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.NotFoundException;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.server.entities.MissingTemplateException;

import exceptions.SurveyBlockedException;


/*
 * Accept submitted surveys
 */
@Path("/submission")
public class Upload extends Application {
	
	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(Upload.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Upload.class);
		return s;
	}
	
	private static final String OPEN_ROSA_VERSION_HEADER = "X-OpenRosa-Version";
	private static final String OPEN_ROSA_VERSION = "1.0";
	private static final String DATE_HEADER = "Date";
	
	private static final String RESPONSE_MSG1 = 
		"<OpenRosaResponse xmlns=\"http://openrosa.org/http/response\">";
	private static final String RESPONSE_MSG2 = 
		"</OpenRosaResponse>";
	
	@Context UriInfo uriInfo;
	
	/*
	 * New Submission
	 * No Key - login required
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postInstance(
			@QueryParam("deviceID") String deviceId,
			@Context HttpServletRequest request) throws IOException {
		
		log.info("New submssion");
		return submission(request, null, null, deviceId);
	}
	
	/*
	 * Update
	 * No Key provided login required
	 */
	@POST
	@Path("/{instanceId}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postUpdateInstance(
			@Context HttpServletRequest request,
			@QueryParam("deviceID") String deviceId,
	        @PathParam("instanceId") String instanceId) throws IOException {
		
		log.info("Update submssion: " + instanceId);
		return submission(request, instanceId, null, deviceId);
	}
	
	/*
	 * New Submission
	 * Authentication key included
	 */
	@POST
	@Path("/key/{key}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postInstanceWithKey(
			@Context HttpServletRequest request,
			@QueryParam("deviceID") String deviceId,
			@PathParam("key") String key) throws IOException {
		
		log.info("New submssion with key");
		return submission(request, null, key, deviceId);
	}
	
	/*
	 * Update
	 * Key Provided
	 */
	@POST
	@Path("/key/{key}/{instanceId}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postUpdateInstanceWithKey(
			@Context HttpServletRequest request,
			@QueryParam("deviceID") String deviceId,
			@PathParam("key") String key,
	        @PathParam("instanceId") String instanceId) throws IOException {
		
		log.info("Update submssion with key: " + instanceId);
		return submission(request, instanceId, key, deviceId);
	}
	
	private Response submission(HttpServletRequest request,  String instanceId, String key, String deviceId) 
			throws IOException {
	
		Response response = null;
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		}

		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-Upload");
		
		/*
		 * Authenticate the user using either the user id associated with the key, if provided, or the
		 *  user id they provided on login
		 */
		String user = null;
		if(key != null) {
			try {
				user = GeneralUtilityMethods.getDynamicUser(connectionSD, key);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			user = request.getRemoteUser();
		}
		
		if(user == null) {
			log.info("Error: Attempting to upload results: user not found");
			throw new AuthorisationException();
		} else {
			a.isAuthorised(connectionSD, user);
			log.info("User: " + user);
		}
		
		try {
			if (connectionSD != null) {
				connectionSD.close();
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Failed to close connection", e);
		}
		// End Authorisation

		// Extract the data
		try {
			log.info("Upload Started ================= " + instanceId + " ==============");
			log.info("Url:" + request.getRequestURI());
			XFormData xForm = new XFormData();
			xForm.loadMultiPartMime(request, user, instanceId, deviceId);
			log.info("Server:" + request.getServerName());
			log.info("Info: Upload finished ---------------- " + instanceId + " ------------");
			
			response = Response.created(uriInfo.getBaseUri()).status(HttpServletResponse.SC_CREATED)
					.entity(RESPONSE_MSG1 + 	"<message>Upload Success</message>" + RESPONSE_MSG2)
					.type("text/xml")
					.header(OPEN_ROSA_VERSION_HEADER, OPEN_ROSA_VERSION).build();
					
		} catch (SurveyBlockedException e) {
			log.info(getErrorMessage(key, e.getMessage()));
			response = Response.status(Status.FORBIDDEN).entity(getErrorMessage(key, e.getMessage())).build();
		} catch (AuthorisationException e) {
			log.info(e.getMessage());
			response = Response.status(Status.UNAUTHORIZED).entity(getErrorMessage(key, e.getMessage())).build();
		} catch (NotFoundException e) {
			log.info(e.getMessage());
			response = Response.status(Status.NOT_FOUND).entity(getErrorMessage(key, e.getMessage())).build();
		} catch (MissingTemplateException e) {
			log.log(Level.SEVERE, "", e);
			response = Response.status(Status.NOT_FOUND).entity(getErrorMessage(key, e.getMessage())).build();
		} catch (Exception e) {
			log.log(Level.SEVERE, "", e);
			response = Response.status(Status.BAD_REQUEST).entity(getErrorMessage(key, e.getMessage())).build();
		}

		return response;
	}
	
	/*
	 * Format an error message
	 * If the submission was called with a key value set then respond with JSON
	 */
	String getErrorMessage(String key, String error) {
		String msg = error;
		
		if(key != null) {
			msg = "{" +
					"\"status\": \"error\"," +
					"\"message\": \"" + error + "\"" +
					"}";
		}
		return msg;
	}
	
	/*
	 * Head request to return the actual URL to submit data to
	 * This is required by the Java Rosa protocol
	 */
		@HEAD
		@Produces(MediaType.TEXT_XML)
		public void getHead(@Context HttpServletRequest request,  @Context HttpServletResponse resp) {
		
			String url = request.getScheme() + "://" + request.getServerName() + "/submission";
			
			log.info("URL:" + url); 
			resp.setHeader("location", url);
			resp.setHeader(OPEN_ROSA_VERSION_HEADER,  OPEN_ROSA_VERSION);
			resp.setStatus(HttpServletResponse.SC_OK);
			
		}
}

