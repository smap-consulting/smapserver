import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;


public class Mdu {
	private class Address {
		public String building_name;
		public int number_first;
		public int number_last;
		public String street_name;
		public String locality_name;
		public String postcode;
		public String lon;
		public String lat;
		
		public Address(String bn, int nf, int nl, String sn, String ln, String p,
				String lon, String lat) {
			building_name = bn;
			number_first = nf;
			number_last = nl;
			street_name = sn;
			locality_name = ln;
			postcode = p;
			this.lon = lon;
			this.lat = lat;
		}
	}
	
	void apply(boolean testRun, Connection sd, Connection results, 
			String results_table, String survey_id) throws Exception {

		String sql = null;
		PreparedStatement pstmt = null;
		
		// 1. Define the addresses
		ArrayList<Address> addresses = new ArrayList<Address> ();

		addresses.add(new Address("building1", 50, 55, "Smith Street", "Brunswick West", "3055", "144.97", "-37.4"));
		addresses.add(new Address("building2", 12, 36, "Jones Street", "Brunswick West", "3055", "144.95", "-37.8"));

		// 2. Remove existing data
		sql = "truncate table tasks cascade;";
		pstmt = sd.prepareStatement(sql);
		pstmt.executeUpdate();
		sql = "delete from locations;";
		pstmt = sd.prepareStatement(sql);
		pstmt.executeUpdate();
		sql = "delete from " + results_table + ";";
		pstmt = results.prepareStatement(sql);
		pstmt.executeUpdate();
		
		// Locations statement
		sql = "insert into locations (name, number, street, locality, postcode, country, " +
				"geo_type,  geo_point) " +
				"values(?,?,?,?,?,?,?,GeometryFromText(?, 4326));";
		PreparedStatement pstmtLoc = sd.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		
		// Results table statement
		sql = "insert into " + results_table + " (parkey, building_name_init, building_name_update, number_first_init, number_last_init," +
				"number_first_update, number_last_update, " +
				"street_name_init, street_name_update," +
				"locality_name_init, locality_name_update) " +
				"values(0, ?,?,?,?,?,?,?,?,?,?);";
		PreparedStatement pstmtRes = results.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		
		// Tasks table statement
		sql = "insert into tasks (location_id, type, title, url, form_id, initial_data, assignment_mode " +
				",dynamic_open) " +
				"values(?,?,?,?,?,?,?,'true');";
		PreparedStatement pstmtTas = sd.prepareStatement(sql);
		
		ResultSet generatedKeys = null;
		int l_id = -1;
		int r_id = -1;
		for(int i = 0; i < addresses.size(); i++) {
			Address a = addresses.get(i);
			
			// 3. Write to the Locations table
			String locnString = "POINT(" + a.lon + " " + a.lat + ")";
			
			pstmtLoc.setString(1, a.building_name);
			pstmtLoc.setInt(2, a.number_first);
			pstmtLoc.setString(3, a.street_name);
			pstmtLoc.setString(4, a.locality_name);
			pstmtLoc.setString(5, a.postcode);
			pstmtLoc.setString(6, "Australia");
			pstmtLoc.setString(7, "POINT");
			pstmtLoc.setString(8, locnString);
			
			pstmtLoc.executeUpdate();
			generatedKeys = pstmtLoc.getGeneratedKeys();
			if(generatedKeys.next()) {
				l_id = generatedKeys.getInt(1);
			} else {
				throw new Exception("Failed to get location key");
			}
			
			// 4. Write to the results table
			
			pstmtRes.setString(1, a.building_name);
			pstmtRes.setString(2, a.building_name);
			pstmtRes.setInt(3, a.number_first);
			pstmtRes.setInt(4, a.number_last);
			pstmtRes.setInt(5, a.number_first);
			pstmtRes.setInt(6, a.number_last);
			pstmtRes.setString(7, a.street_name);
			pstmtRes.setString(8, a.street_name);
			pstmtRes.setString(9, a.locality_name);
			pstmtRes.setString(10, a.locality_name);
			
			pstmtRes.executeUpdate();
			generatedKeys = pstmtRes.getGeneratedKeys();
			if(generatedKeys.next()) {
				r_id = generatedKeys.getInt(1);
			} else {
				throw new Exception("Failed to get location key");
			}
			
			// 5. Write to the tasks table
			String url = "http://10.0.2.2/formXML?key=" + survey_id;
			String instanceURL = "http://10.0.2.2/instanceXML/mdu/" + r_id;
			
			pstmtTas.setInt(1, l_id);
			pstmtTas.setString(2, "xform");
			pstmtTas.setString(3, "Validate MDU details");
			pstmtTas.setString(4, url);
			pstmtTas.setString(5, "mdu");
			pstmtTas.setString(6, instanceURL);
			pstmtTas.setString(7, "dynamic");		// Dynamic assignments
			
			
			pstmtTas.executeUpdate();
			
			System.out.println("l_id:" + l_id + " r_id:" + r_id);
			
		}
		
	}

}
