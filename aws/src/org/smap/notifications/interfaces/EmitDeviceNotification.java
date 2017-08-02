package org.smap.notifications.interfaces;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;

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
 * Manage access to the Dynamo table that holds the connection between user and
 * device
 */
public class EmitDeviceNotification {

	private static Logger log = Logger.getLogger(EmitDeviceNotification.class.getName());

    static AmazonDynamoDB dynamoDB;
	Properties properties = new Properties();
	String tableName = null;
	String region = null;

	public EmitDeviceNotification() {
		
		// get properties file
		try {
			properties.load(new FileInputStream("/smap_bin/resources/properties/aws.properties"));
			tableName = properties.getProperty("userDevices_table");
			region = properties.getProperty("userDevices_region");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error reading properties", e);
		}
        
		/*
		try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/Users/neilpenman/.aws/credentials), and is in valid format.",
                    e);
        }
        */
            
		//create a new SNS client and set endpoint
		dynamoDB = AmazonDynamoDBClient.builder()
				.withRegion(region)
				.withCredentials(new ProfileCredentialsProvider())
				.build();

	}

	/*
	 * Send a message to users registered with a server, name combo
	 */
	public void notify(String server, String user) {
		
		// For testing on local host - can leave in final code
		if(server.equals("smap")) {
			server = "dev.smap.com.au";
		}
		
		System.out.println("Notify: " + server + ":" + user);
		 HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
         Condition conditionServer = new Condition()
             .withComparisonOperator(ComparisonOperator.EQ.toString())
             .withAttributeValueList(new AttributeValue().withS(server));
         Condition conditionUser = new Condition()
                 .withComparisonOperator(ComparisonOperator.EQ.toString())
                 .withAttributeValueList(new AttributeValue().withS(user));
         scanFilter.put("smapServer", conditionServer);
         scanFilter.put("userIdent", conditionUser);
         ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
         ScanResult scanResult = dynamoDB.scan(scanRequest);
         System.out.println("Result: " + scanResult); 
		
		
	
		
	}

}
