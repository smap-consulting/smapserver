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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;



import org.smap.sdal.model.Survey;
import org.smap.sdal.model.Translation;


public class TranslationManager {
	
	private static Logger log =
			 Logger.getLogger(TranslationManager.class.getName());

	private String manifestQuerySql = 
			" from translation t, survey s, users u, user_project up, project p" +
					" where u.id = up.u_id " +
					" and p.id = up.p_id " +
					" and s.p_id = up.p_id " +
					" and s.s_id = t.s_id " +
					" and (t.type = 'image' or t.type = 'video' or t.type = 'audio' or t.type = 'csv') " +
					" and u.ident = ? " +
					" and t.s_id = ?; ";
	
	public List<Translation> getManifestBySurvey(Connection sd, 
			PreparedStatement pstmt,
			String user, 
			int surveyId
			)	throws SQLException {
		
		ArrayList<Translation> translations = new ArrayList<Translation>();	// Results of request
		
		ResultSet resultSet = null;
		String sql = "select t.t_id, t.s_id, t.language, t.text_id, t.type, t.value " +
				manifestQuerySql;
		
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setString(1, user);
		pstmt.setInt(2, surveyId);
		resultSet = pstmt.executeQuery();
		
		while (resultSet.next()) {								

			Translation t = new Translation();
			t.setId(resultSet.getInt(1));
			t.setSurveyId(resultSet.getInt(2));
			t.setLanguage(resultSet.getString(3));
			t.setTextId(resultSet.getString(4));
			t.setType(resultSet.getString(5));
			t.setValue(resultSet.getString(6));
			
			translations.add(t);
		} 
		return translations;
	}
	
	/*
	 * Returns true if the user can access the survey and that survey has a manifest
	 */
	public boolean hasManifest(Connection sd, 
			PreparedStatement pstmt,
			String user, 
			int surveyId
			)	throws SQLException {
		
		boolean hasManifest = false;
		
		String sql = "select count(*) " +
				manifestQuerySql;
		
		ResultSet resultSet = null;
		pstmt = sd.prepareStatement(sql);	 			
		pstmt.setString(1, user);
		pstmt.setInt(2, surveyId);
		resultSet = pstmt.executeQuery();
		
		if(resultSet.next()) {
			if(resultSet.getInt(1) > 0) {
				hasManifest = true;
			}
		}
		
		return hasManifest;	
	}
}
