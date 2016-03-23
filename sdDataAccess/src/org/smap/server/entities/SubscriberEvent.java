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

public class SubscriberEvent implements Serializable{
	
	private static final long serialVersionUID = -6410553162606844278L;

	private int se_id;
	
	private UploadEvent ue;
	
	private String subscriber = null;
	
	private String status = null;
	
	private String dest = null;
	
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
