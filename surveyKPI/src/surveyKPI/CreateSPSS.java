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
import org.smap.sdal.managers.PDFManager;
import org.smap.sdal.managers.SpssManager;

/*
 * Creates a PDF template
 * HTML Fragments 
 *   <h3>  - Use for group Labels
 *   .group - group elements
 *   .hint - Hints
 */

@Path("/spss/{sId}")
public class CreateSPSS extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(CreateSPSS.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}

	
	@GET
	@Produces("application/x-download")
	public Response getSpssService (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@QueryParam("language") String language,
			@QueryParam("filename") String filename) throws Exception {
		
		log.info("Create SPS from survey:" + sId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createSPS");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation 
	
		
		// Get the base path
		Response response = null;
		
		try {
			SpssManager spssm = new SpssManager();  
			
			
			String sps = spssm.createSPS(
					connectionSD,
					request.getRemoteUser(),
					"none", 
					sId, 
					filename,
					response);
			
			response = Response.ok(sps).build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();	
		} finally {
			
			SDDataSource.closeConnection("createSPS", connectionSD);	
			
		}
		return response;
	}
	

}
