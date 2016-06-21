package managers;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import koboToolboxApi.Data_CSV;
import model.DataEndPoint;
import model.StatsResultsC3;
import model.AdminStatsSqlCreator;
import model.Task;

import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TasksManager {
	
	private static Logger log =
			 Logger.getLogger(Data_CSV.class.getName());

	private int orgId;							// Organisation id
	
	public TasksManager(int orgId) {
		this.orgId = orgId;
	}
	
	/*
	 * 
	 * groups:
	 * 	    status
	 * x
	 *		scheduled
	 *		completed
	 * 		organisation
	 * 
	 * Period (for x = scheduled or other time based values)
	 * 	 	day
	 * 		week
	 * 		month
	 * 		year
	 * 
	 * 	
	 */
	public Response getTasks(Connection sd, 
			int orgId, 
			int userId,
			int limit) throws Exception {
		
		Response response = null;
		
		
		String sql = "select u.name as user, t.title as title, t.schedule_at as scheduled, a.status as status "
				+ "from users u, tasks t, assignments a, project p "
				+ "where u.id = a.assignee "
				+ "and t.id = a.task_id "
				+ "and t.p_id = p.id "
				+ "and p.o_id = ? "
				+ "order by a.id desc limit ?;";
		
		PreparedStatement pstmt = null;
		
		try {

			ArrayList<Task> tasks = new ArrayList<Task>();
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, orgId);
			pstmt.setInt(2, limit);
			log.info("get individual tasks: " + pstmt.toString());	
			ResultSet rs = pstmt.executeQuery();
			
			while(rs.next()) {
				Task task = new Task();
				task.user = rs.getString("user");
				task.title = rs.getString("title");
				task.scheduled = rs.getTimestamp("scheduled");
				task.setStatus(rs.getString("status"));
				
				tasks.add(task);
			}
		
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			response = Response.ok(gson.toJson(tasks)).build();

			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		return response;
	}
	
	/*
	 * 
	 * groups:
	 * 	    status
	 * x
	 *		scheduled
	 *		completed
	 * 		organisation
	 * 
	 * Period (for x = scheduled or other time based values)
	 * 	 	day
	 * 		week
	 * 		month
	 * 		year
	 * 
	 * 	
	 */
	public Response getTaskStats(Connection sd, 
			int orgId, 
			String group, 
			String x, 
			String period,
			int userId) throws Exception {
		
		Response response = null;
		
		AdminStatsSqlCreator sqlCreator= new AdminStatsSqlCreator("tasks", orgId, group, x, period, userId);
		PreparedStatement pstmt = null;
		
		try {

			StatsResultsC3 res = new StatsResultsC3();
			sqlCreator.prepareSQL();
			pstmt = sqlCreator.prepareStatement(sd);
			log.info("get task statistics: " + pstmt.toString());
			
			
			// 1. Get the groups
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {			
				String groupVal = rs.getString("group");	
				if(groupVal == null) {
					groupVal = "new";
				}
				res.setGroups(groupVal);
			}
			
			// 2. Create the output data array
			rs = pstmt.executeQuery();
			while(rs.next()) {
				String xValue = rs.getString("xvalue");
				String groupVal = rs.getString("group");
				if(groupVal == null) {
					groupVal = "new";
				}
				int value = rs.getInt("value");
				
				res.add(xValue, groupVal, value);
			}
			
			/*
			 * 3. Translate group names
			 */
			if(res.groups != null) {
				for (int i = 0; i < res.groups.size(); i++) { 
					String g = res.groups.get(i);
					if(g.equals("accepted")) {
						res.groups.set(i, "pending");
					} else if(g.equals("submitted")) {
						res.groups.set(i, "completed");
					}
				}
			}
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			response = Response.ok(gson.toJson(res)).build();

			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		return response;
	}
	
}
