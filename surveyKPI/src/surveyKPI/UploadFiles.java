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
import javax.ws.rs.Produces;
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
import org.smap.sdal.Utilities.UtilityMethods;
import org.smap.sdal.managers.QuestionManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.CSVParser;
import utilities.MediaInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/upload")
public class UploadFiles extends Application {
	
	// Analysts can upload files to a single survey, Admin is required to do this for the whole organisation
	Authorise surveyLevelAuth = new Authorise(null, Authorise.ANALYST);
	Authorise orgLevelAuth = new Authorise(null, Authorise.ADMIN);
	
	private static Logger log =
			 Logger.getLogger(UploadFiles.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(UploadFiles.class);
		return s;
	}
 
	@POST
	@Produces("application/json")
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
					log.info("Form field:" + item.getFieldName() + " - " + item.getString());
				
					if(item.getFieldName().equals("original_url")) {
						original_url = item.getString();
						log.info("original url:" + original_url);
					} else if(item.getFieldName().equals("survey_id")) {
						sId = Integer.parseInt(item.getString());
						log.info("surveyId:" + sId);
					}
					
				} else if(!item.isFormField()) {
					// Handle Uploaded files.
					log.info("Field Name = "+item.getFieldName()+
						", File Name = "+item.getName()+
						", Content type = "+item.getContentType()+
						", File Size = "+item.getSize());
					
					String fileName = item.getName();
					fileName = fileName.replaceAll(" ", "_"); // Remove spaces from file name
	
					// Authorisation - Access
					connectionSD = SDDataSource.getConnection("fieldManager-MediaUpload");
					if(sId > 0) {
						surveyLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
						surveyLevelAuth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
					} else {
						orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
					}
					// End authorisation
					
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
					    
					    // Create thumbnails
					    UtilityMethods.createThumbnail(fileName, folderPath, savedFile);
					    
					    // Apply changes from CSV files to survey definition
					    String contentType = UtilityMethods.getContentType(fileName);
					    if(contentType.equals("text/csv")) {
					    	applyCSVChanges(connectionSD, user, sId, fileName, savedFile, basePath, mediaInfo);
					    }
					    
					    MediaResponse mResponse = new MediaResponse ();
					    mResponse.files = mediaInfo.get();			
						Gson gson = new GsonBuilder().disableHtmlEscaping().create();
						String resp = gson.toJson(mResponse);
						log.info("Responding with " + mResponse.files.size() + " files");
						response = Response.ok(resp).build();	
					} else {
						response = Response.serverError().entity("Media folder not found").build();
					}
				
						    
	
						
				}
			}
			
		} catch(FileUploadException ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().entity(ex.getMessage()).build();
		} finally {
	
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
		
	}
	
	/*
	 * Return available files
	 */
	@GET
	@Produces("application/json")
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
		if(sId > 0) {
			surveyLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
			surveyLevelAuth.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		} else {
			orgLevelAuth.isAuthorised(connectionSD, request.getRemoteUser());
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
			
			log.info("Media query on: " + mediaInfo.getPath());
				
			MediaResponse mResponse = new MediaResponse();
			mResponse.files = mediaInfo.get();			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(mResponse);
			response = Response.ok(resp).build();		
			
		}  catch(Exception ex) {
			log.log(Level.SEVERE,ex.getMessage(), ex);
			response = Response.serverError().build();
		} finally {
	
			if (pstmt != null) { try {pstmt.close();} catch (SQLException e) {}}

			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return response;		
	}
	
	/*
	 * Update the survey with any changes resulting from the uploaded CSV file
	 */
	private void applyCSVChanges(Connection connectionSD, 
			String user, 
			int sId, 
			String csvFileName, 
			File csvFile,
			String basePath,
			MediaInfo mediaInfo) throws Exception {
		/*
		 * Find surveys that use this CSV file
		 */
		if(sId > 0) {  // TODO A specific survey has been requested
			
			applyCSVChangesToSurvey(connectionSD, user, sId, csvFileName, csvFile);
			
		} else {		// Organisational level
			
			log.info("Organisational Level");
			// Get all the surveys that reference this CSV file and are in the same organisation
			SurveyManager sm = new SurveyManager();
			ArrayList<Survey> surveys = sm.getByOrganisationAndExternalCSV(connectionSD, user,	csvFileName);
			for(Survey s : surveys) {
				
				log.info("Survey: " + s.id);
				// Check that there is not already a survey level file with the same name				
				String surveyUrl = mediaInfo.getUrlForSurveyId(sId, connectionSD);
				if(surveyUrl != null) {
					String surveyPath = basePath + surveyUrl + "/" + csvFileName;
					File surveyFile = new File(surveyPath);
					if(surveyFile.exists()) {
						continue;	// This survey has a survey specific version of the CSV file
					}
				}
					
				applyCSVChangesToSurvey(connectionSD, user, s.id, csvFileName, csvFile);
			}
		}
	}
	
	/*
	 * Filter to use when applying changes to a survey
	 */
	private class Filter {
		
		private class Rule {
			public int column;
			public int function;	// 1: contains, 2: startswith, 3: endswith, 4: matches 
			public String value;
		}
		private boolean includeAll = false;
		private Rule r1 = null;
		private Rule r2 = null;		// Secondary filter rule
		
		public Filter(String [] cols, String appearance) {
			int idx1 = appearance.indexOf('(');
			int idx2 = appearance.indexOf(')');
			if(idx1 > 0 && idx2 > idx1) {
				String criteriaString = appearance.substring(idx1 + 1, idx2);
				log.info("#### criteria for csv filter: " + criteriaString);
				
				String criteria [] = criteriaString.split(",");
				if(criteria.length < 4) {
					
					log.info("Info: Criteria elements less than 4, incude all rows");
					includeAll = true;
					
				} else {
					
					r1 = new Rule();
					r1.column = -1;
					// Ignore file name which is first criterion
					for(int i = 1; i < criteria.length; i++) {						
										
						// function
						if(i == 1) { 
							
							// remove quotes
							criteria[i] = criteria[i].trim();
							criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
							log.info("@@@@ criterion " + i + " " + criteria[i]);
							
							if(criteria[i].equals("contains")) {
								r1.function = 1;	
							} else if(criteria[i].equals("startswith")) {
								r1.function = 2;	
							} else if(criteria[i].equals("endswith")) {
								r1.function = 3;	
							} else if(criteria[i].equals("matches")) {
								r1.function = 4;	
							} else {
								log.info("Error: unknown function, " + criteria[i] +
										", include all rows");
								includeAll = true;
								return;
							}
						}
						
						// Column to match
						if(i == 2) {
							
							// remove quotes
							criteria[i] = criteria[i].trim();
							criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
							log.info("@@@@ criterion " + i + " " + criteria[i]);
							
							for(int j = 0; j < cols.length; j++) {
								log.info("***: " + criteria[i] + " : " + cols[j]);
								if (criteria[i].equals(cols[j])) {
									r1.column = j;
									break;
								}	
							}
							if(r1.column == -1) {
								log.info("Error: no matching column, include all rows");
								includeAll = true;
								return;
							}
						}
						
						// Value to match
						if(i == 3) {
							
							// Check for quotes - only strings are supported for filtering values
							criteria[i] = criteria[i].trim();
							if(criteria[i].charAt(0) == '\'') {
								criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
								log.info("@@@@ value criterion " + i + " " + criteria[i]);
								
								r1.value =  criteria[i];
								
							} else {
								log.info("Info dynamic filter value are not supported: " + 
											criteria[i] + ", include all rows ");
								includeAll = true;
								return;
							}
							
							
						}
						
						if(i == 4) {
							// TODO add rules for extra filters
						}
					}
					includeAll = false;
					
				}
			} else {
				log.info("Error: Unknown appearance: " + appearance + ", include all rows");
				includeAll = true;
			}
			
		}
		
		/*
		 * Return true if the row should be included
		 */
		public boolean isIncluded(String [] cols) {
			boolean include = true;
					
			if(!includeAll) {
				switch(r1.function) {
					case 1: // contains
						if(!cols[r1.column].contains(r1.value)) {
							include = false;
						}
						break;
					case 2: // startswith
						if(!cols[r1.column].startsWith(r1.value)) {
							include = false;
						}
						break;
					case 3: // endswith
						if(!cols[r1.column].startsWith(r1.value)) {
							include = false;
						}
						break;
					case 4: // matches
						if(!cols[r1.column].equals(r1.value)) {
							include = false;
						}
						break;
				}
				
				if(include) {	// Check the secondary filter
					if(r2 != null) {
						// TODO 
					}
				}
			}
			
			return include;
		}
	}
	
	private class ValueLabelCols {
		public int value;
		public int label;
	}
	
	private void applyCSVChangesToSurvey(Connection connectionSD, 
			String user, 
			int sId, 
			String csvFileName,
			File csvFile) throws Exception {
		
		log.info("About to update: " + sId);
		QuestionManager qm = new QuestionManager();
		SurveyManager sm = new SurveyManager();
		ArrayList<org.smap.sdal.model.Question> questions = qm.getByCSV(connectionSD, sId, csvFileName);
		ArrayList<ChangeSet> changes = new ArrayList<ChangeSet> ();
		
		// Create one change set per question
		for(org.smap.sdal.model.Question q : questions) {
			
			log.info("Updating question: " + q.name + " : " + q.type);
			
			/*
			 * Create a changeset
			 */
			ChangeSet cs = new ChangeSet();
			cs.type = "option_update";
			cs.items = new ArrayList<ChangeItem> ();
			changes.add(cs);
			
			/*
			 * Get the list of options from the file that match the selection criteria in the appearance column
			 */
			try {
				FileReader reader = new FileReader(csvFile);
				BufferedReader br = new BufferedReader(reader);
				CSVParser parser = new CSVParser();
		       
				// Get Header
				String line = br.readLine();
				String cols [] = parser.parseLine(line);
				System.out.println("Header: " + line);
		       
				Filter filter = new Filter(cols, q.appearance);								// Get a filter
				ValueLabelCols vlc = getValueLabelCols(connectionSD, q.id, q.name, cols);	// Identify the columns in the CSV file that have the value and label

				while(line != null) {
					line = br.readLine();
					if(line != null) {
						String [] optionCols = parser.parseLine(line);
						if(filter.isIncluded(optionCols)) {
							System.out.println("        ## Include " + line);
			    		   
							ChangeItem c = new ChangeItem();
							c.qId = q.id;
							c.name = q.name;				// Add for logging
							c.fileName = csvFileName;		// Add for logging
							c.qType = q.type;
							c.newVal = optionCols[vlc.label];
							c.key = optionCols[vlc.value];
			    		  
							cs.items.add(c);
			    		   
						} else {
							System.out.println("        ## Ignore " + line);
						}
					}
				}
		       
			       // TODO delete all file options that were not in the latest file (file version number)
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		 
		// Apply the changes 
		sm.applyChangeSetArray(connectionSD, sId, user, changes);
		      
	}
	
	private ValueLabelCols getValueLabelCols(Connection connectionSD, int qId, String qDisplayName, String [] cols) throws Exception {
		
		ValueLabelCols vlc = new ValueLabelCols();
		
		/*
		 * Ignore language in the query, these values are codes and are (currently) independent of language
		 */
		PreparedStatement pstmt = null;
		String sql = "SELECT o.ovalue, t.value " +
				"from option o, translation t " +  		
				"where o.label_id = t.text_id " +
				"and o.q_id = ? " +
				"and externalfile ='false';";	
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1,  qId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String valueName = rs.getString(1);
				String labelName = rs.getString(2);
				System.out.println("Value column: " + valueName + " : " + labelName);
				
				vlc.value = -1;
				vlc.label = -1;
				for(int i = 0; i < cols.length; i++) {
					if(cols[i].toLowerCase().equals(valueName.toLowerCase())) {
						vlc.value = i;
					} else if(cols[i].toLowerCase().equals(labelName.toLowerCase())) {
						vlc.label = i;
					}
				}
			} else {
				throw new Exception("The names of the columns to use in this csv file "
						+ "have not been set in question " + qDisplayName);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) try {pstmt.close();} catch(Exception e) {};
		}
		return vlc;
	}

}


