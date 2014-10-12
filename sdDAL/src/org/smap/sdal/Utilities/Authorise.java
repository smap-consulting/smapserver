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
		//requiredGroup = group;

		// permittedGroups = new ArrayList<String> ();
		// permittedGroups.add(ANALYST);
		//permittedGroups.add(ADMIN);
	}
	
	/*
	 * Check to see if the user has the role required to upload any survey
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
		
		log.info(sql + " user: " + user);
		
		try {
			pstmt = conn.prepareStatement(sql);
			//pstmt.setString(1, requiredGroup);
			pstmt.setString(1, user);
			for(int i = 0; i < permittedGroups.size(); i++) {
				System.out.println("   -- group: " + permittedGroups.get(i));
				pstmt.setString(i + 2, permittedGroups.get(i));
			}

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
			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
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
	public boolean isValidSurvey(Connection conn, String user, int sId, boolean isDeleted)
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

		String sql = "select count(*) from survey s, users u, user_project up, project p " +
				" where u.id = up.u_id" +
				" and p.id = up.p_id" +
				" and s.p_id = up.p_id" +
				" and s.s_id = ? " +
				" and u.ident = ? " +
				" and s.deleted = ?;"; 
		log.info("isValidSurvey" + sql + " : " + sId + " : " + user + " : " + isDeleted);
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, user);
			pstmt.setBoolean(3, isDeleted);
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
 			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
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
		log.info("isBlocked" + sql + " : " + sId + " : " + isBlocked);
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setBoolean(2, isBlocked);
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
 			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
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
		//log.info(sql + " : " + pId + " : " + user);
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, pId);
			pstmt.setString(2, user);
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
 			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
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
		// log.info(sql + " : " + tId + " : " + user);
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, tId);
			pstmt.setString(2, user);
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
 			log.info("Task validation failed for: " + user + " survey was: " + tId);
 			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
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
		//log.info(sql + " : " + aId + " : " + user);
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, aId);
			pstmt.setString(2, user);
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
 			
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e3) {
				log.log(Level.SEVERE,"Failed to close connection", e3);
			}
			
			if(sqlError) {
				throw new ServerException();
			} else {
				throw new AuthorisationException();
			}
		} 
 		
		return true;
	}
}
