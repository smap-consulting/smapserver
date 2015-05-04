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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/eventList")
public class EventList extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(EventList.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(EventList.class);
		return s;
	}
	
	public EventList() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
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
			@QueryParam("is_forward") boolean is_forward,
			@QueryParam("start_key") int start_key,
			@QueryParam("rec_limit") int rec_limit) {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		log.info("Get events, Project id: " + projectId + " Survey id: " + sName);

		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
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
				sql = "SELECT se.se_id, ue.ue_id, ue.upload_time, ue.user_name, ue.imei, ue.file_name, ue.survey_name, ue.location, " +
						"se.status as se_status, se.reason as se_reason, " +
						"ue.status as ue_status, ue.reason as ue_reason, " +
						"se.dest as dest, ue.ident " +
						"FROM upload_event ue " +
						"left outer join subscriber_event se " +
						"on ue.ue_id = se.ue_id " +
						"inner join user_project up " +
						"on ue.p_id = up.p_id " +
						"inner join users u " +
						"on up.u_id = u.id " +
						"WHERE u.ident = ? " +
						subscriberSelect +
						projSelect +
						filter +
						" ORDER BY ue.ue_id desc;";
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setString(1, user);
				int argIdx = 2;
				if(projectId != 0) {
					pstmt.setInt(argIdx++, projectId);
				}
				if(start_key > 0) {
					pstmt.setInt(argIdx++, start_key);
				}
				
			} else {
				sql = "SELECT se.se_id, ue.ue_id, ue.upload_time, ue.user_name, ue.imei, ue.file_name, ue.survey_name, ue.location, " +
						"se.status as se_status, se.reason as se_reason, " +
						"ue.status as ue_status, ue.reason as ue_reason, " +
						"se.dest as dest, ue.ident " +
						"FROM upload_event ue " +
						"left outer join subscriber_event se " +
						"on ue.ue_id = se.ue_id " +
						"WHERE ue.s_id = ? " +
						subscriberSelect +
						filter +
						" ORDER BY ue.ue_id desc;";
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, Integer.parseInt(sName));
				if(start_key > 0) {
					pstmt.setInt(2, start_key);
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
				 if(
						 (status == null && !hideNotLoaded) ||
						 (status != null && !hideSuccess && status.equals("success")) ||
						 (status != null && !hideErrors && status.equals("error") && (se_reason == null || !se_reason.startsWith("Duplicate survey:"))) ||
						 (status != null && !hideDuplicates && status.equals("error") && (se_reason != null && se_reason.startsWith("Duplicate survey:")) ||
						 (status != null && !hideMerged && status.equals("merged")))
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
					jp.put("survey_name", resultSet.getString("survey_name"));
					jp.put("dest", resultSet.getString("dest"));
					jp.put("imei", resultSet.getString("imei"));
					jp.put("ident", resultSet.getString("ident"));
					String reason = resultSet.getString("se_reason");
					if(status == null) {
						status = "not_loaded";
						reason = "Not added to database";
					}
					jp.put("status", status);
					jp.put("reason", reason);
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
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

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		// End Authorisation
		
		if(rec_limit == 0) {
			rec_limit = 200;	// Default for number of records to return
		}
		String filter = "";
		if(start_key > 0) {
			filter = " and n.id < ? ";
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
			if(sName == null || sName.equals("_all")) {
				String projSelect = "";
				if(projectId != 0) {	// set to 0 to get all available projects
					projSelect = "AND up.p_id = ? ";
				}
				sql = "SELECT n.id, n.status, n.notify_details, n.status_details, n.event_time " +
						"from notification_log n, users u " +
						"where u.ident = ? " +
						"and u.o_id = n.o_id " +
						projSelect +
						filter +
						" ORDER BY n.id desc;";
			
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setString(1, user);
				int argIdx = 2;
				if(start_key > 0) {
					pstmt.setInt(argIdx++, start_key);
				}	
			} else {
				System.out.println("Not all");
			}
			log.info("Events List: " + pstmt.toString());

			 resultSet = pstmt.executeQuery();
			 JSONArray ja = new JSONArray();	
			 int countRecords = 0;
			 int maxRec = 0;
			 while (resultSet.next()) {
				 String status = resultSet.getString("status");
				 String statusDetails = resultSet.getString("status_details");
				 if(
						 (status != null && !hideSuccess && status.equals("success")) ||
						 (status != null && !hideErrors && status.equals("error")) 
						
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
			try {
				if(resultSet != null) {
					resultSet.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
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
	}
	
	// Get totals for notifications
	@GET
	@Produces("application/json")
	@Path("/notifications/{projectId}/{sName}/totals")
	public String getNotificationTotals(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@PathParam("sName") String sName,
			@QueryParam("hide_errors") boolean hideErrors,
			@QueryParam("hide_success") boolean hideSuccess
			) {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		// End Authorisation
		
		HashMap<String,StatusTotal> sList = new HashMap<String,StatusTotal> ();
		JSONObject jo = new JSONObject();
		
		PreparedStatement pstmt = null;

		try {
			if(!hideSuccess) {
				addNotificationTotals("success", user, pstmt, connectionSD,	sList); 
			}
			if(!hideErrors) {
				addNotificationTotals("error", user, pstmt, connectionSD,	sList); 
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
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
			@QueryParam("groupby") String groupby,
			@QueryParam("is_forward") boolean is_forward) {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		HashMap<String,StatusTotal> sList = new HashMap<String,StatusTotal> ();
		JSONObject jo = new JSONObject();
		
		PreparedStatement pstmt = null;

		try {
			if(!hideSuccess) {
				addStatusTotals("success", sName, projectId, user, pstmt, connectionSD,	groupby, sList, is_forward); 
			}
			if(!hideErrors) {
				addStatusTotals("errors", sName, projectId, user, pstmt, connectionSD,	groupby, sList, is_forward); 
			}
			if(!hideDuplicates) {
				addStatusTotals("duplicates", sName, projectId, user, pstmt, connectionSD,	groupby, sList, is_forward); 
			}
			if(!hideMerged) {
				addStatusTotals("merged", sName, projectId, user, pstmt, connectionSD,	groupby, sList, is_forward); 
			}
			if(!hideNotLoaded) {
				addStatusTotals("not_loaded", sName, projectId, user, pstmt, connectionSD, groupby, sList, is_forward); 
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
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
		}
		
		return jo.toString();
	}
	
	private void addStatusTotals(String status, String sName, int projectId,
			String user,
			PreparedStatement pstmt, 
			Connection connectionSD,
			String groupby,
			HashMap<String,StatusTotal> sList,
			boolean isForward) throws SQLException {
		
		String selectStatus = null;
		if(status.equals("success")) {
			selectStatus = "AND se.status = 'success' ";
		} else if(status.equals("errors")) {
			selectStatus = "AND se.status = 'error' AND se.reason not like 'Duplicate survey:%' ";
		} else if(status.equals("not_loaded")) {
			selectStatus = "AND se.status is null ";
		} else if(status.equals("duplicates")) {
			selectStatus = "AND se.status = 'error' AND se.reason like 'Duplicate survey:%' ";
		} else if(status.equals("merged")) {
			selectStatus = "AND se.status = 'merged' ";
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
				aggregate = "ue.survey_name, se.dest";
				getDest = ",se.dest ";
			} else {
				aggregate = "ue.survey_name";
				getDest = "";
			}
			
			sql = "SELECT count(*), ue.survey_name " +
					getDest +
					"FROM upload_event ue, subscriber_event se, user_project up, users u " +
					"WHERE u.ident = ? " +
					"AND ue.ue_id = se.ue_id " +
					"AND ue.p_id = up.p_id " +
					"AND up.u_id = u.id " +
					"AND ue.s_id in (select s_id from survey where deleted = 'false') " +
					subscriberSelect +
					selectStatus +
					projSelect +
					"GROUP BY " + aggregate +
					" ORDER BY " + aggregate + " desc;";

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			if(projectId != 0) {
				pstmt.setInt(2, projectId);
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
			
			sql = "SELECT count(*), ue.imei " +
					getDest +
					"FROM upload_event ue, subscriber_event se, user_project up, users u " +
					"WHERE u.ident = ? " +
					"AND ue.s_id = ? " +
					"AND ue.ue_id = se.ue_id " +
					"AND ue.p_id = up.p_id " +
					"AND up.u_id = u.id " +
					"AND ue.s_id in (select s_id from survey where deleted = 'false') " +
					subscriberSelect +
					selectStatus +
					" GROUP BY " + aggregate +
					" ORDER BY " + aggregate + " ASC;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, Integer.parseInt(sName));
		} else if(groupby.equals("month")) {
			
			String aggregate = "extract(year from upload_time) || '-' || extract(month from upload_time)";
			
			if(isForward) {

				aggregate += ",se.dest ";
			} 
			
			sql = "SELECT count(*), " + aggregate +
					"FROM upload_event ue, subscriber_event se, user_project up, users u " +
					"WHERE u.ident = ? " +
					"AND ue.s_id = ? " +
					"AND ue.ue_id = se.ue_id " +
					"AND ue.p_id = up.p_id " +
					"AND up.u_id = u.id " +
					"AND ue.s_id in (select s_id from survey where deleted = 'false') " +
					subscriberSelect +
					selectStatus +
					" GROUP BY " + aggregate +
					" ORDER BY " + aggregate + " desc;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, Integer.parseInt(sName));
		} else if(groupby.equals("week")) {
			

			String aggregate = "extract(year from upload_time) || '-' || extract(week from upload_time)";
			if(isForward) {			
				aggregate += ",se.dest ";
			} 
			
			sql = "SELECT count(*), " + aggregate +
					"FROM upload_event ue, subscriber_event se, user_project up, users u " +
					"WHERE u.ident = ? " +
					"AND ue.s_id = ? " +
					"AND ue.ue_id = se.ue_id " +
					"AND ue.p_id = up.p_id " +
					"AND up.u_id = u.id " +
					"AND ue.s_id in (select s_id from survey where deleted = 'false') " +
					subscriberSelect +
					selectStatus +
					" GROUP BY " + aggregate +
					" ORDER BY " + aggregate + " desc;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, Integer.parseInt(sName));
		} else if(groupby.equals("day")) {
			
			String aggregate = "extract(year from upload_time) || '-' || extract(month from upload_time) || '-' || extract(day from upload_time)";	
			if(isForward) {
				aggregate += ",se.dest ";
			}
			
			sql = "SELECT count(*), " + aggregate +
					"FROM upload_event ue, subscriber_event se, user_project up, users u " +
					"WHERE u.ident = ? " +
					"AND ue.s_id = ? " +
					"AND ue.ue_id = se.ue_id " +
					"AND ue.p_id = up.p_id " +
					"AND up.u_id = u.id " +
					"AND ue.s_id in (select s_id from survey where deleted = 'false') " +
					subscriberSelect +
					selectStatus +
					" GROUP BY " + aggregate +
					" ORDER BY " + aggregate + " desc;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, Integer.parseInt(sName));
		}

		log.info("Get events: " + pstmt.toString());

		 ResultSet resultSet = pstmt.executeQuery();
		 while (resultSet.next()) {
			 int count = resultSet.getInt(1);
			 String key = resultSet.getString(2);
			 String dest = "";
			 if(isForward) {
				 dest = resultSet.getString(3);
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
			 }
		 }
	}
	
	private void addNotificationTotals(String status, 
			String user,
			PreparedStatement pstmt, 
			Connection connectionSD,
			HashMap<String,StatusTotal> sList) throws SQLException {
		
		String sql = null;
		
			
		sql = "SELECT count(*) " +
				"from notification_log n, users u " +
				"where u.ident = ? " +
				"and n.o_id = u.o_id " +
				"and n.status = ?;";

		try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		pstmt = connectionSD.prepareStatement(sql);
		pstmt.setString(1, user);			
		pstmt.setString(2, status);
			

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
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		if(sName.equals("_all")) {
			
		} else {
			sId = Integer.parseInt(sName);
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		
		JSONObject jo = new JSONObject();
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSurvey = null;
		

		try {
			
			String sql = null;
			String surveyIdent = null;
			
			/*
			 * Get the latest survey version
			 */
			
			sql = "select ident, display_name, version, s_id from survey where p_id = ?;";
			pstmtSurvey = connectionSD.prepareStatement(sql);
			pstmtSurvey.setInt(1, projectId);
			ResultSet rs = pstmtSurvey.executeQuery();
			JSONObject js = new JSONObject();
			while(rs.next()) {
				JSONObject jOneSurvey = new JSONObject();
				jOneSurvey.put("name", rs.getString(2));
				jOneSurvey.put("version", rs.getInt(3));
				js.put(rs.getString(1), jOneSurvey);
				
				int surveyId = rs.getInt(4);
				if(surveyId == sId) {
					surveyIdent = rs.getString(1);
				}
			}
			jo.put("all_surveys", js);
			
			if(sId == -1) {
				sql = "select u.ident, u.name, fd.device_id, fd.form_ident, fd.form_version " +
						"from users u inner join user_project up on u.id = up.u_id " +
						"inner join project p on up.p_id = p.id and p.id = ? " +
						"left outer join form_downloads fd on fd.u_id = u.id " +
						"and fd.form_ident in (select ident from survey where p_id = ?) " +
						"order by u.name asc;";
			} else {
				sql = "select u.ident, u.name, fd.device_id, fd.form_ident, fd.form_version " +
						"from users u inner join user_project up on u.id = up.u_id " +
						"inner join project p on up.p_id = p.id and p.id = ? " +
						"left outer join form_downloads fd on fd.u_id = u.id " +
						"and fd.form_ident = ? " +
						"order by u.name asc;";
			}
			
			pstmt = connectionSD.prepareStatement(sql);
			if(sId == -1) {
				pstmt.setInt(1, projectId);
				pstmt.setInt(2, projectId);
			} else {
				pstmt.setInt(1, projectId);
				pstmt.setString(2, surveyIdent);
			}
			log.info("Get form downloads: " + pstmt.toString());
			rs = pstmt.executeQuery();
			
	
			JSONArray ja = new JSONArray();
			while(rs.next()) {
				JSONObject jr = new JSONObject();
				
				jr.put("u_ident", rs.getString(1));
				jr.put("u_name", rs.getString(2));
				jr.put("device_id", rs.getString(3));
				jr.put("survey_ident", rs.getString(4));
				jr.put("survey_version", rs.getInt(5));
				ja.put(jr);
			}
			
			jo.put("forms", ja);

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e); 
		} catch (JSONException e) {
			log.log(Level.SEVERE, "JSON Exception", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtSurvey != null) {pstmtSurvey.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
		}
		
		return jo.toString();
	}

}

