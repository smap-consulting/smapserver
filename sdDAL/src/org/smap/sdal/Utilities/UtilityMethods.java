package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Survey;


public class UtilityMethods {
	
	private static Logger log =
			 Logger.getLogger(UtilityMethods.class.getName());
	
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
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		
		try {
			
			pstmt = cRel.prepareStatement(sql);
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
			log.info(sql + " : " + sId + " : " + fId);
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
				pstmt2 = cRel.prepareStatement(sql);	
				pstmt2.setInt(1, key);
				log.info(pstmt2.toString());
				
				ResultSet childRecs = pstmt2.executeQuery();
				while(childRecs.next()) {
					int childKey = childRecs.getInt(1);
					markRecord(cRel, cSD, childTable, value, reason, childKey, sId, childFormId);
				}
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) try {pstmt.close();} catch(Exception e) {};
			if (pstmt2 != null) try {pstmt2.close();} catch(Exception e) {};
		}
		
		
	}
	
	/*
	 * Get applicable user idents from an email
	 */
	static public ArrayList<String> getIdentsFromEmail(
			Connection connectionSD, 
			PreparedStatement pstmt, 
			String email) throws SQLException {
		
		ArrayList<String> idents = new ArrayList<String> ();
		
		/*
		 * Get the table name and column name containing the text data
		 */
		String sql = "select ident from users where email = ?";

		pstmt = connectionSD.prepareStatement(sql);	
		pstmt.setString(1, email);
		log.info(pstmt.toString());
		ResultSet rs = pstmt.executeQuery();
		while (rs.next()) {
			idents.add(rs.getString(1));
		}
		
		return idents;
	}
	
	/*
	 * Get the administrator email for the organisation that the user belongs to
	 */
	static public String getAdminEmail(
			Connection sd, 
			String user) throws SQLException {
		
		String adminEmail = "<admin email not set>";
		
		String sqlGetAdminEmail = "select o.admin_email " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sqlGetAdminEmail);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String email = rs.getString(1);
				if(email != null && email.trim().length() > 0) {
					adminEmail = rs.getString(1);
				}
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (Exception e) {}
		}
		return adminEmail;
	}
	

	
	/*
	 * Get the smtp host for the organisation that the user belongs to
	 */
	static public String getSmtpHost(
			Connection sd, 
			String email,
			String user) throws SQLException {
		
		String smtpHost = null;
		
		String sqlIdent = "select o.smtp_host " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		
		String sqlEmail = "select o.smtp_host " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		
		String sqlServer = "select smtp_host " +
				" from server ";
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {

			if(user != null) {
				pstmt = sd.prepareStatement(sqlIdent);
				pstmt.setString(1, user);
				System.out.println("SQL:" + pstmt.toString());
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					if(host != null) {
						host = host.trim();
						if(host.length() > 0) {
							smtpHost = host;
						}
					}		
				}
			} else if(email != null) {
				/*
				 * This will be for a forgotton password
				 * Use the email server for the matching email
				 */
				pstmt = sd.prepareStatement(sqlEmail);
				pstmt.setString(1, email);
				System.out.println("SQL:" + pstmt.toString());
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					if(host != null) {
						host = host.trim();
						if(host.length() > 0) {
							smtpHost = host;
						}
					}		
				}
			} else {
			
			}
			System.out.println("Using organisation email: " + smtpHost);
		
			/*
			 * If the smtp_host was not set at the organisation level try the server level defaults
			 */
			if(smtpHost == null) {

				try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sqlServer);
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					if(host != null && host.trim().length() > 0) {
						smtpHost = rs.getString(1);
					}
				}
				System.out.println("Using server email: " + smtpHost);
			}
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		return smtpHost;
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

		log.info(sql + " : " + uuid + " : "  + email);
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
	static public void sendEmail( 
			String email, 
			String uuid, 
			String type, 
			String subject,
			String sender,
			String adminName,
			String interval,
			ArrayList<String> idents,
			String docURL,
			String adminEmail,
			String smtp_host,
			String serverName) throws Exception  {
		
		if(smtp_host == null) {
			throw new Exception("smtp_host not available");
		}
		
		RecipientType rt = null;
		try {
			Properties props = System.getProperties();
			props.put("mail.smtp.host", smtp_host);	
			Session session = Session.getInstance(props, null);
			session.setDebug(true);
			Message msg = new MimeMessage(session);
			
			if(type.equals("notify")) {
				rt = Message.RecipientType.BCC;
			} else {
				rt = Message.RecipientType.TO;
			}
		    msg.setRecipients(rt,	InternetAddress.parse(email, false));
		    msg.setSubject(subject);
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
		    
		    StringBuffer txtMessage = new StringBuffer("");
		    if(type.equals("reset")) {
			    txtMessage.append("Goto https://");
			    txtMessage.append(serverName);
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
			    txtMessage.append(serverName);
			    txtMessage.append("\n");
			    txtMessage.append("You will need to specify your password before you can log on.  To do this click on the following link https://");
			    txtMessage.append(serverName);
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
			    txtMessage.append(serverName);
			    txtMessage.append("\n");
			    txtMessage.append(serverName);
			    txtMessage.append(docURL);
			    txtMessage.append("\n");
			    txtMessage.append(" Do not reply to this email address it is not monitored. If you don't think you should be receiving these then send an email to ");	
			    txtMessage.append(adminEmail);
			    txtMessage.append(".");

		    }
		    
		    //String from = type + "@" + serverName;
			//msg.setFrom(new InternetAddress(from));
		    msg.setText(txtMessage.toString());
		    msg.setHeader("X-Mailer", "msgsend");
		    log.info("Sending email from: " + sender);
		    Transport.send(msg);
		} catch(MessagingException me) {
			log.info("Messaging Exception");
			throw new Exception(me.getMessage());
		}
	}
	
	/*
	 * Get a substring of a date that is in ISO 8601 format
	 */
	public static String getPartDate(String fullDate, String format) throws ParseException {
		
		String partDate = null;
		
		// parse the input date
		SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");  // ISO8601 date formats - add timezone after upgrade of java rosa libraries
		SimpleDateFormat outFormat = new SimpleDateFormat(format);
		Date theDate = null;

		theDate = inFormat.parse(fullDate);
		partDate = outFormat.format(theDate).toString();
		
		return partDate;
}
	
	public static String getPartLocation(String location, String dimension) {
		
		String partLocation= "0.0";
		
		String vals[] = location.split(" ");
		if(vals.length > 2) {
			if(dimension.equals("lat")) {
				partLocation = vals[0];
			} else if(dimension.equals("lon")) {
				partLocation = vals[1];
			}
		}	
		
		return partLocation;
	}
	
	/*
	 * Get the content type from the filename
	 */
	public static String getContentType(String filename) {
		
		String ct = null;
		String extension = "";
		int idx = filename.lastIndexOf('.');
		if(idx > 0) {
			extension = filename.substring(idx+1).toLowerCase();
		}
		
	      if (extension.equals("xml")) {
          	ct = "text/xml";
          } else if (extension.equals("jpg") || extension.equals("jpeg") || extension.equals("jpe")) {
          	ct = "image/jpeg";
          } else if (extension.equals("png")) {
            	ct = "image/png";
          } else if (extension.equals("gif")) {
          	ct = "image/gif";
          } else if (extension.equals("3gp")) {
          	ct = "video/3gp";
          } else if (extension.equals("3ga")) {
            ct = "audio/3ga";
          } else if (extension.equals("mp2") || extension.equals("mp3") || extension.equals("mpga")) {
            ct = "audio/mpeg";
          } else if (extension.equals("mpeg") || extension.equals("mpg") || extension.equals("mpe")) {
            ct = "video/mpeg";
          } else if (extension.equals("qt") || extension.equals("mov")) {
            ct = "video/quicktime";
          } else if (extension.equals("mp4") || extension.equals("m4p")) {
          	ct = "video/mp4";
          } else if (extension.equals("avi")) {
            ct = "video/x-msvideo";
          } else if (extension.equals("movie")) {
            ct = "video/x-sgi-movie";
          } else if (extension.equals("m4a")) {
          	ct = "audio/m4a";
          } else if (extension.equals("csv")) {
          	ct = "text/csv";
          } else if (extension.equals("amr")) {
          	ct = "audio/amr";
          } else if (extension.equals("xls")) {
          	ct = "application/vnd.ms-excel";
          }  else {
          	ct = "application/octet-stream";
          	log.info("	Info: unrecognised content type for extension " + extension);           
          }
		
		return ct;
	}
	
	/*
	 * Create a thumbnail for a file
	 */
	public static void createThumbnail(String name, String path, File file) {
		
		String contentType = getContentType(name);
		String source = path + "/" + name;
		String dest = path + "/thumbs/" + name;
		
		int idx = dest.lastIndexOf('.');
		String destRoot = dest;
		if(idx > 0) {
			destRoot = dest.substring(0, idx + 1);
		}
		
		String cmd = null;
		log.info("Creating thumbnail for content type: " + contentType);
		if(contentType.startsWith("image")) {
			cmd = "/usr/bin/convert -thumbnail 100x100 \"" + source + "\" \"" + dest + "\"";
		} else if(contentType.startsWith("video")) {
			cmd = "/usr/bin/ffmpeg -i \"" + source + "\" -vf scale=-1:100 -vframes 1 \"" + destRoot + "jpg\"";
		} 
		
		log.info("Exec: " + cmd);
		
		if(cmd != null) {
			try {
	
				Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", cmd});
	    		
	    		int code = proc.waitFor();
	    		log.info("Attachment processing finished with status:" + code);
	    		if(code != 0) {
	    			log.info("Error: Attachment processing failed");
	    			InputStream stderr = proc.getErrorStream();
	    	        InputStreamReader isr = new InputStreamReader(stderr);
	    	        BufferedReader br = new BufferedReader(isr);
	    	        String line = null;
	    	        while ( (line = br.readLine()) != null) {
	    	        	log.info("** " + line);
	    	        }
	    		}
	    		
			} catch (Exception e) {
				e.printStackTrace();
	    	}
		}
	}
	
	/*
	 * Get labels for an option or question
	 */
	public static void getLabels(Connection connectionSD,
			Survey s, 
			String text_id, 
			String hint_id, 
			ArrayList<Label> labels,
			String basePath,
			int oId) throws Exception {
		
		PreparedStatement pstmt = null;
		
		try {
			
			String sql = "select t.type, t.value from translation t where t.s_id = ? and t.language = ? and t.text_id = ?";
			pstmt = connectionSD.prepareStatement(sql);
			ManifestValue manifest = new ManifestValue();
			
			for(int i = 0; i < s.languages.size(); i++) {
	
				Label l = new Label();
				
				// Get label and media
				pstmt.setInt(1, s.id);
				pstmt.setString(2, s.languages.get(i));
				pstmt.setString(3, text_id);
				
				ResultSet resultSet = pstmt.executeQuery();		
				while(resultSet.next()) {
	
					String t = resultSet.getString(1).trim();
					String v = resultSet.getString(2);
					
					if(t.equals("none")) {
						l.text = v;
					} else if(basePath != null && oId > 0) {
						getFileUrl(manifest, s.ident, v, basePath, oId);
						System.out.println("Url: " + manifest.url + " : " + v);
						if(t.equals("image")) {
							l.image = v;
							l.imageUrl = manifest.url;
							l.imageThumb = manifest.thumbsUrl;
						} else if(t.equals("audio")) {
							l.audio = v;
							l.audioUrl = manifest.url;
							l.audioThumb = null;
						} else if(t.equals("video")) {
							l.video = v;
							l.videoUrl = manifest.url;
							l.videoThumb = manifest.thumbsUrl;
						}
					} 
	
				}
				
				// Get hint
				if(hint_id != null) {
					pstmt.setInt(1, s.id);
					pstmt.setString(2, s.languages.get(i));
					pstmt.setString(3, hint_id);
					
					resultSet = pstmt.executeQuery();
					
					if(resultSet.next()) {
						String t = resultSet.getString(1).trim();
						String v = resultSet.getString(2);
						
						if(t.equals("none")) {
							l.hint = v;
						} else {
							log.info("Error: Invalid type for hint: " + t);
						}
					}
				}
				
				labels.add(l);		
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
		}
	}
		
	/*
	 * Get the partial (URL) of the file and its file path or null if the file does not exist
	 */
	public static void getFileUrl(ManifestValue manifest, String sIdent, String fileName, String basePath, int oId) {
		
		String url = null;
		String thumbsUrl = null;
		File file = null;
		File thumb = null;
		
		// First try the survey level
		url = "/media/" + sIdent + "/" + fileName;	
		log.info("Info: Getting file url for file: " + basePath + url);
		thumbsUrl = "/media/" + sIdent + "/thumbs/" + fileName;	
		file = new File(basePath + url);
		if(file.exists()) {
			manifest.url = url;
			manifest.filePath = basePath + url;
			
			thumb = new File(basePath + thumbsUrl);
			if(thumb.exists()) {
				manifest.thumbsUrl = thumbsUrl;
			}
		} else {
		
			// Second try the organisation level
			url = "/media/organisation/" + oId + "/" + fileName;
			thumbsUrl = "/media/organisation/" + oId + "/thumbs/" + fileName;
			file = new File(basePath + url);
			if(file.exists()) {
				manifest.url = url;
				manifest.filePath = basePath + url;
				
				thumb = new File(basePath + thumbsUrl);
				if(thumb.exists()) {
					manifest.thumbsUrl = thumbsUrl;
				}
			}		
		}
	}
	
}
