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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.smap.model.IE;
import org.smap.model.SurveyInstance;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.server.entities.SubscriberEvent;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class SubUshahidi extends Subscriber {
	

	/**
	 * @param args
	 */
	public SubUshahidi() {
		super();

	}
	
	@Override
	public String getDest() {
		return "ushahidi";
		
	}
	
	@Override
	public void upload(SurveyInstance instance, InputStream is, String remoteUser, 
			String server, String device, SubscriberEvent se, String confFilePath, String formStatus,
			String basePath, String filePath, String updateId, int ue_id)  {
		
		String url = null;	// Address of Ushahidi API

			
		IE topElement = instance.getTopElement();	// Get the results object

				
		XPathFactory factory = XPathFactory.newInstance();	// Get an XPath object to parse the configuration file
	    XPath xpath = factory.newXPath();
		XPathExpression expr = null;

		List<String> images = topElement.getMedia("image");
		List<String> videos = topElement.getMedia("video");
		videos.addAll(topElement.getMedia("audio"));
		
		HttpClient httpClient = new DefaultHttpClient();
		httpClient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);		
		MultipartEntity entity = new MultipartEntity( );

		try {
			/*
			 * Get the results document
			 */
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder b = dbf.newDocumentBuilder();							
			Document surveyDocument = b.parse(is);

			/*
			 * Get the URL of the Ushahidi server
			 */
			expr = xpath.compile("/subscriber/subscriber_specific/url");
	    	url = expr.evaluate(configurationDocument);
	    	
			/*
			 * Add the parameters to the URL
			 */
			expr = xpath.compile("/subscriber/subscriber_specific/parameter");
	    	NodeList nodes = (NodeList) expr.evaluate(configurationDocument, XPathConstants.NODESET);			
	    	// Get all the parameters
	    	for (int i = 0; i < nodes.getLength(); i++) {
	    		Node param = nodes.item(i);
	    		
	    		// Get the attributes
	    		HashMap<String,String> mapHash = new HashMap();
	    		
	    		expr = xpath.compile("name");
	    		String name = expr.evaluate(param);
	    		expr = xpath.compile("type");
	    		String type = expr.evaluate(param);
	    		expr = xpath.compile("value");
	    		String value = expr.evaluate(param);
	    		    		
	    		expr = xpath.compile("map");
	    		NodeList maps = (NodeList) expr.evaluate(param, XPathConstants.NODESET);
	    		for(int j = 0; j < maps.getLength(); j++) {
	    			Node map = maps.item(j);
	    			
	    			expr = xpath.compile("key");
	    			String key = expr.evaluate(map);
	    			expr = xpath.compile("value");
	    			String mapValue = expr.evaluate(map);
	    			mapHash.put(key, mapValue);
	    		}
	    		
	    		// debug
	    		/*
	    	    System.out.println("Xpath discovered node:" + value);
	    	    System.out.println("  Name Attribute:" + name);
	    	    System.out.println("  Type Attribute:" + type);
	    	    Set set = mapHash.entrySet();
	    	    Iterator it = set.iterator();
	    	    while(it.hasNext()) {
	    	    	Map.Entry me = (Map.Entry) it.next();
	    	    	System.out.println("        Map:(" + me.getKey() + "," + me.getValue() + ")");
	    	    }
	    	    */
	    	    // end debug

	    	    // Ignore if the name, type or value are missing
	    	    if(name == null || name.trim().isEmpty()
	    	    		|| type == null || type.trim().isEmpty() 
	    	    		|| value == null || value.trim().isEmpty()) {
    	    		System.out.println("Warning: empty name, type or value - ignoring");
    	    		continue;
	    	    }
	    	    
	    	    // Get the parameter values to add to the url as parameters
	    	    String parValue = null;	    	    
	    	    if(type.equals("text")) {

	    	    	parValue = value;
	    	    	
	    	    } else if(type.equals("xpath")) {	// retrieve result from survey upload
	    	    		
	    	    	expr = xpath.compile(value);
	    	    	parValue = expr.evaluate(surveyDocument);
	    	    	System.out.println("      xPathResult:" + parValue);	//debug
	    	    	
	    	    } else if(type.equals("other")){
	    	    	
	    	    	if(value.equals("remoteUser")) {
	    	    		parValue = remoteUser;
	    	    	} else {
	    	    		System.out.println("Warning: unknown other value:" + value);
	    	    		continue;
	    	    	}
	    	    
	    	    } else {	
	    	    	System.out.println("Warning: unknown type:" + type);
	    	    }
	    	    
	    		// Ushahidi parameters take only part of the full date, and part of the location
	    		if(name.equals("incident_date")) {	// Only the year, day month part of the date is used
	    			parValue = UtilityMethodsEmail.getPartDate(parValue, "MM/dd/yyyy");
	    		} else if(name.equals("incident_hour")) {	// Only the hour part of the date is used
	    			parValue = UtilityMethodsEmail.getPartDate(parValue, "hh");
	    		} else if(name.equals("incident_minute")) {	// Only the minute part of the date is used
	    			parValue = UtilityMethodsEmail.getPartDate(parValue, "mm");
	    		} else if(name.equals("incident_ampm")) {	// Only the am/pm part of the date is used
	    			parValue = UtilityMethodsEmail.getPartDate(parValue, "aa").toLowerCase();
	    		} else if(name.equals("latitude")) {	// Only the lat part of the location is used
	    			parValue = UtilityMethodsEmail.getPartLocation(parValue, "lat");
	    		} else if(name.equals("longitude")) {	// Only the lon part of the location is used
	    			parValue = UtilityMethodsEmail.getPartLocation(parValue, "lon");
	    		}
	    		
	    		// Set some default values if the results data is deficient
	    		if(name.equals("incident_category")) {
	    			if(parValue == null || parValue.trim().length() == 0) {
	    				parValue = "1";	// Default to a category of 1 so the report gets loaded
	    			}
	    		} else if(name.equals("person_first")) {
	    			if(parValue == null) {
	    				parValue = "unknown";
	    			}
	    		} else if(name.equals("person_first")) {
	    			if(parValue == null) {
	    				parValue = "unknown";
	    			}
	    		} else if(name.equals("person_last")) {
	    			if(parValue == null) {
	    				parValue = "unknown";
	    			}
	    		}
	    		
	    		// Do any mapping that may have been asked for
	    		String mapValue = mapHash.get(parValue);
	    		if(mapValue != null) {
	    			parValue = mapValue;
	    		}

	    		// Add the parameter to the URL
	    		System.out.println("      Final attribute value:" + parValue);	// debug
	    		entity.addPart(name, new StringBody(parValue));
	    	}
	    	
	    	// Add all the images
			for(String image : images) {
				if(image != null && image.trim().length() > 0) {
					String imagePath = "/ebs1/attachments/" + instance.getTemplateName() + "/" + remoteUser + "_" + image;
					System.out.println("        Image:" + imagePath);
					
					entity.addPart("incident_photo[]", new FileBody(new File(imagePath)));
				}
			}
			for(String video : videos) {
				if(video != null && video.trim().length() > 0) {
					String videoPath = "http://" + server + "/attachments/" + instance.getTemplateName() + "/" + remoteUser + "_" + video;
					System.out.println("        Image:" + videoPath);
					
					entity.addPart("incident_video[]",new StringBody(videoPath));
				}
			}
			
			// We want the response in XML
			entity.addPart("resp", new StringBody("XML"));

			// Make the request
			HttpPost postRequest = new HttpPost(url);
			postRequest.setEntity(entity);	
			HttpResponse response = httpClient.execute(postRequest);
			 
			if (response.getStatusLine().getStatusCode() != 200 && response.getStatusLine().getStatusCode() != 201) {
				throw new RuntimeException("Failed : HTTP error code : "
					+ response.getStatusLine().getStatusCode());
			}

			DocumentBuilder dbResp = dbf.newDocumentBuilder();
			Document xmlResp = dbResp.parse(response.getEntity().getContent());
			String serverResponse = xmlResp.getElementsByTagName("success").item(0).getTextContent();
			String errorCode = xmlResp.getElementsByTagName("code").item(0).getTextContent();
			String errorMessage = xmlResp.getElementsByTagName("message").item(0).getTextContent();
			
			System.out.println("        Server Response:" + serverResponse);
			if(serverResponse.equals("true")) {
				se.setStatus("success");
			} else {
				se.setStatus("error");				
			}
			se.setReason(errorCode + ":" + errorMessage);
		} catch (UnsupportedEncodingException e) {
			se.setStatus("error");
			String msg = "UnsupportedCodingException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(ClientProtocolException e) {
			se.setStatus("error");
			String msg = "ClientProtocolException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(IOException e) {
			se.setStatus("error");
			String msg = "IOException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(SAXException e) {
			se.setStatus("error");
			String msg = "SAXException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(ParserConfigurationException e) {
			se.setStatus("error");
			String msg = "ParserConfigurationException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(IllegalArgumentException e) {
			se.setStatus("error");			
			String msg = "IllegalArgumentException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(XPathExpressionException e) {
			se.setStatus("error");			
			String msg = "XPathExpressionException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(ParseException e) {
			se.setStatus("error");			
			String msg = "Error parsing dates:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} finally {	
			httpClient.getConnectionManager().shutdown();
		}
			
		return;
	}

}
