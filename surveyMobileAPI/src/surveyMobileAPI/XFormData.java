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

package surveyMobileAPI;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.smap.model.SurveyInstance;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.NotFoundException;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.MissingSurveyException;
import org.smap.server.entities.MissingTemplateException;
import org.smap.server.entities.UploadEvent;
import org.smap.server.managers.PersistenceContext;
import org.smap.server.managers.UploadEventManager;

import exceptions.SurveyBlockedException;

class SaveDetails {
	String fileName = null;
	String instanceDir = null;
	String filePath = null;
	String origSurveyIdent = null;
	int iosImageCount = 0;
	int iosVideoCount = 0;
}

public class XFormData {
	
	private static Logger log =
			 Logger.getLogger(XFormData.class.getName());
	
	String serverName = null;
	
	Authorise a = new Authorise(null, Authorise.ENUM);

	public XFormData() {
		
	}
	
	public void loadMultiPartMime(
				HttpServletRequest request, 
				String user, 
				String updateInstanceId) 
			throws SurveyBlockedException, MissingSurveyException, IOException, FileUploadException, 
			MissingTemplateException, AuthorisationException, Exception {

		log.info("loadMultiPartMime()");
		
		// Use Apache Commons file upload to get the items in the file
		SaveDetails saveDetails = null;
		PersistenceContext pc = new PersistenceContext("pgsql_jpa");
		DiskFileItemFactory  factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		List <FileItem> items = upload.parseRequest(request);
		int assignmentId = 0;
		
		serverName = request.getServerName();
		SurveyInstance si = null;
		String templateName = null;
		String form_status = request.getHeader("form_status");
		boolean incomplete = false;	// Set true if odk has more attachments to send
	
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + serverName.toLowerCase();
		}
		
		/*
		 * Save the XML submission file
		 */
		Iterator<FileItem> iter = items.iterator();
		String thisInstanceId = null;
		while (iter.hasNext()) {
		    FileItem item = (FileItem) iter.next();	    
	    	String name = item.getFieldName();
	        if(name.equals("xml_submission_file") || name.equals("xml_submission_data")) {		// xml_submission_data is the name used by webForms
	        	si = new SurveyInstance(item.getInputStream());
	        	
	        	// Extend the instance with data available in the template
	        	// This will get a default location if one exists
				templateName = si.getTemplateName();
				
				saveDetails = saveToDisk(item, request, basePath, null, templateName, null, 0, 0);
				log.info("Saved xml_submission file:" + saveDetails.fileName + " (FieldName: " + item.getFieldName() + ")");
				
				SurveyTemplate template = new SurveyTemplate();
				template.readDatabase(templateName);										
				template.extendInstance(si, false);
				
				thisInstanceId = si.getUuid();

				break;	// There is only one XML submission file
	        }
		}
		
    	/*
    	 * Complete saving to disk 
    	 * Return error if save to disk fails (exception thrown)
    	 */
		iter = items.iterator();
		int iosImageCount = 0;
		int iosVideoCount = 0;
		log.info("############################ Saving everything to disk  ######################");
		while (iter.hasNext()) {
		    FileItem item = (FileItem) iter.next();	 
		    String fieldName = item.getFieldName();
		    String dataUrl = null;
		    
		    log.info("==== Item: " + fieldName);
		    if (item.isFormField() && !fieldName.equals("xml_submission_data")) {
		    	// Check to see if this form field indicates the submission is incomplete
		    	if(fieldName.equals("*isIncomplete*") && item.getString().equals("yes")) {
		    		log.info("    ++++++ Incomplete Submission");
		    		incomplete = true;
		    	} else if ((dataUrl = item.getString()).startsWith("data:")) {
		    		// File Attachment from web forms
		    		SaveDetails attachSaveDetails = saveToDisk(item, request, basePath, saveDetails.instanceDir, 
		    				templateName, dataUrl.substring(dataUrl.indexOf("base64") + 7), iosImageCount, iosVideoCount);
		    		iosImageCount = attachSaveDetails.iosImageCount;
		    		iosVideoCount = attachSaveDetails.iosVideoCount;
		    		log.info("Saved webforms attachment:" + attachSaveDetails.fileName + " (FieldName: " + fieldName + ")");
		    	} else if(fieldName.equals("assignment_id"))  {
		    		log.info("Got assignment id ++++++++++++++++++" + item.getString());
		    		try {
		    			assignmentId = Integer.parseInt(item.getString());
		    		} catch (Exception e) {
		    			
		    		}
		    	} else {
		    		log.info("Warning FormField Ignored, Item:" + item.getFieldName() + ":" + item.getString());
		    	}		    	
		    } else {
		        if(!fieldName.equals("xml_submission_file") && !fieldName.equals("xml_submission_data")) {
		        	SaveDetails attachSaveDetails = saveToDisk(item, request, basePath, 
		        			saveDetails.instanceDir, templateName, null, iosImageCount, iosVideoCount);
		        	iosImageCount = attachSaveDetails.iosImageCount;
		        	iosVideoCount = attachSaveDetails.iosVideoCount;
			    	log.info("Saved file:" + attachSaveDetails.fileName + " (FieldName: " + fieldName + ")");
		        }
		    }
		}
		log.info("####################### End of Saving everything to disk ##############################");
		
