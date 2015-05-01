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

package org.smap.server.entities;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

/*
 * Class to store an upload event
 */
@Entity(name = "UPLOAD_EVENT")
public class UploadEvent implements Serializable {

	private static final long serialVersionUID = -4784547709615805141L;

	// Database Attributes
	@Id
	@Column(name="ue_id", nullable=false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator="ue_seq")
	@SequenceGenerator(name="ue_seq", sequenceName="ue_seq")
	private int ue_id;

	@Column(name="upload_time")
	private Date uploadTime;
	
	@Column(name="user_name")
	private String userName;
	
	@Column(name="file_name")
	private String fileName;
	
	@Column(name="survey_name")
	private String surveyName;
	
	@Column(name="s_id")
	private int s_id;
	
	@Column(name="ident")
	private String ident;
	
	@Column(name="p_id")
	private int p_id;
	
	@Column(name="imei")
	private String imei;
	
	@Column(name="orig_survey_ident")
	private String origSurveyIdent;
	
	@Column(name="update_id")
	private String updateId;
	
	@Column(name="assignment_id")
	private int assignmentId;
	
	@Column(name="instanceid")
	private String instanceId;

	@Column(name="status")
	private String status;
	
	@Column(name="reason")
	private String reason;
	
	@Column(name="location")	// Store location as a string of "longitude latitude"
	private String location; 
	
	@Column(name="server_name")
	private String serverName;
	
	@Column(name="form_status")
	private String formStatus;
	
	@Column(name="file_path")
	private String filePath;
	
	@Column(name="incomplete")
	private boolean incomplete = false;
	
	/*
	 * Constructor
	 */
	public UploadEvent() {
	}
	
	/*
	 * Getters
	 */
	public int getId() {
		return ue_id;
	}
	
	public String getIdent() {
		return ident;
	}
	
	public Date getUploadTime() {
		return uploadTime;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public String getFileName() {
		return fileName;
	}

	public String getSurveyName() {
		return surveyName;
	}
	
	public int getSurveyId() {
		return s_id;
	}
	
	public int getProjectId() {
		return p_id;
	}
	
	public String getImei() {
		return imei;
	}
	
	public String getOrigSurveyIdent() {
		return origSurveyIdent;
	}
	
	public String getUpdateId() {
		return updateId;
	}
	
	public int getAssignmentId() {
		return assignmentId;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getReason() {
		return reason;
	}
	
	public String getLocation() {
		return location;
	}
	
	public String getServerName() {
		return serverName;
	}
	
	public String getFormStatus() {
		return formStatus;
	}
	
	public String getFilePath() {
		return filePath;
	}
	
	public boolean getIncomplete() {
		return incomplete;
	}
	
	/*
	 * Setters
	 */
	
	public void setIdent(String v) {
		ident = v;
	}
	
	public void setUploadTime(Date uploadTime) {
		this.uploadTime = uploadTime;
	}
	
	public void setUserName(String name) {
		this.userName = name;
	}
	
	public void setFileName(String name) {
		this.fileName = name;
	}

	public void setSurveyName(String name) {
		this.surveyName = name;
	}
	
	public void setSurveyId(int name) {
		this.s_id = name;
	}
	
	public void setProjectId(int name) {
		this.p_id = name;
	}
	
	public void setImei(String imei) {
		this.imei = imei;
	}
	
	public void setOrigSurveyIdent(String v) {
		origSurveyIdent = v;
	}
	
	public void setUpdateId(String v) {
		updateId = v;
	}
	
	public void setAssignmentId(int v) {
		assignmentId = v;
	}
	
	public void setInstanceId(String v) {
		instanceId = v;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public void setReason(String reason) {
		this.reason = reason;
	}
	
	public void setLocation(String location) {
		if(location != null) {
			String params[] = location.split(" ");
			if(params.length > 1) {
				this.location = params[1] + " " + params[0];
			}
		}
	}
	
	public void setServerName(String value) {
		serverName = value;
	}
	
	public void setFormStatus(String value) {
		formStatus = value;
	}
	
	public void setFilePath(String value) {
		filePath = value;
	}
	
	public void setIncomplete(boolean value) {
		incomplete = value;
	}

}
