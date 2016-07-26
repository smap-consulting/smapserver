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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.GetXForm;

public class JdbcQuestionManager {

	private static Logger log =
			 Logger.getLogger(GetXForm.class.getName());
	
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
			+ "required_msg,"
			+ "appearance,"
			+ "path,"
			+ "nodeset,"
			+ "nodeset_value,"
			+ "nodeset_label,"
			+ "cascade_instance,"
			+ "column_name,"
			+ "published,"
			+ "l_id,"
			+ "autoplay) "
			+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	
	PreparedStatement pstmtGetBySurveyId;
	PreparedStatement pstmtGetByFormId;
	String sqlGet = "select "
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
			+ "required_msg,"
			+ "appearance,"
			+ "path,"
			+ "nodeset,"
			+ "nodeset_value,"
			+ "nodeset_label,"
			+ "cascade_instance,"
			+ "column_name,"
			+ "published,"
			+ "l_id,"
			+ "autoplay "
			+ "from question where soft_deleted = 'false' and ";
	String sqlGetBySurveyId = "f_id in (select f_id from form where s_id = ?)"
			+ " order by f_id, seq";
	String sqlGetByFormId = "f_id  = ?"
			+ " order by seq";
	
	/*
	 * Constructor
	 */
	public JdbcQuestionManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		pstmtGetBySurveyId = sd.prepareStatement(sqlGet + sqlGetBySurveyId);
		pstmtGetByFormId = sd.prepareStatement(sqlGet + sqlGetByFormId);
	}
	
	/*
	 * Write to the database
	 */
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
		pstmt.setString(19, q.getRequiredMsg());
		pstmt.setString(20, q.getAppearance());
		pstmt.setString(21, q.getPath());
		pstmt.setString(22, q.getNodeset());
		pstmt.setString(23, q.getNodesetValue());
		pstmt.setString(24, q.getNodesetLabel());
		pstmt.setString(25, q.getCascadeInstance());
		pstmt.setString(26, q.getColumnName());
		pstmt.setBoolean(27, q.isPublished());
		pstmt.setInt(28, q.getListId());
		pstmt.setString(29, q.getAutoPlay());
		
		//log.info("Write question: " + pstmt.toString());
		pstmt.executeUpdate();
		
		ResultSet rs = pstmt.getGeneratedKeys();
		if(rs.next()) {
			q.setId(rs.getInt(1));
			
		}
	}
	
	/*
	 * Get a list of questions in the passed in survey
	 */
	public List <Question> getBySurveyId(int sId) throws SQLException {
		pstmtGetBySurveyId.setInt(1, sId);
		return getQuestionList(pstmtGetBySurveyId);
	}
	
	/*
	 * Get a list of questions in the passed in form
	 */
	public List <Question> getByFormId(int fId) throws SQLException {
		pstmtGetByFormId.setInt(1, fId);
		return getQuestionList(pstmtGetByFormId);
	}
	
	/*
	 * Close prepared statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtGetByFormId != null) {pstmtGetByFormId.close();}} catch(Exception e) {};
		try {if(pstmtGetBySurveyId != null) {pstmtGetBySurveyId.close();}} catch(Exception e) {};
	}
	
	private List<Question> getQuestionList(PreparedStatement pstmtGet) throws SQLException {
		
		ArrayList <Question> questions = new ArrayList<Question> ();
		
		log.info("Get question list: " + pstmtGet.toString());
		ResultSet rs = pstmtGet.executeQuery();
		while(rs.next()) {
			Question q = new Question();
			q.setId(rs.getInt(1));
			q.setFormId(rs.getInt(2));
			q.setSeq(rs.getInt(3));
			q.setName(rs.getString(4));
			q.setType(rs.getString(5));
			q.setQuestion(rs.getString(6));
			q.setQTextId(rs.getString(7));
			q.setDefaultAnswer(rs.getString(8));
			q.setInfo(rs.getString(9));
			q.setInfoTextId(rs.getString(10));
			q.setVisible(rs.getBoolean(11));
			q.setSource(rs.getString(12));
			q.setSourceParam(rs.getString(13));
			q.setReadOnly(rs.getBoolean(14));
			q.setMandatory(rs.getBoolean(15));
			q.setRelevant(rs.getString(16));
			q.setCalculate(rs.getString(17));
			q.setConstraint(rs.getString(18));
			q.setConstraintMsg(rs.getString(19));
			q.setRequiredMsg(rs.getString(20));
			q.setAppearance(rs.getString(21));
			q.setPath(rs.getString(22));
			q.setNodeset(rs.getString(23));
			q.setNodesetValue(rs.getString(24));
			q.setNodesetLabel(rs.getString(25));
			q.setCascadeInstance(rs.getString(26));
			q.setColumnName(rs.getString(27));
			q.setPublished(rs.getBoolean(28));
			q.setListId(rs.getInt(29));
			q.setAutoPlay(rs.getString(30));
		
			questions.add(q);
		}
		return questions;
	}
}
