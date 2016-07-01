package surveyKPI;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;

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

/*
 * This service handles requests from data tables components:
 *    1) PDF export
 */
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/tables")
public class Tables extends Application {

	private static Logger log =
			 Logger.getLogger(Tables.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	@POST
	@Consumes("application/json")
	@Path("/pdf")
	public Response setManaged(
			@Context HttpServletRequest request, 
			@FormParam("settings") String settings
			) { 
		
		System.out.println("Requested PDF file");
		
		Response response = null;
		
		return response;
	}
	

}

