package org.smap.notifications.interfaces;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;



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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class EmitNotifications {
	
	/*
	 * Events
	 */
	public static int AWS_REGISTER_ORGANISATION = 0;
	
	private static Logger log =
			 Logger.getLogger(EmitNotifications.class.getName());
	
	Properties properties = new Properties();
	
	public EmitNotifications() {
		try {
				properties.load(getClass().getClassLoader().getResourceAsStream("aws.properties"));
			}
			catch (Exception e) { 
				log.log(Level.SEVERE, "Error reading properties", e);
			}
	}
	
	/*
	 * Publish to an SNS topic
	 */
	public void publish(int event, String msg, String subject) {
		
		//create a new SNS client and set endpoint
		AmazonSNSClient snsClient = new AmazonSNSClient(new ClasspathPropertiesFileCredentialsProvider());		                           
		snsClient.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));	// Singapore
		
		String topic = getTopic(event);
		
		if(topic != null) {
			PublishRequest publishRequest = new PublishRequest(topic, msg, subject);
			PublishResult publishResult = snsClient.publish(publishRequest);
			log.info("Publish: " + subject + " MessageId - " + publishResult.getMessageId());
		} 
		
	}
	
	private String getTopic(int event) {
		
		String topic = null;
		
		if (event == AWS_REGISTER_ORGANISATION) {
			topic = properties.getProperty("register_organisation_topic");
			log.info("Publish: register");
		} else {
			log.info("Error: Publish: invalid event: " + event);
		}
		
		return topic;
	}

}


