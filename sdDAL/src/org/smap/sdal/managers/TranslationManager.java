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

package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.ManifestValue;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


public class TranslationManager {
	
	private static Logger log =
			 Logger.getLogger(TranslationManager.class.getName());

	private String manifestQuerySql = 
			" from translation t, survey s " +
					" where s.s_id = t.s_id " +
					" and (t.type = 'image' or t.type = 'video' or t.type = 'audio') " +
					" and t.s_id = ?; ";
	
	public List<ManifestValue> getManifestBySurvey(Connection sd, 
			String user, 
			int surveyId,
			String basePath,
			String surveyIdent
			)	throws SQLException {
		
		HashMap<String, String> files = new HashMap<String, String> ();
		ArrayList<ManifestValue> manifests = new ArrayList<ManifestValue>();	// Results of request
		int oId = GeneralUtilityMethods.getOrganisationId(sd, user);
		
		String sqlQuestionLevel = "select t.text_id, t.type, t.value " +
				manifestQuerySql;
		PreparedStatement pstmtQuestionLevel = null;
		
		try {
			
			/*
			 * Get Question and Option level manifests from the translation table
			 */
			pstmtQuestionLevel = sd.prepareStatement(sqlQuestionLevel);	 			
			pstmtQuestionLevel.setInt(1, surveyId);

			ResultSet rs = pstmtQuestionLevel.executeQuery();
			
			while (rs.next()) {								
	
				ManifestValue m = new ManifestValue();
				m.sId = surveyId;
				m.text_id = rs.getString(1);
				m.type = rs.getString(2);
				m.value = rs.getString(3);
								
				if(m.value != null) {
					// Get file name from value (Just for legacy, new media should be stored as the file name only)
					int idx = m.value.lastIndexOf('/');	
					m.fileName = m.value.substring(idx + 1);					
					UtilityMethodsEmail.getFileUrl(m, surveyIdent, m.fileName, basePath, oId, surveyId);		// Url will be null if file does not exist
					
					// Make sure we have not already added this file (Happens with multiple languages referencing the same file)
					if(files.get(m.fileName) == null) {
						files.put(m.fileName, m.fileName);
						manifests.add(m);
					}
				}
			} 
			
			List<ManifestValue> surveyManifests = getSurveyManifests(sd, surveyId, surveyIdent, basePath, oId, false);
			manifests.addAll(surveyManifests);
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmtQuestionLevel != null) { try {pstmtQuestionLevel.close();} catch (SQLException e) {}}
		}
		
