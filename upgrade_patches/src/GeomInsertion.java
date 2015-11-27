import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class GeomInsertion {
	void apply(boolean testRun, Connection sd, Connection results) throws SQLException {
		Statement stmtRel = results.createStatement();
		Statement stmtRel2 = results.createStatement();
		
		double minLon = 0.0, maxLon = 0.0;
		double minLat = 0.0, maxLat = 0.0;
		double lon, lat;

		String sql = "select adp, community, prikey from s92_uganda_brat_hh_22_nov order by adp, community asc;";
		ResultSet records = stmtRel.executeQuery(sql);
		
		// Get the records
		while(records.next()) {
			
			String adp = records.getString(1);
			String community = records.getString(2);
			int prikey = records.getInt(3);
			
			if(adp.equals("adp_kachonga")) {
				System.out.println("Kachonga");
				if(community.equals("community_chadongo")) {
					System.out.println("	community_chadongo");
					
					minLon = 34.0814;
					maxLon = 34.0969;
					minLat = 0.9587;
					maxLat = 0.9789;
					
				} else if(community.equals("community_nabiganda")) {
					System.out.println("	community_nabiganda");
					
					minLon = 34.11;
					maxLon = 34.12;
					minLat = 0.97;
					maxLat = 0.98;
					
				} else {
					System.out.println("	-----------> Error unknown community:" + community);
				}
			
			
			} else if(adp.equals("adp_budumba")) {
				System.out.println("Budumba");
				if(community.equals("community_mabale")) {
					System.out.println("	Mabale");
					
					minLon = 33.8326;
					maxLon = 33.8417;
					minLat = 0.7995;
					maxLat = 0.8256;
					
				} else if(community.equals("community_mahuyu")) {
					System.out.println("	Mahuyu");
					
					minLon = 33.8108;
					maxLon = 33.8461;
					minLat = 0.8161;
					maxLat = 0.8355;
					
				} else if(community.equals("community_busabi")) {
					System.out.println("	Busabi");
					
					minLon = 33.8372;
					maxLon = 33.8964;
					minLat = 0.7837;
					maxLat = 0.8299;
					
				} else if(community.equals("community_bunghanga")) {
					System.out.println("	Bunghanga");
					
					minLon = 33.8249;
					maxLon = 33.9119;
					minLat = 0.7708;
					maxLat = 0.8060;
					
				} else {
					System.out.println("	-----------> Error unknown community:" + community);
				}
			} else {
				System.out.println("------------> Error unknown adp:" + adp);
			}
			
			lon = minLon + Math.random() * (maxLon - minLon);
			lat = minLat + Math.random() * (maxLat - minLat);
			
			System.out.println(lon + " : " + lat);
			
			String sqlUp = "update s92_uganda_brat_hh_22_nov set the_geom = GeometryFromText('POINT(" + 
					lon + " " + lat + ")', 4326) where prikey = " + prikey + ";";
			
			if(testRun) {
				System.out.println("     SQL: " + sqlUp);
			} else {
				stmtRel2.execute(sqlUp);
			}
			
		}
	}

}
