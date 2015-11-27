import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class NormalisePorts {
	void apply(boolean testRun, Connection results) throws SQLException {
	
		Statement stmtRel = results.createStatement();


		String sql1 = "select gid, portxlocidxaddresses from multiports;";
		String sql2 = "insert into mtu_ports (gid, mtu_id, port, address) values (?, ?, ?, ?);";
		PreparedStatement insertStatement = results.prepareStatement(sql2);
		
		
		ResultSet multiports = stmtRel.executeQuery(sql1);
	
		// Get the tables
		int id = 1;
		while(multiports.next()) {
			
			int mtu_id = multiports.getInt(1);
			String pa = multiports.getString(2);
			String address = null;
			
			System.out.println(mtu_id + " : " + pa);
			
			// Extract the ports
			int idx1 = 0;
			int idx2;
			while((idx2 = pa.indexOf("LOC", idx1)) > -1) {
				String port = pa.substring(idx1, idx2);
				
				int idxa1 = pa.indexOf(" - ", idx2 + 3);
				int idxa2 = pa.indexOf("LOC", idxa1 + 1);
				if(idxa2 < 0) {
					idxa2 = pa.indexOf(" - ", idxa1 + 1);
				}
				if(idxa2 > 0) {
					address = pa.substring(idxa1 + 3, idxa2 - 1);
				} else {
					
					address = pa.substring(idxa1 + 3);
				}
				
				System.out.println("         " + port + " : " + address);
				insertStatement.setInt(1, id);
				insertStatement.setInt(2, mtu_id);
				insertStatement.setInt(3, Integer.parseInt(port));
				insertStatement.setString(4, address);
				insertStatement.execute();
				id++;
				
				idx1 = pa.indexOf("LOC", idx2 + 1) - 1;	// Assumes maximum of 9 ports!
				if(idx1 < 0) {
					break;
				}
			}
			

			

		}
		


	}

}
