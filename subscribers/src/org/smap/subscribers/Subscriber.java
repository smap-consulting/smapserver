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

package org.smap.subscribers;
import java.io.InputStream;
import java.util.Date;
import java.util.ResourceBundle;

import org.smap.model.SurveyInstance;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.HostUnreachableException;
import org.smap.server.entities.SubscriberEvent;
import org.w3c.dom.Document;
import org.w3c.dom.Node;


public abstract class Subscriber {
	
	String name = null;
	String filter = null;
	boolean enabled = false;
	boolean surveySpecific = false;
	String sIdentRemote;		
	String sNameRemote;
	int sId;
	String user = null;
	String password = null;
	String host = null;
	Document configurationDocument = null;
	ResourceBundle localisation;
	String tz = "UTC";		// set default tz

	final int DUPLICATE_DROP = 1;
	final int DUPLICATE_REPLACE = 2;
	private int duplicatePolicy = DUPLICATE_DROP;
	
	Subscriber() {
		
	}

	/*
	 * @param event
	 * @param id
	 * @param user
	 * @param server
	 * @param se
	 */
	public abstract void upload(SurveyInstance event, InputStream id, String user, String server, String device, 
			SubscriberEvent se, String confFilePath, String formStatus, String basePath, String filePath,
			String updateId, int ue_id, Date uploadTime, 
			String surveyNotes, String locationTrigger,
			String auditFilePath, ResourceBundle l, Survey survey) throws HostUnreachableException;

	/*
	 * Getters
	 */
	public String getSubscriberName() {
		return name;
	}
	public String getSubscriberFilter() {
		return filter;
	}
	public int getDuplicatePolicy() {
		return duplicatePolicy;
	}
	public boolean isEnabled() {
		return enabled;
	}
	public boolean isSurveySpecific() {
		return surveySpecific;
	}
	public String getHostname() {
		return host;
	}
	public int getSurveyId() {
		return sId;
	}
	public String getSurveyIdRemote() {
		return sIdentRemote;
	}
	public String getSurveyNameRemote() {
		return sNameRemote;
	}
	public Document getConfigurationDocument() {
		return configurationDocument;
	}
	public abstract String getDest(); 	// Destination of subscriber - specific to each implementation

	/*
	 * Setters
	 */
	public void setSubscriberName(String value) {
		name = value;
	}
	public void setSubscriberFilter(String value) {
		filter = value;
	}
	public void setDuplicatePolicy(String value) {
		if(value == null) {
			duplicatePolicy = DUPLICATE_DROP;
		} else {
			if(value.equals("drop")) {
				duplicatePolicy = DUPLICATE_DROP;
			} else if(value.equals("replace")) {
				duplicatePolicy = DUPLICATE_REPLACE;
			}
		}
	}
	public void setEnabled(boolean value) {
		enabled = value;
	}
	public void setUser(String value) {
		user = value;
	}
	public void setHostname(String value) {
		host = value;
	}
	public void setPassword(String value) {
		password = value;
	}
	public void setSurveyId(int value) {
		sId = value;
		if(value > 0) {
			surveySpecific = true;
		}else {
			surveySpecific = false;
		}
	}
	public void setSurveyIdRemote(String value) {
		sIdentRemote = value;
	}
	public void setSurveyNameRemote(String value) {
		sNameRemote = value;
	}
	public void setConfigurationDocument(Document value) {
		configurationDocument = value;
		initialise();
	}
	
	/*
	 * Initialise the subscriber data using the configuration file
	 */
	public void initialise() {
		// Get the enabled element value
		String enabledString = null;
		String subscriberFilter = null;
		String duplicatePolicy = null;
		enabledString = configurationDocument.getElementsByTagName("enabled").item(0).getTextContent();
		if(enabledString.trim().length() != 0 && enabledString.toLowerCase().startsWith("y")) {
			enabled = true;
		}
		setEnabled(enabled);
		
		// Get the filter element value
		subscriberFilter = configurationDocument.getElementsByTagName("filter").item(0).getTextContent();
		if(subscriberFilter.trim().length() == 0) {
			subscriberFilter = null;
		}
		setSubscriberFilter(subscriberFilter); 
		
		// Get the duplicate policy value
		Node n =configurationDocument.getElementsByTagName("duplicate_policy").item(0);
		if(n != null) {
			duplicatePolicy = n.getTextContent();
			if(duplicatePolicy.trim().length() == 0) {
				duplicatePolicy = null;
			}
		}
		
		setDuplicatePolicy(duplicatePolicy); 
		
	}
}
