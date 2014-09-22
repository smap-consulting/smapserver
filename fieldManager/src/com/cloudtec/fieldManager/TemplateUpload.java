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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.server.managers.PersistenceContext;
import org.smap.server.managers.SurveyManager;
import org.smap.server.utilities.PutXForm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

  
/**
 * Servlet implementation class CommonsFileUploadServlet
 */
public class TemplateUpload extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Logger log =
			 Logger.getLogger(TemplateUpload.class.getName());
	
	Authorise a = new Authorise(Authorise.ANALYST);
	
	private class Message {
		String host;
		String mesg;
		String project;
		String survey;
		String fileName;
		ArrayList<String> hints;
	}
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public TemplateUpload() {
        super();
    }

    private class SaveResponse {
    	public int code = 0;
    	public String fileName = null;
    	public String errMesg = "";
    	public ArrayList<String> hints = new ArrayList<String> ();
    	boolean foundErrorMsg;
    }
    
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
 
		//String contextPath = request.getContextPath();
		DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();
		String displayName = null;
		int projectId = -1;
		String surveyIdent = null;
		String projectName = null;
		String fileName = null;
		String serverName = request.getServerName();
		FileItem uploadedFile = null;

		log.info("upload template -----------------------");
		log.info("    Server:" + serverName);
		
		fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB TODO handle this with exception and redirect to an error page
		ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
			
		try {
			/*
			 * Parse the request
			 */
			List<?> items = uploadHandler.parseRequest(request);
			Iterator<?> itr = items.iterator();
			/*
			 * First get the form Handle Fields.
			 */
			while(itr.hasNext()) {
				
				FileItem item = (FileItem) itr.next();

				if(item.isFormField()) {
					if(item.getFieldName().equals("templateName")) {
						displayName = item.getString("utf-8");
						if(displayName != null) {
							displayName = displayName.trim();
						}
						log.info("Template: " + displayName);
						
						
					} else if(item.getFieldName().equals("projectId")) {
						projectId = Integer.parseInt(item.getString());
						log.info("Template: " + projectId);
						
						// Authorisation - Access
						Connection connectionSD = SDDataSource.getConnection("fieldManager-Template Upload");
						a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
						// End Authorisation
						
						// Get the project name
						PreparedStatement pstmt = null;
						try {
							String sql = "select name from project where id = ?;";
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setInt(1, projectId);
							ResultSet rs = pstmt.executeQuery();
							if(rs.next()) {
								projectName = rs.getString(1);
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}
							try {
								if (connectionSD != null) {
									connectionSD.close();
								}
							} catch (SQLException e) {
								log("Failed to close connection",e);
							}
						}
					} else if(item.getFieldName().equals("surveyIdent")) {
						surveyIdent = item.getString();
						if(surveyIdent != null) {
							surveyIdent = surveyIdent.trim();
						}
						System.out.println("Survey Ident: " + surveyIdent);
					
					}else {
						System.out.println("Unknown field name = "+item.getFieldName()+", Value = "+item.getString());
					}
				} else {
					uploadedFile = (FileItem) item;
				}
			} 
			
			/*
			 * Next get the file
			 */

			if(uploadedFile != null) {

				//Handle Uploaded files.
				System.out.println("Field Name = "+ uploadedFile.getFieldName()+
					", File Name = "+ uploadedFile.getName()+
					", Content type = "+ uploadedFile.getContentType()+
					", File Size = "+ uploadedFile.getSize());
				
				fileName = uploadedFile.getName();
				
				// If the survey display name already exists on this server, for this project, then throw an error
				SurveyManager surveys = new SurveyManager(new PersistenceContext("pgsql_jpa"));
				if(surveys.surveyExists(displayName, projectId)) {		
					String mesg = "Survey " + displayName + " Exists in project " + projectName;
					System.out.println(mesg);
					
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				} 	
				
				/*
				 * Save the file and get the path to the file on disk
				 */
				SaveResponse resp = saveToDisk(uploadedFile, serverName, displayName, projectId);
				if(resp.code != 0) {

					if(!resp.foundErrorMsg) { // Error but no error message found
						resp.hints.add("Check the 'name' and 'list_name' columns for accented characters.");
						resp.hints.add("Check these names in both worksheets.</li></ul>");
						resp.hints.add("Finally: Contact tech support, this may be a system error.");
					}
					
					setErrorResponse(request, response, resp.errMesg, resp.hints, serverName, projectName, displayName, fileName);
					return;

				}
				File templateFile = new File(resp.fileName);
				
				// Parse the form into an object model
				PutXForm loader = new PutXForm();
				SurveyTemplate model = loader.put(new FileInputStream(templateFile));	// Load the XForm into the model
				//model.printModel();
				
				// Set the survey name to the one entered by the user 
				if(displayName != null && displayName.length() != 0) {
					model.getSurvey().setDisplayName(displayName);
					model.getSurvey().setFileName(resp.fileName);
				} else {
					String mesg = "Error: No survey name";
					System.out.println(mesg);
					
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				}
				
				// Set the project id to the one entered by the user 
				if(projectId != -1) {
					model.getSurvey().setProjectId(projectId);
				} else {
					String mesg = "Error: No project";
					System.out.println(mesg);
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				}
				
				// Set the survey ident to the one entered by the user 
				if(surveyIdent != null && surveyIdent.length() != 0) {
					model.getSurvey().setIdent(surveyIdent);
				} 
				
				// Set the initial survey version
				model.getSurvey().setVersion(1);
				
				
				// If there is more than one geom per form or too many questions then throw an error
				ArrayList formsWithError = null;
				if((formsWithError = model.multipleGeoms()).size() > 0) {		
					String mesg = "";
					for(int i = 0; i < formsWithError.size(); i++) {
						if(i > 0) {
							mesg += "\n";
						}
						mesg += formsWithError.get(i);
					}
					System.out.println(mesg);
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				} 
				
				// If there are duplicate question names in a form then throw an error
				ArrayList<String> duplicateNames = null;
				if((duplicateNames = model.duplicateNames()).size() > 0) {		
					String mesg = "Error: The following question names are duplicates:";
					for(int i = 0; i < duplicateNames.size(); i++) {
						if(i > 0) {
							mesg += ",";
						}
						mesg += duplicateNames.get(i);
					}
					System.out.println(mesg);
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				} 	
				
				// If there are duplicate option names in a form then throw an error
				ArrayList<String> duplicateOptionNames = null;
				if((duplicateOptionNames = model.duplicateOptionValues()).size() > 0) {		
					String mesg = "Error:\n";
					for(int i = 0; i < duplicateOptionNames.size(); i++) {
						if(i > 0) {
							mesg += ",\n";
						}
						mesg += duplicateOptionNames.get(i);
					}
					System.out.println(mesg);
					setErrorResponse(request, response, mesg, null, serverName, projectName, displayName, fileName);
					return;
				} 	
					
				//model.printModel();
				model.writeDatabase();	// write the survey definitions
				log.info("userevent: " + request.getRemoteUser() + " : create survey : " + displayName);
					
			}
			
		} catch(AuthorisationException ex) {
			log.log(Level.SEVERE,"Authorisation error loading template", ex);
			throw ex;		// re-throw
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,"Error encountered while parsing the request", ex);
			setErrorResponse(request, response, "Error: " + ex.getMessage(), null, serverName, projectName, displayName, fileName);
			return;
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
			log("Error encountered while uploading file",ex);
			setErrorResponse(request, response, "Error: " + ex.getMessage(), null, serverName, projectName, displayName, fileName);
			return;
		}
		
		request.getRequestDispatcher("/templateManagement.html").forward(request, response);
		return;

	}
	
	/*
	 * 1. Save the uploaded file
	 * 		{files parameter in web.xml}/templates/displayName.xml
	 *      {files parameter in web.xml}/templates/xls/displayName.xls(x)  (Only if a .xls(x) file was loaded
	 * 2. Transform to XML (if required)
	 * 3. Return the path to the XML file
	 */
	private SaveResponse saveToDisk(FileItem item, String serverName, String targetName,
			int projectId) throws Exception {
		String filePath = null;
		String fileFolder = null;
		SaveResponse response = new SaveResponse();
        StringBuffer errorMesgBuf = new StringBuffer("");
		
		// Remove special characters from the target name
	    String specRegex = "[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]";
		targetName = targetName.replaceAll(specRegex, "");	
		targetName = targetName.replaceAll(" ", "_");
		// The target name is not shown to users so it doesn't need to support unicode, however pyxform fails if it includes unicode chars
		targetName = targetName.replaceAll("\\P{Print}", "_");	// remove all non printable (non ascii) characters. 
		
		boolean isXLS = false;
		boolean isXLSX = false;
		
		String itemName = item.getName();
		log.info("     ItemName" + itemName);
		if(itemName.toLowerCase().endsWith("xls")) {
			isXLS = true;
		} else if(itemName.toLowerCase().endsWith("xlsx")) {
			isXLSX = true;
		}

		// Construct the file folder and full path
		String basePath = getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + serverName.toLowerCase();
		}
	    fileFolder = basePath + "/templates/" + projectId; 
	    filePath = fileFolder + "/";
	    
	    filePath += targetName;
	    if(isXLS) {
	    	filePath += ".xls";
	    } else if (isXLSX) {
	    	filePath += ".xls";
	    } else {
	    	filePath += ".xml";
	    }
	    response.fileName = fileFolder + "/" + targetName + ".xml";		// Return the final xml name not the interim xls name
	    
	    // 1. Create the project folder if it does not exist
	    File folder = new File(fileFolder);
	    FileUtils.forceMkdir(folder);
	    
	    // 2. Save the file
	    File savedFile = new File(filePath);
	    item.write(savedFile);
	    
	    // 3. Transform to XML if it is an XLS file
	    if(isXLS || isXLSX) {

	    	log.info("Transforming from: " + "python /usr/bin/smap/pyxform/xls2xform.py " +
		    		filePath + " " + response.fileName +" 2>&1");
	    	Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/usr/bin/smap/processUpload.sh " +
		    		filePath + " " + response.fileName + " 2>&1"});
	        
	    	InputStream stderr = proc.getInputStream();
	        InputStreamReader isr = new InputStreamReader(stderr);
	        BufferedReader br = new BufferedReader(isr);
	        String line = null;
	        //errorMesgBuf.append("<ol>");
	        response.foundErrorMsg = false;
	        while ( (line = br.readLine()) != null) {
	        	System.out.println("** " + line);
	        	if(line.startsWith("errors") || line.startsWith("Invalid") || 
	        			line.startsWith("/") || line.startsWith("java")) {
	        		errorMesgBuf.append("System error");
	        		response.hints.add(line);
	        		response.hints.add("Contact your system administrator for support");
	        		if(!line.equals("errors.PyXFormError")) {
	        			response.foundErrorMsg = true;
	        		}
	        	} else if(line.contains("Exception: java.lang.Boolean")) {
        			// calculation errors
	        		errorMesgBuf.append("Calculation error");
        			response.hints.add("Check calculation column for an invalid formula (A valid formula results in either true or false)");
        			response.hints.add("Otherwise check relevant column for a formula that does not result in a true or false result");
	        		response.foundErrorMsg = true;
	        	
	        	// Test for calculation on a group
        		} else if(line.contains("Can't set data value for node that has children")) {
        			response.hints.add("Check calculation column for a formula on a group or repeat");
	        		response.foundErrorMsg = true;
        		
	        	// Test for select questions without list name
        		} else if(line.contains("Unknown question type 'select_multiple'") ||
        				line.contains("Unknown question type 'select_one'")) {	
        			response.hints.add("Check the survey sheet. Make sure you have specified a list name " +
        					"for all the choice questions (select_one, select_multiple)");
	        		response.foundErrorMsg = true;
        		
	           	// Test for invalid function
        		} else if(line.contains("cannot handle function")) {	
        			errorMesgBuf.append("Invalid Function");
        			int posName = line.indexOf('\'');
        			int posName2 = line.indexOf('\'', posName + 1);
        			if(posName != -1 && posName2 != -1) {
        				String name = line.substring(posName + 1, posName2);
        				response.hints.add("Function " + name + " is invalid");
        				response.hints.add("Check for capital letters or spelling mistakes");
        				response.hints.add(line);
        			}
	        		response.foundErrorMsg = true;

	        	// Test for Xpath evaluation errors
        		} else if(line.startsWith("org.javarosa.core.log.WrappedException")) {	
        			errorMesgBuf.append("Validation Error");
        			response.hints.add("line");
	        		response.foundErrorMsg = true;

        		// Test for circular reference
        		} else if(line.contains("=>")) {	
        			errorMesgBuf.append("Check for circular references");
        			int posName = line.lastIndexOf('/');
        			if(posName != -1) {
        				String name = line.substring(posName + 1);
        				response.hints.add("A 'relevant' statement for question " + 
        						name + " is potentially referring to  itself instead of referring to another question") ;
        				response.hints.add("<li>Relevant statements are evaluated before a question is asked so they " +
        						"cannot refer to their own questions");
        			}
	        		response.foundErrorMsg = true;
        		} else if(line.contains("XFormParseException")) {	
        			
        			System.out.println("Xform Parse Exception: " + line);
        			int posStart = line.lastIndexOf("line:");
        			int posEnd = line.lastIndexOf(" Couldn");
        			if(posStart != -1 && posEnd != -1) {
        				errorMesgBuf.append("Error with the text:");
        				String name = line.substring(posStart + 5, posEnd);
        				response.hints.add(name); 
        				response.foundErrorMsg = true;
        			} else {
        				posStart = line.indexOf(':');
        				if(posStart != -1) {
        					String name = line.substring(posStart + 1);
            				response.hints.add(name);
            				response.foundErrorMsg = true;
        				}
        				
        			}
	        		
        		} 

	        }   
	        
	    	response.code = proc.waitFor();
            log.info("Process exitValue: " + response.code);
	    }
	    
	    response.errMesg = errorMesgBuf.toString();
	    return response;
	}
	
	private String getResponseMessage(String mesg, ArrayList<String> hints, String host, String project, String survey, String fileName) {

		Message m = new Message();
		m.mesg = mesg;
		m.host = host;
		m.project = project;
		m.survey = survey;
		m.fileName = fileName;
		m.hints = hints;
		
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		return gson.toJson(m);
	
	}
	
	private void setErrorResponse(HttpServletRequest request, 
			HttpServletResponse response, 
			String mesg, 
			ArrayList<String> hints, String serverName, 
			String projectName, 
			String surveyName, 
			String fileName) throws ServletException, IOException {
		
		Connection connectionSD = SDDataSource.getConnection("fieldManager-Template Upload");
		String admin_email = "administrator@smap.com.au";
		
		// Get the email address of the organisational administrator
		PreparedStatement pstmt = null;
		try {
			String sql = "select admin_email from organisation o, users u " +
					"where o.id = u.o_id " +
					"and u.ident = ?";
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String email = rs.getString(1);
				if(email != null && email.trim().length() > 0) {
					admin_email = email;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log("Failed to close connection",e);
			}
		}
		
		request.setAttribute("administrator", admin_email);
		request.setAttribute("message", getResponseMessage(mesg, hints, serverName, projectName, surveyName, fileName));
		request.getRequestDispatcher("/templateUploadResponse.jsp").forward(request, response);
	}
	
}
