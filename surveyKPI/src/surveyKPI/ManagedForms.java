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
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.model.TableManager;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LinkageManager;
import org.smap.sdal.managers.ManagedFormsManager;
import org.smap.sdal.model.Filter;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Link;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.TableColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
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
			@PathParam("dpId") int managedId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-GetConfig");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		if(managedId > 0) {
			a.isValidCustomReport(sd, request.getRemoteUser(), managedId);
		}
		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-GetConfig");
		Response response = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {
			
			ManagedFormsManager qm = new ManagedFormsManager();
			ManagedFormConfig mfc = qm.getColumns(sd, cResults, sId, managedId, request.getRemoteUser());
			response = Response.ok(gson.toJson(mfc)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-GetConfig", sd);
			ResultsDataSource.closeConnection("surveyKPI-GetConfig", cResults);
		}


		return response;
	}

	/*
	 * Return the surveys in the project along with their management information
	 */
	@GET
	@Path("/surveys/{pId}")
	@Produces("application/json")
	public Response getSurveys(@Context HttpServletRequest request,
			@PathParam("pId") int pId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-GetSurveys");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidProject(sd, request.getRemoteUser(), pId);
		// End Authorisation
		
		Response response = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {
			
			ManagedFormsManager mf = new ManagedFormsManager();
			ArrayList<ManagedFormItem> items = mf.getManagedForms(sd, pId);
			response = Response.ok(gson.toJson(items)).build();
		
				
		} catch (Exception e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} finally {
			SDDataSource.closeConnection("surveyKPI-GetConfig", sd);
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
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), am.sId, false);
		// End Authorisation

		String sql = "update survey set managed_id = ? where s_id = ?;";
		PreparedStatement pstmt = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Add Managed Forms");
		
		try {

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, am.sId);	// Get the table name of the top level form
			TableManager tm = new TableManager();
			
			// 1. Check that the managed form is compatible with the survey
			String compatibleMsg = compatibleManagedForm(sd, localisation, am.sId, am.manageId);
			if(compatibleMsg != null) {
				throw new Exception(localisation.getString("mf_nc") + " " + compatibleMsg);
			}
			
			// 2. Add the management id to the survey record
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, am.manageId);
			pstmt.setInt(2, am.sId);
			log.info("Adding managed survey: " + pstmt.toString());
			pstmt.executeUpdate();
			
			// 3. Create results tables if they do not exist
			if(am.manageId > 0) {
				String sIdent = GeneralUtilityMethods.getSurveyIdent(sd, am.sId);
				tm.createTable(cResults, sd, f.tableName, sIdent, am.sId, am.manageId);
			}
			
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
			
			SDDataSource.closeConnection("surveyKPI-managedForms", sd);	
			ResultsDataSource.closeConnection("surveyKPI-Add Managed Forms", cResults);
		}
		
		return response;
	}
	
	/*
	 * Verify that
	 *  1. Calculations in the managed form refer to questions in either the managed form or the form
	 *     we are attaching to
	 */
	private String compatibleManagedForm(Connection sd, ResourceBundle localisation, int sId, int managedId) {
		
		StringBuffer compatibleMsg = new StringBuffer("");
			
		if(managedId > 0 && sId > 0) {
				
			try {
				ArrayList<TableColumn> managedColumns = new ArrayList<TableColumn> ();				
				ManagedFormsManager qm = new ManagedFormsManager();
				qm.getDataProcessingConfig(sd, managedId, managedColumns, null);
					
				org.smap.sdal.model.Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);	// Get the table name of the top level form		
				ArrayList<TableColumn> formColumns = GeneralUtilityMethods.getColumnsInForm(sd, null, 0, f.id, null, false, false, false, false, 
						false	// Don't include other meta data
						);
				
				for(TableColumn mc : managedColumns) {
					
					if(mc.type.equals("calculate")) {
						
						for(int i = 0; i < mc.calculation.columns.size(); i++) {
							String refColumn = mc.calculation.columns.get(i);
							boolean referenceExists = false;
							
							// Check to see if the referenced column is in the managed form
							for(TableColumn mc2 : managedColumns) {
								if(refColumn.equals(mc2.name)) {
									referenceExists = true;
									break;
								}
							}
							
							// Check to see if the referenced column is in the form that is being attached to
							if(!referenceExists) {
								for(TableColumn fc2 : formColumns) {
									if(refColumn.equals(fc2.name)) {
										referenceExists = true;
										break;
									}
								}
							}
							
							// Report the missing reference
							if(!referenceExists) {
								compatibleMsg.append(localisation.getString("mf_col") + " " + 
										refColumn + " " + localisation.getString("mf_cninc"));
							}
						}
						
						
					}
					
				}
			} catch (Exception e) {
				compatibleMsg.append(e.getMessage());
			}
		}
		
		if(compatibleMsg.length() == 0) {
			return null;
		} else {
			return compatibleMsg.toString();
		}

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

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
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
				throw new Exception(localisation.getString("mf_blocked"));
			}
			
			/*
			 * Get the data processing columns
			 */
			ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
			ManagedFormsManager qm = new ManagedFormsManager();
			qm.getDataProcessingConfig(sd,dpId, columns, null);
			
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
				//if(u.value != null && u.value.trim().length() == 0) {
				//	u.value = null;
				//}
				//if(u.currentValue != null && u.currentValue.trim().length() == 0) {
				//	u.currentValue = null;
				//}
				
				String sqlUpdate = "update " + f.tableName;
				
				if(u.value == null) {
					sqlUpdate += " set " + u.name + " = null ";
				} else {
					sqlUpdate += " set " + u.name + " = ? ";		
				}
				sqlUpdate += "where "
						+ "prikey = ? ";
						//+ "and (" + u.name;
				
	
				//if(u.currentValue == null) {
				//	sqlUpdate += " is null);";
				//} else {
				//	sqlUpdate += " = ? or " + u.name + " is null)";
				//}
				
				try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (Exception e) {}
				pstmtUpdate = cResults.prepareStatement(sqlUpdate);
				
				// Set the parameters
				int paramCount = 1;
				if(u.value != null) {
					if(columnType.equals("text") || columnType.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.value);
					} else if(columnType.equals("date")) {
						java.util.Date inputDate = dateFormat.parse(u.value);
						pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
					} else if(columnType.equals("integer")) {
						int inputInt = Integer.parseInt(u.value);
						pstmtUpdate.setInt(paramCount++, inputInt);
					} else if(columnType.equals("decimal")) {
						double inputDouble = Double.parseDouble(u.value);
						pstmtUpdate.setDouble(paramCount++, inputDouble);
					} else {
						log.info("Warning: unknown type: " + columnType + " value: " + u.value);
						pstmtUpdate.setString(paramCount++, u.value);
					}
				}
				pstmtUpdate.setInt(paramCount++, u.prikey);
				/*
				 * Disable this integrity check
				 * There are currently too many false errors
				if(u.currentValue != null) {
					if(columnType.equals("text") || columnType.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.currentValue);
					} else if(columnType.equals("date")) {
						java.util.Date inputDate = dateFormat.parse(u.currentValue);
						pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
					} else if(columnType.equals("integer")) {
						int inputInt = Integer.parseInt(u.currentValue);
						pstmtUpdate.setInt(paramCount++, inputInt);
					} else if(columnType.equals("decimal")) {
						double inputDouble = Double.parseDouble(u.currentValue);
						pstmtUpdate.setDouble(paramCount++, inputDouble);
					} else {
						pstmtUpdate.setString(paramCount++, u.currentValue);	// Default
					}
				} 
				*/
				
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
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (Exception e) {}
			
			SDDataSource.closeConnection("surveyKPI-managedForms", sd);
			ResultsDataSource.closeConnection("surveyKPI-Update Managed Forms", cResults);
		}
		
		return response;
	}
	
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/config/{sId}")
	public Response updateManageConfig(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
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
		

		String sqlUpdate = "update general_settings set settings = ? where u_id = ? and s_id = ? and key = 'mf';";
		PreparedStatement pstmtUpdate = null;
		
		String sqlInsert = "insert into general_settings (settings, u_id, s_id, key) values(?, ?, ?, 'mf');";
		PreparedStatement pstmtInsert = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		try {

			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());	// Get user id
			
			pstmtUpdate = sd.prepareStatement(sqlUpdate);
			pstmtUpdate.setString(1, settings);
			pstmtUpdate.setInt(2, uId);
			pstmtUpdate.setInt(3, sId);
			log.info("Updating managed form settings: " + pstmtUpdate.toString());
			int count = pstmtUpdate.executeUpdate();
			if(count == 0) {
				pstmtInsert = sd.prepareStatement(sqlInsert);
				pstmtInsert.setString(1, settings);
				pstmtInsert.setInt(2, uId);
				pstmtInsert.setInt(3, sId);
				log.info("Inserting managed form settings: " + pstmtInsert.toString());
				pstmtInsert.executeUpdate();
			}

			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (Exception e) {}
			try {if (pstmtInsert != null) {pstmtInsert.close();}} catch (Exception e) {}
	
			SDDataSource.closeConnection("surveyKPI-managedForms", sd);
		}
		
		return response;
	}
	
	/*
	 * Get data connected to the passed in record
	 */
	@GET
	@Path("/connected/{sId}/{fId}/{prikey}")
	@Produces("application/json")
	public Response getLinks(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("fId") int fId,
			@PathParam("prikey") int prikey) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ManagedForms-getLinks");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation

		Response response = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-ManagedForms-getLinks");
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {
			
			LinkageManager lm = new LinkageManager();
			ArrayList<Link> links = lm.getSurveyLinks(sd, cResults, sId, fId, prikey);
			response = Response.ok(gson.toJson(links)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-ManagedForms-getLinks", sd);
			ResultsDataSource.closeConnection("surveyKPI-ManagedForms-getLinks", cResults);
		}


		return response;
	}

	
	/*
	 * Get the filterable data associated with a column in a results table
	 */
	@GET
	@Path("/filters/{sId}/{fId}/{colname}")
	@Produces("application/json")
	public Response getFilter(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("fId") int fId,
			@PathParam("colname") String colname) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-QuestionsInForm");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation

		Response response = null;
		Filter filter = new Filter();
		String tableName = null;
		int count = 0;
		final int MAX_VALUES = 10;
		
		colname = colname.replace("'", "''");	// Escape apostrophes
		
		// SQL to get the column type
		String sqlColType = "select data_type from information_schema.columns "
				+ "where table_name = ? "
				+ "and column_name = ?"; 
		PreparedStatement pstmtGetColType = null;
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetMin = null;
		PreparedStatement pstmtGetMax = null;
		PreparedStatement pstmtGetVals = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-filters");
		ResultSet rs = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {

			// If the form id was not provided assume the top level form for the survey is required
			Form f = null;
			if(fId <= 0) {
				f = GeneralUtilityMethods.getTopLevelForm(sd, sId); // Get formId of top level form and its table name
			} else {
				f = GeneralUtilityMethods.getForm(sd, sId, fId);
			}
			fId = f.id;
			tableName = f.tableName;
			
			String sqlGetMax = "select max(" + colname + ") from " + tableName; 	// SQL to get max value
			String sqlGetMin = "select min(" + colname + ") from " + tableName; 	// SQL to get min value
			
			String sqlGetVals = "select distinct(" + colname + ") from " + tableName + 
					" order by " + colname + " asc"; 	// SQL to get distinct values
			
			/*
			 * Get the column type
			 */
			pstmtGetColType = cResults.prepareStatement(sqlColType);
			pstmtGetColType.setString(1, tableName);
			pstmtGetColType.setString(2, colname);
			rs = pstmtGetColType.executeQuery();
			if(!rs.next()) {
				throw new Exception("Unknown table " + tableName + " or column " + colname);
			} else {
				filter.qType = rs.getString(1);
			}
			rs.close();
			
			/*
			 * Get the count of distinct data values
			 */
			String sql = "select count(distinct " + colname + ") as n from " + tableName;
			pstmt = cResults.prepareStatement(sql);
			rs = pstmt.executeQuery();
			if(rs.next()) {
				count = rs.getInt(1);
				System.out.println("There are " + count + " distinct values");
			}
			rs.close();
			
			/*
			 * Get the range of valid values or a list of valid values
			 */
			pstmtGetMin = cResults.prepareStatement(sqlGetMin);
			pstmtGetMax = cResults.prepareStatement(sqlGetMax);
			
			if(filter.qType.equals("integer")) {
			
				if(count > MAX_VALUES) {
					
					filter.range = true;
					
					rs = pstmtGetMin.executeQuery();
					if(rs.next()) {
						filter.iMin = rs.getInt(1);
					}
					rs.close();
					
					rs = pstmtGetMax.executeQuery();
					if(rs.next()) {
						filter.iMax = rs.getInt(1);
					}
					rs.close();
				} else {
					pstmtGetVals = cResults.prepareStatement(sqlGetVals);
						
					rs = pstmtGetVals.executeQuery();
					filter.iValues = new ArrayList<Integer> ();
					while(rs.next()) {
						filter.iValues.add(rs.getInt(1));
					}
					rs.close();
						
				}
			} else if(filter.qType.equals("text")) {
				filter.search = true;
			} else if(filter.qType.startsWith("timestamp")) {
				filter.range = true;
				
				rs = pstmtGetMin.executeQuery();
				if(rs.next()) {
					filter.tMin = rs.getTimestamp(1);
				}
				rs.close();
				
				rs = pstmtGetMax.executeQuery();
				if(rs.next()) {
					filter.tMax = rs.getTimestamp(1);
				}
				System.out.println("Max: " + filter.tMax);
				rs.close();
				
			} else {
				filter.search = true;
			}
			
			response = Response.ok(gson.toJson(filter)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetColType != null) {pstmtGetColType.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetMin != null) {pstmtGetMin.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetMax != null) {pstmtGetMax.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetVals != null) {pstmtGetVals.close();	}} catch (SQLException e) {	}
			
			SDDataSource.closeConnection("surveyKPI-QuestionsInForm", sd);
			ResultsDataSource.closeConnection("surveyKPI-QuestionsInForm", cResults);
		}


		return response;
	}

	

	

}