		Connection connectionSD = null;
		Survey survey = null;
		try {
			SurveyManager sm = new SurveyManager();
			connectionSD = SDDataSource.getConnection("surveyMobileAPI-XFormData");
			survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
			
			if(survey.getDeleted()) {
				String reason = survey.displayName + " has been deleted";
				if(!GeneralUtilityMethods.hasUploadErrorBeenReported(connectionSD, user, si.getImei(), templateName, reason)) {
					writeUploadError(user, survey, templateName, si, pc, reason);
				}
				throw new NotFoundException();
			}
			if(survey.getBlocked()) {	// Throw an exception if the survey has been blocked form accepting any more submssions
				String reason = survey.displayName + " has been blocked";
				if(!GeneralUtilityMethods.hasUploadErrorBeenReported(connectionSD, user, si.getImei(), templateName, reason)) {
					writeUploadError(user, survey, templateName, si, pc, reason);
				}
				throw new SurveyBlockedException();
			}
			
			a.isValidSurvey(connectionSD, user, survey.id, false);		// Throw an exception of the user is not authorised to upload this survey		
		
		} finally {
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}	
		}
		
		// Write the upload event
		UploadEvent ue = new UploadEvent();
		ue.setUserName(user);
		ue.setFormStatus(form_status);
		ue.setServerName(serverName);
		ue.setSurveyId(survey.id);
		ue.setIdent(templateName);
		ue.setFilePath(saveDetails.filePath);
		ue.setProjectId(survey.getPId());
		ue.setUploadTime(new Date());	
		ue.setFileName(saveDetails.fileName);
		ue.setSurveyName(survey.getDisplayName());
		ue.setUpdateId(updateInstanceId);
		ue.setAssignmentId(assignmentId);
		ue.setInstanceId(thisInstanceId);
		ue.setLocation(si.getSurveyGeopoint());
		ue.setImei(si.getImei());
		ue.setOrigSurveyIdent(saveDetails.origSurveyIdent);
		ue.setStatus("success"); // Not really needed any more as status is really set in the subscriber event
		ue.setIncomplete(incomplete);
		UploadEventManager uem = new UploadEventManager(pc);
		uem.persist(ue);
		
		log.info("userevent: " + user + " : upload results : " + si.getDisplayName());
	}
	
	private void writeUploadError(String user, Survey survey, String templateName, 
			SurveyInstance si, PersistenceContext pc, String reason) throws Exception {
		log.info("Writing upload error");
		UploadEvent ue = new UploadEvent();
		ue.setUserName(user);
		ue.setServerName(serverName);
		ue.setSurveyId(survey.id);
		ue.setIdent(templateName);
		ue.setProjectId(survey.getPId());
		ue.setUploadTime(new Date());	
		ue.setSurveyName(survey.getDisplayName());
		ue.setLocation(si.getSurveyGeopoint());
		ue.setImei(si.getImei());
		ue.setStatus("error"); // Not really needed any more as status is really set in the subscriber event
		ue.setReason(reason);
		UploadEventManager uem = new UploadEventManager(pc);
		uem.persist(ue);
	}
	
	private SaveDetails saveToDisk(
			FileItem item, 
			HttpServletRequest request, 
			String basePath, 
			String instanceDir, 
			String templateName,
			String base64Data,
			int iosImageCount,
			int iosVideoCount) throws Exception {
		
		SaveDetails saveDetails = new SaveDetails();
		saveDetails.iosImageCount = iosImageCount;
		saveDetails.iosVideoCount = iosVideoCount;
		
		// Set the item name
		String itemName = item.getName();
		if(base64Data != null) {
			itemName = item.getFieldName();
		}
		if(itemName == null) {
			itemName = "none";
		}
		String[] splitFileName = itemName.split("[/]");
		saveDetails.origSurveyIdent = splitFileName[splitFileName.length-1].replaceAll("[ ]", "\\ ");
		log.info("Save to disk: " + saveDetails.origSurveyIdent);
		
		/*
		 * Modify file name if it is image.jpg or capturedvideo.MOV
		 *  ios (version 4 at least) always sets image file names to image.jpg
		 *  When sending from webforms on ios if the file name is image.jpg it the data will be changed to refer to image_0.jpg, image_1.jpg
		 *  We need to rename the files to match these names 
		 */
		if(saveDetails.origSurveyIdent.equals("image.jpg")) {
			saveDetails.origSurveyIdent = "image_" + saveDetails.iosImageCount + ".jpg";
			saveDetails.iosImageCount++;
		}
		if(saveDetails.origSurveyIdent.equals("capturedvideo.MOV")) {
			saveDetails.origSurveyIdent = "capturedvideo_" + saveDetails.iosVideoCount + ".MOV";
			saveDetails.iosVideoCount++;
		}
		/*
		 * Use UUID's for the instance folder and the name of the instance xml file
		 * The folder and the instance file should have the same name as this is the odk convention
		 * However they need to be globally unique to prevent clashes resulting from 2 phones submitting the same instance name
		 */
		if(item.getFieldName().equals("xml_submission_file") || item.getFieldName().equals("xml_submission_data")) {	
			instanceDir = String.valueOf(UUID.randomUUID());	// Use UUIDs for filenames
			saveDetails.fileName = instanceDir + ".xml";
		} else {
		    saveDetails.fileName = saveDetails.origSurveyIdent;
		}
		
		String surveyPath = basePath + "/uploadedSurveys/" +  templateName;
		String instancePath = surveyPath + "/" + instanceDir;
		File folder = new File(surveyPath);
		FileUtils.forceMkdir(folder);
	    folder = new File(instancePath);
	    FileUtils.forceMkdir(folder);
		

	    saveDetails.filePath = instancePath + "/" + saveDetails.fileName; 
	    log.info("Saving to:" + saveDetails.filePath);
	    try{
	        File savedFile = new File(saveDetails.filePath);
	        if(base64Data != null) {
	        	FileUtils.writeByteArrayToFile(savedFile, Base64.decodeBase64(base64Data));
	        } else {
	        	item.write(savedFile);
	        }
	    }
	    catch(Exception e){
	        e.printStackTrace();
	    }
	    
	    saveDetails.instanceDir = instanceDir;
	    return saveDetails;
	}

	
}
