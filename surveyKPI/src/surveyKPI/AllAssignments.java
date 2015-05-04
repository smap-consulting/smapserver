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
import javax.ws.rs.DELETE;
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
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.NotFoundException;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.Assignment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import taskModel.AssignFromSurvey;
import taskModel.Features;
import taskModel.Geometry;
import taskModel.TaskAddress;
import taskModel.TaskAddressSettings;
import utilities.CSVReader;
import utilities.QuestionInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*
 * Used by an administrator or analyst to view task status and make updates
 */
@Path("/assignments")
public class AllAssignments extends Application {


	Authorise a = null;;
	
	private static Logger log =
			 Logger.getLogger(Survey.class.getName());
	
	// Tell class loader about the root classes.  
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(AllAssignments.class);
		return s;
	}
	
	public AllAssignments() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);		
	}

	
	/*
	 * Return the existing assignments
	 */
	@GET
	@Path("/{projectId}")
	@Produces("application/json")
	public Response getAssignments(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId, 
			@QueryParam("user") int user_filter
			) {
		
		Response response = null;
					
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		JSONObject jo = new JSONObject();
		JSONArray ja = new JSONArray();
		JSONObject task_groups = new JSONObject();	// Array of task groups
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSurvey = null;
		PreparedStatement pstmtGeo = null;
		try {
			
			// Get the assignments
			String sql1 = "SELECT " +
					"t.id as task_id," +
					"t.type," +
					"t.title," +
					"t.url," +
					"t.form_id," +
					"t.initial_data," +
					"t.schedule_at," +
					"a.status as assignment_status," +
					"a.id as assignment_id, " +
					"t.address as address, " +
					"t.country as country, " +
					"t.postcode as postcode, " +
					"t.locality as locality, " +
					"t.street as street, " +
					"t.number as number, " +
					"t.geo_type as geo_type, " +
					"u.id as user_id, " +
					"u.ident as ident, " +
					"u.name as user_name, " + 
					"tg.tg_id as task_group_id, " +
					"tg.name as task_group_name " +
					"from task_group tg " +
					"left outer join tasks t on tg.tg_id = t.tg_id " +
					"left outer join assignments a " +
						" on a.task_id = t.id " + 
						" and a.status != 'deleted' " +
					"left outer join users u on a.assignee = u.id " +
					" where tg.p_id = ? ";
					
			String sql2 = null;
			if(user_filter == 0) {					// All users (default)	
				sql2 = "";							
			} else if(user_filter == -1) {			// Unassigned users
				sql2 = " and u.id is null";
			} else {								// The specified user
				sql2 = " and u.id = ? ";		
			}
			
			String sql3 = " order by tg.tg_id, t.form_id;";
			
			pstmt = connectionSD.prepareStatement(sql1 + sql2 + sql3);	
			pstmt.setInt(1, projectId);
			if(user_filter > 0) {
				pstmt.setInt(2, user_filter);
			}
			log.info("SQL: " + pstmt.toString());
			
			// Statement to get survey name
			String sqlSurvey = "select display_name from survey where s_id = ?";
			pstmtSurvey = connectionSD.prepareStatement(sqlSurvey);
			
			ResultSet resultSet = pstmt.executeQuery();
			int t_id = 0;
			int tg_id = 0;	// Task group id

			while (resultSet.next()) {
				JSONObject jr = new JSONObject();
				JSONObject jp = new JSONObject();
				JSONObject jg = null;
				
				jr.put("type", "Feature");
				
				// Create the new Task Assignment Objects

				// Populate the new Task Assignment
				t_id = resultSet.getInt("task_id");
				tg_id = resultSet.getInt("task_group_id");
				jp.put("task_id", t_id);
				jp.put("task_group_id", tg_id);
				
				String tg_name = resultSet.getString("task_group_name");
				if(tg_name == null || tg_name.trim().length() == 0) {
					jp.put("task_group_name", tg_id);
				} else {
					jp.put("task_group_name", tg_name);
				}
				
				String taskType = resultSet.getString("type");
				jp.put("type", taskType);
				if(taskType != null && taskType.equals("xform")) {
					int s_id = resultSet.getInt("form_id");
					pstmtSurvey.setInt(1, s_id);
					ResultSet sRs = pstmtSurvey.executeQuery();
					if(sRs.next()) {
						jp.put("survey_name", sRs.getString(1));
					}
					
				}
				
				jp.put("assignment_id", resultSet.getInt("assignment_id"));
				String assStatus = resultSet.getString("assignment_status");
				if(assStatus == null) {
					assStatus = "new";
				}
				jp.put("assignment_status", assStatus);
				
				jp.put("user_id", resultSet.getInt("user_id"));
				jp.put("user_ident", resultSet.getString("ident"));
				String user_name = resultSet.getString("user_name");
				if(user_name == null) {
					user_name = "";
				}
				jp.put("user_name", user_name);
				jp.put("address", resultSet.getString("address"));
				
				String geo_type = resultSet.getString("geo_type");
				// Get the coordinates
				if(geo_type != null) {
					// Add the coordinates
					jp.put("geo_type", geo_type);

					String sql = null;
					if(geo_type.equals("POINT")) {
						sql = "select ST_AsGeoJSON(geo_point) from tasks where id = ?;";
					} else if (geo_type.equals("POLYGON")) {
						sql = "select ST_AsGeoJSON(geo_polygon) from tasks where id = ?;";
					} else if (geo_type.equals("LINESTRING")) {
						sql = "select ST_AsGeoJSON(geo_linestring) from tasks where id = ?;";
					}
					if(pstmtGeo != null) {pstmtGeo.close();};
					pstmtGeo = connectionSD.prepareStatement(sql);
					pstmtGeo.setInt(1, t_id);
					ResultSet resultSetGeo = pstmtGeo.executeQuery();
					if(resultSetGeo.next()) {
						String geoString = resultSetGeo.getString(1);
						if(geoString != null) {
							jg = new JSONObject(geoString);	
							jr.put("geometry", jg);
						}
					}
					
				}
				jr.put("properties", jp);
				ja.put(jr);
				
			}
			
			jo.put("type", "FeatureCollection");
			jo.put("features", ja);
			
			/*
			 * Add task group details to the response
			 */
			String sql = "select tg_id, name, address_params from task_group;";
			if(pstmt != null) {pstmt.close();};
			pstmt = connectionSD.prepareStatement(sql);
			ResultSet tgrs = pstmt.executeQuery();
			while (tgrs.next()) {
				JSONObject tg = new JSONObject();
				tg.put("tg_id", tgrs.getInt(1));
				tg.put("tg_name", tgrs.getString(2));
				tg.put("tg_address_params", tgrs.getString(3));
				task_groups.put(tgrs.getString(1), tg);
			}
			jo.put("task_groups", task_groups);
			 
			response = Response.ok(jo.toString()).build();			
				
		} catch (SQLException e) {
			
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
		} catch (Exception e) {
			
			log.log(Level.SEVERE,"", e);
			response = Response.serverError().build();
		} finally {
			if (pstmt != null) try {pstmt.close();} catch (SQLException e) {};
			if (pstmtSurvey != null) try {pstmtSurvey.close();} catch (SQLException e) {};
			if (pstmtGeo != null) try {pstmtGeo.close();} catch (SQLException e) {};		
			try {if (connectionSD != null) {connectionSD.close();}} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}

		return response;
	}
	
	/*
	 * Add a task for every survey result that has location
	 * Add a task for the array of locations passed in the input parameters
	 */
	@POST
	@Path("/addSurvey/{projectId}")
	public Response addSurvey(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@FormParam("settings") String settings) { 
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		
		Response response = null;
		ArrayList<TaskAddress> addressArray = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		log.info("Assignment:" + settings);
		AssignFromSurvey as = new Gson().fromJson(settings, AssignFromSurvey.class);

		log.info("User id: " + as.user_id);
		
		String userName = request.getRemoteUser();
		int sId = as.source_survey_id;								// Source survey id (optional)
			
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		if(sId > 0) {
			a.isValidSurvey(connectionSD, userName, sId, false);	// Validate that the user can access this survey
		}
		// End Authorisation

		Connection connectionRel = null; 
		PreparedStatement pstmt = null;
		PreparedStatement pstmtInsert = null;
		PreparedStatement pstmtAssign = null;
		PreparedStatement pstmtCheckGeom = null;
		PreparedStatement pstmtTaskGroup = null;
		PreparedStatement pstmtGetSurveyIdent = null;
		
		try {
			connectionRel = ResultsDataSource.getConnection("surveyKPI-AllAssignments");
			connectionSD.setAutoCommit(false);
			
			/*
			 * Create the task group if an existing task group was not specified
			 */
			int taskGroupId = -1;
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			ResultSet keys = null;
			if(as.task_group_id <= 0) {
				
				String addressParams = gson.toJson(as.address_columns); 				
				String tgSql = "insert into task_group ( " +
						"name, " +
						"p_id, " +
						"address_params) " +
					"values (?, ?, ?);";
					
				pstmtTaskGroup = connectionSD.prepareStatement(tgSql, Statement.RETURN_GENERATED_KEYS);
				pstmtTaskGroup.setString(1, as.task_group_name);
				pstmtTaskGroup.setInt(2, projectId);
				pstmtTaskGroup.setString(3, addressParams);
				log.info("Sql: " + tgSql + " : " + as.task_group_name + " : " + addressParams);
				pstmtTaskGroup.execute();
				keys = pstmtTaskGroup.getGeneratedKeys();
				if(keys.next()) {
					taskGroupId = keys.getInt(1);
				}
			} else {
				taskGroupId = as.task_group_id;
			}
			
			/*
			 * Create the tasks unless no tasks have been specified
			 */
			if(as.form_id > 0) {
				String sql = null;
				ResultSet resultSet = null;
				String insertSql1 = "insert into tasks (" +
							"p_id, " +
							"tg_id, " +
							"type, " +
							"title, " +
							"form_id, " +
							"url, " +
							"geo_type, ";
							
				String insertSql2 =	"initial_data, " +
							"existing_record," +
							"address," +
							"schedule_at) " +
						"values (" +
							"?, " + 
							"?, " + 
							"'xform', " +
							"?, " +
							"?, " +
							"?, " +
							"?, ST_GeomFromText(?, 4326), " +
							"?, " +
							"?," +
							"?," +
							"now());";
				
				String assignSQL = "insert into assignments (assignee, status, task_id) values (?, ?, ?);";
				pstmtAssign = connectionSD.prepareStatement(assignSQL);
				
				String checkGeomSQL = "select count(*) from information_schema.columns where table_name = ? and column_name = 'the_geom'";
				pstmtCheckGeom = connectionRel.prepareStatement(checkGeomSQL);
				
				String getSurveyIdentSQL = "select ident from survey where s_id = ?;";
				pstmtGetSurveyIdent = connectionSD.prepareStatement(getSurveyIdentSQL);
				
				/*
				 * Todo: Generate form url and initial instance url in myassignments service
				 */
				String hostname = request.getServerName();
				if(hostname.equals("localhost")) {
						hostname = "10.0.2.2";	// For android emulator
				}
				pstmtGetSurveyIdent.setInt(1, as.form_id);
				resultSet = pstmtGetSurveyIdent.executeQuery();
				String initial_data_url = null;
				String target_form_url = null;
				if(resultSet.next()) {
					String target_form_ident = resultSet.getString(1);
					target_form_url = "http://" + hostname + "/formXML?key=" + target_form_ident;
				} else {
					throw new Exception("Form identifier not found for form id: " + as.form_id);
				}
				
				/*
				 * Get the tasks from the passed in source survey if this has been set
				 */
				if(sId != -1) {
					/*
					 * Get Forms and row counts in this survey
					 */
					sql = "select distinct f.table_name, f.parentform from form f " +
							"where f.s_id = ? " + 
							"order by f.table_name;";		
				
					log.info(sql + " : " + sId);
					pstmt = connectionSD.prepareStatement(sql);	 
					pstmt.setInt(1, sId);
					resultSet = pstmt.executeQuery();
		
					while (resultSet.next()) {
						String tableName2 = null;
						String tableName = resultSet.getString(1);
						String p_id = resultSet.getString(2);
						if(p_id == null) {	// The top level form
		
							QuestionInfo filterQuestion = null;
							String filterSql = null;
							if(as.filter != null) {
								String fValue = null;
								String fValue2 = null;
								filterQuestion = new QuestionInfo(sId, as.filter.qId, connectionSD, false, as.filter.lang, urlprefix);
								log.info("Filter question type: " + as.filter.qType);
								if(as.filter.qType != null) {
									if(as.filter.qType.startsWith("select")) {
										fValue = as.filter.oValue;
									} else if(as.filter.qType.equals("int")) {
										fValue = String.valueOf(as.filter.qInteger);
									} else if(as.filter.qType.equals("date")  || as.filter.qType.equals("dateTime")) {
										Timestamp startDate = new Timestamp(as.filter.qStartDate);
										Timestamp endDate = new Timestamp(as.filter.qEndDate);
										
										fValue = startDate.toString();
										fValue2 = endDate.toString();
									} else {
										fValue = as.filter.qText;
									}
								}
								
								filterSql = filterQuestion.getFilterExpression(fValue, fValue2);		
								log.info("filter: " + filterSql);
							}
							// Check to see if this form has geometry columns
							boolean hasGeom = false;
							pstmtCheckGeom.setString(1, tableName);
							log.info("Sql: " + checkGeomSQL + " : " + tableName);
							ResultSet resultSetGeom = pstmtCheckGeom.executeQuery();
							if(resultSetGeom.next()) {
								if(resultSetGeom.getInt(1) > 0) {
									hasGeom = true;
								}
							}
							
							// Get the primary key, location and address columns from this top level table
							String getTaskSql = null;
							String getTaskSqlWhere = null;
							String getTaskSqlEnd = null;
							
							if(hasGeom) {
								log.info("Has geometry");
								getTaskSql = "select " + tableName +".prikey, ST_AsText(" + tableName + ".the_geom) as the_geom ";
								getTaskSqlWhere = " from " + tableName + " where " + tableName + "._bad = 'false'";	
								getTaskSqlEnd = ";";
							} else {
								log.info("No geom found");
								// Get a subform that has geometry
								
								PreparedStatement pstmt2 = connectionSD.prepareStatement(sql);	 
								pstmt2.setInt(1, sId);
								ResultSet resultSet2 = pstmt2.executeQuery();
					
								while (resultSet2.next()) {
									String aTable = resultSet2.getString(1);
									pstmtCheckGeom.setString(1, aTable);
									log.info("Sql: " + checkGeomSQL + " : " + aTable);
									resultSetGeom = pstmtCheckGeom.executeQuery();
									if(resultSetGeom.next()) {
										if(resultSetGeom.getInt(1) > 0) {
											hasGeom = true;
											tableName2 = aTable;
										}
									}
								}
								pstmt2.close();
								resultSet2.close();
								getTaskSql = "select " + tableName + 
										".prikey, ST_AsText(ST_MakeLine(" + tableName2 + ".the_geom)) as the_geom ";
								getTaskSqlWhere = " from " + tableName + ", " + tableName2 + " where " + tableName +".prikey = " + tableName2 + 	".parkey " +
										" and " + tableName + "._bad = 'false'";
								getTaskSqlEnd = "group by " + tableName + ".prikey ";
							}
							
							// Finally if we still haven't found a geometry column then set all locations to 0, 0
							if(!hasGeom) {
								log.info("No geometry columns found");
								
								getTaskSql = "select " + tableName + ".prikey, 'POINT(0 0)' as the_geom ";
								getTaskSqlWhere = " from " + tableName + " where " + tableName + "._bad = 'false'";	
								getTaskSqlEnd = ";";
								
								log.info("where: " + getTaskSqlWhere);
							}
							
										
							if(as.address_columns != null) {
								for(int i = 0; i < as.address_columns.size(); i++) {
									TaskAddressSettings add = as.address_columns.get(i);
									if(add.selected) {
										getTaskSql += "," + tableName + "." + add.name;
									}
								}
							}
							//if(hasGeom) {
								
								/*
								 * Get the source form ident
								 */
								pstmtGetSurveyIdent.setInt(1, as.source_survey_id);
								if(resultSet != null) try {resultSet.close();} catch(Exception e) {};
								resultSet = pstmtGetSurveyIdent.executeQuery();
								String source_survey_ident = null;
								if(resultSet.next()) {
									source_survey_ident = resultSet.getString(1);
								} else {
									throw new Exception("Form identifier not found for form id: " + as.source_survey_id);
								}
								getTaskSql += getTaskSqlWhere;
								if(filterSql != null) {
									getTaskSql += " and " + filterSql;
								}
								getTaskSql += getTaskSqlEnd;
	
								if(pstmt != null) {pstmt.close();};
								pstmt = connectionRel.prepareStatement(getTaskSql);	
								log.info("SQL Get Tasks: " + pstmt.toString());
								resultSet = pstmt.executeQuery();
								while (resultSet.next()) {
					
									/*
									 * The original URL for instance data only allowed searching via primary key
									 *  the prikey was the last part of the path.
									 *  This use is now deprecated and a more flexible approach is used where the key
									 *  is passed as an attribute.  
									 *  The old path value of primary key is ignored with this new format
									 *  and is set to zero here.
									 */
									if(as.update_results && (as.source_survey_id == as.form_id)) {
										initial_data_url = "http://" + hostname + "/instanceXML/" + 
										source_survey_ident + "/0?key=prikey&keyval=" + resultSet.getString(1);
									}
									
									String location = null;
									int recordId = resultSet.getInt(1);
									log.info("Has geom: " +hasGeom);
									if(hasGeom) {
										location = resultSet.getString("the_geom");
									} 
									if(location == null) {
										location = "POINT(0 0)";
									} else if(location.startsWith("LINESTRING")) {
										log.info("Starts with linestring: " + location.split(" ").length);
										if(location.split(" ").length < 3) {	// Convert to point if there is only one location in the line
											location = location.replaceFirst("LINESTRING", "POINT");
										}
									}	 
									
									log.info("Location: " + location);
									
									String geoType = null;
									if(pstmtInsert != null) {pstmtInsert.close();};
									if(location.startsWith("POINT")) {
										pstmtInsert = connectionSD.prepareStatement(insertSql1 + "geo_point," + insertSql2, Statement.RETURN_GENERATED_KEYS);
										geoType = "POINT";
									} else if(location.startsWith("POLYGON")) {
										pstmtInsert = connectionSD.prepareStatement(insertSql1 + "geo_polygon," + insertSql2, Statement.RETURN_GENERATED_KEYS);
										geoType = "POLYGON";
									} else if(location.startsWith("LINESTRING")) {
										pstmtInsert = connectionSD.prepareStatement(insertSql1 + "geo_linestring," + insertSql2, Statement.RETURN_GENERATED_KEYS);
										geoType = "LINESTRING";
									} else {
										log.log(Level.SEVERE, "Unknown location type: " + location);
									}
									pstmtInsert.setInt(1, projectId);
									pstmtInsert.setInt(2, taskGroupId);
									pstmtInsert.setString(3, as.project_name + " : " + as.survey_name + " : " + resultSet.getString(1));
									pstmtInsert.setInt(4, as.form_id);
									pstmtInsert.setString(5, target_form_url);	
									pstmtInsert.setString(6, geoType);
									pstmtInsert.setString(7, location);
									pstmtInsert.setString(8, initial_data_url);			// Initial data
									pstmtInsert.setInt(9, recordId);			// Initial data
									
									/*
									 * Create address JSON string
									 */
									String addressString = null;
									if(as.address_columns != null) {
										
										addressArray = new ArrayList<TaskAddress> ();
										for(int i = 0; i < as.address_columns.size(); i++) {
											TaskAddressSettings add = as.address_columns.get(i);
											if(add.selected) {
												TaskAddress ta = new TaskAddress();
												ta.name = add.name;
												ta.value = resultSet.getString(add.name);
												addressArray.add(ta);
											}
										}
										gson = new GsonBuilder().disableHtmlEscaping().create();
										addressString = gson.toJson(addressArray); 
									}
									
									pstmtInsert.setString(10, addressString);			// Address
									
									log.info("SQL: " + pstmtInsert.toString());
									
									int count = pstmtInsert.executeUpdate();
									if(count != 1) {
										log.info("Error: Failed to insert task");
									} else {
										if(as.user_id > 0) {	// Assign the user to the new task
										
											keys = pstmtInsert.getGeneratedKeys();
											if(keys.next()) {
												int taskId = keys.getInt(1);
		
												pstmtAssign.setInt(1, as.user_id);
												pstmtAssign.setString(2, "accepted");
												pstmtAssign.setInt(3, taskId);
												
												log.info("SQL:" + pstmtAssign.toString());
												
												pstmtAssign.executeUpdate();
											}
											if(keys != null) try{ keys.close(); } catch(SQLException e) {};
	
									
										}
									}
								}
						
							break;
						} else {
							log.info("parent is:" + p_id + ":");
						}
					}
				}
				
				/*
				 * Set the tasks from the passed in task list
				 */
				if(as.new_tasks != null) {
					log.info("Crating " + as.new_tasks.features.length + " Ad-Hoc tasks");
					
					// Assume POINT location, TODO POLYGON, LINESTRING
					if(pstmtInsert != null) {pstmtInsert.close();};
					String geoType = "POINT";
					pstmtInsert = connectionSD.prepareStatement(insertSql1 + "geo_point," + insertSql2, Statement.RETURN_GENERATED_KEYS);
					
					// Create a dummy location if this task does not have one
					if(as.new_tasks.features.length == 0) {
						Features f = new Features();
						f.geometry = new Geometry();
						f.geometry.coordinates = new String[2];
						f.geometry.coordinates[0] = "0.0";
						f.geometry.coordinates[1] = "0.0";
						as.new_tasks.features = new Features[1];
						as.new_tasks.features[0] = f;
					}
					// Tasks have locations
					for(int i = 0; i < as.new_tasks.features.length; i++) {
						Features f = as.new_tasks.features[i];
						log.info("Creating task at " + f.geometry.coordinates[0] + " : " + f.geometry.coordinates[1]);
					
						pstmtInsert.setInt(1, projectId);
						pstmtInsert.setInt(2, taskGroupId);
						String title = null;
						if(f.properties != null && f.properties.title != null && !f.properties.title.equals("null")) {
							title = as.project_name + " : " + as.survey_name + " : " + f.properties.title;
						} else {
							title = as.project_name + " : " + as.survey_name;
						}
						pstmtInsert.setString(3, title);
						pstmtInsert.setInt(4, as.form_id);
						pstmtInsert.setString(5, target_form_url);	
						pstmtInsert.setString(6, "POINT");
						pstmtInsert.setString(7, "POINT(" + f.geometry.coordinates[0] + " " + f.geometry.coordinates[1] + ")");	// The location
						pstmtInsert.setString(8, null);			// Initial data url
						pstmtInsert.setInt(9, 0);				// Initial data record id
						pstmtInsert.setString(10, null);		// Address TBD
						
						log.info("Insert task: " + pstmtInsert.toString()); 
						int count = pstmtInsert.executeUpdate();
						if(count != 1) {
							log.info("Error: Failed to insert task");
						} else if((f.properties != null && f.properties.userId > 0) || as.user_id > 0) {	// Assign the user to the new task
							
							keys = pstmtInsert.getGeneratedKeys();
							if(keys.next()) {
								int taskId = keys.getInt(1);
								
								if(f.properties != null && f.properties.userId > 0) {
									pstmtAssign.setInt(1, f.properties.userId);
									pstmtAssign.setString(2, f.properties.assignment_status);
								} else {
									pstmtAssign.setInt(1, as.user_id);
									pstmtAssign.setString(2, "accepted");
								}
								
								pstmtAssign.setInt(3, taskId);
								
								log.info("Assign status: " + pstmtAssign.toString());
								pstmtAssign.executeUpdate();
							}
							if(keys != null) try{ keys.close(); } catch(SQLException e) {};
	
						}
					}

				}
			}
			connectionSD.commit();
				
		} catch (Exception e) {
			log.info("Error: " + e.getMessage());
			if(e.getMessage() != null && e.getMessage().contains("\"the_geom\" does not exist")) {
				String msg = "The survey results do not have coordinates " + as.source_survey_name;
				response = Response.status(Status.NO_CONTENT).entity(msg).build();
			} else if(e.getMessage() != null && e.getMessage().contains("does not exist")) {
				String msg = "No results have been submitted for " + as.source_survey_name;
				response = Response.status(Status.NO_CONTENT).entity(msg).build();
			} else {
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				log.log(Level.SEVERE,"", e);
			}	
			
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} finally {
			
			if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
			if(pstmtInsert != null) try {	pstmtInsert.close(); } catch(SQLException e) {};
			if(pstmtAssign != null) try {	pstmtAssign.close(); } catch(SQLException e) {};
			if(pstmtTaskGroup != null) try {	pstmtTaskGroup.close(); } catch(SQLException e) {};
			if(pstmtGetSurveyIdent != null) try {	pstmtGetSurveyIdent.close(); } catch(SQLException e) {};
			if (connectionSD != null) try { 
				connectionSD.setAutoCommit(true);
				connectionSD.close(); 
			} catch(SQLException e) {};
			if (connectionRel != null) try { connectionRel.close(); } catch(SQLException e) {};
			
		}
		
		return response;
	}
	
	/*
	 * Update the task assignment
	 */
	@POST
	public Response updateAssignmentStatus(@Context HttpServletRequest request, 
			@FormParam("settings") String settings) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		log.info("Assignment:" + settings);
		Type type = new TypeToken<ArrayList<Assignment>>(){}.getType();		
		ArrayList<Assignment> aArray = new Gson().fromJson(settings, type);
		
		String userName = request.getRemoteUser();	
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		for(int i = 0; i < aArray.size(); i++) {
			
			Assignment ass = aArray.get(i);
			
			if(ass.assignment_id == 0) {	// New assignment
				a.isValidTask(connectionSD, request.getRemoteUser(), ass.task_id);
			} else {	// update existing assignment
				a.isValidAssignment(connectionSD, request.getRemoteUser(), ass.assignment_id);
			}
	
		}
		// End Authorisation

		PreparedStatement pstmtInsert = null;
		PreparedStatement pstmtUpdate = null;
		String insertSQL = "insert into assignments (assignee, status, task_id) values (?, ?, ?);";
		String updateSQL = "update assignments set " +
				"assignee = ?," +
				"status = ? " +
				"where id = ?;";
		
		try {
			pstmtInsert = connectionSD.prepareStatement(insertSQL);
			pstmtUpdate = connectionSD.prepareStatement(updateSQL);
			connectionSD.setAutoCommit(false);
			
			for(int i = 0; i < aArray.size(); i++) {
			
				Assignment a = aArray.get(i);
				
				if(a.assignment_id == 0) {	// New assignment
					pstmtInsert.setInt(1,a.user.id);
					pstmtInsert.setString(2, a.assignment_status);
					pstmtInsert.setInt(3, a.task_id);
					pstmtInsert.executeUpdate();
				} else {	// update existing assignment
					pstmtUpdate.setInt(1,a.user.id);
					pstmtUpdate.setString(2, a.assignment_status);
					pstmtUpdate.setInt(3, a.assignment_id);
					pstmtUpdate.executeUpdate();
				}
		
			}
			connectionSD.commit();
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"", e);
			
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} finally {
			try {if (pstmtUpdate != null) {pstmtUpdate.close();}} catch (SQLException e) {}
			try {if (pstmtInsert != null) {pstmtInsert.close();}} catch (SQLException e) {}
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);	// Set auto commit back to true to ensure the connection has this when returned to the pool
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"", e);
			}
		}
		
		return response;
	}
	
	private class Column {
		int index;
		String name;
		String type;
		String geomCol;
		ArrayList<String> choices = null;
	}
	
	/*
	 * Load tasks, that is survey results, from a file
	 */
	@POST
	@Path("/load")
	public Response loadTasksFromFile(@Context HttpServletRequest request) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		log.info("Load tasks from file");
		
		String userName = request.getRemoteUser();	
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments-LoadTasks From File");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End role based authorisation - Check access to the requested survey once the survey id has been extracted
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();	
		fileItemFactory.setSizeThreshold(20*1024*1024); // 20 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);

		// SQL to get the form id of the top level form for the survey
		String sqlGetFormId = "select f_id, table_name from form where s_id = ? and (parentform is null or parentform = 0)";
		PreparedStatement pstmtGetFormId = null;
		
		// SQL to get a column name from the survey
		String sqlGetCol = "select q_id, qname, qtype from question where f_id = ? and qname = ?";
		PreparedStatement pstmtGetCol = null;
		
		// SQL to get choices for a select question
		String sqlGetChoices = "select ovalue from option where q_id = ?";
		PreparedStatement pstmtGetChoices = null;
		
		// Prepared Statements used in the clearing and inserting of data
		PreparedStatement pstmtDeleteExisting = null;
		PreparedStatement pstmtInsert = null;
		
		String fileName = null;
		String filePath = null;
		File savedFile = null;
		String contentType = null;
		int sId = 0;
		String sIdent = null;		// Survey Ident
		boolean hasGeopoint = false;
		int lonIndex = -1;			// Column containing longitude TODO support multiple geometries
		int latIndex = -1;			// Column containing latitude
		int fId = 0;
		String tableName = null;
		boolean clear_existing = false;
		HashMap<String, File> mediaFiles = new HashMap<String, File> ();
		
		Connection results = ResultsDataSource.getConnection("surveyKPI-AllAssignments-LoadTasks From File");
		try {
			
			// Get the base path
			String basePath = GeneralUtilityMethods.getBasePath(request);
			
			// Get the items from the multi part mime
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				if(item.isFormField()) {
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
				
					if(item.getFieldName().equals("survey")) {
						sId = Integer.parseInt(item.getString());
						a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
						a.canLoadTasks(connectionSD, sId);
						
						sIdent = GeneralUtilityMethods.getSurveyIdent(connectionSD, sId);
					} else if(item.getFieldName().equals("clear_existing")) {
						clear_existing = true;
					}
					
					
				} else if(!item.isFormField()) {
					// Handle Uploaded file
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					if(item.getSize() > 0) {
					    contentType = item.getContentType();
						fileName = String.valueOf(UUID.randomUUID());
						
						filePath = basePath + "/temp/" + fileName + ".csv";
					    savedFile = new File(filePath);
					    item.write(savedFile);
					}					
				}

			}
			
			// Get the form id for the top level form of this survey
			pstmtGetFormId = connectionSD.prepareStatement(sqlGetFormId);
			pstmtGetFormId.setInt(1, sId);
			log.info("Get top level form: " + pstmtGetFormId.toString());
			ResultSet rs = pstmtGetFormId.executeQuery();
			if(rs.next()) {
				fId = rs.getInt(1);
				tableName = rs.getString(2);
			}
			
			// Prepare the statement to get the column names in the survey that are to be updated
			pstmtGetCol = connectionSD.prepareStatement(sqlGetCol);
			pstmtGetCol.setInt(1, fId);
			
			// Prepare the statement to get select choices
			pstmtGetChoices = connectionSD.prepareStatement(sqlGetChoices);
			
			// If this is a zip file extract the contents and set the path to the expand csv file that should be inside
			// Refer to http://www.mkyong.com/java/how-to-decompress-files-from-a-zip-file/
			log.info("Content Type: " + contentType);
			if(contentType.equals("application/zip")) {
				String zipFolderPath = savedFile.getAbsolutePath() + ".dir";
				File zipFolder = new File(zipFolderPath);
				if(!zipFolder.exists()) {
					zipFolder.mkdir();
				}
				ZipInputStream zis = new ZipInputStream(new FileInputStream(savedFile));
				ZipEntry ze = null;
				byte[] buffer = new byte[1024];
				while((ze = zis.getNextEntry()) != null) {
					String zFileName = ze.getName();
					if(!zFileName.startsWith("__MAC")) {	// Files added by macintosh zip utility
						
						log.info("File in zip: " + ze.getName());
						File zFile = new File(zipFolderPath + File.separator + zFileName);
						if(zFileName.endsWith(".csv")) {
							savedFile = zFile;
						}
						new File(zFile.getParent()).mkdirs();	// Make sure path is complete 
					
						if(ze.isDirectory()) {
							zFile.mkdir();
						} else {
							// Save the filename and File for processing with each record of data
							
							// Remove the path from the filename - every file in the zip file must have a unique name
							int idx = zFileName.lastIndexOf('/');
							if(idx > 0) {
								zFileName = zFileName.substring(idx + 1);
							}
							mediaFiles.put(zFileName, zFile);
							
							// Write the file
							FileOutputStream fos = new FileOutputStream(zFile);
							int len;
				            while ((len = zis.read(buffer)) > 0) {
				            	fos.write(buffer, 0, len);
				            }
				            fos.close();
						}
					}
		            zis.closeEntry();
				}
				zis.close();
			}
			
			/*
			 * Process the CSV file
			 */
			log.info("Form Id: " + fId);
			if(savedFile != null && fId != 0) {

				String [] line;
				CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(savedFile)));
				line = reader.readNext();
				ArrayList<Column> columns = new ArrayList<Column> ();
				if(line != null && line.length > 0) {
					
					// Assume first line is the header
					for(int i = 0; i < line.length; i++) {
						String colName = line[i].replace("'", "''");	// Escape apostrophes
						
						// If this column is in the survey then add it to the list of columns to be processed
						Column col = getColumn(pstmtGetCol, pstmtGetChoices, colName);
						if(col != null) {
							col.index = i;
							if(col.geomCol != null) {
								// Do not add the geom columns to the list of columns to be parsed
								if(col.geomCol.equals("lon")) {
									lonIndex = i;
								} else if(col.geomCol.equals("lat")) {
									latIndex = i;
								}
							} else {
								columns.add(col);
							}
						}
					}
					
					log.info("Loading data from " + columns.size() + " columns out of " + line.length + " columns in the data file");
					
					/*
					 * Create the insert statement
					 */		
					boolean moreThanOneCol = false;
					StringBuffer sqlInsert = new StringBuffer("insert into " + tableName + "(");
					for(int i = 0; i < columns.size(); i++) {
						
						Column col = columns.get(i);
						
						if(i > 0) {
							moreThanOneCol = true;
							sqlInsert.append(",");
						}
						if(col.type.equals("select")) {
							for(int j = 0; j < col.choices.size(); j++) {
								if(j > 0) {
									sqlInsert.append(",");
								}
								sqlInsert.append(UtilityMethodsEmail.cleanName(col.name + "__" + col.choices.get(j)));
							}
						} else {
							sqlInsert.append(UtilityMethodsEmail.cleanName(col.name));
						}

					}
					
					// Add the geometry column if latitude and longitude were provided in the csv
					if(lonIndex >= 0 && latIndex >= 0 ) {
						if(moreThanOneCol) {
							sqlInsert.append(",");
						}
						hasGeopoint = true;
						sqlInsert.append("the_geom");
					}
					
					sqlInsert.append(") values("); 
					for(int i = 0; i < columns.size(); i++) {
						
						Column col = columns.get(i);
						
						if(i > 0) {
							sqlInsert.append(",");
						}
						if(col.type.equals("select")) {
							
							for(int j = 0; j < col.choices.size(); j++) {
								if(j > 0) {
									sqlInsert.append(",");
								}
								sqlInsert.append("?");
							}
						} else {
							sqlInsert.append("?");
						}
					}
					
					// Add the geometry value
					if(hasGeopoint) {
						if(moreThanOneCol) {
							sqlInsert.append(",");
						}
						sqlInsert.append("ST_GeomFromText('POINT(' || ? || ' ' || ? ||')', 4326)");
					}
					sqlInsert.append(");");
					
					pstmtInsert = results.prepareStatement(sqlInsert.toString());
					
					/*
					 * Get the data
					 */
					log.info("userevent: " + request.getRemoteUser() + " : loading task file : Previous contents are" + (clear_existing ? " deleted" : " preserved"));
					results.setAutoCommit(false);
					if(clear_existing) {
						String sqlDeleteExisting = "truncate " + tableName + ";";
						pstmtDeleteExisting = results.prepareStatement(sqlDeleteExisting);
						
						log.info("Clearing results: " + pstmtDeleteExisting.toString());
						pstmtDeleteExisting.executeUpdate();
					}
					
					while ((line = reader.readNext()) != null) {
						
						int index = 1;
						for(int i = 0; i < columns.size(); i++) {
							Column col = columns.get(i);
							String value = line[col.index];				

							// If the data references a media file then process the attachement
							if(col.type.equals("audio") || col.type.equals("video") || col.type.equals("image")) {
								
								File srcPathFile = mediaFiles.get(value);
								if(srcPathFile != null) {
									value = GeneralUtilityMethods.createAttachments(
										value, 
										srcPathFile, 
										basePath, 
										sIdent);
								}
							}
							
							if(col.type.equals("select")) {
								String [] choices = value.split("\\s");
								for(int k = 0; k < col.choices.size(); k++) {
									String cVal = UtilityMethodsEmail.cleanName(col.choices.get(k));
									boolean hasChoice = false;
									for(int l = 0; l < choices.length; l++) {
										if(cVal.equals(UtilityMethodsEmail.cleanName(choices[l]))) {
											hasChoice = true;
											break;
										}
									}
									if(hasChoice) {
										pstmtInsert.setInt(index++, 1);
									} else {
										pstmtInsert.setInt(index++, 0);
									}
									
								}
							} else if(col.type.equals("int")) {
								int iVal = 0;
								try { iVal = Integer.parseInt(value);} catch (Exception e) {}
								pstmtInsert.setInt(index++, iVal);
							} else if(col.type.equals("decimal")) {
								double dVal = 0.0;
								try { dVal = Double.parseDouble(value);} catch (Exception e) {}
								pstmtInsert.setDouble(index++, dVal);
							} else {
								pstmtInsert.setString(index++, value);
							}
							
						}
						
						// Add the geopoint value if it exists
						if(hasGeopoint) {
							String lon = line[lonIndex];
							String lat = line[latIndex];
							if(lon == null) {
								lon = "0.0";
							}
							if(lat == null) {
								lat = "0.0";
							}
							pstmtInsert.setString(index++, lon);
							pstmtInsert.setString(index++, lat);
							
						}
						log.info("Inserting row: " + pstmtInsert.toString());
						pstmtInsert.executeUpdate();
						
				    }
					results.commit();

				}
			}
			
				
		} catch (AuthorisationException e) {
			log.log(Level.SEVERE,"", e);
			
			response = Response.status(Status.FORBIDDEN).entity("Cannot load tasks from a file to this survey").build();
			
		} catch (NotFoundException e) {
			log.log(Level.SEVERE,"", e);
			
			throw new NotFoundException();
			
		} catch (Exception e) {
			response = Response.serverError().build();
			try { results.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			log.log(Level.SEVERE,"", e);
			
		} finally {
			try {if (pstmtGetCol != null) {pstmtGetCol.close();}} catch (SQLException e) {}
			try {if (pstmtGetFormId != null) {pstmtGetFormId.close();}} catch (SQLException e) {}
			try {if (pstmtGetChoices != null) {pstmtGetChoices.close();}} catch (SQLException e) {}
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"", e);
			}
			try {
				if (results != null) {
					results.setAutoCommit(true);
					results.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Check to see if a question is in a form
	 */
	private Column getColumn(PreparedStatement pstmtGetCol, PreparedStatement pstmtGetChoices, String qName) throws SQLException {
		Column col = null;
		String geomCol = null;
		
		String colName = UtilityMethodsEmail.cleanName(qName);	// Convert question name to a column name
		
		// Cater for lat, lon columns which map to a geopoint
		if(colName.equals("lat") || colName.equals("lon")) {
			geomCol = colName;
			colName = "the_geom";
		} 
		
		pstmtGetCol.setString(2, colName);
		ResultSet rs = pstmtGetCol.executeQuery();
		if(rs.next()) {
			// This column name is in the survey
			col = new Column();
			col.name = colName;
			col.geomCol = geomCol;				// This column holds the latitude or the longitude or neither
			col.type = rs.getString("qtype");
			
			if(col.type.startsWith("select")) {
				
				// Get choices for this select question
				int qId = rs.getInt("q_id");
				
				col.choices = new ArrayList<String> ();
				pstmtGetChoices.setInt(1, qId);
				log.info("Get choices:" + pstmtGetChoices.toString());
				ResultSet rsChoices = pstmtGetChoices.executeQuery();
				while(rsChoices.next()) {
					col.choices.add(rsChoices.getString("ovalue"));
				}
			}
		}
		return col;
	}
	
	/*
	 * Delete tasks
	 */
	@DELETE
	public Response deleteTasks(@Context HttpServletRequest request,
			@FormParam("settings") String settings) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		log.info("Assignment:" + settings);
		Type type = new TypeToken<ArrayList<Assignment>>(){}.getType();		
		ArrayList<Assignment> aArray = new Gson().fromJson(settings, type);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		for(int i = 0; i < aArray.size(); i++) {
			
			Assignment ass = aArray.get(i);		
			a.isValidTask(connectionSD, request.getRemoteUser(), ass.task_id);
	
		}
		// End Authorisation

		PreparedStatement pstmtCount = null;
		PreparedStatement pstmtUpdate = null;
		PreparedStatement pstmtDelete = null;
		PreparedStatement pstmtDeleteEmptyGroup = null;

		try {
			//connectionSD.setAutoCommit(false);
			//String countSQL = "select count(*) from tasks t, assignments a " +
			//		"where t.id = a.task_id " +
			//		"and t.id = ?";
			//pstmtCount = connectionSD.prepareStatement(countSQL);
			
			//String updateSQL = "update assignments set status = 'cancelled' where task_id = ? " +
			//		"and (status = 'new' " +
			//		"or status = 'accepted' " + 
			//		"or status = 'pending'); "; 
			//pstmtUpdate = connectionSD.prepareStatement(updateSQL);
			
			String deleteSQL = "delete from tasks where id = ?; "; 
			pstmtDelete = connectionSD.prepareStatement(deleteSQL);
			
			//String deleteEmptyGroupSQL = "delete from task_group tg where not exists (select 1 from tasks t where t.tg_id = tg.tg_id);";
			//pstmtDeleteEmptyGroup = connectionSD.prepareStatement(deleteEmptyGroupSQL);
			
			for(int i = 0; i < aArray.size(); i++) {
				
				Assignment a = aArray.get(i);
				
				// Check to see if the task has any assignments
				//pstmtCount.setInt(1, a.task_id);
				//ResultSet rs = pstmtCount.executeQuery();
				//int countAss = 0;
				//if(rs.next()) {
				//	countAss = rs.getInt(1);
				//}
				
				//if(countAss > 0) {
				//	log.info(updateSQL + " : " + a.task_id);
				//	pstmtUpdate.setInt(1, a.task_id);
				//	pstmtUpdate.execute();
				//} else {

					pstmtDelete.setInt(1, a.task_id);
					log.info("SQL: " + pstmtDelete.toString());
					pstmtDelete.execute();
				//}
			}
			
			// Delete any task groups that have no tasks
			//pstmtDeleteEmptyGroup.execute();
			//connectionSD.commit();
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"", e);
			
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} finally {
			if (pstmtCount != null) try {pstmtCount.close();} catch (SQLException e) {};
			if (pstmtUpdate != null) try {pstmtUpdate.close();} catch (SQLException e) {};
			if (pstmtDelete != null) try {pstmtDelete.close();} catch (SQLException e) {};
			if (pstmtDeleteEmptyGroup != null) try {pstmtDeleteEmptyGroup.close();} catch (SQLException e) {};
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.info("Failed to close connection");
				log.log(Level.SEVERE,"", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Delete task group
	 * This web service takes no account of tasks that have already been assigned
	 */
	@DELETE
	@Path("/{taskGroupId}")
	public Response deleteTaskGroup(@Context HttpServletRequest request,
			@PathParam("taskGroupId") int tg_id) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		PreparedStatement pstmtDelete = null;

		try {
			
			
			String deleteSQL = "delete from task_group where tg_id = ?; "; 
			pstmtDelete = connectionSD.prepareStatement(deleteSQL);
			
			pstmtDelete.setInt(1, tg_id);
			log.info("SQL: " + pstmtDelete.toString());
			pstmtDelete.execute();

				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"", e);
			
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		} finally {
			
			if (pstmtDelete != null) try {pstmtDelete.close();} catch (SQLException e) {};
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.info("Failed to close connection");
				log.log(Level.SEVERE,"", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Mark cancelled tasks as deleted
	 * This may be required if tasks are allocated to a user who never updates them
	 */
	@Path("/cancelled/{projectId}")
	@DELETE
	public Response forceRemoveCancelledTasks(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId
			) { 

		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.info("Error: Can't find PostgreSQL JDBC Driver");
		    e.printStackTrace();
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-AllAssignments");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation

		PreparedStatement pstmtDelete = null;
		
		try {
			
			String deleteSQL = "delete from tasks t " +
					" where t.p_id = ? " +
					" and t.id in (select task_id from assignments a " +
					" where a.status = 'cancelled' or a.status = 'rejected'); "; 
					
			pstmtDelete = connectionSD.prepareStatement(deleteSQL);

			log.info(deleteSQL + " : " + projectId);
			pstmtDelete.setInt(1, projectId);
			pstmtDelete.execute();
			
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"", e);
			try {
				connectionSD.rollback();
			} catch (Exception ex) {
				
			}
		} finally {
			if (pstmtDelete != null) try {pstmtDelete.close();} catch (SQLException e) {};
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"", e);
			}
		}
		
		return response;
	}
}



