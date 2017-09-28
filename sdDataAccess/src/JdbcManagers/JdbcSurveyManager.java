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

	// Create new
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
			+ "instance_name,"
			+ "loaded_from_xls,"
			+ "created) "
			+ "values (nextval('s_seq'), ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, now());";
	
	// Update survey ident based on id
	PreparedStatement pstmtUpdate = null;
	String sqlUpdate = "update survey set "
			+ "ident = ? "
			+ "where s_id = ?;";
	
	// Retrieve
	PreparedStatement pstmtGetByIdent = null;
	PreparedStatement pstmtGetById = null;
	String sqlGet = "select "
			+ "s_id, "
			+ "name,"
			+ "display_name, "
			+ "p_id, "
			+ "def_lang, "
			+ "class,"
			+ "ident,"
			+ "version,"
			+ "manifest,"
			+ "instance_name,"
			+ "deleted,"
			+ "hrk,"
			+ "timing_data "
			+ "from survey where ";
	String sqlIdentWhere = "ident = ?;";
	String sqlIdWhere = "s_id = ?;";

	// Check existence
	PreparedStatement pstmtExists = null;
	String sqlExists = "select count(*) from survey where display_name = ? and p_id = ?;";
	
	/*
	 * Constructor
	 */
	public JdbcSurveyManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		pstmtUpdate = sd.prepareStatement(sqlUpdate);
		//pstmtUpdateSelfCalcs = sd.prepareStatement(sqlUpdateSelfCalcs);
		//pstmtUpdateSelfCalcsManifest = sd.prepareStatement(sqlUpdateSelfCalcsManifest);
		pstmtGetByIdent = sd.prepareStatement(sqlGet + sqlIdentWhere);
		pstmtGetById = sd.prepareStatement(sqlGet + sqlIdWhere);
		pstmtExists = sd.prepareStatement(sqlExists);
	}
	
	/*
	 * Store a new survey
	 */
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
		pstmt.setBoolean(10, s.getLoadedFromXls());
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
	
	/*
	 * Update a survey
	 */
	public void update(Survey s) throws SQLException {
		pstmtUpdate.setString(1, s.getIdent());
		pstmtUpdate.setInt(2, s.getId());
		pstmtUpdate.executeUpdate();
	}
	
	
	/*
	 * Update reference to self in calculations
	 *
	public void updateSelfCalcs(String ident, int sId) throws SQLException {
		pstmtUpdateSelfCalcs.setString(1, ident);
		pstmtUpdateSelfCalcs.setInt(2, sId);
		pstmtUpdateSelfCalcs.executeUpdate();
		
		pstmtUpdateSelfCalcsManifest.setString(1, ident);
		pstmtUpdateSelfCalcsManifest.setInt(2, sId);
		pstmtUpdateSelfCalcsManifest.executeUpdate();
	}
	*/
	
	/*
	 * Get a survey using its ident
	 */
	public Survey getByIdent(String ident) throws SQLException {
		pstmtGetByIdent.setString(1, ident);
		return getSurvey(pstmtGetByIdent);
	}
	
	/*
	 * Get a survey using its id
	 */
	public Survey getById(int id) throws SQLException {
		pstmtGetById.setInt(1, id);
		return getSurvey(pstmtGetById);
	}
	
	/*
	 * Return true if a survey exists with the specified name and projectId
	 */
	public boolean surveyExists(String name, int projectId) throws SQLException {
		
		boolean exists = false;
		
		pstmtExists.setString(1, name);
		pstmtExists.setInt(2,  projectId);
		ResultSet rs = pstmtExists.executeQuery();
		if(rs.next()) {
			if(rs.getInt(1) > 0) {
				exists = true;
			}
		}
		return exists;
	}
	
	/*
	 * Close statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtUpdate != null) {pstmtUpdate.close();}} catch(Exception e) {};
		//try {if(pstmtUpdateSelfCalcs != null) {pstmtUpdateSelfCalcs.close();}} catch(Exception e) {};
		//try {if(pstmtUpdateSelfCalcsManifest != null) {pstmtUpdateSelfCalcsManifest.close();}} catch(Exception e) {};
		try {if(pstmtGetByIdent != null) {pstmtGetByIdent.close();}} catch(Exception e) {};
		try {if(pstmtGetById != null) {pstmtGetById.close();}} catch(Exception e) {};
	}
	
	/*
	 * Common function to populate a survey object from the database
	 * The query used to find the survey is specified in the prepared statement
	 */
	private Survey getSurvey(PreparedStatement pstmt) throws SQLException {
		Survey s = null;
		
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			s = new Survey();
			s.setId(rs.getInt(1));
			s.setName(rs.getString(2));
			s.setDisplayName(rs.getString(3));
			s.setProjectId(rs.getInt(4));
			s.setDefLang(rs.getString(5));
			s.setSurveyClass(rs.getString(6));
			s.setIdent(rs.getString(7));
			s.setVersion(rs.getInt(8));
			s.setManifest(rs.getString(9));
			s.setInstanceName(rs.getString(10));
			s.setDeleted(rs.getBoolean(11));
			s.setHrk(rs.getString(12));
			s.setTimingData(rs.getBoolean(13));
		}
		return s;
	}
}
