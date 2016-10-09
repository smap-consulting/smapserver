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
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/*
 * Get HTML Manifest for offline use of forms
 */

@Path("/htmlManifest")
public class HtmlManifest extends Application{

	Authorise a = new Authorise(null, Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(HtmlManifest.class.getName());
	
	class Results {
		public String name;
		public Form subForm;	// Non null if this results item is a sub form
		public String value;
		public boolean begin_group;
		public boolean end_group;
		
		public Results (String n, Form f, String v, boolean bg, boolean eg) {
			name = n;
			subForm = f;
			value = v;
			begin_group = bg;
			end_group = eg;
		}
	}
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(HtmlManifest.class);
		return s;
	}


	/*
	 * Parameters
	 *  sName : Survey Name
	 *  prikey : Primary key of data record in the top level table of this survey
	 *  instructions on whether to preserve or replace each record
	 */
	@GET
	@Path("/{sName}")
	@Produces("text/cache-manifest")
	public Response getInstance(@Context HttpServletRequest request,
			@PathParam("sName") String templateName
			) throws IOException {

		Response response = null;
		
		log.info("htmlManifest: Survey=" + templateName);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}
		
		String user = request.getRemoteUser();
		Survey survey = null;
		
		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-HtmlManifest");
		// Catch authorisation errors and return 404 - not found which should clear the cache
		try {
			a.isAuthorised(connectionSD, user);
			SurveyManager sm = new SurveyManager();
			survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
			boolean superUser = false;
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			a.isValidSurvey(connectionSD, user, survey.id, false, superUser);	// Validate that the user can access this survey
			a.isBlocked(connectionSD, survey.id, false);
		} catch (Exception e) {
			response = Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException ex) {
				
			}
		    return response;
		}
		// End Authorisation
		
		// Create manifest header - TODO allow for invalidation of manifest through changing the version
		StringBuilder manifest = new StringBuilder("CACHE MANIFEST\n");
		manifest = manifest.append("# Form Version 4\n");
		
		// Add standard files: TODO replace with reading a file generated by webforms
		manifest.append("CACHE:\n");
		manifest.append("http://fonts.googleapis.com/css?family=Open+Sans:400,700,600&subset=latin,cyrillic-ext,cyrillic,greek-ext,greek,vietnamese,latin-ext\n");
		manifest.append("/webforms/css/libs/webform_formhub.css\n");
		manifest.append("/webforms/css/libs/webform_print_formhub.css\n");
		manifest.append("/public/build/js/webform-combined.min.js\n");
		
		// Add icons
		manifest.append("/webforms/images/favicon.ico\n");
		manifest.append("/webforms/images/fieldTask_144_144_min.png\n");
		manifest.append("/webforms/images/fieldTask_114_114_min.png\n");
		manifest.append("/webforms/images/fieldTask_72_72_min.png\n");
		manifest.append("/webforms/images/fieldTask_57_57_min.png\n");
		
		// Get any media files
		try {			
			// Get database driver and connection to the results database
			ArrayList<String> media = getMedia(connectionSD, survey.id);
			
			for(int i = 0; i < media.size(); i++) {
				manifest.append("/media/" + media.get(i) + "\n");		// TODO this is wrong needs to call getFileURL
			}			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
		} finally {
			SDDataSource.closeConnection("surveyMobileAPI-HtmlManifest", connectionSD);
		}
		
		// Add default manifest entries
		manifest.append("\nFALLBACK:\n");
		manifest.append("/ /offline\n");
		manifest.append("\nNETWORK:\n");
		manifest.append("*\n");
		
    	response = Response.ok(manifest.toString()).build();
		
    	return response;
	}
	
	/*
	 * Get the primary key from the passed in key values
	 *  The key must be in the top level form
	 */
	ArrayList<String> getMedia(Connection connection, int s_id)  {

		ArrayList<String> media = new ArrayList<String> ();
		
		String sql = "select value from translation where s_id = ? " +
				"and (type = 'image' or type = 'audio' or type = 'video');";
		System.out.println("Getting media: " + sql + " : " + s_id);

		try {
			PreparedStatement pstmt = connection.prepareStatement(sql);
			pstmt.setInt(1, s_id);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				media.add(rs.getString(1));
			}
		} catch (Exception e) {	
				e.printStackTrace();
		}

		return media;
	}
	
 
}

