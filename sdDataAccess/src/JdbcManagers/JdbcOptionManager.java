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
import java.sql.SQLException;

import org.smap.server.entities.Option;

public class JdbcOptionManager {

	PreparedStatement pstmt = null;
	String sql = "insert into option ("
			+ "o_id, "
			+ "seq, "
			+ "label, "
			+ "label_id, "
			+ "ovalue, "
			+ "cascade_filters, "
			+ "externalfile, "
			+ "column_name, "
			+ "l_id, "
			+ "published) "
			+ "values (nextval('o_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	
	public JdbcOptionManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql);
	}
	
	public void write(Option o) throws SQLException {
		pstmt.setInt(1, o.getSeq());
		pstmt.setString(2, o.getLabel());
		pstmt.setString(3, o.getLabelId());
		pstmt.setString(4, o.getValue());
		pstmt.setString(5, o.getCascadeFilters());
		pstmt.setBoolean(6, o.getExternalFile());
		pstmt.setString(7, o.getColumnName());
		pstmt.setInt(8,  o.getListId());
		pstmt.setBoolean(9, false);	// published
		pstmt.executeUpdate();
	}
	
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
	}
}
