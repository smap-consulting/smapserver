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
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/usertrail")
public class UserTrail extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(UserTrail.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UserTrail.class);
		return s;
	}
	

	
	public class Feature {
		public int id;
		public double[] coordinates = new double[2];
		public Timestamp time;
		public long rawTime;
	}
	
	public class Trail {
		String userName = null;
		public ArrayList<Feature> features = null;
		
	}
	
	public class Survey {
		public int id;
		public double[] coordinates = new double[2];
		public Timestamp time;
	}
	
	public class SurveyList {
		String userName = null;
		public ArrayList<Survey> surveys = null;
		
	}
	
	public UserTrail() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
	}

	
	/*
	 * Get the trail of points 
	 */
	@GET
	@Produces("application/json")
	@Path("/trail")
	public Response getTrail(@Context HttpServletRequest request, 
			@QueryParam("projectId") int projectId,
			@QueryParam("userId") int uId,
			@QueryParam("startDate") long start_t,
			@QueryParam("endDate") long end_t) {


		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Class not found Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		}
		
		Timestamp startDate = new Timestamp(start_t);
		Timestamp endDate = new Timestamp(end_t);
			
		log.info("Getting trail between" + startDate.toGMTString() + " and " + endDate.toGMTString());;

		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			
			String sql = "SELECT ut.id as id, ST_X(ST_Transform(ut.the_geom, 3857)) as x, " +
						"ST_Y(ST_Transform(ut.the_geom, 3857)) as y, ut.event_time as event_time, " +	
						"extract(epoch from ut.event_time) * 1000 as raw_time, " + 
						"u.name as user_name " +	
					"FROM user_trail ut, user_project up, users u  " +
					"where up.p_id = ? " + 	
					"and up.u_id = ut.u_id " +
					"and up.u_id = u.id " +
					"and up.restricted = false " +
					"and up.allocated = true " +
					"and ut.event_time >= ? " +
					"and ut.event_time < ? " +
					"and ut.u_id = ? " +
					"order by ut.event_time asc;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, projectId);
			pstmt.setTimestamp(2, startDate);
			pstmt.setTimestamp(3, endDate);
			pstmt.setInt(4, uId);

			log.info("Events List: " + sql + " : " + uId + " : " + projectId + " : " + startDate + " : " + endDate);

			resultSet = pstmt.executeQuery();
			 
			Trail trail = new Trail();
			trail.features = new ArrayList<Feature> ();
			 
			while (resultSet.next()) {
				
				if(trail.userName == null) {
					trail.userName = resultSet.getString("user_name");
				}
				
				Feature f = new Feature();
				f.id = resultSet.getInt("id");
				f.time = resultSet.getTimestamp("event_time");	
				f.rawTime = resultSet.getLong("raw_time");
				f.coordinates[0] = resultSet.getDouble("x");
				f.coordinates[1] = resultSet.getDouble("y");
				trail.features.add(f);
			}
			 
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(trail);
			response = Response.ok(resp).build();
			 

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();

		} finally {
			try {if(resultSet != null) {resultSet.close();}	} catch (SQLException e) {	}	
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", connectionSD);
		}
		
		return response;
	}

	/*
	 * Get the survey locations 
	 */
	@GET
	@Produces("application/json")
	@Path("/surveys")
	public Response getSurveyLocations(@Context HttpServletRequest request, 
			@QueryParam("projectId") int projectId,
			@QueryParam("userId") int uId,
			@QueryParam("startDate") long start_t,
			@QueryParam("endDate") long end_t) {

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Class not found Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		}
		
		log.info("Get Survey Locations: Project id:" + projectId);

		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-EventList");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		Timestamp startDate = new Timestamp(start_t);
		Timestamp endDate = new Timestamp(end_t);
		
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			
			String sql = "SELECT t.id as id, ST_X(ST_Transform(t.the_geom, 3857)) as x, " +
						"ST_Y(ST_Transform(t.the_geom, 3857)) as y, t.completion_time as completion_time, " +	
						"u.name as user_name " +	
					"FROM task_completion t, user_project up, users u  " +
					"where up.p_id = ? " + 	
					"and up.u_id = t.u_id " +
					"and up.u_id = u.id " +
					"and up.restricted = false " +
					"and up.allocated = true " +
					"and t.completion_time >= ? " +
					"and t.completion_time < ? " +
					"and t.u_id = ? " +
					"order by t.completion_time asc;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, projectId);
			pstmt.setTimestamp(2, startDate);
			pstmt.setTimestamp(3, endDate);
			pstmt.setInt(4, uId);

			log.info("Events List: " + sql + " : " + uId + " : " + projectId + " : " + startDate + " : " + endDate);

			resultSet = pstmt.executeQuery();
			 
			SurveyList sl = new SurveyList();
			sl.surveys = new ArrayList<Survey> ();
			 
			while (resultSet.next()) {
				
				if(sl.userName == null) {
					sl.userName = resultSet.getString("user_name");
				}
				
				Survey s = new Survey();
				s.id = resultSet.getInt("id");
				s.time = resultSet.getTimestamp("completion_time");	
				s.coordinates[0] = resultSet.getDouble("x");
				s.coordinates[1] = resultSet.getDouble("y");
				sl.surveys.add(s);
			}
			 
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String resp = gson.toJson(sl);
			response = Response.ok(resp).build();
			 

		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();

		} finally {
			try {if(resultSet != null) {resultSet.close();}	} catch (SQLException e) {	}	
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-EventList", connectionSD);
		}
		
		return response;
	}


}

