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
import org.smap.server.entities.Project;
import org.smap.server.entities.Survey;

public class JdbcProjectManager {

	PreparedStatement pstmtGetById = null;
	String sqlGet = "select "
			+ "id, "
			+ "o_id,"
			+ "name,"
			+ "changed_by "
			+ "from project where ";
	String sqlIdWhere = "id = ?;";

	
	public JdbcProjectManager(Connection sd) throws SQLException {
		pstmtGetById = sd.prepareStatement(sqlGet + sqlIdWhere);
	}
	

	
	public Project getById(int id) throws SQLException {
		pstmtGetById.setInt(1, id);
		return getProject(pstmtGetById);
	}
	
	
	public void close() {
		try {if(pstmtGetById != null) {pstmtGetById.close();}} catch(Exception e) {};
	}
	
	private Project getProject(PreparedStatement pstmt) throws SQLException {
		Project p = null;
		
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			p = new Project();
			p.setId(rs.getInt(1));
			p.setOId(rs.getInt(2));
			p.setName(rs.getString(3));
			p.setChangedBy(rs.getString(4));
		}
		return p;
	}
}
