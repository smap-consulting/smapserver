package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Result;


public class GeneralUtilityMethods {
	
	private static Logger log =
			 Logger.getLogger(GeneralUtilityMethods.class.getName());

	private static int LENGTH_QUESTION_NAME = 45;   // 63 max size of postgresql column names. Allow 10 chars for options + 2 chars for option separator
	private static int LENGTH_QUESTION_RAND = 3;
	private static int LENGTH_OPTION_NAME = 16;  
	private static int LENGTH_OPTION_RAND = 3;
	
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
	static public String cleanName(String in, boolean isQuestion) {
		
		String out = in.trim().toLowerCase();

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
		
		// If the name exceeds the max length then truncate to max size and add random characters to the end of the name
		int maxlength = isQuestion ? (LENGTH_QUESTION_NAME - LENGTH_QUESTION_RAND) : (LENGTH_OPTION_NAME - LENGTH_OPTION_RAND);
		int randLength = isQuestion ? LENGTH_QUESTION_RAND : LENGTH_OPTION_RAND;
		
		if(out.length() >= maxlength) {
			out = out.substring(0, maxlength);
			
			String rand  = String.valueOf(UUID.randomUUID());
			rand = rand.substring(0, randLength);
			
			out += rand;
		}
		
		System.out.println("Cleaned name: " + out + " from: " + in);
		
		return out;
	}
	
	
	
	/*
	 * Escape characters reserved for HTML
	 */
	static public String esc(String in) {
		String out = in;
		if(out != null) {
			out = out.replace("&", "&amp;");
			out = out.replace("<", "&lt;");
			out = out.replace(">", "&gt;");
		}
		return out;
	}
	
	/*
	 * Unescape characters reserved for HTML
	 */
	static public String unesc(String in) {
		String out = in;
		if(out != null) {
			out = out.replace("&amp;", "&");
			out = out.replace("&lt;", "<");
			out = out.replace("&gt;", ">");
		}
		return out;
	}
	
	/*
	 * Get Base Path
	 */
	static public String getBasePath(HttpServletRequest request) {
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		return basePath;
	}
	
	/*
	 * Throw a 404 exception if this is not a business server
	 */
	static public void assertBusinessServer(String host) {
		System.out.println("Host is: " + host);
		
		if(!host.contains("zarkman.com") &&
				!host.equals("localhost") &&
				!host.equals("dev.smap.com.au")) {
			throw new AuthorisationException();
		}
		
	}
	
	/*
	 * Rename template files
	 */
	static public void renameTemplateFiles(String oldName, String newName, String basePath, int oldProjectId, int newProjectId ) throws IOException {
		
		String oldFileName = convertDisplayNameToFileName(oldName);
		String newFileName = convertDisplayNameToFileName(newName);
			
		String fromDirectory = basePath + "/templates/" + oldProjectId;
		String toDirectory = basePath + "/templates/" + newProjectId;
			
		log.info("Renaming files from " + fromDirectory + "/" + oldFileName + " to " + toDirectory + "/" + newFileName);
		File dir = new File(fromDirectory);
		FileFilter fileFilter = new WildcardFileFilter(oldFileName + ".*");
		File[] files = dir.listFiles(fileFilter);
		
		if(files.length > 0) {
			moveFiles(files, toDirectory, newFileName);  
		} else {
			
			// Try the old /templates/xls location for files
			fromDirectory = basePath + "/templates/XLS";
			dir = new File(fromDirectory);
			files = dir.listFiles(fileFilter);
			moveFiles(files, toDirectory, newFileName); 
			
			// try the /templates location
			fromDirectory = basePath + "/templates";
			dir = new File(fromDirectory);
			files = dir.listFiles(fileFilter);
			moveFiles(files, toDirectory, newFileName); 
			
		}
		 
	}
	
	/*
	 * Move an array of files to a new location
	 */
	static void moveFiles(File[] files, String toDirectory, String newFileName)  {
		if(files != null) {	// Can be null if the directory did not exist
			for (int i = 0; i < files.length; i++) {
			   log.info("renaming file: " + files[i]);
			   String filename = files[i].getName();
			   String ext = filename.substring(filename.lastIndexOf('.'));
			   String newPath = toDirectory + "/" + newFileName + ext;
			   try {
				   FileUtils.moveFile(files[i], new File(newPath));
				   log.info("Moved " + files[i] + " to " + newPath );
			   } catch (IOException e) {
				   log.info("Error moving " + files[i] + " to " + newPath + ", message: " + e.getMessage() );
				   
			   }
			}
		}
	}
	
