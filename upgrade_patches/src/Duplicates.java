import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class Duplicates {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
	
		Statement stmtRel = results.createStatement();
		Statement stmtRel2 = results.createStatement();
		Statement stmtRel3 = results.createStatement();
		Statement stmtRel4 = results.createStatement();
		Statement stmtRel5 = results.createStatement();

		String sql1 = "select table_name from information_schema.columns where column_name = '_instanceid'";
		String sql2 = "select table_name from information_schema.columns where column_name = 'instanceid'";
		
		ResultSet tables1 = stmtRel.executeQuery(sql1);
	
		// Get the tables
		while(tables1.next()) {
			
			String tableName = tables1.getString(1).toLowerCase();
			
			// Get the duplicates
			String sqld1 = "select count(*), _instanceid from " + tableName + " where _bad = 'false' group by _instanceid;";
			
			ResultSet d1 = stmtRel2.executeQuery(sqld1);
			while(d1.next()) {
				int count = d1.getInt(1);
				String uuid = d1.getString(2);

				if(testRun) {
					System.out.println("=======> Dups _inst: " + tableName + " : " + uuid + " : " + count) ;
				} else if (count > 1 && uuid != null) {
					System.out.println("=======> Dups _inst: " + tableName + " : " + uuid + " : " + count) ;
				}
			}

		}
		
		// Get the tables
		ResultSet tables2 = stmtRel3.executeQuery(sql2);
		while(tables2.next()) {
			
			String tableName = tables2.getString(1).toLowerCase();
			
			// Get the duplicates
			String sqld2 = "select count(*), instanceid from " + tableName + " where _bad = 'false'  group by instanceid;";
			
						
			ResultSet d2 = stmtRel4.executeQuery(sqld2);
			while(d2.next()) {
				int count = d2.getInt(1);
				String uuid = d2.getString(2);
				if(testRun) {
					System.out.println("=======> Dups inst: " + tableName + " : " + uuid + " : " + count) ;	
				} else if (count > 1  && uuid != null) {
					System.out.println("=======> Dups inst: " + tableName + " : " + uuid + " : " + count) ;
					String sqld2_i = "select prikey from " + tableName + " where instanceid = '" + uuid + "';";
					ResultSet d2_i = stmtRel5.executeQuery(sqld2_i);
					while(d2_i.next()) {
						System.out.println("                 " + d2_i.getString(1) + " : " + uuid);
					}
					
				}
			}
		}

	}

}
