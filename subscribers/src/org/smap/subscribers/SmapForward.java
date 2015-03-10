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

package org.smap.subscribers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.smap.model.SurveyInstance;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.server.entities.SubscriberEvent;


public class SmapForward extends Subscriber {
	

	/**
	 * @param args
	 */
	public SmapForward() {
		super();

	}
	
	@Override
	public String getDest() {
		return sNameRemote + " @ " + host;
		
	}
	
	@Override
	public void upload(SurveyInstance instance, InputStream xis, String remoteUser, 
			String server, String device, SubscriberEvent se, String confFilePath, String formStatus,
			String basePath, String filePath, String updateId, int ue_id) {
		
		File tempFile = null;
		final String changeIdXSLT = 
				"<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
						"<xsl:param name=\"surveyId\"/>" +
						"<xsl:template match=\"@*|node()\">" +
							"<xsl:copy>" +
								"<xsl:apply-templates select=\"@*|node()\"/>" +
							"</xsl:copy>" +
						"</xsl:template>" +
						"<xsl:template match=\"@id\">" +
							"<xsl:attribute name=\"id\">" +
							"<xsl:value-of select=\"$surveyId\"/>" +
 					  		"</xsl:attribute>" +
						"</xsl:template>" +
						"</xsl:stylesheet>";
		
		// TODO check that the enumerator was authorised to submit this survey. This function should be in Subscriber
		// Check for connectivity to this host - if not then disable all attempts to this host for 1 minute	
		
		/*
		 * Submit the survey to the remote system
		 */
		CloseableHttpClient httpclient = null;
        ContentType ct = null;
        HttpResponse response = null;
        int responseCode = 0;
        String responseReason = null;
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        
		// Remove trailing slashes from the host
		if(host.endsWith("/")) {
			host = host.substring(0, host.length() -1);
		}
		
        String urlString = host + "/submission";
        /*
         * Update submissions are currently not supported
         * To support we will need to:
         *   1) Get all the attachments for the updated survey even those that were not modified as
         *      they will need to be re-sent since the url of the attachment on the receiving system will
         *      be different to the url on the sending syste
         */
        if(updateId != null && updateId.trim().length() > 0) {
        	urlString += "/" + updateId.trim();
        	/*
           	se.setStatus("error");		
			se.setReason("Forwarding of updates not supported");
			return;
			*/
        }
        
        String host_name = null;
		String protocol = null;
		int port = 0;
		if(host.startsWith("https://")) {
			host_name = host.substring(8);
			port = 443;
			protocol = "https";
		} else if(host.startsWith("http://")) {
			host_name = host.substring(7);
			port = 80;
			protocol = "http";
		}
		

	    try {
			HttpHost target = new HttpHost(host_name, port, protocol);
		    CredentialsProvider credsProvider = new BasicCredentialsProvider();
		    credsProvider.setCredentials(
		                new AuthScope(target.getHostName(), target.getPort()),
		                new UsernamePasswordCredentials(user, password));
		    httpclient = HttpClients.custom()
		                .setDefaultCredentialsProvider(credsProvider)
		                .build();

            HttpClientContext localContext = HttpClientContext.create();
            HttpPost req = new HttpPost(URI.create(urlString));
	    	
            tempFile = populateRequest(formStatus, filePath, req, changeIdXSLT, ct, entityBuilder);
		   
	        // prepare response and return uploaded

            System.out.println("	Info: submitting to: " + req.getURI().toString());
            response = httpclient.execute(target, req, localContext);
            responseCode = response.getStatusLine().getStatusCode();
            responseReason = response.getStatusLine().getReasonPhrase(); 
           	
			
            // verify that the response was a 201 or 202.
            // If it wasn't, the submission has failed.
        	System.out.println("	Info: Response code: " + responseCode + " : " + responseReason);
            if (responseCode != HttpStatus.SC_CREATED && responseCode != HttpStatus.SC_ACCEPTED) {      
            	System.out.println("	Error: upload failed: ");
            	se.setStatus("error");		
				se.setReason(responseCode + ":" + responseReason);
            } else {
            	se.setStatus("success");
            }
	       			
		} catch (UnsupportedEncodingException e) {
			se.setStatus("error");
			String msg = "UnsupportedCodingException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(ClientProtocolException e) {
			se.setStatus("host_unreachable");
			String msg = "ClientProtocolException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(IOException e) {
			se.setStatus("host_unreachable");
			String msg = "IOException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} catch(IllegalArgumentException e) {
			se.setStatus("error");			
			String msg = "IllegalArgumentException:" + e.getMessage();
			se.setReason(msg);
			System.out.println("        " + msg);
		} finally {
        	try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        	
        	if(tempFile != null) {
        		tempFile.delete();
        	}
        }
		
	}


