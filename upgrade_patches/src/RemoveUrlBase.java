import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class RemoveUrlBase {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
		Statement stmtSD = sd.createStatement();
		Statement stmtSD2 = sd.createStatement();
		Statement stmtRel = results.createStatement();

		String sql = "select table_name, f_id from form;";
		ResultSet tables = stmtSD.executeQuery(sql);
		
		// Get the tables
		while(tables.next()) {
			
			String tableName = tables.getString(1).toLowerCase();
			//System.out.println("Table:" + tableName);
			int f_id = tables.getInt(2);
			
			// Get the attachment questions in this table
			sql = "select q.qname from question q " +
					" where q.f_id = " + f_id +
					" and (q.qtype = 'image' or q.qtype = 'video' or q.qtype = 'audio')";
			
			ResultSet questions = stmtSD2.executeQuery(sql);
			while(questions.next()) {
				String q = questions.getString(1).toLowerCase();
				
				updateTable(tableName, q, testRun, stmtRel);
				
			}
		}
		updateTable("reports", "thumb_url", testRun, stmtRel);
		updateTable("reports", "data_url", testRun, stmtRel);
	}
	
	void updateTable(String table, String column, boolean testRun, Statement stmtRel) {
		String sql = "update " + table + " set " + column + 
				"= regexp_replace(" + column + ",'^https?://[a-z0-9\\.\\-_]*/',  '');"; 
		String sql2 = "update " + table + " set " + column + 
				"= regexp_replace(" + column + ",'^//[a-z0-9\\.\\-_]*/',  '');"; 
	
		try {
			if(testRun) {
				System.out.println("     SQL: " + sql);
			} else {
				int count = stmtRel.executeUpdate(sql);
				count += stmtRel.executeUpdate(sql2);
				if(count > 0) {
					System.out.println("    Patched " + count + " rows in table + " + table + " for Question: " + column);
				}
			}
		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				// Survey has been deleted - not a problem
				System.out.println("    Survey with table " + table + " has been deleted");
			} else {
				System.out.println("    ## Error: " + e.getMessage());
			}
		}
	}

}
