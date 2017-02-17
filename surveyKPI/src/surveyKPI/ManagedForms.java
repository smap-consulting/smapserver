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
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.managers.LinkageManager;
import org.smap.sdal.managers.ManagedFormsManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.ActionLink;
import org.smap.sdal.model.Filter;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Link;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.ManagedFormUserConfig;
import org.smap.sdal.model.Role;
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
	
	Authorise a = null;
	Authorise aSuper = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	public ManagedForms() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.MANAGE);		// Enumerators with MANAGE access can process managed forms
		a = new Authorise(authorisations, null);		
	}
	
	/*
	 * Return the management configuration
	 */
	@GET
	@Path("/config/{sId}/{dpId}")
	@Produces("application/json")
	public Response getReportConfig(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("dpId") int managedId) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-GetReportConfig");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		if(managedId > 0) {
			a.isValidManagedForm(sd, request.getRemoteUser(), managedId);
		}
		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-GetReportConfig");
		Response response = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			ManagedFormsManager qm = new ManagedFormsManager();
			ManagedFormConfig mfc = qm.getManagedFormConfig(sd, cResults, sId, managedId, request.getRemoteUser(), oId, superUser);
			/*
			 * Remove data that is only used on the server
			 */
			for(TableColumn tc : mfc.columns) {
				tc.actions = null;
				tc.calculation = null;
			}
			response = Response.ok(gson.toJson(mfc)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-GetReportConfig", sd);
			ResultsDataSource.closeConnection("surveyKPI-GetReportConfig", cResults);
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
		Connection sd = SDDataSource.getConnection("surveyKPI-GetManagedForms");
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
			SDDataSource.closeConnection("surveyKPI-GetManagedForms", sd);
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
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aSuper.isAuthorised(sd, request.getRemoteUser());
		aSuper.isValidSurvey(sd, request.getRemoteUser(), am.sId, false, superUser);
		// End Authorisation

		String sql = "update survey set managed_id = ? where s_id = ?;";
		PreparedStatement pstmt = null;
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Add Managed Forms");
		
		try {
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, am.sId);	// Get the table name of the top level form
			TableManager tm = new TableManager();
			
			// 1. Check that the managed form is compatible with the survey
			String compatibleMsg = compatibleManagedForm(sd, localisation, am.sId, 
					am.manageId, request.getRemoteUser(), oId, superUser);
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
				boolean tableChanged = tm.createTable(cResults, sd, f.tableName, sIdent, am.sId, am.manageId);
				// Add any previously unpublished columns not in a changeset (Occurs if this is a new survey sharing an existing table)
				boolean tablePublished = tm.addUnpublishedColumns(sd, cResults, am.sId);			
				if(tableChanged || tablePublished) {
					tm.markPublished(sd, am.sId);		// only mark published if there have been changes made
				}
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
	private String compatibleManagedForm(Connection sd, ResourceBundle localisation, int sId, 
			int managedId,
			String user,
			int oId,
			boolean superUser) {
		
		StringBuffer compatibleMsg = new StringBuffer("");
			
		if(managedId > 0 && sId > 0) {
				
			try {
				ArrayList<TableColumn> managedColumns = new ArrayList<TableColumn> ();				
				ManagedFormsManager qm = new ManagedFormsManager();
				qm.getDataProcessingConfig(sd, managedId, managedColumns, null, oId);
					
				org.smap.sdal.model.Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);	// Get the table name of the top level form		
				ArrayList<TableColumn> formColumns = GeneralUtilityMethods.getColumnsInForm(sd, 
						null,
						sId,
						user,
						0,
						f.id, 
						null, 
						false, 
						false, 
						false, 
						false, 
						false,	// Don't include other meta data
						true,	// Include preloads
						true,	// Include instancename
						superUser,
						false		// HXL only include with XLS exports
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
	@Path("/update/{sId}/{managedId}")
	public Response updateManagedRecord(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("managedId") int managedId,
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
		
		
		String sqlCanUpdate = "select p_id from survey "
				+ "where s_id = ? "
				+ "and managed_id = ? "
				+ "and blocked = 'false' "
				+ "and deleted = 'false';";
		PreparedStatement pstmtCanUpdate = null;
		
		PreparedStatement pstmtUpdate = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		Connection cResults = ResultsDataSource.getConnection("surveyKPI-Update Managed Forms");
		int priority = -1;
		
		try {

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			int pId = 0;
			/*
			 * Verify that the survey is managed by the provided data processing id and get
			 */
			pstmtCanUpdate = sd.prepareStatement(sqlCanUpdate);
			pstmtCanUpdate.setInt(1, sId);
			pstmtCanUpdate.setInt(2, managedId);
			ResultSet rs = pstmtCanUpdate.executeQuery();
			if(rs.next()) {
				pId = rs.getInt(1);
			}
			if(pId == 0) {
				throw new Exception(localisation.getString("mf_blocked"));
			}
			
			/*
			 * Get the data processing columns
			 */
			ArrayList<TableColumn> columns = new ArrayList<TableColumn> ();
			ManagedFormsManager qm = new ManagedFormsManager();
			qm.getDataProcessingConfig(sd, managedId, columns, null, oId);
			
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
				TableColumn tc = null;
				for(int j = 0; j < columns.size(); j++) {
					TableColumn xx = columns.get(j);
					if(xx.name.equals(u.name)) {
						if(!xx.readonly) {
							updateable = true;
							tc = xx;
						}
						break;
					}
				}
				if(!updateable) {
					throw new Exception(u.name + " " + localisation.getString("mf_nu"));
				}
				
				String sqlUpdate = "update " + f.tableName;
				
				if(u.value == null) {
					sqlUpdate += " set " + u.name + " = null ";
				} else {
					sqlUpdate += " set " + u.name + " = ? ";		
				}
				sqlUpdate += "where "
						+ "prikey = ? ";
				
				try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (Exception e) {}
				pstmtUpdate = cResults.prepareStatement(sqlUpdate);
				
				// Set the parameters
				int paramCount = 1;
				if(u.value != null) {
					if(tc.type.equals("text") || tc.type.equals("select_one")) {
						pstmtUpdate.setString(paramCount++, u.value);
					} else if(tc.type.equals("date")) {
						if(u.value == null || u.value.trim().length() == 0) {
							pstmtUpdate.setDate(paramCount++, null);
						} else {
							java.util.Date inputDate = dateFormat.parse(u.value);
							pstmtUpdate.setDate(paramCount++, new java.sql.Date(inputDate.getTime()));
						}
					} else if(tc.type.equals("integer")) {
						int inputInt = Integer.parseInt(u.value);
						pstmtUpdate.setInt(paramCount++, inputInt);
					} else if(tc.type.equals("decimal")) {
						double inputDouble = Double.parseDouble(u.value);
						pstmtUpdate.setDouble(paramCount++, inputDouble);
					} else {
						log.info("Warning: unknown type: " + tc.type + " value: " + u.value);
						pstmtUpdate.setString(paramCount++, u.value);
					}
				}
				pstmtUpdate.setInt(paramCount++, u.prikey);
				
				log.info("Updating managed survey: " + pstmtUpdate.toString());
				int count = pstmtUpdate.executeUpdate();
				if(count == 0) {
					throw new Exception("Update failed: "
							+ "Try refreshing your view of the data as someone may already "
							+ "have updated this record.");
				}
				
				/*
				 * Apply any required actions
				 */
				if(tc.actions != null && tc.actions.size() > 0) {
					ActionManager am = new ActionManager();
					if(priority < 0) {
						priority = am.getPriority(cResults, f.tableName, u.prikey);
					}
					am.applyManagedFormActions(sd, tc, oId, sId, pId, managedId, u.prikey, priority, u.value, localisation);
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
	
	/*
	 * Get link to an action without creating an alert
	 */
	@GET
	@Produces("application/json")
	@Path("/actionlink/{sId}/{managedId}/{prikey}")
	public Response getActionLink(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("managedId") int managedId,
			@PathParam("prikey") int prikey,
			@QueryParam("roles") String roles
			) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String sqlCanUpdate = "select p_id from survey "
				+ "where s_id = ? "
				+ "and managed_id = ? "
				+ "and deleted = 'false';";
		PreparedStatement pstmtCanUpdate = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Get Action Link");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		try {

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			int pId = 0;
			
			/*
			 * Verify that the survey is managed by the provided data processing id and get the project id
			 */
			pstmtCanUpdate = sd.prepareStatement(sqlCanUpdate);
			pstmtCanUpdate.setInt(1, sId);
			pstmtCanUpdate.setInt(2, managedId);
			ResultSet rs = pstmtCanUpdate.executeQuery();
			if(rs.next()) {
				pId = rs.getInt(1);
			}
			if(pId == 0) {
				throw new Exception(localisation.getString("mf_blocked"));
			}
			ActionManager am = new ActionManager();
			Action action = new Action("respond");
			action.sId = sId;
			action.managedId = managedId;
			action.prikey = prikey;
			action.pId = pId;
			
			
			if(roles != null) {
				String [] rArray = roles.split(",");
				if(rArray.length > 0) {
					action.roles = new ArrayList<Role> ();
					for (int i = 0; i < rArray.length; i++) {
						Role r = new Role();
						try {
							r.id = Integer.parseInt(rArray[i]);
							action.roles.add(r);
						} catch (Exception e) {
							log.info("Error: Invalid Role Id: " + rArray[i] + " : " + e.getMessage());
						}
					}
				}
			}
			
			log.info("Creating action for prikey: " + prikey);
			ActionLink al = new ActionLink();
			al.link = request.getScheme() +
					"://" +
					request.getServerName() + 
					am.getLink(sd, action, oId);
			
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(al, ActionLink.class);
			response = Response.ok(resp).build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
	
			try {if (pstmtCanUpdate != null) {pstmtCanUpdate.close();}} catch (Exception e) {}
			
			SDDataSource.closeConnection("surveyKPI-Get Action Link", sd);
		}
		
		return response;
	}

	
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/updatestatus/{uIdent}/{status}")
	public Response updateStatus(
			@Context HttpServletRequest request, 
			@PathParam("uIdent") String uIdent,
			@PathParam("status") String status
			) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String sql = "update alert set status = ? where link like ?";
		PreparedStatement pstmt = null;
		
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, status);
			pstmt.setString(2, "%" + uIdent);
			log.info("Update action status" + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		
			
			SDDataSource.closeConnection("surveyKPI-managedForms", sd);
		
		}
		
		return response;
	}
	
	/*
	 * Update the configuration settings
	 */
	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/config/{sId}/{key}")
	public Response updateManageConfig(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("key") String key,		// Type of report to be saved
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
		
		String sqlUpdate = "update general_settings set settings = ? where u_id = ? and s_id = ? and key = ?;";
		PreparedStatement pstmtUpdate = null;
		
		String sqlInsert = "insert into general_settings (settings, u_id, s_id, key) values(?, ?, ?, ?);";
		PreparedStatement pstmtInsert = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		try {

			if(key.equals("mf")) {
				/*
				 * Convert the input to java classes and then back to json to ensure it is well formed
				 */
				Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				Type type = new TypeToken<ManagedFormUserConfig>(){}.getType();	
				ManagedFormUserConfig uc = gson.fromJson(settings, type);
				settings = gson.toJson(uc);
			}
			
			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());	// Get user id
			
			pstmtUpdate = sd.prepareStatement(sqlUpdate);
			pstmtUpdate.setString(1, settings);
			pstmtUpdate.setInt(2, uId);
			pstmtUpdate.setInt(3, sId);
			pstmtUpdate.setString(4, key);
			log.info("Updating managed form settings: " + pstmtUpdate.toString());
			int count = pstmtUpdate.executeUpdate();
			
			if(count == 0) {
				pstmtInsert = sd.prepareStatement(sqlInsert);
				pstmtInsert.setString(1, settings);
				pstmtInsert.setInt(2, uId);
				pstmtInsert.setInt(3, sId);
				pstmtInsert.setString(4, key);
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
	 * Get the configuration settings
	 */
	@GET
	@Produces("application/json")
	@Path("/getreportconfig/{sId}/{key}")
	public Response getManageConfig(
			@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("key") String key
			) { 
		
		Response response = null;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String sql = "select settings from general_settings where u_id = ? and s_id = ? and key = ?;";
		PreparedStatement pstmt = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		try {

			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());	// Get user id
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, uId);
			pstmt.setInt(2, sId);
			pstmt.setString(3, key);
			
			log.info("Getting settings: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				response = Response.ok(rs.getString(1)).build();
			} else {

				response = Response.ok().build();
			}
				
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
	
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
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
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
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
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

