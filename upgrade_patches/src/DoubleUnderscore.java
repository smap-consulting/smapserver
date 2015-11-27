import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class DoubleUnderscore {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
		Statement stmtSD = sd.createStatement();
		Statement stmtSD2 = sd.createStatement();
		Statement stmtRel = results.createStatement();
		Statement stmtRel2 = results.createStatement();

		String sql = "select table_name, f_id from form;";
		ResultSet tables = stmtSD.executeQuery(sql);
		
		// Get the tables
		while(tables.next()) {
			
			String tableName = tables.getString(1).toLowerCase();
			//System.out.println("Table:" + tableName);
			int f_id = tables.getInt(2);
			
			// Get the select multiple questions in this table
			sql = "select q.qname, o.ovalue from question q, option o " +
					" where q.q_id = o.q_id" +
					" and q.f_id = " + f_id +
					" and q.qtype = 'select'";
			
			ResultSet questions = stmtSD2.executeQuery(sql);
			while(questions.next()) {
				String q = questions.getString(1).toLowerCase();
				String o = questions.getString(2).toLowerCase();
				//System.out.println("Question: " + q + "__" + o);
				
				// Determine if this question-option needs patching
				sql = "select count(*) from information_schema.columns " +
						" where table_name='" + tableName + "'" +
						" and column_name='" + q + "_" + o + "';";
				
				ResultSet countRS = stmtRel.executeQuery(sql);
				if(countRS.next()) {
					int count = countRS.getInt(1);
					
					if(count > 0) {
				
						System.out.println("=======> About to patch"+ q + "_" + o);
						sql = "alter table " + tableName + " rename column " + q + "_" + o + " to " +
								q + "__" + o + ";";
						if(testRun) {
							System.out.println("     SQL: " + sql);
						} else {
							stmtRel2.execute(sql);
							System.out.println("    Patched"+ q + "__" + o);
						}
					}
				}
			}
		}
	}

}
