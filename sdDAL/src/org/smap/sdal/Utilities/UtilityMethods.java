package org.smap.sdal.Utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

public class UtilityMethods {
	
	private static String [] reservedSQL = new String [] {
		"all",
		"analyse",
		"analyze",
		"and",
		"any",
		"array",
		"as",
		"asc",
		"assignment",
		"asymmetric",
		"authorization",
		"between",
		"binary",
		"both",
		"case",
		"cast",
		"check",
		"collate",
		"column",
		"constraint",
		"create",
		"cross",
		"current_date",
		"current_role",
		"current_time",
		"current_timestamp",
		"current_user",
		"default",
		"deferrable",
		"desc",
		"distinct",
		"do",
		"else",
		"end",
		"except",
		"false",
		"for",
		"foreign",
		"freeze",
		"from",
		"full",
		"grant",
		"group",
		"having",
		"ilike",
		"in",
		"initially",
		"inner",
		"intersect",
		"into",
		"is",
		"isnull",
		"join",
		"leading",
		"left",
		"like",
		"limit",
		"localtime",
		"localtimestamp",
		"natural",
		"new",
		"not",
		"notnull",
		"null",
		"off",
		"offset",
		"old",
		"on",
		"only",
		"or",
		"order",
		"outer",
		"overlaps",
		"placing",
		"primary",
		"references",
		"right",
		"select",
		"session_user",
		"similar",
		"some",
		"symmetric",
		"table",
		"then",
		"to",
		"trailing",
		"true",
		"union",
		"unique",
		"user",
		"using",
		"verbose",
		"when",
		"where"
	};


    
	/*
	 * Remove any characters from the name that will prevent it being used as a database column name
	 */
	static public String cleanName(String in) {
		
		String out = null;
		
		if(in != null) {
			out = in.trim().toLowerCase();
			//String lowerCaseOut = out.toLowerCase();	// Preserve case as this is important for odkCollect
	
			out = out.replace(" ", "");	// Remove spaces
			out = out.replaceAll("[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]", "x");	// Remove special characters ;
		
			/*
			 * Rename legacy fields that are the same as postgres / sql reserved words
			 */
			for(int i = 0; i < reservedSQL.length; i++) {
				if(out.equals(reservedSQL[i])) {
					out = "__" + out;
					break;
				}
			}
		}

		
		return out;
	}
	
	
	

	/*
	 * Mark a record and all its children as either bad or good
	 */
	static public void markRecord(Connection cRel, Connection cSD, String tName, boolean value, String reason, int key, int sId, int fId) throws Exception {
		// TODO add optimistic locking		
		String sql = "update " + tName + " set _bad = ?, _bad_reason = ? " + 
				" where prikey = ?;";
			
		System.out.println(sql + " : " + value + " : " + reason + " : " + key);
		PreparedStatement pstmt = cRel.prepareStatement(sql);
		pstmt.setBoolean(1, value);
		pstmt.setString(2, reason);
		pstmt.setInt(3, key);
		int count = pstmt.executeUpdate();
		
		if(count != 1) {
			throw new Exception("Upate count not equal to 1");
		}
		
		// Get the child tables
		sql = "SELECT DISTINCT f.table_name, f_id FROM form f " +
				" where f.s_id = ? " + 
				" and f.parentform = ?;";
		System.out.println(sql + " : " + sId + " : " + fId);
		pstmt = cSD.prepareStatement(sql);
		pstmt.setInt(1, sId);
		pstmt.setInt(2, fId);
		
		ResultSet tableSet = pstmt.executeQuery();
		while(tableSet.next()) {
			String childTable = tableSet.getString(1);
			int childFormId = tableSet.getInt(2);
			
			// Get the child records to be updated
			sql = "select prikey from " + childTable + 
					" where parkey = ?;";
			PreparedStatement pstmt2 = cRel.prepareStatement(sql);	
			pstmt2.setInt(1, key);
			System.out.println(sql + " : " + key);
			
			ResultSet childRecs = pstmt2.executeQuery();
			while(childRecs.next()) {
				int childKey = childRecs.getInt(1);
				markRecord(cRel, cSD, childTable, value, reason, childKey, sId, childFormId);
			}
		}
		
		
	}
	
	// Check to see if email is enabled on this server
	static public boolean hasEmail(HttpServletRequest request) throws Exception {
		System.out.println("checking");
		boolean email = false;
		String has_email = request.getServletContext().getInitParameter("au.com.smap.smtp_on");
		System.out.println("checking2");
		if(has_email != null && has_email.equals("true")) {
			email = true;
		}
		return email;
	}
	
	// Get applicable user idents from an email
	static public ArrayList<String> getIdentsFromEmail(
			Connection connectionSD, 
			PreparedStatement pstmt, 
			String email) throws SQLException {
		
		ArrayList<String> idents = new ArrayList<String> ();
		
		/*
		 * Get the table name and column name containing the text data
		 */
		String sql = "select ident from users where email = ?";

		System.out.println(sql + " : "  + email);
		pstmt = connectionSD.prepareStatement(sql);	
		pstmt.setString(1, email);
		ResultSet rs = pstmt.executeQuery();
		while (rs.next()) {
			idents.add(rs.getString(1));
		}
		
		return idents;
	}
	
