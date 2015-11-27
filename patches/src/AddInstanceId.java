import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class AddInstanceId {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
		Statement stmtSD = sd.createStatement();
		Statement stmtRel = results.createStatement();


		String sql = "select table_name from form where parentform is null;";
		ResultSet tables = stmtSD.executeQuery(sql);
		
		// Get the tables
		while(tables.next()) {
			
			String tableName = tables.getString(1).toLowerCase();
			//System.out.println("Table:" + tableName);

			
			// add instance id
			sql = "alter table " + tableName + " add column _instanceid text;";
			if(testRun) {
				System.out.println("     SQL: " + sql);
			} else {
				System.out.println("=======> About to patch: " + tableName);
				try {
					stmtRel.execute(sql);
					results.commit();
					System.out.println("    Patched:"+ tableName);
				} catch (Exception e) {
					System.out.println("    Failed to patch:"+ tableName);
					e.printStackTrace();
					results.rollback();
				}

			}
	
		}
	}

}
