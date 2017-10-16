import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;


public class PurgeUploadedDirectories {
	
	String device = null;
	
	void apply(boolean testRun, Connection sd) throws Exception {
		
		ArrayList<String> dirs = getDirectories();
		System.out.println("Number of directories to delete: " + dirs.size());
		
		String sql = "select count(*) from upload_event where file_path like ? and (upload_time > (now() - interval '180 days')) ";
		PreparedStatement pstmt = sd.prepareStatement(sql);
		
		for(String dir : dirs) {
			pstmt.setString(1,  dir + "%");
			ResultSet rs = pstmt.executeQuery();
			
			int count = 0;
			if(rs.next()) {
				count = rs.getInt(1);
			}
			if(count == 0) {
				if(!testRun) {
					File d = new File(dir);
					if(d.isDirectory()) {
						Utilities.deleteDirectory(d);
						Utilities.deleteFile(d);
					} else {
						Utilities.deleteFile(d);
					
					}
				}
				System.out.println("Need to erase: " + dir);
			}
		}
	}
	

	private ArrayList<String> getDirectories() {
		ArrayList<String> dirs = new ArrayList<String> ();
		
		File folder = new File("/smap/uploadedSurveys");
		File[] files = folder.listFiles();
		Date now = new Date();
		long nowMs = now.getTime();
		for(int i = 0; i < files.length; i++) {
			String dir = files[i].getPath();
			long ageMs = nowMs - files[i].lastModified();
			long ageDays = ageMs / (1000 * 3600 * 24);
			if(ageDays > 180) {
				dirs.add(dir);
			} else {
				// Don't delete this one
			}
		}
		return dirs;
	}
}

