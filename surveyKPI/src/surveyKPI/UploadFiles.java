package surveyKPI;

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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/upload")
public class UploadFiles extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(UploadFiles.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UploadFiles.class);
		return s;
	}
 
	@POST
	@Path("/media")
	public void getEvents(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response
			) throws IOException {
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String serverName = request.getServerName();
		//String contextPath = request.getContextPath();
		String original_url = "/edit.html?mesg=error loading media file";
		String mesg = null;
		int sId = 0;
	
		log.info("upload files - media -----------------------");
		log.info("    Server:" + serverName);
		
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		boolean commitOpen = false;
		Connection connection = null; 
		PreparedStatement pstmt = null;
		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			//String qId = null;
			String oId = null;
			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				// Get form parameters
				
				if(item.isFormField()) {
					System.out.println("Form field:" + item.getFieldName() + " - " + item.getString());
				
					if(item.getFieldName().equals("original_url")) {
						original_url = item.getString();
						System.out.println("original url:" + original_url);
					} else if(item.getFieldName().equals("survey_id")) {
						sId = Integer.parseInt(item.getString());
						System.out.println("surveyId:" + sId);
						
						// Authorisation - Access
						Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
						a.isAuthorised(connectionSD, request.getRemoteUser());
						a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
						// End Authorisation
					}
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					System.out.println("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
					//String fileSuffix = null;
					//String itemType = item.getContentType();
	
					connection = SDDataSource.getConnection("fieldManager-MediaUpload");
					a.isAuthorised(connection, request.getRemoteUser());
					a.isValidSurvey(connection, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
							
					String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
							
					if(basePath == null) {
						basePath = "/smap";
					} else if(basePath.equals("/ebs1")) {
						basePath = "/ebs1/servers/" + serverName.toLowerCase();
					}
					
					if(sId > 0) {
						
						// Get the survey ident
						String survey_ident = null;
						String sql = "select ident from survey where s_id = ?;";
						pstmt = connection.prepareStatement(sql);
						
						System.out.println("sql: " + sql + " : " + sId);
						
						pstmt.setInt(1, sId);
						ResultSet resultSet = pstmt.executeQuery();
						if(resultSet.next()) {
							survey_ident = resultSet.getString(1);
						} else {
							throw new Exception("Form identifier not found for form id: " + sId);
						}
						
						String folderLocn = "/media/" + survey_ident;
						String url = folderLocn + "/" + fileName;
						String folderPath = basePath + folderLocn;
						String filePath = basePath + url;
						
						// Make sure the media folder exists for this survey
						File folder = new File(folderPath);
						FileUtils.forceMkdir(folder);
						
					    File savedFile = new File(filePath);
					    item.write(savedFile);
					}
						    
	
						
				}
			}
			
		} catch(FileUploadException ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			//log("Error encountered while parsing the request",ex);
			return;
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			//log("Error encountered while uploading file",ex);
			return;
		} finally {
	
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}
			if(commitOpen) {try {connection.rollback();} catch (Exception e) {}}
			try {
				if (connection != null) {
					connection.setAutoCommit(true);
					connection.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
				//log("Failed to close connection",e);
			}
		}
		
		if(mesg != null) {
			original_url += "&mesg=" + mesg;
		}
		
		response.sendRedirect(response.encodeRedirectURL(original_url));
		
	}

}


