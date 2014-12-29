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
}

public class XFormData {
	
	private static Logger log =
			 Logger.getLogger(XFormData.class.getName());
	
	String serverName = null;
	
	Authorise a = new Authorise(null, Authorise.ENUM);

	public XFormData() {
		
	}
	
	public void loadMultiPartMime(HttpServletRequest request, String user, String updateInstanceId) 
			throws SurveyBlockedException, MissingSurveyException, IOException, FileUploadException, 
			MissingTemplateException, AuthorisationException, Exception {

		log.info("loadMultiPartMime()");
		
		// Use Apache Commons file upload to get the items in the file
		SaveDetails saveDetails = null;
		PersistenceContext pc = new PersistenceContext("pgsql_jpa");
		DiskFileItemFactory  factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		List <FileItem> items = upload.parseRequest(request);
		
		serverName = request.getServerName();
		SurveyInstance si = null;
		String templateName = null;
		String form_status = request.getHeader("form_status");
		boolean incomplete = false;	// Set true if odk has more attachments to send
		
		System.out.println("Form status: " + form_status);

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
				
				saveDetails = saveToDisk(item, request, basePath, null, templateName);
				log.info("Saved xml_submission file:" + saveDetails.fileName + " (FieldName: " + item.getFieldName() + ")");
				
				SurveyTemplate template = new SurveyTemplate();
				template.readDatabase(templateName);										
				template.extendInstance(si);
				
				thisInstanceId = si.getUuid();

				break;	// There is only one XML submission file
	        }
		}
		
    	/*
    	 * Complete saving to disk 
    	 * Return error if save to disk fails (exception thrown)
    	 */
		iter = items.iterator();
		while (iter.hasNext()) {
		    FileItem item = (FileItem) iter.next();	 
		    String fieldName = item.getFieldName();
		    
		    if (item.isFormField() && !fieldName.equals("xml_submission_data")) {
		    	// Check to see if this form field indicates the submission is incomplete
		    	if(item.getFieldName().equals("*isIncomplete*") && item.getString().equals("yes")) {
		    		System.out.println("    ++++++ Incomplete Submission");
		    		incomplete = true;
		    	} else {
		    		log.info("Warning FormField Ignored, Item:" + item.getFieldName() + ":" + item.getString());
		    	}		    	
		    } else {
		        if(!fieldName.equals("xml_submission_file") && !fieldName.equals("xml_submission_data")) {
		        	SaveDetails attachSaveDetails = saveToDisk(item, request, basePath, saveDetails.instanceDir, templateName);
			    	log.info("Saved file:" + attachSaveDetails.fileName + " (FieldName: " + fieldName + ")");
		        }
		    }
		}
		
		Connection connectionSD = null;
		Survey survey = null;
		try {
			SurveyManager sm = new SurveyManager();
			connectionSD = SDDataSource.getConnection("surveyMobileAPI-XFormData");
			survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
					
			a.isValidSurvey(connectionSD, user, survey.id, false);		// Throw an exception of the user is not authorised to upload this survey		
			if(survey.getBlocked()) {	// Throw an exception if the survey has been blocked form accepting any more submssions
				throw new SurveyBlockedException();
			}
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
		ue.setSurveyName(si.getDisplayName());
		ue.setUpdateId(updateInstanceId);
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
	
	private SaveDetails saveToDisk(FileItem item, HttpServletRequest request, 
			String basePath, String instanceDir, String templateName) throws Exception {
		
		System.out.println("Save to disk: " + item.getName());
		SaveDetails saveDetails = new SaveDetails();
		
		String itemName = item.getName();
		if(itemName == null) {
			// Uploaded data had not been stored as a file
			saveDetails.origSurveyIdent = "none";
		} else {
			String[] splitFileName = item.getName().split("[/]");
			saveDetails.origSurveyIdent = splitFileName[splitFileName.length-1].replaceAll("[ ]", "\\ ");
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
	    System.out.println("Saving to:" + saveDetails.filePath);
	    try{
	        File savedFile = new File(saveDetails.filePath);
	        item.write(savedFile);
	    }
	    catch(Exception e){
	        e.printStackTrace();
	    }
	    
	    saveDetails.instanceDir = instanceDir;
	    return saveDetails;
	}

	
}
