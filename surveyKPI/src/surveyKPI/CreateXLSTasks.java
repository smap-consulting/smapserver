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
import org.smap.sdal.managers.PDFManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;

import utilities.XLSFormManager;
import utilities.XLSTaskManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

/*
 * Creates an XLS file with the list of current tasks
 */

@Path("/xlsTasks/{tgId}")
public class CreateXLSTasks extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(CreateXLSTasks.class.getName());

	
	@GET
	@Produces("application/x-download")
	public Response getXLSTasksService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("tgId") int tgId,
			@QueryParam("filetype") String filetype) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
				
		Connection sd = SDDataSource.getConnection("createXLSTasks");	
		// Authorisation - Access

		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidTaskGroup(sd, request.getRemoteUser(), tgId, false);
		// End Authorisation 
		
		TaskManager tm = new TaskManager();
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			
			// Get the task group name
			TaskGroup tg = tm.getTaskGroupDetails(sd, tgId);
			
			// Get the task list
			TaskListGeoJson tl = tm.getTasks(sd, tgId, true);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(tg.name + "." + filetype, response);
			
			// Create XLSTasks File
			XLSTaskManager xf = new XLSTaskManager(filetype);
			xf.createXLSTaskList(response.getOutputStream(), tl);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		return Response.ok("").build();
	}
	

}
