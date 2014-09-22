package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

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

public class ProjectManager {
	
	private static Logger log =
			 Logger.getLogger(ProjectManager.class.getName());


	/*
	 * Get the details of a project
	 */
	public Project getById(
			String user,
			int pId
			) throws SQLException {
		
		Project p = null;	// Survey to return
		Connection connectionSD = SDDataSource.getConnection("ProjectManager");
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		String sql = "p.name " +
				" users u, user_project up, project p" +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and u.ident = ? " +
				" and p.id = ?; ";
	
		try {
			pstmt = connectionSD.prepareStatement(sql);	 			
			pstmt.setString(1, user);
			pstmt.setInt(2, pId);

			log.info(sql + " : " + user + " : " + pId);
			resultSet = pstmt.executeQuery();

			if (resultSet.next()) {								
	
				p = new Project();
				p.id = pId;
				p.name = resultSet.getString(1);	
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			try { if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			if (connectionSD != null) {
        		connectionSD.close();
        		connectionSD = null;
        	}
		}
		
		return p;
		
	}


	
}
