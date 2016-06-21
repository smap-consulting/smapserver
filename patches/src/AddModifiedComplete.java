import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class AddModifiedComplete {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
		Statement stmtSD = sd.createStatement();
		Statement stmtRel = results.createStatement();


		String sql = "select table_name from form where parentform is null or parentform = 0;";
		ResultSet tables = stmtSD.executeQuery(sql);
		
		// Get the tables
		while(tables.next()) {
			
			String tableName = tables.getString(1);
			if(tableName != null) {
				tableName = tableName.toLowerCase();

			
				// add new columns			
				sql = "alter table " + tableName + " add column _complete boolean default true; " +
						"alter table " + tableName + " add column _modified boolean default false; ";
				if(testRun) {
					System.out.println("     SQL: " + sql);
				} else {
					System.out.println("=======> About to patch: " + tableName);
					try {
						stmtRel.execute(sql);
						results.commit();
						System.out.println("    Patched:"+ tableName);
					} catch (Exception e) {
						if(e.getMessage().contains("does not exist")) {
							System.out.println("    No data collected, table does not exist");
							results.commit();
						} else if(e.getMessage().contains("already exists")) {
							System.out.println("    Already patched");
							results.commit();
						} else {
							System.out.println("    Failed to patch:"+ tableName);
							e.printStackTrace();
							results.rollback();
						}
					}
	
				}
			}
	
		}
	}

}
