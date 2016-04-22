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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.DataProcessingConfig;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import model.Settings;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the questions in the top level form for the requested survey
 */
@Path("/managed")
public class ManagedForms extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	/*
	 * Return a list of all questions in the top level form
	 *  Exclude read only
	 */
	@GET
	@Path("/questionsInMainForm/{sId}")
	@Produces("application/json")
	public Response getQuestions(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("dpId") int dpId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionsInForm");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
		ArrayList<TableColumn> configColumns = null;
		Response response = null;
		
		String sql = "select config from data_processing where id = ?;";
		PreparedStatement pstmt = null;
		
		String sqlGetForm = "select  "
				+ "f_id,"
				+ "table_name "
				+ "from form "
				+ "where s_id = ? "
				+ "and parentform = 0;";
		PreparedStatement pstmtGetForm = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-QuestionsInForm");
		int fId;
		ResultSet rs = null;
		String tableName;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {

			/*
			 * Get formId of top level form and its table name
			 * These are required by the getColumnsInForm method
			 */
			pstmtGetForm = sd.prepareStatement(sqlGetForm);
			pstmtGetForm.setInt(1, sId);
			rs = pstmtGetForm.executeQuery();
			if(rs.next()) {
				fId = rs.getInt(1);
				tableName = rs.getString(2);
			} else {
				throw new Exception("Failed to get parent form");
			}
			
			ArrayList<Column> columnList = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					0,
					fId,
					tableName,
					false,	// Don't include Read only
					true,	// Include parent key
					true,	// Include "bad"
					true	// Include instanceId
					);		
			
			/*
			 * Get the columns from the 
			 */
			if(dpId > 0) {
				pstmt = sd.prepareStatement(sql);	 
				pstmt.setInt(1,  sId);
	
				if(rs != null) {
					rs.close();
				}
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String config = rs.getString("config");
				
					System.out.println("   Config: " + config);
					if(config != null) {
						DataProcessingConfig dpConfig = gson.fromJson(config, DataProcessingConfig.class);
						configColumns = dpConfig.columns;
					} 
				}
			} else {
				configColumns = new ArrayList<TableColumn> ();
			}
			
			/*
			 * Add any configuration settings
			 * Order the config according to the current survey definition and
			 * Add any new columns that may have been added to the survey since the configuration was created
			 */
			
			for(int i = 0; i < columnList.size(); i++) {
				Column c = columnList.get(i);
				if(keepThis(c.name)) {
					TableColumn tc = new TableColumn(c.humanName);
					tc.hide = hideDefault(c.humanName);
					for(int j = 0; j < configColumns.size(); j++) {
						TableColumn tcConfig = configColumns.get(j);
						if(tcConfig.name.equals(tc.name)) {
							tc.include = tcConfig.include;
							tc.hide = tcConfig.hide;
							break;
						}
					}
					
					columns.add(tc);
				}
			}
			
			String resp = gson.toJson(columns);
			response = Response.ok(resp).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
			}
		}


		return response;
	}

	/*
	 * Update the settings
	 */
	class AddManaged {
		int sId;
		int manageId;
	}
	
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/add")
	public Response updateSettings(
			@Context HttpServletRequest request, 
			@FormParam("settings") String settings
			) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String user = request.getRemoteUser();
		log.info("Settings:" + settings);
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		AddManaged am = gson.fromJson(settings, AddManaged.class);
		
		String sql = "update survey set managed_id = ? where s_id = ?;";
		
		PreparedStatement pstmt = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), am.sId, false);
		// End Authorisation

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, am.manageId);
			pstmt.setInt(2, am.sId);
			log.info("Adding managed survey: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
			
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Identify any columns that should be dropped
	 */
	private boolean keepThis(String name) {
		boolean keep = true;
		
		if(name.equals("_s_id") ||
				name.equals("parkey") ||
				name.equals("_version") ||
				name.equals("_complete") ||
				name.equals("_location_trigger") ||
				name.equals("_device") ||
				name.equals("_bad") ||
				name.equals("_bad_reason") ||
				name.equals("instanceid")
				) {
			keep = false;
		}
		return keep;
	}
	
	/*
	 * Set a default hide value
	 */
	private boolean hideDefault(String name) {
		boolean hide = false;
		
		if(name.equals("_s_id") ||
				name.equals("prikey") ||
				name.equals("User") ||
				name.equals("Upload Time") ||
				name.equals("Survey Notes") ||
				name.equals("_start") ||
				name.equals("_end") 
				) {
			hide = true;
		}
		return hide;
	}

}