	// Get applicable user idents from an email
	static public String getAdminEmail(
			Connection sd, 
			PreparedStatement pstmt, 
			String user) throws SQLException {
		
		String adminEmail = "<admin email not set>";
		
		String sqlGetAdminEmail = "select o.admin_email " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		pstmt = sd.prepareStatement(sqlGetAdminEmail);
		pstmt.setString(1, user);
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			String email = rs.getString(1);
			if(email != null && email.trim().length() > 0) {
				adminEmail = rs.getString(1);
			}
		}
		return adminEmail;
	}
	
	// Set a one time password
	static public String setOnetimePassword(
			Connection connectionSD, 
			PreparedStatement pstmt, 
			String email, 
			String interval) throws SQLException {
		
		String uuid = String.valueOf(UUID.randomUUID());
		interval = interval.replace("'", "''");	// Escape apostrophes
		
		/*
		 * Update the users table by adding the UUID and expiry time
		 */
		String sql = "update users set" +
				" one_time_password = ?," +
				" one_time_password_expiry = timestamp 'now' + interval '" + interval + "' " +		
				" where email = ?";

		System.out.println(sql + " : " + uuid + " : "  + email);
		pstmt = connectionSD.prepareStatement(sql);	
		pstmt.setString(1, uuid);
		pstmt.setString(2, email);
		int count = pstmt.executeUpdate();
		
		if(count > 0) {
			return uuid;
		} else {
			return null;
		}
	}
	
	// Send an email
	static public void sendEmail(HttpServletRequest request, 
			String email, 
			String uuid, 
			String type, 
			String subject,
			String adminName,
			String interval,
			ArrayList<String> idents,
			String docURL,
			String adminEmail) throws Exception  {
		
		String smtp_host = request.getServletContext().getInitParameter("au.com.smap.smtp_host");
		String from = request.getServletContext().getInitParameter("au.com.smap.password_reset_from");
		RecipientType rt = null;
		
		try {
			Properties props = System.getProperties();
			props.put("mail.smtp.host", smtp_host);	
			Session session = Session.getInstance(props, null);
			session.setDebug(true);
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(from));
			
			if(type.equals("notify")) {
				rt = Message.RecipientType.BCC;
			} else {
				rt = Message.RecipientType.TO;
			}
		    msg.setRecipients(rt,	InternetAddress.parse(email, false));
		    msg.setSubject(subject);
		    
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
		    
		    StringBuffer txtMessage = new StringBuffer("");
		    if(type.equals("reset")) {
			    txtMessage.append("Goto https://");
			    txtMessage.append(request.getServerName());
			    txtMessage.append("/resetPassword.html?token=");
			    txtMessage.append(uuid);
			    txtMessage.append(" to reset your password.\n\n");
			    txtMessage.append("Your user name is: ");
			    txtMessage.append(identString.toString());
			    txtMessage.append("\n\n");
			    txtMessage.append(" The link is valid for ");
			    txtMessage.append(interval);
			    txtMessage.append("\n");
			    txtMessage.append(" Do not reply to this email address it is not monitored. If you don't think you should be receiving these then send an email to ");	
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");
			
		    } else if(type.equals("newuser")) {
		    	
			    txtMessage.append(adminName);
			    txtMessage.append(" has given you access to a Smap server with address http://");
			    txtMessage.append(request.getServerName());
			    txtMessage.append("\n");
			    txtMessage.append("You will need to specify your password before you can log on.  To do this click on the following link https://");
			    txtMessage.append(request.getServerName());
			    txtMessage.append("/resetPassword.html?token=");
			    txtMessage.append(uuid);
			    txtMessage.append("\n\n");
			    txtMessage.append("Your user name is: ");
			    txtMessage.append(identString.toString());
			    txtMessage.append("\n\n");
			    txtMessage.append(" The link is valid for ");
			    txtMessage.append(interval);
			    txtMessage.append("\n");
			    txtMessage.append(" Do not reply to this email address it is not monitored. If you don't think you should be receiving these then send an email to ");	
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");			

		    } else if(type.equals("notify")) {
			    txtMessage.append("This is a notification from Smap server http://");
			    txtMessage.append(request.getServerName());
			    txtMessage.append("\n");
			    txtMessage.append(request.getServerName());
			    txtMessage.append(docURL);
			    txtMessage.append("\n");
			    txtMessage.append(" Do not reply to this email address it is not monitored. If you don't think you should be receiving these then send an email to ");	
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");

		    }
		    
		    msg.setText(txtMessage.toString());
		    msg.setHeader("X-Mailer", "msgsend");
		    Transport.send(msg);
		} catch(MessagingException me) {
			System.out.println("Messaging Exception");
			throw new Exception(me.getMessage());
		}
	}
	
	
}
