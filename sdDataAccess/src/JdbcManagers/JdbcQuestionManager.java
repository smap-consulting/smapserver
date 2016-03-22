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

import org.smap.server.entities.Option;
import org.smap.server.entities.Question;

public class JdbcQuestionManager {

	PreparedStatement pstmt = null;
	String sql = "insert into question ("
			+ "q_id, "
			+ "f_id, "
			+ "seq, "
			+ "qname, "
			+ "qtype, "
			+ "question, "
			+ "qtext_id, "
			+ "defaultanswer, "
			+ "info, "
			+ "infotext_id,"
			+ "visible,"
			+ "source,"
			+ "source_param,"
			+ "readonly,"
			+ "mandatory,"
			+ "relevant,"
			+ "calculate,"
			+ "qconstraint,"
			+ "constraint_msg,"
			+ "appearance,"
			+ "path,"
			+ "nodeset,"
			+ "nodeset_value,"
			+ "nodeset_label,"
			+ "cascade_instance,"
			+ "column_name,"
			+ "published,"
			+ "l_id) "
			+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?);";
	
	public JdbcQuestionManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
	}
	
	public void write(Question q) throws SQLException {
		pstmt.setInt(1, q.getFormId());
		pstmt.setInt(2, q.getSeq());
		pstmt.setString(3, q.getName());
		pstmt.setString(4, q.getType());
		pstmt.setString(5, q.getQuestion());
		pstmt.setString(6, q.getQTextId());
		pstmt.setString(7, q.getDefaultAnswer());
		pstmt.setString(8,  q.getInfo());
		pstmt.setString(9, q.getInfoTextId());	
		pstmt.setBoolean(10, q.isVisible());	
		pstmt.setString(11, q.getSource());	
		pstmt.setString(12, q.getSourceParam());	
		pstmt.setBoolean(13, q.isReadOnly());
		pstmt.setBoolean(14, q.isMandatory());
		pstmt.setString(15, q.getRelevant());
		pstmt.setString(16, q.getCalculate());
		pstmt.setString(17, q.getConstraint());
		pstmt.setString(18, q.getConstraintMsg());
		pstmt.setString(19, q.getAppearance());
		pstmt.setString(20, q.getPath());
		pstmt.setString(21, q.getNodeset());
		pstmt.setString(22, q.getNodesetValue());
		pstmt.setString(23, q.getNodesetLabel());
		pstmt.setString(24, q.getCascadeInstance());
		pstmt.setString(25, q.getColumnName());
		pstmt.setBoolean(26, q.isPublished());
		pstmt.setInt(27, q.getListId());
		
		pstmt.executeUpdate();
		
		ResultSet rs = pstmt.getGeneratedKeys();
		if(rs.next()) {
			q.setId(rs.getInt(1));
			
		}
	}
	
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
	}
}
