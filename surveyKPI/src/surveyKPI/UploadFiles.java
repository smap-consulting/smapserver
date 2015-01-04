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
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import model.MediaItem;
import model.MediaResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.MediaInfo;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
	public Response sendMedia(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();		
		String serverName = request.getServerName();
		String user = request.getRemoteUser();

		String original_url = "/edit.html?mesg=error loading media file";
		int sId = -1;
	
		log.info("upload files - media -----------------------");
		log.info("    Server:" + serverName);
		
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
	
		boolean commitOpen = false;
		Connection connectionSD = null; 

		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();

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
	
					connectionSD = SDDataSource.getConnection("fieldManager-MediaUpload");
					a.isAuthorised(connectionSD, request.getRemoteUser());
					if(sId > 0) {
						a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
					}
					
					String basePath = request.getServletContext().getInitParameter("au.com.smap.files");		
					if(basePath == null) {
						basePath = "/smap";
					} else if(basePath.equals("/ebs1")) {
						basePath = "/ebs1/servers/" + serverName.toLowerCase();
					}
					
					MediaInfo mediaInfo = new MediaInfo();
					if(sId > 0) {
						mediaInfo.setFolder(basePath, sId, connectionSD);
					} else {		
						mediaInfo.setFolder(basePath, user, connectionSD);				 
					}
					
					String folderPath = mediaInfo.getPath();
					if(folderPath != null) {
						String filePath = folderPath + "/" + fileName;
					    File savedFile = new File(filePath);
					    item.write(savedFile);
					    
					    MediaResponse mResponse = new MediaResponse ();
					    mResponse.files = mediaInfo.get();			
						Gson gson = new GsonBuilder().disableHtmlEscaping().create();
						String resp = gson.toJson(mResponse);
						System.out.println("Responding with " + mResponse.files.size() + " files");
						response = Response.ok(resp).build();	
					} else {
						response = Response.serverError().entity("Media folder not found").build();
					}
				
						    
	
						
				}
			}
			
		} catch(FileUploadException ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
				//log("Failed to close connection",e);
			}
		}
		
		return response;
		
	}
	
	/*
	 * Return available files
	 */
	@GET
	@Path("/media")
	public Response getMedia(
			@Context HttpServletRequest request
			) throws IOException {
		
		Response response = null;
		String serverName = request.getServerName();
		String user = request.getRemoteUser();
		int sId = -1;	// TODO set from request if available
		
		/*
		 * Authorise
		 *  If survey ident is passed then check user access to survey
		 *  Else provide access to the media for the organisation
		 */
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-UploadFiles");
		a.isAuthorised(connectionSD, user);
		if(sId > 0) {
			a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		}
		// End Authorisation		
		
		/*
		 * Get the path to the files
		 */
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {
			basePath = "/ebs1/servers/" + serverName.toLowerCase();
		}
	
		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setServer(request.getRequestURL().toString());

		PreparedStatement pstmt = null;		
		try {
					
			// Get the path to the media folder	
			if(sId > 0) {
				mediaInfo.setFolder(basePath, sId, connectionSD);
			} else {		
				mediaInfo.setFolder(basePath, user, connectionSD);				 
			}
			
			System.out.println("Media query on: " + mediaInfo.getPath());
				
			MediaResponse mResponse = new MediaResponse();
			mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			response = Response.ok(resp).build();		
			
		}  catch(Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			//log("Error encountered while uploading file",ex);
			response = Response.serverError().build();
		} finally {
	
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}

			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
				//log("Failed to close connection",e);
			}
		}
		
		return response;		
	}

}


