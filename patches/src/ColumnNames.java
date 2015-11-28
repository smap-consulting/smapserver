import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class ColumnNames {
	void apply(boolean testRun, Connection sd) throws SQLException {
	

		String sql1 = "select q_id, qname from question where column_name_applied = 'false';";
		String sql2 = "update question set column_name = ?, column_name_applied = 'true' where q_id = ?;";
		
		String sql_option_1 = "select o_id, ovalue from option where column_name is null;";
		String sql_option_2 = "update option set column_name = ? where o_id = ?;";
		
		
		PreparedStatement pstmt1 = sd.prepareStatement(sql1);
		PreparedStatement pstmt2 = sd.prepareStatement(sql2);
		PreparedStatement pstmtOption1 = sd.prepareStatement(sql_option_1);
		PreparedStatement pstmtOption2 = sd.prepareStatement(sql_option_2);
		
		// Process Questions
		ResultSet q1Rs = pstmt1.executeQuery();
		while(q1Rs.next()) {
			
			int qId = q1Rs.getInt(1);
			String qName = q1Rs.getString(2);
			
			String column_name = DatabaseNames.cleanName(qName, true);
			if(testRun) {
				System.out.println("  ==== Converting question: " + qName + "      ->      " + column_name);
			} else {
				pstmt2.setString(1, column_name);
				pstmt2.setInt(2,  qId);
				pstmt2.executeUpdate();
				System.out.println("  ==== Converted question: " + qName + "      ->      " + column_name);
			}
		}
		
		// Process Questions
		ResultSet optionRs = pstmtOption1.executeQuery();
		while(optionRs.next()) {
			
			int oId = optionRs.getInt(1);
			String oValue = optionRs.getString(2);
			
			String column_name = DatabaseNames.cleanName(oValue, false);
			if(testRun) {
				System.out.println("  ==== Converting option: " + oValue + "      ->      " + column_name);
			} else {
				pstmtOption2.setString(1, column_name);
				pstmtOption2.setInt(2,  oId);
				pstmtOption2.executeUpdate();
				System.out.println("  ==== Converted option: " + oValue + "      ->      " + column_name);
			}
		}
		

	}

}
