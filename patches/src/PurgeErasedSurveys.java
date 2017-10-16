import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;


public class PurgeErasedSurveys {
	
	String device = null;
	
	private class Data {
		int id;
		String dir;
		public Data(int id, String dir) {
			this.id = id;
			this.dir = dir;
		}
	}
	
	void apply(boolean testRun, Connection sd) throws Exception {
		
		ArrayList<Data> ids = getSurveyIds();
		System.out.println("Number of surveys to check for survey erasure: " + ids.size());
		
		String sql = "select count(*) from survey where s_id = ?";
		PreparedStatement pstmt = sd.prepareStatement(sql);
		
		for(Data item : ids) {
			pstmt.setInt(1,  item.id);
			ResultSet rs = pstmt.executeQuery();
			
			int count = 0;
			if(rs.next()) {
				count = rs.getInt(1);
			}
			if(count == 0) {
				if(!testRun) {
					System.out.println("Erase directory: " + item.dir);
					File d = new File(item.dir);
					if(d.isDirectory()) {
						Utilities.deleteDirectory(d);	
						Utilities.deleteFile(d);
					} else {
						Utilities.deleteFile(d);					
					}
				} else {
					System.out.println("Need to erase directory: " + item.dir);
				}
			}
		}
	}
	


	private ArrayList<Data> getSurveyIds() {
		ArrayList<Data> ids = new ArrayList<Data> ();
		
		File folder = new File("/smap/uploadedSurveys");
		File[] files = folder.listFiles();
		for(int i = 0; i < files.length; i++) {
			String dir = files[i].getPath();
			String [] a = dir.split("_");
			if(a.length > 0) {
				String stringId = a[a.length - 1];
				try {
					int id = Integer.parseInt(stringId);
					if(id > 0) {
						ids.add(new Data(id, dir));
					}
				} catch (Exception e) {
					System.out.println("Could not convert:" + stringId + " to integer");
				}
			}
			
		}
		return ids;
	}
}

