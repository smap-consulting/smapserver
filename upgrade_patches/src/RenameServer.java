import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class RenameServer {
	void apply(boolean testRun, Connection sd, Connection results, String old_name, String new_name) throws SQLException {
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
			
			// Get the questions that will contain links
			sql = "select q.qname from question q " +
					" where q.f_id = " + f_id +
					" and (q.qtype = 'image' or q.qtype = 'video' or q.qtype = 'audio')";
			
			ResultSet questions = stmtSD2.executeQuery(sql);
			while(questions.next()) {
				String q = questions.getString(1).toLowerCase();

				
				System.out.println("=======> About to patch: "+ q) ;
				
				// Remove the old name
				sql = "update " + tableName + " set " + q + "='//' || '" + new_name + "' || substr(" + q + ", " + old_name.length() + ", length(" + q + ") ) " +
						"where substr(q, 2, " + (old_name.length() + 2) + ") = '" + old_name + "';";
				
				if(testRun) {
					System.out.println("     SQL: " + sql);
				} else {
					stmtRel2.execute(sql);
					System.out.println("    Patched:"+ q);
				}
				
			}
		}
		
		// TODO patch reports tables
	}

}
