package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.AssignFromSurvey;
import org.smap.sdal.model.Assignment;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.NameId;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.Task;
import org.smap.sdal.model.TaskAssignment;
import org.smap.sdal.model.TaskBulkAction;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.TaskGroup;
import org.smap.sdal.model.TaskListGeoJson;
import org.smap.sdal.model.TaskProperties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/*****************************************************************************

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

 ******************************************************************************/

/*
 * Manage the table that stores details on tasks
 */
public class CustomReportsManager {
	
	private static Logger log =
			 Logger.getLogger(CustomReportsManager.class.getName());
	
	/*
	 * Save a report to the database
	 */
	public void save(Connection sd, 
			String reportName, 
			ArrayList<TableColumn> config, 
			int oId,
			String type) throws Exception {
		
		String sql = "insert into custom_report (o_id, name, config, type) values (?, ?, ?, ?);";
		PreparedStatement pstmt = null;
		
		try {
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			String configString = gson.toJson(config);

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setString(2, reportName);
			pstmt.setString(3, configString);
			pstmt.setString(4,  type);
			
			log.info(pstmt.toString());
			pstmt.executeUpdate();
			
		} catch (SQLException e) {
			throw(new Exception(e.getMessage()));
		} finally {
			try {pstmt.close();} catch(Exception e) {};
		}
	}
	
	public ArrayList<NameId> getList(Connection sd, int oId, String type) throws SQLException {
		
		ArrayList<NameId> reports = new ArrayList<NameId> ();
		
		String sql1 = "select id, name from custom_report where o_id = ? ";
		String sql2 = "and type = ? ";
		String sql3 = "order by name asc";
		
		PreparedStatement pstmt = null;
		
		try {
			
			if(type != null) {
				pstmt = sd.prepareStatement(sql1 + sql2 + sql3);
			} else {
				pstmt = sd.prepareStatement(sql1 + sql3);
			}
			
			pstmt.setInt(1, oId);
			if(type != null) {
				pstmt.setString(2, type);
			}
			
			log.info(pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			while(rs.next()) {
				NameId item = new NameId();
				item.id = rs.getInt(1);
				item.name = rs.getString(2);
				reports.add(item);
			}
			
		} finally {
			try {pstmt.close();} catch(Exception e) {};
		}
		
		return reports;
		
	}

}


