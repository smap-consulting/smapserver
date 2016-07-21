package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.model.NameId;
import org.smap.sdal.model.ReportItem;
import org.smap.sdal.model.TableColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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
	
	/*
	 * get a list of reports for a select list
	 */
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
	
	/*
	 * Get a list of reports with full details
	 */
	public ArrayList<ReportItem> getCustomReports(Connection sd, int oId) throws SQLException {
		
		ArrayList<ReportItem> reports = new ArrayList<ReportItem> ();
		
		String sql = "select id, name, type from custom_report where o_id = ? "
				+ "order by name asc";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			
			pstmt.setInt(1, oId);
			ResultSet rs = pstmt.executeQuery();
			
			while(rs.next()) {
				ReportItem item = new ReportItem();
				item.id = rs.getInt(1);
				item.name = rs.getString(2);
				reports.add(item);
			}
			
		} finally {
			try {pstmt.close();} catch(Exception e) {};
		}
		
		return reports;
		
	}
	
	/*
	 * Get a report from the database
	 */
	public ArrayList<TableColumn> get(Connection sd, int crId) throws Exception {
		
		ArrayList<TableColumn> config = null;
		String sql = "select config from custom_report where id = ?";
		PreparedStatement pstmt = null;
		
		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, crId);
			
			log.info(pstmt.toString());
			pstmt.executeQuery();
			
			String configString = null;
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				configString = rs.getString(1);
				Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				Type type = new TypeToken<ArrayList<TableColumn>>(){}.getType();
				config = gson.fromJson(configString, type);
			}
			
			
		} catch (SQLException e) {
			throw(new Exception(e.getMessage()));
		} finally {
			try {pstmt.close();} catch(Exception e) {};
		}
		
		return config;
	}

}


