package model;
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
import java.sql.SQLException;
import java.util.ArrayList;

public class AdminStatsSqlCreator {
	
	int orgId;
	String type;
	String group;
	String x;
	String period;
	int userId;
	
	StringBuffer sql = new StringBuffer();
	ArrayList<String> params = new ArrayList<String> ();
	
	
	public AdminStatsSqlCreator(String type, 
			int orgId, 
			String group, 
			String x, 
			String period, 
			int userId) {
		
		this.type = type;
		this.orgId = orgId;
		this.group = group;
		this.x = x;
		this.period = period;
		this.userId = userId;
	}
	
	/*
	 * The columns in the select should be:
	 *    xvalue	string
	 *    group		string
	 *    value		int
	 */
	public void prepareSQL() throws Exception {
		
		String xCol = null;
		String xSel = null;
		String groupSel = null;
		String valueCol = null;
		String tables = null;
		String join = null;
		String orgCheck = null;
		String userCheck = null;
		
		/*
		 * Set up sql fragments
		 */
		
		if(type.equals("tasks")) {
			
			// Get the x column
			if(x.equals("scheduled")) {
				xCol = "schedule_at";
			} else {
				throw new Exception("Unknown value for x axis: " + x);
			}
			
			// Get the group column
			if(group.equals("status")) {
				groupSel = "assignments.status";
			} else {
				throw new Exception("Unknown value for group: " + group);
			}
			
			tables = "assignments, tasks";
			join = "tasks.id = assignments.task_id";
			
			// Get the check for the user filter
			if(userId > 0) {
				userCheck = "assignee  = ?";
			}
			
		} else {
			throw new Exception("Unknown summary type: " + period);
		}
		
		// Get the value column
		valueCol = "count(*)";
		
		// period selection
		if(period != null) {
			if(period.equals("day")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(month from " + xCol +
						") || '-' || extract(day from " + xCol + 
						")";
			} else 	if(period.equals("month")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(month from " + xCol +
						")";
			} else 	if(period.equals("year")) {
				xSel = "extract(year from " + xCol + 
						")";
			} else if(period.equals("week")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(week from " + xCol +
						")";
			} else {
				throw new Exception("Unknown period value: " + period);
			}
		} else {
			xSel = xCol;
		}
		
		// Get the check for the correct organisation
		if(orgId > 0) {
			orgCheck = "p_id in (select p_id from project where o_id = ?)";
		}
		
		/*
		 * Construct sql
		 */
		sql.append("select ");
		
		// Add x axis value
		sql.append(xSel);
		sql.append(" as xvalue");
		
		// Add group value
		sql.append(",");
		sql.append(groupSel);
		sql.append(" as group");
		
		// Add data value
		sql.append(",");
		sql.append(valueCol);
		sql.append(" as value");
		
		// Add from
		sql.append(" from ");
		sql.append(tables);
		
		// Add where
		boolean needsAnd = false;
		if(join != null || orgCheck != null || userCheck != null) {
			sql.append(" where ");
		}
		if(join != null) {
			sql.append(join);
			needsAnd = true;
		}
		if(orgCheck != null) {
			if(needsAnd) {
				sql.append(" and ");
			}
			sql.append(orgCheck);
			params.add("orgId");
			needsAnd = true;
		}
		if(userCheck != null) {
			if(needsAnd) {
				sql.append(" and ");
			}
			sql.append(userCheck);
			params.add("userId");
			needsAnd = true;
		}
		
		// Add group by
		sql.append(" group by ");
		sql.append(xSel);
		sql.append(",");
		sql.append(groupSel);
		
		// Add order by
		sql.append(" order by ");
		sql.append(xSel);
		sql.append(",");
		sql.append(groupSel);
		
		sql.append(";");
		
	}
	
	/*
	 * Get a prepared statement for this query
	 */
	public PreparedStatement prepareStatement(Connection sd) throws SQLException {
		PreparedStatement pstmt = sd.prepareStatement(sql.toString());
		for(int i = 0; i < params.size(); i++) {
			String param = params.get(i);
			if(param.equals("orgId")) {
				pstmt.setInt(i + 1, orgId);
			} else if(param.equals("userId")) {
				pstmt.setInt(i + 1, userId);
			}
		}
		return pstmt;
	}
}
