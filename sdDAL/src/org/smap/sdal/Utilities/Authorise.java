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

package org.smap.sdal.Utilities;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Authorise {
	
	private static Logger log =
			 Logger.getLogger(Authorise.class.getName());
	
	public static String ENUM = "enum";
	public static String ANALYST = "analyst";
	public static String ADMIN = "admin";
	public static String ORG = "org admin";
	public static String MANAGE = "manage";
	public static String SECURITY = "security";
	
	public static int ADMIN_ID = 1;
	public static int ANALYST_ID = 2;
	public static int ENUM_ID = 3;
	public static int ORG_ID = 4;
	public static int MANAGE_ID = 5;
	public static int SECURITY_ID = 6;
	
	//private String requiredGroup;
	ArrayList<String> permittedGroups; 
	
	/*
	 * Create authorise object with required security policies
	 */
	public Authorise(ArrayList<String> groups, String group) {
		
		if(groups == null) {
			permittedGroups = new ArrayList<String> ();
			permittedGroups.add(group);
		} else {
			permittedGroups = groups;
		}
	}
	
	/*
	 * Check to see if the user has the rights to perform the requested action
	 */
	public boolean isAuthorised(Connection conn, String user) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		String sql = "select count(*) from users u, groups g, user_group ug " +
				" where u.id = ug.u_id " +
				" and g.id = ug.g_id " +
				" and u.ident = ? " +
				" and (" +
				" g.name = ? ";
				
		if(permittedGroups.size() > 1) {
			for(int i = 1; i < permittedGroups.size(); i++)
				sql += " or g.name = ? ";
		}
		sql += ");";
		
		try {
			pstmt = conn.prepareStatement(sql); 	
			pstmt.setString(1, user);
			for(int i = 0; i < permittedGroups.size(); i++) {
				pstmt.setString(i + 2, permittedGroups.get(i));
			}

			log.info("isAuthorised: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.info("Authorisation failed for: " + user + " groups required were one of: " );
			for(int i = 0; i < permittedGroups.size(); i++) {
				log.info("  ==== " + permittedGroups.get(i));
			}
			log.log(Level.SEVERE,"SQL Error during authorisation", e);
			sqlError = true;
		} finally {		
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
		// Check to see if the user was authorised to access this service
 		if(count == 0 || sqlError) {
 			log.info("Authorisation failed for: " + user + " group required was one of: ");
 			for(int i = 0; i < permittedGroups.size(); i++) {
				log.info("  ==== " + permittedGroups.get(i));
			}
 			// Close the connection as throwing an exception will end the service call
			
 			SDDataSource.closeConnection("isAuthorised", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Check to make sure the user is a valid temporary user
	 */
	public boolean isValidTemporaryUser(Connection conn, String user) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		String sql = "select count(*) from users u " +
				" where u.ident = ? " +
				" and u.temporary = true";
		
		try {
			pstmt = conn.prepareStatement(sql); 	
			pstmt.setString(1, user);

			log.info("is temporary user: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"SQL Error during authorisation", e);
			sqlError = true;
		} finally {		
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
		// Check to see if the user was authorised to access this service
 		if(count == 0 || sqlError) {
 			log.info("Authorisation failed for: " + user + " needs to be a temporary user");
 			SDDataSource.closeConnection("isAuthorised", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this particular survey
	 */
	public boolean isValidSurvey(Connection conn, String user, int sId, boolean isDeleted, boolean superUser)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		/*
		 * 1) Make sure the survey has not been soft deleted and exists or alternately 
		 *    that it has been soft deleted and exists
		 * 2) Make sure survey is in a project that the user has access to
		 */

		StringBuffer sql = new StringBuffer("select count(*) from survey s, users u, user_project up, project p "
				+ "where u.id = up.u_id "
				+ "and p.id = up.p_id "
				+ "and s.p_id = up.p_id "
				+ "and s.s_id = ? "
				+ "and u.ident = ? "
				+ "and s.deleted = ? ");
		
		try {		
			
			if(!superUser) {
				// Add RBAC
				sql.append(GeneralUtilityMethods.getSurveyRBAC());
			}
			
			pstmt = conn.prepareStatement(sql.toString());
			pstmt.setInt(1, sId);
			pstmt.setString(2, user);
			pstmt.setBoolean(3, isDeleted);
			
			if(!superUser) {
				pstmt.setString(4, user);
			}
			log.info("IsValidSurvey: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for: " + user + " survey was: " + sId);
 			
 			SDDataSource.closeConnection("isValidSurvey", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this particular query
	 */
	public boolean isValidQuery(Connection conn, String user, int queryId, boolean isDeleted, boolean superUser)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		/*
		 * 1) Make sure the survey  exists 
		 * 2) Make sure user has access to the query
		 */

		StringBuffer sql = new StringBuffer("select count(*) from custom_query q, users u "
				+ "where u.id = q.u_id "
				+ "and q.id = ? "
				+ "and u.ident = ? ");
		
		try {		
			
			pstmt = conn.prepareStatement(sql.toString());
			pstmt.setInt(1, queryId);
			pstmt.setString(2, user);
			
			log.info("IsValidQuery: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for: " + user + " query was: " + queryId);
 			
 			SDDataSource.closeConnection("isValidQuery", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this particular role
	 */
	public boolean isValidRole(Connection conn, String user, int rId)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		/*
		 * 1) Make sure the role is in the users organisation
		 */

		String sql = "select count(*) from role r, users u "
				+ "where u.o_id = r.o_id "
				+ "and u.ident = ? "
				+ "and r.id = ?";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, rId);
			
			log.info("IsValidRole: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for: " + user + " role was: " + rId);
 			
 			SDDataSource.closeConnection("isValidRole", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this managedForm
	 */
	public boolean isValidManagedForm(Connection conn, String user, int crId)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		/*
		 * 1) Make sure the survey has not been soft deleted and exists or alternately 
		 *    that it has been soft deleted and exists
		 * 2) Make sure survey is in a project that the user has access to
		 */

		String sql = "select count(*) from custom_report cr, users u "
				+ "where cr.o_id = u.o_id "
				+ "and cr.id = ? "
				+ "and u.ident = ? ";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, crId);
			pstmt.setString(2, user);
			
			log.info("IsValidCustomReport: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for: " + user + " custom report was: " + crId);
 			
 			SDDataSource.closeConnection("isValidSurvey", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this particular task group
	 */
	public boolean isValidTaskGroup(Connection conn, String user, int tgId, boolean isDeleted)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from task_group tg, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and tg.p_id = up.p_id" +
				" and tg.tg_id = ? " +
				" and u.ident = ?;";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, tgId);
			pstmt.setString(2, user);
			
			log.info("IsValidTaskGroup: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for: " + user + " task group was: " + tgId);
 			
 			SDDataSource.closeConnection("isValidTaskGroup", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the question is in the survey
	 */
	public boolean isValidQuestion(Connection conn, String user, int sId, int qId)
			throws ServerException, AuthorisationException, NotFoundException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;
		
		/*
		 * 1) Make sure the survey has not been soft deleted and exists or alternately 
		 *    that it has been soft deleted and exists
		 * 2) Make sure survey is in a project that the user has access to
		 */

		String sql = "select count(*) from question q, form f " +
				" where q.f_id = f.f_id" +
				" and f.s_id = ?" +
				" and q.q_id = ?;"; 
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, qId);
			
			log.info("IsValidQuestion: " + pstmt.toString());
			
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Question validation failed for question: " + qId + " survey was: " + sId);
 			
 			SDDataSource.closeConnection("isValidQuestion", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new NotFoundException();	// Not found rather than not authorised as we could not find a resource that the user had access to
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the survey is not blocked
	 */
	public boolean isBlocked(Connection conn, int sId, boolean isBlocked)
			throws ServerException, AuthorisationException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from survey s " +
				" where s.s_id = ? " +
				" and s.blocked = ?;"; 
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setBoolean(2, isBlocked);
			
			log.info("isBlocked: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation - isBlocked", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for block check: " + isBlocked + " survey was: " + sId);
 			
 			SDDataSource.closeConnection("isBlocked", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new BlockedException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the survey can load tasks from a file
	 */
	public boolean canLoadTasks(Connection conn, int sId)
			throws ServerException, AuthorisationException {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from survey s " +
				" where s.s_id = ? " +
				" and s.task_file = 'true';"; 
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			
			log.info("Verify can load tasks: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation - cannot load tasks", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Survey validation failed for can load tasks: survey was: " + sId);
 			
 			SDDataSource.closeConnection("canLoadTasks", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}

	/*
	 * Verify that the user is entitled to access this project
	 */
	public boolean isValidProject(Connection conn, String user, int pId) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and p.id = ? " +
				" and u.ident = ?;";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, pId);
			pstmt.setString(2, user);
			log.info("IsValidProject: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Project validation failed for: " + user + " project was: " + pId);
 			
 			SDDataSource.closeConnection("isValidProject", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException("Invalid Project: " + pId);
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this project
	 */
	public boolean projectInUsersOrganisation(Connection conn, String user, int pId) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from project p " +
				" where p.id = ? " +
				" and p.o_id = ?;";
		
		
		
		try {
			int oId = GeneralUtilityMethods.getOrganisationId(conn, user, 0);
			
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, pId);
			pstmt.setInt(2, oId);
			log.info("IsProjectInUsersOrganisation: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
			}
		}
		
 		if(count == 0) {
 			log.info("Project in organisation validation failed for: " + user + " project was: " + pId);
 			
 			SDDataSource.closeConnection("isValidProject", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is a member of the supplied organisation
	 */
	public boolean isValidOrganisation(Connection conn, String user, int oId) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from users u " +
				" where u.o_id = ?" +
				" and u.ident = ?;";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setString(2, user);
			log.info("IsValidOrganisation: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Security: Project validation failed for: " + user + " organisation was: " + oId);
 			
 			SDDataSource.closeConnection("isValidOrganisation", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this task
	 */
	public boolean isValidTask(Connection conn, String user, int tId) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from tasks t, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and p.id = t.p_id " +
				" and t.id = ? " +
				" and u.ident = ?;";
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, tId);
			pstmt.setString(2, user);
			
			log.info("Is valid task: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
			
			if(count == 0) {
				log.info("Validation of task failed: " + pstmt.toString());
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Task validation failed for: " + user + " survey was: " + tId);
 			
 			SDDataSource.closeConnection("isValidTask", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
	
	/*
	 * Verify that the user is entitled to access this assignment
	 */
	public boolean isValidAssignment(Connection conn, String user, int aId) {
		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		int count = 0;
		boolean sqlError = false;

		String sql = "select count(*) from assignments a, tasks t, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and p.id = t.p_id " +
				" and t.id = a.task_id " +
				" and a.id = ? " +
				" and u.ident = ?;";
		
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, aId);
			pstmt.setString(2, user);
			
			log.info("Is valid assignment: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			resultSet.next();
			
			count = resultSet.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
			sqlError = true;
		} finally {
			// Close the result set and prepared statement
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
 		if(count == 0) {
 			log.info("Assignment validation failed for: " + user + " survey was: " + aId);
 			
 			SDDataSource.closeConnection("isValidAssignment", conn);
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
}
