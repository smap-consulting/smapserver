package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.model.ChangeItem;


public class GeneralUtilityMethods {
	
	private static Logger log =
			 Logger.getLogger(GeneralUtilityMethods.class.getName());
	
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
		 
		String sqlClearKey = "delete from dynamic_users " +
				" where u_id =  ? " +
				" and survey_ident = ?;";		
		PreparedStatement pstmtClearKey = null;
		
		String sqlAddKey = "insert into dynamic_users (u_id, survey_ident, access_key) " +
				" values (?, ?, ?);";		
		PreparedStatement pstmtAddKey = null;
		
		log.info("GetNewAccessKey");
		try {
		
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
			 * Clear any old keys for this user and this survey
			 */
			pstmtClearKey = sd.prepareStatement(sqlClearKey);
			pstmtClearKey.setInt(1, userId);
			pstmtClearKey.setString(2, surveyIdent);
			log.info("Clear old keys:" + pstmtClearKey.toString());
			pstmtClearKey.executeUpdate();
			
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
			
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtGetUserId != null) { pstmtGetUserId.close();}} catch (SQLException e) {}
			try {if (pstmtClearKey != null) { pstmtClearKey.close();}} catch (SQLException e) {}
			try {if (pstmtAddKey != null) { pstmtAddKey.close();}} catch (SQLException e) {}
		}
		
		return key;
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
				" and d.access_key = ?";
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
						c.qId = qId;
						c.name = qName;					// Add for logging
						c.fileName = csvFileName;		// Add for logging
						c.qType = qType;
						c.newVal = optionCols[vlc.label];
						c.key = optionCols[vlc.value];
		    		  
						ciList.add(c);
		    		   
					} else {
						// ignore line
					}
				}
			}
	       
		       // TODO delete all file options that were not in the latest file (file version number)
		} catch (Exception e) {
			e.printStackTrace();
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
								responses.add(email);
							}
						}
					} else {
						log.info("******** " + rs.getString(1));
						responses.add(rs.getString(1));
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
	
}
