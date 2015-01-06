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
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private class Message {
		String host;
		ArrayList<String> mesgArray;
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
    	public ArrayList<String> errMesg = new ArrayList<String> ();
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
		ArrayList<String> mesgArray = new ArrayList<String> ();
		
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
					// String mesg = "Survey " + displayName + " Exists in project " + projectName;
					mesgArray.add("$c_survey");
					mesgArray.add(" '");
					mesgArray.add(displayName);
					mesgArray.add("' ");
					mesgArray.add("$e_u_exists");
					mesgArray.add(" '");
					mesgArray.add(projectName);
					mesgArray.add("'");
					System.out.println(mesgArray.toString());
					
					ArrayList<String> hints = new ArrayList<String>(); 
					hints.add("$e_h_rename");
					
					setErrorResponse(request, response, mesgArray, hints, serverName, projectName, displayName, fileName);
					return;
				} 	
				
				/*
				 * Save the file and get the path to the file on disk
				 */
				SaveResponse resp = saveToDisk(uploadedFile, serverName, displayName, projectId);
				if(resp.code != 0) {

					if(!resp.foundErrorMsg) { // Error but no error message found
						resp.hints.add("Check the 'name' and 'list_name' columns for accented characters.");
						resp.hints.add("Finally: Contact tech support, this may be a system error.");
					}
					//System.out.println(resp.errMesg.toString());
					
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
					mesgArray.add("No survey name");		// TODO Language
					System.out.println(mesgArray.toString());
					
					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
					return;
				}
				
				// Set the project id to the one entered by the user 
				if(projectId != -1) {
					model.getSurvey().setProjectId(projectId);
				} else {
					mesgArray.add("No project");		// TODO Language
					System.out.println(mesgArray.toString());

					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
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
					mesgArray.add(mesg);		// TODO Language
					System.out.println(mesgArray.toString());
					
					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
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
					mesgArray.add(mesg);		// TODO Language
					System.out.println(mesgArray.toString());
					
					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
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
					mesgArray.add(mesg);		// TODO Language
					System.out.println(mesgArray.toString());

					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
					return;
				} 
				
				// If there are mandatory read only questions without a relevance or constraints that don't reference the current question then throw an error
				ArrayList<String> manReadQuestions = null;
				if((manReadQuestions = model.manReadQuestions()).size() > 0) {		
					String mesg = "Error:\n";
					for(int i = 0; i < manReadQuestions.size(); i++) {
						if(i > 0) {
							mesg += ",\n";
						}
						mesg += manReadQuestions.get(i);
					}
					mesgArray.add(mesg);		// TODO Language
					System.out.println(mesgArray.toString());
					
					setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
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
			mesgArray.add(ex.getMessage());		// TODO Language
			setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
			return;
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
			log("Error encountered while uploading file",ex);
			mesgArray.add(ex.getMessage());		// TODO Language
			setErrorResponse(request, response, mesgArray, null, serverName, projectName, displayName, fileName);
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
	        response.foundErrorMsg = false;
	        boolean hasCircularRef = false;
	        boolean hasInvalidFunction = false;
	        boolean hasSystemError = false;
	        boolean hasValidationError = false;
	        boolean hasDisplayConditionError = false;
	        while ( (line = br.readLine()) != null) {
	        	System.out.println("** " + line);
	        	if(line.startsWith("errors") || line.startsWith("Invalid") || 
	        			line.startsWith("java")) {
	        		if(!line.contains("Dependency cycles")) {
	        			if(!hasSystemError) {
	        				hasSystemError = true;
		        			
		        			response.hints.add(line);		        			
		        			// Test for select questions without list name
		        			if(line.contains("Unknown question type 'select_multiple'")) {	
		        				response.errMesg.add("$e_u_sm_no_list");
		        				//errorMesgBuf.append("select_multiple question without list name");
		        				//response.hints.add("Check the survey sheet. Make sure you have specified a list name for all the select_multiple questions");
		        				response.hints.add("$e_h_sm_no_list");
		        				response.foundErrorMsg = true;
		        			} else if(line.contains("Unknown question type 'select_one'")) {
		        				response.errMesg.add("$e_u_so_no_list");
		        				//errorMesgBuf.append("select_one question without list name");
		        				//response.hints.add("Check the survey sheet. Make sure you have specified a list name " +
	            				//	"for all the select_one questions");
		        				response.hints.add("$e_h_so_no_list");
		        				response.foundErrorMsg = true;
		        			} else {
		        				response.errMesg.add("$e_unknown");
		        				response.hints.add("$e_get_help");
		        				//response.hints.add("Contact your system administrator for support");
		        				if(!line.equals("errors.PyXFormError")) {
		        					response.foundErrorMsg = true;
		        				}
		        			}
	        			}
	        		}
	        	} else if(line.contains("Exception: java.lang.Boolean")) {
        			// calculation errors
	        		response.errMesg.add("$e_calc");
        			response.hints.add("$e_h_calc1");
        			response.hints.add("$e_h_calc2");
	        		response.foundErrorMsg = true;
	        	
	        	// Test for calculation on a group
        		} else if(line.contains("Can't set data value for node that has children")) {
        			response.hints.add("Check calculation column for a formula on a group or repeat");
	        		response.foundErrorMsg = true;
        		
	           	// Test for invalid function
        		} else if(line.contains("cannot handle function")) {	
        			if(!hasInvalidFunction) {
        				hasInvalidFunction = true;
        				response.errMesg.add("$e_inv_f");
	        			int posName = line.indexOf('\'');
	        			int posName2 = line.indexOf('\'', posName + 1);
	        			if(posName != -1 && posName2 != -1) {
	        				String name = line.substring(posName + 1, posName2);
	        				if(!isValidFunction(name)) {
	        					response.hints.add("Function " + name + " has a problem");
	        					if(isValidFunction(name.toLowerCase())) {
	        						response.hints.add("e_h_f1");
	        					} else {
	        						response.hints.add("e_h_f2");
	        					}
	        				}
	        			}
	        			response.hints.add(line);	        			
		        		response.foundErrorMsg = true;
        			}

	        	// Test for Xpath evaluation errors
        		} else if(line.startsWith("org.javarosa.core.log.WrappedException")) {	
        			if(!hasValidationError) {
        				hasValidationError = true;
        				response.errMesg.add("$e_val");
	        			response.hints.add(line);
		        		response.foundErrorMsg = true;
        			}

        		// Test for circular reference
        		} else if(line.contains("=>")) {	
        			if(!hasCircularRef) {
        				response.errMesg.add("$e_circ");
        				hasCircularRef = true;
        			
        				int posName = line.lastIndexOf('/');
	        			if(posName != -1) {
	        				String name = line.substring(posName + 1);
	        				response.errMesg.add(" ");
	        				response.errMesg.add("$e_in_q");
	        				response.errMesg.add(" '");
	        				response.errMesg.add(name);
	        				response.errMesg.add("'");
	        				response.hints.add("$e_h_c1") ;
	        				response.hints.add("$e_h_c2");
	        			} else {
	        				response.hints.add(line);
	        			}
        			}
	        		response.foundErrorMsg = true;
        		} else if(line.contains("Mismatched brackets")) {	
        			if(!hasDisplayConditionError) {
        				response.errMesg.add("$e_brackets");
        				hasDisplayConditionError = true;
	        			response.hints.add(line);			
        			}
	        		response.foundErrorMsg = true;
        		} else if(line.contains("XFormParseException")) {	
        			
        			System.out.println("Xform Parse Exception: " + line);
        			int posStart = line.lastIndexOf("line:");
        			int posEnd = line.lastIndexOf(" Couldn");
        			if(posStart != -1 && posEnd != -1) {
        				response.errMesg.add("$e_text");
        				response.errMesg.add(" '");
        				String name = line.substring(posStart + 5, posEnd);
        				response.errMesg.add(name); 
        				response.errMesg.add("'");
        				response.foundErrorMsg = true;
        			} else {
        				posStart = line.indexOf(':');
        				if(posStart != -1) {
        					String name = line.substring(posStart + 1);
            				response.hints.add(name);
            				response.foundErrorMsg = true;
        				}
        				
        			}
	        		
        		} else if(line.startsWith("======") 
        				|| line.startsWith("Traceback") 
        				|| line.startsWith("  ") 
        				|| line.startsWith("[Fatal Error]") 
        				|| line.startsWith(">>") 
        				|| line.startsWith("processing")) {
        			// ignore all of the above
        		} else {
        			response.errMesg.add(line);
        		}

	        }   
	        
	    	response.code = proc.waitFor();
            log.info("Process exitValue: " + response.code);
	    }
	    
	    return response;
	}
	
	private boolean isValidFunction(String fn) {
		boolean valid = false;
		
		switch(fn) {
		case "not":
			valid = true;
			break;
		case "selected":
			valid = true;
			break;
		case "count-selected":
			valid = true;
			break;
		case "regex":
			valid = true;
			break;
		case "if":
			valid = true;
			break;
		case "string-length":
			valid = true;
			break;
		case "format-date":
			valid = true;
			break;
		case "decimal-date-time":
			valid = true;
			break;
		case "date-time":
			valid = true;
			break;
		case "indexed-repeat":
			valid = true;
			break;
		case "position":
			valid = true;
			break;
		case "count":
			valid = true;
			break;
		case "pow":
			valid = true;
			break;
		case "random":
			valid = true;
			break;
		case "round":
			valid = true;
			break;
		case "concat":
			valid = true;
			break;
		case "join":
			valid = true;
			break;
		case "number":
			valid = true;
			break;
		case "int":
			valid = true;
			break;
		case "string":
			valid = true;
			break;
		default:
			valid = false;
		}
		
		return valid;
	}
	private String getResponseMessage( 
				ArrayList<String> mesgArray,
				ArrayList<String> hints, 
				String host, 
				String project, 
				String survey, 
				String fileName
			) {

		Message m = new Message();
		m.mesgArray = mesgArray;
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
			ArrayList<String> mesgArray, 
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
		request.setAttribute("message", getResponseMessage(mesgArray, hints, serverName, projectName, surveyName, fileName));
		request.getRequestDispatcher("/templateUploadResponse.jsp").forward(request, response);
	}
	
}
