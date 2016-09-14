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
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PDFSurveyManager;

/*
 * Creates a PDF template
 * HTML Fragments 
 *   <h3>  - Use for group Labels
 *   .group - group elements
 *   .hint - Hints
 */

@Path("/pdf/{sId}")
public class CreatePDF extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(CreatePDF.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	
	@GET
	@Produces("application/x-download")
	public Response getPDFService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@QueryParam("instance") String instanceId,
			@QueryParam("language") String language,
			@QueryParam("landscape") boolean landscape,
			@QueryParam("filename") String filename) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		log.info("Create PDF from survey:" + sId + " for record: " + instanceId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation 
		
		lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "Create PDF for instance: " + instanceId);
		
		Connection cResults = ResultsDataSource.getConnection("createPDF");
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		try {
			PDFSurveyManager pm = new PDFSurveyManager();  
			
			
			pm.createPdf(
					connectionSD,
					cResults,
					response.getOutputStream(),
					basePath, 
					request.getRemoteUser(),
					language, 
					sId, 
					instanceId,
					filename,
					landscape,
					response);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			SDDataSource.closeConnection("createPDF", connectionSD);	
			ResultsDataSource.closeConnection("createPDF", cResults);
			
		}
		return Response.ok("").build();
	}
	

}
