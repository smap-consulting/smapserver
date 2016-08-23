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
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXB;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.ODKForm;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.XformsJavaRosa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Get surveys assigned to the user (ODK Format)
 * Example output:
	<forms>
		<form url="//{server}/formXml?odkFormKey={generated key}">{name}</form>
	</forms>

 * 
 */

@Path("/formList")

public class FormList extends Application {
	
	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(FormList.class.getName());
	
	
	// Respond with XML 
	@GET
	@Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})   
	public Response getFormListXML(@Context HttpServletRequest request, 
			@QueryParam("user") String userId) throws IOException {
		
		return getFormList(request, userId, true);
	}
 
	/*
	 * Get the form list
	 */
	private Response getFormList(HttpServletRequest request,
			String userId,
			boolean isXML) throws IOException {

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
			return response;
		}
		
		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormList");
		String user = request.getRemoteUser();
		if(user == null) {
		    user = userId;
		} 
	    a.isAuthorised(connectionSD, user);	//Authorisation - Access 

		String host = request.getServerName();
		int portNumber = request.getLocalPort();
		String javaRosaVersion = request.getHeader("X-OpenRosa-Version");
		PreparedStatement pstmt = null;
		ArrayList<org.smap.sdal.model.Survey> surveys = null;
		
		try {
			SurveyManager sm = new SurveyManager();
			boolean superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			surveys = sm.getSurveys(connectionSD, pstmt, user, false, false, 0, superUser);
			
			// Determine whether or not a manifest identifying media files exists for this survey
			TranslationManager translationMgr = new TranslationManager();
			for (int i = 0; i < surveys.size(); i++ ) {
				Survey s = surveys.get(i);
				s.setHasManifest(translationMgr.hasManifest(connectionSD, user, s.getId())); 
			}
			
			XformsJavaRosa formList = processXForm(host, portNumber, surveys);	
			
			// Convert response into xml
			String resp = null;
			if(isXML) {
				StringWriter writer = new StringWriter();
				JAXB.marshal(formList, writer);
				resp = writer.toString();
			} else {
				Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
				resp = gsonResp.toJson(formList.xform);
			}
			response = Response.ok(resp).header("X-OpenRosa-Version", javaRosaVersion).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().entity(e.getMessage()).build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		
			SDDataSource.closeConnection("surveyMobileAPI-FormList", connectionSD);
			
		}
		
		return response;

	}
		
	private XformsJavaRosa processXForm(String host, int portNumber, 
			ArrayList<org.smap.sdal.model.Survey> surveys) {

		String port = "";
		String responsePath = "";
		String protocol = "";

		//XFormListProxy formList = new XFormListProxy();
		XformsJavaRosa formList = new XformsJavaRosa();
		formList.xform = new ArrayList<ODKForm> ();
		
		if(portNumber != 80  && portNumber != 443) {
			port = ":" + String.valueOf(portNumber);
		}
		
		if(portNumber == 443) {
			protocol = "https://";
		} else {
			protocol = "http://";
		}

		// Extract the data
		try {
			
			for (int i = 0; i < surveys.size(); i++ ) {
				//XFormProxy form = new XFormProxy();
				ODKForm form = new ODKForm();
				Survey s = surveys.get(i);
				form.formID = String.valueOf(s.getIdent());
				form.name = s.getName();
				form.name = s.getDisplayName();
				form.majorMinorVersion = " ";
				form.version = String.valueOf(s.version);
				form.downloadUrl = protocol + host + port + responsePath + "/formXML?key=" + form.formID;
				if(s.hasManifest()) {
					form.manifestUrl = protocol + host + port + responsePath +
							"/xformsManifest?key=" + s.getIdent();
				}
				formList.xform.add(form);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "", e);
			return null;
		} 		

		return formList;
	}
}

