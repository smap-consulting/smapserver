package org.smap.sdal.managers;

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
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class EmailManager {
	
	private static Logger log =
			 Logger.getLogger(EmailManager.class.getName());
	
	/*
	 * Add an authenticator class
	 */
	private class Authenticator extends javax.mail.Authenticator {
		private PasswordAuthentication authentication;

		public Authenticator(String username, String password) {
			authentication = new PasswordAuthentication(username, password);
		}

		protected PasswordAuthentication getPasswordAuthentication() {
			return authentication;
		}
	}
	
	// Send an email
	public void sendEmail( 
			String email, 
			String uuid, 
			String type, 
			String subject,
			String content,
			String sender,
			String adminName,
			String interval,
			ArrayList<String> idents,
			String docURL,
			String filePath,	// The next two parameters are for an attachment TODO make an array
			String filename,
			String adminEmail,
			EmailServer emailServer,
			String scheme,
			String serverName,
			ResourceBundle localisation) throws Exception  {
		
		if(emailServer.smtpHost == null) {
			throw new Exception("Cannot send email, smtp_host not available");
		}
		
		RecipientType rt = null;
		try {
			Properties props = System.getProperties();
			props.put("mail.smtp.host", emailServer.smtpHost);	
			
			Authenticator authenticator = null;
			
			// Create an authenticator if the user name and password is available
			if(emailServer.emailUser != null && emailServer.emailPassword != null 
					&& emailServer.emailUser.trim().length() > 0 
					&& emailServer.emailPassword.trim().length() > 0) {
				String authUser = emailServer.emailUser + "@" + emailServer.emailDomain;
				authenticator = new Authenticator(authUser, emailServer.emailPassword);
				props.setProperty("mail.smtp.submitter", authenticator.getPasswordAuthentication().getUserName());
				props.setProperty("mail.smtp.auth", "true");
				props.setProperty("mail.smtp.starttls.enable", "true");
				if(emailServer.emailPort > 0) {
					props.setProperty("mail.smtp.port", String.valueOf(emailServer.emailPort));
				} else {
					props.setProperty("mail.smtp.port", "587");	
				}
				
				sender = emailServer.emailUser;
				
				log.info("Trying to send email with authentication");
			} else {
				if(emailServer.emailPort > 0) {
					props.setProperty("mail.smtp.port", String.valueOf(emailServer.emailPort));
				} else {
					// Use default port (25?)
				}
				log.info("No authentication");
			}
		
			props.setProperty("mail.smtp.connectiontimeout", "60000");
			props.setProperty("mail.smtp.timeout", "60000");
			props.setProperty("mail.smtp.writetimeout", "60000");
			Session session = Session.getInstance(props, authenticator);
			Message msg = new MimeMessage(session);
			if(type.equals("notify")) {
				rt = Message.RecipientType.BCC;
			} else {
				rt = Message.RecipientType.TO;
			}
			
			log.info("Sending to email addresses: " + email);
			InternetAddress[] emailArray = InternetAddress.parse(email);
			log.info("Number of email addresses: " + emailArray.length);
		    msg.setRecipients(rt,	emailArray);
		    msg.setSubject(subject);
		    
		    sender = sender + "@" + emailServer.emailDomain;
		 
		    log.info("Sending email from: " + sender);
		    msg.setFrom(InternetAddress.parse(sender, false)[0]);
	    
		    StringBuffer identString = new StringBuffer();
	    	int count = 0;
	    	if(idents != null) {
		    	for(String ident : idents) {
		    		if(count++ > 0) {
		    			identString.append(" or ");
		    		} 
		    		identString.append(ident);
		    	}
	    	}
		    
	    	log.info("Email type: " + type + " content: " + content);
		    StringBuffer txtMessage = new StringBuffer("");
		    if(content != null && content.trim().length() > 0) {
		    	txtMessage.append(content);			// User has specified email content
			    txtMessage.append("\n\n");
			    
			    // Add a link to the report if docURL is not null
			    if(docURL != null) {
			    	txtMessage.append(scheme + "://");
				    txtMessage.append(serverName);
				    txtMessage.append(docURL);
			    }
		    } else if(type.equals("reset")) {
		    	txtMessage.append(localisation.getString("c_goto"));
			    txtMessage.append(" " + scheme + "://");
			    txtMessage.append(serverName);
			    txtMessage.append("/resetPassword.html?token=");
			    txtMessage.append(uuid);
			    txtMessage.append(" ");
			    txtMessage.append(localisation.getString("email_rp"));
			    txtMessage.append("\n\n");
			    txtMessage.append(localisation.getString("email_un"));
			    txtMessage.append(": ");
			    txtMessage.append(identString.toString());
			    txtMessage.append("\n\n ");
			    txtMessage.append(localisation.getString("email_vf"));
			    txtMessage.append(" ");
			    txtMessage.append(interval);
			    txtMessage.append("\n ");
			    //txtMessage.append("Do not reply to this email address it is not monitored. If you don't think you should be receiving these then send an email to");	
			    txtMessage.append(localisation.getString("email_dnr"));
			    txtMessage.append(" ");
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");
			
		    } else if(type.equals("newuser")) {
		    	
			    txtMessage.append(adminName);
			    txtMessage.append(" ");
			    txtMessage.append(localisation.getString("email_hga"));
			    txtMessage.append(" " + scheme + "://");
			    txtMessage.append(serverName);
			    txtMessage.append("\n");
			    txtMessage.append(localisation.getString("email_sp"));
			    txtMessage.append(" " + scheme + "://");
			    txtMessage.append(serverName);
			    txtMessage.append("/resetPassword.html?token=");
			    txtMessage.append(uuid);
			    txtMessage.append("\n\n");
			    txtMessage.append(localisation.getString("email_un"));
			    txtMessage.append(": ");
			    txtMessage.append(identString.toString());
			    txtMessage.append("\n\n");
			    txtMessage.append(localisation.getString("email_vf"));
			    txtMessage.append(" ");
			    txtMessage.append(interval);
			    txtMessage.append("\n");
			    txtMessage.append(localisation.getString("email_dnr"));
			    txtMessage.append(" ");
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");			

		    } else if(type.equals("notify")) {
		    	txtMessage.append(localisation.getString("email_ian"));
		    	txtMessage.append(" " + scheme + "://");
			    txtMessage.append(serverName);
			    txtMessage.append(". ");
			    
			    txtMessage.append(localisation.getString("email_dnr"));
			    txtMessage.append(" ");
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");	
			    txtMessage.append("\n\n");
			    if(docURL != null) {
			    	txtMessage.append(scheme + "://");
				    txtMessage.append(serverName);
				    txtMessage.append(docURL);
			    }

		    }
		    
		    BodyPart messageBodyPart = new MimeBodyPart();
		    messageBodyPart.setText(txtMessage.toString());
		    Multipart multipart = new MimeMultipart();
		    multipart.addBodyPart(messageBodyPart);
		    
		    // Add file attachments if they exist
		    if(filePath != null) {			 
			    messageBodyPart = new MimeBodyPart();
			    DataSource source = new FileDataSource(filePath);
			    messageBodyPart.setDataHandler(new DataHandler(source));
		        messageBodyPart.setFileName(filename);
		        multipart.addBodyPart(messageBodyPart);
		    }
	        

	        msg.setContent(multipart);
		    
		    msg.setHeader("X-Mailer", "msgsend");
		    log.info("Sending email from: " + sender);
		    Transport.send(msg);
		    
		} catch(MessagingException me) {
			log.log(Level.SEVERE, "Messaging Exception");
			throw new Exception(localisation.getString("email_cs") + "  " + me.getMessage());
		}
		
		
	}
}


