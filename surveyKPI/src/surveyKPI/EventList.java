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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/eventList")
public class EventList extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(EventList.class.getName());


	public EventList() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
	}

	/*
	 * Retry a notification
	 */
	@GET
	@Path("/retry/{messageId}")
	public Response retry(@Context HttpServletRequest request,
			@PathParam("messageId") int messageId,
			@PathParam("notificationId") int notificationId
			) {
		
		Response response = null;
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList - retry");
		a.isAuthorised(sd, user);
		if(messageId != 0) {
			a.isValidMessage(sd, request.getRemoteUser(), messageId);
		}
		
		String sqlNot = "delete from notification_log where message_id = ?";
		PreparedStatement pstmtNot = null;
		
		String sqlMsg = "update message set processed_time = null where id = ?";
		PreparedStatement pstmtMsg = null;
		
		try {
			
			// Delete notification
			pstmtNot = sd.prepareStatement(sqlNot);
			pstmtNot.setInt(1,messageId);
			pstmtNot.executeUpdate();
			
			// Set message as unprocessed
			pstmtMsg = sd.prepareStatement(sqlMsg);
			pstmtMsg.setInt(1,messageId);
			pstmtMsg.executeUpdate();
			
			
		} catch (SQLException e) {
				
			log.log(Level.SEVERE, "SQL Exception", e);
		
		} finally {
			try {if (pstmtNot != null) {pstmtNot.close();}} catch (SQLException e) {}
			try {if (pstmtMsg != null) {pstmtMsg.close();}} catch (SQLException e) {}
			SDDataSource.closeConnection("surveyKPI-EventList - retry", sd);
		}
		
		return response;
	}
	
	// Respond with JSON 
	@GET
	@Produces("application/json")
	@Path("/{projectId}/{sName}")
	public String getEvents(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@PathParam("sName") String sName,
			@QueryParam("hide_errors") boolean hideErrors,
			@QueryParam("hide_duplicates") boolean hideDuplicates,
			@QueryParam("hide_merged") boolean hideMerged,
			@QueryParam("hide_success") boolean hideSuccess,
			@QueryParam("hide_not_loaded") boolean hideNotLoaded,
			@QueryParam("hide_upload_errors") boolean hideUploadErrors,
			@QueryParam("is_forward") boolean is_forward,
			@QueryParam("start_key") int start_key,
			@QueryParam("rec_limit") int rec_limit) {
		
		log.info("Get events, Project id: " + projectId + " Survey id: " + sName);
		HashMap<Integer, String> surveyNames = new HashMap<Integer, String> ();
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(sd, user);
		if(projectId != 0) {
			a.isValidProject(sd, request.getRemoteUser(), projectId);
		}
		// End Authorisation
		
		if(rec_limit == 0) {
			rec_limit = 200;	// Default for number of records to return
		}
		String filter = "";
		if(start_key > 0) {
			filter = " AND ue.ue_id < ? ";
		}
		
		JSONObject jo = new JSONObject();
		
		String subscriberSelect = "";
		if(!is_forward) {
			subscriberSelect = "AND (se.subscriber = 'results_db' or se.subscriber is null) ";
		} else {
			subscriberSelect = "AND se.subscriber != 'results_db' ";
		}

		
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			int oId = GeneralUtilityMethods.getOrganisationId(sd, user);
			
			// Record limiting
			JSONObject jTotals = new JSONObject();
			jo.put("totals", jTotals);
			jTotals.put("start_key", start_key);
			jTotals.put("rec_limit", rec_limit);
			jTotals.put("more_recs", 0);	// Default
			
			String sql = null;
			if(sName == null || sName.equals("_all")) {
				String projSelect = "";
				if(projectId != 0) {	// set to 0 to get all available projects
					projSelect = "AND up.p_id = ? ";
				}
				sql = "SELECT se.se_id, ue.ue_id, ue.s_id, ue.upload_time, ue.user_name, ue.imei, ue.file_name, ue.survey_name, ue.location, "
						+ "se.status as se_status, se.reason as se_reason, "
						+ "se.dest as dest, ue.ident,"
						+"ue.status as upload_status, ue.reason as upload_reason "
						+ "from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ projSelect
						+ filter
						+ " order by ue.ue_id desc;";
				pstmt = sd.prepareStatement(sql);
				int argIdx = 1;
				pstmt.setString(argIdx++, user);
				pstmt.setInt(argIdx++, oId);
				if(projectId != 0) {
					pstmt.setInt(argIdx++, projectId);
				}
				if(start_key > 0) {
					pstmt.setInt(argIdx++, start_key);
				}
				
			} else {
				sql = "SELECT se.se_id, ue.ue_id, ue.s_id, ue.upload_time, ue.user_name, ue.imei, ue.file_name, ue.survey_name, ue.location, " +
						"se.status as se_status, se.reason as se_reason, " +
						//"ue.status as ue_status, ue.reason as ue_reason, " +
						"se.dest as dest, ue.ident, " +
						"ue.status as upload_status, ue.reason as upload_reason " +
						"FROM upload_event ue " +
						"left outer join subscriber_event se " +
						"on ue.ue_id = se.ue_id " +
						"inner join user_project up " +
						"on ue.p_id = up.p_id " +
						"inner join users u " +
						"on up.u_id = u.id " +
						"where u.ident = ? " +
						"and ue.s_id = ? " +
						"and up.p_id = ? " +
						subscriberSelect +
						filter +
						" ORDER BY ue.ue_id desc;";
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, Integer.parseInt(sName));
				pstmt.setInt(3, projectId);
				if(start_key > 0) {
					pstmt.setInt(4, start_key);
				}
			}

			 log.info("Events List: " + pstmt.toString());

			 resultSet = pstmt.executeQuery();
			 JSONArray ja = new JSONArray();	
			 int countRecords = 0;
			 int maxRec = 0;
			 while (resultSet.next()) {
				 String status = resultSet.getString("se_status");
				 String se_reason = resultSet.getString("se_reason");
				 String upload_status = resultSet.getString("upload_status");
				 String upload_reason = resultSet.getString("upload_reason");
				 
				 if(
						 (status == null && !hideNotLoaded) ||
						 (status != null && !hideSuccess && status.equals("success")) ||
						 (status != null && !hideErrors && status.equals("error") && (se_reason == null || !se_reason.startsWith("Duplicate survey:"))) ||
						 (status != null && !hideDuplicates && status.equals("error") && (se_reason != null && se_reason.startsWith("Duplicate survey:")) ||
						 (status != null && !hideMerged && status.equals("merged")) ||
						 (upload_status != null && upload_status.equals("error") && !hideUploadErrors)
						 )) {
					
					
					// Only return max limit
					if(countRecords++ >= rec_limit) {
						// We have at least one more record than we want to return
						jTotals.put("more_recs", 1);	// Ideally we should record the number of records still to be returned
						countRecords--;					// Set to the number of records actually returned
						break;
					}
					
					JSONObject jr = new JSONObject();
					jr.put("type", "Feature");
					
					// Add Geometry
					JSONObject jg = null;
					String geom = resultSet.getString("location");					 
					if(geom != null) {
						JSONArray jCoords = new JSONArray();
						String[] coords = geom.split(" ");
						if(coords.length == 2) {
							jCoords.put(Double.parseDouble(coords[0]));
							jCoords.put(Double.parseDouble(coords[1]));
							jg = new JSONObject();
							jg.put("type", "Point");
							jg.put("coordinates", jCoords);
							jr.put("geometry", jg);
						}
					}
					
					// Add the properties
					JSONObject jp = new JSONObject();
					jp.put("se_id", resultSet.getInt("se_id")); 
					jp.put("ue_id", resultSet.getInt("ue_id"));
					jp.put("upload_time", resultSet.getString("upload_time"));
					jp.put("user_name", resultSet.getString("user_name"));
					jp.put("file_name", resultSet.getString("file_name"));
					int sId = resultSet.getInt("s_id");
					String nm = surveyNames.get(sId);
					if(nm == null) {
						nm = GeneralUtilityMethods.getSurveyName(sd, sId);
						if(nm == null) { // erased survey
							nm = resultSet.getString("survey_name");
						}
					}
					jp.put("survey_name", nm);
					jp.put("dest", resultSet.getString("dest"));
					jp.put("imei", resultSet.getString("imei"));
					jp.put("ident", resultSet.getString("ident"));
					if(upload_status != null && upload_status.equals("error")) {
						jp.put("status", "upload error");
						jp.put("reason", upload_reason);
					} else {
						if(status == null) {					// Not loaded by subscriber
								status = "not_loaded";
								se_reason = "Not added to database";
						}
						jp.put("status", status);
						jp.put("reason", se_reason);
					}
					jr.put("properties", jp);
					ja.put(jr);
					
					maxRec = resultSet.getInt("ue_id");
				 }
				 
				 jTotals.put("max_rec", maxRec);
				 jTotals.put("returned_count", countRecords);
				 String uploadTime = resultSet.getString("upload_time");
				 String aUploadTime [] = uploadTime.split(" ");
				 jTotals.put("to_date", aUploadTime[0]);
				 if(countRecords == 1) {	 
					 if(aUploadTime.length > 1) {
						 jTotals.put("from_date", aUploadTime[0]);
					 }
				 }
				 
				 jo.put("type", "FeatureCollection");
				 jo.put("features", ja);
			 }
			 

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {
			try {
				if(resultSet != null) {
					resultSet.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", sd);
		}
		
		return jo.toString();
	}

	/*
	 * Get the individual notification events
	 */
	@GET
	@Produces("application/json")
	@Path("/notifications/{projectId}/{sName}")
	public String getNotificationEvents(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@PathParam("sName") String sName,
			@QueryParam("hide_errors") boolean hideErrors,
			@QueryParam("hide_success") boolean hideSuccess,
			@QueryParam("start_key") int start_key,
			@QueryParam("rec_limit") int rec_limit) {
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(sd, user);
		// End Authorisation
		
		if(rec_limit == 0) {
			rec_limit = 200;	// Default for number of records to return
		}
		String filter = "";
		if(start_key > 0) {
			filter = " and n.id < ? ";
		}
		
		int sId = 0;
		if(sName != null && !sName.equals("_all")) {
			sId = Integer.parseInt(sName);
		}
		
		JSONObject jo = new JSONObject();

		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			// Record limiting
			JSONObject jTotals = new JSONObject();
			jo.put("totals", jTotals);
			jTotals.put("start_key", start_key);
			jTotals.put("rec_limit", rec_limit);
			jTotals.put("more_recs", 0);	// Default
			
			String sql = null;
			String projSurveySelect = "";
			if(sId > 0) {
				projSurveySelect = "and n.s_id = ? ";
			} else if(projectId > 0) {
				projSurveySelect = "and n.p_id = ? ";
			}
				
			sql = "SELECT n.id, n.status, n.notify_details, n.status_details, n.event_time, n.message_id, type " +
					"from notification_log n, users u " +
					"where u.ident = ? " +
					"and u.o_id = n.o_id " +
					filter +
					projSurveySelect +
					" ORDER BY n.id desc";
		
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			int argIdx = 2;
			if(start_key > 0) {
				pstmt.setInt(argIdx++, start_key);
			}
			if(sId > 0) {
				pstmt.setInt(argIdx++, sId);
			} else if(projectId > 0) {
				pstmt.setInt(argIdx++, projectId);
			}
			
			log.info("Events List: " + pstmt.toString());

			 resultSet = pstmt.executeQuery();
			 JSONArray ja = new JSONArray();	
			 int countRecords = 0;
			 int maxRec = 0;
			 while (resultSet.next()) {
				 String status = resultSet.getString("status");
				 if(
						 (status != null && !hideSuccess && status.toLowerCase().equals("success")) ||
						 (status != null && !hideErrors && status.toLowerCase().equals("error")) 
						
						 ) {
					
					
					// Only return max limit
					if(countRecords++ >= rec_limit) {
						// We have at least one more record than we want to return
						jTotals.put("more_recs", 1);	// Ideally we should record the number of records still to be returned
						countRecords--;					// Set to the number of records actually returned
						break;
					}
					
					JSONObject jr = new JSONObject();
					jr.put("type", "Feature");
					
					// Add the properties
					JSONObject jp = new JSONObject();
					jp.put("id", resultSet.getInt("id")); 
					jp.put("notify_details", resultSet.getString("notify_details"));
					jp.put("status", resultSet.getString("status"));
					jp.put("status_details", resultSet.getString("status_details"));
					jp.put("event_time", resultSet.getString("event_time"));
					jp.put("message_id", resultSet.getString("message_id"));
					jp.put("type", resultSet.getString("type"));
					jr.put("properties", jp);
					ja.put(jr);
					
					maxRec = resultSet.getInt("id");
				 }
				 
				 jTotals.put("max_rec", maxRec);
				 jTotals.put("returned_count", countRecords);
				 String eventTime = resultSet.getString("event_time");
				 String aEventTime [] = eventTime.split(" ");
				 jTotals.put("to_date", aEventTime[0]);
				 if(countRecords == 1) {	 
					 if(aEventTime.length > 1) {
						 jTotals.put("from_date", aEventTime[0]);
					 }
				 }
				 
				 jo.put("type", "FeatureCollection");
				 jo.put("features", ja);
			 }
			 

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {
			
			try {if(resultSet != null) {resultSet.close();}	} catch (SQLException e) {}
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", sd);
		}
		
		return jo.toString();
	}


	private class StatusTotal {
		String key;
		String dest;
		int success = 0;
		int errors = 0;
		int duplicates = 0;
		int merged = 0;
		int notLoaded = 0;
		int uploadErrors = 0;
	}
	
	// Get totals for notifications
	@GET
	@Produces("application/json")
	@Path("/notifications/{projectId}/{sName}/totals")
	public String getNotificationTotals(@Context HttpServletRequest request, 
			@PathParam("projectId") int pId,
			@PathParam("sName") String sName,
			@QueryParam("hide_errors") boolean hideErrors,
			@QueryParam("hide_success") boolean hideSuccess
			) {
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(sd, user);
		// End Authorisation
		
		HashMap<String,StatusTotal> sList = new HashMap<String,StatusTotal> ();
		JSONObject jo = new JSONObject();
		
		PreparedStatement pstmt = null;

		int sId = 0;
		if(sName != null && !sName.equals("_all")) {
			sId = Integer.parseInt(sName);
		}
		
		try {
			if(!hideSuccess) {
				addNotificationTotals("success", user, pstmt, sd,	sList, pId, sId); 
			}
			if(!hideErrors) {
				addNotificationTotals("error", user, pstmt, sd, sList, pId, sId); 
			}
			
			
			ArrayList<String> totals = new ArrayList<String> ();
			for (String uniqueTotal : sList.keySet()) {
				totals.add(uniqueTotal);
			}
			
			// Create JSON array
			JSONArray ja = new JSONArray();	
			StatusTotal st = sList.get("notifications");
			
			JSONObject jr = new JSONObject();
			jr.put("type", "Feature");
					
			// Add the properties
			JSONObject jp = new JSONObject();
			jp.put("key", "notifications");
			if(!hideSuccess) {
				jp.put("success", st.success);
			}
			if(!hideErrors) {
				jp.put("errors", st.errors);
			}
			jr.put("properties", jp);
			ja.put(jr);
				 
			jo.put("type", "FeatureCollection");
			jo.put("features", ja);

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", sd);
		}
		
		return jo.toString();
	}
	
	// Get totals for events
	@GET
	@Produces("application/json")
	@Path("/{projectId}/{sName}/totals")
	public String getEventTotals(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@PathParam("sName") String sName,
			@QueryParam("hide_errors") boolean hideErrors,
			@QueryParam("hide_duplicates") boolean hideDuplicates,
			@QueryParam("hide_merged") boolean hideMerged,
			@QueryParam("hide_success") boolean hideSuccess,
			@QueryParam("hide_not_loaded") boolean hideNotLoaded,
			@QueryParam("hide_upload_errors") boolean hideUploadErrors,
			@QueryParam("groupby") String groupby,
			@QueryParam("is_forward") boolean is_forward) {
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(sd, user);
		if(projectId != 0) {	// 0 is not a valid project but represents all projects
			a.isValidProject(sd, request.getRemoteUser(), projectId);
		}
		// End Authorisation
		
		HashMap<String,StatusTotal> sList = new HashMap<String,StatusTotal> ();
		JSONObject jo = new JSONObject();

		try {
			int oId = GeneralUtilityMethods.getOrganisationId(sd, user);
			
			if(!hideSuccess) {
				addStatusTotals("success", sName, projectId, user, sd,	groupby, sList, is_forward, oId); 
			}
			if(!hideErrors) {
				addStatusTotals("errors", sName, projectId, user, sd,	groupby, sList, is_forward, oId); 
			}
			if(!hideDuplicates) {
				addStatusTotals("duplicates", sName, projectId, user, sd,	groupby, sList, is_forward, oId); 
			}
			if(!hideMerged) {
				addStatusTotals("merged", sName, projectId, user, sd,	groupby, sList, is_forward, oId); 
			}
			if(!hideNotLoaded) {
				addStatusTotals("not_loaded", sName, projectId, user, sd, groupby, sList, is_forward, oId); 
			}
			if(!hideUploadErrors) {
				addStatusTotals("upload_errors", sName, projectId, user, sd, groupby, sList, is_forward, oId); 
			}
			
			
			ArrayList<String> totals = new ArrayList<String> ();
			for (String uniqueTotal : sList.keySet()) {
				totals.add(uniqueTotal);
			}
		
			// Sort the list of keys 
			List<String> sortedTotals = totals.subList(0, totals.size());
			Collections.sort(sortedTotals);
			// If the totals are grouped by day, week or month then sort in descending order
			if(groupby != null && (groupby.equals("day") || groupby.equals("week") || groupby.equals("month"))) {
				Collections.reverse(sortedTotals);
			}
			
			// Create JSON array
			JSONArray ja = new JSONArray();			 
			for (int i = 0; i < sortedTotals.size(); i++) {
				String key = sortedTotals.get(i);
				StatusTotal st = sList.get(key);
				
				JSONObject jr = new JSONObject();
				jr.put("type", "Feature");
					
				// Add the properties
				JSONObject jp = new JSONObject();
				jp.put("key", st.key);
				jp.put("dest", st.dest);
				if(!hideSuccess) {
					jp.put("success", st.success);
				}
				if(!hideErrors) {
					jp.put("errors", st.errors);
				}
				if(!hideDuplicates) {
					jp.put("duplicates", st.duplicates);
				}
				if(!hideMerged) {
					jp.put("merged", st.merged);
				}
				if(!hideNotLoaded) {
					jp.put("not_loaded", st.notLoaded);
				}
				if(!hideUploadErrors) {
					jp.put("upload_errors", st.uploadErrors);
				}
				jr.put("properties", jp);
				ja.put(jr);
			}
				 
			jo.put("type", "FeatureCollection");
			jo.put("features", ja);

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {

			SDDataSource.closeConnection("surveyKPI-EventList", sd);
		}
		
		return jo.toString();
	}
	
	private void addStatusTotals(
			String status, 
			String sName, 
			int projectId,
			String user,
			Connection sd,
			String groupby,
			HashMap<String,StatusTotal> sList,
			boolean isForward,
			int oId) throws SQLException {
		
		PreparedStatement pstmt = null;
		
		try {
			String selectStatus = null;
			if(status.equals("success")) {
				selectStatus = "AND se.status = 'success' ";
			} else if(status.equals("errors")) {
				selectStatus = "AND (coalesce(se.status,'') = 'error' AND coalesce(se.reason,'') not like 'Duplicate survey:%') ";
			} else if(status.equals("not_loaded")) {
				selectStatus = "AND ue.status != 'error' and se.status is null ";
			} else if(status.equals("duplicates")) {
				selectStatus = "AND se.status = 'error' AND se.reason like 'Duplicate survey:%' ";
			} else if(status.equals("merged")) {
				selectStatus = "AND se.status = 'merged' ";
			} else if(status.equals("upload_errors")) {
				selectStatus = "AND ue.status = 'error' ";
			}
			
			String subscriberSelect = "";
			if(!isForward) {
				subscriberSelect = "AND (se.subscriber = 'results_db' or se.subscriber is null) ";
			} else {
				subscriberSelect = "AND se.subscriber != 'results_db' and se.subscriber is not null ";
			}
			
			String sql = null;
			if(sName == null || sName.equals("_all")) {
				String projSelect = "";
				if(projectId != 0) {	// set to 0 to get all available projects
					projSelect = " AND up.p_id = ? ";
				}
				String aggregate;
				String getDest;
				if(isForward) {
					aggregate = "ue.ident, se.dest";
					getDest = ",se.dest ";
				} else {
					aggregate = "ue.ident";
					getDest = "";
				}
				
				sql = "SELECT count(*), ue.ident "
						+ getDest
						+ "from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ selectStatus
						+ projSelect
						+ "GROUP BY " + aggregate
						+ " ORDER BY " + aggregate + " desc";
	
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, oId);
				if(projectId != 0) {
					pstmt.setInt(3, projectId);
				}
			} else if(groupby == null || groupby.equals("device")) {
				
				String aggregate;
				String getDest;
				if(isForward) {
					aggregate = "ue.imei, se.dest";
					getDest = ",se.dest ";
				} else {
					aggregate = "ue.imei";
					getDest = "";
				}
				
				sql = "SELECT count(*), ue.imei "
						+ getDest
						+ "from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and ue.s_id = ? "
						+ "and up.p_id = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ selectStatus
						+ " group by " + aggregate
						+ " order by " + aggregate + " asc";
				
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, Integer.parseInt(sName));
				pstmt.setInt(3, projectId);
				pstmt.setInt(4, oId);
				
			} else if(groupby.equals("month")) {
				
				String aggregate = "extract(year from upload_time) || '-' || lpad(cast(extract(month from upload_time) as varchar), 2, '0')";
				
				if(isForward) {
	
					aggregate += ",se.dest ";
				} 
				
				sql = "SELECT count(*), " 
						+ aggregate
						+ " from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and ue.s_id = ? "
						+ "and up.p_id = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ selectStatus
						+ " group by " + aggregate
						+ " order by " + aggregate + " desc";
				
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, Integer.parseInt(sName));
				pstmt.setInt(3, projectId);
				pstmt.setInt(4, oId);
			} else if(groupby.equals("week")) {			
	
				String aggregate = "extract(year from upload_time) || '-' || lpad(cast(extract(week from upload_time) as varchar), 2, '0')";
				if(isForward) {			
					aggregate += ", se.dest ";
				} 
				
				sql = "SELECT count(*), " 
						+ aggregate
						+ " from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and ue.s_id = ? "
						+ "and up.p_id = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ selectStatus
						+ " group by " + aggregate
						+ " order by " + aggregate + " desc";
				
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, Integer.parseInt(sName));
				pstmt.setInt(3, projectId);
				pstmt.setInt(4, oId);
				
			} else if(groupby.equals("day")) {
				
				String aggregate = "extract(year from upload_time) || '-' || "
						+ "lpad(cast(extract(month from upload_time) as varchar), 2, '0') || '-' || "
						+ "lpad(cast(extract(day from upload_time) as varchar), 2, '0') ";	
				if(isForward) {
					aggregate += ",se.dest ";
				}
				
				sql = "SELECT count(*), " 
						+ aggregate
						+ " from upload_event ue "
						+ "left outer join subscriber_event se "
						+ "on ue.ue_id = se.ue_id "
						+ "inner join user_project up "
						+ "on ue.p_id = up.p_id "
						+ "inner join users u "
						+ "on up.u_id = u.id "
						+ "inner join project p "
						+ "on up.p_id = p.id "
						+ "where u.ident = ? "
						+ "and ue.s_id = ? "
						+ "and up.p_id = ? "
						+ "and p.o_id = ? "
						+ subscriberSelect
						+ selectStatus
						+ " group by " + aggregate
						+ " order by " + aggregate + " desc";
				
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				pstmt.setInt(2, Integer.parseInt(sName));
				pstmt.setInt(3, projectId);
				pstmt.setInt(4, oId);
			}
	
			log.info("Get totals for events: " + pstmt.toString());
	
			 ResultSet resultSet = pstmt.executeQuery();
			 while (resultSet.next()) {
				 int count = resultSet.getInt(1);
				 String key = resultSet.getString(2);
				 String dest = "";
				 if(isForward) {
					 dest = resultSet.getString(3);
				 }
				 
				 if(sName == null || sName.equals("_all")) {
					 // Convert survey ident to display name
					 String sIdent = key;
					 String nm = GeneralUtilityMethods.getSurveyNameFromIdent(sd, sIdent);
					 if(nm != null) {
						 key = nm;
					 }
				 }
				 StatusTotal st = sList.get(key + dest);
				
				 if(st == null) {
					 st = new StatusTotal();
					 sList.put(key + dest, st);
					 st.key = key;
					 st.dest = dest;
				 }
				 if(status.equals("success")) {
					 st.success = count;
				 } else if(status.equals("errors")) {
					 st.errors = count;
				 } else if(status.equals("duplicates")) {
					 st.duplicates = count;
				 } else if(status.equals("merged")) {
					 st.merged = count;
				 } else if(status.equals("not_loaded")) {
					 st.notLoaded = count;
				 } else if(status.equals("upload_errors")) {
					 st.uploadErrors = count;
				 }
			 }
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
	}
	
	private void addNotificationTotals(String status, 
			String user,
			PreparedStatement pstmt, 
			Connection sd,
			HashMap<String,StatusTotal> sList,
			int pId,
			int sId) throws SQLException {
		
		String sql = null;
		
		String filter = "";
		if(sId > 0) {		// surveyId is always a stronger filter than projectId
			filter = "and n.s_id = ?";
		} else if(pId > 0) {				
			filter = "and n.p_id = ?";
		}
			
		sql = "SELECT count(*) " +
				"from notification_log n, users u " +
				"where u.ident = ? " +
				"and n.o_id = u.o_id " +
				"and n.status = ?" +
				filter +
				";";

		try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sql);
		pstmt.setString(1, user);			
		pstmt.setString(2, status);
		if(sId > 0) {
			pstmt.setInt(3, sId);
		} else if(pId > 0) {
			pstmt.setInt(3, pId);
		}

		log.info("Event totals: " + pstmt.toString());
		ResultSet resultSet = pstmt.executeQuery();
		if (resultSet.next()) {
			int count = resultSet.getInt(1);
			 
			StatusTotal st = sList.get("notifications");			
			if(st == null) {
				st = new StatusTotal();
				sList.put("notifications", st);
				st.key = "notifications";
			 }
			 if(status.equals("success")) {
				 st.success = count;
			 } else if(status.equals("error")) {
				 st.errors = count;
			 } 
		 }
	}
	
	// Get forms
	@GET
	@Produces("application/json")
	@Path("/{projectId}/{sName}/forms")
	public String getEventForms(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@PathParam("sName") String sName) {

		int sId = -1;
		
		if(sName.equals("_all")) {
			
		} else {
			sId = Integer.parseInt(sName);
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(sd, user);
		if(projectId != 0) {
			a.isValidProject(sd, request.getRemoteUser(), projectId);
		}
		// End Authorisation
		
		
		JSONArray ja = new JSONArray();
		PreparedStatement pstmt = null;

		try {
			
			String sql = null;
			ResultSet rs = null;
			
			if(sId == -1) {
				sql = "select u.name, u.ident, s.display_name, s.version, fd.form_ident, fd.form_version, fd.device_id "
						+ "from survey s inner join project p on s.p_id = p.id and p.id = ? "
						+ "inner join user_project up on p.id = up.p_id "
						+ "inner join users u on u.id = up.u_id "  
						+ "left outer join form_downloads fd on fd.u_id = u.id "
						+ "and fd.form_ident = s.ident "
						+ "where u.temporary = 'false' "
						+ "and s.deleted = 'false' "
						+ "and s.blocked = 'false' "
						+ "order by u.name, s.display_name asc";

			} else {
				sql = "select u.name, u.ident, s.display_name, s.version, fd.form_ident, fd.form_version, fd.device_id "
						+ "from survey s inner join project p on s.p_id = p.id and p.id = ? "
						+ "inner join user_project up on p.id = up.p_id "
						+ "inner join users u on u.id = up.u_id "  
						+ "left outer join form_downloads fd on fd.u_id = u.id "
						+ "and fd.form_ident = s.ident "
						+ "where u.temporary = 'false' "
						+ "and s.deleted = 'false' "
						+ "and s.blocked = 'false' "
						+ "and s.s_id = ? "
						+ "order by u.name, s.display_name asc";
			
			}
			
			pstmt = sd.prepareStatement(sql);
			if(sId == -1) {
				pstmt.setInt(1, projectId);
			} else {
				pstmt.setInt(1, projectId);
				pstmt.setInt(2, sId);
			}
			log.info("Get form downloads: " + pstmt.toString());
			rs = pstmt.executeQuery();
			
	
			while(rs.next()) {
				JSONObject jr = new JSONObject();
				
				jr.put("u_name", rs.getString(1));
				jr.put("u_ident", rs.getString(2));
				jr.put("survey_name", rs.getString(3));
				jr.put("survey_version", rs.getString(4));
				
				String dl_survey = rs.getString(5);
				if(dl_survey == null) {
					jr.put("no_download", true);
				}
				
				String dl_version = rs.getString(6);
				jr.put("download_version", (dl_version != null) ? dl_version : "");
				
				String dl_device = rs.getString(7);
				jr.put("device_id", (dl_device != null) ? dl_device : "");
			
				ja.put(jr);
			}
			

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e); 
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", sd);
		}
		
		return ja.toString();
	}

}

