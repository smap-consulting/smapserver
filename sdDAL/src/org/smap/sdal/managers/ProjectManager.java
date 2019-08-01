package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.ProjectLinks;
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
	 * Get projects
	 */
	public ArrayList<Project> getProjects(
			Connection sd, 
			String user,
			boolean all,			// If true get all projects in user organisation, otherwise just ones they are in
			boolean links,		// If true include links to other data that uses the project id as a key
			String urlprefix		// Url prefix for links
			) throws ApplicationException, SQLException {
		
		PreparedStatement pstmt = null;
		ArrayList<Project> projects = new ArrayList<Project> ();
		
		try {
			int o_id = GeneralUtilityMethods.getOrganisationId(sd, user);
			ResultSet resultSet = null;
			
			if(o_id > 0) {
				
				String cols = "select p.id, p.name, p.description, p.tasks_only, p.changed_by, p.changed_ts ";
				String sql = null;
				if(all) {
					sql = cols
							+ "from project p "
							+ "where p.o_id = ? "
							+ "order by p.name asc;";		
				} else {
					sql = cols
							+ "from project p, user_project up, users u "
							+ "where p.o_id = ? "
							+ "and p.id = up.p_id "
							+ "and up.u_id = u.id "
							+ "and u.ident = ? "
							+ "order by p.name asc;";		
					
				}
				pstmt = sd.prepareStatement(sql);			
				pstmt.setInt(1, o_id);
				if(!all) {
					pstmt.setString(2, user);
				}
				
				log.info("Get project list: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
				while(resultSet.next()) {
					Project project = new Project();
					project.id = resultSet.getInt("id");
					project.name = resultSet.getString("name");
					project.desc = resultSet.getString("description");
					project.tasks_only = resultSet.getBoolean("tasks_only");
					project.changed_by = resultSet.getString("changed_by");
					project.changed_ts = resultSet.getString("changed_ts");
					
					if(links) {
						project.links = new ProjectLinks();
						project.links.task_groups = urlprefix + "api/v1/tasks/groups/" + project.id;
					}
					projects.add(project);
			
				}
						
			} else {
				throw new ApplicationException("Error: No organisation");
			}
				
		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
		}
		
		return projects;
	}

	/*
	 * Create a new project
	 */
	public void createProject(
			Connection sd, 
			Project p, 
			int o_id, 
			int u_id, 
			String userIdent) throws SQLException {
		
		String sql = "insert into project (name, description, o_id, changed_by, changed_ts) " +
				" values (?, ?, ?, ?, now());";
		int p_id = 0;
		
		PreparedStatement pstmt = null;
		try {
		
			pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			pstmt.setString(1, p.name);
			pstmt.setString(2, p.desc);
			pstmt.setInt(3, o_id);
			pstmt.setString(4, userIdent);
			log.info("Insert project: " + pstmt.toString());
			pstmt.executeUpdate();
			ResultSet rs = pstmt.getGeneratedKeys();
			if(rs.next()) {
				p_id = rs.getInt(1);
			}
			pstmt.close();
		
			if(p_id > 0) {
				// Add the user to the new project by default
				sql = "insert into user_project (u_id, p_id) " +
						" values (?, ?);";
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, u_id);
				pstmt.setInt(2, p_id);
				log.info("Add the user to the project " + pstmt.toString());
				pstmt.executeUpdate();
				pstmt.close();
			}
		} finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
	}
	
}
