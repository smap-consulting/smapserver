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

package JdbcManagers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.smap.server.entities.Translation;

public class JdbcTranslationManager {

	PreparedStatement pstmt = null;
	String sql = "insert into translation (s_id, language, text_id, type, value) values (?, ?, ?, ?, ?);";
	
	PreparedStatement pstmtGetBySurveyId = null;
	String sqlGet = "select "
			+ "t_id,"
			+ "s_id,"
			+ "language,"
			+ "text_id,"
			+ "type,"
			+ "value "
			+ "from translation where ";
	String sqlGetBySurveyId = "s_id = ? "
			+ "order by language;";
			
	/*
	 * Constructor
	 */
	public JdbcTranslationManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql);
		pstmtGetBySurveyId = sd.prepareStatement(sqlGet + sqlGetBySurveyId);
	}
	
	/*
	 * Write a new translation object to the database
	 */
	public void write(int sId, String language, String text_id, String type, String value) throws SQLException {
		pstmt.setInt(1, sId);
		pstmt.setString(2, language);
		pstmt.setString(3, text_id);
		pstmt.setString(4, type);
		pstmt.setString(5, value);
		
		System.out.println("Write translation: " + pstmt.toString());
		pstmt.executeUpdate();
	}
	
	/*
	 * Write multiple translation objects to the database
	 */
	public void persistBatch(int sId, Collection<HashMap<String, Translation>> l) throws SQLException {
		
		Iterator<HashMap<String,Translation>> itrL = l.iterator();
		while(itrL.hasNext()) {								
			HashMap<String,Translation> types = (HashMap<String, Translation>) itrL.next();
			
			Collection<Translation> t = types.values();
			Iterator<Translation> itrT = t.iterator();

			while(itrT.hasNext()) {
				Translation trans = (Translation) itrT.next();
				System.out.println("Write: " + trans.getValue());
				write(sId, trans.getLanguage(), trans.getTextId(), trans.getType(), trans.getValue());
			}
		}
			
	}
	
	/*
	 * Get translations by survey id
	 */
	public List<Translation> getBySurveyId(int sId) throws SQLException {
		pstmtGetBySurveyId.setInt(1, sId);
		return getTranslationList(pstmtGetBySurveyId);
	}
	/*
	 * Close prepared statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtGetBySurveyId != null) {pstmtGetBySurveyId.close();}} catch(Exception e) {};
	}
	
	private List<Translation> getTranslationList(PreparedStatement pstmt) throws SQLException {
	
		ArrayList <Translation> trans = new ArrayList<Translation> ();
		
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			Translation t = new Translation();
			
			t.setId(rs.getInt(1));
			t.setSurveyId(rs.getInt(2));
			t.setLanguage(rs.getString(3));
			t.setTextId(rs.getString(4));
			t.setType(rs.getString(5));
			t.setValue(rs.getString(6));
			
			trans.add(t);
		}
		return trans;
	}
}