		return manifests;
	}
	
	
	/*
	 * Get the manifest items to linked forms
	 */
	public List<ManifestValue> getSurveyManifests(Connection sd,  
			int surveyId,
			String surveyIdent,
			String basePath,
			int oId,
			boolean linkedOnly
			)	throws SQLException {
		
		ArrayList<ManifestValue> manifests = new ArrayList<ManifestValue>();	// Results of request
		
		String sql = "select manifest from survey where s_id = ? and manifest is not null; ";
		PreparedStatement pstmt = null;
		
		try {
			
			ResultSet rs = null;
			
			/*
			 * Get Survey Level manifests from survey table
			 */
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, surveyId);
			
			rs = pstmt.executeQuery();
			if(rs.next()) {
				String manifestString = rs.getString(1);
				Type type = new TypeToken<ArrayList<String>>(){}.getType();
				ArrayList<String> manifestList = new Gson().fromJson(manifestString, type);
				
				for(int i = 0; i < manifestList.size(); i++) {
					
					ManifestValue m = new ManifestValue();
					m.fileName = manifestList.get(i);
					m.sId = surveyId;
					
					if(m.fileName.equals("linked_self")) {
						m.fileName = "linked_" + surveyIdent;
					} else if(m.fileName.equals("linked_s_pd_self")) {
						m.fileName = "linked_s_pd_" + surveyIdent;
					} else if(m.fileName.startsWith("chart_self")) {
						m.fileName = m.fileName.replace("chart_self", "chart_" + surveyIdent);
					}
					
					if(m.fileName.endsWith(".csv") || m.fileName.endsWith(".zip")) {
						if(!linkedOnly) {
							m.type = "csv";
							UtilityMethodsEmail.getFileUrl(m, surveyIdent, m.fileName, basePath, oId, surveyId);
							manifests.add(m);
						}
					} else {
						m.type = "linked";
						m.url = "/surveyKPI/file/" + m.fileName + ".csv/survey/" + surveyId + "?linked=true";
						manifests.add(m);
					}				
				}
			}
					
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}
		}
		
		return manifests;
	}
	
	/*
	 * Get the manifest entries for csv files used by pulldata functions (required by enketo)
	 */
	public List<ManifestValue> getPulldataManifests(Connection sd, 
			int surveyId
			)	throws SQLException {
		
		
		ArrayList<ManifestValue> manifests = new ArrayList<ManifestValue>();	// Results of request
		
		String sql = "select manifest from survey where s_id = ? and manifest is not null; ";
		PreparedStatement pstmt = null;
		
		String pull = "select count(*) from question q, form f "
				+ "where q.f_id = f.f_id "
				+ "and f.s_id = ? "
				+ "and q.calculate like ?";
		PreparedStatement pstmtPull = null;
		
		try {
			
			ResultSet rs = null;
			
			/*
			 * Get Survey Level manifests from survey table
			 */
			pstmt = sd.prepareStatement(sql);	 			
			pstmt.setInt(1, surveyId);
			log.info("SQL survey level manifests:" + pstmt.toString());
			
			pstmtPull = sd.prepareStatement(pull);
			pstmtPull.setInt(1, surveyId);
			
			rs = pstmt.executeQuery();
			if(rs.next()) {
				String manifestString = rs.getString(1);
				Type type = new TypeToken<ArrayList<String>>(){}.getType();
				ArrayList<String> manifestList = new Gson().fromJson(manifestString, type);
				
				for(int i = 0; i < manifestList.size(); i++) {
					
					ManifestValue m = new ManifestValue();
					m.fileName = manifestList.get(i);
					m.sId = surveyId;
					
					m.baseName = m.fileName;
					if(m.baseName.endsWith(".csv")) {
						m.baseName = m.baseName.substring(0, m.baseName.length() - 4);
						m.type = "csv";
					} else {
						m.type = "linked";
					}
					pstmtPull.setString(2, "%pulldata%" + m.baseName + "%");
					//log.info("Looking for pulldata manifests: " + pstmtPull.toString());
					rs = pstmtPull.executeQuery();
					if(rs.next() && rs.getInt(1) > 0) {
						String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, surveyId);
						if(m.type.equals("csv")) {
							int oId = GeneralUtilityMethods.getOrganisationIdForSurvey(sd, surveyId);
							UtilityMethodsEmail.getFileUrl(m, surveyIdent, m.fileName, "/smap", oId, surveyId);
						} else {
							// TODO location depends on user
							//m.url = "/surveyKPI/file/" + m.fileName + ".csv/survey/" + surveyId + "?linked=true";
						}
						
						if(m.fileName.equals("linked_self")) {
							m.fileName = "linked_" + surveyIdent;
						} else if(m.fileName.equals("linked_s_pd_self")) {
							m.fileName = "linked_s_pd_" + surveyIdent;
						}
						
						manifests.add(m);
					} 
				}
			}
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}
			if (pstmtPull != null) { try {pstmtPull.close();} catch (SQLException e) {}}
		}
		
		return manifests;
	}
	
	/*
	 * Returns true if the survey has a manifest at either the survey or question level
	 */
	public boolean hasManifest(Connection sd, 
			String user, 
			int surveyId
			)	throws SQLException {
		
		boolean hasManifest = false;
		
		// Test for a question level manifest
		String sqlQuestionLevel = "select count(*) " +
				manifestQuerySql;
		
		// Test for a survey level manifest
		String sqlSurveyLevel = "select manifest from survey where s_id = ? and manifest is not null";
		
		PreparedStatement pstmtQuestionLevel = null;
		PreparedStatement pstmtSurveyLevel = null;
		
		try {
			ResultSet resultSet = null;
			pstmtQuestionLevel = sd.prepareStatement(sqlQuestionLevel);	 			
			pstmtQuestionLevel.setInt(1, surveyId);
			
			resultSet = pstmtQuestionLevel.executeQuery();
			
			if(resultSet.next()) {
				if(resultSet.getInt(1) > 0) {
					hasManifest = true;
				}
			}
			
			if(!hasManifest) {
				/*
				 * Test for a survey level manifest
				 */
				pstmtSurveyLevel = sd.prepareStatement(sqlSurveyLevel);
				pstmtSurveyLevel.setInt(1, surveyId);
				resultSet = pstmtSurveyLevel.executeQuery();
				
				if(resultSet.next()) {
					String manifest = resultSet.getString(1);	
					if(manifest.trim().length() > 0) {
						hasManifest = true;			
					}
				}
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmtQuestionLevel != null) { try {pstmtQuestionLevel.close();} catch (SQLException e) {}}
			if (pstmtSurveyLevel != null) { try {pstmtSurveyLevel.close();} catch (SQLException e) {}}
		}
		
		return hasManifest;	
	}
	
}
