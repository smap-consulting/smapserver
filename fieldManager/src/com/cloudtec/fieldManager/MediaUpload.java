/*****************************************************************************

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

 ******************************************************************************/

package com.cloudtec.fieldManager;
/*
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

  
/**
 * Servlet implementation class CommonsFileUploadServlet
 *
public class MediaUpload extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(MediaUpload.class.getName());
	
    /**
     * @see HttpServlet#HttpServlet()
     *
    public MediaUpload() {
        super();
    }
    
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 *
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
 
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String serverName = request.getServerName();
		String original_url = null;
		String mesg = null;
		boolean error = false;

		log.info("upload media -----------------------");
		log.info("    Server:" + serverName);
		
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);

		boolean commitOpen = false;
		Connection connection = null; 
		PreparedStatement pstmt = null;
		try {
			/*
			 * Parse the request
			 *
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			String sId = null;
			String qId = null;
			String oId = null;
			while(itr.hasNext()) {
				FileItem item = (FileItem) itr.next();
				
				// Get form parameters
				if(item.isFormField()) {
					System.out.println("Form field:" + item.getFieldName() + " - " + item.getString());
					if(item.getFieldName().equals("media_keys")) {
						String keys [] = item.getString().split(":");
						if(keys.length > 0) {
							sId = keys[0];
							System.out.println("sId:" + sId);
						}
						if(keys.length > 1) {
							qId = keys[1];
							System.out.println("qId:" + qId);
						}
						if(keys.length > 2 ) {
							oId = keys[2];
						}
					} else if(item.getFieldName().equals("original_url")) {
						original_url = item.getString();
						System.out.println("original url:" + original_url);
					}
				} else {
					// Handle Uploaded files.
					System.out.println("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					fileName = fileName.replaceAll(" ", "_");	// Remove spaces (not understood by odkCollect)
					//String fileSuffix = null;
					String itemType = item.getContentType();
					String type = null;	// image or video or audio
					
					if(itemType.equals("image/png")) {
						type = "image";
					} else if(itemType.equals("image/jpeg")) {
						type = "image";
					} else if(itemType.equals("image/gif")) {
						type = "image";
					} else if(itemType.equals("video/3gpp")) {
						type = "video";
					} else if(itemType.equals("video/mp4")) {
						type = "video";
					} else if(itemType.equals("audio/mpeg")) {
						type = "audio";
					} else if(itemType.equals("audio/mp3")) {
						type = "audio";
					} else if(itemType.equals("audio/x-wav")) {
						type = "audio";
					} else if(itemType.equals("application/pdf")) {
						type = "image";
					} else if(itemType.equals("text/csv")) {
						type = "csv";
					} else {
						mesg = "This type of file is not currently supported.";
						error = true;
					}
					
					System.out.println("Type:" + type);
					if(type != null) {
						if(type.equals("csv") && qId != null && !qId.equals("-1")) {
							mesg = "CSV file cannot be added to a question";
						} else {
						    connection = SDDataSource.getConnection("fieldManager-MediaUpload");
							a.isAuthorised(connection, request.getRemoteUser());
							a.isValidSurvey(connection, request.getRemoteUser(), Integer.parseInt(sId), false);	// Validate that the user can access this survey
							
							// Get the survey ident
							String survey_ident = null;
							String sql = "select ident from survey where s_id = ?;";
							pstmt = connection.prepareStatement(sql);
							
							System.out.println("sql: " + sql + " : " + sId);
							
							pstmt.setInt(1, Integer.parseInt(sId));
							ResultSet resultSet = pstmt.executeQuery();
							if(resultSet.next()) {
								survey_ident = resultSet.getString(1);
							} else {
								throw new Exception("Form identifier not found for form id: " + sId);
							}
							
							// Construct the file path
	
							String basePath = getServletContext().getInitParameter("au.com.smap.files");
							if(basePath == null) {
								basePath = "/smap";
							} else if(basePath.equals("/ebs1")) {
								basePath = "/ebs1/servers/" + serverName.toLowerCase();
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
						    
						    // Add the translation
						    // 1) Get the languages
						    // 2) Get the text_id from the question or option
						    // 3) delete any existing translations of the same type for this survey and text_id
						    // 4) Insert the new translation type = type, value = sub path excluding server
						    // for each language
						    String text_id = null;
						    
						    // 1) Get the languages
						    sql = "select distinct language from translation where s_id = ?;";
							List<String> lang = new ArrayList<String>();
							pstmt = connection.prepareStatement(sql);
							pstmt.setInt(1, Integer.parseInt(sId));
							ResultSet rs = pstmt.executeQuery();
							while(rs.next()) {
								lang.add(rs.getString(1));
							}
							if(lang.size() == 0) {
								lang.add("eng");	// Default to english
							}
							
							// 2) Get the text_id
						    if(qId == null || qId.equals("-1")) {
						    	System.out.println("Survey");
						    	text_id = fileName;
						    } else if(oId == null) {
						    	System.out.println("Question");
						    	
						    	sql = "SELECT qtext_id FROM question " +
						    			"WHERE q_id = ?;"; 
						    	pstmt = connection.prepareStatement(sql);
						    	pstmt.setInt(1, Integer.parseInt(qId));
						    	rs = pstmt.executeQuery();
						    	if(rs.next()) {
									text_id = rs.getString(1);
								}
						    } else {
						    	System.out.println("Option");
						    	
						    	sql = "SELECT label_id FROM option " +
						    			"WHERE o_id = ?;"; 
						    	pstmt = connection.prepareStatement(sql);
						    	pstmt.setInt(1, Integer.parseInt(oId));
						    	rs = pstmt.executeQuery();
						    	if(rs.next()) {
									text_id = rs.getString(1);
								}
						    }
						    System.out.println("Text id:" + text_id);
	
						    if(text_id != null) {
						    	connection.setAutoCommit(false);
						    	commitOpen = true;
						    	
						    	// 3) Delete existing media file
						    	sql = "delete FROM translation " +
						    			" where s_id = ? " +
						    			" and text_id = ? " + 
						    			" and type = ? ";
						    	pstmt = connection.prepareStatement(sql);
						    	pstmt.setInt(1, Integer.parseInt(sId));
						    	pstmt.setString(2, text_id);
						    	pstmt.setString(3, type);
						    	pstmt.execute();
						    	
						    	// 4) Insert new media file for each language
						    	for(int i = 0; i < lang.size(); i++) {
						    		
						    		String language = lang.get(i);
							    	sql = "insert into translation (t_id, s_id, text_id, type, value,language) " +
							    			"values (nextval('t_seq'),?,?,?,?,?);"; 
							    	pstmt = connection.prepareStatement(sql);
							    	pstmt.setInt(1, Integer.parseInt(sId));
							    	pstmt.setString(2, text_id);
							    	pstmt.setString(3, type);
							    	pstmt.setString(4, survey_ident + "/" + fileName);
							    	pstmt.setString(5, language);
							    	pstmt.execute();
						    	}
						    	
						    	// 5) Update survey version
						    	sql = "update survey set version = version + 1 where s_id = ?";
						    	pstmt = connection.prepareStatement(sql);
						    	pstmt.setInt(1, Integer.parseInt(sId));
						    	pstmt.execute();
						    	
						    	connection.commit();
						    	commitOpen = false;
						    	
						    }
						}
					    

					}				
						
				}
			}
			
		} catch(FileUploadException ex) {
			System.out.println(ex.getMessage());
			log("Error encountered while parsing the request",ex);
			return;
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
			log("Error encountered while uploading file",ex);
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
				log("Failed to close connection",e);
			}
		}
		
		if(error) {
			System.out.println("Error in media upload: " + mesg);
			request.setAttribute("message", mesg);
			request.getRequestDispatcher("/mediaUploadResponse.jsp").forward(request, response);
		} else {
			response.sendRedirect(response.encodeRedirectURL(original_url));
		}
		
		return;

	}
	
	
}
*/