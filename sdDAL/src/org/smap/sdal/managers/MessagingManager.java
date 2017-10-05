package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.SurveyMessage;
import org.smap.sdal.model.TaskMessage;
import org.smap.sdal.model.UserMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
	 * Create a message resulting from a change to a task
	 */
	public void taskChange(Connection sd, int taskId) throws SQLException {
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		String data = gson.toJson(new TaskMessage(taskId));		
		int oId = GeneralUtilityMethods.getOrganisationIdForTask(sd, taskId);	
		if(oId >= 0) {
			createMessage(sd, oId, "task", null, data);
		}
	}
	
	/*
	 * Create a message resulting from a change to a user
	 */
	public void userChange(Connection sd, String userIdent) throws SQLException {
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		String data = gson.toJson(new UserMessage(userIdent));		
		int oId = GeneralUtilityMethods.getOrganisationId(sd, userIdent, 0);	
		if(oId >= 0) {
			createMessage(sd, oId, "user", null, data);
		}
	}
	
	/*
	 * Create a message resulting from a change to a form
	 */
	public void surveyChange(Connection sd, int sId, int linkedId) throws SQLException {
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		SurveyMessage sm = new SurveyMessage(sId);
		sm.linkedSurveyId = linkedId;
		String data = gson.toJson(sm);
		int oId = GeneralUtilityMethods.getOrganisationIdForSurvey(sd, sId);	
		if(oId >= 0) {
			createMessage(sd, oId, "survey", null, data);
		}
	}
	
	/*
	 * Create a new message
	 */
	public void createMessage(Connection sd, int oId, String topic, String msg, String data) throws SQLException {
		
		String sqlMsg = "insert into message" + "(o_id, topic, description, data, outbound, created_time) "
				+ "values(?, ?, ?, ?, 'true', now())";
		PreparedStatement pstmtMsg = null;
		
		try {
			pstmtMsg = sd.prepareStatement(sqlMsg);
			pstmtMsg.setInt(1, oId);
			pstmtMsg.setString(2, topic);
			pstmtMsg.setString(3, msg);
			pstmtMsg.setString(4, data);
			log.info("Add message: " + pstmtMsg.toString());
			pstmtMsg.executeUpdate();
		} finally {

			try {if (pstmtMsg != null) {	pstmtMsg.close();}} catch (SQLException e) {}

		}
	}
	
	
	

}
