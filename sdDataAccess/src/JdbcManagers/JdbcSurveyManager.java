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
import java.sql.Statement;

import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Survey;

public class JdbcSurveyManager {

	PreparedStatement pstmt = null;
	String sql = "insert into survey ("
			+ "s_id, "
			+ "name,"
			+ "last_updated_time, "
			+ "display_name, "
			+ "p_id, "
			+ "def_lang, "
			+ "class,"
			+ "ident,"
			+ "version,"
			+ "manifest,"
			+ "instance_name) "
			+ "values (nextval('s_seq'), ?, now(), ?, ?, ?, ?, ?, ?, ?, ?);";
	
	PreparedStatement pstmtUpdate = null;
	String sqlUpdate = "update survey set "
			+ "ident = ? "
			+ "where s_id = ?;";
	
	public JdbcSurveyManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		pstmtUpdate = sd.prepareStatement(sqlUpdate);
	}
	
	public void write(Survey s) throws SQLException {
		pstmt.setString(1, s.getName());
		pstmt.setString(2, s.getDisplayName());
		pstmt.setInt(3, s.getProjectId());
		pstmt.setString(4, s.getDefLang());
		pstmt.setString(5, s.getSurveyClass());
		pstmt.setString(6, s.getIdent());
		pstmt.setInt(7, s.getVersion());
		pstmt.setString(8, s.getManifest());
		pstmt.setString(9, s.getInstanceName());
		pstmt.executeUpdate();
					
		ResultSet rs = pstmt.getGeneratedKeys();
		if(rs.next()) {
			s.setId(rs.getInt(1));
			
			String ident = s.getIdent();
			if(ident == null || ident.trim().length() == 0) {
				String surveyName = "s" + s.getProjectId() + "_" + s.getId();
				s.setIdent(surveyName);
				update(s);
			}
		}
	}
	
	public void update(Survey s) throws SQLException {
		pstmtUpdate.setString(1, s.getIdent());
		pstmtUpdate.setInt(2, s.getId());
		pstmtUpdate.executeUpdate();
	}
	
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtUpdate != null) {pstmtUpdate.close();}} catch(Exception e) {};
	}
}
