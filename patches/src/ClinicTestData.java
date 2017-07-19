import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class ClinicTestData {
	void apply(Connection sd, Connection results) throws SQLException {
		
		Statement stmtSD = sd.createStatement();

		String sql = "select table_name from form where parentform = 0 "
				+ "and s_id in (select s_id from survey where display_name = 'Medical Clinic' and not deleted)";
		ResultSet tables = stmtSD.executeQuery(sql);
		
	
		// Get the tables
		String tableName = null;
		if(tables.next()) {
			
			tableName = tables.getString(1).toLowerCase();
			System.out.println("Medical Data in Table:" + tableName);

			String sqlInsert = "insert into " + tableName + "(id, name) values (?, ?)";
			PreparedStatement pstmt = results.prepareStatement(sqlInsert);
			for(int i = 0; i < 1000; i++) {
				pstmt.setString(1, String.valueOf(i));
				pstmt.setString(2, 'A' + String.valueOf(i));
				pstmt.executeUpdate();
			}
			results.commit();
			pstmt.close();
			
			System.out.println("complete");
	
		} else {
			System.out.println("Table: " + tableName + " not found");
		}
	}

}
