package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.model.EmailServer;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Survey;


public class UtilityMethodsEmail {
	
	private static Logger log =
			 Logger.getLogger(UtilityMethodsEmail.class.getName());

	/*
	 * Mark a record and all its children as either bad or good
	 */
	static public void markRecord(Connection cRel, 
			Connection cSD, 
			String tName, 
			boolean value, 
			String reason, 
			int key, 
			int sId, 
			int fId,
			boolean modified,
			boolean isChild) throws Exception {
	
		String sql = "update " + tName + " set _bad = ?, _bad_reason = ?, _modified = ? " + 
				" where prikey = ? and _modified = 'false';";
		String sqlChild = "update " + tName + " set _bad = ?, _bad_reason = ? " + 
				" where prikey = ?;";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		
		try {
			
			if(isChild) {
				pstmt = cRel.prepareStatement(sqlChild);
			} else {
				pstmt = cRel.prepareStatement(sql);
			}
			pstmt.setBoolean(1, value);
			pstmt.setString(2, reason);
			if(isChild) {
				pstmt.setInt(3, key);
			} else {
				pstmt.setBoolean(3, modified);
				pstmt.setInt(4, key);
			}
			
			log.info("Mark record: " + pstmt.toString());
			int count = pstmt.executeUpdate();
			
			if(count != 1) {
				throw new Exception("Failed to update record");
			}
			
			// Get the child tables
			sql = "SELECT DISTINCT f.table_name, f_id FROM form f " +
					" where f.s_id = ? " + 
					" and f.parentform = ?;";
			log.info(sql + " : " + sId + " : " + fId);
			
			if (pstmt != null) try {pstmt.close();} catch(Exception e) {};
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
				
				if (pstmt2 != null) try {pstmt2.close();} catch(Exception e) {};
				pstmt2 = cRel.prepareStatement(sql);	
				pstmt2.setInt(1, key);
				log.info(pstmt2.toString());
				
				ResultSet childRecs = pstmt2.executeQuery();
				while(childRecs.next()) {
					int childKey = childRecs.getInt(1);
					markRecord(cRel, cSD, childTable, value, reason, childKey, 
							sId, childFormId, modified, true);
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
		 * Do a case insensitive check
		 */
		String sql = "select ident from users where email ilike ?";

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
	 * Get the email address for an ident
	 */
	static public String getEmailFromIdent(
			Connection connectionSD, 
			PreparedStatement pstmt, 
			String ident) throws SQLException {
		
		String email = null;;
		
		/*
		 * Get the table name and column name containing the text data
		 * Do a case insensitive check
		 */
		String sql = "select email from users where ident = ?";

		pstmt = connectionSD.prepareStatement(sql);	
		pstmt.setString(1, ident);
		log.info(pstmt.toString());
		ResultSet rs = pstmt.executeQuery();
		if (rs.next()) {
			email = rs.getString(1);
		}
		
		return email;
	}
	
	/*
	 * Get the administrator email for the organisation that the user belongs to
	 */
	static public Organisation getOrganisationDefaults(
			Connection sd, 
			String user) throws SQLException {
		
		Organisation o = new Organisation();
		
		String sqlOrganisation = "select o.id, o.name, o.company_name, o.admin_email, o.smtp_host, " +
				" o.email_domain, o.default_email_content,"
				+ "o.locale, o.company_email " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sqlOrganisation);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				o.id = rs.getInt(1);
				o.name = rs.getString(2);
				o.company_name = rs.getString(3);
				o.admin_email = rs.getString(4);
				o.smtp_host = rs.getString(5);
				o.email_domain = rs.getString(6);
				o.default_email_content = rs.getString(7);
				o.locale = rs.getString(8);
				o.company_email = rs.getString(9);
				
				if(o.locale == null) {
					o.locale = "en";
				}
				
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (Exception e) {}
		}
		return o;
	}
	

	
	/*
	 * Get the smtp host for the organisation that the user belongs to
	 */
	static public EmailServer getSmtpHost(
			Connection sd, 
			String email,
			String user) throws SQLException {
		
		EmailServer emailServer = new EmailServer();
		
		String sqlIdent = "select o.smtp_host, o.email_domain, o.email_user, o.email_password, o.email_port " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.ident = ?;";
		
		String sqlEmail = "select o.smtp_host, o.email_domain, o.email_user, o.email_password, o.email_port " +
				" from organisation o, users u " +
				" where u.o_id = o.id " +
				" and u.email ilike ?;";
		
		String sqlServer = "select smtp_host, email_domain, email_user, email_password, email_port " +
				" from server ";
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {

			if(user != null) {
				pstmt = sd.prepareStatement(sqlIdent);
				pstmt.setString(1, user);
				log.info("Get smtp_host based on user ident, SQL:" + pstmt.toString());
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					String domain = rs.getString(2);
					String emailuser = rs.getString(3);
					String emailpassword = rs.getString(4);
					int emailport = rs.getInt(5);
					if(host != null) {
						if(host.trim().length() > 0) {
							emailServer.smtpHost = host;
						}
					}
					if(domain != null) {		
						if(domain.trim().length() > 0) {
							emailServer.emailDomain = domain;
						}
					}
					if(emailuser != null) {		
						if(emailuser.trim().length() > 0) {
							emailServer.emailUser = emailuser;
						}
					}
					if(emailpassword != null) {		
						if(emailpassword.trim().length() > 0) {
							emailServer.emailPassword = emailpassword;
						}
					}
					if(emailport > 0) {		
						emailServer.emailPort = emailport;	
					}
					
				}
			} else if(email != null) {
				/*
				 * This will be for a forgotton password
				 * Use the email server for the matching email
				 */
				pstmt = sd.prepareStatement(sqlEmail);
				pstmt.setString(1, email);
				log.info("Get smtp_host based on email, SQL:" + pstmt.toString());
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					String domain = rs.getString(2);
					String emailuser = rs.getString(3);
					String emailpassword = rs.getString(4);
					int emailport = rs.getInt(5);
					if(host != null) {
						if(host.trim().length() > 0) {
							emailServer.smtpHost = host;
						}
					}
					if(domain != null) {		
						if(domain.trim().length() > 0) {
							emailServer.emailDomain = domain;
						}
					}
					if(emailuser != null) {		
						if(emailuser.trim().length() > 0) {
							emailServer.emailUser = emailuser;
						}
					}
					if(emailpassword != null) {		
						if(emailpassword.trim().length() > 0) {
							emailServer.emailPassword = emailpassword;
						}
					}
					if(emailport > 0) {		
						emailServer.emailPort = emailport;	
					}
				}
			} 
		
			/*
			 * If the smtp_host or the email_domain was not set at the organisation level try the server level defaults
			 */
			if(emailServer.smtpHost == null || emailServer.emailDomain == null) {

				try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
				pstmt = sd.prepareStatement(sqlServer);
				rs = pstmt.executeQuery();
				if(rs.next()) {
					String host = rs.getString(1);
					String domain = rs.getString(2);
					if(emailServer.smtpHost == null) {
						emailServer.smtpHost = host;
					}
					if(emailServer.emailDomain == null) {
						emailServer.emailDomain = domain;
					}
					if(emailServer.emailUser == null) {
						emailServer.emailUser = rs.getString(3);
						if(emailServer.emailUser != null && emailServer.emailUser.indexOf('@') > 0) {
							emailServer.emailUser = emailServer.emailUser.substring(0, emailServer.emailUser.indexOf('@'));
						}
					}
					if(emailServer.emailPassword == null) {
						emailServer.emailPassword = rs.getString(4);
					}
					if(emailServer.emailPort == 0) {
						emailServer.emailPort = rs.getInt(5);
					}
				}
			}
			
			log.info("Using server email: " + emailServer.smtpHost + " domain: " + emailServer.emailDomain);
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		return emailServer;
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
		 * Do a case insensitive test against email
		 */
		String sql = "update users set" +
				" one_time_password = ?," +
				" one_time_password_expiry = timestamp 'now' + interval '" + interval + "' " + 
				" where email ilike ?";

		pstmt = connectionSD.prepareStatement(sql);	
		pstmt.setString(1, uuid);
		pstmt.setString(2, email);
		log.info("SQL set oneTimePassword: " + pstmt.toString());
		int count = pstmt.executeUpdate();
		
		if(count > 0) {
			return uuid;
		} else {
			return null;
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
          } else if (extension.equals("geojson")) {
            	ct = "application/geojson";
          } else {
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
			
			for(int i = 0; i < s.languages.size(); i++) {
	
				Label l = new Label();
				ResultSet resultSet;
				
				// Get label and media
				if(text_id != null) {
					pstmt.setInt(1, s.id);
					pstmt.setString(2, s.languages.get(i).name);
					pstmt.setString(3, text_id);
					//log.info("Get labels: " + pstmt.toString());
					
					resultSet = pstmt.executeQuery();		
					while(resultSet.next()) {
		
						String t = resultSet.getString(1).trim();
						String v = resultSet.getString(2);
						
						if(t.equals("none")) {
							l.text = GeneralUtilityMethods.convertAllEmbeddedOutput(v, true);
						} else if(basePath != null && oId > 0) {
							ManifestValue manifest = new ManifestValue();
							getFileUrl(manifest, s.ident, v, basePath, oId);
							log.info("Url: " + manifest.url + " : " + v);
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
				}
				
				// Get hint
				if(hint_id != null) {
					pstmt.setInt(1, s.id);
					pstmt.setString(2, s.languages.get(i).name);
					pstmt.setString(3, hint_id);
					
					log.info("Get hint: " + pstmt.toString());
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
	 * Set labels for an option or question
	 */
	public static void setLabels(Connection sd,
			int sId, 
			String path, 
			ArrayList<Label> labels,
			String basePath) throws SQLException {
		
		ArrayList<Language> languages = new ArrayList<Language>();
		
		PreparedStatement pstmt = null;
		
		try {
		
			/*
			 * Get the languages
			 */
			languages = GeneralUtilityMethods.getLanguages(sd, sId);
			log.info("Adding labels for: " + languages.toString());
			
			String sql = "insert into translation (s_id, language, text_id, type, value) " +
					"values (?, ?, ?, ?, ?)";
			
			pstmt = sd.prepareStatement(sql);
			
			for(int i = 0; i < languages.size(); i++) {
	
				
				Label l = labels.get(i);
				
				// Set common values
				pstmt.setInt(1, sId);
				pstmt.setString(2, languages.get(i).name);
				
				System.out.println("$$$$ language: " + languages.get(i));
				System.out.println("     text: " + l.text);
				System.out.println("     hint: " + l.hint);
				System.out.println("     image: " + l.image);
				System.out.println("     video: " + l.video);
				System.out.println("     audio: " + l.audio);
				
				// Update text
				if(l.text != null ) {
					pstmt.setString(3, path + ":label");
					pstmt.setString(4, "none");
					pstmt.setString(5, GeneralUtilityMethods.convertAllxlsNames(l.text, sId, sd, true));
					log.info("Set text label: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
				// Update hint
				if(l.hint != null) {
					pstmt.setString(3, path + ":hint");
					pstmt.setString(4, "none");
					pstmt.setString(5, GeneralUtilityMethods.convertAllxlsNames(l.hint, sId, sd, true));
					log.info("Set hint label: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
				// Update image
				if(l.image != null) {
					pstmt.setString(3, path + ":label");
					pstmt.setString(4, "image");
					pstmt.setString(5, l.image);
					log.info("Set image label: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
				// Update video
				if(l.video != null) {
					pstmt.setString(3, path + ":label");
					pstmt.setString(4, "video");
					pstmt.setString(5, l.video);
					log.info("Set video label: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
				// Update audio
				if(l.audio != null) {
					pstmt.setString(3, path + ":label");
					pstmt.setString(4, "audio");
					pstmt.setString(5, l.audio);
					log.info("Set audio label: " + pstmt.toString());
					pstmt.executeUpdate();
				}
				
	
			}
		} catch (SQLException e) {
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
