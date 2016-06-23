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
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Link;

public class LinkageManager {
	
	private static Logger log =
			 Logger.getLogger(LinkageManager.class.getName());

	public ArrayList<Link> getSurveyLinks(Connection sd, Connection cRel, int sId, int fId, int prikey) throws SQLException {
		ArrayList<Link> links = new ArrayList<Link> ();
		
		ResultSet rs = null;
		ResultSet rsHrk = null;
		
		// SQL to get default settings for child forms
		String sql = "select f_id from form where parentform = ? and s_id = ?";
		PreparedStatement pstmt = null;
		
		// SQL to get linked forms
		String sqlLinked = "select linked_survey, f_id, column_name from question "
				+ "where linked_survey > 0 "
				+ "and f_id = ? "
				+ "and f_id in (select f_id from form where s_id = ?)";
		PreparedStatement pstmtLinked = null;
		
		PreparedStatement pstmtGetHrk = null;
		
		try {

			// If the passed in form id was 0 then use the top level form
			if(fId == 0) {
				Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId);
				fId = f.id;
			}
			
			// Get the child forms
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, fId);
			pstmt.setInt(2, sId);
			log.info("Links:  Getting child forms: " + pstmt.toString());
			rs = pstmt.executeQuery();
			while(rs.next()) {
				Link l = new Link();
				l.type = "child";
				l.fId = rs.getInt(1);
				l.parkey = prikey;
				
				links.add(l);
			}
			rs.close();
			
			// Get the linked forms
			pstmtLinked = sd.prepareStatement(sqlLinked);
			pstmtLinked.setInt(1, fId);
			pstmtLinked.setInt(2, sId);
			log.info("Links:  Getting linked forms: " + pstmtLinked.toString());
			rs = pstmtLinked.executeQuery();
			while(rs.next()) {
				int linkedSId = rs.getInt(1);
				int valueFId = rs.getInt(2);
				String valueColName = rs.getString(3);
				
				String hrk = null;
				Form valueForm = GeneralUtilityMethods.getForm(sd, sId, valueFId);
				Form f = GeneralUtilityMethods.getTopLevelForm(sd, linkedSId);
	
				// SQL to get the HRK value
				String sqlGetHrk = "select " + valueColName + " from " + valueForm.tableName + " where prikey = ?;";
				pstmtGetHrk = cRel.prepareStatement(sqlGetHrk);
				pstmtGetHrk.setInt(1,prikey);
				log.info("Getting Hrk: " + pstmtGetHrk.toString());
				rsHrk = pstmtGetHrk.executeQuery();
				if(rsHrk.next()) {
					hrk = rsHrk.getString(1);
				}
				
				Link l = new Link();
				l.type = "link";
				l.fId = f.id;
				l.sId = linkedSId;
				l.hrk = hrk;
				
				links.add(l);
			}
		
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtLinked != null) {pstmtLinked.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetHrk != null) {pstmtGetHrk.close();	}} catch (SQLException e) {	}
		}

		return links;
	}
}
