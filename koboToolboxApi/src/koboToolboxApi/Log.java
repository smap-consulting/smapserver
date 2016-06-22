package koboToolboxApi;
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
import javax.servlet.http.HttpServletResponse;

import managers.DataManager;
import model.DataEndPoint;
import model.LogItem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.Survey;

import utils.Utils;

/*
 * Provides access to collected data
 */
@Path("/v1/log")
public class Log extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Log.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Log.class);
		return s;
	}
	
	public Log() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * KoboToolBox API version 1 /log
	 * Get log entries
	 */
	@GET
	@Produces("application/json")
	@Path("/{sId}")
	public Response getDataRecords(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("start") int start,
			@QueryParam("limit") int limit,
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("sort") String sort,			// Column Name to sort on
			@QueryParam("dirn") String dirn				// Sort direction, asc || desc
			) { 
		
		Response response = null;
		String user = request.getRemoteUser();
		ArrayList<LogItem> logItems = new ArrayList<LogItem> ();
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get log records");
		a.isAuthorised(sd, request.getRemoteUser());
		if(sId > 0) {
			a.isValidSurvey(sd, user, sId, false);
		}
		// End Authorisation
		
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		if(dirn == null) {
			dirn = "asc";
		} else {
			dirn = dirn.replace("'", "''");
		}
		if(sort == null) {
			sort = "id";
		}
		
		try {

			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
				
			String sql = "select l.id, l.log_time, l.s_id, s.display_name, l.user_ident, l.event, l.note "
					+ "from log l "
					+ "left outer join survey s "
					+ "on s.s_id = l.s_id "
					+ "where l.id > ? ";
			
			String sqlSelect = "";
			if(sId == 0) {
				sqlSelect = "and l.s_id in (select s_id from survey s, user_project up where s.p_id = up.p_id and u_id = ?) ";
			} else {
				sqlSelect = "and l.s_id = ? ";
			}
				
			String sqlOrder = "order by l." + sort + " " + dirn;
			
			pstmt = sd.prepareStatement(sql + sqlSelect + sqlOrder);
			int paramCount = 1;
			pstmt.setInt(paramCount++, start);	
			if(sId == 0) {
				int uId = GeneralUtilityMethods.getUserId(sd, user);
				pstmt.setInt(paramCount++, uId);
			} else {
				pstmt.setInt(paramCount++, sId);
			}
			log.info("Get data: " + pstmt.toString());
			rs = pstmt.executeQuery();
				
			int index = 0;	
			while (rs.next()) {
					
				if(limit > 0 && index >= limit) {
					break;
				}
				index++;
					
				LogItem li = new LogItem();

				li.id = rs.getInt("id");
				li.log_time = rs.getTimestamp("log_time");
				li.sId = rs.getInt("s_id");
				li.sName = rs.getString("display_name");
				li.userIdent = rs.getString("user_ident");
				li.event = rs.getString("event");
				li.note = rs.getString("note");
						
				logItems.add(li);
			}
						
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			response = Response.ok(gson.toJson(logItems)).build();
			
	
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
					
			SDDataSource.closeConnection("koboToolboxApi - get log records", sd);
		}
		
		return response;
		
	}
	
	/*
	 * Convert the human name for the sort column into sql
	 */
	private String getSortColumn(ArrayList<Column> columns, String sort) {
		String col = "prikey";	// default to prikey
		System.out.println("Getting sort column: " + sort + "x");
		sort = sort.trim();
		for(int i = 0; i < columns.size(); i++) {
			System.out.println("        x" + columns.get(i).humanName + "x");
			if(columns.get(i).humanName.equals(sort)) {
				Column c = columns.get(i);

				if(c.isCalculate()) {
					col = c.calculation;
				} else {
					col = c.name;
				}
				break;
			}
		}
		return col;
	}
	
	
}