	/*
	 * Add the body to the request
	 */
	private File populateRequest(String formStatus, String filePath, HttpPost req, 
			final String changeIdXSLT,
			ContentType ct,
			MultipartEntityBuilder entityBuilder) throws IOException {
	    
		File ammendedFile = null;
		
		final File instanceFile = new File(filePath);	
		
	    if (!instanceFile.exists()) {
	    	System.out.println("	Error: File to be forwarded " + filePath + " does not exist");
	    } else {
	
	    	if(formStatus != null) {
	    		System.out.println("Setting form status in header: " + formStatus);
	    		req.setHeader("form_status", formStatus);						// smap add form_status header
	    	} else {
	    		System.out.println("Form Status null");
	    	}
		    // add the submission file after transforming the survey id
	        System.out.println("Instance file: " + instanceFile);
	        PipedInputStream in = new PipedInputStream();
	        final PipedOutputStream outStream = new PipedOutputStream(in);
	        new Thread(
	          new Runnable(){
	            public void run(){
			        try {
			        	InputStream xslStream = new ByteArrayInputStream(changeIdXSLT.getBytes("UTF-8"));
		            	Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(xslStream));
						StreamSource source = new StreamSource(instanceFile);
						StreamResult out = new StreamResult(outStream);
						transformer.setParameter("surveyId", new String(sIdentRemote));
			        	transformer.transform(source, out);
			        	outStream.close();
			        } catch (TransformerConfigurationException e1) {
						e1.printStackTrace();
					} catch (TransformerFactoryConfigurationError e1) {
						e1.printStackTrace();
					} catch (TransformerException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
	            }
	          }
	        ).start();
	     
	        
	        /*
	         * Add submission file as file body, hence save to temporary file first
	         */
	        System.out.println("Saving stream to file");
	        ammendedFile = saveStreamTemp(in);
	        FileBody fb = new FileBody(ammendedFile);
	        entityBuilder.addPart("xml_submission_file", fb);	
		
	        System.out.println("Instance file path: " + instanceFile.getPath());
	
		    /*
		     *  find all files referenced by the survey
		     *  Temporarily check to see if the parent directory is "uploadedSurveys". If it is
		     *   then we will need to scan the submission file to get the list of attachments to 
		     *   send. 
		     *  Alternatively this is a newly submitted survey stored in its own directory just as
		     *  surveys are stored on the phone.  We can then just add all the surveys that are in 
		     *  the same directory as the submission file.
		     */
	        
	        File[] allFiles = null;
	        File parentDirectory = instanceFile.getParentFile();
	        if(parentDirectory.getName().equals("uploadedSurveys")) {
	        	// Old survey
	        	allFiles = new File[0];	// TODO remove, I don't think this is needed
	        } else {
	        	allFiles = instanceFile.getParentFile().listFiles();
	        }
		    
		    // add media files ignoring invisible files and the submission file
		    List<File> files = new ArrayList<File>();
		    for (File f : allFiles) {
		        String fileName = f.getName();
		        if (!fileName.startsWith(".") && !fileName.equals(instanceFile.getName())) {	// ignore invisible files and instance xml file    
		        	files.add(f);
		        }
		    }
		    
		    for (int j = 0; j < files.size(); j++) {	
	
	            File f = files.get(j);
	            String fileName = f.getName();
	            ct = ContentType.create(UtilityMethodsEmail.getContentType(fileName));
	            FileBody fba = new FileBody(f, ct, fileName);
		        entityBuilder.addPart(fileName, fba);
		
		    }
		    
	        req.setEntity(entityBuilder.build());
	    }
	    return ammendedFile;
	}
	
	private File saveStreamTemp(InputStream in) throws IOException {

		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File file = new File("/tmp/upload" + timeStamp);
	    FileUtils.copyInputStreamToFile(in, file);

	    return file;
	}
}