	/*
	 * Delete template files
	 */
	static public void deleteTemplateFiles(String name, String basePath, int projectId ) throws IOException {
		
		String fileName = convertDisplayNameToFileName(name);
		
		
		String directory = basePath + "/templates/" + projectId;
		log.info("Deleting files in " + directory + " with stem: " + fileName);
		File dir = new File(directory);
		FileFilter fileFilter = new WildcardFileFilter(fileName + ".*");
		File[] files = dir.listFiles(fileFilter);
		 for (int i = 0; i < files.length; i++) {
		   log.info("deleting file: " + files[i]);
		   files[i].delete();
		 }
	}
	
	/*
	 * Delete a directory
	 */
	static public void deleteDirectory(String directory) {
		
		File dir = new File(directory);
		
		File[] files = dir.listFiles();
		if(files != null) {
			for (int i = 0; i < files.length; i++) {
				log.info("deleting file: " + files[i]);
				if(files[i].isDirectory()) {
					deleteDirectory(files[i].getAbsolutePath());
				} else {
					files[i].delete();
				}		
			}
		}
		log.info("Deleting directory " + directory);
		dir.delete();
	}
	
	/*
	 * Get the PDF Template File
	 */
	static public File getPdfTemplate(String basePath, String displayName, int pId) {
		
		String templateName = basePath + "/templates/" + pId + "/" + 
				convertDisplayNameToFileName(displayName) +
				"_template.pdf";
		
		log.info("Attempt to get a pdf template with name: " + templateName);
		File templateFile = new File(templateName);
		
		return templateFile;
	}
	
	/*
	 * convert display name to file name
	 */
	static public String convertDisplayNameToFileName(String name) {
		// Remove special characters from the display name.  Use the display name rather than the source name as old survey files had spaces replaced by "_" wheras source name had the space removed
	    String specRegex = "[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]";
		String file_name = name.replaceAll(specRegex, "");	
		file_name = file_name.replaceAll(" ", "_");
		file_name = file_name.replaceAll("\\P{Print}", "_");	// remove all non printable (non ascii) characters. 
		
		return file_name;
	}
	
	/*
	 * 
	 */
	static public String createAttachments(String srcName, File srcPathFile, String basePath, String surveyName) {
		
		log.info("Create attachments");
		
		String value = null;
		String srcExt = "";
		
		int idx = srcName.lastIndexOf('.');
		if(idx > 0) {
			srcExt = srcName.substring(idx+1);
		}
		
		String dstName = String.valueOf(UUID.randomUUID());
		String dstDir = basePath + "/attachments/" + surveyName;
		String dstThumbsPath = basePath + "/attachments/" + surveyName + "/thumbs";
		String dstFlvPath = basePath + "/attachments/" + surveyName + "/flv";
		File dstPathFile = new File(dstDir + "/" + dstName + "." + srcExt);
		File dstDirFile = new File(dstDir);
		File dstThumbsFile = new File(dstThumbsPath);
		File dstFlvFile = new File(dstFlvPath);

		String contentType = org.smap.sdal.Utilities.UtilityMethodsEmail.getContentType(srcName);

		try {
			log.info("Processing attachment: " + srcPathFile.getAbsolutePath() + " as " + dstPathFile);
			FileUtils.forceMkdir(dstDirFile);
			FileUtils.forceMkdir(dstThumbsFile);
			FileUtils.forceMkdir(dstFlvFile);
			FileUtils.copyFile(srcPathFile, dstPathFile);
			processAttachment(dstName, dstDir, contentType,srcExt);
			
		} catch (IOException e) {
			log.log(Level.SEVERE,"Error", e);
		}
		// Create a URL that references the attachment (but without the hostname or scheme)
		value = "attachments/" + surveyName + "/" + dstName + "." + srcExt;
		
		return value;
	}
	
	/*
	 * Create thumbnails, reformat video files etc
	 */
	private static void processAttachment(String fileName, String destDir, String contentType, String ext) {

    	String cmd = "/usr/bin/smap/processAttachment.sh " + fileName + " " + destDir + " " + contentType +
    			" " + ext +
 				" >> /var/log/subscribers/attachments.log 2>&1";
		log.info("Exec: " + cmd);
		try {

			Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", cmd});
    		
    		int code = proc.waitFor();
    		log.info("Attachment processing finished with status:" + code);
    		if(code != 0) {
    			log.info("Error: Attachment processing failed");
    		}
    		
		} catch (Exception e) {
			e.printStackTrace();
    	}
		
	}
	
	/*
	 * Get the organisation id for the user
	 */
	static public int getOrganisationId(
			Connection sd, 
			String user) throws SQLException {
		
		int o_id = -1;
		
		String sqlGetOrgId = "select o_id " +
				" from users u " +
				" where u.ident = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetOrgId);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				o_id = rs.getInt(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return o_id;
	}
	
