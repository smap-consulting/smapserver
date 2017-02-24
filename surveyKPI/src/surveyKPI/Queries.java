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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MiscPDFManager;
import org.smap.sdal.managers.QueryManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Query;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import utilities.XLSTaskManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import model.Settings;

/*
 * Manages Queries
 * Queries are owned by an individual
 */

@Path("/query")
public class Queries extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Queries.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	public Queries() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Get the user's queries
	 */
	@GET
	@Produces("application/json")
	public Response getQueries(
			@QueryParam("published") boolean published,	// Include queries not owned by user but accessible (TODO)
			@Context HttpServletRequest request
			) throws IOException {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = SDDataSource.getConnection("surveyKPI-get query");
		
		// Authorisation not required - queries belong to any authenticated user
	
		try {
			
			// Get queries
			QueryManager qm = new QueryManager();
			ArrayList<Query> queries = qm.getQueries(sd, request.getRemoteUser());		
			
			// Return groups to calling program
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(queries);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI - getQueries", sd);
		}
		
		return response;
	}
	
	/*
	 * Update a query
	 * If the query id is -1 then create a new query
	 */
	@POST
	@Produces("application/json")
	public Response saveQuery(
			@FormParam("query") String queryString,
			@Context HttpServletRequest request) {
		
		GeneralUtilityMethods.assertBusinessServer(request.getServerName());
		
		Response response = null;
		Connection sd = SDDataSource.getConnection("surveyKPI-save query");

		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Query q = gson.fromJson(queryString, Query.class);
		String formsString = gson.toJson(q.forms);
		
		String sqlInsert = "insert into custom_query (u_id, name, query) values(?, ?, ?)";
		String sqlUpdate = "update custom_query set "
				+ "name = ?,"
				+ "query = ? "
				+ "where u_id = ? "
				+ "and id = ?";
		PreparedStatement pstmt = null;
		
		try {
			
			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());
			if(q.id <= 0) {
				// Create a new query
				pstmt = sd.prepareStatement(sqlInsert);
				pstmt.setInt(1, uId);
				pstmt.setString(2, q.name);
				pstmt.setString(3, formsString);
				
			} else {
				// Update a query
				pstmt = sd.prepareStatement(sqlUpdate);
				pstmt.setString(1, q.name);
				pstmt.setString(2, formsString);
				pstmt.setInt(3, uId);
				pstmt.setInt(4, q.id);
			}
			log.info("Change to queries: " + pstmt.toString());
			pstmt.executeUpdate();
			
			// Return the updated list
			QueryManager qm = new QueryManager();
			ArrayList<Query> queries = qm.getQueries(sd, request.getRemoteUser());		
			
			// Return groups to calling program
			String resp = gson.toJson(queries);	
			response = Response.ok(resp).build();	
			
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("Save query", sd);
			
		}
		
		return response;
	}
	


}
