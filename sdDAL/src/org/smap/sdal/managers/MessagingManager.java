package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Organisation;

/*****************************************************************************
 * 
 * This file is part of SMAP.
 * 
 * SMAP is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * SMAP is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SMAP. If not, see <http://www.gnu.org/licenses/>.
 * 
 ******************************************************************************/

/*
 * Manage the table that stores details on the forwarding of data onto other
 * systems
 */
public class MessagingManager {

	private static Logger log = Logger.getLogger(MessagingManager.class.getName());

	LogManager lm = new LogManager(); // Application log

	/*
	 * Apply any outbound messages
	 */
	public void applyOutbound(Connection sd, String serverName) {

		ResultSet rs = null;
		PreparedStatement pstmtGetMessages = null;
		PreparedStatement pstmtConfirm = null;

		String sqlGetMessages = "select id, "
				+ "o_id, "
				+ "topic, "
				+ "description, "
				+ "data "
				+ "from message "
				+ "where outbound "
				+ "and processed_time is null";

		String sqlConfirm = "update message set processed_time = now(), status = ? where id = ?; ";

		try {

			pstmtGetMessages = sd.prepareStatement(sqlGetMessages);
			pstmtConfirm = sd.prepareStatement(sqlConfirm);

			rs = pstmtGetMessages.executeQuery();
			while (rs.next()) {

				int id = rs.getInt(1);
				int o_id = rs.getInt(2);
				String topic = rs.getString(3);
				String description = rs.getString(4);
				
				// Localisation
				Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, o_id);
				Locale locale = new Locale(organisation.locale);
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				log.info("++++++ Message: " + topic + " " + description);

				String status = "Success";
				
				/*
				 * Send document to target
				 */
				
				pstmtConfirm.setString(1, "Sending");
				pstmtConfirm.setInt(2, id);
				log.info(pstmtConfirm.toString());
				pstmtConfirm.executeUpdate();
				
				EmailServer emailServer = UtilityMethodsEmail.getSmtpHost(sd, null, null);
				if (isValidEmail(topic) && 
						emailServer.smtpHost != null && emailServer.smtpHost.trim().length() > 0) {

					// Set the subject
					String subject = "";
					String from = "";

					subject += localisation.getString("c_message");

					try {
						EmailManager em = new EmailManager();

						em.sendEmail(topic, null, "notify", subject, description, from, null, null, null, null, null,
								null, organisation.getAdminEmail(), emailServer, "https", serverName, localisation);
					} catch (Exception e) {
						status = "Error";
					}

				} else {
					log.log(Level.SEVERE, "Error: Attempt to do email notification but email server not set");
					status = "Error: email server not enabled";
				}
				
				pstmtConfirm.setString(1, status);
				pstmtConfirm.setInt(2, id);
				log.info(pstmtConfirm.toString());
				pstmtConfirm.executeUpdate();

			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		} finally {
			try {
				if (pstmtGetMessages != null) {
					pstmtGetMessages.close();
				}
			} catch (Exception e) {
			}
			try {
				if (pstmtConfirm != null) {
					pstmtConfirm.close();
				}
			} catch (Exception e) {
			}
		}

	}

	/*
	 * Validate an email
	 */
	public boolean isValidEmail(String email) {
		boolean isValid = true;
		try {
			InternetAddress emailAddr = new InternetAddress(email);
			emailAddr.validate();
		} catch (AddressException ex) {
			isValid = false;
		}
		return isValid;
	}
}
