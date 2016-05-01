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

import org.smap.model.TableManager;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableColumnMarkup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
	 * Return the management configuration
	 */
	@GET
	@Path("/config/{sId}/{dpId}")
	@Produces("application/json")
	public Response getConfig(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("dpId") int dpId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionsInForm");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
		ArrayList<TableColumn> configColumns = null;

		Response response = null;
		
		String sql = "select config from show_columns where id = ? and dp_id = ?;";
		PreparedStatement pstmt = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-QuestionsInForm");
		ResultSet rs = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {

			/*
			 * Get formId of top level form and its table name
			 * These are required by the getColumnsInForm method
			 */
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);
			
			ArrayList<Column> columnList = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					0,
					f.id,
					f.tableName,
					false,	// Don't include Read only
					true,	// Include parent key
					true,	// Include "bad"
					true	// Include instanceId
					);		
			
			/*
			 * Get the columns to show for this survey and management function
			 */
			if(dpId > 0) {
				pstmt = sd.prepareStatement(sql);	 
				pstmt.setInt(1,  sId);
				pstmt.setInt(2,  dpId);
	
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String config = rs.getString("config");
				
					System.out.println("   Config: " + config);
					if(config != null) {
						Type type = new TypeToken<ArrayList<TableColumn>>(){}.getType();	
						configColumns = gson.fromJson(config, type);
					} else {
						configColumns = new ArrayList<TableColumn> ();
					}
				} else {
					configColumns = new ArrayList<TableColumn> ();
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
					TableColumn tc = new TableColumn(c.name, c.humanName);
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
			
			/*
			 * Add the data processing columns and configuration
			 */
			getDataProcessingConfig(dpId, columns);
			
			response = Response.ok(gson.toJson(columns)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			
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
	 * Make a survey managed
	 */
	class AddManaged {
		int sId;
		int manageId;
	}
	
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/add")
	public Response setManaged(
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
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		AddManaged am = gson.fromJson(settings, AddManaged.class);
		
		String sql = "update survey set managed_id = ? where s_id = ?;";
		PreparedStatement pstmt = null;
		
		String sqlAdd = null;
		PreparedStatement pstmtAdd = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), am.sId, false);
		// End Authorisation

		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Add Managed Forms");
		
		try {

			// 1. Add the management id to the survey record
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, am.manageId);
			pstmt.setInt(2, am.sId);
			log.info("Adding managed survey: " + pstmt.toString());
			pstmt.executeUpdate();
			
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, am.sId);	// Get the table name of the top level form

			// 2. Create results tables if they do not exist
			TableManager tm = new TableManager();
			String sIdent = GeneralUtilityMethods.getSurveyIdent(sd, am.sId);
			tm.createTable(cResults, sd, f.tableName, sIdent);
			
			// 3.  Add the data processing columns to the results table
			ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
			getDataProcessingConfig(am.manageId, columns);
			
			for(int i = 0; i < columns.size(); i++) {
				TableColumn tc = columns.get(i);
				if(tc.type != null) {
					
					if(tc.type.equals("calculate")) {
						continue;		// Calculated types are not stored in the database
					}
					
					String type;
					if(tc.type.equals("select_one")) {
						type = "text";
					} else {
						type = tc.type;
					}
					sqlAdd = "alter table " + f.tableName + " add column " + tc.name + " " + type;
					if(pstmtAdd != null) try{pstmtAdd.close();} catch(Exception e) {}
					
					pstmtAdd = cResults.prepareStatement(sqlAdd);
					log.info("Adding management column: " + pstmtAdd.toString());
					try {
						pstmtAdd.executeUpdate();
					} catch (Exception e) {
						String msg = e.getMessage();
						if(msg.contains("already exists")) {
							System.out.println("Info: Management column already exists");
						} else {
							throw e;
						}
					} finally {
						pstmtAdd.close();
					}
				} else {
					System.out.println("Error: managed column not added as type was null: " + tc.name);
				}
			}
			
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
			try {if (pstmtAdd != null) {pstmtAdd.close();}} catch (Exception e) {}
			
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
	 * Update a data record
	 */
	class Update {
		String name;
		String value;
		String currentValue;
		int prikey;
	}
	
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/update/{sId}/{dpId}")
	public Response updateManagedRecord(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("dpId") int dpId,
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
		
		System.out.println("Updates: " + settings);
		Type type = new TypeToken<ArrayList<Update>>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		ArrayList<Update> updates = gson.fromJson(settings, type);
		
		
		String sqlCanUpdate = "select count(*) from survey "
				+ "where s_id = ? "
				+ "and managed_id = ? "
				+ "and blocked = 'false' "
				+ "and deleted = 'false';";
		PreparedStatement pstmtCanUpdate = null;
		
		PreparedStatement pstmtUpdate = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation

		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Update Managed Forms");
		
		try {

			/*
			 * Verify that the survey is managed by the provided data processing id and get
			 */
			pstmtCanUpdate = sd.prepareStatement(sqlCanUpdate);
			pstmtCanUpdate.setInt(1, sId);
			pstmtCanUpdate.setInt(2, dpId);
			ResultSet rs = pstmtCanUpdate.executeQuery();
			int count = 0;
			if(rs.next()) {
				count = rs.getInt(1);
			}
			if(count == 0) {
				throw new Exception("Cannot update this survey. Check it is not blocked or deleted");
			}
			
			/*
			 * Get the data processing columns
			 */
			ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
			getDataProcessingConfig(dpId, columns);
			
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);	// Get the table name of the top level form
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			
			/*
			 * Process each column
			 */
			cResults.setAutoCommit(false);
			for(int i = 0; i < updates.size(); i++) {
			
				Update u = updates.get(i);
				
				// 1. Escape quotes in update name, though not really necessary due to next step 
				u.name = u.name.replace("'", "''").trim();
				
				// 2. Confirm this is an editable managed column
				boolean updateable = false;
				String columnType = null;
				for(int j = 0; j < columns.size(); j++) {
					TableColumn tc = columns.get(j);
					if(tc.name.equals(u.name)) {
						if(!tc.readonly) {
							updateable = true;
							columnType = tc.type;
						}
						break;
					}
				}
				if(!updateable) {
					throw new Exception("Update failed " + u.name + " is not updatable");
				}
				
				// 2. Apply the update
				if(u.value != null && u.value.trim().length() == 0) {
					u.value = null;
				}
				if(u.currentValue != null && u.currentValue.trim().length() == 0) {
					u.currentValue = null;
				}
				
				String sqlUpdate = "update " + f.tableName;
				
				if(u.value == null) {
					sqlUpdate += " set " + u.name + " = null ";
				} else {
					sqlUpdate += " set " + u.name + " = ? ";		
				}
				sqlUpdate += "where "
						+ "prikey = ? "
						+ "and " + u.name;
				
				if(u.currentValue == null) {
					sqlUpdate += " is null;";
				} else {
					sqlUpdate += " = ?;";
				}
						
				pstmtUpdate = cResults.prepareStatement(sqlUpdate);
				
				// Set the parameters
				int paramCount = 1;
				if(u.value != null) {
					if(columnType.equals("text") || columnType.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.value);
					} else if(columnType.equals("date")) {
						java.util.Date inputDate = dateFormat.parse(u.value);
						pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
					} else {
						log.info("Warning: unknown type: " + columnType + " value: " + u.value);
						pstmtUpdate.setString(paramCount++, u.value);
					}
				}
				pstmtUpdate.setInt(paramCount++, u.prikey);
				if(u.currentValue != null) {
					if(columnType.equals("text") || columnType.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.currentValue);
					} else if(columnType.equals("date")) {
						java.util.Date inputDate = dateFormat.parse(u.currentValue);
						pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
					}
				} 
				
				log.info("Updating managed survey: " + pstmtUpdate.toString());
				count = pstmtUpdate.executeUpdate();
				if(count == 0) {
					throw new Exception("Update failed: "
							+ "Try refreshing your view of the data as someone may already "
							+ "have updated this record.");
				}
				
			}
			cResults.commit();
			response = Response.ok().build();
				
		} catch (Exception e) {
			try{cResults.rollback();} catch(Exception ex) {}
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try{cResults.setAutoCommit(true);} catch(Exception ex) {}
			
			try {if (pstmtCanUpdate != null) {pstmtCanUpdate.close();}} catch (Exception e) {}
			
			try {
				if (sd != null) {
					sd.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
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
				name.equals("User") ||
				name.equals("Upload Time") ||
				name.equals("Survey Notes") ||
				name.equals("_start") ||
				name.equals("decision_date") ||
				name.equals("programme") ||
				name.equals("project") ||
				name.equals("instanceName") ||
				name.equals("_end") 
				) {
			hide = true;
		}
		
		return hide;
	}
	
	private void addProcessing(TableColumn tc) {
		String name = tc.name;
		tc.mgmt = true;
		if(name.equals("_mgmt_responsible")) {
			tc.hide = false;
			tc.readonly = false;
			tc.type = "text";
		} else if(name.equals("_mgmt_action_deadline")) {
			tc.hide = false;
			tc.readonly = false;
			tc.type = "date";
		} else if(name.equals("_mgmt_action_date")) {
			tc.hide = true;
			tc.readonly = false;
			tc.type = "date";
		} else if(name.equals("_mgmt_response_status")) {
			tc.hide = false;
			tc.readonly = true;
			tc.type = "calculate";
			tc.markup = new ArrayList<TableColumnMarkup> ();
			tc.markup.add(new TableColumnMarkup("Deadline met", "bg-success"));
			tc.markup.add(new TableColumnMarkup("Done with delay", "bg-info"));
			tc.markup.add(new TableColumnMarkup("In the pipeline", "bg-warning"));
			tc.markup.add(new TableColumnMarkup("Deadline crossed", "bg-danger"));
		} else if(name.equals("_mgmt_action_taken")) {
			tc.hide = false;
			tc.readonly = false;
			tc.type = "text";
		} else if(name.equals("_mgmt_address_recommendation")) {
			tc.hide = false;
			tc.readonly = false;
			tc.type = "select_one";
			tc.choices = new ArrayList<String> ();
			tc.choices.add("Yes");
			tc.choices.add("No, needs further work");		
		} else if(name.equals("_mgmt_comment")) {
			tc.hide = false;
			tc.readonly = false;
			tc.type = "text";
		}
	}
	
	
	/*
	 * Add the data processing columns
	 */
	private void getDataProcessingConfig(int dpId, ArrayList<TableColumn> formColumns) {
		
		/*
		 * Manually create this (TODO retrieve from database)
		 */
		ArrayList<Column> columns = new ArrayList<Column> ();
		GeneralUtilityMethods.addManagementColumns(columns);
		for(int i = 0; i < columns.size(); i++) {
			Column c = columns.get(i);
			TableColumn tc = new TableColumn(c.name, c.humanName);
			tc.hide = hideDefault(c.name);
			addProcessing(tc);
			System.out.println("   " + c.name + " ---- " + c.humanName + " ---- " + tc.hide);
			formColumns.add(tc);
		}
		
	}

}

