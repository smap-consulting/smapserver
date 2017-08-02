package tools;

/*
 * Copyright 2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.notifications.interfaces.EmitNotifications;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreatePlatformApplicationRequest;
import com.amazonaws.services.sns.model.CreatePlatformApplicationResult;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.DeletePlatformApplicationRequest;
import com.amazonaws.services.sns.model.EndpointDisabledException;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

import sun.rmi.runtime.Log;
import tools.SampleMessageGenerator.Platform;

public class AmazonSNSClientWrapper {

	private static Logger log =
			 Logger.getLogger(EmitNotifications.class.getName());
	private final AmazonSNS snsClient;

	public AmazonSNSClientWrapper(AmazonSNS client) {
		this.snsClient = client;
	}

	private CreatePlatformEndpointResult createPlatformEndpoint(
			Platform platform, String customData, String platformToken,
			String applicationArn) {
		
		CreatePlatformEndpointRequest platformEndpointRequest = new CreatePlatformEndpointRequest();
		platformEndpointRequest.setCustomUserData(customData);
		platformEndpointRequest.setToken(platformToken);
		platformEndpointRequest.setPlatformApplicationArn(applicationArn);
		return snsClient.createPlatformEndpoint(platformEndpointRequest);
	}

	private PublishResult publish(String endpointArn, Platform platform,
			Map<Platform, Map<String, MessageAttributeValue>> attributesMap) {
		PublishRequest publishRequest = new PublishRequest();
		Map<String, MessageAttributeValue> notificationAttributes = getValidNotificationAttributes(attributesMap
				.get(platform));
		if (notificationAttributes != null && !notificationAttributes.isEmpty()) {
			publishRequest.setMessageAttributes(notificationAttributes);
		}
		publishRequest.setMessageStructure("json");
		// If the message attributes are not set in the requisite method,
		// notification is sent with default attributes
		String message = getPlatformSampleMessage(platform);
		Map<String, String> messageMap = new HashMap<String, String>();
		messageMap.put(platform.name(), message);
		message = SampleMessageGenerator.jsonify(messageMap);
		// For direct publish to mobile end points, topicArn is not relevant.
		publishRequest.setTargetArn(endpointArn);

		// Display the message that will be sent to the endpoint/
		System.out.println("{Message Body: " + message + "}");
		StringBuilder builder = new StringBuilder();
		builder.append("{Message Attributes: ");
		for (Map.Entry<String, MessageAttributeValue> entry : notificationAttributes
				.entrySet()) {
			builder.append("(\"" + entry.getKey() + "\": \""
					+ entry.getValue().getStringValue() + "\"),");
		}
		builder.deleteCharAt(builder.length() - 1);
		builder.append("}");
		System.out.println(builder.toString());

		publishRequest.setMessage(message);
		
		PublishResult result = null;
		try {
			result = snsClient.publish(publishRequest);
		} catch (EndpointDisabledException e) {
			System.out.println("Endpoint disabled:  TODO remove from dynamo DB");
		} catch(Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return result;
	}

	public void demoNotification(Platform platform,	String platformToken,
			Map<Platform, Map<String, MessageAttributeValue>> attrsMap, String platformApplicationArn) {
		
		// Create an Endpoint. This corresponds to an app on a device.
		CreatePlatformEndpointResult platformEndpointResult = createPlatformEndpoint(
				platform,
				"CustomData - Useful to store endpoint specific data",
				platformToken, platformApplicationArn);
		System.out.println(platformEndpointResult);

		// Publish a push notification to an Endpoint.
		PublishResult publishResult = publish(
				platformEndpointResult.getEndpointArn(), platform, attrsMap);
		if(publishResult != null) {
			System.out.println("Published! \n{MessageId="
					+ publishResult.getMessageId() + "}");
		}
	}

	private String getPlatformSampleMessage(Platform platform) {
		switch (platform) {
		case APNS:
			return SampleMessageGenerator.getSampleAppleMessage();
		case APNS_SANDBOX:
			return SampleMessageGenerator.getSampleAppleMessage();
		case GCM:
			return SampleMessageGenerator.getSampleAndroidMessage();
		case ADM:
			return SampleMessageGenerator.getSampleKindleMessage();
		case BAIDU:
			return SampleMessageGenerator.getSampleBaiduMessage();
		case WNS:
			return SampleMessageGenerator.getSampleWNSMessage();
		case MPNS:
			return SampleMessageGenerator.getSampleMPNSMessage();
		default:
			throw new IllegalArgumentException("Platform not supported : "
					+ platform.name());
		}
	}

	public static Map<String, MessageAttributeValue> getValidNotificationAttributes(
			Map<String, MessageAttributeValue> notificationAttributes) {
		Map<String, MessageAttributeValue> validAttributes = new HashMap<String, MessageAttributeValue>();

		if (notificationAttributes == null) return validAttributes;

		for (Map.Entry<String, MessageAttributeValue> entry : notificationAttributes
				.entrySet()) {
			if (!StringUtils.isBlank(entry.getValue().getStringValue())) {
				validAttributes.put(entry.getKey(), entry.getValue());
			}
		}
		return validAttributes;
	}
}