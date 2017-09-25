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
import java.util.Stack;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.GetXForm;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Element;

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
			+ "parameters,"
			+ "nodeset,"
			+ "nodeset_value,"
			+ "nodeset_label,"
			+ "column_name,"
			+ "published,"
			+ "l_id,"
			+ "autoplay,"
			+ "accuracy,"
			+ "dataType"
			+ ") "
			+ "values (nextval('q_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	
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
			+ "parameters,"
			+ "nodeset,"
			+ "nodeset_value,"
			+ "nodeset_label,"
			+ "column_name,"
			+ "published,"
			+ "l_id,"
			+ "autoplay,"
			+ "accuracy,"
			+ "dataType "
			+ "from question where soft_deleted = 'false' and ";
	String sqlGetBySurveyId = "f_id in (select f_id from form where s_id = ?)"
			+ " order by f_id, seq";
	String sqlGetByFormId = "f_id  = ?"
			+ " order by seq";
	
	String sqlGetListName = "select name from listname where l_id = ?";
	PreparedStatement pstmtGetListName;
	
	/*
	 * Constructor
	 */
	public JdbcQuestionManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		pstmtGetBySurveyId = sd.prepareStatement(sqlGet + sqlGetBySurveyId);
		pstmtGetByFormId = sd.prepareStatement(sqlGet + sqlGetByFormId);
		pstmtGetListName = sd.prepareStatement(sqlGetListName);
	}
	
	/*
	 * Write to the database
	 */
	public void write(Question q, String xFormRoot) throws Exception {
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
		pstmt.setString(15, q.getRelevant(false, null, xFormRoot));
		pstmt.setString(16, q.getCalculate(false, null, xFormRoot));
		pstmt.setString(17, q.getConstraint(false, null, xFormRoot));
		pstmt.setString(18, q.getConstraintMsg()); // ok
		pstmt.setString(19, q.getRequiredMsg());
		pstmt.setString(20, q.getAppearance(false, null));
		pstmt.setString(21, q.getParameters());
		
		String nodeset = null;
		String nodeset_value = null;
		String nodeset_label = null;
		if(q.getType().startsWith("select")) {
			
			//nodeset = q.getNodeset(false, true, null, false);
			nodeset = UtilityMethods.getNodeset(false, 
					true, 
					null, 
					false,
					q.getNodeset(),
					q.getAppearance(false, null),
					q.getFormId());
			if(nodeset == null || nodeset.trim().length() == 0) {
				// the remaining item list values TODO is there an issue with this??????
				nodeset = GeneralUtilityMethods.getNodesetFromChoiceFilter(null, q.getListName());
				nodeset_value = "name";
				nodeset_label = "jr:itext(itextId)";
				//cascade_instance = q.getListName();
			} else {
				nodeset_value = q.getNodesetValue();
				nodeset_label = q.getNodesetLabel();
			}
			
		}	
		pstmt.setString(22, nodeset);
		pstmt.setString(23, nodeset_value);
		pstmt.setString(24, nodeset_label);
		
		pstmt.setString(25, q.getColumnName()); 
		pstmt.setBoolean(26, q.isPublished());
		pstmt.setInt(27, q.getListId());  
		pstmt.setString(28, q.getAutoPlay());
		pstmt.setString(29, q.getAccuracy());
		pstmt.setString(30, q.getDataType());
		
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
	public List <Question> getBySurveyId(int sId, List <Form> forms) throws SQLException {
		pstmtGetBySurveyId.setInt(1, sId);
		return getQuestionList(pstmtGetBySurveyId, forms);
	}
	
	/*
	 * Get a list of questions in the passed in form
	 */
	public List <Question> getByFormId(int fId) throws SQLException {
		pstmtGetByFormId.setInt(1, fId);
		return getQuestionList(pstmtGetByFormId, null);
	}
	
	/*
	 * Close prepared statements
	 */
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
		try {if(pstmtGetByFormId != null) {pstmtGetByFormId.close();}} catch(Exception e) {};
		try {if(pstmtGetBySurveyId != null) {pstmtGetBySurveyId.close();}} catch(Exception e) {};
	}
	
	private List<Question> getQuestionList(PreparedStatement pstmtGet, 
			List <Form> forms) throws SQLException {
		
		ArrayList <Question> questions = new ArrayList<Question> ();
		Stack<String> paths = new Stack<String>();
		String currentPath = "";
		
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
			q.setParameters(rs.getString(22));
			q.setNodeset(rs.getString(23));
			q.setNodesetValue(rs.getString(24));
			q.setNodesetLabel(rs.getString(25));
			q.setColumnName(rs.getString(26));
			q.setPublished(rs.getBoolean(27));
			q.setListId(rs.getInt(28));
			q.setAutoPlay(rs.getString(29));
			q.setAccuracy(rs.getString(30));
			q.setDataType(rs.getString(31));
		
			/*
			 * If the list id exists then set the list name
			 */
			if(q.getListId() > 0) {
				pstmtGetListName.setInt(1, q.getListId());
				ResultSet rsListName = pstmtGetListName.executeQuery();
				if(rsListName.next()) {
					q.setListName(rsListName.getString(1));
				}
			}
			/*
			 * Set the relative path
			 */
			q.setRelativePath(currentPath + "/" + q.getName());
			if(q.getType().equals("begin group")) {
				paths.push(currentPath);
				currentPath = q.getRelativePath();
			} else if(q.getType().equals("end group")) {
				currentPath = paths.pop();
			}
			
			/*
			 * If this question marks the start of a sub form then set the relative path of the subform
			 */
			if(forms != null && (q.getType().equals("begin repeat") || q.getType().equals("geopolygon") || q.getType().equals("geolinestring"))) {
				for(Form f : forms) {
					if(f.getParentQuestionId() == q.getId()) {
						f.setRelativePath(q.getRelativePath());
					}
				}
			}
			
			questions.add(q);
		}
		return questions;
	}
}
