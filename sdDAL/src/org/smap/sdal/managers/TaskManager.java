package org.smap.sdal.managers;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Location;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class TaskManager {
	
	private static Logger log =
			 Logger.getLogger(TaskManager.class.getName());
	
	/*
	 * Save a list of locations replacing the existing ones
	 */
	public ArrayList<Location>  getLocations(Connection sd, 
			int oId) throws SQLException {
		
		String sql = "select id, locn_group, locn_type, uid, name from locations where o_id = ? order by id asc;";
		PreparedStatement pstmt = null;
		ArrayList<Location> locations = new ArrayList<Location> ();

		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, oId);
			
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				Location locn = new Location();
				
				locn.id = rs.getInt(1);
				locn.group = rs.getString(2);
				locn.type = rs.getString(3);
				locn.uid = rs.getString(4);
				locn.name = rs.getString(5);
				
				locations.add(locn);
			}
			

		} catch(Exception e) {
			throw(e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	
		return locations;
		
	}
	
	/*
	 * Save a list of locations replacing the existing ones
	 */
	public void saveLocations(Connection sd, 
			ArrayList<Location> tags,
			int oId) throws SQLException {
		
	
		String sqlTruncate = "truncate table locations;";
		PreparedStatement pstmtTruncate = null;
		
		String sql = "insert into locations (o_id, locn_group, locn_type, uid, name) values (?, ?, ?, ?, ?);";
		PreparedStatement pstmt = null;

		try {
			
			sd.setAutoCommit(false);
			
			// Remove existing data
			pstmtTruncate = sd.prepareStatement(sqlTruncate);
			pstmtTruncate.executeUpdate();
			
			// Add new data
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, oId);
			for(int i = 0; i < tags.size(); i++) {
				
				Location t = tags.get(i);
	
				pstmt.setString(2, t.group);
				pstmt.setString(3, t.type);
				pstmt.setString(4, t.uid);
				pstmt.setString(5, t.name);
			
				pstmt.executeUpdate();
			}
			sd.commit();
		} catch(Exception e) {
			sd.rollback();
			throw(e);
		} finally {
			sd.setAutoCommit(true);
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtTruncate != null) {pstmtTruncate.close();} } catch (SQLException e) {	}
		}
	
		
	}
	

}


