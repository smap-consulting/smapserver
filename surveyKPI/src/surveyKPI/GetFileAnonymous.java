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
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.FileManager;

/*
 * Downloads a file for a user using an anonymous link
 */

@Path("/file/id/{ident}/{filename}")
public class GetFileAnonymous extends Application {
	
	Authorise a = null;
	Authorise aOrg = new Authorise(null, Authorise.ORG);
	
	private static Logger log =
			 Logger.getLogger(GetFileAnonymous.class.getName());

	public GetFileAnonymous() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		a = new Authorise(authorisations, null);	
	}
	
	/*
	 * Get file for anonymous user
	 */
	@GET
	@Produces("application/x-download")
	@Path("/organisation")
	public Response getOrganisationFileAnon(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("filename") String filename,
			@PathParam("ident") String user) throws SQLException {
			
		int oId = 0;
		Response r = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("Get Organisation File");	
		a.isValidTemporaryUser(connectionSD, user);
		a.isAuthorised(connectionSD, user);		
		try {		
			oId = GeneralUtilityMethods.getOrganisationId(connectionSD, user);
		} catch(Exception e) {
			// ignore error
		}
		// End Authorisation 
		
		
		log.info("Get File anonymously: " + filename + " for organisation: " + oId);
		try {
			
			FileManager fm = new FileManager();
			r = fm.getOrganisationFile(connectionSD, request, response, user, oId, filename, false, true);
			
		}  catch (Exception e) {
			log.info("Error getting file:" + e.getMessage());
			r = Response.status(Status.NOT_FOUND).build();
		} finally {	
			SDDataSource.closeConnection("Get Organisation File", connectionSD);	
		}
		
		return r;
		
	}
	
	
}
