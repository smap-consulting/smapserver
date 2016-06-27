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
 * Manage the log table
 */
public class LogManager {
	
	private static Logger log =
			 Logger.getLogger(LogManager.class.getName());
	
	/*
	 * Get the current task groups
	 */
	public void writeLog(Connection sd, 
			int sId,
			String uIdent,
			String event,
			String note)  {
		
		String sql = "insert into log ("
				+ "log_time,"
				+ "s_id,"
				+ "o_id,"
				+ "user_ident,"
				+ "event,"
				+ "note) values (now(), ?, ?, ?, ?, ?);";

		PreparedStatement pstmt = null;
		
		try {
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, uIdent);
			pstmt = sd.prepareStatement(sql);	
			pstmt.setInt(1, sId);
			pstmt.setInt(2, oId);
			pstmt.setString(3, uIdent);
			pstmt.setString(4,  event);
			pstmt.setString(5,  note);
			
			pstmt.executeUpdate();


		} catch(Exception e) {
			log.log(Level.SEVERE, "SQL Error", e);
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}

	}
	
}


