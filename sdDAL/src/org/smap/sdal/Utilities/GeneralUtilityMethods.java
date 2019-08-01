package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.smap.sdal.constants.SmapQuestionTypes;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.managers.CsvTableManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.OrganisationManager;
import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.managers.UserManager;
import org.smap.sdal.model.AssignmentDetails;
import org.smap.sdal.model.AuditData;
import org.smap.sdal.model.AuditItem;
import org.smap.sdal.model.AutoUpdate;
import org.smap.sdal.model.ChoiceList;
import org.smap.sdal.model.ColDesc;
import org.smap.sdal.model.ColValues;
import org.smap.sdal.model.FileDescription;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.FormLength;
import org.smap.sdal.model.GeoPoint;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.LanguageItem;
import org.smap.sdal.model.Line;
import org.smap.sdal.model.LinkedTarget;
import org.smap.sdal.model.ManifestInfo;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.MySensitiveData;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Point;
import org.smap.sdal.model.Polygon;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.RoleColumnFilter;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.SqlFragParam;
import org.smap.sdal.model.SqlParam;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.SurveyLinkDetails;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableUpdateStatus;
import org.smap.sdal.model.TaskFeature;
import org.smap.sdal.model.User;
import org.smap.sdal.model.UserGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GeneralUtilityMethods {

	private static Logger log = Logger.getLogger(GeneralUtilityMethods.class.getName());

	private static LogManager lm = new LogManager();		// Application log

	private static int LENGTH_QUESTION_NAME = 57; // 63 max size of postgresql column names.
	private static int LENGTH_QUESTION_RAND = 5;


	private static String[] smapMeta = new String[] { "_hrk", "instanceid", "_instanceid", "_start", "_end", "_device",
			"prikey", "parkey", "_bad", "_bad_reason", "_user", "_survey_notes", 
			SmapServerMeta.UPLOAD_TIME_NAME,
			SmapServerMeta.SCHEDULED_START_NAME,
			SmapServerMeta.SURVEY_ID_NAME, 
			"_version",
			"_complete", "_location_trigger", "_modified", "_task_key", "_task_replace" };

	private static String[] reservedSQL = new String[] { "all", "analyse", "analyze", "and", "any", "array", "as",
			"asc", "assignment", "asymmetric", "authorization", "between", "binary", "both", "case", "cast", "check",
			"collate", "column", "constraint", "create", "cross", "current_date", "current_role", "current_time",
			"current_timestamp", "current_user", "default", "deferrable", "desc", "distinct", "do", "else", "end",
			"except", "false", "for", "foreign", "freeze", "from", "full", "grant", "group", "having", "ilike", "in",
			"initially", "inner", "intersect", "into", "is", "isnull", "join", "leading", "left", "like", "limit",
			"localtime", "localtimestamp", "natural", "new", "not", "notnull", "null", "off", "offset", "old", "on",
			"only", "or", "order", "outer", "overlaps", "placing", "primary", "references", "right", "select",
			"session_user", "similar", "some", "symmetric", "table", "then", "to", "trailing", "true", "union",
			"unique", "user", "using", "verbose", "when", "where" };
	
	 private static final String UTF8_BOM = "\uFEFF";

	/*
	 * Remove any characters from the name that will prevent it being used as a
	 * database column name
	 */
	static public String cleanName(String in, boolean isQuestion, boolean removeSqlReserved, boolean removeSmapMeta) {

		String out = null;

		if (in != null) {
			out = in.trim().toLowerCase();

			out = out.replace(" ", ""); // Remove spaces
			out = out.replaceAll("[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]", "x"); // Remove special
			// characters ;

			/*
			 * Rename fields that are the same as postgres / sql reserved words
			 */
			if (removeSqlReserved) {
				for (int i = 0; i < reservedSQL.length; i++) {
					if (out.equals(reservedSQL[i])) {
						out = "__" + out;
						break;
					}
				}
			}

			/*
			 * Rename fields that are the same as a Smap reserved word
			 */
			if (removeSmapMeta) {
				for (int i = 0; i < smapMeta.length; i++) {
					if (out.equals(smapMeta[i])) {
						out = "__" + out;
						break;
					}
				}
			}

			// If the name exceeds the max length then truncate to max size and add random
			// characters to the end of the name
			if(isQuestion) {
				int maxlength = LENGTH_QUESTION_NAME - LENGTH_QUESTION_RAND;
				if (out.length() >= maxlength) {
					out = out.substring(0, maxlength);
	
					String rand = String.valueOf(UUID.randomUUID());
					rand = rand.substring(0, LENGTH_QUESTION_RAND);
	
					out += rand;
				}
			}
		}

		return out;
	}
	
	/*
	 * Remove any characters from the name that will prevent it being used as a
	 * database column name
	 */
	static public String cleanNameNoRand(String in) {

		String out = null;

		if (in != null) {
			out = in.trim().toLowerCase();

			out = out.replace(" ", ""); // Remove spaces
			out = out.replaceAll("[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]", "x"); // Remove special
			// characters ;

			/*
			 * Rename fields that are the same as postgres / sql reserved words
			 */
			for (int i = 0; i < reservedSQL.length; i++) {
				if (out.equals(reservedSQL[i])) {
					out = "__" + out;
					break;
				}
			}
		}

		return out;
	}

	/*
	 * Escape characters reserved for HTML
	 */
	static public String esc(String in) {
		String out = in;
		if (out != null) {
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
		if (out != null) {
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
		if (basePath == null) {
			basePath = "/smap";
		} else if (basePath.equals("/ebs1")) { // Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		return basePath;
	}

	/*
	 * Get the URL prefix for media
	 */
	static public String getUrlPrefix(HttpServletRequest request) {
		return request.getScheme() + "://" + request.getServerName() + "/";
	}

	/*
	 * Throw a 404 exception if this is not a business server
	 */
	static public void assertBusinessServer(String host) {
		log.info("Business Server check: " + host);

		if (!isBusinessServer(host)) {
			log.info("Business Server check failed: " + host);
			throw new AuthorisationException();
		}

	}

	static public boolean isBusinessServer(String host) {

		boolean businessServer = true;
		if(host.endsWith("smap.com.au") 
				&& !host.equals("sg.smap.com.au")
				&& !host.equals("dev.smap.com.au")) {
			businessServer = false;
		}
		
		return businessServer;
	}

	/*
	 * Throw a 404 exception if this is not a self registration server
	 */
	static public void assertSelfRegistrationServer(String host) {
		log.info("Self registration check: " + host);

		if (!host.equals("sg.smap.com.au") 
				&& !host.equals("localhost") 
				&& !host.endsWith("reachnettechnologies.com")
				&& !host.endsWith("datacollect.icanreach.com") 
				&& !host.endsWith("encontactone.com")
				&& !host.equals("app.kontrolid.com")) {

			log.info("Self registration check failed: " + host);
			throw new AuthorisationException();
		}

	}

	/*
	 * Rename template files
	 */
	static public void renameTemplateFiles(String oldName, String newName, String basePath, int oldProjectId,
			int newProjectId) throws IOException {

		String oldFileName = convertDisplayNameToFileName(oldName);
		String newFileName = convertDisplayNameToFileName(newName);

		String fromDirectory = basePath + "/templates/" + oldProjectId;
		String toDirectory = basePath + "/templates/" + newProjectId;

		log.info("Renaming files from " + fromDirectory + "/" + oldFileName + " to " + toDirectory + "/" + newFileName);
		File dir = new File(fromDirectory);
		FileFilter fileFilter = new WildcardFileFilter(oldFileName + ".*");
		File[] files = dir.listFiles(fileFilter);

		if (files != null) {
			if (files.length > 0) {
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

	}

	/*
	 * Move an array of files to a new location
	 */
	static void moveFiles(File[] files, String toDirectory, String newFileName) {
		if (files != null) { // Can be null if the directory did not exist
			for (int i = 0; i < files.length; i++) {
				log.info("renaming file: " + files[i]);
				String filename = files[i].getName();
				String ext = filename.substring(filename.lastIndexOf('.'));
				String newPath = toDirectory + "/" + newFileName + ext;
				try {
					FileUtils.moveFile(files[i], new File(newPath));
					log.info("Moved " + files[i] + " to " + newPath);
				} catch (IOException e) {
					log.info("Error moving " + files[i] + " to " + newPath + ", message: " + e.getMessage());

				}
			}
		}
	}

	/*
	 * Delete template files
	 */
	static public void deleteTemplateFiles(String name, String basePath, int projectId) throws IOException {

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
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				log.info("deleting file: " + files[i]);
				if (files[i].isDirectory()) {
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

		String templateName = basePath + "/templates/" + pId + "/" + convertDisplayNameToFileName(displayName)
		+ "_template.pdf";

		log.info("Attempt to get a pdf template with name: " + templateName);
		File templateFile = new File(templateName);

		return templateFile;
	}

	/*
	 * Get a document template
	 */
	static public File getDocumentTemplate(String basePath, String fileName, int oId) {

		String templateName = basePath + "/media/organisation/" + oId + "/" + fileName;

		log.info("Attempt to get a document  template with name: " + templateName);
		File templateFile = new File(templateName);

		return templateFile;
	}

	/*
	 * convert display name to file name
	 */
	static public String convertDisplayNameToFileName(String name) {
		// Remove special characters from the display name. Use the display name rather
		// than the source name as old survey files had spaces replaced by "_" wheras
		// source name had the space removed
		String specRegex = "[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]";
		String file_name = name.replaceAll(specRegex, "");
		file_name = file_name.replaceAll(" ", "_");
		file_name = file_name.replaceAll("\\P{Print}", "_"); // remove all non printable (non ascii) characters.

		return file_name;
	}

	/*
	 * Add an attachment to a survey
	 */
	static public String createAttachments(String srcName, File srcPathFile, String basePath, String surveyName, String srcUrl) {

		log.info("Create attachments");

		String value = null;
		String srcExt = "";

		int idx = srcName.lastIndexOf('.');
		if (idx > 0) {
			srcExt = srcName.substring(idx + 1);
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
			
			FileUtils.forceMkdir(dstDirFile);
			FileUtils.forceMkdir(dstThumbsFile);
			FileUtils.forceMkdir(dstFlvFile);
			if(srcPathFile != null) {
				log.info("Processing attachment: " + srcPathFile.getAbsolutePath() + " as " + dstPathFile);
				FileUtils.copyFile(srcPathFile, dstPathFile);
			} else if(srcUrl != null) {
				log.info("Processing attachment: " + srcUrl + " as " + dstPathFile);
				FileUtils.copyURLToFile(new URL(srcUrl), dstPathFile);
			}
			processAttachment(dstName, dstDir, contentType, srcExt);

		} catch (IOException e) {
			log.log(Level.SEVERE, "Error", e);
		}
		// Create a URL that references the attachment (but without the hostname or
		// scheme)
		value = "attachments/" + surveyName + "/" + dstName + "." + srcExt;

		return value;
	}

	/*
	 * Create thumbnails, reformat video files etc
	 */
	private static void processAttachment(String fileName, String destDir, String contentType, String ext) {

		String cmd = "/smap_bin/processAttachment.sh " + fileName + " " + destDir + " " + contentType + " " + ext
				+ " >> /var/log/subscribers/attachments.log 2>&1";
		log.info("Exec: " + cmd);
		try {

			Process proc = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", cmd });

			int code = proc.waitFor();
			log.info("Attachment processing finished with status:" + code);
			if (code != 0) {
				log.info("Error: Attachment processing failed");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/*
	 * Return the users language
	 */
	static public String getUserLanguage(Connection sd, HttpServletRequest request, String user) throws SQLException {

		String language = null;

		String sql = "select language " + "from users u " + "where u.ident = ?";

		PreparedStatement pstmt = null;

		if(user != null) {
			try {
	
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, user);
				ResultSet rs = pstmt.executeQuery();
				if (rs.next()) {
					language = rs.getString(1);
				}
	
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Error", e);
				throw e;
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			}
		}

		if (language == null || language.trim().length() == 0) {
			Locale locale = request.getLocale();
			if(locale == null) {
				language = "en"; // Default to English
			} else {
				language = locale.getLanguage();
			}
		}
		return language;
	}

	/*
	 * Return true if the user has the specified role
	 */
	static public boolean hasSecurityRole(Connection sd, String user, int rId) throws SQLException {
		boolean value = false;

		String sql = "select count(*) " 
				+ "from users u, user_role ur " 
				+ "where u.ident = ? "
				+ "and u.id = ur.u_id " 
				+ "and ur.r_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, rId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				value = (rs.getInt(1) > 0);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return value;
	}
	
	/*
	 * Return true if the user has the security group
	 */
	static public boolean hasSecurityRole(Connection sd, String user) throws SQLException {
		boolean securityRole = false;

		String sql = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.ident = ? "
				+ "and u.id = ug.u_id " 
				+ "and ug.g_id = 6";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				securityRole = (rs.getInt(1) > 0);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return securityRole;
	}

	/*
	 * Return true if the user has the enterprise administrator group
	 */
	static public boolean isEntUser(Connection con, String ident) throws SQLException {

		String sql = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.id = ug.u_id "
				+ "and ug.g_id = " + Authorise.ENTERPRISE_ID + " "
				+ "and u.ident = ? ";

		boolean isEnt = false;
		PreparedStatement pstmt = null;
		try {
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, ident);
			ResultSet resultSet = pstmt.executeQuery();

			if (resultSet.next()) {
				isEnt = (resultSet.getInt(1) > 0);
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return isEnt;
	}
	
	/*
	 * Return true if the user has the organisational administrator role
	 */
	static public boolean isOrgUser(Connection sd, String ident) throws SQLException {

		String sql = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.id = ug.u_id "
				+ "and ug.g_id = " + Authorise.ORG_ID + " "
				+ "and u.ident = ? ";

		boolean isOrg = false;
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, ident);
			ResultSet resultSet = pstmt.executeQuery();

			if (resultSet.next()) {
				isOrg = (resultSet.getInt(1) > 0);
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return isOrg;
	}
	
	/*
	 * Return true if the user identified by their id has the organisational administrator role
	 * Also check in the archive as they may not be logged in to this organisation
	 */
	static public boolean isOrgUserById(Connection sd, int id, int o_id) throws SQLException {

		String sql = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.id = ug.u_id "
				+ "and ug.g_id = " + Authorise.ORG_ID + " "
				+ "and u.id = ?"
				+ "and u.o_id =?";

		boolean isOrg = false;
		PreparedStatement pstmt = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			pstmt.setInt(2, o_id);
			ResultSet resultSet = pstmt.executeQuery();

			if (resultSet.next()) {
				isOrg = (resultSet.getInt(1) > 0);
			} else {
				// User is presumably in a different organisation at the moment
				User u = getArchivedUser(sd, o_id, id);
				for(UserGroup ug : u.groups) {
					if(ug.id == Authorise.ORG_ID) {
						isOrg = true;
						break;
					}
				}
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return isOrg;
	}
	
	/*
	 * Return true if the user is an administrator
	 */
	static public boolean isAdminUser(Connection con, String ident) {

		String sql = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.id = ug.u_id "
				+ "and ug.g_id = 1 " 
				+ "and u.ident = ? ";

		boolean isAdmin = false;
		PreparedStatement pstmt = null;
		try {
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, ident);
			ResultSet resultSet = pstmt.executeQuery();

			if (resultSet.next()) {
				isAdmin = (resultSet.getInt(1) > 0);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		return isAdmin;
	}

	/*
	 * Return true if the user is a security user
	 */
	static public boolean isSuperUser(Connection sd, String user) throws SQLException {
		boolean superUser = false;

		String sqlGetOrgId = "select count(*) " 
				+ "from users u, user_group ug " 
				+ "where u.ident = ? "
				+ "and u.id = ug.u_id " 
				+ "and (ug.g_id = 6 or ug.g_id = 4)";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlGetOrgId);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				superUser = (rs.getInt(1) > 0);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return superUser;
	}
	
	/*
	 * Return true if the user has view data but not admin and not analysis
	 */
	static public boolean isOnlyViewData(Connection sd, String user) throws SQLException {
		boolean onlyView = false;

		String sqlGetAccessIds = "select ug.g_id " 
				+ "from users u, user_group ug " 
				+ "where u.ident = ? "
				+ "and u.id = ug.u_id " 
				+ "and (ug.g_id = " + Authorise.VIEW_DATA_ID 
				+ " or ug.g_id = " + Authorise.ADMIN_ID 
				+ " or ug.g_id = " + Authorise.ANALYST_ID 
				+ " or ug.g_id = " + Authorise.ORG_ID
				+ ")";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlGetAccessIds);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				int gId = rs.getInt(1);
				if(gId == Authorise.VIEW_DATA_ID) {
					onlyView = true;
				} else {
					onlyView = false;
					break;
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return onlyView;
	}

	/*
	 * Get the current enterprise id for the user 
	 */
	static public int getEnterpriseId(Connection sd, String user) throws SQLException {

		int e_id = -1;

		String sql = "select o.e_id " 
				+ "from users u, organisation o " 
				+ "where u.ident = ? "
				+ "and u.o_id = o.id";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				e_id = rs.getInt(1);
			} 

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {	}
		}

		return e_id;
	}

	/*
	 * Return true if the api is enabled for the users organisation
	 */
	static public boolean isApiEnabled(Connection sd, String user) throws SQLException {
		boolean enabled = false;
		
		String sql = "Select o.can_use_api "
				+ "from users u, organisation o "
				+ "where u.o_id = o.id "
				+ "and u.ident = ?";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				enabled = rs.getBoolean(1);
			} 

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {	}
		}

		return enabled;
	}
	
	/*
	 * Get the organisation object for this organisation id
	 */
	static public Organisation getOrganisation(Connection sd, int oId) throws SQLException {

		Organisation org = null;

		/*
		 * Get the organisation
		 */
		String sql = "select id, "
				+ "name, "
				+ "company_name, "
				+ "company_address, "
				+ "company_phone, "
				+ "company_email, "
				+ "can_edit, "
				+ "can_notify, "
				+ "can_use_api, "
				+ "can_submit, "
				+ "can_sms, "
				+ "set_as_theme, "
				+ "navbar_color, "
				+ "email_task, "
				+ "changed_by, "
				+ "changed_ts," 
				+ "admin_email, "
				+ "smtp_host, "
				+ "email_domain, "
				+ "email_user, "
				+ "email_password, "
				+ "email_port, "
				+ "default_email_content, "
				+ "website, "
				+ "locale,"
				+ "timezone,"
				+ "server_description,"
				+ "e_id "
				+ "from organisation "
				+ "where organisation.id = ? "
				+ "order by name asc;";			
		
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);

			ResultSet resultSet = pstmt.executeQuery();
			
			if(resultSet.next()) {
				org = new Organisation();
				org.id = resultSet.getInt("id");
				org.name = resultSet.getString("name");
				org.company_name = resultSet.getString("company_name");
				org.company_address = resultSet.getString("company_address");
				org.company_phone = resultSet.getString("company_phone");
				org.company_email = resultSet.getString("company_email");
				org.can_edit = resultSet.getBoolean("can_edit");
				org.can_notify = resultSet.getBoolean("can_notify");
				org.can_use_api = resultSet.getBoolean("can_use_api");
				org.can_submit = resultSet.getBoolean("can_submit");
				org.can_sms = resultSet.getBoolean("can_sms");
				org.appearance.set_as_theme = resultSet.getBoolean("set_as_theme");
				org.appearance.navbar_color = resultSet.getString("navbar_color");
				org.email_task = resultSet.getBoolean("email_task");
				org.changed_by = resultSet.getString("changed_by");
				org.changed_ts = resultSet.getString("changed_ts");
				org.admin_email = resultSet.getString("admin_email");
				org.smtp_host = resultSet.getString("smtp_host");
				org.email_domain = resultSet.getString("email_domain");
				org.email_user = resultSet.getString("email_user");
				org.email_password = resultSet.getString("email_password");
				org.email_port = resultSet.getInt("email_port");
				org.default_email_content = resultSet.getString("default_email_content");
				org.website = resultSet.getString("website");
				org.locale = resultSet.getString("locale");
				if(org.locale == null) {
					org.locale = "en";	// Default english
				}
				org.timeZone = resultSet.getString("timeZone");
				if(org.timeZone == null) {
					org.timeZone = "UTC";
				}
				org.server_description = resultSet.getString("server_description");
			}

	

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {	}
		}

		return org;
	}
	
	/*
	 * Get the current organisation id for the user 
	 */
	static public int getOrganisationId(Connection sd, String user) throws SQLException {

		int o_id = -1;

		String sql1 = "select o_id " 
				+ " from users u " 
				+ " where u.ident = ?;";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql1);
			pstmt.setString(1, user);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			} 

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {	}
		}

		return o_id;
	}
	
	/*
	 * Get the organisation id for the survey
	 */
	static public int getOrganisationIdForSurvey(Connection sd, int sId) throws SQLException {

		int o_id = -1;

		String sql = "select p.o_id " + 
				" from survey s, project p " + 
				"where s.p_id = p.id " + 
				"and s.s_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return o_id;
	}
	
	/*
	 * Get the organisation id for the survey ident
	 */
	static public int getOrganisationIdForSurveyIdent(Connection sd, String ident) throws SQLException {

		int o_id = -1;

		String sql = "select p.o_id " + 
				" from survey s, project p " + 
				"where s.p_id = p.id " + 
				"and s.ident = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, ident);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return o_id;
	}

	/*
	 * Get the organisation id for the project
	 */
	static public int getOrganisationIdForProject(Connection sd, int pId) throws SQLException {

		int o_id = -1;

		String sql = "select p.o_id " 
				+ " from project p " 
				+ "where p.id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, pId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			}

		} finally {
			try {
				if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}

		return o_id;
	}

	/*
	 * Get the organisation id for the task
	 */
	static public int getOrganisationIdForTask(Connection sd, int taskId) throws SQLException {

		int o_id = -1;

		String sql = "select p.o_id " 
				+ " from tasks t, task_group tg, project p " 
				+ "where tg.p_id = p.id "
				+ "and t.tg_id = tg.tg_id " 
				+ "and t.id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, taskId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (Exception e) {}
		}

		return o_id;
	}
	
	/*
	 * Get the organisation id for the notification
	 */
	static public int getOrganisationIdForNotification(Connection sd, int id) throws SQLException {

		int o_id = -1;

		String sql = "select p.o_id "
				+ "from survey s, project p, forward f "
				+ "where s.p_id = p.id "
				+ "and s.s_id = f.s_id "
				+ "and f.id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				o_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (Exception e) {}
		}

		return o_id;
	}
	
	/*
	 * Get the task group name
	 */
	static public String getTaskGroupName(Connection sd, int tgId) throws SQLException {

		String name = null;

		String sql = "select name " 
				+ " from task_group " 
				+ "where tg_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, tgId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return name;
	}
	
	/*
	 * Get the task id for an assignment
	 */
	static public int getTaskId(Connection sd, int aId) throws SQLException {

		int taskId = 0;

		String sql = "select task_id " 
				+ " from assignments " 
				+ "where id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, aId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				taskId = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return taskId;
	}
	
	/*
	 * Get the notification name
	 */
	static public String getNotificationName(Connection sd, int id) throws SQLException {

		String name = null;

		String sql = "select name " 
				+ " from forward " 
				+ "where id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return name;
	}

	/*
	 * Get the assignment status of a temporary user
	 */
	static public AssignmentDetails getAssignmentStatusForTempUser(Connection sd, String userIdent) throws SQLException {

		AssignmentDetails a = new AssignmentDetails();
		String actionLink = "/action/" + userIdent;

		String sql = "select status, completed_date, cancelled_date, deleted_date " 
				+ " from assignments " 
				+ "where action_link = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, actionLink);
			ResultSet rs = pstmt.executeQuery();
			log.info("Getting assignment status: " + pstmt.toString());
			if (rs.next()) {
				a.status = rs.getString(1);
				a.completed_date = rs.getTimestamp(2);
				a.cancelled_date = rs.getTimestamp(3);
				a.deleted_date = rs.getTimestamp(4);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return a;
	}
	
	/*
	 * Get the task project name from its id
	 */
	static public String getProjectName(Connection sd, int id) throws SQLException {

		String name = null;

		String sql = "select name " 
				+ " from project " 
				+ "where id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return name;
	}
	
	/*
	 * Get the organisation name for the organisation id
	 */
	static public String getOrganisationName(Connection sd, int o_id) throws SQLException {

		String sqlGetOrgName = "select o.name, o.company_name "
				+ " from organisation o " 
				+ " where o.id = ?;";

		PreparedStatement pstmt = null;
		String name = null;

		try {

			pstmt = sd.prepareStatement(sqlGetOrgName);
			pstmt.setInt(1, o_id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(2);
				if (name == null || name.trim().length() == 0) {
					name = rs.getString(1);
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return name;
	}
	
	/*
	 * Get the organisation time zone for the organisation id
	 */
	static public String getOrganisationTZ(Connection sd, int o_id) throws SQLException {

		String sqlGetOrgName = "select o.timezone "
				+ " from organisation o " 
				+ " where o.id = ?;";

		PreparedStatement pstmt = null;
		String tz = null;

		try {

			pstmt = sd.prepareStatement(sqlGetOrgName);
			pstmt.setInt(1, o_id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				tz = rs.getString(1);
				if (tz == null || tz.trim().length() == 0) {
					tz = "UTC";
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return tz;
	}

	/*
	 * Get the user id from the user ident
	 */
	static public int getUserId(Connection sd, String user) throws SQLException {

		int id = -1;

		String sql = "select id " + " from users u " + " where u.ident = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			log.info("Get user id: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
		}

		return id;
	}
	
	/*
	 * Get the user id from the user ident
	 */
	static public int getUserIdOrgCheck(Connection sd, String user, int oId) throws SQLException {

		int id = -1;

		String sql = "select id " + " from users u " + " where u.ident = ? and u.o_id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			pstmt.setInt(2, oId);
			log.info("Get user id: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
		}

		return id;
	}

	/*
	 * Get the role id from the role name
	 */
	static public int getRoleId(Connection sd, String name, int oId) throws SQLException {

		int id = -1;

		String sql = "select id from role where name = ? and o_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setInt(2, oId);
			log.info("Get role id: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
		}

		return id;
	}

	/*
	 * Get the user ident from the user id
	 */
	static public String getUserIdent(Connection sd, int id) throws SQLException {

		String u_ident = null;

		String sql = "select ident " 
				+ " from users u " 
				+ " where u.id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				u_ident = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return u_ident;
	}
	
	/*
	 * Get the user name from the user id
	 */
	static public String getUserName(Connection sd, int id) throws SQLException {

		String name = null;

		String sql = "select name " 
				+ " from users u " 
				+ " where u.id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return name;
	}

	/*
	 * Get the user email from the user ident
	 */
	static public String getUserEmail(Connection sd, String user) throws SQLException {

		String email = null;

		String sql = "select email " + " from users u " + " where u.ident = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				email = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return email;
	}

	/*
	 * Update the project id in the upload_event table
	 */
	static public void updateUploadEvent(Connection sd, int pId, int sId) throws SQLException {

		String updatePId = "update upload_event set p_id = ? where s_id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(updatePId);
			pstmt.setInt(1, pId);
			pstmt.setInt(2, sId);
			pstmt.executeUpdate();

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

	}

	/*
	 * The subscriber batch job has no direct connection to incoming requests Get
	 * the server name from the last upload event
	 */
	static public String getSubmissionServer(Connection sd) throws SQLException {

		String sql = "select server_name from upload_event order by ue_id desc limit 1";
		String serverName = "smap";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				serverName = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return serverName;

	}

	/*
	 * Get Safe Template File Name Returns safe file names from the display name for
	 * the template
	 */
	static public String getSafeTemplateName(String targetName) {
		String specRegex = "[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]";
		targetName = targetName.replaceAll(specRegex, "");
		targetName = targetName.replaceAll(" ", "_");
		// The target name is not shown to users so it doesn't need to support unicode,
		// however pyxform fails if it includes unicode chars
		targetName = targetName.replaceAll("\\P{Print}", "_"); // remove all non printable (non ascii) characters.

		return targetName;
	}

	/*
	 * Get the survey ident from the id
	 */
	static public String getSurveyIdent(Connection sd, int surveyId) throws SQLException {

		String surveyIdent = null;

		String sql = "select ident " 
				+ " from survey " 
				+ " where s_id = ?";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				surveyIdent = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return surveyIdent;
	}

	/*
	 * Get the survey id from the ident
	 */
	static public int getSurveyId(Connection sd, String sIdent) throws SQLException {

		int sId = 0;

		String sql = "select s_id " 
				+ "from survey " 
				+ "where ident = ?";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, sIdent);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				sId = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return sId;
	}
	
	/*
	 * Get the latest survey id from the past in surveyId
	 * HACK
	 * This results from the difficulty of moving away from using surveyId on the client while the 
	 *  survey ident now should be the correct identifier of a unique survey
	 */
	static public int getLatestSurveyId(Connection sd, int sId) throws SQLException {

		int latestSurveyId = sId;

		String sql = "select ident, deleted " 
				+ "from survey " 
				+ "where s_id = ?";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				String ident = rs.getString(1);
				boolean deleted = rs.getBoolean(2);
				
				if(deleted) {
					// Get index of second underscore
					int idx = ident.indexOf('_');
					if(idx > 0 && idx < ident.length() - 1) {
						idx = ident.indexOf('_', idx+1);
						if(idx > 0) {
							ident = ident.substring(0, idx);		// The unique ident for the current survey
							latestSurveyId = getSurveyId(sd, ident);
						}
					}
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return latestSurveyId;
	}


	/*
	 * Get the survey id from the form id
	 */
	static public int getSurveyIdForm(Connection sd, int fId) throws SQLException {

		int sId = 0;

		String sql = "select f.s_id from form f " 
				+ "where f.f_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, fId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				sId = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return sId;
	}

	/*
	 * Get the survey name from the id
	 */
	static public String getSurveyName(Connection sd, int surveyId) throws SQLException {

		String surveyName = null;

		String sqlGetSurveyName = "select display_name " 
				+ " from survey " 
				+ " where s_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlGetSurveyName);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				surveyName = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return surveyName;
	}
	
	/*
	 * Get the survey name from the surveyident
	 */
	static public String getSurveyNameFromIdent(Connection sd, String sIdent) throws SQLException {

		String surveyName = null;

		String sqlGetSurveyName = "select display_name " 
				+ "from survey " 
				+ "where ident = ? "
				+ "and not hidden";		// Get the latest

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlGetSurveyName);
			pstmt.setString(1, sIdent);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				surveyName = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return surveyName;
	}
	
	/*
	 * Get the form id from the form name
	 */
	static public int getFormId(Connection sd, int sId, String name) throws SQLException {

		int fId = 0;

		String sql = "select f_id " 
				+ "from form " 
				+ "where s_id = ? "
				+ "and name = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, name);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				fId = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return fId;
	}

	/*
	 * Return true if the upload error has already been reported This function is
	 * used to prevent large numbers of duplicate errors being recorded when
	 * submission of bad results is automatically retried
	 */
	public static boolean hasUploadErrorBeenReported(Connection sd, String user, String device, String ident,
			String reason) throws SQLException {

		boolean reported = false;

		String sqlReport = "select count(*) from upload_event " + "where user_name = ? " + "and imei = ? "
				+ "and ident = ? " + "and reason = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlReport);
			pstmt.setString(1, user);
			pstmt.setString(2, device);
			pstmt.setString(3, ident);
			pstmt.setString(4, reason);
			log.info("Has error been reported: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				reported = (rs.getInt(1) > 0) ? true : false;
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return reported;
	}

	/*
	 * Get the survey project id from the survey id
	 */
	static public int getProjectId(Connection sd, int surveyId) throws SQLException {

		int p_id = 0;

		String sql = "select p_id "
				+ " from survey " 
				+ " where s_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				p_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return p_id;
	}
	
	/*
	 * Get the survey project id from the survey ident
	 */
	static public int getProjectIdFromSurveyIdent(Connection sd, String sIdent) throws SQLException {

		int p_id = 0;

		String sql = "select p_id "
				+ " from survey " 
				+ " where ident = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, sIdent);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				p_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return p_id;
	}
	
	/*
	 * Get the survey project id from the task group id
	 */
	static public int getProjectIdFromTaskGroup(Connection sd, int tgId) throws SQLException {

		int p_id = 0;

		String sql = "select p_id " 
				+ " from task_group " 
				+ " where tg_id = ?";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, tgId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				p_id = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return p_id;
	}

	/*
	 * Get the survey human readable key using the survey id
	 */
	static public String getHrk(Connection sd, int surveyId) throws SQLException {

		String hrk = null;

		String sql = "select hrk " + " from survey " + " where s_id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, surveyId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				hrk = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		if(hrk != null && hrk.trim().length() == 0) {
			hrk = null;
		}
		return hrk;
	}

	/*
	 * Get the question id using the form id and question name Used by the editor to
	 * get the question id of a newly created question
	 */
	static public int getQuestionId(Connection sd, int formId, int sId, int changeQId, String qName) throws Exception {

		int qId = 0;

		String sqlGetQuestionId = "select q_id from question where f_id = ? and qname = ?";

		String sqlGetQuestionIdFromSurvey = "select q_id " 
				+ " from question " 
				+ " where qname = ? "
				+ "and f_id in (select f_id from form where s_id = ?)";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sqlGetQuestionId);
			pstmt.setInt(1, formId);
			pstmt.setString(2, qName);
			log.info("SQL get question id: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				qId = rs.getInt(1);
			} else {
				// Try without the form id, the question may have been moved to a different form
				pstmt.close();
				pstmt = sd.prepareStatement(sqlGetQuestionIdFromSurvey);
				pstmt.setString(1, qName);
				pstmt.setInt(2, sId);
				log.info("Getting question id without the form id: " + pstmt.toString());
				rs = pstmt.executeQuery();
				if (rs.next()) {
					qId = rs.getInt(1);
					log.info("Found qId: " + qId);
				} else {
					// Question has been deleted or renamed. Not to worry
					log.info("Question not found: " + sId + " : " + formId + " : " + qName);
				}

				// If there is more than one question with the same name then use the qId in the
				// change item
				// This will work for existing questions and this question was presumably added
				// from xlsForm
				if (rs.next()) {
					log.info("setting question id to changeQId: " + changeQId);
					qId = changeQId;
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return qId;
	}

	/*
	 * Get the column name from the question name This assumes that all names in the
	 * survey are unique
	 */
	static public String getColumnName(Connection sd, int sId, String qName) throws SQLException {

		String column_name = null;

		String sql = "select q.column_name " 
				+ " from question q, form f" 
				+ " where q.f_id = f.f_id "
				+ " and f.s_id = ? " 
				+ " and q.qname = ?";

		PreparedStatement pstmt = null;

		try {
			
			if(qName != null) {
				if(qName.equals("_hrk")) {
					column_name = "_hrk";
				} else if(qName.equals("prikey")) {
					column_name = "prikey";
				} else if(qName.equals("_user")) {
					column_name = "_user";
				} else {
					pstmt = sd.prepareStatement(sql);
					pstmt.setInt(1, sId);
					pstmt.setString(2, qName);
					ResultSet rs = pstmt.executeQuery();
					if (rs.next()) {
						column_name = rs.getString(1);
					}
				}
				
				if(column_name == null) {
					// Try preloads
					ArrayList<MetaItem> preloads = getPreloads(sd, sId);
					for(MetaItem item : preloads) {
						if(item.name.equals(qName)) {							
							column_name = item.columnName;
							break;
						}
					}
				}
			}

		} finally {
			try {if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}

		return column_name;
	}

	/*
	 * Get the column name from the question id This assumes that all names in the
	 * survey are unique
	 */
	static public String getColumnNameFromId(Connection sd, int sId, int qId) throws SQLException {

		String column_name = null;

		String sql = "select q.column_name " + " from question q, form f" + " where q.f_id = f.f_id "
				+ " and f.s_id = ? " + " and q.q_id = ?;";

		PreparedStatement pstmt = null;

		if (qId == SmapServerMeta.SCHEDULED_START_ID) {
			column_name = SmapServerMeta.SCHEDULED_START_NAME;
		} else if (qId == SmapServerMeta.UPLOAD_TIME_ID) {
			column_name = SmapServerMeta.UPLOAD_TIME_NAME;
		} else if(qId > 0) {
			try {

				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, sId);
				pstmt.setInt(2, qId);
				ResultSet rs = pstmt.executeQuery();
				if (rs.next()) {
					column_name = rs.getString(1);
				}

			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			}
		} else {
			column_name = getPreloadColumnName(sd, sId, qId);		
		}

		return column_name;
	}

	/*
	 * Get a question details
	 */
	static public Question getQuestion(Connection sd, int qId) throws SQLException {

		Question question = new Question();

		String sql = "select appearance, qname, column_name, qtype " 
				+ "from question "
				+ "where q_id = ?;";

		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, qId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				question.appearance = rs.getString(1);
				question.name = rs.getString(2);
				question.columnName = rs.getString(3);
				question.type = rs.getString(4);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
		} 
	
		return question;
	}

	/*
	 * Get an access key to allow results for a form to be securely submitted
	 */
	public static String getNewAccessKey(Connection sd, String userIdent, String surveyIdent) throws SQLException {

		String key = null;
		int userId = -1;

		String sqlGetUserId = "select u.id from users u where u.ident = ?;";
		PreparedStatement pstmtGetUserId = null;

		String sqlClearObsoleteKeys = "delete from dynamic_users " + " where expiry < now() " + " or expiry is null;";
		PreparedStatement pstmtClearObsoleteKeys = null;

		String interval = "7 days";
		String sqlAddKey = "insert into dynamic_users (u_id, survey_ident, access_key, expiry) "
				+ " values (?, ?, ?, timestamp 'now' + interval '" + interval + "');";
		PreparedStatement pstmtAddKey = null;

		String sqlGetKey = "select access_key from dynamic_users where u_id = ? "
				+ "and expiry > now() + interval ' 2 days'"; // Get a new key if less than 2 days before old one expires
		PreparedStatement pstmtGetKey = null;

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
			log.info("Get User id:" + pstmtGetUserId.toString());
			ResultSet rs = pstmtGetUserId.executeQuery();
			if (rs.next()) {
				userId = rs.getInt(1);
			}

			/*
			 * Get the existing access key
			 */
			pstmtGetKey = sd.prepareStatement(sqlGetKey);
			pstmtGetKey.setInt(1, userId);
			rs = pstmtGetKey.executeQuery();
			if (rs.next()) {
				key = rs.getString(1);
			}

			/*
			 * Get a new key if necessary
			 */
			if (key == null) {

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

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtGetUserId != null) {
					pstmtGetUserId.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtClearObsoleteKeys != null) {
					pstmtClearObsoleteKeys.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtAddKey != null) {
					pstmtAddKey.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtGetKey != null) {
					pstmtGetKey.close();
				}
			} catch (SQLException e) {
			}
		}

		return key;
	}

	/*
	 * Delete access keys for a user when they log out
	 */
	public static void deleteAccessKeys(Connection sd, String userIdent) throws SQLException {

		String sqlDeleteKeys = "delete from dynamic_users d where d.u_id in "
				+ "(select u.id from users u where u.ident = ?);";
		PreparedStatement pstmtDeleteKeys = null;

		log.info("DeleteAccessKeys");
		try {

			/*
			 * Delete any keys for this user
			 */
			pstmtDeleteKeys = sd.prepareStatement(sqlDeleteKeys);
			pstmtDeleteKeys.setString(1, userIdent);
			pstmtDeleteKeys.executeUpdate();

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtDeleteKeys != null) {
					pstmtDeleteKeys.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Get a dynamic user's details from their unique key
	 */
	public static String getDynamicUser(Connection sd, String key) throws SQLException {

		String userIdent = null;

		String sqlGetUserDetails = "select u.ident from users u, dynamic_users d " 
				+ " where u.id = d.u_id "
				+ " and d.access_key = ? " 
				+ " and d.expiry > now();";
		PreparedStatement pstmtGetUserDetails = null;

		log.info("GetDynamicUser");
		try {

			/*
			 * Get the user id
			 */
			pstmtGetUserDetails = sd.prepareStatement(sqlGetUserDetails);
			pstmtGetUserDetails.setString(1, key);
			log.info("Get User details:" + pstmtGetUserDetails.toString());
			ResultSet rs = pstmtGetUserDetails.executeQuery();
			if (rs.next()) {
				userIdent = rs.getString(1);
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtGetUserDetails != null) {
					pstmtGetUserDetails.close();
				}
			} catch (SQLException e) {
			}
		}

		return userIdent;
	}

	/*
	 * Return true if this questions appearance means that choices come from an
	 * external file
	 */
	public static boolean isAppearanceExternalFile(String appearance) {
		if (appearance != null && (appearance.toLowerCase().trim().contains("search(") ||
				appearance.toLowerCase().trim().contains("lookup_choices("))) {
			return true;
		} else {
			return false;
		}
	}
	
	/*
	 * Add nodeset functions to a nodeset
	 */
	public static String addNodesetFunctions(String nodeset, String randomize) {
		if (parameterIsSet(randomize)) {
			return "randomize(" + nodeset + ")";
		} else {
			return nodeset;
		}
	}

	/*
	 * Get a list of options from an external file
	 *
	public static void getOptionsFromFile(
			Connection sd, 
			ResourceBundle localisation, 
			String user,
			int sId,
			ArrayList<ChangeItem> ciList, 
			File csvFile, 
			File oldCsvFile,
			String csvFileName, 
			String qName, 
			int l_id, 
			int qId, String qType, String qAppearance) throws ApplicationWarning, Exception {

		// Store the value and label data for each row in here
		class OptionItem {
			public String value;
			public ArrayList<LanguageItem> label = new ArrayList<LanguageItem> ();
			private String filterString;
			public HashMap<String, String> filter;

			public OptionItem(String[] data, ArrayList<ValueLabelCols> vlcA, HashMap<String, String> f) {

				for(int i = 0; i < vlcA.size(); i++) {
					ValueLabelCols vlc = vlcA.get(i);
					if(i == 0) {
						value = data[vlc.value];
					}
					label.add(new LanguageItem(vlc.language, data[vlc.label]));
				}

				filter = f;
				filterString = "";
				if (f != null) {
					String fv = f.get("_smap_cascade");
					if (fv != null) {
						filterString = fv;
					}
				}
			}

			public boolean equals(Object o) {

				if ((o instanceof OptionItem) 
						&& value.equals(((OptionItem) o).value) 
						&& labelsUnchanged((OptionItem) o) 
						&& filterString.equals(((OptionItem) o).filterString)) {
					return true;
				}
				return false;
			}

			private boolean labelsUnchanged(OptionItem oi) {
				boolean unchanged = true;

				if(oi.label.size() != label.size()) {
					unchanged = false;
				} else {
					for(int i = 0; i < label.size(); i++) {
						LanguageItem thisLabel = label.get(i);
						LanguageItem otherLabel = oi.label.get(i);
						if(!thisLabel.language.equals(otherLabel.language) ||
								!thisLabel.text.equals(otherLabel.text)) {
							unchanged = false;
							break;
						}
					}
				}
				return unchanged;
			}
		}

		/*
		 * Start code_migration compressed
		 * Important! loading options from a CSV file into choices for a question has been deprecated
		 * As an interim implementation and select multiple questions that uses a CSV file will be marked as compressed
		 * This will mean it will not depend on these options to store results
		 * 
		 * deprecate this!
		 *
		if(qType.equals("select")) {
			String sqlHasExternal = "select count(*) from option where l_id = ? and externalfile;";
			PreparedStatement pstmtHasExternal = null;
			String sqlSetCompressed = "update question set compressed = 'true' where q_id = ?";
			PreparedStatement pstmtSetCompressed = null;
			try {
				pstmtHasExternal = sd.prepareStatement(sqlHasExternal);
				pstmtHasExternal.setInt(1, l_id);
				ResultSet rs = pstmtHasExternal.executeQuery();
				
				boolean hasExternals = false;
				if(rs.next()) {
					if(rs.getInt(1) > 0) {
						hasExternals = true;
					}
				}
				if(hasExternals) {
					pstmtSetCompressed = sd.prepareStatement(sqlSetCompressed);
					pstmtSetCompressed.setInt(1, qId);
					pstmtSetCompressed.executeUpdate();
				}
			} finally {
				if(pstmtHasExternal != null) try {pstmtHasExternal.close();} catch(Exception e) {}
				if(pstmtSetCompressed != null) try {pstmtSetCompressed.close();} catch(Exception e) {}
			}
		}
		/*
		 * End code Migration
		 *
		HashMap<String, String> optionValues = new HashMap<String, String> ();	// Ensure uniqueness of values
		List<OptionItem> listNew = new ArrayList<OptionItem>();
		List<OptionItem> listOld = new ArrayList<OptionItem>();

		BufferedReader brNew = null;
		BufferedReader brOld = null;
		try {
			FileReader readerNew = new FileReader(csvFile);
			brNew = new BufferedReader(readerNew);

			FileReader readerOld = null;
			if (oldCsvFile != null && oldCsvFile.exists()) {
				readerOld = new FileReader(oldCsvFile);
				brOld = new BufferedReader(readerOld);
			}

			CSVParser parser = new CSVParser();

			// Get Header
			String newLine = brNew.readLine();
			String cols[] = parser.parseLine(newLine);

			CSVFilter filter = new CSVFilter(cols, qAppearance); // Get a filter
			ValueLabelColsResp vlcA = getValueLabelCols(sd, qId, qName, cols); // Identify the columns in the CSV file that
			if(vlcA.values.size() == 0) {
				String msg = localisation.getString("ex_csv_nc");
				msg = msg.replace("%s1", qName);
				lm.writeLog(sd, sId, user, "csv file", msg);
				throw new ApplicationWarning(msg);
			}
		
			/*
			 * Read the old and new data rows Only get the columns that are to be applied as
			 * if a new unrelated column is added it should not change existing data Based
			 * on:
			 * https://stackoverflow.com/questions/31426187/want-to-find-content-difference-
			 * between-two-text-files-with-java
			 *
			while (newLine != null) {
				newLine = brNew.readLine();
				if(newLine != null) {
					String[] data = parser.parseLine(newLine);
					if (filter.isIncluded(data)) {
						OptionItem item = new OptionItem(data, vlcA.values, filter.GetCascadeFilter(data));
						// This this option if we do not already have it, csv files can have many duplicates
						String test = optionValues.get(item.value);
						if(test == null) {
							listNew.add(item);
							optionValues.put(item.value, item.value);
						}
						
					}
				}
			}

			// Compare with old values as long as there was not an error in reading the columns from the csv file
			if(vlcA.error) {
				// Force delete of existing choices
				if (brOld != null) {
					newLine = brOld.readLine(); // Jump past the header
					while (newLine != null) {
						newLine = brOld.readLine(); // Jump past the header
						if(newLine != null) {
							String[] data = parser.parseLine(newLine);
							if (filter.isIncluded(data)) {
								ChangeItem c = new ChangeItem();
								c.option = new Option();
								c.option.l_id = l_id;
								c.qName = qName; // Add for logging
								c.fileName = csvFileName; // Add for logging
								c.qType = qType;
								c.option.value = data[vlcA.values.get(0).value];
								c.action = "delete";

								ciList.add(c);
							}
						}
					}
				}
			} else {
				// Get the old values so we can look for changes
				if (brOld != null) {
					newLine = brOld.readLine(); // Jump past the header
					while (newLine != null) {
						newLine = brOld.readLine(); // Jump past the header
						if(newLine != null) {
							String[] data = parser.parseLine(newLine);
							if (filter.isIncluded(data)) {
								listOld.add(new OptionItem(data, vlcA.values, filter.GetCascadeFilter(data)));
							}
						}
					}
				}
			}

			// debug
			log.info(" ======== New list: " + listNew.size());
			log.info(" ======== Old list" + listOld.size());

			/*
			 * Create a list of items to add that are in the new list but not in the old
			 * Create a list of items to remove that are in the old list but not in the new
			 *
			ArrayList<OptionItem> listAdd = new ArrayList<OptionItem>(listNew);
			listAdd.removeAll(listOld);
			ArrayList<OptionItem> listDel = new ArrayList<OptionItem>(listOld);
			listDel.removeAll(listNew);

			// Add a limit of 100 changes to be applied
			if(listAdd.size() < 100 && listDel.size() < 100) {
				/*
				 * Delete Items 
				 * Do this first as presumably its possible that the new option could have identical identifiers to an 
				 * option its replacing but with a different label
				 *
				log.info("There are " + listDel.size() + " items to delete");
				for (OptionItem item : listDel) {
	
					ChangeItem c = new ChangeItem();
					c.option = new Option();
					c.option.l_id = l_id;
					c.qName = qName; // Add for logging
					c.fileName = csvFileName; // Add for logging
					c.qType = qType;
					c.option.value = item.value;
					c.action = "delete";
	
					ciList.add(c);
	
				}
	
				/*
				 * Add new items
				 *
				log.info("Adding " + listAdd.size() + " items");
				for (OptionItem item : listAdd) {
	
					ChangeItem c = new ChangeItem();
					c.option = new Option();
					c.option.l_id = l_id;
					c.qName = qName; // Add for logging
					c.fileName = csvFileName; // Add for logging
					c.qType = qType;
					c.option.externalLabel = item.label;
					c.option.value = item.value;
					c.option.cascade_filters = item.filter;
					c.action = "add";
	
					ciList.add(c);
	
				}
			}

		} finally {
			try {brNew.close();} catch (Exception e) {};
			if (brOld != null) {	try {brNew.close();} catch (Exception e) {}};
		}
	}
	*/
	
	/*
	 * Check for existence of an option
	 */
	public static boolean optionExists(Connection sd, int l_id, String value, String cascade_filters) {
		boolean exists = false;

		String sql = "select count(*) from option where l_id = ? and ovalue = ? and cascade_filters = ?;";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, l_id);
			pstmt.setString(2, value);
			pstmt.setString(3, cascade_filters);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				if (rs.getInt(1) > 0) {
					exists = true;
				}
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
			}
			;
		}

		return exists;
	}

	/*
	 * Convert an audit file into a Hashmap
	 */
	public static  AuditData getAuditHashMap(File csvFile, String auditPath, ResourceBundle localisation) {

		AuditData data = new AuditData();
		
		BufferedReader br = null;

		try {
			FileReader reader = new FileReader(csvFile);
			br = new BufferedReader(reader);
			CSVParser parser = new CSVParser(localisation);

			// Get Header
			String line = br.readLine();

			log.info(" Start Get Audit =======================================================");
			while (line != null) {
				
				data.rawAudit.append(line).append("\n");		// Save the raw data
				
				String[] auditCols = parser.parseLine(line);
				int time = 0;
				if (auditCols.length >= 4 && auditCols[0] != null && auditCols[0].equals("question")) {
					String id = auditCols[1];
					if (id != null) {
						id = id.trim();
						if (id.startsWith(auditPath)) {
							int idx = id.lastIndexOf('/');
							if(idx >= 0) {
								String name = id.substring(idx + 1);
							
								log.info("Name: " + name);
								try {
									BigInteger from = new BigInteger(auditCols[2]);
									BigInteger to = new BigInteger(auditCols[3]);
									BigInteger diff = to.subtract(from);
									time = diff.intValue();
									
									// Timer audit value based on total time in the question
									AuditItem currentItem = data.firstPassAudit.get(name);
									if(currentItem == null) {
										currentItem = new AuditItem();
									}	
									currentItem.time += time;
									
									// Location audit value based on location of last entry into the question
									if(auditCols.length >= 6) {
										Double lat = new Double(auditCols[4]);
										Double lon = new Double(auditCols[5]);
										currentItem.location = new GeoPoint(lat, lon);
									}
									data.firstPassAudit .put(name, currentItem);
									
								} catch (Exception e) {
									log.info("Error: invalid audit line: " + e.getMessage() + " : " + line);
								}
							}
						}
					}

				}
				line = br.readLine();
			}

		
			
			log.info(" End Get Audit xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		} finally {
			try {br.close();} catch (Exception e) {};
		}
		
		return data;

	}
	
	/*
	 * Convert an audit file into a Hashmap
	 */
	public static  HashMap<String, AuditItem> getAuditValues(AuditData data, ArrayList<String> columns, ResourceBundle localisation) {

			/*
			 * Only add audit values that are in this form Also make sure we had a timing
			 * value for very column in this form
			 */
		HashMap<String, AuditItem> auditItems = new HashMap();

		for (String col : columns) {
			if (!col.startsWith("_") && !col.equals("meta") && 
					!col.equals("instanceID") && !col.equals("instanceName")) {				

				AuditItem ai = data.firstPassAudit.get(col);
				log.info("Getting audit item for: " + col);
				if(ai == null) {
					log.info("audit item not found");
					ai = new AuditItem();
				}
				auditItems.put(col, ai);

			}
		}
			
		log.info(" End Get Audit xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

		
		
		return auditItems;

	}

	/*
	 * Get languages that have been used in a survey resulting in a translation
	 * entry This is used to get languages for surveys loaded from xlfForm prior to
	 * the creation of the editor After the creation of the editor the available
	 * languages, some of which may not have any translation entries, are stored in
	 * the languages table
	 */
	public static ArrayList<String> getLanguagesUsedInSurvey(Connection sd, int sId) throws SQLException {

		PreparedStatement pstmt = null;

		ArrayList<String> languages = new ArrayList<String>();
		try {
			String sqlLanguages = "select distinct t.language from translation t where s_id = ? order by t.language asc";
			pstmt = sd.prepareStatement(sqlLanguages);

			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				languages.add(rs.getString(1));
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {}
		}
		return languages;
	}

	/*
	 * Get languages from the languages table
	 */
	public static ArrayList<Language> getLanguages(Connection sd, int sId) throws SQLException {

		PreparedStatement pstmt = null;
		ArrayList<Language> languages = new ArrayList<Language>();

		try {
			String sqlLanguages = "select id, language, seq from language where s_id = ? order by seq asc";
			pstmt = sd.prepareStatement(sqlLanguages);

			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				languages.add(new Language(rs.getInt(1), rs.getString(2)));
			}

			if (languages.size() == 0) {
				// Survey was loaded from an xlsForm and the languages array was not set, get
				// languages from translations
				ArrayList<String> languageNames = GeneralUtilityMethods.getLanguagesUsedInSurvey(sd, sId);
				for (int i = 0; i < languageNames.size(); i++) {
					languages.add(new Language(-1, languageNames.get(i)));
				}
				GeneralUtilityMethods.setLanguages(sd, sId, languages);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return languages;
	}

	/*
	 * Set the languages in the language table
	 */
	public static void setLanguages(Connection sd, int sId, ArrayList<Language> languages) throws SQLException {

		PreparedStatement pstmtDelete = null;
		PreparedStatement pstmtInsert = null;
		PreparedStatement pstmtUpdate = null;
		PreparedStatement pstmtUpdateTranslations = null;

		try {
			String sqlDelete = "delete from language where id = ? and s_id = ?;";
			pstmtDelete = sd.prepareStatement(sqlDelete);

			String sqlInsert = "insert into language(s_id, language, seq) values(?, ?, ?);";
			pstmtInsert = sd.prepareStatement(sqlInsert);

			String sqlUpdate = "update language " + "set language = ?, " + "seq = ? " + "where id = ? "
					+ "and s_id = ?"; // Security
			pstmtUpdate = sd.prepareStatement(sqlUpdate);

			String sqlUpdateTranslations = "update translation " + "set language = ? " + "where s_id = ? "
					+ "and language = (select language from language where id = ?);";
			pstmtUpdateTranslations = sd.prepareStatement(sqlUpdateTranslations);

			// Process each language in the list
			int seq = 0;
			for (int i = 0; i < languages.size(); i++) {

				Language language = languages.get(i);

				if (language.deleted) {
					// Delete language
					pstmtDelete.setInt(1, language.id);
					pstmtDelete.setInt(2, sId);

					log.info("Delete language: " + pstmtDelete.toString());
					pstmtDelete.executeUpdate();

				} else if (language.id > 0) {

					// Update the translations using this language
					// (note: for historical reasons the language name is repeated in each
					// translation rather than the language id)
					pstmtUpdateTranslations.setString(1, language.name);
					pstmtUpdateTranslations.setInt(2, sId);
					pstmtUpdateTranslations.setInt(3, language.id);

					log.info("Update Translations: " + pstmtUpdateTranslations.toString());
					pstmtUpdateTranslations.executeUpdate();

					// Update language name
					pstmtUpdate.setString(1, language.name);
					pstmtUpdate.setInt(2, seq);
					pstmtUpdate.setInt(3, language.id);
					pstmtUpdate.setInt(4, sId);

					log.info("Update Language: " + pstmtUpdate.toString());
					pstmtUpdate.executeUpdate();

					seq++;
				} else if (language.id <= 0) {
					// insert language
					pstmtInsert.setInt(1, sId);
					pstmtInsert.setString(2, language.name);
					pstmtInsert.setInt(3, seq);

					log.info("Insert Language: " + pstmtInsert.toString());
					pstmtInsert.executeUpdate();

					seq++;
				}

			}

		} catch (SQLException e) {
			try {
				sd.rollback();
			} catch (Exception ex) {
				log.log(Level.SEVERE, "", ex);
			}
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtDelete != null) {
					pstmtDelete.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtInsert != null) {
					pstmtInsert.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtUpdate != null) {
					pstmtUpdate.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtUpdateTranslations != null) {
					pstmtUpdateTranslations.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Make sure media is consistent across all languages A future change may have
	 * media per language enabled
	 */
	public static void setMediaForLanguages(Connection sd, int sId, ArrayList<Language> languages) throws SQLException {

		// ArrayList<String> languages = new ArrayList<String> ();

		PreparedStatement pstmtGetLanguages = null;
		PreparedStatement pstmtGetMedia = null;
		PreparedStatement pstmtHasMedia = null;
		PreparedStatement pstmtDeleteMedia = null;
		PreparedStatement pstmtInsertMedia = null;

		String sqlHasMedia = "select count(*) from translation t " + "where t.s_id = ? " + "and t.type = ? "
				+ "and t.value = ? " + "and t.text_id = ? " + "and t.language = ?";

		String sqlDeleteMedia = "delete from translation where s_id = ? " + "and type = ? " + "and text_id = ? "
				+ "and language = ?";

		String sqlInsertMedia = "insert into translation (s_id, type, text_id, value, language) values (?, ?, ?, ?, ?); ";

		try {

			// 1. Get the media from the translation table ignoring language
			String sqlGetMedia = "select distinct t.type, t.value, t.text_id from translation t " + "where t.s_id = ? "
					+ "and (t.type = 'image' or t.type = 'audio' or t.type = 'video'); ";

			pstmtGetMedia = sd.prepareStatement(sqlGetMedia);
			pstmtGetMedia.setInt(1, sId);

			log.info("Get distinct media: " + pstmtGetMedia.toString());
			ResultSet rs = pstmtGetMedia.executeQuery();

			/*
			 * Prepare statments used within the loop
			 */
			pstmtHasMedia = sd.prepareStatement(sqlHasMedia);
			pstmtHasMedia.setInt(1, sId);

			pstmtDeleteMedia = sd.prepareStatement(sqlDeleteMedia);
			pstmtDeleteMedia.setInt(1, sId);

			pstmtInsertMedia = sd.prepareStatement(sqlInsertMedia);
			pstmtInsertMedia.setInt(1, sId);

			while (rs.next()) {
				String type = rs.getString(1);
				String value = rs.getString(2);
				String text_id = rs.getString(3);

				// 2. Check that each language has this media
				for (Language language : languages) {
					String languageName = language.name;
					boolean hasMedia = false;

					pstmtHasMedia.setString(2, type);
					pstmtHasMedia.setString(3, value);
					pstmtHasMedia.setString(4, text_id);
					pstmtHasMedia.setString(5, languageName);

					log.info("Has Media: " + pstmtHasMedia.toString());
					ResultSet rsHasMedia = pstmtHasMedia.executeQuery();
					if (rsHasMedia.next()) {
						if (rsHasMedia.getInt(1) > 0) {
							hasMedia = true;
						}
					}

					if (!hasMedia) {

						// 3. Delete any translation entries for the media that have the wrong value
						pstmtDeleteMedia.setString(2, type);
						pstmtDeleteMedia.setString(3, text_id);
						pstmtDeleteMedia.setString(4, languageName);

						log.info("SQL delete media: " + pstmtDeleteMedia.toString());
						pstmtDeleteMedia.executeUpdate();

						// 4. Insert this translation value
						pstmtInsertMedia.setString(2, type);
						pstmtInsertMedia.setString(3, text_id);
						pstmtInsertMedia.setString(4, value);
						pstmtInsertMedia.setString(5, languageName);

						log.info("SQL insert media: " + pstmtInsertMedia.toString());
						pstmtInsertMedia.executeUpdate();
					}

				}

			}

		} catch (SQLException e) {
			try {
				sd.rollback();
			} catch (Exception ex) {
				log.log(Level.SEVERE, "", ex);
			}
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtGetLanguages != null) {
					pstmtGetLanguages.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtGetMedia != null) {
					pstmtGetMedia.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtHasMedia != null) {
					pstmtHasMedia.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtDeleteMedia != null) {
					pstmtDeleteMedia.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtInsertMedia != null) {
					pstmtInsertMedia.close();
				}
			} catch (SQLException e) {
			}

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
				if (deflang == null) {
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
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtDefLang != null) {
					pstmtDefLang.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtDefLang2 != null) {
					pstmtDefLang2.close();
				}
			} catch (SQLException e) {
			}
		}
		return deflang;
	}

	/*
	 * Get the answer for a specific question and a specific instance
	 */
	public static ArrayList<String> getResponseForEmailQuestion(Connection sd, Connection results, int sId, String qName,
			String instanceId) throws SQLException {

		PreparedStatement pstmtQuestion = null;
		PreparedStatement pstmtOption = null;
		PreparedStatement pstmtResults = null;

		String sqlQuestion = "select qType, q_id, f_id from question where qname = ? and f_id in "
				+ "(select f_id from form where s_id = ?)";
		String sqlOption = "select o.ovalue, o.column_name from option o, question q where q.q_id = ? and q.l_id = o.l_id";

		String qType = null;
		int fId = 0;
		int qId = 0;

		ArrayList<String> responses = new ArrayList<String>();
		try {
			pstmtQuestion = sd.prepareStatement(sqlQuestion);
			pstmtQuestion.setString(1, qName);
			pstmtQuestion.setInt(2, sId);
			log.info("GetResponseForQuestion: " + pstmtQuestion.toString());
			ResultSet rs = pstmtQuestion.executeQuery();
			if (rs.next()) {
				qType = rs.getString(1);
				qId = rs.getInt(2);
				fId = rs.getInt(3);

				ArrayList<String> tableStack = getTableStack(sd, fId);
				ArrayList<String> options = new ArrayList<String>();

				// First table is for the question, last is for the instance id
				StringBuffer query = new StringBuffer();

				// Add the select
				if (qType.equals("select")) {
					pstmtOption = sd.prepareStatement(sqlOption);
					pstmtOption.setInt(1, qId);

					log.info("Get Options: " + pstmtOption.toString());
					ResultSet rsOptions = pstmtOption.executeQuery();

					query.append("select ");
					int count = 0;
					while (rsOptions.next()) {
						String oValue = rsOptions.getString(1);
						String oColumnName = rsOptions.getString(2);
						options.add(oValue);

						if (count > 0) {
							query.append(",");
						}
						query.append(" t0.");
						query.append(qName);
						query.append("__");
						query.append(oColumnName);
						query.append(" as ");
						query.append(oValue);
						count++;
					}
					query.append(" from ");
				} else {
					query.append("select t0." + qName + " from ");
				}

				// Add the tables
				for (int i = 0; i < tableStack.size(); i++) {
					if (i > 0) {
						query.append(",");
					}
					query.append(tableStack.get(i));
					query.append(" t");
					query.append(i);
				}

				// Add the join
				query.append(" where ");
				if (tableStack.size() > 1) {
					for (int i = 1; i < tableStack.size(); i++) {
						if (i > 1) {
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
				if (tableStack.size() > 1) {
					query.append(" and ");
				}
				query.append(" t");
				query.append(tableStack.size() - 1);
				query.append(".instanceid = ?");

				pstmtResults = results.prepareStatement(query.toString());
				pstmtResults.setString(1, instanceId);
				log.info("Get results for a question: " + pstmtResults.toString());

				rs = pstmtResults.executeQuery();
				while (rs.next()) {
					if (qType.equals("select")) {
						for (String option : options) {
							int isSelected = rs.getInt(option);

							if (isSelected > 0) {
								String email = option.replaceFirst("_amp_", "@");
								email = email.replaceAll("_dot_", ".");
								log.info("******** " + email);
								String emails[] = email.split(",");
								for (int i = 0; i < emails.length; i++) {
									responses.add(emails[i]);
								}
							}
						}
					} else {
						log.info("******** " + rs.getString(1));
						String email = rs.getString(1);
						if (email != null) {
							String[] emails = email.split(",");
							for (int i = 0; i < emails.length; i++) {
								responses.add(emails[i]);
							}
						}

					}
				}
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtQuestion != null) {
					pstmtQuestion.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtOption != null) {
					pstmtOption.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtResults != null) {
					pstmtResults.close();
				}
			} catch (SQLException e) {
			}
		}
		return responses;
	}
	
	/*
	 * Get the answer for a specific question and a specific instance
	 */
	public static String getResponseMetaValue(Connection sd, Connection results, int sId, String metaName,
			String instanceId) throws SQLException {

		PreparedStatement pstmtResults = null;

		String value = null;
		try {
			ArrayList<MetaItem> preloads = getPreloads(sd, sId);
			for(MetaItem item : preloads) {
				if(item.name.equals(metaName)) {
					Form f = getTopLevelForm(sd, sId);
					StringBuffer query = new StringBuffer("select ");
					query.append(item.columnName);
					query.append(" from ");
					query.append(f.tableName);
					query.append(" where instanceid = ?");
					
					pstmtResults = results.prepareStatement(query.toString());
					pstmtResults.setString(1, instanceId);
					log.info("Get results for a question: " + pstmtResults.toString());
					ResultSet rs = pstmtResults.executeQuery();
					if (rs.next()) {
						value = rs.getString(1);
					}
					break;
				}
			}
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {if (pstmtResults != null) {	pstmtResults.close();}} catch (SQLException e) {}
		}
		return value;
	}

	/*
	 * Starting from the past in question get all the tables up to the highest
	 * parent that are part of this survey
	 */
	public static ArrayList<String> getTableStack(Connection sd, int fId) throws SQLException {
		ArrayList<String> tables = new ArrayList<String>();

		PreparedStatement pstmtTable = null;
		String sqlTable = "select table_name, parentform from form where f_id = ?";

		try {
			pstmtTable = sd.prepareStatement(sqlTable);

			while (fId > 0) {
				pstmtTable.setInt(1, fId);
				ResultSet rs = pstmtTable.executeQuery();
				if (rs.next()) {
					tables.add(rs.getString(1));
					fId = rs.getInt(2);
				} else {
					fId = 0;
				}
			}

		} finally {
			try {
				if (pstmtTable != null) {pstmtTable.close();}} catch (SQLException e) {}
		}

		return tables;

	}

	/*
	 * Return column type if the passed in column name is in the table else return
	 * null
	 */
	public static String columnType(Connection connection, String tableName, String columnName) throws SQLException {

		String type = null;

		String sql = "select data_type from information_schema.columns where table_name = ? " + "and column_name = ?;";
		PreparedStatement pstmt = null;

		try {
			pstmt = connection.prepareStatement(sql);
			pstmt.setString(1, tableName);
			pstmt.setString(2, columnName);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				type = rs.getString(1);
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return type;

	}

	/*
	 * Return a list of results columns for a form
	 */
	public static ArrayList<TableColumn> getColumnsInForm(
			Connection sd, 
			Connection cResults, 
			ResourceBundle localisation,
			String language,
			int sId, 
			String surveyIdent,
			String user,
			ArrayList<Role> roles,
			int formParent, 
			int f_id, 
			String table_name, 
			boolean includeRO, 
			boolean includeParentKey,
			boolean includeBad, 
			boolean includeInstanceId, 
			boolean includePrikey,
			boolean includeOtherMeta, 
			boolean includePreloads,
			boolean includeInstanceName, 
			boolean includeSurveyDuration, 
			boolean superUser,
			boolean hxl,
			boolean audit,
			String tz,
			boolean mgmt)	// If set substitute display name for the question name if it is not null, also publish un published
					throws Exception {

		int oId = GeneralUtilityMethods.getOrganisationId(sd, user);
		ArrayList<TableColumn> columnList = new ArrayList<TableColumn>();
		ArrayList<TableColumn> realQuestions = new ArrayList<TableColumn>(); // Temporary array so that all property
		// questions can be added first
		boolean uptodateTable = false; // Set true if the results table has the latest meta data columns
		TableColumn durationColumn = null;

		// Get sensitive data restrictions
		OrganisationManager om = new OrganisationManager(localisation);
		MySensitiveData msd = om.getMySensitiveData(sd, user);
		
		// Get column restrictions for RBAC
		StringBuffer colList = new StringBuffer("");
		if (!superUser) {
			if (sId > 0) {
				RoleManager rm = new RoleManager(localisation);
				
				ArrayList<RoleColumnFilter> rcfArray = new ArrayList<> ();
				if(user != null) {
					rcfArray = rm.getSurveyColumnFilter(sd, sId, user);
				} else if(roles != null) {
					rcfArray = rm.getSurveyColumnFilterRoleList(sd, sId, roles);
				}
				
				if (rcfArray.size() > 0) {
					colList.append(" and q_id in (");
					for (int i = 0; i < rcfArray.size(); i++) {
						RoleColumnFilter rcf = rcfArray.get(i);
						if (i > 0) {
							colList.append(",");
						}
						colList.append(rcf.id);
					}
					colList.append(")");
				}
			}
		}

		// SQL to get the questions
		String sqlQuestion1 = "select qname, qtype, column_name, q_id, readonly, "
				+ "source_param, appearance, display_name, l_id, compressed " 
				+ "from question "
				+ "where f_id = ? "
				+ "and source is not null "
				+ "and published = 'true' "
				+ "and soft_deleted = 'false' ";
		
		String sqlQuestion2 = colList.toString();
		String sqlQuestion3 = "order by seq";
		PreparedStatement pstmtQuestions = sd.prepareStatement(sqlQuestion1 + sqlQuestion2 + sqlQuestion3);

		// Get column names for select multiple questions in an uncompressed legacy select multiple
		String sqlSelectMultipleNotCompressed = "select distinct o.column_name, o.ovalue, o.seq, o.display_name " 
				+ "from option o, question q "
				+ "where o.l_id = q.l_id " 
				+ "and q.q_id = ? " 
				+ "and o.externalfile = ? " 
				+ "and o.published = 'true' "
				+ "order by o.seq;";
		PreparedStatement pstmtSelectMultipleNotCompressed = sd.prepareStatement(sqlSelectMultipleNotCompressed);
				
		// Get column names for select multiple questions for compressed select multiples (ignore published)
		String sqlSelectMultiple = "select distinct o.column_name, o.ovalue, o.seq, o.display_name " 
				+ "from option o, question q "
				+ "where o.l_id = q.l_id " 
				+ "and q.q_id = ? " 
				+ "and o.externalfile = ? " 
				+ "order by o.seq;";
		PreparedStatement pstmtSelectChoices = sd.prepareStatement(sqlSelectMultiple);

		updateUnPublished(sd, cResults, table_name, f_id, true);		// Ensure that all columns marked not published really are
		
		TableColumn c = new TableColumn();
		c.column_name = "prikey";
		c.displayName = "prikey";
		c.type = SmapQuestionTypes.INT;
		c.question_name = c.column_name;
		if (includePrikey) {
			columnList.add(c);
		}
		
		// Add assigned if this is a management request
		if(mgmt) {
			if(!GeneralUtilityMethods.hasColumn(cResults, table_name, "_assigned")) {
				GeneralUtilityMethods.addColumn(cResults, table_name, "_assigned", "text");
			}
			c = new TableColumn();
			c.column_name = "_assigned";
			c.displayName = "_assigned";
			c.type = SmapQuestionTypes.STRING;
			c.question_name = c.column_name;
			columnList.add(c);
		}

		// Add HRK if it has been specified
		if (includeOtherMeta && GeneralUtilityMethods.columnType(cResults, table_name, "_hrk") != null) {
			c = new TableColumn();
			c.column_name = "_hrk";
			c.displayName = "Key";
			c.type = SmapQuestionTypes.STRING;
			c.question_name = c.column_name;
			columnList.add(c);
		}

		if (includeParentKey) {
			c = new TableColumn();
			c.column_name = "parkey";
			c.displayName = "parkey";
			c.type = SmapQuestionTypes.INT;
			c.question_name = c.column_name;
			columnList.add(c);
		}

		ArrayList<MetaItem> preloads = getPreloads(sd, sId);
		if (includeSurveyDuration && formParent == 0) {
			durationColumn = new TableColumn();
			durationColumn.column_name = "_duration";
			durationColumn.displayName = localisation.getString("a_sd");
			durationColumn.type = SmapQuestionTypes.DURATION;
			durationColumn.isMeta = true;
			getStartEndName(preloads, durationColumn);
			durationColumn.question_name = durationColumn.column_name;
			columnList.add(durationColumn);
		}

		if (includeBad) {
			c = new TableColumn();
			c.column_name = "_bad";
			c.displayName = "_bad";
			c.type = SmapQuestionTypes.BOOLEAN;
			c.question_name = c.column_name;
			columnList.add(c);

			c = new TableColumn();
			c.column_name = "_bad_reason";
			c.displayName = "_bad_reason";
			c.type = SmapQuestionTypes.STRING;
			c.question_name = c.column_name;
			columnList.add(c);
		}

		// For the top level form add default columns that are not in the question list
		if (includeOtherMeta && formParent == 0) {

			c = new TableColumn();
			c.column_name = "_user";
			c.displayName = localisation.getString("a_user");
			c.type = SmapQuestionTypes.STRING;
			c.isMeta = true;
			c.question_name = c.column_name;
			columnList.add(c);

			if (GeneralUtilityMethods.columnType(cResults, table_name, SmapServerMeta.SCHEDULED_START_NAME) != null) {
				uptodateTable = true; // This is the latest meta column that was added
			}

			if (uptodateTable || GeneralUtilityMethods.columnType(cResults, table_name, SmapServerMeta.UPLOAD_TIME_NAME) != null) {

				c = new TableColumn();
				c.column_name = SmapServerMeta.UPLOAD_TIME_NAME;
				c.displayName = localisation.getString("a_ut");
				c.type = SmapQuestionTypes.DATETIME;
				c.isMeta = true;
				c.question_name = c.column_name;
				columnList.add(c);

				c = new TableColumn();
				c.column_name = SmapServerMeta.SURVEY_ID_NAME;
				c.displayName = localisation.getString("a_name");
				c.type = "";
				c.isMeta = true;
				c.question_name = c.column_name;
				columnList.add(c);
			}

			if (uptodateTable || GeneralUtilityMethods.columnType(cResults, table_name, SmapServerMeta.SCHEDULED_START_NAME) != null) {
				c = new TableColumn();
				c.column_name = SmapServerMeta.SCHEDULED_START_NAME;
				c.displayName = SmapServerMeta.SCHEDULED_START_NAME;
				c.type = SmapQuestionTypes.DATETIME;
				c.isMeta = true;
				columnList.add(c);
			}
			
			if (uptodateTable || GeneralUtilityMethods.columnType(cResults, table_name, "_version") != null) {
				c = new TableColumn();
				c.column_name = "_version";
				c.displayName = localisation.getString("a_v");
				c.type = SmapQuestionTypes.STRING;
				c.isMeta = true;
				columnList.add(c);
			}

			if (uptodateTable || GeneralUtilityMethods.columnType(cResults, table_name, "_complete") != null) {
				c = new TableColumn();
				c.column_name = "_complete";
				c.displayName = localisation.getString("a_comp");
				c.type = SmapQuestionTypes.BOOLEAN;
				c.isMeta = true;
				columnList.add(c);
			}

			if (includeInstanceId && (uptodateTable
					|| GeneralUtilityMethods.columnType(cResults, table_name, "instanceid") != null)) {
				c = new TableColumn();
				c.column_name = "instanceid";
				c.displayName = "instanceid";
				c.type = "";
				c.isMeta = true;
				columnList.add(c);
			}

			if (uptodateTable) {
				c = new TableColumn();
				c.column_name = "_survey_notes";
				c.displayName = localisation.getString("a_sn");
				c.type = SmapQuestionTypes.STRING;
				c.isMeta = true;
				columnList.add(c);

				c = new TableColumn();
				c.column_name = "_location_trigger";
				c.displayName = localisation.getString("a_lt");
				c.type = SmapQuestionTypes.STRING;
				c.isMeta = true;
				columnList.add(c);

				c = new TableColumn();
				c.column_name = "instancename";
				c.displayName = localisation.getString("a_inst");
				c.type = SmapQuestionTypes.STRING;
				c.isMeta = true;
				columnList.add(c);
			}

			// Add preloads that have been specified in the survey definition
			if (includePreloads) {
				for(MetaItem mi : preloads) {
					if(mi.isPreload) {
						c = new TableColumn();
						c.column_name = mi.columnName;
						c.displayName = mi.name;
						c.question_name = mi.name;
						c.type = mi.dataType;
						if(c.type != null && c.type.equals("timestamp")) {
							c.type = "dateTime";
						}
						columnList.add(c);
					}
				}
			}

		}

		if (audit && GeneralUtilityMethods.columnType(cResults, table_name, "_audit") != null) {
			c = new TableColumn();
			c.column_name = "_audit";
			c.displayName = "Audit";
			c.type = SmapQuestionTypes.AUDIT;
			c.question_name = c.column_name;
			columnList.add(c);
		}


		try {
			
			pstmtQuestions.setInt(1, f_id);

			log.info("SQL: Get columns:" + pstmtQuestions.toString());
			ResultSet rsQuestions = pstmtQuestions.executeQuery();

			/*
			 * Get columns
			 */
			while (rsQuestions.next()) {

				String question_name = rsQuestions.getString(1);
				String qType = rsQuestions.getString(2);
				String question_column_name = rsQuestions.getString(3);
				int qId = rsQuestions.getInt(4);
				boolean ro = rsQuestions.getBoolean(5);
				String source_param = rsQuestions.getString(6);
				String appearance = rsQuestions.getString(7);
				String display_name = rsQuestions.getString(8);
				int l_id = rsQuestions.getInt(9);
				boolean compressed = rsQuestions.getBoolean(10);
				
				String hxlCode = getHxlCode(appearance, question_name);

				if (durationColumn != null && source_param != null) {
					if (source_param.equals("start")) {
						durationColumn.startName = question_column_name;
					} else if (source_param.equals("end")) {
						durationColumn.endName = question_column_name;
					}
				}

				String cName = question_column_name.trim().toLowerCase();
				if (cName.equals("parkey") || cName.equals("_bad") || cName.equals("_bad_reason")
						|| cName.equals("_task_key") || cName.equals("_task_replace") || cName.equals("_modified")
						|| cName.equals("_instanceid") || cName.equals("instanceid")) {
					continue;
				}

				if (cName.equals("instancename") && !includeInstanceName) {
					continue;
				}

				if (!includeRO && ro) {
					continue; // Drop read only columns if they are not selected to be exported
				}

				if (qType.equals("select") && !compressed) {		// deprecated

					// Get the choices, either all from an external file or all from an internal
					// file but not both
					pstmtSelectMultipleNotCompressed.setInt(1, qId);
					pstmtSelectMultipleNotCompressed.setBoolean(2, false);	// no external
					log.info("Get choices for select multiple question: " + pstmtSelectMultipleNotCompressed.toString());
					ResultSet rsMultiples = pstmtSelectMultipleNotCompressed.executeQuery();

					HashMap<String, String> uniqueColumns = new HashMap<String, String>();
					int multIdx = 0;
					TableColumn firstOption = null;
					while (rsMultiples.next()) {
						String uk = question_column_name + "xx" + rsMultiples.getString(2); // Column name can be
						// randomised so don't use
						// it for uniqueness

						if (uniqueColumns.get(uk) == null) {
							uniqueColumns.put(uk, uk);

							c = new TableColumn();
							
							String optionName = rsMultiples.getString(1);
							String optionLabel = rsMultiples.getString(2);
							c.column_name = question_column_name + "__" + optionName;
							c.displayName = question_name + " - " + optionLabel;
							c.option_name = rsMultiples.getString(2);
							c.question_name = question_name;
							c.l_id = l_id;
							c.qId = qId;
							c.type = qType;
							c.compressed = false;
							c.readonly = ro;
							if (hxlCode != null) {
								c.hxlCode = hxlCode + "+label";
							}

							// Add options to first column of select multiple
							if(multIdx == 0) {
								firstOption = c;
								firstOption.choices = new ArrayList<KeyValue> ();
							}
							multIdx++;
							firstOption.choices.add(new KeyValue(optionName, optionLabel));

							realQuestions.add(c);
						}
					}
				} else {
					c = new TableColumn();
					c.column_name = question_column_name;
					c.displayName = (display_name == null || display_name.trim().length() == 0) ? question_name : display_name;
					c.question_name = question_name;
					c.qId = qId;
					c.type = qType;
					c.readonly = ro;
					c.hxlCode = hxlCode;
					c.l_id = l_id;
					c.compressed = compressed;
					
					// Check for sensitive data
					if(msd.signature) {
						if(qType.equals("image") && appearance != null && appearance.contains("signature")) {
							continue;
						}
					}
					if (GeneralUtilityMethods.isPropertyType(source_param, question_column_name)) {
						if (includePreloads) {
							columnList.add(c);
						}
					} else {
						realQuestions.add(c);
					}
					
					if (qType.equals("select1") || qType.equals("select") || qType.equals("rank")) {
		
						c.choices = new ArrayList<KeyValue> ();	
						// Don't get external select 1 choices it is not necessary
						if(!qType.equals("select1") && GeneralUtilityMethods.hasExternalChoices(sd, qId)) {
							ArrayList<Option> options = GeneralUtilityMethods.getExternalChoices(sd, 
									cResults, localisation, user, oId, sId, qId, null, surveyIdent, tz);
							if(options != null) {
								for(Option o : options) {
									String label ="";
									if(o.externalLabel != null) {
										for(LanguageItem el : o.externalLabel) {
											if(el.language.equals(language)) {
												label = el.text;
											}
										}
									}
									c.choices.add(new KeyValue(o.value, label));
								}
							}
						} else {
							// Compressed select multiple or internal select1 add the options
							pstmtSelectChoices.setInt(1, qId);
							pstmtSelectChoices.setBoolean(2, false);	// No external
							ResultSet rsMultiples = pstmtSelectChoices.executeQuery();
						
							while (rsMultiples.next()) {
								// Get the choices
								String displayName = rsMultiples.getString(4);
								String choiceValue = rsMultiples.getString(2);
								if(displayName == null || displayName.trim().length() == 0) {
									displayName =choiceValue; 
								} else {
									// Record that we are using display names
									c.selectDisplayNames = true;
								}
								String optionName = choiceValue;		// Set to choice value
								String optionLabel = displayName;	// Set to choice value / display name
	
								c.choices.add(new KeyValue(optionName, optionLabel));
								
							}
						}
					} 
				}

			}
		} finally {
			try {if (pstmtQuestions != null) {pstmtQuestions.close();	}} catch (Exception e) {}
			try {if (pstmtSelectMultipleNotCompressed != null) {pstmtSelectMultipleNotCompressed.close();}} catch (Exception e) {}
			try {if (pstmtSelectChoices != null) {pstmtSelectChoices.close();}} catch (Exception e) {}
		}

		columnList.addAll(realQuestions); // Add the real questions after the property questions

		return columnList;
	}
	
	/*
	 * Get the start duration and end duration names from the preloads
	 */
	public static void getStartEndName(ArrayList<MetaItem> preloads, TableColumn durn) {
		for(MetaItem mi : preloads) {
			if(mi.isPreload) {
				if(mi.sourceParam != null) {
					if(mi.sourceParam.equals("start")) {
						durn.startName = mi.columnName;
					} else if(mi.sourceParam.equals("end")) {
						durn.endName = mi.columnName;
					}
				}
			}
		}
	}

	/*
	 * Return a list of choices by list id in a survey
	 */
	public static ArrayList<ChoiceList> getChoicesInForm(Connection sd, int sId, int f_id) throws SQLException {

		ArrayList<ChoiceList> choiceLists = new ArrayList<ChoiceList>();

		// SQL to get the default language
		String sqlGetDefLang = "select def_lang from survey where s_id = ?";
		PreparedStatement pstmtDefLang = sd.prepareStatement(sqlGetDefLang);

		// SQL to get the choices for a survey
		String sqlGetChoices = "select o.l_id, " + "o.ovalue as value, " + "t.value, " + "t.language "
				+ "from option o, translation t, survey s " + "where s.s_id = ? " + "and s.s_id = t.s_id "
				+ "and o.l_id in (select l_id from listname where s_id = ?) " + "and o.label_id = t.text_id ";

		String sqlGetChoices2 = "and t.language = ? ";
		String sqlGetChoices3 = "order by o.l_id, o.seq";
		PreparedStatement pstmtChoices = null;
		try {

			// Get the default lang
			pstmtDefLang.setInt(1, sId);
			ResultSet rsDefLang = pstmtDefLang.executeQuery();
			String defLang = null;
			if (rsDefLang.next()) {
				defLang = rsDefLang.getString(1);
			}

			if (defLang == null) {
				pstmtChoices = sd.prepareStatement(sqlGetChoices + sqlGetChoices3);
			} else {
				pstmtChoices = sd.prepareStatement(sqlGetChoices + sqlGetChoices2 + sqlGetChoices3);
			}
			pstmtChoices.setInt(1, sId);
			pstmtChoices.setInt(2, sId);
			if (defLang != null) {
				pstmtChoices.setString(3, defLang);
			}

			log.info("SQL: Get choices:" + pstmtChoices.toString());
			ResultSet rsChoices = pstmtChoices.executeQuery();

			/*
			 * Get columns
			 */
			int currentList = 0;
			String firstLang = null;
			ChoiceList cl = null;
			while (rsChoices.next()) {
				int l_id = rsChoices.getInt(1);
				String name = rsChoices.getString(2);
				String label = rsChoices.getString(3);
				String language = rsChoices.getString(4);

				if (defLang == null) {
					if (firstLang == null) {
						firstLang = language;
					} else if (!firstLang.equals(language)) {
						continue; // Only want one language
					}
				}

				if (l_id != currentList) {
					cl = new ChoiceList(l_id);
					choiceLists.add(cl);
					currentList = l_id;
				}

				cl.choices.add(new KeyValueSimp(name, label));

			}
		} finally {
			try {
				if (pstmtDefLang != null) {
					pstmtDefLang.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtChoices != null) {
					pstmtChoices.close();
				}
			} catch (SQLException e) {
			}
		}

		return choiceLists;
	}

	/*
	 * Get the Hxl Code from an appearance value and the question name
	 */
	public static String getHxlCode(String appearance, String name) {
		String hxlCode = null;
		if (appearance != null) {
			String appValues[] = appearance.split(" ");
			for (int i = 0; i < appValues.length; i++) {
				if (appValues[i].startsWith("#")) {
					hxlCode = appValues[i].trim();
					break;
				}
			}
		}

		if (hxlCode == null) {
			// TODO try to get hxl code from defaut column name
		}
		return hxlCode;
	}

	/*
	 * Return true if this question is a property type question like deviceid
	 */
	public static boolean isPropertyType(String source_param, String name) {

		boolean isProperty = false;

		if (source_param != null && (source_param.equals("deviceid") || source_param.equals("phonenumber")
				|| source_param.equals("simserial") || source_param.equals("subscriberid")
				|| source_param.equals("today") || source_param.equals("start") || source_param.equals("end"))) {

			isProperty = true;

		} else if (name != null) {
			name = name.toLowerCase();
			if(name.equals("_instanceid") || name.equals("meta") || name.equals("instanceid")
					|| name.equals("instancename") || name.equals("meta_groupend") || name.equals("_task_key")) {

				isProperty = true;
			}

		} 

		return isProperty;
	}

	/*
	 * Returns the SQL fragment that makes up the date range restriction
	 */
	public static String getDateRange(Date startDate, Date endDate, String dateName) {
		String sqlFrag = "";
		boolean needAnd = false;

		if (startDate != null) {
			sqlFrag += dateName + " >= ? ";
			needAnd = true;
		}
		if (endDate != null) {
			if (needAnd) {
				sqlFrag += "and ";
			}
			sqlFrag += dateName + " < ? ";
		}

		return sqlFrag;
	}

	/*
	 * Add the filename to the response
	 */
	public static void setFilenameInResponse(String filename, HttpServletResponse response) {

		String escapedFileName = null;

		log.info("Setting filename in response: " + filename);
		if (filename == null) {
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

		response.setHeader("Content-Disposition", "attachment; filename=\"" + escapedFileName + "\"");
		response.setStatus(HttpServletResponse.SC_OK);
	}

	/*
	 * Get the choice filter from an xform nodeset
	 */
	public static String getChoiceFilterFromNodeset(String nodeset, boolean xlsName) {

		StringBuffer choice_filter = new StringBuffer("");
		String[] filterParts = null;

		if (nodeset != null) {
			int idx = nodeset.indexOf('[');
			int idx2 = nodeset.indexOf(']');
			if (idx > -1 && idx2 > idx) {
				filterParts = nodeset.substring(idx + 1, idx2).trim().split("\\s+");
				for (int i = 0; i < filterParts.length; i++) {
					if (filterParts[i].startsWith("/")) {
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
	 * Get the nodeset from a choice filter
	 */
	public static String getNodesetFromChoiceFilter(String choice_filter, String listName) {

		StringBuffer nodeset = new StringBuffer("");

		nodeset.append("instance('");
		nodeset.append(listName);
		nodeset.append("')");
		nodeset.append("/root/item");
		if (choice_filter != null && choice_filter.trim().length() > 0) {
			nodeset.append("[");
			nodeset.append(choice_filter);
			nodeset.append("]");
		}

		return nodeset.toString().trim();

	}

	/*
	 * Convert all xml fragments embedded in the supplied string to names ODK uses
	 * an html fragment <output/> to show values from questions in labels
	 */
	public static String convertAllEmbeddedOutput(String inputEsc, boolean xlsName) {

		StringBuffer output = new StringBuffer("");
		int idx = 0;
		String input = unesc(inputEsc);

		if (input != null) {

			while (idx >= 0) {

				idx = input.indexOf("<output");

				if (idx >= 0) {
					output.append(input.substring(0, idx));
					input = input.substring(idx + 1);
					idx = input.indexOf(">");
					if (idx >= 0) {
						String outputTxt = input.substring(0, idx + 1);
						input = input.substring(idx + 1);
						String[] parts = outputTxt.split("\\s+");
						for (int i = 0; i < parts.length; i++) {
							if (parts[i].startsWith("/")) {
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
		String[] parts = null;

		if (input != null) {

			parts = input.trim().split("\\s+");
			for (int i = 0; i < parts.length; i++) {
				if (parts[i].startsWith("/") && notInQuotes(output)) {
					output.append(xpathNameToName(parts[i], xlsName).trim() + " ");
				} else {
					output.append(parts[i].trim() + " ");
				}

			}
		}

		return output.toString().trim();
	}

	/*
	 * Convert all xPath labels in the supplied string to names Xpaths in labels are
	 * embedded in <output/> elements
	 */
	public static String convertAllXpathLabels(String input, boolean xlsName) {
		StringBuffer output = new StringBuffer("");

		if (input != null) {

			int idx = input.indexOf("<output");
			while (idx >= 0) {

				output.append(input.substring(0, idx));

				int idx2 = input.indexOf('/', idx + 1);
				int idx3 = input.indexOf('"', idx2 + 1);
				if (idx2 >= 0 && idx3 >= 0) {
					String elem = input.substring(idx2, idx3).trim();
					output.append(xpathNameToName(elem, xlsName).trim());
					int idx4 = input.indexOf('>', idx3) + 1;
					if (idx4 >= 0) {
						input = input.substring(idx4);
					} else {
						output.append("error in idx 4");
						break;
					}
				} else {
					output.append("error: idx 2:3:" + idx2 + " : " + idx3);
					break;
				}
				idx = input.indexOf("<output");
			}

			output.append(input);
		}

		return output.toString().trim();
	}

	/*
	 * Convert an xpath name to just a name
	 */
	public static String xpathNameToName(String xpath, boolean xlsName) {
		String name = null;

		int idx = xpath.lastIndexOf("/");
		if (idx >= 0) {
			name = xpath.substring(idx + 1);
			if (xlsName) {
				name = "${" + name + "}";
			}
		}
		return name;
	}

	/*
	 * Convert names in xls format ${ } to an SQL query
	 */
	public static String convertAllxlsNamesToQuery(String input, int sId, Connection sd) throws SQLException {

		if (input == null) {
			return null;
		} else if (input.trim().length() == 0) {
			return null;
		}

		StringBuffer output = new StringBuffer("");
		String item;

		Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {

			String matched = matcher.group();
			String qname = matched.substring(2, matched.length() - 1);

			// Add any text before the match
			int startOfGroup = matcher.start();
			item = input.substring(start, startOfGroup).trim();
			convertSqlFragToHrkElement(item, output);

			// Add the column name
			if (output.length() > 0) {
				output.append(" || ");
			}
			String columnName = getColumnName(sd, sId, qname);
			if (columnName == null && (qname.equals("prikey") || qname.equals("_start")
					|| qname.equals(SmapServerMeta.UPLOAD_TIME_NAME)
					|| qname.equals(SmapServerMeta.SCHEDULED_START_NAME)
					|| qname.equals("_end") || qname.equals("device") || qname.equals("instancename"))) {
				columnName = qname;
			}
			output.append(columnName);

			// Reset the start
			start = matcher.end();
		}

		// Get the remainder of the string
		if (start < input.length()) {
			item = input.substring(start).trim();
			convertSqlFragToHrkElement(item, output);
		}

		return output.toString().trim();

	}

	/*
	 * Where an expression is validated as a good xpath expression then the xls names need to be converted
	 * to xpaths.  However it is not necessary to create the full xpath which may not be available hence a
	 * pseudo xpath of /name is created
	 */
	public static String convertAllxlsNamesToPseudoXPath(String input)  {

		if (input == null) {
			return null;
		} else if (input.trim().length() == 0) {
			return null;
		}

		StringBuffer output = new StringBuffer("");

		Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {

			String matched = matcher.group();
			String qname = matched.substring(2, matched.length() - 1);

			// Add any text before the match
			int startOfGroup = matcher.start();
			output.append(input.substring(start, startOfGroup));				

			// Add the question name
			output.append("/").append(qname);

			// Reset the start
			start = matcher.end();

		}

		// Get the remainder of the string
		if (start < input.length()) {
			output.append(input.substring(start));	
		}

		return output.toString().trim();
	}

	/*
	 * Add a component that is not a data
	 */
	private static void convertSqlFragToHrkElement(String item, StringBuffer output) {

		if (item.length() > 0) {
			if (output.length() > 0) {
				output.append(" || ");
			}
			if (item.contains("serial(")) {
				int idx0 = item.indexOf("serial(");
				int idx1 = item.indexOf('(');
				int idx2 = item.indexOf(')');

				if (idx0 > 0) {
					String initialText = item.substring(0, idx0);
					output.append('\'');
					initialText = initialText.replaceAll("'", "''"); // escape quotes
					output.append(initialText);
					output.append('\'');
					output.append(" || ");
				}
				if (idx2 > idx1) {
					String offset = item.substring(idx1 + 1, idx2);
					if (offset.trim().length() > 0) {
						try {
							Integer.valueOf(offset);
							output.append("prikey + " + offset);
						} catch (Exception e) {
							log.info("Error parsing HRK item: " + item);
							output.append("prikey");
						}
					} else {
						output.append("prikey");
					}
				} else {
					log.info("Error parsing HRK item: " + item);
				}

				if (idx2 + 1 < item.length()) {
					output.append(" || ");
					String finalText = item.substring(idx2 + 1);
					output.append('\'');
					finalText = finalText.replaceAll("'", "''"); // escape quotes
					output.append(finalText);
					output.append('\'');

				}
			} else {
				output.append('\'');
				item = item.replaceAll("'", "''"); // escape quotes
				output.append(item);
				output.append('\'');
			}
		}

	}

	/*
	 * Translate a question type from its representation in the database to the
	 * survey model used for editing
	 */
	public static String translateTypeFromDB(String in, boolean readonly, boolean visible) {

		String out = in;

		if (in.equals("string") && !visible) {
			out = "calculate";
		} 

		return out;

	}

	/*
	 * Translate a question type from its representation in the survey model to the
	 * database
	 */
	public static String translateTypeToDB(String in, String name) {

		String out = in;

		if (in.equals("begin repeat") && name.startsWith("geopolygon")) {
			out = "geopolygon";
		} else if (in.equals("begin repeat") && name.startsWith("geolinestring")) {
			out = "geolinestring";
		}

		return out;

	}

	/*
	 * Return true if a question type is a geometry
	 */
	public static boolean isGeometry(String qType) {
		boolean isGeom = false;
		if (qType.equals("geopoint") || qType.equals("geopolygon") || qType.equals("geolinestring")
				|| qType.equals("geotrace") || qType.equals("geoshape")) {

			isGeom = true;
		}
		return isGeom;
	}

	/*
	 * Get the readonly value for a question as stored in the database
	 */
	public static boolean translateReadonlyToDB(String type, boolean in) {

		boolean out = in;

		if (type.equals("note")) {
			out = true;
		}

		return out;

	}

	// Get the timestamp
	public static Timestamp getTimeStamp() {

		java.util.Date today = new java.util.Date();
		return new Timestamp(today.getTime());

	}

	/*
	 * Check to see if there are any choices from an external file for a question
	 */
	public static boolean hasExternalChoices(Connection sd, int qId) throws SQLException {
		
		boolean external = false;
		PreparedStatement pstmt = null;
		String sql = "select q.appearance from question q "
				+ "where q.q_id = ? "
				+ "and q.appearance like '%search(%'";

		try {
			pstmt = sd.prepareStatement(sql);
				
			pstmt.setInt(1, qId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {			
				external = true;
			}	

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return external;
	}

	/*
	 * Convert a question name to a question id
	 */
	public static int getQuestionIdFromName(Connection sd, int sId, String name) throws SQLException {

		String sql = "select q_id " 
				+ "from question q " 
				+ "where q.qname = ? "
				+ "and q.f_id in (select f_id from form where s_id = ?)";

		int qId = 0;
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setInt(2, sId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				qId = rs.getInt(1);
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return qId;
	}
	
	/*
	 * Convert a question name to a display name
	 */
	public static String getDisplayName(Connection sd, int sId, String name) throws SQLException {

		String sql = "select display_name " 
				+ "from question q " 
				+ "where q.qname = ? "
				+ "and q.f_id in (select f_id from form where s_id = ?)";

		String display_name = null;
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setInt(2, sId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				display_name = rs.getString(1);
			} 

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		if(display_name == null) {
			display_name = name;
		}
		
		return display_name;
	}
	
	/*
	 * Convert a question id to a question name
	 */
	public static String getQuestionNameFromId(Connection sd, int sId, int id) throws SQLException {

		String sql = "select q.qname " 
				+ "from question q " 
				+ "where q.q_id = ? "
				+ "and q.f_id in (select f_id from form where s_id = ?)";

		String qName = null;
		PreparedStatement pstmt = null;

		try {
			if(id >= 0) {
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, id);
				pstmt.setInt(2, sId);
				ResultSet rs = pstmt.executeQuery();
				if (rs.next()) {
					qName = rs.getString(1);
				}
			} else {
				if(id == SmapServerMeta.UPLOAD_TIME_ID) {
					qName = SmapServerMeta.UPLOAD_TIME_NAME;
				} else if(id == SmapServerMeta.SCHEDULED_START_ID) {
					qName = SmapServerMeta.SCHEDULED_START_NAME;
				} else {
					ArrayList<MetaItem> preloads = getPreloads(sd, sId);
					for(MetaItem mi : preloads) {
						if(mi.id == id) {
							qName = mi.name;
							break;
						}
					}
					
				}
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return qName;
	}

	/*
	 * Check to see if there are any choices from an external file for a question
	 */
	public static boolean listHasExternalChoices(Connection sd, int sId, int listId) throws SQLException {

		boolean hasExternal = false;
		// String sql = "select count(*) from option o where o.l_id = ? and o.externalfile = 'true';";
		PreparedStatement pstmt = null;
		String sql = "select q.appearance from question q, form f "
				+ "where f.s_id = ? "
				+ "and f.f_id = q.f_id "
				+ "and q.l_id = ? "
				+ "and q.appearance like '%search(%'";
		try {
			pstmt = sd.prepareStatement(sql);
			
			pstmt.setInt(1, sId);
			pstmt.setInt(2, listId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {			
				hasExternal = true;
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return hasExternal;
	}
	
	/*
	 * Get choices from an external file
	 */
	public static ArrayList<Option> getExternalChoices(
			Connection sd, 
			Connection cResults,
			ResourceBundle localisation, 
			String remoteUser,
			int oId, 
			int sId, 
			int qId, 
			ArrayList<String> matches,
			String surveyIdent,
			String tz) throws Exception {

		ArrayList<Option> choices = new ArrayList<Option> ();		
		String sql = "select q.external_table, q.l_id from question q where q.q_id = ?";
		PreparedStatement pstmt = null;
		
		String sqlChoices = "select ovalue, label_id, column_name "
				+ "from option "
				+ "where l_id = ? "
				+ "and not externalfile "
				+ "and ovalue !~ '^[0-9]+$'";
		PreparedStatement pstmtChoices = null;
			
		String sqlLabels = "select t.value, t.language " 
						+ "from translation t "
						+ "where t.text_id = ? "
						+ "and t.type = 'none' "
						+ "and t.s_id = ? "
						+ "order by t.language asc";
		PreparedStatement pstmtLabels = null;
		
		try {
			String filename = null;
			
			pstmt = sd.prepareStatement(sql);			
			pstmt.setInt(1, qId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {		
				
				filename = rs.getString(1);
				int l_id = rs.getInt(2);
				
				if(filename != null) {
					
					pstmtChoices = sd.prepareStatement(sqlChoices);
					pstmtChoices.setInt(1, l_id);
					log.info("Get choices: " + pstmtChoices.toString());
					ResultSet rsChoices = pstmtChoices.executeQuery();
					if(rsChoices.next()) {
						String ovalue = rsChoices.getString(1);
						String oLabelId = rsChoices.getString(2);
						String oColumn = rsChoices.getString(3);
						
						pstmtLabels = sd.prepareCall(sqlLabels);
						pstmtLabels.setString(1, oLabelId);
						pstmtLabels.setInt(2,  sId);
						log.info(pstmtLabels.toString());
						ResultSet rsLabels = pstmtLabels.executeQuery();
						ArrayList<LanguageItem> languageItems = new ArrayList<LanguageItem> ();
						while(rsLabels.next()) {
							String label = rsLabels.getString(1);
							String language = rsLabels.getString(2);
							languageItems.add(new LanguageItem(language, label));
						}
						
						if(languageItems.size() > 0) {
							if(filename.startsWith("linked_s")) {
								if(filename.equals("linked_self")) {
									filename = "linked_" + surveyIdent;
								}
								
								ArrayList<String> matchCols = null; 
								String selection = null;
								if(matches != null && matches.size() == 1) {
									// Match on ovalue
									matchCols = new ArrayList<> ();
									matchCols.add(ovalue);
									
									selection = oColumn + " = ?";
								}
								// Get data from another form
								SurveyTableManager stm = new SurveyTableManager(sd, cResults, localisation, oId, sId, filename, remoteUser);
								stm.initData(pstmt, "choices", null, null, selection, matches, matchCols, tz);
								
								Option o = null;
								while((o = stm.getLineAsOption(ovalue, languageItems)) != null) {
									choices.add(o);
								}
								
							} else {
								// Get data from a csv table
								CsvTableManager csvMgr = new CsvTableManager(sd, localisation);
								choices = csvMgr.getChoices(oId, sId, filename, ovalue, languageItems, matches);
							}
						}
					}
					
				} 
			}

		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtChoices != null) {pstmtChoices.close();}} catch (SQLException e) {}
			try {if (pstmtLabels != null) {pstmtLabels.close();}} catch (SQLException e) {}
		}

		return choices;
	}

	/*
	 * Re-sequence options starting from 0
	 */
	public static void cleanOptionSequences(Connection sd, int listId) throws SQLException {

		String sql = "select o_id, seq from option where l_id = ? order by seq asc;";
		PreparedStatement pstmt = null;

		String sqlUpdate = "update option set seq = ? where o_id = ?;";
		PreparedStatement pstmtUpdate = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, listId);

			pstmtUpdate = sd.prepareStatement(sqlUpdate);

			ResultSet rs = pstmt.executeQuery();
			int newSeq = 0;
			while (rs.next()) {
				int oId = rs.getInt(1);
				int seq = rs.getInt(2);
				if (seq != newSeq) {
					pstmtUpdate.setInt(1, newSeq);
					pstmtUpdate.setInt(2, oId);

					log.info("Updating sequence for list id: " + listId + " : " + pstmtUpdate.toString());
					pstmtUpdate.execute();
				}
				newSeq++;
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtUpdate != null) {
					pstmtUpdate.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Re-sequence questions starting from 0
	 */
	public static void cleanQuestionSequences(Connection sd, int fId) throws SQLException {

		String sql = "select q_id, seq, qname from question where f_id = ? order by seq asc;";
		PreparedStatement pstmt = null;

		String sqlUpdate = "update question set seq = ? where q_id = ?;";
		PreparedStatement pstmtUpdate = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, fId);

			pstmtUpdate = sd.prepareStatement(sqlUpdate);

			ResultSet rs = pstmt.executeQuery();
			int newSeq = 0;
			while (rs.next()) {
				int qId = rs.getInt(1);
				int seq = rs.getInt(2);
				String qname = rs.getString(3);

				// Once we reach the meta group ensure their sequence remains well after any
				// other questions
				if (qname.equals("meta")) {
					newSeq += 5000;
				}

				if (seq != newSeq) {
					pstmtUpdate.setInt(1, newSeq);
					pstmtUpdate.setInt(2, qId);

					log.info("Updating question sequence for form id: " + fId + " : " + pstmtUpdate.toString());
					pstmtUpdate.execute();
				}
				newSeq++;
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmtUpdate != null) {
					pstmtUpdate.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Get the list name from the list id
	 */
	public static String getListName(Connection sd, int l_id) throws SQLException {

		String listName = null;
		String sql = "select name " + "from listname l " + "where l.l_id = ?";

		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, l_id);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				listName = rs.getString(1);
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return listName;

	}

	/*
	 * Get the list name from the question id
	 */
	public static String getListNameForQuestion(Connection sd, int qId) throws SQLException {

		String listName = null;
		String sql = "select l.name " + "from listname l, question q " + "where q.l_id = l.l_id " + "and q.q_id = ?";

		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, qId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				listName = rs.getString(1);
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return listName;

	}

	/*
	 * Get the question name from the question id
	 */
	public static String getNameForQuestion(Connection sd, int qId) throws SQLException {

		String name = null;
		String sql = "select qname "
				+ "from question " 
				+ "where q_id = ?";

		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, qId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				name = rs.getString(1);
			}

		}  finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}

		return name;

	}

	/*
	 * Get the id from the list name and survey Id If the list does not exist then
	 * create it
	 */
	public static int getListId(Connection sd, int sId, String name) throws SQLException {
		int listId = 0;

		// I don't think we need to clean the list name
		// String cleanName = GeneralUtilityMethods.cleanName(name, true, false, false);
		PreparedStatement pstmtGetListId = null;
		String sqlGetListId = "select l_id from listname where s_id = ? and name = ?;";

		PreparedStatement pstmtListName = null;
		String sqlListName = "insert into listname (s_id, name) values (?, ?);";

		try {
			pstmtGetListId = sd.prepareStatement(sqlGetListId);
			pstmtGetListId.setInt(1, sId);
			pstmtGetListId.setString(2, name);

			log.info("SQL: Get list id: " + pstmtGetListId.toString());
			ResultSet rs = pstmtGetListId.executeQuery();
			if (rs.next()) {
				listId = rs.getInt(1);
			} else { // Create listname

				pstmtListName = sd.prepareStatement(sqlListName, Statement.RETURN_GENERATED_KEYS);
				pstmtListName.setInt(1, sId);
				pstmtListName.setString(2, name);

				log.info("SQL: Create list name: " + pstmtListName.toString());

				pstmtListName.executeUpdate();

				rs = pstmtListName.getGeneratedKeys();
				rs.next();
				listId = rs.getInt(1);

			}
		} finally {
			try {if (pstmtGetListId != null) {pstmtGetListId.close();}} catch (SQLException e) {}
			try {if (pstmtListName != null) {pstmtListName.close();}} catch (SQLException e) {}
		}

		return listId;
	}

	/*
	 * Get manifest parameters from appearance or calculations
	 *  replaceSelf is set to True for embedding itemsets in webforms
	 */
	public static ArrayList<String> getManifestParams(Connection sd, int qId, String property, String filename,
			boolean isAppearance, String sIdent) throws SQLException {
		ArrayList<String> params = null;

		PreparedStatement pstmt = null;
		String sql = "SELECT o.ovalue, t.value " 
				+ "from option o, translation t, question q "
				+ "where o.label_id = t.text_id " 
				+ "and o.l_id = q.l_id " 
				+ "and q.q_id = ? "
				+ "and externalfile ='false';";

		try {
			pstmt = sd.prepareStatement(sql);

			// Check to see if this appearance references a manifest file
			if (property != null && (property.contains("search(") || property.contains("pulldata(") 
					|| property.contains("lookup(") || property.contains("lookup_choices("))) {
				// Yes it references a manifest

				int idx1 = property.indexOf('(');
				int idx2 = property.indexOf(')');
				if (idx1 > 0 && idx2 > idx1) {
					String criteriaString = property.substring(idx1 + 1, idx2);

					String criteria[] = criteriaString.split(",");
					if (criteria.length > 0) {

						if (criteria[0] != null && criteria[0].length() > 2) { // allow for quotes
							String appFilename = criteria[0].trim();

							appFilename = appFilename.substring(1, appFilename.length() - 1);
							if (appFilename.endsWith("self")) {
								appFilename = appFilename.replace("self", sIdent);
							}
							if(appFilename.startsWith("chart_s")) {
								// Add key
								appFilename += "_";
								appFilename += GeneralUtilityMethods.getKeyQuestionPulldata(criteria);
							}
							if (filename.equals(appFilename)) { // We want this one
								//log.info("We have found a manifest link to " + filename);

								if (isAppearance) {

									params = getRefQuestionsSearch(criteria);

									// Need to get columns from choices
									pstmt.setInt(1, qId);
									log.info("Getting search columns from choices: " + pstmt.toString());
									ResultSet rs = pstmt.executeQuery();
									while (rs.next()) {
										if (params == null) {
											params = new ArrayList<String>();
										}
										params.add(rs.getString(1));
										String label = rs.getString(2);
										if(label != null) {
											String [] multLabels = label.split(",");
											for(String v : multLabels) {
												params.add(v.trim());
											}
										}
									}
								} else {
									params = getRefQuestionsPulldata(criteria);
								}

							}

						}
					}
				}
			}
		} finally {
			if (pstmt != null) try {pstmt.close();	} catch (Exception e) {}
		}

		return params;
	}

	/*
	 * Add to a survey level manifest String, a manifest from an appearance
	 * attribute
	 */
	public static ManifestInfo addManifestFromAppearance(String appearance, String inputManifest) {

		ManifestInfo mi = new ManifestInfo();
		String manifestType = null;

		mi.manifest = inputManifest;
		mi.changed = false;

		// Check to see if this appearance references a manifest file
		if (appearance != null && appearance.toLowerCase().trim().contains("search(")) {
			// Yes it references a manifest

			int idx1 = appearance.indexOf('(');
			int idx2 = appearance.indexOf(')');
			if (idx1 > 0 && idx2 > idx1) {
				String criteriaString = appearance.substring(idx1 + 1, idx2);

				String criteria[] = criteriaString.split(",");
				if (criteria.length > 0) {

					if (criteria[0] != null && criteria[0].length() > 2) { // allow for quotes
						String filename = criteria[0].trim();
						filename = filename.substring(1, filename.length() - 1);

						if (filename.startsWith("linked_s")) { // Linked survey
							manifestType = "linked";
						} else {
							filename += ".csv";
							manifestType = "csv";
						}

						updateManifest(mi, filename, manifestType);

					}
				}
			}
		}
		return mi;

	}

	/*
	 * Add a survey level manifest such as a csv file from an calculate attribute
	 */
	public static ManifestInfo addManifestFromCalculate(String calculate, String inputManifest) {

		ManifestInfo mi = new ManifestInfo();
		String manifestType = null;

		mi.manifest = inputManifest;
		mi.changed = false;

		// Check to see if this calculate references a manifest file
		if (calculate != null && calculate.toLowerCase().trim().contains("pulldata(")) {

			// Yes it references a manifest
			// Get all the pulldata functions from this calculate

			int idx1 = calculate.indexOf("pulldata");
			while (idx1 >= 0) {
				idx1 = calculate.indexOf('(', idx1);
				int idx2 = calculate.indexOf(')', idx1);
				if (idx1 >= 0 && idx2 > idx1) {
					String criteriaString = calculate.substring(idx1 + 1, idx2);

					String criteria[] = criteriaString.split(",");

					if (criteria.length > 0) {

						if (criteria[0] != null && criteria[0].length() > 2) { // allow for quotes
							String filename = criteria[0].trim();
							filename = filename.substring(1, filename.length() - 1);

							if (filename.startsWith("linked_s")) { // Linked
								manifestType = "linked";
							} else if (filename.startsWith("chart_s")) { // Linked chart type data
								
								manifestType = "linked";
								filename += "_" + getKeyQuestionPulldata(criteria);		// Each key needs its own file
							} else {
								filename += ".csv";
								manifestType = "csv";
							}

							updateManifest(mi, filename, manifestType);
						}
					}
					idx1 = calculate.indexOf("pulldata(", idx2);
				}
			}
		}

		return mi;

	}

	/*
	 * Update the manifest
	 */
	private static void updateManifest(ManifestInfo mi, String filename, String manifestType) {

		String inputManifest = mi.manifest;
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		ArrayList<String> mArray = null;
		if (inputManifest == null) {
			mArray = new ArrayList<String>();
		} else {
			Type type = new TypeToken<ArrayList<String>>() {}.getType();
			mArray = gson.fromJson(inputManifest, type);
		}
		if (!mArray.contains(filename)) {
			mArray.add(filename);
			mi.changed = true;
			mi.filename = filename;
		}

		mi.manifest = gson.toJson(mArray);

	}

	/*
	 * Update the form dependencies table from the survey manifest
	 */
	public static void updateFormDependencies(Connection sd, int sId) throws SQLException {

		String sql = "select manifest from survey where s_id = ? and manifest is not null; ";
		PreparedStatement pstmt = null;

		String sqlDel = "delete from form_dependencies where linker_s_id = ?";
		PreparedStatement pstmtDel = null;

		String sqlIns = "insert into form_dependencies (linker_s_id, linked_s_id) values (?, ?)";
		PreparedStatement pstmtIns = null;

		try {

			ResultSet rs = null;

			pstmtDel = sd.prepareStatement(sqlDel);
			pstmtDel.setInt(1, sId);
			pstmtIns = sd.prepareStatement(sqlIns);
			pstmtIns.setInt(1, sId);

			/*
			 * Get Survey Level manifests from survey table
			 */
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("SQL survey level manifests:" + pstmt.toString());

			rs = pstmt.executeQuery();
			if (rs.next()) {
				String manifestString = rs.getString(1);
				Type type = new TypeToken<ArrayList<String>>() {
				}.getType();
				ArrayList<String> manifestList = new Gson().fromJson(manifestString, type);

				HashMap<Integer, Integer> linkedSurveys = new HashMap<Integer, Integer>();
				for (int i = 0; i < manifestList.size(); i++) {
					int linked_sId = 0;
					String fileName = manifestList.get(i);

					log.info("Linked file name: " + fileName);
					if (fileName.equals("linked_self")) {
						linked_sId = sId;
					} else if (fileName.equals("linked_s_pd_self")) {
						linked_sId = sId;
					} else if (fileName.startsWith("chart_self")) {
						linked_sId = sId;
					} else if (fileName.startsWith("linked_s")) {
						String ident = fileName.substring(fileName.indexOf("s"));
						log.info("Linked Survey Ident: " + ident);
						linked_sId = getSurveyId(sd, ident);
					} else if (fileName.startsWith("chart_s")) {
						String ident = fileName.substring(fileName.indexOf("s"), fileName.lastIndexOf('_'));
						log.info("Chart Survey Ident: " + ident);
						linked_sId = getSurveyId(sd, ident);
					}

					if (linked_sId > 0) {
						linkedSurveys.put(linked_sId, linked_sId);
					}
				}

				// Delete old entries for this survey if they exist
				pstmtDel.executeUpdate();

				// Add new entries
				for (int linked : linkedSurveys.keySet()) {
					pstmtIns.setInt(2, linked);
					log.info("Write form dependency: " + pstmtIns.toString());
					pstmtIns.executeUpdate();
				}
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			}
			if (pstmtDel != null) {
				try {
					pstmtDel.close();
				} catch (SQLException e) {
				}
			}
			if (pstmtIns != null) {
				try {
					pstmtIns.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	/*
	 * Get the questions referenced by a search function in a linked survey
	 */
	private static ArrayList<String> getRefQuestionsSearch(String[] params) {
		ArrayList<String> refQuestions = new ArrayList<String>();
		String param = null;

		/*
		 * The number of parameters can vary from 1 to 6 params[0] is the primary
		 * function: "search" params[1] is the matching function, ie 'matches' params[2]
		 * is a question name (Get this one) params[3] is a value for the question in
		 * param[2] params[4] is the filter column name (Get this one) params[5] is the
		 * filter value
		 * 
		 */
		if (params.length > 2) {
			param = params[2].trim();
			param = param.substring(1, param.length() - 1); // Remove quotes
			refQuestions.add(param);
		}
		if (params.length > 4) {
			param = params[4].trim();
			param = param.substring(1, param.length() - 1); // Remove quotes
			refQuestions.add(param);
		}
		return refQuestions;
	}
	
	/*
	 * Get the question that is used as a key in a pulldata chart function embedded in a calculate
	 */
	private static String getKeyQuestionCalculate(String calculate) {
		
		String key = null;
		
		if (calculate != null && calculate.toLowerCase().trim().contains("pulldata(")) {

			// Yes it references a manifest
			// Get first pulldata functions from this calculate - Assume only one pulldata containing time series

			int idx1 = calculate.indexOf("pulldata");
			while (idx1 >= 0) {
				idx1 = calculate.indexOf('(', idx1);
				int idx2 = calculate.indexOf(')', idx1);
				if (idx1 >= 0 && idx2 > idx1) {
					String criteriaString = calculate.substring(idx1 + 1, idx2);

					String criteria[] = criteriaString.split(",");

					if (criteria.length > 0) {

						if (criteria[0] != null && criteria[0].length() > 2) { // allow for quotes
							String filename = criteria[0].trim();
							filename = filename.substring(1, filename.length() - 1);

							if (filename.startsWith("chart_s")) { // Linked chart type data
								
								key = getKeyQuestionPulldata(criteria);		// Each key needs its own file
								break;
							}
						}
					}
					idx1 = calculate.indexOf("pulldata(", idx2);
				}
			}
		}
		return key;
	}
	
	/*
	 * Get the question that is used as a key when retrieving data for charts
	 */
	private static String getKeyQuestionPulldata(String[] params) {
		String param = null;

		/*
		 * pulldata('chart_self', 'data_column', 'key_column', keyvalue)
		 * The key column is the thid one
		 * 
		 */
		if (params.length > 2) {
			param = params[2].trim();
			param = param.substring(1, param.length() - 1); // Remove quotes
		}
	
		return param;
	}


	/*
	 * Get the questions referenced by a pulldata function in a linked survey
	 */
	private static ArrayList<String> getRefQuestionsPulldata(String[] params) {
		ArrayList<String> refQuestions = new ArrayList<String>();
		String param = null;

		/*
		 * The number of parameters are 4 params[0] is the primary function "pulldata"
		 * params[1] is the data column (Get this one) params[2] is the key column (Get
		 * this one) params[3] is the key value
		 * 
		 */
		if (params.length > 1) {
			param = params[1].trim();
			param = param.substring(1, param.length() - 1); // Remove quotes
			refQuestions.add(param);
		}
		if (params.length > 2) {
			param = params[2].trim();
			param = param.substring(1, param.length() - 1); // Remove quotes
			refQuestions.add(param);
		}
		return refQuestions;
	}

	/*
	 * Get the surveys that link to the provided survey
	 */
	public static ArrayList<SurveyLinkDetails> getLinkingSurveys(Connection sd, int sId) {

		ArrayList<SurveyLinkDetails> sList = new ArrayList<SurveyLinkDetails>();

		String sql = "select q.q_id, f.f_id, s.s_id, linked_target " + "from question q, form f, survey s "
				+ "where q.f_id = f.f_id " + "and f.s_id = s.s_id " + "and split_part(q.linked_target, '::', 1) = ?";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, String.valueOf(sId));
			log.info("Getting linking surveys: " + pstmt.toString());

			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				SurveyLinkDetails sld = new SurveyLinkDetails();

				sld.fromQuestionId = rs.getInt(1);
				sld.fromFormId = rs.getInt(2);
				sld.fromSurveyId = rs.getInt(3);

				LinkedTarget lt = GeneralUtilityMethods.getLinkTargetObject(rs.getString(4));
				sld.toSurveyId = lt.sId;
				sld.toQuestionId = lt.qId;

				if (sld.fromSurveyId != sld.toSurveyId) {
					sList.add(sld);
				}

			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return sList;
	}

	/*
	 * Get the question that links to the provided survey/question from the provided
	 * form
	 *
	 * public static int getLinkingQuestion(Connection sd, int formFromId, String
	 * linkedTarget) {
	 * 
	 * int questionId = 0;
	 * 
	 * String sql = "select q.q_id " + "from question q, form f " +
	 * "where q.f_id = f.f_id " + "and f.f_id = ? " + "and q.linked_target = ?";
	 * PreparedStatement pstmt = null;
	 * 
	 * try { pstmt = sd.prepareStatement(sql); pstmt.setInt(1, formFromId);
	 * pstmt.setString(2, linkedTarget); log.info("Getting linking surveys: " +
	 * pstmt.toString() );
	 * 
	 * ResultSet rs = pstmt.executeQuery(); if(rs.next()) {
	 * 
	 * questionId = rs.getInt(1);
	 * 
	 * }
	 * 
	 * } catch (Exception e) { log.log(Level.SEVERE, "Exception", e); } finally {
	 * try {if (pstmt != null) {pstmt.close(); }} catch (SQLException e) { } }
	 * 
	 * return questionId; }
	 */

	/*
	 * Get the surveys and questions that the provided form links to
	 */
	public static ArrayList<SurveyLinkDetails> getLinkedSurveys(Connection sd, int sId) {

		ArrayList<SurveyLinkDetails> sList = new ArrayList<SurveyLinkDetails>();

		String sql = "select q.q_id, f.f_id, q.linked_target " + "from question q, form f, survey s "
				+ "where q.f_id = f.f_id " + "and f.s_id = s.s_id " + "and s.s_id = ? "
				+ "and q.linked_target is not null";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("Getting linked surveys: " + pstmt.toString());

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				SurveyLinkDetails sld = new SurveyLinkDetails();
				sld.fromSurveyId = sId;
				sld.fromQuestionId = rs.getInt(1);
				sld.fromFormId = rs.getInt(2);

				LinkedTarget lt = GeneralUtilityMethods.getLinkTargetObject(rs.getString(3));
				sld.toSurveyId = lt.sId;
				sld.toQuestionId = lt.qId;

				if (sld.fromSurveyId != sld.toSurveyId) {
					sList.add(sld);
				}

			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return sList;
	}

	/*
	 * Get the main results table for a survey if it exists
	 */
	public static String getMainResultsTable(Connection sd, Connection conn, int sId) {
		String table = null;

		String sql = "select table_name from form where s_id = ? and parentform = 0";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);

			log.info("Getting main form: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				String table_name = rs.getString(1);
				if (tableExists(conn, table_name)) {
					table = table_name;
				}
			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			try {	if (pstmt != null) {	pstmt.close();}} catch (Exception e) {}
		}

		return table;
	}
	
	/*
	 * Get the main results table for a survey using the survey ident as a key if it exists
	 */
	public static String getMainResultsTableSurveyIdent(Connection sd, Connection conn, String sIdent) {
		String table = null;

		String sql = "select table_name from form "
				+ "where s_id  = (select s_id from survey where ident = ?) "
				+ "and parentform = 0";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, sIdent);

			log.info("Get main table: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				String table_name = rs.getString(1);
				if (tableExists(conn, table_name)) {
					table = table_name;
				}
			}

		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			try {	if (pstmt != null) {	pstmt.close();}} catch (Exception e) {}
		}

		return table;
	}

	/*
	 * Check for the existence of a table for a subform identified by its parent question
	 */
	public static boolean subFormTableExists(Connection sd, Connection cResults, int qId) throws SQLException {

		String sql = "select table_name from form where parentquestion = ?";
		PreparedStatement pstmt = null;
		boolean tableExists = false;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, qId);

			ResultSet rs = pstmt.executeQuery();
			log.info(pstmt.toString());
			if (rs.next()) {
				tableExists = tableExists(cResults, rs.getString(1));
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return tableExists;
	}
	
	/*
	 * Check for the existence of a table
	 */
	public static boolean tableExists(Connection conn, String tableName) throws SQLException {

		String sqlTableExists = "select count(*) from information_schema.tables where table_name =?;";
		PreparedStatement pstmt = null;
		int count = 0;

		try {
			pstmt = conn.prepareStatement(sqlTableExists);
			pstmt.setString(1, tableName.toLowerCase());

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getInt(1);
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return (count > 0);
	}
	
	/*
	 * Check for the existence of a table in a specific schema
	 */
	public static boolean tableExistsInSchema(Connection conn, String tableName, String schema) throws SQLException {

		String sqlTableExists = "select count(*) from information_schema.tables where table_name = ? and table_schema = ?";
		PreparedStatement pstmt = null;
		int count = 0;

		try {
			pstmt = conn.prepareStatement(sqlTableExists);
			pstmt.setString(1, tableName);
			pstmt.setString(2, schema);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getInt(1);
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return (count > 0);
	}

	/*
	 * Method to check for presence of the specified column
	 */
	public static boolean hasColumn(Connection cRel, String tablename, String columnName) {

		boolean hasColumn = false;

		String sql = "select column_name " + "from information_schema.columns "
				+ "where table_name = ? and column_name = ?;";

		PreparedStatement pstmt = null;

		try {
			pstmt = cRel.prepareStatement(sql);
			pstmt.setString(1, tablename);
			pstmt.setString(2, columnName);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				hasColumn = true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}

		return hasColumn;
	}
	
	/*
	 * Method to add a column to a table
	 */
	public static void addColumn(Connection conn, String tablename, String columnName, String type) throws SQLException {

		String sql = "alter table " + tablename + " add column " + columnName + " " + type;

		PreparedStatement pstmt = conn.prepareStatement(sql);
		log.info("Adding column: " + pstmt.toString());
		try {
			pstmt.executeUpdate();
		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("already exists")) {
				log.info("Column already exists");
			} else {
				throw e;
			}
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}
	}
	
	/*
	 * Method to lock a record out to a user
	 */
	public static int lockRecord(Connection conn, String tablename, String instanceId, String user) throws SQLException {

		int count = 0;
		
		String sql = "update " + tablename + " set _assigned = ? "
				+ "where instanceid = ? "
				+ "and _assigned is null";

		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, user);
		pstmt.setString(2,instanceId);
		log.info("locking record: " + pstmt.toString());
		try {
			count = pstmt.executeUpdate();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}
		
		return count;
	}
	
	/*
	 * Method to assign a record to a user
	 */
	public static int assignRecord(Connection conn, String tablename, String instanceId, String user) throws SQLException {

		int count = 0;
		
		String sql = "update " + tablename + " set _assigned = ? "
				+ "where instanceid = ? ";

		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, user);
		pstmt.setString(2,instanceId);
		log.info("locking record: " + pstmt.toString());
		try {
			count = pstmt.executeUpdate();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}
		
		return count;
	}
	
	/*
	 * Method to release a record 
	 */
	public static int releaseRecord(Connection conn, String tablename, String instanceId, String user) throws SQLException {

		int count = 0;
		String sql = "update " + tablename + " set _assigned = null "
				+ "where instanceid = ? "
				+ "and _assigned = ?";

		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, instanceId);
		pstmt.setString(2,user);
		log.info("locking record: " + pstmt.toString());
		try {
			count = pstmt.executeUpdate();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (Exception e) {}
		}
		
		return count;
		
	}
	
	/*
	 * Method to check for presence of the specified column in a specific schema
	 */
	public static boolean hasColumnInSchema(Connection cRel, String tablename, String columnName, String schema) {

		boolean hasColumn = false;

		String sql = "select column_name " + "from information_schema.columns "
				+ "where table_name = ? and table_schema = ? and column_name = ?;";

		PreparedStatement pstmt = null;

		try {
			pstmt = cRel.prepareStatement(sql);
			pstmt.setString(1, tablename);
			pstmt.setString(2, schema);
			pstmt.setString(3, columnName);
			//log.info("SQL: " + pstmt.toString());

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				hasColumn = true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
			}
		}

		return hasColumn;
	}
	
	/*
	 * Get the columns from a table in a specific schema
	 */
	public static ArrayList<String> getColumnsInSchema(Connection cRel, String tablename, String schema) {

		ArrayList<String> cols = new ArrayList<String> ();
		
		String sql = "select column_name from information_schema.columns "
				+ "where table_name = ? and table_schema = ?;";
		PreparedStatement pstmt = null;

		try {
			pstmt = cRel.prepareStatement(sql);
			pstmt.setString(1, tablename);
			pstmt.setString(2, schema);
			//log.info("SQL: " + pstmt.toString());

			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				cols.add(rs.getString(1));
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
			}
		}

		return cols;
	}


	/*
	 * Get the table that contains a question name If there is a duplicate question
	 * in a survey then throw an error
	 */
	public static String getTableForQuestion(Connection sd, int sId, String column_name) throws Exception {

		String sql = "select table_name from form f, question q " + "where f.s_id = ? " + "and f.f_id = q.f_id "
				+ "and q.column_name = ?;";

		PreparedStatement pstmt = null;
		int count = 0;
		String table = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, column_name);

			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				table = rs.getString(1);
				count++;
			}

			if (count == 0) {
				throw new Exception("Table containing question \"" + column_name + "\" in survey " + sId
						+ " not found. Check your LQAS template to see if this question name should be there.");
			} else if (count > 1) {
				throw new Exception("Duplicate " + column_name + " found in survey " + sId);
			}

		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return table;
	}

	/*
	 * Get the details of the top level form
	 */
	public static Form getTopLevelForm(Connection sd, int sId) throws SQLException {

		Form f = new Form();

		String sql = "select  " 
				+ "f_id," 
				+ "table_name "
				+ "from form "
				+ "where s_id = ? "
				+ "and parentform = 0;";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				f.id = rs.getInt("f_id");
				f.tableName = rs.getString("table_name");
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return f;

	}

	/*
	 * Get the details of the provided form Id
	 */
	public static Form getForm(Connection sd, int sId, int fId) throws SQLException {

		Form f = new Form();

		String sql = "select  " + "f_id," + "table_name " + "from form " + "where s_id = ? " + "and f_id = ?;";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, fId);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				f.id = rs.getInt("f_id");
				f.tableName = rs.getString("table_name");
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return f;

	}

	/*
	 * Get the details of the form that contains the specified question
	 */
	public static Form getFormWithQuestion(Connection sd, int qId) throws SQLException {

		Form f = new Form();

		String sql = "select  " + "f_id," + "from question " + "where q_id = ? ";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, qId);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				f.id = rs.getInt("f_id");
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return f;

	}

	/*
	 * Convert a location in well known text into latitude
	 */
	public static String wktToLatLng(String location, String axis) {
		String val = null;
		int idx;
		int idx2;
		String[] coords = null;

		if (location != null) {
			idx = location.indexOf('(');
			if (idx >= 0) {
				idx2 = location.lastIndexOf(')');
				if (idx2 >= 0) {
					location = location.substring(idx + 1, idx2);
					coords = location.split(" ");

					if (coords.length > 1) {
						if (axis.equals("lng")) {
							val = coords[0];
						} else {
							val = coords[1];
						}
					}
				}

			}
		}

		return val;

	}

	/*
	 * Get the index in the language array for the provided language
	 */
	public static int getLanguageIdx(org.smap.sdal.model.Survey survey, String language) {
		int idx = 0;

		if (survey != null && survey.languages != null) {
			for (int i = 0; i < survey.languages.size(); i++) {
				if (survey.languages.get(i).name.equals(language)) {
					idx = i;
					break;
				}
			}
		}
		return idx;
	}

	public static String getLanguage(String s) {
		String lang = "";
		if (s != null) {
			if (isLanguage(s, 0x0600, 0x06E0)) { // Arabic
				lang = "arabic";
			} else if (isLanguage(s, 0x0980, 0x09FF)) {
				lang = "bengali";
			}
		}
		return lang;
	}

	public static boolean isRtlLanguage(String s) {

		return isLanguage(s, 0x0600, 0x06E0);

	}

	/*
	 * Return true if the language should be rendered Right to Left Based on:
	 * http://stackoverflow.com/questions/15107313/how-to-determine-a-string-is-
	 * english-or-arabic
	 */
	public static boolean isLanguage(String s, int start, int end) {

		// Check a maximum of 10 characters
		if(s != null) {
			int len = (s.length() > 10) ? 10 : s.length();
			for (int i = 0; i < len;) {
				int c = s.codePointAt(i);
				if (c >= start && c <= end)
					return true;
				i += Character.charCount(c);
			}
		}
		return false;

	}

	/*
	 * Get a list of users with a specific role
	 */
	public static ArrayList<KeyValue> getUsersWithRole(Connection sd, int oId, String role) throws SQLException {

		ArrayList<KeyValue> users = new ArrayList<KeyValue>();

		String sql = "select u.ident, u.name " + "from users u, user_role ur, role r " + "where u.id = ur.u_id "
				+ "and ur.r_id = r.id " + "and r.o_id = u.o_id " + "and u.o_id = ? " + "and r.name = ? "
				+ "and u.temporary = false";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setString(2, role);
			log.info("Get users with role: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				users.add(new KeyValue(rs.getString(1), rs.getString(2)));
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		return users;

	}

	/*
	 * Return the SQL that does survey level Role Based Access Control
	 */
	public static String getSurveyRBAC() {
		return "and ((s.s_id not in (select s_id from survey_role where enabled = true)) or " // No roles on survey
				+ "(s.s_id in (select s_id from users u, user_role ur, survey_role sr where u.ident = ? and sr.enabled = true and u.id = ur.u_id and ur.r_id = sr.r_id)) " // User also has role
				+ ") ";
	}
	
	/*
	 * Return the SQL that does survey level Role Based Access Control (modified for use with upload event)
	 */
	public static String getSurveyRBACUploadEvent() {
		return "((ue.s_id not in (select s_id from survey_role where enabled = true)) or " // No roles on survey
				+ "(ue.s_id in (select s_id from users u, user_role ur, survey_role sr where u.ident = ? and sr.enabled = true and u.id = ur.u_id and ur.r_id = sr.r_id)) " // User also has role
				+ ") ";
	}

	/*
	 * Translate a question name to the version used in Kobo
	 */
	public static String translateToKobo(String in) {
		String out = in;

		if (in.equals("_end")) {
			out = "end";
		} else if (in.equals("_start")) {
			out = "start";
		} else if (in.equals("_device")) {
			out = "deviceid";
		} else if (in.equals("instanceid")) {
			out = "uuid";
		}
		return out;
	}

	/*
	 * Set the time on a java date to 23:59 and convert to a Timestamp
	 * Use the passed in timezone as the basis for determining hour
	 */
	public static Timestamp endOfDay(Date d, String tz) {
		
		Calendar cal = new GregorianCalendar();
		cal.setTime(d);
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		
		TimeZone timeZone = TimeZone.getTimeZone(tz);
		int offsetFromUTC = timeZone.getOffset(d.getTime());
		cal.add(Calendar.MILLISECOND, -offsetFromUTC);
		
		Timestamp endOfDay = new Timestamp(cal.getTime().getTime());

		return endOfDay;
	}
	
	/*
	 * Set the time on a java date to 00:00 and convert to a Timestamp
	 * Use the passed in timezone as the basis for determining hour
	 */
	public static Timestamp startOfDay(Date d, String tz) {

		Calendar cal = new GregorianCalendar();
		cal.setTime(d);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		
		TimeZone timeZone = TimeZone.getTimeZone(tz);
		int offsetFromUTC = timeZone.getOffset(d.getTime());
		
		// Subtract the offset between the tz and UTC in order to get the UTC time of the calendar value assuming it is in the specified timezone
		cal.add(Calendar.MILLISECOND, -offsetFromUTC);
		Timestamp startOfDay = new Timestamp(cal.getTime().getTime());

		return startOfDay;
	}

	/*
	 * Update the survey version
	 */
	public static void updateVersion(Connection sd, int sId) throws SQLException {

		String sql = "update survey set version = version + 1 where s_id = ?";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.executeUpdate();

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Update the survey version
	 */
	public static void setAutoUpdates(Connection sd, int sId, int managedId, ArrayList<AutoUpdate> autoUpdates)
			throws SQLException {

		String sqlGet = "select auto_updates from survey where s_id = ? and auto_updates is not null";
		PreparedStatement pstmtGet = null;
		String sql = "update survey set auto_updates = ? where s_id = ?";
		PreparedStatement pstmt = null;

		HashMap<Integer, ArrayList<AutoUpdate>> storedUpdate = null;
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		Type type = new TypeToken<HashMap<Integer, ArrayList<AutoUpdate>>>() {
		}.getType();
		try {
			// Get the current auto updates
			pstmtGet = sd.prepareStatement(sqlGet);
			pstmtGet.setInt(1, sId);
			ResultSet rs = pstmtGet.executeQuery();
			if (rs.next()) {
				String auString = rs.getString(1);
				storedUpdate = gson.fromJson(auString, type);
			}

			// Create a new structure if none already exists
			if (storedUpdate == null) {
				storedUpdate = new HashMap<Integer, ArrayList<AutoUpdate>>();
			}

			// Merge in the new value for this managed form
			if (autoUpdates == null) {
				storedUpdate.clear();
			} else {
				storedUpdate.put(managedId, autoUpdates);
			}

			// Save the updated value
			String saveString = null;
			if (!storedUpdate.isEmpty()) {
				saveString = gson.toJson(storedUpdate);
			}
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, saveString);
			pstmt.setInt(2, sId);
			log.info("Set auto update: " + pstmt.toString());
			pstmt.executeUpdate();

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {
				if (pstmtGet != null) {
					pstmtGet.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

	}

	/*
	 * Convert a :: separated String into surveyId and Question Id
	 */
	public static LinkedTarget getLinkTargetObject(String in) {
		LinkedTarget lt = new LinkedTarget();

		if (in != null) {
			String[] values = in.split("::");
			if (values.length > 0) {
				String sId = values[0].trim();
				try {
					lt.sId = Integer.parseInt(sId);
				} catch (Exception e) {
					log.log(Level.SEVERE, "Error converting linked survey id; " + sId, e);
				}
			}
			if (values.length > 1) {
				String qId = values[1].trim();
				try {
					lt.qId = Integer.parseInt(qId);
				} catch (Exception e) {
					log.log(Level.SEVERE, "Error converting linked question id; " + qId, e);
				}
			}
		}

		return lt;
	}

	/*
	 * Get the search question from appearance Used when converting searches into
	 * cascading selects
	 */
	public static String getFirstSearchQuestionFromAppearance(String appearance) {
		String filterQuestion = null;

		if (appearance != null && appearance.toLowerCase().trim().contains("search(")) {
			int idx1 = appearance.indexOf('(');
			int idx2 = appearance.indexOf(')');

			if (idx1 > 0 && idx2 > idx1) {
				String criteriaString = appearance.substring(idx1 + 1, idx2);
				log.info("#### criteria for csv filter: " + criteriaString);
				String criteria[] = criteriaString.split(",");
				if (criteria.length >= 4) {
					// remove quotes
					filterQuestion = criteria[3].trim();

				}
			}
		}

		return filterQuestion;
	}
	
	/*
	 * Get the first question that uses the specified list and that is in the specified survey
	 */
	public static int getQuestionFromList(Connection sd, int sId, int listId) throws SQLException {

		int qId = 0;

		String sql = "select q.q_id from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and f.s_id = ? "
				+ "and q.l_id = ? ";

		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, listId);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				qId = rs.getInt(1);
			}

		} catch (SQLException e) {
			log.log(Level.SEVERE, "Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {
			}
		}

		return qId;
	}


	/*
	 * Get centroid of geoJson Used when converting searches into cascading selects
	 * The data will be returned as a string containing longitude, latitude
	 */
	public static String getGeoJsonCentroid(String geoJson) throws SQLException {
		String centroid = "0.0, 0.0";
		int count = 0;
		Double lonTotal = 0.0;
		Double latTotal = 0.0;

		Pattern pattern = Pattern.compile("\\[[0-9\\.\\-,]+?\\]");
		java.util.regex.Matcher matcher = pattern.matcher(geoJson);
		while (matcher.find()) {

			count++;
			String matched = matcher.group();
			String c = matched.substring(1, matched.length() - 1);

			String coordArray[] = c.split(",");
			if (coordArray.length > 1) {
				lonTotal += Double.parseDouble(coordArray[0]);
				latTotal += Double.parseDouble(coordArray[1]);
			}
		}

		if (count > 0) {
			centroid = String.valueOf(lonTotal / count) + "," + String.valueOf(latTotal / count);
		}

		return centroid;
	}

	/*
	 * Replace links to self with links to absolute survey ident
	 */
	public static String removeSelfReferences(String in, String sIdent) {
		String resp = in;
		resp = resp.replaceAll("linked_self", "linked_" + sIdent);
		resp = resp.replaceAll("linked_s_pd_self", "linked_s_pd_" + sIdent);
		resp = resp.replaceAll("chart_self", "chart_" + sIdent);
		String key = getKeyQuestionCalculate(in);
		if(key != null) {
			resp = resp.replaceAll("chart_" + sIdent, "chart_" + sIdent + "_" + key);
		}

		return resp;
	}

	/*
	 * If there is an odd number of quotation marks
	 */
	public static boolean notInQuotes(StringBuffer str) {
		boolean niq = false;

		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == '\'') {
				count++;
			}
		}
		if ((count & 1) == 0) {
			niq = true;
		}
		return niq;
	}

	/*
	 * Get zip output stream
	 */
	public static void writeFilesToZipOutputStream(ZipOutputStream zos, ArrayList<FileDescription> files)
			throws IOException {
		
		byte[] buffer = new byte[1024];
		for (int i = 0; i < files.size(); i++) {
			FileDescription file = files.get(i);
			ZipEntry ze = new ZipEntry(file.name);
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(file.path);

			int len;
			while ((len = in.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}

			in.close();
			zos.closeEntry();
		}
		zos.close();
	}
	
	/*
	 * Write a directory to a Zip output stream
	 */
	public static void writeDirToZipOutputStream(ZipOutputStream zos, File dir)
			throws IOException {
		
		byte[] buffer = new byte[1024];
		
		for (File file : dir.listFiles()) {
			ZipEntry ze = new ZipEntry(file.getName());
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(file);

			int len;
			while ((len = in.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}

			in.close();
			zos.closeEntry();
		}
		zos.close();
	}

	/*
	 * Check to see if the passed in survey response, identified by an instance id, is within the filtered set of responses
	 */
	public static boolean testFilter(Connection cResults, ResourceBundle localisation, Survey survey, String filter, String instanceId, String tz) throws Exception {

		boolean testResult = false;

		StringBuffer filterQuery = new StringBuffer("select count(*) from ");
		filterQuery.append(getTableOuterJoin(survey.forms, 0, null));
		filterQuery.append(" where ");
		filterQuery.append(getMainTable(survey.forms));
		filterQuery.append(".instanceid = ?");

		// Add the filter
		filterQuery.append(" and (");
		SqlFrag frag = new SqlFrag();
		frag.addSqlFragment(filter, false, localisation);
		filterQuery.append(frag.sql);
		filterQuery.append(")");

		PreparedStatement pstmt = null;
		try {
			pstmt = cResults.prepareStatement(filterQuery.toString());
			pstmt.setString(1, instanceId);

			int idx = 2;
			idx = GeneralUtilityMethods.setFragParams(pstmt, frag, idx, tz);

			log.info("Evaluate Filter: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				if(rs.getInt(1) > 0) {
					testResult = true;
				}
			}

		} catch(Exception e) { 
			throw new Exception(e);
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		return testResult;
	}

	/*
	 * Return SQL that can be used to filter out records not matching a filter
	 */
	public static String getFilterCheck(Connection cResults, ResourceBundle localisation, Survey survey, String filter, String tz) throws Exception {

		String resp = null;

		StringBuffer filterQuery = new StringBuffer("(select ");
		filterQuery.append(getMainTable(survey.forms));
		filterQuery.append(".instanceid from ");		
		filterQuery.append(getTableOuterJoin(survey.forms, 0, null));
		filterQuery.append(" where (");		

		// Add the filter
		SqlFrag frag = new SqlFrag();
		frag.addSqlFragment(filter, false, localisation);
		filterQuery.append(frag.sql);
		filterQuery.append("))");

		PreparedStatement pstmt = null;
		try {
			pstmt = cResults.prepareStatement(filterQuery.toString());
			int idx = 1;
			idx = GeneralUtilityMethods.setFragParams(pstmt, frag, idx, tz);

			resp = pstmt.toString();

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		log.info("Filter check sql: " + resp);

		return resp;
	}

	/*
	 * 
	 */
	private static String getMainTable(ArrayList<Form> forms) throws Exception {
		for (Form f : forms) {
			if(f.parentform == 0) {
				return f.tableName;
			}
		}
		throw new Exception("Main table not found");
	}
	/*
	 * Get an outer join that will get all the data in the survey
	 */
	private static String getTableOuterJoin(ArrayList<Form> forms, int parent, String parentTableName) {
		StringBuffer out = new StringBuffer("");

		for (Form f : forms) {
			if(f.parentform == parent && !f.reference) {
				if(parent != 0) {
					out.append(" left outer join ");
				}
				out.append(f.tableName);
				if(parent != 0) {
					out.append(" on ");
					out.append(parentTableName);
					out.append(".prikey");
					out.append(" = ");
					out.append(f.tableName);
					out.append(".parkey");
				}
				out.append(getTableOuterJoin(forms, f.id, f.tableName));
			}
		}

		return out.toString();
	}

	/*
	 * Set the parameters for an array of sql fragments
	 */
	public static int setArrayFragParams(PreparedStatement pstmt, ArrayList<SqlFrag> rfArray, int index, String tz) throws Exception {
		for(SqlFrag rf : rfArray) {
			index = setFragParams(pstmt, rf, index, tz);
		}
		return index;
	}

	/*
	 * Set the parameters for an array of sql fragments
	 */
	public static int setFragParams(PreparedStatement pstmt, SqlFrag frag, int index, String tz) throws Exception {
		int attribIdx = index;
		for(int i = 0; i < frag.params.size(); i++) {
			SqlFragParam p = frag.params.get(i);
			if(p.getType().equals("text")) {
				pstmt.setString(attribIdx++, p.sValue);
			} else if(p.getType().equals("integer")) {
				pstmt.setInt(attribIdx++,  p.iValue);
			} else if(p.getType().equals("double")) {
				pstmt.setDouble(attribIdx++,  p.dValue);
			} else if(p.getType().equals("date")) {
				pstmt.setTimestamp(attribIdx++,  startOfDay(java.sql.Date.valueOf(p.sValue), tz));
			} else {
				throw new Exception("Unknown parameter type: " + p.getType());
			}
		}
		return attribIdx;
	}

	/*
	 * Return true if document synchronisation is enabled on this server
	 */
	public static boolean documentSyncEnabled(Connection sd) throws SQLException {

		boolean enabled = false;
		String sql = "select document_sync from server;";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next())  {
				enabled = rs.getBoolean(1);
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		return enabled;
	}

	/*
	 * Get document server configuration
	 */
	public static HashMap<String, String> docServerConfig(Connection sd) throws SQLException {

		HashMap<String, String> config = new HashMap<> ();
		String sql = "select doc_server, doc_server_user, doc_server_password from server;";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next())  {
				config.put("server", rs.getString(1));
				config.put("user", rs.getString(2));
				config.put("password", rs.getString(3));
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		return config;
	}

	/*
	 * Record the fact that a user has downloaded a form
	 */
	public static void recordFormDownload(Connection sd, String user, String formIdent, int version, String deviceId) throws SQLException {

		String sqlDel = "delete from form_downloads where u_id = ? and form_ident = ? and device_id = ?";
		PreparedStatement pstmtDel = null;

		String sql = "insert into form_downloads(u_id, form_ident, form_version, device_id, updated_time) "
				+ "values(?, ?, ?, ?, now())";
		PreparedStatement pstmt = null;

		try {
			int uId = getUserId(sd, user);

			pstmtDel = sd.prepareStatement(sqlDel);
			pstmtDel.setInt(1, uId);
			pstmtDel.setString(2, formIdent);
			pstmtDel.setString(3, deviceId);
			pstmtDel.executeUpdate();

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, uId);
			pstmt.setString(2, formIdent);
			pstmt.setInt(3, version);
			pstmt.setString(4, deviceId);
			pstmt.executeUpdate();	

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

	}

	/*
	 * Convert Parameters to Key Value array
	 */
	public static ArrayList<KeyValueSimp> convertParametersToArray(String in) {
		ArrayList<KeyValueSimp> out = new ArrayList<> ();

		if(in != null) {
			String params = removeSurroundingWhiteSpace(in, '=');
			params = params.replace('\n', ' ');	// replace new lines with spaces
			params = params.replace('\r', ' ');	// replace carriage returns with spaces

			String[] pArray = params.split(";");
			if(pArray.length == 1) {
				pArray = params.split(" ");		// Temporary fallback to splitting on white space
			}
			for(int i = 0; i < pArray.length; i++) {
				String[] px = pArray[i].split("=");
				if(px.length == 2) {
					out.add(new KeyValueSimp(px[0].trim(), px[1].trim()));
				}
			}
		}
		
		return out;
	}
	
	/*
	 * Convert Parameters to a String
	 */
	public static String convertParametersToString(ArrayList<KeyValueSimp> in) {
		
		String params = null;
		StringBuffer out = new StringBuffer();;
		if(in != null && in.size() > 0) {
			
			int idx = 0;
			for(KeyValueSimp kv : in) {
				if(idx++ > 0) {
					out.append(";");
				}
				out.append(kv.k).append("=").append(kv.v);
			}
			params = out.toString();
		}
		
		return params;
	}
	
	/*
	 * Remove any white space surrounding a character
	 */
	public static String removeSurroundingWhiteSpace(String in, char c) {
		StringBuffer out = new StringBuffer("");

		if(in != null) {
			boolean validLocn = false;
			boolean foundChar = false;
			for(int i = 0; i < in.length(); i++) {

				if(in.charAt(i) != ' ') {
					out.append(in.charAt(i));
				}

				// Determine if the location is valid for a space
				// a. After the character has been found and after some text
				if(in.charAt(i) == '=') {
					foundChar = true;
				}
				if(foundChar && in.charAt(i) != c && in.charAt(i) != ' ') {
					validLocn = true;
					foundChar = false;
				}

				// only add a space when the location is valid for a space
				if(in.charAt(i) == ' ' && validLocn) {
					out.append(' ');
					validLocn = false;
				}

			}
		}

		return out.toString();
	}

	/*
	 * Make sure there is white space around a character
	 * Don't make a change if the character is within single quotes
	 */
	public static String addSurroundingWhiteSpace(String in, char [] cArray) {
		StringBuffer out = new StringBuffer("");

		if(in != null) {
			int quoteCount = 0;

			for(int i = 0; i < in.length(); i++) {

				if(in.charAt(i) == '\'') {
					quoteCount++;
				}

				boolean charInList = false;
				for(int j = 0; j < cArray.length; j++) {
					if(in.charAt(i) == cArray[j]) {
						if((i < in.length() - 1) && in.charAt(i+1) == '=' && (in.charAt(i) == '<' || in.charAt(i) == '>')) {
							charInList = false;
						} else if(i > 0 && in.charAt(i) == '=' && (in.charAt(i-1) == '<' || in.charAt(i-1) == '>')) {
							charInList = false;
						} else if(i > 0 && in.charAt(i) == '=' && in.charAt(i-1) == '!' ) {
							charInList = false;
						} else if(i < in.length() - 1 && in.charAt(i) == '(' && in.charAt(i+1) == ')') {
							charInList = false;
						} else if(i > 0 && in.charAt(i) == ')' && in.charAt(i-1) == '(' ) {
							charInList = false;
						} else {
							charInList = true;
						}
						break;
					}
				}
				if(charInList && quoteCount%2 == 0) {
					if(i > 0 && in.charAt(i-1) != ' ') {
						out.append(' ');
					}
					out.append(in.charAt(i));
					if(i < in.length() - 1 && in.charAt(i+1) != ' ') {
						out.append(' ');
					}
				} else {
					out.append(in.charAt(i));
				}
			}
		}

		return out.toString();
	}

	/*
	 * Make sure there is white space around a String of characters
	 * Don't make a change if the character is within single quotes
	 */
	public static String addSurroundingWhiteSpace(String in, String [] token) {
		StringBuffer out = new StringBuffer("");

		if(in != null) {
			int quoteCount = 0;

			for(int i = 0; i < in.length(); i++) {

				if(in.charAt(i) == '\'') {
					quoteCount++;
				}

				int tokenIndex = -1;
				for(int j = 0; j < token.length; j++) {
					if(in.substring(i).startsWith(token[j])) {				
						tokenIndex = j;
						break;
					}
				}

				if(tokenIndex >= 0 && quoteCount%2 == 0) {
					if(i > 0 && in.charAt(i-1) != ' ') {
						out.append(' ');
					}
					out.append(token[tokenIndex]);
					i += token[tokenIndex].length() - 1;		// i will be incremented again next time round the loop
					if(i + 1 < in.length() && in.charAt(i+2) != ' ') {
						out.append(' ');
					}
				} else {
					out.append(in.charAt(i));
				}
			}
		}

		return out.toString();
	}

	/*
	 * Get an array of question names from a string that contains xls names
	 */
	public static ArrayList<String> getXlsNames(
			String input) throws Exception {

		ArrayList<String> output = new ArrayList<> (); 

		if(input != null) {
			Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
			java.util.regex.Matcher matcher = pattern.matcher(input);

			while (matcher.find()) {

				String matched = matcher.group();
				String qname = matched.substring(2, matched.length() - 1);
				output.add(qname);
			}
		}

		return output;
	}

	/*
	 * Remove leading or trailing whitespace around question names
	 */
	public static String cleanXlsNames(
			String input)  {

		StringBuffer output = new StringBuffer(""); 

		if(input != null) {
			Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
			java.util.regex.Matcher matcher = pattern.matcher(input);

			int start = 0;
			while (matcher.find()) {

				// Add any text before the match
				int startOfGroup = matcher.start();
				output.append(input.substring(start, startOfGroup));
				
				String matched = matcher.group();
				String qname = matched.substring(2, matched.length() - 1).trim();
				output.append("${").append(qname).append("}");
				
				// Reset the start
				start = matcher.end();
			}
			
			// Get the remainder of the string
			if (start < input.length()) {
				output.append(input.substring(start));
			}
		}

		if(output.length() > 0) {
			return output.toString().trim();
		} else {
			return null;
		}
	}

	/*
	 * Return true if this is a meta question
	 */
	public static boolean isMetaQuestion(String name) {
		boolean meta = false;

		name = name.toLowerCase();

		if(name.equals("instanceid")) {
			meta = true;
		} else if(name.equals("instancename")) {
			meta = true;
		} else if(name.equals("meta")) {
			meta = true;
		} else if(name.equals("meta_groupend")) {
			meta = true;
		}

		return meta;

	}

	/*
	 * Get a preload item from the type
	 * If this is not a preload return null
	 */
	public static MetaItem getPreloadItem(String type, String name, String display_name, int metaItem, String settings) throws Exception {

		MetaItem item = null;

		if(type.equals("start")) {
			item = new MetaItem(metaItem, "dateTime", name, type, cleanName(name, true, true, false), "timestamp", true, display_name, settings);
		} else if(type.equals("end")) {
			item = new MetaItem(metaItem, "dateTime", name, type, cleanName(name, true, true, false), "timestamp", true, display_name, settings);
		} else if(type.equals("today")) {
			item = new MetaItem(metaItem, "date", name, type, cleanName(name, true, true, false), "date", true, display_name, settings);
		} else if(type.equals("deviceid")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} else if(type.equals("subscriberid")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} else if(type.equals("simserial")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} else if(type.equals("phonenumber")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} else if(type.equals("username")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} else if(type.equals("email")) {
			item = new MetaItem(metaItem, "string", name, type, cleanName(name, true, true, false), "property", true, display_name, settings);
		} 

		return item;

	}
	
	public static String getPreloadColumnName(Connection sd, int sId, int preloadId) throws SQLException {
		String colname = null;
		ArrayList<MetaItem> preloads = getPreloads(sd, sId);
		for(MetaItem item : preloads) {
			if(item.id == preloadId) {
				colname = item.columnName;
				break;
			}
		}
		return colname;
	}

	/*
	 * Get preloads in a survey
	 */
	public static void setPreloads(Connection sd, int sId, ArrayList<MetaItem> preloads) throws SQLException {

		String sql = "update survey "
				+ "set meta = ? "
				+ "where s_id = ?";
		PreparedStatement pstmt = null;

		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		String metaString = gson.toJson(preloads);
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, metaString);
			pstmt.setInt(2, sId);
			log.info("Update meta item data: " + pstmt.toString());
			pstmt.executeUpdate();

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		return;
	}
	
	/*
	 * Get preloads in a survey
	 */
	public static ArrayList<MetaItem> getPreloads(Connection sd, int sId) throws SQLException {
		ArrayList<MetaItem> preloads = null;

		String sql = "select meta from survey where s_id = ?;";
		PreparedStatement pstmt = null;

		String metaString = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next())  {
				metaString = rs.getString(1);
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		if(metaString != null) {
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			preloads = gson.fromJson(metaString, new TypeToken<ArrayList<MetaItem>>() {}.getType());
		} else {
			preloads = new ArrayList <>();
		}

		return preloads;
	}
	
	/*
	 * Get details for a preload
	 */
	public static MetaItem getPreloadDetails(Connection sd, int sId, int metaId) throws SQLException {
		MetaItem item = null;
		ArrayList<MetaItem> preloads = null;

		String sql = "select meta from survey where s_id = ?;";
		PreparedStatement pstmt = null;

		String metaString = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next())  {
				metaString = rs.getString(1);
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}

		if(metaString != null) {
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			preloads = gson.fromJson(metaString, new TypeToken<ArrayList<MetaItem>>() {}.getType());
			for(MetaItem mi : preloads) {
				if(mi.id == metaId) {
					item = mi;
					break;
				}
			}
		}

		return item;
	}
	
	public static Question getPreloadAsQuestion(Connection sd, int sId, int metaId) throws SQLException {
		Question q = null;
		MetaItem item = getPreloadDetails(sd, sId, metaId); 
		if(item != null) {
			q = new Question();
			q.columnName = item.columnName;
			q.type = item.dataType;
			q.display_name = item.display_name;
			q.name = item.name;
			q.appearance = item.settings;
		}
		
		return q;
	}

	/*
	 * Get Column Values from a result set created using ColDesc
	 */
	public static int getColValues(ResultSet rs, ColValues values, int dataColumn, 
			ArrayList<ColDesc> columns, boolean merge_select_multiple,
			String surveyName) throws SQLException {

		ColDesc item = columns.get(dataColumn);
		StringBuffer selMulValue = new StringBuffer("");

		if(merge_select_multiple && item.qType != null && item.qType.equals("select") && item.choices != null && !item.compressed) {
			if(rs != null) {
				for(KeyValue choice : item.choices) {
					int smv = rs.getInt(dataColumn + 1);
					if(smv == 1) {
						if(selMulValue.length() > 0) {
							selMulValue.append(" ");
						}
						selMulValue.append(choice.k);
					}
					dataColumn++;
				}
			} else {
				// Jump over data columns 
				dataColumn += item.choices.size();
			}

			values.name = item.displayName;
			values.label = item.label;
			values.value = selMulValue.toString();

		} else {
			if(item.displayName != null) {
				values.name = item.displayName;
			} else {
				values.name = item.column_name;
			}
			values.label = item.label;
			if(rs != null) {
				values.value = rs.getString(dataColumn + 1);
			}
			dataColumn++;
		}
		values.type = item.qType;
		
		if(item.column_name != null && item.column_name.equals(SmapServerMeta.SURVEY_ID_NAME)) {
			values.value = surveyName;
		}

		return dataColumn;
	}

	/*
	 * Set questions as published if the results column is available
	 */
	public static void setPublished(Connection sd, Connection cRel, int sId) throws SQLException {

		String sql = "select f.table_name, q.column_name, q.q_id, q.qtype, q.compressed, q.l_id "
				+ "from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and f.s_id = ? "
				+ "and q.source is not null "
				+ "and q.published = 'false' "
				+ "and q.soft_deleted = 'false' ";		
		PreparedStatement pstmt = null;

		String sqlUpdate = "update question set published = true where q_id = ?";
		PreparedStatement pstmtUpdate = null;
		
		String sqlGetChoices = "select o_id, column_name from option where l_id = ?";
		PreparedStatement pstmtGetChoices = null;
		
		String sqlUpdateChoices = "update option set published = true where o_id = ?";
		PreparedStatement pstmtUpdateChoices = null;
		try {
			pstmtUpdate = sd.prepareStatement(sqlUpdate);

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next())  {
				String qType = rs.getString(4);
				boolean compressed = rs.getBoolean(5);
				if(qType.equals("select") && !compressed) {
					// Automatically set published the publish status of options determine if data is actually available 
					pstmtUpdateChoices = sd.prepareStatement(sqlUpdateChoices);
					pstmtGetChoices = sd.prepareStatement(sqlGetChoices);
					pstmtGetChoices.setInt(1, rs.getInt(6));
					ResultSet rsChoices = pstmtGetChoices.executeQuery();
					while(rsChoices.next()) {
						if(hasColumn(cRel, rs.getString(1), rs.getString(2) + "__" + rsChoices.getString(2))) {
							pstmtUpdateChoices.setInt(1, rsChoices.getInt(1));
							pstmtUpdateChoices.executeUpdate();
						}
					}
				} 
				if(hasColumn(cRel, rs.getString(1), rs.getString(2))) {
					pstmtUpdate.setInt(1, rs.getInt(3));
					pstmtUpdate.executeUpdate();
				}
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
			if(pstmtUpdate != null) try {pstmtUpdate.close();} catch(Exception e) {}
			if(pstmtGetChoices != null) try {pstmtGetChoices.close();} catch(Exception e) {}
			if(pstmtUpdateChoices != null) try {pstmtUpdateChoices.close();} catch(Exception e) {}
		}

	}

	/*
	 * Get survey group
	 */
	public static int getSurveyGroup(Connection sd, int sId) throws SQLException {

		int group = 0;
		String sql = "select group_survey_id from survey where s_id = ?";
		PreparedStatement pstmt = null;
		
		String sql2 = "select count(*) from survey where group_survey_id = ?";
		PreparedStatement pstmt2 = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1,  sId);
			log.info("Check if this survey is in a group: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next())  {
				group = rs.getInt(1);
			}
			
			if(group == 0) {
				// Perhaps this survey is the main survey in the group
				pstmt2 = sd.prepareStatement(sql2);
				pstmt2.setInt(1,  sId);
				log.info("Check if this survey is the main survey: " + pstmt2.toString());
				rs = pstmt2.executeQuery();			
				if(rs.next())  {
					if(rs.getInt(1) > 0) {
						// Its the parent form
						group = sId;
					}
				}
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
			if(pstmt2 != null) try {pstmt2.close();} catch(Exception e) {}
		}

		return group;
	}

	public static String getSurveyParameter(String param, ArrayList<KeyValueSimp> params) {
		
		String value = null;
		if (params != null) {
			for(KeyValueSimp kv : params) {
				if(kv.k.equals(param)) {
					value = kv.v;
					break;
				}	
			}
		}
		return value;
	}
	
	/*
	 * Get the longitude and latitude from a WKT POINT
	 */
	public static String [] getLonLat(String point) {
		String [] coords = null;
		int idx1 = point.indexOf("(");
		int idx2 = point.indexOf(")");
		if(idx2 > idx1) {
			String lonLat = point.substring(idx1 + 1, idx2);
			coords = lonLat.split(" ");
		}
		return coords;
	}
	
	/*
	 * Convert a geojson string into a WKT string
	 * Assume Point TODO update to cater for other types
	 */
	public static String getWKTfromGeoJson(String json) {
		
		String wkt = null;
		if(json != null) {
			int idx1 = json.indexOf("[");
			int idx2 = json.indexOf("]");
			
			if(idx2 > idx1) {
				String c = json.substring(idx1 + 1, idx2);
				String [] coords = c.split(",");
				if(coords.length >= 2) {
					wkt = "POINT(" + coords[0].trim() + " " + coords[1].trim() + ")"; 
				}
				
			}
		}
			
		return wkt;
	}
	
	public static String getOdkPolygon(Polygon p) {
		StringBuffer coordsString = new StringBuffer("");
		ArrayList<ArrayList<Double>> coords = p.coordinates.get(0);
		if(coords != null && coords.size() > 0) {

			for(int i = 0; i < coords.size(); i++) {
				ArrayList<Double> points = coords.get(i);
				if(points.size() > 1) {
					if(i > 0) {
						coordsString.append(";");
					}
					coordsString.append(points.get(1)).append(" ").append(points.get(0));
				}
			}	
		}
		return coordsString.toString();
	}
	
	public static String getOdkLine(Line l) {
		StringBuffer coordsString = new StringBuffer("");
		ArrayList<ArrayList<Double>> coords = l.coordinates;
		if(coords != null && coords.size() > 0) {

			for(int i = 0; i < coords.size(); i++) {
				ArrayList<Double> points = coords.get(i);
				if(points.size() > 1) {
					if(i > 0) {
						coordsString.append(";");
					}
					coordsString.append(points.get(1)).append(" ").append(points.get(0));
				}
			}	
		}
		return coordsString.toString();
	}

	/*
	 * Update settings in question that identify if choices are in an external file
	 */
	public static void setExternalFileValues(Connection sd, Question q) throws SQLException {
		
		if((q.type.startsWith("select") || q.type.equals("rank")) && isAppearanceExternalFile(q.appearance)) {
			ManifestInfo mi = addManifestFromAppearance(q.appearance, null);
			q.external_choices = true;
			q.external_table = mi.filename;
		} else {
			q.external_choices = false;
			q.external_table = null;
		}
		
		String sql = "update question "
				+ "set external_choices = ?,"
				+ "external_table = ? "
				+ "where q_id = ?";
		PreparedStatement pstmt = null;

		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  q.external_choices ? "yes" : "no");
			pstmt.setString(2, q.external_table);
			pstmt.setInt(3,  q.id);
			pstmt.executeUpdate();
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
		}
		
	}
	
	/*
	 * Create a temporary user
	 */
	public static String createTempUser(Connection sd, ResourceBundle localisation, int oId, String email, 
			String assignee_name, int pId, TaskFeature tf) throws Exception {
		UserManager um = new UserManager(localisation);
		String tempUserId = "u" + String.valueOf(UUID.randomUUID());
		User u = new User();
		u.ident = tempUserId;
		u.email = email;
		u.name = assignee_name;

		// Only allow access to the project used by this task
		u.projects = new ArrayList<Project> ();
		Project p = new Project();
		p.id = pId;
		u.projects.add(p);

		// Only allow enum access
		u.groups = new ArrayList<UserGroup> ();
		u.groups.add(new UserGroup(Authorise.ENUM_ID, Authorise.ENUM));
		int assignee = um.createTemporaryUser(sd, u, oId);
		if(tf != null) {
			tf.properties.assignee = assignee;
		}
		
		return tempUserId;
	}
	
	/*
	 * Delete a temporary user
	 */
	public static void deleteTempUser(Connection sd, ResourceBundle localisation, int oId, String uIdent) throws Exception {
		
		String sql = "delete from users " 
				+ "where ident = ? "
				+ "and temporary = 'true' "
				+ "and o_id = ?";	
		PreparedStatement pstmt = null;

		if(uIdent != null) {

			try {
			
				pstmt = sd.prepareStatement(sql);
				pstmt.setString(1, uIdent);
				pstmt.setInt(2,  oId);
				log.info("Delete temporary user: " + pstmt.toString());
				int count = pstmt.executeUpdate();
				if(count == 0) {
					log.info("error: failed to delete temporay user: " + uIdent);
				}
				// Delete any csv table definitions that they have
				SurveyTableManager stm = new SurveyTableManager(sd, localisation);
				stm.deleteForUsers(uIdent);			// Delete references to this survey in the csv table 
				
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			}

		} else {
			throw new Exception("Null User Ident");
		}
	}
	
	public static boolean isAttachmentType(String type) {
		boolean attachment = false;
		if(type.equals("image") || type.equals("audio") || type.equals("video") || type.equals("file")) {
			attachment = true;
		}
		return attachment;
	}
	
	/*
	 * Return true if email tasks are blocked.  That is the email_task value is false
	 */
	public static boolean emailTaskBlocked(Connection sd, int oId) throws Exception {
		
		boolean emailTask = true;
		String sql = "select email_task from organisation " 
				+ "where id = ? ";	
		PreparedStatement pstmt = null;

		try {
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
		
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				emailTask = !rs.getBoolean(1);
			}
				
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return emailTask;

	}
	
	/*
	 * Return true if timezone is valid
	 */
	public static boolean isValidTimezone(Connection sd, String tz) throws Exception {
		
		boolean isValid = false;
		String sql = "select count(*) from pg_timezone_names where name = ?"; 
		PreparedStatement pstmt = null;

		try {
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, tz);
		
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				isValid = rs.getInt(1)> 0;
			}
				
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return isValid;

	}
	
	/*
	 * Add the thread value that links replaced records
	 */
	public static void continueThread(Connection cResults, String table, int prikey, int sourceKey) throws SQLException {
		
		String sqlCopyThreadCol = "update " + table 
						+ " set _thread = (select _thread from " + table + " where prikey = ?),"
						+ " _assigned = (select _assigned from " + table + " where prikey = ?) "
						+ "where prikey = ?";
		PreparedStatement pstmtCopyThreadCol = null;
			
		try {
			initialiseThread(cResults, table, sourceKey, null);
			
			// At this point the thread col must exist and the _thread value for the source must exist
			pstmtCopyThreadCol = cResults.prepareStatement(sqlCopyThreadCol);
			pstmtCopyThreadCol.setInt(1, sourceKey);
			pstmtCopyThreadCol.setInt(2, sourceKey);
			pstmtCopyThreadCol.setInt(3, prikey);
			log.info("continue thread: " + pstmtCopyThreadCol.toString());
			pstmtCopyThreadCol.executeUpdate();
			
			
		} finally {
			if(pstmtCopyThreadCol != null) try{pstmtCopyThreadCol.close();}catch(Exception e) {}
		}

	}
	
	/*
	 * Add the thread value that links replaced records
	 * Either get the thread key using the sourceKey or if that is not set use the passed in instanceId directly
	 */
	public static void initialiseThread(Connection cResults, String table, int sourceKey, String instanceId) throws SQLException {
		
		String sqlInitThreadCol = "update " + table + " set _thread = instanceid where prikey = ?";
		PreparedStatement pstmtInitThreadCol = null;
		
		String sqlInitThreadCol2 = "update " + table + " set _thread = instanceid where instanceid = ?";
		PreparedStatement pstmtInitThreadCol2 = null;
			
		try {
			if(!GeneralUtilityMethods.hasColumn(cResults, table, "_thread")) {
				// Add the thread column
				GeneralUtilityMethods.addColumn(cResults, table, "_thread", "text");
				
				// Initialise the thread column
				if(sourceKey > 0) {
					pstmtInitThreadCol = cResults.prepareStatement(sqlInitThreadCol);
					pstmtInitThreadCol.setInt(1, sourceKey);
					pstmtInitThreadCol.executeUpdate();
				} else {
					pstmtInitThreadCol2 = cResults.prepareStatement(sqlInitThreadCol2);
					pstmtInitThreadCol2.setString(1, instanceId);
					pstmtInitThreadCol2.executeUpdate();
				}
			}
			if(!GeneralUtilityMethods.hasColumn(cResults, table, "_assigned")) {
				GeneralUtilityMethods.addColumn(cResults, table, "_assigned", "text");
			}
			
		} finally {
			if(pstmtInitThreadCol != null) try{pstmtInitThreadCol.close();}catch(Exception e) {}
			if(pstmtInitThreadCol2 != null) try{pstmtInitThreadCol2.close();}catch(Exception e) {}
		}

	}
	
	/*
	 * Get the thread for a record
	 * This is a UUID which identifies a series of changes to a record.
	 * Ie if you start with Record A and then Update it then the two records will have the same thread id.
	 */
	public static String getThread(Connection cResults, String table, String instanceId) throws SQLException {
		
		String thread = null;
		String sql = "select _thread from " + table + " where instanceid = ?";
		PreparedStatement pstmt = null;
		
		String sqlUpdate = "update table " + table + " set_thread = ? where instanceid = ?";
			
		try {
			
			initialiseThread(cResults, table, 0, instanceId);
			
			pstmt = cResults.prepareStatement(sql);
			pstmt.setString(1, instanceId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				thread = rs.getString(1);
			}	
			
			// If there is no thread then we should start one (Should only be required for legacy records)
			if (thread == null) {
				if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
				pstmt = cResults.prepareStatement(sqlUpdate);
				pstmt.setString(1,  instanceId);
				pstmt.setString(2,  instanceId);
				
				thread = instanceId;
			}
			
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e) {}
		}
		
		return thread;

	}
	
	/*
	 * Set columns which have actually been published but are still marked as un-published to published
	 * This function has been added due to the difficulty of keeping the published indicator always up to date
	 *  in the situation where you have shared tables
	 */
	public static void updateUnPublished(Connection sd, Connection cResults, String tableName, 
			int fId, boolean publish) throws SQLException {
		
		String sql = "select q_id, column_name, qtype, compressed " 
				+ "from question where f_id = ? "
				+ "and source is not null "
				+ "and published = 'false' "
				+ "and soft_deleted = 'false' ";
		PreparedStatement pstmt = null;
		
		String sqlPublished = "select count(*) from information_schema.columns where table_name = ? " 
				+ "and column_name = ?";
		PreparedStatement pstmtPub = null;
		
		String sqlUpdate = "update question set published = 'true' where q_id = ?";
		PreparedStatement pstmtUpdate = null;
		
		ResultSet rs = null;
		ResultSet rsPub = null;
		try {
			pstmtPub = cResults.prepareStatement(sqlPublished);
			pstmtPub.setString(1, tableName);
			
			pstmtUpdate = sd.prepareStatement(sqlUpdate);
			
			// Get unpublished
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, fId);
		
			log.info(pstmt.toString());
			rs = pstmt.executeQuery();
			while(rs.next()) {
				int qId = rs.getInt(1);
				String columnName = rs.getString(2);
				String type = rs.getString(3);
				boolean compressed = rs.getBoolean(4);
				
				pstmtPub.setString(2, columnName);	
				rsPub = pstmtPub.executeQuery();
				int count = 0;
				if(rsPub.next()) {
					count = rsPub.getInt(1);
				}
				if(count > 0) {
					// Column has been published update the schema to reflect this
					pstmtUpdate.setInt(1, qId);
					pstmtUpdate.executeUpdate();
				} else if(publish) {
					if(!GeneralUtilityMethods.hasColumn(cResults, tableName, columnName)) {
						GeneralUtilityMethods.alterColumn(cResults, tableName, type, columnName, compressed);
					}
					pstmtUpdate.setInt(1, qId);
					pstmtUpdate.executeUpdate();
				}
			}
			
		} finally {
			if(rs != null) {try{rs.close();} catch(Exception e) {}}
			if(rsPub != null) {try{rsPub.close();} catch(Exception e) {}}
			if(pstmt != null) {try{pstmt.close();} catch(Exception e) {}}
			if(pstmtPub != null) {try{pstmtPub.close();} catch(Exception e) {}}
			if(pstmtUpdate != null) {try{pstmtUpdate.close();} catch(Exception e) {}}
		}
	}

	/*
	 * Return the number of questions in each form of the survey
	 */
	public static ArrayList<FormLength> getFormLengths(Connection sd, int sId) throws Exception {
		
		ArrayList<FormLength> formList = new ArrayList<FormLength> ();
		
		String sql = "select f.f_id, f.name, count(*) "
				+ "from question q, form f "
				+ "where f.s_id = ? "
				+ "and f.f_id = q.f_id "
				+ "and qtype != 'begin group' "
				+ "and qtype != 'end group' "
				+ "and qtype != 'begin repeat' "
				+ "group by f.f_id, f.name";	
		PreparedStatement pstmt = null;

		String sqlLq = "select qname from question "
				+ "where f_id = ? "
				+ "and qtype != 'begin group' "
				+ "and qtype != 'end group' "
				+ "and qtype != 'begin repeat' "
				+ "order by seq asc";
		PreparedStatement pstmtLq = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
		
			log.info("Get question count: " + pstmt.toString());
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				FormLength fl = new FormLength(rs.getInt(1), rs.getString(2), rs.getInt(3));
				formList.add(fl);
				
				if(fl.isTooLong()) {
					// Find last acceptable question name
					pstmtLq = sd.prepareStatement(sqlLq);
					pstmtLq.setInt(1, fl.f_id);
					
					ResultSet rsLq = pstmtLq.executeQuery();
					for(int i = 0; i < FormLength.MAX_FORM_LENGTH; i++) {
						rsLq.next();
					}
					fl.lastQuestionName = rsLq.getString(1);
				}
			}
				
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtLq != null) {pstmtLq.close();}} catch (SQLException e) {}
		}
		
		return formList;

	}
	
	public static boolean parameterIsSet(String parameter) {
		boolean isSet = false;
		if(parameter != null) {
			if(parameter.equals("yes") || parameter.equals("true")) {
				isSet = true;
			}
		}
		return isSet;
	}
	
	public static String removeBOM(String in) {
		
		if (in.startsWith(UTF8_BOM)) {
            in = in.substring(1);
        }
		return in;
	}
	
	public static int addSqlParams(PreparedStatement pstmt, int idx, ArrayList<SqlParam> params) throws SQLException {
		if(params != null && params.size() > 0) {
			for(SqlParam p : params) {
				if(p.type.equals("string")) {
					pstmt.setString(idx++, p.vString);
				} 
			}
		}
		return idx;
	}
	
	public static User getArchivedUser(Connection sd, int oId, int uId) throws SQLException {
		
		String sql = "select settings "
				+ "from user_organisation uo "
				+ "where o_id = ? "
				+ "and u_id = ?";
		PreparedStatement pstmt = null;
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		
		User u = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setInt(2, uId);			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				u = gson.fromJson(rs.getString(1), User.class);
			}
		} finally {
			if(pstmt != null) {try{pstmt.close();} catch(Exception e) {}}
		}
		return u;
	}
	
	public static Timestamp getTimestamp(String timeString) {
			
		Timestamp t = null;
		
		if(timeString != null) {
			
			/*
			 * work around java 8 bug
			 * https://stackoverflow.com/questions/39033525/error-java-time-format-datetimeparseexception-could-not-be-parsed-unparsed-tex
			 */
			timeString = workAroundJava8bug00(timeString);
			timeString = timeString.trim().replace(' ', 'T');
			
			log.info("timestring to test: " + timeString);

			try {
				OffsetDateTime odt = OffsetDateTime.parse( timeString );
				Instant instant = odt.toInstant();
				java.util.Date date = Date.from( instant );				
				t =  new Timestamp(date.getTime());
			} catch (Exception e) {
				log.info("Failed to parse string into Timestamp: "  + timeString + " : " + e.getMessage());			
			}
		}
		return t;
	}
	
	public static String workAroundJava8bug00(String timeString) {
		if ( timeString.indexOf ( "+" ) == ( timeString.length () - 3 ) ) {
		    // If third character from end is a PLUS SIGN, append ':00'.
		    timeString += ":00";
		} else if ( timeString.lastIndexOf ( "-" ) == ( timeString.length () - 3 ) ) {
		    // If third character from end is a PLUS SIGN, append ':00'.
		    timeString += ":00";
		}
		
		return timeString;
	}
	
	/*
	 * Apply any changes to assignment status
	 */
	public static Timestamp getScheduledStart(Connection sd, int assignmentId) {

		PreparedStatement pstmt = null;
		
		String sql = "select schedule_at from tasks where id = (select task_id from assignments where id = ?) ";
		Timestamp scheduledDate = null;
		
		try {

			if(assignmentId > 0) {
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, assignmentId);
				log.info("Get scheduled date: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					scheduledDate = rs.getTimestamp(1);
				}
			}


		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		return scheduledDate;
	}
	
	/*
	 * Restore files from s3
	 * type can be:
	 *  attachments: restore attachments
	 *  uploadedSurveys: restore the raw upload data
	 */
	public static void restoreUploadedFiles(String ident, String type) throws InterruptedException, IOException {
		Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/smap_bin/restoreFiles.sh " + 
				ident + 	" " + type + " >> /var/log/tomcat7/survey.log 2>&1"});
		int code = proc.waitFor();

		if(code != 0) {
			log.info("Error:  Failed to restore files from s3 for ident " + ident + " error code: " + code);
		}

	}
	
	/*
	 * Log a refresh
	 */
	static public void recordRefresh(Connection sd, int oId, String user, Double lat, Double lon) throws SQLException {


		String sql = "update last_refresh "
				+ "set refresh_time = now(), "
				+ "geo_point =  ST_GeomFromText('POINT(' || ? || ' ' || ? ||')', 4326) "
				+ "where o_id = ? "
				+ "and user_ident = ?";

		String sqlInsert = "insert into last_refresh "
				+ "(o_id, user_ident, refresh_time, geo_point) "
				+ "values(?, ?, now(),  ST_GeomFromText('POINT(' || ? || ' ' || ? ||')', 4326))";
		
		PreparedStatement pstmt = null;

		if(user != null) {
			try {
				
				pstmt = sd.prepareStatement(sql);
				pstmt.setDouble(1, lon);
				pstmt.setDouble(2, lat);
				pstmt.setInt(3, oId);
				pstmt.setString(4,  user);
				int count = pstmt.executeUpdate();
				if (count == 0) {
					try {pstmt.close();} catch (Exception e) {};
					pstmt = sd.prepareStatement(sqlInsert);
					pstmt.setInt(1, oId);
					pstmt.setString(2, user);
					pstmt.setDouble(3, lon);
					pstmt.setDouble(4, lat);
					pstmt.executeUpdate();
				}
	
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			}
		}

	}
	
	public static String getWebformLink(String urlprefix, 
			String surveyIdent, 
			String initial_data_source, 
			int assignmentId,
			int taskId,
			String updateId) {
		
		boolean hasParam = false;
		StringBuffer url = new StringBuffer(urlprefix);
		
		url.append("/webForm/").append(surveyIdent);
		
		if(initial_data_source != null && initial_data_source.equals("survey")) {
			url.append(hasParam ? "&" : "?").append("datakey=instanceid&datakeyvalue=").append(updateId);
			hasParam = true;
		} else if(initial_data_source != null && initial_data_source.equals("task")) {
			url.append(hasParam ? "&" : "?").append("taskkey=").append(taskId);
			hasParam = true;
		} else {
			
		}
		url.append(hasParam ? "&" : "?").append("assignment_id=").append(assignmentId);
		return url.toString();
	}
	
	public static String getInitialXmlDataLink(String urlprefix, 
			String surveyIdent, 
			String initial_data_source, 
			int taskId,
			String updateId) {
		
		StringBuffer url = new StringBuffer(urlprefix);		
		url.append("/webForm/instance/").append(surveyIdent);
		
		if(initial_data_source != null && initial_data_source.equals("survey")) {
			url.append("/").append(updateId); 
		} else if(initial_data_source != null && initial_data_source.equals("task")) {
			url.append("/task/").append(taskId);	
		} else {
			return null;
		}
		
		return url.toString();
	}
	
	public static String getInitialDataLink(String urlprefix, 
			String surveyIdent, 
			String initial_data_source, 
			int taskId,
			String updateId) {
		
		StringBuffer url = new StringBuffer(urlprefix);		
		url.append("api/v1/data/").append(surveyIdent);
		
		if(initial_data_source != null && initial_data_source.equals("survey")) {
			url.append("/").append(updateId); 
		} else if(initial_data_source != null && initial_data_source.equals("task")) {
			url.append("/task/").append(taskId);	
		} else {
			return null;
		}
		
		return url.toString();
	}
	
	public static String getPdfLink(String urlprefix, 
			String surveyIdent, 
			String updateId,
			String tz) {
		
		StringBuffer url = new StringBuffer(urlprefix);		
		url.append("surveyKPI/pdf/").append(surveyIdent);
		url.append("?instance=").append(updateId);
		url.append("&tz=").append(tz);
			
		return url.toString();
	}
	
	public static String getWebformLink(String urlprefix, 
			String surveyIdent, 
			String updateId) {
		
		StringBuffer url = new StringBuffer(urlprefix);		
		url.append("webForm/").append(surveyIdent);
		url.append("?datakey=instanceid");
		url.append("&datakeyvalue=").append(updateId);
			
		return url.toString();
	}
	
	public static String getAuditLogLink(String urlprefix, 
			String surveyIdent, 
			String updateId) {
		
		StringBuffer url = new StringBuffer(urlprefix);		
		url.append("api/v1/audit/log/").append(surveyIdent).append("/").append(updateId);
			
		return url.toString();
	}
	
	/*
	 * Starting from the past in question get all the tables up to the highest
	 * parent that are part of this survey
	 */
	public static void createLocation(Connection sd, int oId, 
			String group, String uid, String name, double lon, double lat) throws SQLException {

		PreparedStatement pstmt = null;
		String sql = "insert into locations (o_id, locn_group, locn_type, uid, name, the_geom) "
				+ "values (?, ?, 'nfc', ?, ?, ST_GeomFromText(?, 4326))";

		try {
			pstmt = sd.prepareStatement(sql);

			pstmt.setInt(1, oId);
			pstmt.setString(2,  group);
			pstmt.setString(3,  uid);
			pstmt.setString(4,  name);
			
			Point newPoint = new Point(lon, lat);
			pstmt.setString(5, newPoint.getAsText());
			
			log.info("Create new location (" + name + "): " + pstmt.toString());
			pstmt.executeUpdate();

		} finally {
			try {
				if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}

	}

	/*
	 * Starting from the past in question get all the tables up to the highest
	 * parent that are part of this survey
	 */
	public static void updateLocation(Connection sd, 
			int oId, 
			String group, 
			String uid, 
			String name,
			double lon,
			double lat) throws SQLException {

		PreparedStatement pstmt = null;
		String sql = "update locations "
				+ "set uid = ?,"
				+ "the_geom = ST_GeomFromText(?, 4326) "
				+ "where o_id = ? "
				+ "and locn_group = ? "
				+ "and name = ?";

		try {
			pstmt = sd.prepareStatement(sql);

			pstmt.setString(1, uid);
			
			Point newPoint = new Point(lon, lat);
			pstmt.setString(2, newPoint.getAsText());
			
			pstmt.setInt(3,  oId);
			pstmt.setString(4,  group);
			pstmt.setString(5,  name);

			log.info("Update location: " + pstmt.toString());
			pstmt.executeUpdate();

		} finally {
			try {if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}

	}
	
	public static String getInstanceId(Connection cResults, String tableName, int prikey) throws SQLException {

		String instanceId = null;
		
		PreparedStatement pstmt = null;
		String sql = "select instanceid "
				+ "from " + tableName + " "
				+ "where prikey = ? ";

		try {
			pstmt = cResults.prepareStatement(sql);

			pstmt.setInt(1, prikey);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				instanceId = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}
		return instanceId;
	}
	
	public static String getLatestInstanceId(Connection cResults, String tableName, String instanceId) throws SQLException {

		String latestInstanceId = null;
		
		PreparedStatement pstmt = null;
		String sql = "select instanceid "
				+ "from " + tableName + " "
				+ "where _thread = (select _thread from " + tableName + " where instanceid = ?) "
				+ "order by prikey desc limit 1";

		try {
			pstmt = cResults.prepareStatement(sql);

			pstmt.setString(1, instanceId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				latestInstanceId = rs.getString(1);
			}

		} finally {
			try {if (pstmt != null) {	pstmt.close();}} catch (SQLException e) {}
		}
		return latestInstanceId;
	}
	
	/*
	 * Alter the table
	 */
	public static TableUpdateStatus alterColumn(Connection cResults, String table, String type, String column, boolean compressed) {

		PreparedStatement pstmtAlterTable = null;
		PreparedStatement pstmtApplyGeometryChange = null;

		TableUpdateStatus status = new TableUpdateStatus();
		status.tableAltered = true;
		status.msg = "";

		try {
			if(type.equals("geopoint") || type.equals("geotrace") || type.equals("geoshape")) {

				String geoType = null;

				if(type.equals("geopoint")) {
					geoType = "POINT";
				} else if (type.equals("geotrace")) {
					geoType = "LINESTRING";
				} else if (type.equals("geoshape")) {
					geoType = "POLYGON";
				}
				String gSql = "SELECT AddGeometryColumn('" + table + 
						"', 'the_geom', 4326, '" + geoType + "', 2);";
				log.info("Add geometry column: " + gSql);

				pstmtApplyGeometryChange = cResults.prepareStatement(gSql);
				try { 
					pstmtApplyGeometryChange.executeQuery();
				} catch (Exception e) {
					// Allow this to fail where an older version added a geometry, which was then deleted, then a new 
					//  geometry with altitude was added we need to go on and add the altitude and accuracy
					log.info("Error altering table -- continuing: " + e.getMessage());
					try {cResults.rollback();} catch(Exception ex) {}
				}

				// Add altitude and accuracy
				if(type.equals("geopoint")) {
					String sqlAlterTable = "alter table " + table + " add column the_geom_alt double precision";
					pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
					log.info("Alter table: " + pstmtAlterTable.toString());					
					pstmtAlterTable.executeUpdate();

					try {if (pstmtAlterTable != null) {pstmtAlterTable.close();}} catch (Exception e) {}
					sqlAlterTable = "alter table " + table + " add column the_geom_acc double precision";
					pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
					log.info("Alter table: " + pstmtAlterTable.toString());					
					pstmtAlterTable.executeUpdate();
				}

				// Commit this change to the database
				try { cResults.commit();	} catch(Exception ex) {}
			} else {

				type = getPostgresColType(type, compressed);
				String sqlAlterTable = "alter table " + table + " add column " + column + " " + type + ";";
				pstmtAlterTable = cResults.prepareStatement(sqlAlterTable);
				log.info("Alter table: " + pstmtAlterTable.toString());

				pstmtAlterTable.executeUpdate();

				// Commit this change to the database
				try {cResults.commit();} catch(Exception ex) {}
			} 
		} catch (Exception e) {
			// Report but otherwise ignore any errors
			log.info("Error altering table -- continuing: " + e.getMessage());

			// Rollback this change
			try {cResults.rollback();} catch(Exception ex) {}

			// Only record the update as failed if the problem was not due to the column already existing
			status.msg = e.getMessage();
			if(status.msg == null || !status.msg.contains("already exists")) {
				status.tableAltered = false;
			}
		} finally {
			try {if (pstmtAlterTable != null) {pstmtAlterTable.close();}} catch (Exception e) {}
			try {if (pstmtApplyGeometryChange != null) {pstmtApplyGeometryChange.close();}} catch (Exception e) {}
		}
		return status;
	}

	public static String getPostgresColType(String colType, boolean compressed) {
		if(colType.equals("string")) {
			colType = "text";
		} else if(colType.equals("decimal")) {
			colType = "double precision";
		} else if(colType.equals("select1")) {
			colType = "text";
		} else if(colType.equals("barcode")) {
			colType = "text";
		} else if(colType.equals("note")) {
			colType = "text";
		} else if(colType.equals("calculate")) {
			colType = "text";
		} else if(colType.equals("chart")) {
			colType = "text";
		} else if(colType.equals("parent_form")) {
			colType = "text";
		} else if(colType.equals(SmapQuestionTypes.CHILD_FORM)) {
			colType = "text";
		} else if(colType.equals("acknowledge") || colType.equals("trigger")) {
			colType = "text";
		} else if(colType.equals("range")) {
			colType = "double precision";
		} else if(colType.equals("dateTime")) {
			colType = "timestamp with time zone";					
		} else if(colType.equals("time")) {
			colType = "time with time zone";					
		} else if(GeneralUtilityMethods.isAttachmentType(colType)) {
			colType = "text";					
		} else if(colType.equals("select") && compressed) {
			colType = "text";					
		} else if(colType.equals("rank")) {
			colType = "text";					
		}
		return colType;
	}
	
}

