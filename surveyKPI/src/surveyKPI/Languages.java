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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;



import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns languages for the passed in survey identifier
 */
@Path("/languages/{sId}")
public class Languages extends Application {
	
	private static Logger log =
			 Logger.getLogger(Languages.class.getName());
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	public Languages() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Return the list of languages in this survey
	 */
	@GET
	@Produces("application/json")
	public Response getLanguages(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
	
		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Languages");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
			
		ArrayList<String> langs = new ArrayList<String> ();	

		PreparedStatement pstmt = null;
		try {
			String sql = null;

			sql = "select distinct language "
					+ "from translation "
					+ "where s_id = ? "
					+ "order by language asc";

			
			pstmt = connectionSD.prepareStatement(sql);	 
			pstmt.setInt(1,sId);
			ResultSet resultSet = pstmt.executeQuery();

			while (resultSet.next()) {				
				langs.add(resultSet.getString("language"));
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(langs);
			response = Response.ok(resp).build();
					
				
		} catch (SQLException e) {			
			log.log(Level.SEVERE, "SQL Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
			SDDataSource.closeConnection("surveyKPI-Languages", connectionSD);
		}

		return response;
	}
	

}

