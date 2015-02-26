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
import java.util.HashSet;
import java.util.Set;
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
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import model.Remote;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.model.ODKForm;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.XformsJavaRosa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Get the notifications set for this project
 */

@Path("/notifications")
public class NotificationList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(NotificationList.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(NotificationList.class);
		return s;
	}
	
	@Path("/{projectId}")
	@GET
	public Response getForms(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Notifications");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {
			NotificationManager fm = new NotificationManager();
			ArrayList<Notification> fList = fm.getProjectNotifications(connectionSD, pstmt, request.getRemoteUser(), projectId);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(fList);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
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
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		Type type = new TypeToken<Remote>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Remote r = gson.fromJson(remoteString, type);

		if(r == null) {
			response = Response.serverError().entity("Details for remote server not provided").build();
			return response;
		}
		
		System.out.println("Host: " + r.address);
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
			
			System.out.println("Executing request " + httpget.getRequestLine() + " to target " + target);
          
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
                
            System.out.println("----------------------------------------");
            if(fList != null) {
            	System.out.println("Length: " + fList.xform.size());
				Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
				resp = gsonResp.toJson(fList.xform);
				System.out.println("Response: " + resp);
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
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		Type type = new TypeToken<Notification>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Notification n = gson.fromJson(notificationString, type);
		
		System.out.println("Notification:========== " + notificationString);
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), n.s_id, false);
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {	
			
			NotificationManager nm = new NotificationManager();
			
			// Validate
			if(n.target.equals("forward") && nm.isFeedbackLoop(connectionSD, request.getServerName(), n)) {
				throw new Exception("Survey is being forwarded to itself");
			}
 
			nm.addNotification(connectionSD, pstmt, request.getRemoteUser(), n);
			
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
	
	@Path("/update")
	@POST
	public Response updateNotification(@Context HttpServletRequest request,
			@FormParam("notification") String notificationString) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		Type type = new TypeToken<Notification>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Notification n = gson.fromJson(notificationString, type);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		System.out.println("s_id: " + n.s_id);
		System.out.println("Remote s_id: " + n.remote_s_ident);
		
		PreparedStatement pstmt = null;
		
		
		try {
			a.isValidSurvey(connectionSD, request.getRemoteUser(), n.s_id, false);
			NotificationManager nm = new NotificationManager();
			if(n.target.equals("forward") && nm.isFeedbackLoop(connectionSD, request.getServerName(), n)) {
				throw new Exception("Survey is being forwarded to itself");
			}
			nm.updateNotification(connectionSD, pstmt, request.getRemoteUser(), n);	
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
	
	@Path("/{id}")
	@DELETE
	public Response deleteForward(@Context HttpServletRequest request,
			@PathParam("id") int id) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		// Data level authorisation, is the user authorised to delete this forward
		
		try {
			ResultSet resultSet = null;
			String sql = "select s_id " +
					" from forward " +
					" where id = ?";
			
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			pstmt = connectionSD.prepareStatement(sql);	
			log.info("Delete forward, validate survey:" + sql);
			pstmt.setInt(1, id);	

			resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				int sId = resultSet.getInt(1);
				a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
				NotificationManager fm = new NotificationManager();
				fm.deleteNotification(connectionSD, pstmt, request.getRemoteUser(), id);
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
	
	
	/*
	 * Apply notifications for the supplied upload event
	 * Debug only block this or remove
	 */
	@Path("/apply/{ue_id}")
	@GET
	public Response applyNotifications(@Context HttpServletRequest request,
			@PathParam("ue_id") int ue_id) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		//a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		
		PreparedStatement pstmtGetUploadEvent = null;
		PreparedStatement pstmtGetNotifications = null;
		PreparedStatement pstmtUpdateUploadEvent = null;
		PreparedStatement pstmtNotificationLog = null;
		
		
		try {
			NotificationManager fm = new NotificationManager();
			fm.notifyForSubmission(connectionSD, 
					pstmtGetUploadEvent, 
					pstmtGetNotifications, 
					pstmtUpdateUploadEvent, 
					pstmtNotificationLog, 
					ue_id, request.getRemoteUser(), request.getServerName());	
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"SQL Exception", e);
		    response = Response.serverError().entity("SQL Error").build();
		} catch (Exception e) {
			String msg = e.getMessage();
			log.info(msg);
		    response = Response.serverError().entity(msg).build();
		} finally {
			
			try {if (pstmtGetUploadEvent != null) {pstmtGetUploadEvent.close();}} catch (SQLException e) {}
			try {if (pstmtGetNotifications != null) {pstmtGetNotifications.close();}} catch (SQLException e) {}
			try {if (pstmtUpdateUploadEvent != null) {pstmtUpdateUploadEvent.close();}} catch (SQLException e) {}
			try {if (pstmtNotificationLog != null) {pstmtNotificationLog.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
}

