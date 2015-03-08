package utilities;

/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Manage media files
 * Media information is returned in the format required by JQuery-File-Upload as per
 *    https://github.com/blueimp/jQuery-File-Upload/wiki/Setup
 */
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.smap.sdal.Utilities.UtilityMethods;

import model.MediaItem;
import surveyKPI.Dashboard;

public class MediaInfo {
	
	private static Logger log =
			 Logger.getLogger(MediaInfo.class.getName());
	

	private File folder = null;
	String folderUrl = null;
	String folderPath = null;
	String server = null;
	String settings = null;
	
	public MediaInfo(File f) {			
		folder = f;
	}
	
	public MediaInfo() {			
		folder = null;
	}
	
	/*
	 * Set folder from survey id or the survey Ident
	 */
	public boolean setFolder(String basePath, int sId, String sIdent, Connection conn) {
		boolean status = false;
	
		if(sIdent == null) {
			folderUrl = getUrlForSurveyId(sId, conn);
		} else {
			folderUrl = "media/" + sIdent;
		}
				
		
		if(folderUrl != null) {
			folderPath = basePath + "/" + folderUrl;
			folder = new File(folderPath);	
			createFolders(folderPath);
				
			status = true;
				
		} 
		
		return status;
	}
	
	/*
	 * Set media folder to the organisation folder for the provided user
	 */
	public boolean setFolder(String basePath, String user, String aSettings, Connection conn) {
		boolean status = false;
		
		// Get the organisation id
		String organisationId = null;
		String sql = "select o_id from users where ident = ?;";
		PreparedStatement pstmt = null;
		
		settings = aSettings;
		
		System.out.println("Settings: " + settings);
		
		try {
			pstmt = conn.prepareStatement(sql);	
			pstmt.setString(1, user);
			log.info("SQL: " + pstmt.toString() );
			
			ResultSet resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				
				organisationId = resultSet.getString(1);	
				folderUrl = "media/organisation/" + organisationId;
				if(settings != null && !settings.equals("false")) {
					folderUrl += "/settings";
				}
				folderPath = basePath + "/" + folderUrl;
				folder = new File(folderPath);
				
				createFolders(folderPath);
				
				status = true;
				
			} else {
				throw new Exception("Organisation not found for user: " + user);
			}
							
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}};
		}		
		
		return status;
	}
	
	public void setServer(String url) {

		if(url != null) {
			int a = url.indexOf("//");
			int b = url.indexOf("/", a + 2);
			if(a > 0 && b > a) {
				server = url.substring(0, b) + "/";
			}
		}

	}
	
	/*
	 * Getters
	 */
	public ArrayList<MediaItem> get() {
		ArrayList<MediaItem> media = new ArrayList<MediaItem> ();
		
		if(folder != null) {
			ArrayList <File> files = new ArrayList<File> (FileUtils.listFiles(folder, FileFilterUtils.fileFileFilter(), null));
			
			for(int i = 0; i < files.size(); i++) {
				MediaItem mi = new MediaItem();
				File f = files.get(i);
				mi.name = f.getName();
				mi.size = f.length();
				if(server != null) {
					mi.url = server + folderUrl + "/" + mi.name;
					
					String contentType = UtilityMethods.getContentType(mi.name);
					String thumbName = mi.name;
					
					// Set type
					if(contentType.startsWith("image")) {
						mi.type = "image";
					} else if(contentType.startsWith("video")) {
						mi.type = "video";
					} else if(contentType.startsWith("audio")) {
						mi.type = "audio";
					} else {
						mi.type = "unknown";
					}
					
					if(!contentType.startsWith("image")) {		// Thumbnail has extension jpg
						
						int idx = mi.name.lastIndexOf('.');
						if(idx > 0) {
							thumbName = mi.name.substring(0, idx + 1) + "jpg";
						}
					}
					
					if(contentType.startsWith("text")) {
						mi.thumbnailUrl = "/images/csv.png";
					} else if(contentType.startsWith("audio")) {
						mi.thumbnailUrl = "/images/audio.png";
					} else {
						mi.thumbnailUrl = server + folderUrl + "/thumbs/" + thumbName;
					}
					mi.deleteUrl = server + "surveyKPI/upload" + "/" + folderUrl + "/" + mi.name; 
				} else {
					log.log(Level.SEVERE, "Media Server is null");
				}
				mi.deleteType = "DELETE";
				media.add(mi);
			}
		} else {
			log.info("Error: Get media: folder is null" );
		}
		return media;
	}
	
	public String getPath() {
		return folderPath;
	}
	
	public String getFileName(String initialFileName) {
		String fileName = initialFileName;
		
		if(settings != null && settings.equals("true")) {
			fileName = "bannerLogo";
		}
		return fileName;
	}
	
	/*
	 * Return the path to the file when it is specific to a survey
	 */
	public String getUrlForSurveyId(int sId, Connection conn) {
		
		// Get the survey ident
		String survey_ident = null;
		String sql = "select ident from survey where s_id = ?;";
		PreparedStatement pstmt = null;
		String url = null;
		
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("SQL: " + pstmt.toString());
			
			ResultSet resultSet = pstmt.executeQuery();
			if(resultSet.next()) {
				
				survey_ident = resultSet.getString(1);
				url = "media/" + survey_ident;
				
				
			} else {
				log.info("Error: Form identifier not found for form id: " + sId);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}};
		}		
		return url;
	}

	private void createFolders(String path) {
		try {
			String thumbsPath = path + "/thumbs";
			File thumbsFolder = new File(thumbsPath);
			FileUtils.forceMkdir(folder);
			FileUtils.forceMkdir(thumbsFolder);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
}
