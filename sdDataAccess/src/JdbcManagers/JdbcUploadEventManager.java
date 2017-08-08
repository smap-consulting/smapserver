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

package JdbcManagers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.UploadEvent;

public class JdbcUploadEventManager {

	PreparedStatement pstmt = null;
	String sql = "insert into upload_event ("
			+ "ue_id, "
			+ "upload_time, "
			+ "user_name, "
			+ "file_name, "
			+ "survey_name, "
			+ "imei, "
			+ "status, "
			+ "reason, "
			+ "location,"
			+ "server_name,"
			+ "s_id,"
			+ "p_id,"
			+ "form_status,"
			+ "file_path,"
			+ "orig_survey_ident,"
			+ "update_id,"
			+ "ident,"
			+ "incomplete,"
			+ "instanceid,"
			+ "assignment_id,"
			+ "survey_notes,"
			+ "location_trigger,"
			+ "audit_file_path) "
			+ "values (nextval('ue_seq'), now(), ?, ?, ?, ?, ?, ?, ?, ?, ?"
			+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
			+ ", ?, ?);";
	
	PreparedStatement pstmtFailed = null;
	String sqlGet = "select "
			+ "ue.ue_id, "
			+ "ue.upload_time, "
			+ "ue.user_name, "
			+ "ue.file_name, "
			+ "ue.survey_name, "
			+ "ue.imei, "
			+ "ue.status, "
			+ "ue.reason, "
			+ "ue.location,"
			+ "ue.server_name,"
			+ "ue.s_id,"
			+ "ue.p_id,"
			+ "ue.form_status,"
			+ "ue.file_path,"
			+ "ue.orig_survey_ident,"
			+ "ue.update_id,"
			+ "ue.ident,"
			+ "ue.incomplete,"
			+ "ue.instanceid,"
			+ "ue.assignment_id,"
			+ "ue.survey_notes,"
			+ "ue.location_trigger,"
			+ "ue.audit_file_path "
			+ "from upload_event ue "
				+ "where ue.status = 'success' "
				+ "and ue.s_id is not null "
				+ "and ue.incomplete = 'false' "
				+ "and not exists (select se.se_id from subscriber_event se "
					+ "where se.subscriber = ? and se.ue_id = ue.ue_id)";
	
	PreparedStatement pstmtFailedForward = null;
	String sqlForwardFilter = " and ue.s_id = ?";
	
	/*
	 * Constructor
	 */
	public JdbcUploadEventManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql);
		pstmtFailed = sd.prepareStatement(sqlGet);
		pstmtFailedForward = sd.prepareStatement(sqlGet + sqlForwardFilter);
	}
	
	/*
	 * Write the upload event to the database
	 */
	public void write(UploadEvent ue) throws SQLException {
		pstmt.setString(1, ue.getUserName());
		pstmt.setString(2, ue.getFileName());
		pstmt.setString(3,  ue.getSurveyName());
		pstmt.setString(4, ue.getImei());
		pstmt.setString(5, ue.getStatus());
		pstmt.setString(6, ue.getReason());
		pstmt.setString(7, ue.getLocation());
		pstmt.setString(8, ue.getServerName());
		pstmt.setInt(9, ue.getSurveyId());
		pstmt.setInt(10,  ue.getProjectId());
		pstmt.setString(11, ue.getFormStatus());
		pstmt.setString(12, ue.getFilePath());
		pstmt.setString(13, ue.getOrigSurveyIdent());
		pstmt.setString(14,  ue.getUpdateId());
		pstmt.setString(15,  ue.getIdent());
		pstmt.setBoolean(16, ue.getIncomplete());
		pstmt.setString(17, ue.getInstanceId());
		pstmt.setInt(18, ue.getAssignmentId());
		pstmt.setString(19, ue.getSurveyNotes());
		pstmt.setString(20, ue.getLocationTrigger());
		pstmt.setString(21, ue.getAuditFilePath());
	
		pstmt.executeUpdate();
	}
	

	/*
	 * Get Uplaods that have not been processed by the subscriber
	 */
	public List<UploadEvent> getFailed(String subscriber) throws SQLException {
		pstmtFailed.setString(1, subscriber);
		return getUploadEventList(pstmtFailed);
	}
	
	public List<UploadEvent> getFailedForward(String subscriber, int sId) throws SQLException {
		pstmtFailedForward.setString(1, subscriber);
		pstmtFailedForward.setInt(2, sId);
		return getUploadEventList(pstmtFailedForward);
	}
	
	/*
	 * Close prepared statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtFailed != null) {pstmtFailed.close();}} catch(Exception e) {};
		try {if(pstmtFailedForward != null) {pstmtFailedForward.close();}} catch(Exception e) {};
	}
	
	private List <UploadEvent> getUploadEventList(PreparedStatement pstmt) throws SQLException {
		ArrayList <UploadEvent> ueList = new ArrayList<UploadEvent> ();
		
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			UploadEvent ue = new UploadEvent();
			ue.setId(rs.getInt(1));
			ue.setUploadTime(rs.getTimestamp(2));
			ue.setUserName(rs.getString(3));
			ue.setFileName(rs.getString(4));
			ue.setSurveyName(rs.getString(5));
			ue.setImei(rs.getString(6));
			ue.setStatus(rs.getString(7));
			ue.setReason(rs.getString(8));
			ue.setLocation(rs.getString(9));
			ue.setServerName(rs.getString(10));
			ue.setSurveyId(rs.getInt(11));
			ue.setProjectId(rs.getInt(12));
			ue.setFormStatus(rs.getString(13));
			ue.setFilePath(rs.getString(14));
			ue.setOrigSurveyIdent(rs.getString(15));
			ue.setUpdateId(rs.getString(16));
			ue.setIdent(rs.getString(17));
			ue.setIncomplete(rs.getBoolean(18));
			ue.setInstanceId(rs.getString(19));
			ue.setAssignmentId(rs.getInt(20));
			ue.setSurveyNotes(rs.getString(21));
			ue.setLocationTrigger(rs.getString(22));
			ue.setAuditFilePath(rs.getString(23));
			
			ueList.add(ue);
		}
		return ueList;
	}
}
