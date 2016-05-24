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
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.MiscPDFManager;

/*
 * Creates a PDF
 * HTML Fragments 
 *   <h3>  - Use for group Labels
 *   .group - group elements
 *   .hint - Hints
 */

@Path("/usage/{oId}")
public class UsageReports extends Application {
	
	Authorise a = new Authorise(null, Authorise.ORG);
	
	private static Logger log =
			 Logger.getLogger(UsageReports.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UsageReports.class);
		return s;
	}
	
	//public static Font WingDings = null;
	//public static Font defaultFont = null;

	
	@GET
	@Produces("application/x-download")
	public Response getMonthly (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("oId") int oId,
			@QueryParam("month") int month,			// 1 - 12
			@QueryParam("year") int year,
			@QueryParam("period") String period) throws Exception {

		GeneralUtilityMethods.assertBusinessServer(request.getServerName());   // Service only available on business servers
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("createPDF");	
		a.isAuthorised(connectionSD, request.getRemoteUser());		
		// End Authorisation 
		
		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Get the organisation name
		String org_name = GeneralUtilityMethods.getOrganisationName(connectionSD, oId);
		
		try {
			MiscPDFManager pm = new MiscPDFManager();
			pm.createUsagePdf(
					connectionSD,
					response.getOutputStream(),
					basePath, 
					response,
					oId,
					month,
					year,
					period,
					org_name);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		return Response.ok("").build();
	}
	

}
