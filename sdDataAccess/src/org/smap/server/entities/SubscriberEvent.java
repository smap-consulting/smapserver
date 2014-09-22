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
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Transient;

@Entity(name="SUBSCRIBER_EVENT")
public class SubscriberEvent implements Serializable{
	
	private static final long serialVersionUID = -6410553162606844278L;

	@Id
	@Column(name="se_id", nullable=false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator="se_seq")
	@SequenceGenerator(name="se_seq", sequenceName="se_seq")
	private int se_id;
	
	@ManyToOne
	@JoinColumn(name = "ue_id", referencedColumnName = "ue_id")
	private UploadEvent ue;
	
	@Column(name="subscriber")
	private String subscriber = null;
	
	@Column(name="status")
	private String status = null;
	
	@Column(name="dest")
	private String dest = null;
	
	@Column(name="reason")
	private String reason = null;

	public SubscriberEvent() {
		
	}

	/*
	 * Getters
	 */
	public UploadEvent getUploadEvent() {
		return ue;
	}
	
	public int getId() {
		return se_id;
	}
	
	public String getSubscriber() {
		return subscriber;
	}
	
	public String getDest() {
		return dest;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getReason() {
		return reason;
	}
 
	/*
	 * Setters
	 */
	public void setUploadEvent(UploadEvent value) {
		ue = value;
	}
	   
    public void setSubscriber(String value) {
    	subscriber = value;
    }
    
	public void setStatus(String value) {
		status = value;
	}
	
	public void setDest(String value) {
		dest  = value;
	}
	
	public void setReason(String value) {
		reason = value;
	}

    

}
