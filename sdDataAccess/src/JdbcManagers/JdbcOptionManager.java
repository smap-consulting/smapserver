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
import java.util.List;

import org.smap.server.entities.Form;
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
			+ "l_id) "
			+ "values (nextval('o_seq'), ?, ?, ?, ?, ?, ?, ?, ?);";
	
	PreparedStatement pstmtGetByListId = null;
	String sqlGet = "select "
			+ "o_id, "
			+ "seq, "
			+ "label, "
			+ "label_id, "
			+ "ovalue, "
			+ "cascade_filters, "
			+ "externalfile, "
			+ "column_name, "
			+ "l_id "
			+ "from option where ";
	String sqlGetByListId = "l_id = ?;";
	
	String sqlOptionExists = "select o_id from option where l_id = ? and ovalue = ?;";
	PreparedStatement pstmtOptionExists = null;
	
	ResultSet rs = null;
	
	/*
	 * Constructor
	 */
	public JdbcOptionManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql);
		pstmtGetByListId = sd.prepareStatement(sqlGet + sqlGetByListId);
		pstmtOptionExists = sd.prepareStatement(sqlOptionExists);
	}
	
	/*
	 * Write the option to the database
	 * Only write this option if it has not already been added to this choice list
	 *   If more than one question share the same choice list each maintains its own list of options and 
	 *   duplicates can be created
	 */
	public void write(Option o) throws SQLException {
		
		pstmtOptionExists.setInt(1, o.getListId());
		pstmtOptionExists.setString(2, o.getValue());
		rs = pstmtOptionExists.executeQuery();
		
		if(!rs.next()) {
			pstmt.setInt(1, o.getSeq());
			pstmt.setString(2, o.getLabel());
			pstmt.setString(3, o.getLabelId());
			pstmt.setString(4, o.getValue());
			pstmt.setString(5, o.getCascadeFilters());
			pstmt.setBoolean(6, o.getExternalFile());
			pstmt.setString(7, o.getColumnName());
			pstmt.setInt(8,  o.getListId());
			pstmt.executeUpdate();
		}
	}
	

	/*
	 * Get options by list id
	 */
	public List<Option> getByListId(int l_id) throws SQLException {
		pstmtGetByListId.setInt(1, l_id);
		return getOptionList(pstmtGetByListId);
	}
	
	/*
	 * Close prepared statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtGetByListId != null) {pstmtGetByListId.close();}} catch(Exception e) {};
		try {if(pstmtOptionExists != null) {pstmtOptionExists.close();}} catch(Exception e) {};
		try {if(rs != null) {rs.close();}} catch(Exception e) {};
	}
	
	private List <Option> getOptionList(PreparedStatement pstmt) throws SQLException {
		ArrayList <Option> options = new ArrayList<Option> ();
		
		ResultSet rs = pstmt.executeQuery();
		while(rs.next()) {
			Option o = new Option();
			o.setId(rs.getInt(1));
			o.setSeq(rs.getInt(2));
			o.setLabel(rs.getString(3));
			o.setLabelId(rs.getString(4));
			o.setValue(rs.getString(5));
			o.setCascadeFilters(rs.getString(6));
			o.setExternalFile(rs.getBoolean(7));
			o.setColumnName(rs.getString(8));
			o.setListId(rs.getInt(9));
			options.add(o);
		}
		return options;
	}
}
