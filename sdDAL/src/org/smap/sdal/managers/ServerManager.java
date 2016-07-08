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

import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.AssignFromSurvey;
import org.smap.sdal.model.Assignment;
import org.smap.sdal.model.Location;
import org.smap.sdal.model.ServerData;
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
 * Manage the server table
 */
public class ServerManager {
	
	private static Logger log =
			 Logger.getLogger(ServerManager.class.getName());
	
	public ServerData getServer(Connection sd) {
		
		String sql = "select smtp_host,"
				+ "email_domain,"
				+ "email_user,"
				+ "email_password,"
				+ "email_port,"
				+ "version,"
				+ "mapbox_default,"
				+ "google_key "
				+ "from server;";
		PreparedStatement pstmt = null;
		ServerData data = new ServerData();

		try {
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				data.smtp_host = rs.getString("smtp_host");
				data.email_domain = rs.getString("email_domain");
				data.email_user = rs.getString("email_user");
				data.email_password = rs.getString("email_password");
				data.email_port = rs.getInt("email_port");
				data.version = rs.getString("version");
				data.mapbox_default = rs.getString("mapbox_default");
				data.google_key = rs.getString("google_key");
			}
	
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
		}
		
		return data;
	}
	
}


