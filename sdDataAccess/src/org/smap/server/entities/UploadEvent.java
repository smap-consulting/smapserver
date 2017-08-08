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


/*
 * Class to store an upload event
 */
public class UploadEvent implements Serializable {

	private static final long serialVersionUID = -4784547709615805141L;

	// Database Attributes
	private int ue_id;

	private Date uploadTime;
	
	private String userName;
	
	private String fileName;
	
	private String surveyName;
	
	private int s_id;
	
	private String ident;
	
	private int p_id;
	
	private String imei;
	
	private String origSurveyIdent;

	private String updateId;
	
	private int assignmentId;
	
	private String instanceId;

	private String status;
	
	private String reason;
		
	private String location; 	// Store location as a string of "longitude latitude"
	
	private String serverName;
	
	private String formStatus;
	
	private String filePath;
	
	private String auditFilePath;
	
	private boolean incomplete = false;
	
	private String surveyNotes;
	
	private String locationTrigger;
	
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
	
	public String getInstanceId() {
		return instanceId;
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
	
	public String getAuditFilePath() {
		return auditFilePath;
	}
	
	public boolean getIncomplete() {
		return incomplete;
	}
	
	public String getSurveyNotes() {
		return surveyNotes;
	}
	
	public String getLocationTrigger() {
		return locationTrigger;
	}
	
	/*
	 * Setters
	 */
	public void setId(int v) {
		ue_id = v;
	}
	
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
	
	public void setAuditFilePath(String value) {
		auditFilePath = value;
	}
	
	public void setIncomplete(boolean value) {
		incomplete = value;
	}
	
	public void setSurveyNotes(String value) {
		surveyNotes = value;
	}
	
	public void setLocationTrigger(String value) {
		locationTrigger = value;
	}

}
