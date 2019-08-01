package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.ApplicationException;

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
 * Assume emails are case insensitive
 */
public class PeopleManager {
	
	private static Logger log =
			 Logger.getLogger(PeopleManager.class.getName());
	
	ResourceBundle localisation = null;
	
	public PeopleManager(ResourceBundle l) {
		localisation = l;
	}
	
	/*
	 * Get an email key for this user that can be used to unsubscribe
	 * If the person is already unsubscribed then return null
	 * organisation id is recorded in the people table but unsubscription applies across the whole server
	 */
	public String getEmailKey(Connection sd, 
			int oId,
			String email) throws SQLException {
		
		String sql = "select unsubscribed, uuid "
				+ "from people "
				+ "where email = ?";
		PreparedStatement pstmt = null;
		
		String sqlCreate = "insert into people "
				+ "(o_id, email, unsubscribed, uuid) "
				+ "values(?, ?, 'false', ?)";
		PreparedStatement pstmtCreate = null;
		
		String key = null;
		try {
			if(email != null) {
				email = email.toLowerCase();
				
				pstmt = sd.prepareStatement(sql);	
				pstmt.setString(1, email);
				
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					boolean unsubscribed = rs.getBoolean(1);
					if(!unsubscribed) {
						key = rs.getString(2);
					}
				} else {
					// Create a key for this email and save it in the people table
					key = UUID.randomUUID().toString();
					pstmtCreate = sd.prepareStatement(sqlCreate);
					pstmtCreate.setInt(1,  oId);
					pstmtCreate.setString(2, email);
					pstmtCreate.setString(3, key);
					pstmtCreate.executeUpdate();
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtCreate != null) {pstmtCreate.close();} } catch (SQLException e) {	}
		}
		
		return key;

	}
	
	/*
	 * Get key that can be used to subscribe to emails
	 */
	public String getSubscriptionKey(Connection sd, String email) throws SQLException, ApplicationException {
		
		String sqlRegulate = "select count(*) "
				+ "from people "
				+ "where email = ? "
				+ "and unsubscribed "
				+ "and (when_requested_subscribe + interval '1 day') > timestamp 'now' ";
		PreparedStatement pstmtRegulate = null;
		
		String sql = "select unsubscribed, uuid "
				+ "from people "
				+ "where email = ?";
		PreparedStatement pstmt = null;
		
		// Create an entry with the user initially unsubscribed
		String sqlCreate = "insert into people "
				+ "(o_id, email, unsubscribed, uuid, when_requested_subscribe) "
				+ "values(0, ?, 'true', ?, now())";
		PreparedStatement pstmtCreate = null;
		
		String sqlUpdate = "update people set "
				+ "uuid = ?, "
				+ "when_requested_subscribe = now() "
				+ "where email = ?";		
		PreparedStatement pstmtUpdate = null;
		
		String key = null;
		try {
			
			
			if(email != null) {
				
				email = email.toLowerCase();
				
				// Make sure no subscribe requests have been made in the last 24 hours
				pstmtRegulate = sd.prepareStatement(sqlRegulate);
				pstmtRegulate.setString(1, email);
				log.info("Check for aleady sent subscription request: " + pstmtRegulate.toString());
				ResultSet rs = pstmtRegulate.executeQuery();
				if(rs.next() && rs.getInt(1) > 0) {
					log.info("Email request subscription already sent");
					throw new ApplicationException(localisation.getString("email_subs"));
				}
				
				/*
				 * Get an existing UUID
				 */
				pstmt = sd.prepareStatement(sql);	
				pstmt.setString(1, email);
				
				rs = pstmt.executeQuery();
				if(rs.next()) {
					// We already have an entry for this email
					boolean unsubscribed = rs.getBoolean(1);
					if(unsubscribed) {
						// Create a new key and update the people table
						key = UUID.randomUUID().toString();
						pstmtUpdate = sd.prepareStatement(sqlUpdate);				
						pstmtUpdate.setString(1, key);
						pstmtUpdate.setString(2, email);
						pstmtUpdate.executeUpdate();
					} else {
						throw new ApplicationException(localisation.getString("c_as"));
					}
				} else {
					// Create a key for this email and save it in the people table
					key = UUID.randomUUID().toString();
					pstmtCreate = sd.prepareStatement(sqlCreate);
					pstmtCreate.setString(1, email);
					pstmtCreate.setString(2, key);
					pstmtCreate.executeUpdate();
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtCreate != null) {pstmtCreate.close();} } catch (SQLException e) {	}
			try {if (pstmtUpdate != null) {pstmtUpdate.close();} } catch (SQLException e) {	}
			try {if (pstmtRegulate != null) {pstmtRegulate.close();} } catch (SQLException e) {	}
		}
		
		return key;

	}
	
	/*
	 * Unsubscribe the user based on the key
	 */
	public String unsubscribe(Connection sd, 
			String key) throws SQLException, ApplicationException {
		
		String sql = "update people "
				+ "set unsubscribed = true,"
				+ "when_unsubscribed = now() "
				+ "where uuid = ? "
				+ "and not unsubscribed";
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, key);			
			int count = pstmt.executeUpdate();
			if(count == 0) {
				throw new ApplicationException(localisation.getString("c_ns"));
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return key;

	}
	
	/*
	 * Subscribe the user based on the key
	 */
	public String subscribeStep2(Connection sd, 
			String key) throws SQLException, ApplicationException {
		
		String sql = "update people "
				+ "set unsubscribed = false,"
				+ "when_subscribed = now() "
				+ "where uuid = ? ";
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);	
			pstmt.setString(1, key);			
			int count = pstmt.executeUpdate();
			if(count == 0) {
				throw new ApplicationException(localisation.getString("c_error"));
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
		}
		
		return key;

	}

}


