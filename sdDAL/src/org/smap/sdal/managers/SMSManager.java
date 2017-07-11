package org.smap.sdal.managers;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.smap.sdal.model.EmailServer;

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
 * Manage the sending of emails
 */
public class SMSManager {
	
	private static Logger log =
			 Logger.getLogger(SMSManager.class.getName());
	

	
	// Send an email
	public String sendSMSUrl( 
			String url, 
			String number, 
			String content) throws Exception  {
		
		String responseBody = null;
		
		if(!isValidPhoneNumber(number)) {
			throw new Exception("Invalid phone number: " + number);
		}
		
		CloseableHttpClient httpclient = null;
		try {
			httpclient = HttpClients.createDefault();
				
			url = url.replaceAll("\\$\\{phone\\}", URLEncoder.encode(number, "UTF-8"));
			url = url.replaceAll("\\$\\{msg\\}", URLEncoder.encode(content, "UTF-8"));
			
			HttpGet httpget = new HttpGet(url);
			log.info("Executing SMS request " + httpget.getRequestLine());
			
			ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

	                @Override
	                public String handleResponse(
	                        final HttpResponse response) throws ClientProtocolException, IOException {
	                    int status = response.getStatusLine().getStatusCode();
	                    if (status >= 200 && status < 300) {
	                        HttpEntity entity = response.getEntity();
	                        return entity != null ? EntityUtils.toString(entity) : null;
	                    } else {
	                        throw new ClientProtocolException("Unexpected response status: " + status);
	                    }
	                }

	            };
	            
	            responseBody = httpclient.execute(httpget, responseHandler);
	            log.info("Sent SMS: " + responseBody);
			 
		} finally {
			httpclient.close();
		}
		
		return responseBody;
	}
	
	/*
	 * Validate an email
	 */
	private boolean isValidPhoneNumber(String number) {
		boolean isValid = true;
		if(number == null) {
			isValid = false;
		} else if(number.trim().length() == 0) {
			isValid = false;
		}
		return isValid;
	}
}


