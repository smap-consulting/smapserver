package org.smap.notifications.interfaces;

import java.io.FileInputStream;
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
 * Manage access to the Dynamo table that holds the connection between user and device
 */
public class EmitDeviceNotification {
	
	private static Logger log =
			 Logger.getLogger(EmitDeviceNotification.class.getName());
	
	Properties properties = new Properties();
	
	public EmitDeviceNotification() {
		try {
				properties.load(new FileInputStream("/smap_bin/resources/properties/aws.properties"));
			}
			catch (Exception e) { 
				log.log(Level.SEVERE, "Error reading properties", e);
			}
	}
	
	/*
	 * Get the phone registration identifiers for this user
	 */
	public void getRegistrationIds(String server, String userIdent) {
		
		String tableName = properties.getProperty("userDevices_table");
		System.out.println("Table Name: " + tableName);
		
	
		
	}
	


}


