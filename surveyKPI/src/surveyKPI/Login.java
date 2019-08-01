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
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Login functions
 */
@Path("/login")
public class Login extends Application {
	
	private static Logger log =
			 Logger.getLogger(Login.class.getName());

	private class Key {
		public String key;
	};
	
	/*
	 * Login and get a key for future authentication
	 */
	@GET
	@Path("/key")
	@Produces("application/json")
	public Response getKey(@Context HttpServletRequest request,
			@QueryParam("form") String formIdent) {
		
		Response response = null;
		 		
		// No authorisation is required - the key is returned to the authenticated user
		
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-login-key");
		
		
		String user = request.getRemoteUser();
		
		Key accessToken = new Key();
		try {
			String scope = "user";	// General key for access not just for forms
			if(formIdent != null) {
				scope = formIdent;		
			}
			accessToken.key = GeneralUtilityMethods.getNewAccessKey(connectionSD, user, scope);
			log.info("userevent: " + user + " : requested access key : " + accessToken.key);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Failed to get access key", e);
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-login-key", connectionSD);
		}
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		String resp = gson.toJson(accessToken);
		response = Response.ok(resp).build();
		
		return response;

	}

	



}

