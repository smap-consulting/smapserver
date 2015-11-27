import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class RelocateUploadedFiles {
	
	String device = null;
	
	void apply(boolean testRun, Connection sd, Connection results, String basePath) throws SQLException {
		
		String updateSQL = "update upload_event set file_path = ? where ue_id = ?;";
		PreparedStatement pstmt = sd.prepareStatement(updateSQL);
		
		Statement stmtSD = sd.createStatement();
		String sql = "select ue_id, file_name, survey_name, s_id from upload_event where file_path is null;";
		ResultSet uploads = stmtSD.executeQuery(sql);
		
		// Get the uploads
		while(uploads.next()) {
			
			String fileName = uploads.getString("file_name").toLowerCase();
			String sId = uploads.getString("s_id");
			int ueId = uploads.getInt("ue_id");
			
			if(sId == null) {
				System.out.println("    Obsolete survey: " + fileName + " not moved");
			} else {	
				
				// Move the xml file
				File oldFile = new File(basePath + "/uploadedSurveys/" + fileName);
				
				if(!oldFile.exists()) {
					// System.out.println("    Deleted instance: " + fileName + " not moved");
				} else {
					System.out.println("    Moving File:" + fileName + " survey Id: " + sId);
				
					String instanceDir = String.valueOf(UUID.randomUUID());
					String surveyPath = basePath + "/uploadedSurveys/" +  sId;
					String instancePath = surveyPath + "/" + instanceDir;
					String newPath = instancePath + "/" + instanceDir + ".xml";
					File folder = new File(surveyPath);
					File newFile = new File(newPath);
					
					try {
						if(!testRun) {
							FileUtils.forceMkdir(folder);
							folder = new File(instancePath);
							FileUtils.forceMkdir(folder);	
							
							FileUtils.moveFile(oldFile, newFile);
							System.out.println("        File moved to: " + newPath);
						
							// Update the upload event

							pstmt.setString(1, newPath);
							pstmt.setInt(2, ueId);
							pstmt.executeUpdate();
						}
				    
						// Get the attachments in this survey (if any)
						// Get the connection details for the meta data database
						DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
						DocumentBuilder db = null;
						Document xmlConf = null;			
				
						db = dbf.newDocumentBuilder();
						xmlConf = db.parse(newFile);
						
						Node n = xmlConf.getFirstChild();
						device = null;
						findFiles(n, basePath, sId, instanceDir, testRun);	// Move attachments
					
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
				
		}

	}
	
	void findFiles(Node n, String basePath, String sId, String instanceDir, 
			boolean testRun) throws IOException {
		
		if(n.getNodeType() == Node.ELEMENT_NODE) {
			String name = n.getNodeName();
			String content = n.getTextContent();
			
			//System.out.println("Node: " + name + " : " + content);
			// Device always comes before any attachments
			if(name.equals("_device") || name.equals("device")) {
				device = content;
			} else if(device != null) {
				int idx = content.lastIndexOf(".");
	            if (idx != -1) {
	            	moveFile(device, content, basePath, sId, instanceDir, testRun);
	            }
				
			}
		}
		
		if(n.hasChildNodes()) {
			NodeList nl = n.getChildNodes();
			for(int i = 0; i < nl.getLength(); i++) {
				findFiles(nl.item(i), basePath, sId, instanceDir, testRun);
			}
		} 
			
	}
	
	void moveFile(String device, String filename, String basePath, String sId, 
			String instanceDir, boolean testRun) throws IOException {
		String sourceFile = basePath + "/uploadedSurveys/" + device + "_" + filename;
		String targetFile = basePath + "/uploadedSurveys/" + sId + "/" + instanceDir + "/" + filename;

		File source = new File(sourceFile);
		if(source.exists()) {
			File target = new File(targetFile);
			System.out.println("        Moving Attachment: " + sourceFile + " to " + targetFile);
			try {
				FileUtils.moveFile(source, target);
			} catch (Exception e) {
				System.out.println("        Source file not found: " + sourceFile);
			}
		}
	}
}

