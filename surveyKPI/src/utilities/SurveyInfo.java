package utilities;

/*
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

*/

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import surveyKPI.Dashboard;

public class SurveyInfo {
	
	private static Logger log =
			 Logger.getLogger(SurveyInfo.class.getName());
	
	private int sId;
	private String displayName;
	
	public SurveyInfo(int surveyId, Connection connection) throws SQLException {	
		
		sId = surveyId;
		
		String sql = "SELECT s.display_name" + 
				" FROM survey s" +
				" WHERE s.s_id = ?";
			
		PreparedStatement pstmt = connection.prepareStatement(sql);	
		pstmt.setInt(1, sId);
		ResultSet resultSet = pstmt.executeQuery();
		
		if(resultSet.next()) {
			displayName = resultSet.getString(1);
		} else {
			log.info("Error (SurveyInfo.java) retrieving survey data for survey: " + surveyId );
		}		
		try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
	}
	
	/*
	 * Getters
	 */
	public int getSId() {
		return sId;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
}
