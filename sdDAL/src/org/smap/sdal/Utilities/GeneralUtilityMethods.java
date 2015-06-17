package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.PropertyChange;


public class GeneralUtilityMethods {
	
	private static Logger log =
			 Logger.getLogger(GeneralUtilityMethods.class.getName());

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
		
		String interval = "20 days";
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
		if(appearance != null && appearance.toLowerCase().trim().startsWith("search(")) {
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
		
		/*
		 * Ignore language in the query, these values are codes and are (currently) independent of language
		 */
		PreparedStatement pstmt = null;
		String sql = "SELECT o.ovalue, t.value " +
				"from option o, translation t " +  		
				"where o.label_id = t.text_id " +
				"and o.q_id = ? " +
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
	 * Get languages for a survey
	 */
	public static ArrayList<String> getLanguagesForSurvey(Connection connectionSD, int sId) throws Exception {
		
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
		} catch(Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}
		return languages;
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
		String sqlOption = "select ovalue from option where q_id = ?";
		
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
	 * Return true if the passed in column name is in the table
	 */
	public static boolean hasColumn(Connection sd, String tableName, String columnName) throws SQLException {
		
		boolean result = false;
		
		String sql = "select count(*) from information_schema.columns where table_name = ? " +
				"and column_name = ?;";
		PreparedStatement pstmt = null;
		
		
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1,  tableName);
			pstmt.setString(2,  columnName);
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				if(rs.getInt(1) > 0) {
					result = true;
				}
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}	
		
		return result;
		
	}
	
}
