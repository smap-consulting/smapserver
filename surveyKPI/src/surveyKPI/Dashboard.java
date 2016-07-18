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

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import model.Settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/dashboard")
public class Dashboard extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Dashboard.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Dashboard.class);
		return s;
	}

	
	/*
	 * Get the dashboard settings
	 * The survey id is obtained from the survey table based on the survey name stored in the
	 *  dashboard_settings table
	 */
	@GET
	@Path("/{projectId}")
	@Produces("application/json")
	public Response getSettings(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId 
			) {
		
		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			// log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		 		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Dashboard");
		// No check for valid user as only panels owned by a user are returned
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		String user = request.getRemoteUser();
		ArrayList<Settings> sArray = new ArrayList<Settings> ();

		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		try  {

			String sql = "SELECT " +
					"d.ds_id as id," +
					"d.ds_seq as seq," +
					"d.ds_state as state," +
					"d.ds_title as title," +
					"d.ds_s_id as sId," +
					"d.ds_s_name as sName," +
					"d.ds_type as type," +
					"d.ds_layer_id as layerId," +
					"d.ds_region as region," +
					"d.ds_lang as lang," +
					"d.ds_q_id as qId," +
					"d.ds_date_question_id as dateQuestionId," +
					"d.ds_question as question," +
					"d.ds_fn as fn," +
					"d.ds_table as table, " + 
					"d.ds_key_words as key_words, " +
					"d.ds_q1_function as q1_function, " +
					"d.ds_group_question_id as groupQuestionId, " +
					"d.ds_group_question_text as groupQuestionText, " +
					"d.ds_group_type as groupType, " +
					"d.ds_time_group as timeGroup, " +
					"d.ds_from_date as fromDate, " +
					"d.ds_to_date as toDate, " +
					"d.ds_q_is_calc as qId_is_calc, " +
					"d.ds_filter as filter " +
					" from dashboard_settings d, users u, user_project up, survey s " +
					" where u.id = up.u_id " +
					" and up.restricted = false " +
					" and up.allocated = true " +
					" and up.p_id = ? " +
					" and s.p_id = up.p_id " +
					" and s.s_id = d.ds_s_id " +
					" and u.ident = d.ds_user_ident " +	// Restrict to owning user
					" and u.ident = ? " +
					" order by ds_seq asc;";
			
			log.info(sql + " : " + projectId + " : " + request.getRemoteUser());
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setInt(1, projectId);
			pstmt.setString(2, request.getRemoteUser());

			int idx = 0;
			resultSet = pstmt.executeQuery();
			while (resultSet.next()) {
				
				// Create the new Dashboard object
				Settings s = new Settings();

				// Populate the new Dashboard settings
				s.id = resultSet.getInt("id");
				s.seq = resultSet.getInt("seq");
				s.state = resultSet.getString("state");
				s.title = resultSet.getString("title");
				s.pId = idx;	// panel id
				s.sId = resultSet.getInt("sId");
				s.sName = resultSet.getString("sName");
				s.type = resultSet.getString("type");
				s.layerId = resultSet.getInt("layerId");
				s.region = resultSet.getString("region");
				s.lang = resultSet.getString("lang");
				s.qId = resultSet.getInt("qId");
				s.dateQuestionId = resultSet.getInt("dateQuestionId");
				s.question = resultSet.getString("question");
				s.fn = resultSet.getString("fn");
				s.table = resultSet.getString("table");
				s.key_words = resultSet.getString("key_words");
				s.q1_function = resultSet.getString("q1_function");
				s.groupQuestionId = resultSet.getInt("groupQuestionId");
				s.groupQuestionText = resultSet.getString("groupQuestionText");
				s.groupType = resultSet.getString("groupType");
				s.timeGroup = resultSet.getString("timeGroup");
				s.fromDate = resultSet.getDate("fromDate");
				s.toDate = resultSet.getDate("toDate");
				s.qId_is_calc = resultSet.getBoolean("qId_is_calc");
				s.filter = resultSet.getString("filter");
				
				sArray.add(s);
				
				idx++;		
			}
			resultSet.close();

			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(sArray);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {log.log(Level.SEVERE, "Failed to close connection", e);}
			
			SDDataSource.closeConnection("surveyKPI-Dashboard", connectionSD);
			
		}

		return response;
	}
	
	/*
	 * Update the settings
	 */
	@POST
	@Consumes("application/json")
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
		
		Type type = new TypeToken<ArrayList<Settings>>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		ArrayList<Settings> sArray = gson.fromJson(settings, type);
		
		PreparedStatement pstmtDel = null;
		PreparedStatement pstmtDelView = null;
		PreparedStatement pstmtAddView = null;
		PreparedStatement pstmtReplaceView = null;
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Dashboard");
		// End Authorisation

		try {
			connectionSD.setAutoCommit(false);
			
			String sqlDelView = "delete from dashboard_settings " +
					"where (ds_id = ? or ds_layer_id = ?) " +
					"and ds_user_ident = ?;";	
			pstmtDelView = connectionSD.prepareStatement(sqlDelView);	
			
			String sqlAddView = "insert into dashboard_settings(" +
					"ds_state, ds_seq, ds_title, ds_s_id, ds_s_name, ds_type, ds_layer_id, ds_region," +
					" ds_lang, ds_q_id, ds_date_question_id, ds_question, ds_fn, ds_table, ds_key_words, ds_q1_function, " +
					" ds_group_question_id, ds_group_question_text, ds_group_type, ds_user_ident, ds_time_group," +
					" ds_from_date, ds_to_date, ds_q_is_calc, ds_filter) values (" +
					"?, ?, ?, ?, ?, ?, ?, ?, ? ,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";		
			pstmtAddView = connectionSD.prepareStatement(sqlAddView);	 			
			
			String sqlReplaceView = "update dashboard_settings set " +
					" ds_state = ?," +
					" ds_seq = ?," +
					" ds_title = ?," + 
					" ds_s_id = ?," +
					" ds_s_name = ?," +
					" ds_type = ?," +
					" ds_layer_id = ?," +
					" ds_region = ?," +
					" ds_lang = ?," +
					" ds_q_id = ?," +
					" ds_date_question_id = ?," +
					" ds_question = ?," +
					" ds_fn = ?," +
					" ds_table = ?," +
					" ds_key_words = ?," +
					" ds_q1_function = ?," +
					" ds_group_question_id = ?," +
					" ds_group_question_text = ?," +
					" ds_group_type = ?," +  
					" ds_time_group = ?," +
					" ds_from_date = ?," +
					" ds_to_date = ?," +
					" ds_q_is_calc = ?," +
					" ds_filter = ?" +
					" where ds_id = ? " +
					" and ds_user_ident = ?;";						
			pstmtReplaceView = connectionSD.prepareStatement(sqlReplaceView);
			
			for(Settings s : sArray) {
				
				if(s.state != null) {
					
					//========================== Delete the old view
					
					if(s.id != -1 && s.state.equals("deleted")) {

						pstmtDelView.setInt(1, s.id);
						pstmtDelView.setInt(2, s.id);
						pstmtDelView.setString(3, user);
						log.info("Delete view: " + pstmtDelView.toString());
						pstmtDelView.executeUpdate();
					
					} else if(s.id == -1) {
						
						//==========================  Insert the new view 
						pstmtAddView.setString(1, s.state);
						pstmtAddView.setInt(2, s.seq);
						pstmtAddView.setString(3, s.title);
						pstmtAddView.setInt(4, s.sId);
						pstmtAddView.setString(5, s.sName);
						pstmtAddView.setString(6, s.type);
						pstmtAddView.setInt(7, s.layerId);
						pstmtAddView.setString(8, s.region);
						pstmtAddView.setString(9, s.lang);
						pstmtAddView.setInt(10, s.qId);
						pstmtAddView.setInt(11, s.dateQuestionId);
						pstmtAddView.setString(12, s.question);
						pstmtAddView.setString(13, s.fn);
						pstmtAddView.setString(14, s.table);
						pstmtAddView.setString(15, s.key_words);
						pstmtAddView.setString(16, s.q1_function);
						pstmtAddView.setInt(17, s.groupQuestionId);
						pstmtAddView.setString(18, s.groupQuestionText);
						pstmtAddView.setString(19, s.groupType);
						pstmtAddView.setString(20, user);
						pstmtAddView.setString(21, s.timeGroup);
						pstmtAddView.setDate(22, s.fromDate);
						pstmtAddView.setDate(23, s.toDate);
						pstmtAddView.setBoolean(24, s.qId_is_calc);
						pstmtAddView.setString(25, s.filter);
						log.info("Add view: " + pstmtAddView.toString());
						pstmtAddView.executeUpdate();		

					} else {
						
						//==========================  Update the existing view
						pstmtReplaceView.setString(1, s.state);
						pstmtReplaceView.setInt(2, s.seq);
						pstmtReplaceView.setString(3, s.title);
						pstmtReplaceView.setInt(4, s.sId);
						pstmtReplaceView.setString(5, s.sName);
						pstmtReplaceView.setString(6, s.type);
						pstmtReplaceView.setInt(7, s.layerId);
						pstmtReplaceView.setString(8, s.region);
						pstmtReplaceView.setString(9, s.lang);
						pstmtReplaceView.setInt(10, s.qId);
						pstmtReplaceView.setInt(11, s.dateQuestionId);
						pstmtReplaceView.setString(12, s.question);
						pstmtReplaceView.setString(13, s.fn);
						pstmtReplaceView.setString(14, s.table);
						pstmtReplaceView.setString(15, s.key_words);
						pstmtReplaceView.setString(16, s.q1_function);
						pstmtReplaceView.setInt(17, s.groupQuestionId);
						pstmtReplaceView.setString(18, s.groupQuestionText);
						pstmtReplaceView.setString(19, s.groupType);
						pstmtReplaceView.setString(20, s.timeGroup);
						pstmtReplaceView.setDate(21, s.fromDate);
						pstmtReplaceView.setDate(22, s.toDate);
						pstmtReplaceView.setBoolean(23, s.qId_is_calc);
						pstmtReplaceView.setString(24, s.filter);
						pstmtReplaceView.setInt(25, s.id);
						pstmtReplaceView.setString(26, user);
						
						log.info("Update view: " + pstmtReplaceView.toString());
						pstmtReplaceView.executeUpdate();
					}
					
				}
				
			}
			connectionSD.commit();
			response = Response.ok().build();
				
		} catch (Exception e) {
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmtDelView != null) {pstmtDelView.close();}} catch (SQLException e) {}
			try {if (pstmtAddView != null) {pstmtAddView.close();}} catch (SQLException e) {}
			try {if (pstmtReplaceView != null) {pstmtReplaceView.close();}} catch (SQLException e) {}
			try {if (pstmtDel != null) {pstmtDel.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Dashboard", connectionSD);
		}
		
		return response;
	}

	/*
	 * Update the state of the panel
	 */
	@POST
	@Path("/state")
	@Consumes("application/json")
	public Response updateState(@Context HttpServletRequest request, 
			@FormParam("state") String stateString) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Dashboard");
		// End Authorisation
		
		PreparedStatement pstmt = null;

		
		try {
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			Settings s = gson.fromJson(stateString, Settings.class);
			
			String sql;
			String user = request.getRemoteUser();

			if(s.state != null && !s.state.equals("deleted")) {
				
				sql = "update dashboard_settings set ds_state=? " +
						"where ds_id = ? " +
						"and ds_user_ident = ?;";
				log.info(sql + " : " + s.state + " : " + s.id + " : " + user);
				
				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setString(1, s.state);
				pstmt.setInt(2, s.id);
				pstmt.setString(3, user);
				pstmt.executeUpdate();

			} else if (s.state != null && s.state.equals("deleted")) {
				sql = "delete from dashboard_settings " +
						"where (ds_id = ? or ds_layer_id = ?) " +
						"and ds_user_ident = ?;";
				//log.info(sql + " : " + s.id + " : " + user);

				pstmt = connectionSD.prepareStatement(sql);
				pstmt.setInt(1, s.id);
				pstmt.setInt(2, s.id);
				pstmt.setString(3, user);
				pstmt.executeUpdate();
				
			}
			
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Dashboard", connectionSD);
		}
		
		return response;
	}

}