	/*
	 * Get the organisation name for the organisation id
	 */
	static public String getOrganisationName(
			Connection sd, 
			int o_id) throws SQLException {
		
		
		String sqlGetOrgName = "select o.name, o.company_name " +
				" from organisation o " +
				" where o.id = ?;";
		
		PreparedStatement pstmt = null;
		String name = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetOrgName);
			pstmt.setInt(1, o_id);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				name = rs.getString(2);	
				if(name == null) {
					name = rs.getString(1);
				}
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return name;
	}
	
	/*
	 * Get the user id from the user ident
	 */
	static public int getUserId(
			Connection sd, 
			String user) throws SQLException {
		
		int u_id = -1;
		
		String sqlGetUserId = "select id " +
				" from users u " +
				" where u.ident = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetUserId);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				u_id = rs.getInt(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return u_id;
	}
	
	/*
	 * Update the project id in the upload_event table
	 */
	static public void updateUploadEvent(
			Connection sd, 
			int pId,
			int sId) throws SQLException {
	
		
		String updatePId = "update upload_event set p_id = ? where s_id = ?;"; 
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(updatePId);
			pstmt.setInt(1, pId);
			pstmt.setInt(2, sId);
			pstmt.executeUpdate();	
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}

	}
	
    /*
     * Get Safe Template File Name
     *  Returns safe file names from the display name for the template
     */
	static public String getSafeTemplateName(String targetName) {
		String specRegex = "[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]";
		targetName = targetName.replaceAll(specRegex, "");	
		targetName = targetName.replaceAll(" ", "_");
		// The target name is not shown to users so it doesn't need to support unicode, however pyxform fails if it includes unicode chars
		targetName = targetName.replaceAll("\\P{Print}", "_");	// remove all non printable (non ascii) characters. 
	
		return targetName;
	}
	
	/*
	 * Get the survey ident from the id
	 */
	static public String getSurveyIdent(
			Connection sd, 
			int surveyId) throws SQLException {
		
		 String surveyIdent = null;
		
		String sqlGetSurveyIdent = "select ident " +
				" from survey " +
				" where s_id = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetSurveyIdent);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				surveyIdent = rs.getString(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return surveyIdent;
	}
	
	/*
	 * Return true if the upload error has already been reported
	 * This function is used to prevent large numbers of duplicate errors beign recorded when 
	 *  submission of bad results is automatically retried
	 */
	public static boolean hasUploadErrorBeenReported(
			Connection sd, 
			String user, 
			String device, 
			String ident, 
			String reason) throws SQLException {
		
		boolean reported = false;
		
		String sqlReport = "select count(*) from upload_event " +
				"where user_name = ? " +
				"and imei = ? " +
				"and ident = ? " +
				"and reason = ?;";
			
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlReport);
			pstmt.setString(1, user);
			pstmt.setString(2, device);
			pstmt.setString(3, ident);
			pstmt.setString(4, reason);
			log.info("Has error been reported: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				reported = (rs.getInt(1) > 0) ? true : false;	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return reported;
	}
	
	/*
	 * Get the survey project id from the survey id
	 */
	static public int getProjectId(
			Connection sd, 
			int surveyId) throws SQLException {
		
		int p_id = 0;
		
		String sqlGetSurveyIdent = "select p_id " +
				" from survey " +
				" where s_id = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetSurveyIdent);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				p_id = rs.getInt(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return p_id;
	}
	
	/*
	 * Get the question id using the form id and question name
	 * Used by the editor to get the question id of a newly created question
	 */
	static public int getQuestionId(
			Connection sd, 
			int formId,
			String qName) throws SQLException {
		
		int qId = 0;
		
		String sqlGetQuestionId = "select q_id " +
				" from question " +
				" where f_id = ? " +
				" and qname = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetQuestionId);
			pstmt.setInt(1, formId);
			pstmt.setString(2, qName);
			System.out.println("SQL get question id: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				qId = rs.getInt(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return qId;
	}
	
	
	/*
	 * Get the question path from the question name
	 * This assumes that all names in the survey are unique
	 */
	static public String getQuestionPath(
			Connection sd, 
			int sId,
			String qName) throws SQLException {
		
		String path = null;
		
		String sqlGetQuestionPath = "select q.path " +
				" from question q, form f" +
				" where q.f_id = f.f_id " +
				" and f.s_id = ? " +
				" and q.qname = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetQuestionPath);
			pstmt.setInt(1, sId);
			pstmt.setString(2, qName);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				path = rs.getString(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return path;
	}
	
	/*
	 * Get an access key to allow results for a form to be securely submitted
	 */
	public static String  getNewAccessKey(
			Connection sd, 
			String userIdent,
			String surveyIdent) throws SQLException {
		
		String key = null;
		int userId = -1;
		
		String sqlGetUserId = "select u.id from users u where u.ident = ?;";
		PreparedStatement pstmtGetUserId = null;
		 
		String sqlClearObsoleteKeys = "delete from dynamic_users " +
				" where expiry < now() " +
				" or expiry is null;";		
		PreparedStatement pstmtClearObsoleteKeys = null;
		
		String interval = "7 days";
		String sqlAddKey = "insert into dynamic_users (u_id, survey_ident, access_key, expiry) " +
				" values (?, ?, ?, timestamp 'now' + interval '" + interval + "');";		
		PreparedStatement pstmtAddKey = null;
		
		String sqlGetKey = "select access_key from dynamic_users where u_id = ?;";	
		PreparedStatement pstmtGetKey = null;
		
		log.info("GetAccessKey");
		try {
		
			/*
			 * Delete any expired keys
			 */
			pstmtClearObsoleteKeys = sd.prepareStatement(sqlClearObsoleteKeys);
			pstmtClearObsoleteKeys.executeUpdate();
			
			/*
			 * Get the user id
			 */
			pstmtGetUserId = sd.prepareStatement(sqlGetUserId);
			pstmtGetUserId.setString(1, userIdent);
			log.info("Get User id:" + pstmtGetUserId.toString() );
			ResultSet rs = pstmtGetUserId.executeQuery();
			if(rs.next()) {
				userId = rs.getInt(1);
			}
			
			/*
			 * Get the existing access key
			 */
			pstmtGetKey = sd.prepareStatement(sqlGetKey);
			pstmtGetKey.setInt(1, userId);
			rs = pstmtGetKey.executeQuery();
			if(rs.next()) {
				key = rs.getString(1);
			}
			
			/*
			 * Get a new key if necessary
			 */
			if(key == null) {
				
				/*
				 * Get the new access key
				 */
				key = String.valueOf(UUID.randomUUID());
				
				/*
				 * Save the key in the dynamic users table
				 */
				pstmtAddKey = sd.prepareStatement(sqlAddKey);
				pstmtAddKey.setInt(1, userId);
				pstmtAddKey.setString(2, surveyIdent);
				pstmtAddKey.setString(3, key);
				log.info("Add new key:" + pstmtAddKey.toString());
				pstmtAddKey.executeUpdate();
			}
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtGetUserId != null) { pstmtGetUserId.close();}} catch (SQLException e) {}
			try {if (pstmtClearObsoleteKeys != null) { pstmtClearObsoleteKeys.close();}} catch (SQLException e) {}
			try {if (pstmtAddKey != null) { pstmtAddKey.close();}} catch (SQLException e) {}
			try {if (pstmtGetKey != null) { pstmtGetKey.close();}} catch (SQLException e) {}
		}
		
		return key;
	}
	
	/*
	 * Delete access keys for a user when they log out
	 */
	public static void  deleteAccessKeys(
			Connection sd, 
			String userIdent) throws SQLException {
		
		String sqlDeleteKeys = "delete from dynamic_users d where d.u_id in " +
				"(select u.id from users u where u.ident = ?);";
		PreparedStatement pstmtDeleteKeys = null;
		
		log.info("DeleteAccessKeys");
		try {
		
			/*
			 * Delete any keys for this user
			 */
			pstmtDeleteKeys = sd.prepareStatement(sqlDeleteKeys);
			pstmtDeleteKeys.setString(1, userIdent);
			pstmtDeleteKeys.executeUpdate();

			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtDeleteKeys != null) { pstmtDeleteKeys.close();}} catch (SQLException e) {}
		}
		
	}
	
	/*
	 * Get a dynamic user's details from their unique key
	 */
	public static String  getDynamicUser(
			Connection sd, 
			String key) throws SQLException {
		
		String userIdent = null;
		
		String sqlGetUserDetails = "select u.ident from users u, dynamic_users d " +
				" where u.id = d.u_id " +
				" and d.access_key = ? " +
				" and d.expiry > now();";
		PreparedStatement pstmtGetUserDetails = null;
		
		
		log.info("GetDynamicUser");
		try {
		
			/*
			 * Get the user id
			 */
			pstmtGetUserDetails = sd.prepareStatement(sqlGetUserDetails);
			pstmtGetUserDetails.setString(1, key);
			log.info("Get User details:" + pstmtGetUserDetails.toString() );
			ResultSet rs = pstmtGetUserDetails.executeQuery();
			if(rs.next()) {
				userIdent = rs.getString(1);
			}		
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtGetUserDetails != null) { pstmtGetUserDetails.close();}} catch (SQLException e) {}
		}
		
		return userIdent;
	}
	/*
	 * Return true if this questions appearance means that choices come from an external file
	 */
	public static boolean isAppearanceExternalFile(String appearance) {
		if(appearance != null && appearance.toLowerCase().trim().contains("search(")) {
			return true;
		} else {
			return false;
		}
	}
		
	/*
	 * Get a list of options from an external file
	 */
	public static void getOptionsFromFile(
			Connection connectionSD,
			ArrayList<ChangeItem> ciList, 
			File csvFile,
			String csvFileName,
			String qName,
			int l_id,
			int qId,
			String qType,
			String qAppearance) {
		
		BufferedReader br = null;
		try {
			FileReader reader = new FileReader(csvFile);
			br = new BufferedReader(reader);
			CSVParser parser = new CSVParser();
	       
			// Get Header
			String line = br.readLine();
			String cols [] = parser.parseLine(line);
	       
			CSVFilter filter = new CSVFilter(cols, qAppearance);								// Get a filter
			ValueLabelCols vlc = getValueLabelCols(connectionSD, qId, qName, cols);		// Identify the columns in the CSV file that have the value and label

			while(line != null) {
				line = br.readLine();
				if(line != null) {
					String [] optionCols = parser.parseLine(line);
					if(filter.isIncluded(optionCols)) {
		    		   
						ChangeItem c = new ChangeItem();
						c.property = new PropertyChange();
						c.property.qId = qId;
						c.property.l_id = l_id;
						c.property.name = qName;					// Add for logging
						c.fileName = csvFileName;		// Add for logging
						c.property.qType = qType;
						c.property.newVal = optionCols[vlc.label];
						c.property.key = optionCols[vlc.value];
		    		  
						ciList.add(c);
		    		   
					} else {
						// ignore line
					}
				}
			}
	       
		       // TODO delete all file options that were not in the latest file (file version number)
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
		} finally {
			try {br.close();} catch(Exception e) {};
		}
	}
		
	/*
	 * Return the columns in a CSV file that have the value and label for the given question	
	 */
	public static ValueLabelCols getValueLabelCols(Connection connectionSD, int qId, String qDisplayName, String [] cols) throws Exception {
		
		ValueLabelCols vlc = new ValueLabelCols();
		
		if(cols == null) {
			// No column in this CSV file so there are not going to be any matches
			return vlc;
		}
		/*
		 * Ignore language in the query, these values are codes and are (currently) independent of language
		 */
		PreparedStatement pstmt = null;
		String sql = "SELECT o.ovalue, t.value " +
				"from option o, translation t, question q " +  		
				"where o.label_id = t.text_id " +
				"and o.l_id = q.l_id " +
				"and q.q_id = ? " +
				"and externalfile ='false';";	
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1,  qId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String valueName = rs.getString(1);
				String labelName = rs.getString(2);
				
				vlc.value = -1;
				vlc.label = -1;
				for(int i = 0; i < cols.length; i++) {
					if(cols[i].toLowerCase().equals(valueName.toLowerCase())) {
						vlc.value = i;
					} else if(cols[i].toLowerCase().equals(labelName.toLowerCase())) {
						vlc.label = i;
					}
				}
			} else {
				throw new Exception("The names of the columns to use in this csv file "
						+ "have not been set in question " + qDisplayName);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) try {pstmt.close();} catch(Exception e) {};
		}
		return vlc;
	}
	
	/*
	 * Get languages that have been used in a survey resulting in a translation entry
	 *  This is used to get languages for surveys loaded from xlfForm prior to the creation of the editor
	 *  After the creation of the editor the available languages, some of which may not have any translation entries, are 
	 *   stored in the languages table
	 */
	public static ArrayList<String> getLanguagesUsedInSurvey(Connection connectionSD, int sId) throws SQLException {
		
		PreparedStatement pstmtLanguages = null;
		
		ArrayList<String> languages = new ArrayList<String> ();
		try {
			String sqlLanguages = "select distinct t.language from translation t where s_id = ? order by t.language asc";
			pstmtLanguages = connectionSD.prepareStatement(sqlLanguages);
			
			pstmtLanguages.setInt(1, sId);
			ResultSet rs = pstmtLanguages.executeQuery();
			while(rs.next()) {
				languages.add(rs.getString(1));
			}
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}
		return languages;
	}
	
	/*
	 * Get languages from the languages table
	 */
	public static ArrayList<String> getLanguages(Connection sd, int sId) throws SQLException {
		
		PreparedStatement pstmtLanguages = null;
		ArrayList<String> languages = new ArrayList<String> ();
		
		try {
			String sqlLanguages = "select language, seq from language where s_id = ? order by seq asc";
			pstmtLanguages = sd.prepareStatement(sqlLanguages);
			
			pstmtLanguages.setInt(1, sId);
			ResultSet rs = pstmtLanguages.executeQuery();
			while(rs.next()) {
				languages.add(rs.getString(1));
			}
			
			if(languages.size() == 0) {
				// Survey was loaded from an xlsForm and the languages array was not set, get languages from translations
				languages = GeneralUtilityMethods.getLanguagesUsedInSurvey(sd, sId);
				GeneralUtilityMethods.setLanguages(sd, sId, languages);
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}
		
		return languages;
	}
	
	/*
	 * Set the languages in the language table
	 */
	public static void setLanguages(Connection sd, int sId, ArrayList<String> languages) throws SQLException {
		
		PreparedStatement pstmtDelLanguages = null;
		PreparedStatement pstmtLanguages = null;
		
		try {
			String sqlDelLanguages = "delete from language where s_id = ?;";
			pstmtDelLanguages = sd.prepareStatement(sqlDelLanguages);
			
			String sqlLanguages = "insert into language(s_id, language, seq) values(?, ?, ?);";
			pstmtLanguages = sd.prepareStatement(sqlLanguages);
			
			// Delete existing
			pstmtDelLanguages.setInt(1,sId);
			pstmtDelLanguages.executeUpdate();
			
			// Insert the new languages
			for(int i = 0; i < languages.size(); i++) {
				
				String language = languages.get(i);
				
				pstmtLanguages.setInt(1, sId);
				pstmtLanguages.setString(2, language);
				pstmtLanguages.setInt(3, i);
				
				pstmtLanguages.executeUpdate();			
			}
		
			
		} catch(SQLException e) {
			try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtDelLanguages != null) {pstmtDelLanguages.close();}} catch (SQLException e) {}
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}

	}
	
	/*
	 * Get the default language for a survey
	 */
	public static String getDefaultLanguage(Connection connectionSD, int sId) throws SQLException {
		
		PreparedStatement pstmtDefLang = null;
		PreparedStatement pstmtDefLang2 = null;
		
		String deflang = null;
		try {
			
			String sqlDefLang = "select def_lang from survey where s_id = ?; ";
			pstmtDefLang = connectionSD.prepareStatement(sqlDefLang);
			pstmtDefLang.setInt(1, sId);
			ResultSet resultSet = pstmtDefLang.executeQuery();
			if (resultSet.next()) {
				deflang = resultSet.getString(1);
				if(deflang == null) {
					// Just get the first language in the list	
					String sqlDefLang2 = "select distinct language from translation where s_id = ?; ";
					pstmtDefLang2 = connectionSD.prepareStatement(sqlDefLang2);
					pstmtDefLang2.setInt(1, sId);
					ResultSet resultSet2 = pstmtDefLang2.executeQuery();
					if (resultSet2.next()) {
						deflang = resultSet2.getString(1);
					}
				}
			}
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtDefLang != null) {pstmtDefLang.close();}} catch (SQLException e) {}
			try {if (pstmtDefLang2 != null) {pstmtDefLang2.close();}} catch (SQLException e) {}
		}
		return deflang;
	}
	
	/*
	 * Get the answer for a specific question and a specific instance
	 */
	public static ArrayList<String> getResponseForQuestion(Connection sd, Connection results, int sId, int qId, String instanceId) throws SQLException {
		
		PreparedStatement pstmtQuestion = null;
		PreparedStatement pstmtOption = null;
		PreparedStatement pstmtResults = null;
		
		String sqlQuestion = "select qType, qName, f_id from question where q_id = ?";
		String sqlOption = "select o.ovalue from option o, question q where q.q_id = ? and q.l_id = o.l_id";
		
		String qType = null;
		String qName = null;
		int fId = 0;
		
		ArrayList<String> responses = new ArrayList<String> ();
		try {
			pstmtQuestion = sd.prepareStatement(sqlQuestion);
			pstmtQuestion.setInt(1, qId);
			log.info("GetResponseForQuestion: " + pstmtQuestion.toString());
			ResultSet rs = pstmtQuestion.executeQuery();
			if(rs.next()) {
				qType = rs.getString(1);
				qName = rs.getString(2);
				fId = rs.getInt(3);
				
				ArrayList<String> tableStack = getTableStack(sd, fId);
				ArrayList<String> options = new ArrayList<String> ();
				
				// First table is for the question, last is for the instance id
				StringBuffer query = new StringBuffer();		
				
				// Add the select
				if(qType.equals("select")) {
					pstmtOption = sd.prepareStatement(sqlOption);
					pstmtOption.setInt(1, qId);
					
					log.info("Get Options: " + pstmtOption.toString());
					ResultSet rsOptions = pstmtOption.executeQuery();
					
					query.append("select ");
					int count = 0;
					while(rsOptions.next()) {
						String oName = rsOptions.getString(1);
						options.add(oName);
						
						if(count > 0) {
							query.append(",");
						}
						query.append(" t0.");
						query.append(qName);
						query.append("__");
						query.append(oName);
						query.append(" as ");
						query.append(oName);
						count++;
					}
					query.append(" from ");
				} else {
					query.append("select t0." + qName + " from ");
				}
				
				// Add the tables
				for(int i = 0; i < tableStack.size(); i++) {
					if(i > 0) {
						query.append(",");
					}
					query.append(tableStack.get(i));
					query.append(" t");
					query.append(i);
				}
				
				// Add the join
				query.append(" where ");
				if(tableStack.size() > 1) {
					for(int i = 1; i < tableStack.size(); i++) {
						if(i > 1) {
							query.append(" and ");
						}
						query.append("t");
						query.append(i - 1);
						query.append(".parkey = t");
						query.append(i);
						query.append(".prikey");
					}
				}
				
				// Add the instance
				if(tableStack.size() > 1) {
					query.append(" and ");
				}
				query.append(" t");
				query.append(tableStack.size() - 1);
				query.append(".instanceid = ?");
				
				pstmtResults = results.prepareStatement(query.toString());
				pstmtResults.setString(1, instanceId);
				log.info("Get results for a question: " + pstmtResults.toString());
				
				rs = pstmtResults.executeQuery();
				while(rs.next()) {
					if(qType.equals("select")) {
						for(String option : options) {
							int isSelected = rs.getInt(option);
							
							if(isSelected > 0) { 
								String email=option.replaceFirst("_amp_", "@");
								email=email.replaceAll("_dot_", ".");
								log.info("******** " + email);
								String emails[] = email.split(",");
								responses.add(email);
							}
						}
					} else {
						log.info("******** " + rs.getString(1));
						String email = rs.getString(1);
						if(email != null) {
							String [] emails = email.split(",");
							for(int i = 0; i < emails.length; i++) {
								responses.add(emails[i]);
							}
						}
						
					}
				}
			}
	
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtQuestion != null) {pstmtQuestion.close();}} catch (SQLException e) {}
			try {if (pstmtOption != null) {pstmtOption.close();}} catch (SQLException e) {}
			try {if (pstmtResults != null) {pstmtResults.close();}} catch (SQLException e) {}
		}
		return responses;
	}
	
	/*
	 * Starting from the past in question get all the tables up to the highest parent that are part of this survey
	 */
	public static ArrayList<String> getTableStack(Connection sd, int fId) throws SQLException {
		ArrayList<String> tables = new ArrayList<String> ();
		
		PreparedStatement pstmtTable = null;
		String sqlTable = "select table_name, parentform from form where f_id = ?";
		
		try {
			pstmtTable = sd.prepareStatement(sqlTable);
			
			while (fId > 0) {
				pstmtTable.setInt(1, fId);
				ResultSet rs = pstmtTable.executeQuery();
				if(rs.next()) {
					tables.add(rs.getString(1));
					fId = rs.getInt(2);
				} else {
					fId = 0;
				}
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtTable != null) {pstmtTable.close();}} catch (SQLException e) {}
		}	
		
		return tables;
		
	}
	
	/*
	 * Return column type if the passed in column name is in the table else return null
	 */
	public static String columnType(Connection sd, String tableName, String columnName) throws SQLException {
		
		String type = null;
		
		String sql = "select data_type from information_schema.columns where table_name = ? " +
				"and column_name = ?;";
		PreparedStatement pstmt = null;
		
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  tableName);
			pstmt.setString(2,  columnName);
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				type = rs.getString(1);
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
		return type;
		
	}
	
	/*
	 * Returns the SQL fragment that makes up the date range restriction
	 */
	public static String getDateRange(Date startDate, Date endDate, String dateTable, String dateName) {
		String sqlFrag = "";
		boolean needAnd = false;
		
		if(startDate != null) {
			sqlFrag += dateTable + "." + dateName + " >= ? ";
			needAnd = true;
		}
		if(endDate != null) {
			if(needAnd) {
				sqlFrag += "and ";
			}
			sqlFrag += dateTable + "." + dateName + " < ? ";
		}

		return sqlFrag;
	}
	
	/*
	 * Add the filename to the response
	 */
	public static void setFilenameInResponse(String filename, HttpServletResponse response) {

		String escapedFileName = null;
		
		log.info("Setting filename in response: " + filename);
		if(filename == null) {
			filename = "survey";
		}
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Encoding Filename Error", e);
		}
		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes
		
		response.setHeader("Content-Disposition", "attachment; filename=\"" + escapedFileName +"\"");	
		response.setStatus(HttpServletResponse.SC_OK);	
	}
	
	/*
	 * Get the choice filter from an xform nodeset
	 */
	public static String getChoiceFilterFromNodeset(String nodeset, boolean xlsName) {
		
		StringBuffer choice_filter = new StringBuffer("");
		String [] filterParts = null;
		
		if(nodeset != null) {
			int idx = nodeset.indexOf('[');
			int idx2 = nodeset.indexOf(']');
			if(idx > -1 && idx2 > idx) {
				filterParts = nodeset.substring(idx + 1, idx2).trim().split("\\s+");
				for(int i = 0; i < filterParts.length; i++) {
					if(filterParts[i].startsWith("/")) {
						choice_filter.append(xpathNameToName(filterParts[i], xlsName).trim() + " ");
					} else {
						choice_filter.append(filterParts[i].trim() + " ");
					}
				}
				
			}
		}
		
		return choice_filter.toString().trim();
		
	}

	/*
	 * Convert all xml fragments embedded in the supplied string to names
	 * ODK uses an html fragment <output/> to show values from questions in labels
	 */
	public static String convertAllEmbeddedOutput(String inputEsc, boolean xlsName) {
		
		StringBuffer output = new StringBuffer("");
		int idx = 0;
		String input = unesc(inputEsc);
		
		if(input != null) {
			
			while(idx >= 0) {
				
				idx = input.indexOf("<output");
				
				if(idx >= 0) {
					output.append(input.substring(0, idx));
					input = input.substring(idx + 1);
					idx = input.indexOf(">");
					if(idx >= 0) {
						String outputTxt = input.substring(0, idx + 1);
						input = input.substring(idx + 1);
						String [] parts = outputTxt.split("\\s+");
						for(int i = 0; i < parts.length; i++) {
							if(parts[i].startsWith("/")) {
								output.append(xpathNameToName(parts[i], xlsName).trim());
							} else {
								// ignore
							}		
						}
					} else {
						output.append(input);
					}
				} else {
					output.append(input);
				}
			}	
			
		}
		
		return output.toString().trim();
	}
	
	/*
	 * Convert all xPaths in the supplied string to names
	 */
	public static String convertAllXpathNames(String input, boolean xlsName) {
		StringBuffer output = new StringBuffer("");
		String [] parts = null;
		
		if(input != null) {
			
			parts = input.trim().split("\\s+");
			for(int i = 0; i < parts.length; i++) {
				if(parts[i].startsWith("/")) {
					output.append(xpathNameToName(parts[i], xlsName).trim() + " ");
				} else {
					output.append(parts[i].trim() + " ");
				}
				
			}
		}
		
		return output.toString().trim();
	}
	
	/*
	 * Convert an xpath name to just a name
	 */
	public static String xpathNameToName(String xpath, boolean xlsName) {
		String name = null;
		
		int idx = xpath.lastIndexOf("/");
		if(idx >= 0) {
			name = xpath.substring(idx + 1);
			if(xlsName) {
				name = "${" + name + "}";
			}
		}
		return name;
	}
	
	/*
	 * Convert names in xls format ${ } to xPath
	 */
	public static String convertAllxlsNames(String input, int sId, Connection sd) throws SQLException {
		
		if(input == null) {
			return input;
		}
		
		
		StringBuffer output = new StringBuffer("");
		
		Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {
			
			String matched = matcher.group();
			String qname = matched.substring(2, matched.length() - 1);
			System.out.println("Matched: " + qname);
			
			// Add any text before the match
			int startOfGroup = matcher.start();
			output.append(input.substring(start, startOfGroup));
			
			// Make sure there is a space before the match
			if(output.length() > 0 && output.charAt(output.length() - 1) != ' ') {
				output.append(' ');
			}
			
			// Add the question path
			output.append(getQuestionPath(sd, sId, qname));
			
			// Reset the start
			start = matcher.end();

			// Make sure there is a space after the match or its the end of the string
			if(start < input.length()) {
				if(input.charAt(start) != ' ') {
					output.append(' ');
				}
			}
						
		}
		
		// Get the remainder of the string
		if(start < input.length()) {
			output.append(input.substring(start));		
		}
		
		return output.toString().trim();
	}
	
	/*
	 * Translate a question type from its representation in the database to the survey model used for editing
	 */
	public static String translateTypeFromDB(String in, boolean readonly) {
		
		String out = in;
		
		if(in.equals("string") && readonly) {
			out = "note";
		}
		
		return out;
		
	}
	
	/*
	 * Translate a question type from its representation in the survey model to the database
	 */
	public static String translateTypeToDB(String in) {
		
		String out = in;
		
		if(in.equals("note")) {
			out = "string";
		}
		
		return out;
		
	}
	
	/*
	 * Get the readonly value for a question as stored in the database
	 */
	public static boolean translateReadonlyToDB(String type, boolean in) {
		
		boolean out = in;
		
		if(type.equals("note")) {
			out = true;
		}
		
		return out;
		
	}
	
	// Get the timestamp
	public static Timestamp getTimeStamp() {
		 
		java.util.Date today = new java.util.Date();
		return new Timestamp(today.getTime());
	 
	}
}
