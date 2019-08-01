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

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import model.Remote;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.SubmissionMessage;
import org.smap.sdal.model.XformsJavaRosa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Get the notifications set for this project
 */

@Path("/notifications")
public class NotificationList extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(NotificationList.class.getName());
	
	public NotificationList() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);		// Enumerators with MANAGE access can process managed forms
		a = new Authorise(authorisations, null);		
	}
	
	@Path("/{projectId}")
	@GET
	public Response getNotifications(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId) { 
		
		Response response = null;
		String connectionString = "surveyKPI-Notifications";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			NotificationManager nm = new NotificationManager(localisation);
			ArrayList<Notification> nList = nm.getProjectNotifications(sd, pstmt, request.getRemoteUser(), projectId);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(nList);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;

	}
	
	@Path("/types")
	@GET
	public Response getTypes(@Context HttpServletRequest request) { 
		
		Response response = null;
		String connectionString = "surveyKPI-NotificationList-getTypes";
		
		// No Authorisation required
		
		Connection sd = SDDataSource.getConnection(connectionString);	
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			NotificationManager fm = new NotificationManager(localisation);
			ArrayList<String> tList = fm.getNotificationTypes(sd, request.getRemoteUser());
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(tList);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;

	}
	
	
	/*
	 * Get remote surveys that can be used as the target of a forward
	 */
	@Path("/getRemoteSurveys")
	@POST
	public Response getRemoteSurveys(@Context HttpServletRequest request,
			@FormParam("remote") String remoteString) { 
		
		Response response = null;
		
		Type type = new TypeToken<Remote>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Remote r = gson.fromJson(remoteString, type);

		if(r == null) {
			response = Response.serverError().entity("Details for remote server not provided").build();
			return response;
		}
		
		// Remove trailing slashes
		if(r.address.endsWith("/")) {
			r.address = r.address.substring(0, r.address.length() -1);
		}
		
		String host = null;
		String protocol = null;
		int port;
		if(r.address.startsWith("https://")) {
			host = r.address.substring(8);
			port = 443;
			protocol = "https";
		} else if(r.address.startsWith("http://")) {
			host = r.address.substring(7);
			port = 80;
			protocol = "http";
		} else {
			response = Response.serverError().entity("Invalid server address: " + r.address).build();
			return response;
		}
		
		/*
		 * Call formList on remote host
		 */
		try {
		
			HttpHost target = new HttpHost(host, port, protocol);
		    CredentialsProvider credsProvider = new BasicCredentialsProvider();
		    credsProvider.setCredentials(
		                new AuthScope(target.getHostName(), target.getPort()),
		                new UsernamePasswordCredentials(r.user, r.password));
		    CloseableHttpClient httpclient = HttpClients.custom()
		                .setDefaultCredentialsProvider(credsProvider)
		                .build();

            HttpClientContext localContext = HttpClientContext.create();
		    
			HttpGet httpget = new HttpGet(r.address + "/formList");
			httpget.addHeader("accept", "text/xml");
			httpget.addHeader("X-OpenRosa-Version", "1.1");
			
			log.info("Executing request " + httpget.getRequestLine() + " to target " + target);
          
			XformsJavaRosa fList = null;
			String resp = null;
            CloseableHttpResponse rem_response = httpclient.execute(target, httpget, localContext);
            int status = rem_response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
	            HttpEntity entity = rem_response.getEntity();
	            if(entity != null) {
	            	JAXBContext ctx = JAXBContext.newInstance(XformsJavaRosa.class);
	            	
	            	String rs = EntityUtils.toString(entity,"UTF8");
	          
	        		Unmarshaller um = ctx.createUnmarshaller();
	        		um.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler());
	        		fList =  (XformsJavaRosa) um.unmarshal(new StringReader(rs));
	        		
	        		
	            }
            } else if (status == 401 ){
            	throw new AuthorisationException();
            } else {
            	throw new ClientProtocolException("Failed : HTTP error code : "
      				   + rem_response.getStatusLine().getStatusCode());
            }
                
            if(fList != null) {
            	System.out.println("Length: " + fList.xform.size());
				Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
				resp = gsonResp.toJson(fList.xform);
            }
			
			response = Response.ok(resp).build();
			
		} catch (ClientProtocolException e) {
			log.log(Level.SEVERE,"Client Protocol Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} catch (IOException e) {
			log.log(Level.SEVERE,"IO Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} catch (JAXBException e) {
			log.log(Level.SEVERE,"JAXB Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} catch (AuthorisationException e) {
			log.log(Level.SEVERE,"Authorisation Exception", e);
			response = Response.status(500).entity("Unauthorised").build();	// Don't return a status of 401 as it will log the user out of their local server
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
		}
		
		return response;
	}
	
	/*
	 * Add a notifications
	 */
	@Path("/add")
	@POST
	public Response addNotification(@Context HttpServletRequest request,
			@FormParam("notification") String notificationString) { 
		
		Response response = null;
		String connectionString = "surveyKPI-Survey - add notification";
		
		Type type = new TypeToken<Notification>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Notification n = gson.fromJson(notificationString, type);
		
		log.info("Add Notification:========== " + notificationString);
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), n.s_id, false, superUser);
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {	
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			NotificationManager nm = new NotificationManager(localisation);
			
			// Validate
			if(n.target.equals("forward") && nm.isFeedbackLoop(sd, request.getServerName(), n)) {
				throw new Exception("Survey is being forwarded to itself");
			}
 
			nm.addNotification(sd, pstmt, request.getRemoteUser(), n);
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			String msg = e.getMessage();
			if(msg.contains("forwarddest")) {	// Unique key
				response = Response.serverError().entity("Duplicate forwarding address").build();
			} else {
				response = Response.serverError().entity("SQL Error").build();
				log.log(Level.SEVERE,"SQL Exception", e);
			}
		} catch (AuthorisationException e) {
			log.info("Authorisation Exception");
		    response = Response.serverError().entity("Not authorised").build();
		} catch (ApplicationException e) {
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			String msg = e.getMessage();
			log.info(msg);
			if(msg == null) {
				msg = "System Error";
			}
			if(msg != null && !msg.contains("forwarded to itself")) {
				msg = "System Error";
				log.log(Level.SEVERE,"Error", e);
			}
		    response = Response.serverError().entity(msg).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;

	}
	
	@Path("/update")
	@POST
	public Response updateNotification(@Context HttpServletRequest request,
			@FormParam("notification") String notificationString) { 
		
		Response response = null;
		String connectionString = "surveyKPI-Survey-update notification";
		
		Type type = new TypeToken<Notification>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Notification n = gson.fromJson(notificationString, type);
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), n.s_id, false, superUser);
		// End Authorisation
		
		log.info("Update notification for survey: " + request.getRemoteUser() + " : "+ n.s_id + " Remote s_id: " + 
				n.remote_s_ident + " Email Question: " + n.notifyDetails.emailQuestionName );
		
		PreparedStatement pstmt = null;
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			NotificationManager nm = new NotificationManager(localisation);
			if(n.target.equals("forward") && nm.isFeedbackLoop(sd, request.getServerName(), n)) {
				throw new Exception("Survey is being forwarded to itself");
			}
			nm.updateNotification(sd, pstmt, request.getRemoteUser(), n);	
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"SQL Exception", e);
		    response = Response.serverError().entity("SQL Error").build();
		} catch (AuthorisationException e) {
			log.info("Authorisation Exception");
		    response = Response.serverError().entity("Not authorised").build();
		} catch (ApplicationException e) {
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			String msg = e.getMessage();
			log.info(msg);
			if(msg!= null && !msg.contains("forwarded to itself")) {
				log.log(Level.SEVERE,"Error", e);
			} else if(msg == null) {
				log.log(Level.SEVERE,"Error", e);
				msg = "System error";
			} else {
				msg = "System error";
			}
		    response = Response.serverError().entity(msg).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;

	}
	
	@Path("/{id}")
	@DELETE
	public Response deleteForward(@Context HttpServletRequest request,
			@PathParam("id") int id) { 
		
		Response response = null;
		String connectionString = "surveyKPI-Survey-delete notification";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		boolean superUser = false;
		
		// Data level authorisation, is the user authorised to delete this forward
		
		try {
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			ResultSet resultSet = null;
			String sql = "select s_id " +
					" from forward " +
					" where id = ?";
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, id);	
			log.info("Delete forward, validate survey: " + pstmt.toString());

			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				int sId = resultSet.getInt(1);
				superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
				a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
				NotificationManager fm = new NotificationManager(localisation);
				fm.deleteNotification(sd, request.getRemoteUser(), id);
			}
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"SQL Exception", e);
		    response = Response.serverError().entity("SQL Error").build();
		} catch (AuthorisationException e) {
			log.info("Authorisation Exception");
		    response = Response.serverError().entity("Not authorised").build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			
		}

		return response;

	}
	
	/*
	 * send an immmediate notification
	 */
	@Path("/immediate")
	@POST
	public Response immediateNotification(
			@Context HttpServletRequest request,
			@FormParam("notification") String notificationString) { 
		
		Response response = null;
		String connectionString = "surveyKPI-Survey-send immediate notification";
		
		Type type = new TypeToken<Notification>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Notification n = gson.fromJson(notificationString, type);
		
		log.info("Immediate Notification:========== " + notificationString);
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		if(n.s_id > 0) {
			a.isValidSurvey(sd, request.getRemoteUser(), n.s_id, false, superUser);
		} else {
			a.isValidSurveyIdent(sd, request.getRemoteUser(), n.sIdent, false, superUser);
		}
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		Connection cResults = ResultsDataSource.getConnection(connectionString);
		try {	
			// Localisation			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			NotificationManager nm = new NotificationManager(localisation);
			
			// Validate
			if(n.target.equals("forward") && nm.isFeedbackLoop(sd, request.getServerName(), n)) {
				throw new Exception("Survey is being forwarded to itself");
			}
			
			NotifyDetails nd = n.notifyDetails;
			int pId = GeneralUtilityMethods.getProjectIdFromSurveyIdent(sd, n.sIdent);
			String basePath = GeneralUtilityMethods.getBasePath(request);
			String serverName = request.getServerName();
			String scheme = request.getScheme();
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			SubmissionMessage subMsg = new SubmissionMessage(
					0,					// Task Id - ignore, only relevant for a reminder
					n.sIdent,			// Survey Ident
					pId,
					n.instanceId, 
					nd.from,
					nd.subject, 
					nd.content,
					nd.attach,
					nd.include_references,
					nd.launched_only,
					nd.emailQuestion,
					nd.emailQuestionName,
					nd.emailMeta,
					nd.emails,
					n.target,
					request.getRemoteUser(),
					scheme,
					serverName,
					basePath);
			MessagingManager mm = new MessagingManager();
			mm.createMessage(sd, oId, "submission", "", gson.toJson(subMsg));
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			String msg = e.getMessage();
			if(msg.contains("forwarddest")) {	// Unique key
				response = Response.serverError().entity("Duplicate forwarding address").build();
			} else {
				response = Response.serverError().entity("SQL Error").build();
				log.log(Level.SEVERE,"SQL Exception", e);
			}
		} catch (AuthorisationException e) {
			log.info("Authorisation Exception");
		    response = Response.serverError().entity("Not authorised").build();
		} catch (ApplicationException e) {
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			String msg = e.getMessage();
			log.info(msg);
			if(msg == null) {
				msg = "System Error";
			}
			if(msg != null && !msg.contains("forwarded to itself")) {
				msg = "System Error";
				log.log(Level.SEVERE,"Error", e);
			}
		    response = Response.serverError().entity(msg).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);
			
		}

		return response;

	}

}

