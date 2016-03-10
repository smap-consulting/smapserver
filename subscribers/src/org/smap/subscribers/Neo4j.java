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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
//import org.neo4j.graphdb.GraphDatabaseService;
//import org.neo4j.graphdb.Node;
//import org.neo4j.graphdb.Relationship;
//import org.neo4j.graphdb.RelationshipType;
//import org.neo4j.graphdb.Transaction;
//import org.neo4j.graphdb.factory.GraphDatabaseFactory;
//import org.neo4j.kernel.GraphDatabaseAPI;
//import org.neo4j.server.WrappingNeoServerBootstrapper;
import org.smap.model.SurveyInstance;
import org.smap.server.entities.SubscriberEvent;
import org.w3c.dom.Document;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Neo4j extends Subscriber {

	/*
	 * Class to store neo4j queries
	 */
	private class Statement {
		public String statement;
		public Statement(String v) {
			statement = v;
		}
	}
	
	private class NeoQuery {
		public ArrayList<Statement> statements = new ArrayList<Statement> ();
	}
	


	// Details of survey definitions database
	String dbClassMeta = null;
	String databaseMeta = null;
	String userMeta = null;
	String passwordMeta = null;
	
	// Details for results database
	String dbClass = null;
	String database = null;
	String user = null;
	String password = null;
	
	String gBasePath = null;
	String gFilePath = null;

	/**
	 * @param args
	 */
	public Neo4j() {
		super();

	}
	
	@Override
	public String getDest() {
		return "upload";
		
	}
	
	@Override
	public void upload(SurveyInstance instance, InputStream is, String remoteUser, 
			String server, String device, SubscriberEvent se, String confFilePath, String formStatus,
			String basePath, String filePath, String updateId, int ue_id, Date uploadTime, 
			String surveyNotes, String locationTrigger)  {
		
		gBasePath = basePath;
		gFilePath = filePath;

		if(gBasePath == null || gBasePath.equals("/ebs1")) {
			gBasePath = "/ebs1/servers/" + server.toLowerCase();
		}
		System.out.println("base path: " + gBasePath);
		// Open the configuration file
		String neoServer = null;
		String neoPath = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document xmlConf = null;		
		Connection connection = null;
		try {
				
			// Get the connection details for the database with survey definitions
			db = dbf.newDocumentBuilder();
			xmlConf = db.parse(new File(confFilePath + "/metaDataModel.xml"));
			dbClassMeta = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			databaseMeta = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			userMeta = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			passwordMeta = xmlConf.getElementsByTagName("password").item(0).getTextContent();
			
			// Get the details of the Neo4k connection
			xmlConf = db.parse(new File(confFilePath + "/" + getSubscriberName() + ".xml"));
			neoServer = xmlConf.getElementsByTagName("server").item(0).getTextContent();
			neoPath = xmlConf.getElementsByTagName("path").item(0).getTextContent();
			if(neoPath == null) {
				neoPath = "";
			}
			
			// TODO Check that the neo4j server is running
			// Get the connection details for the target neo4j database
			System.out.println("Writing to neo4j server: " + neoServer);
			
			System.out.println("#######");
			instance.getTopElement().printIEModel("    ");
			System.out.println("#######");
			NeoQuery nq = new NeoQuery();
			nq.statements.add(new Statement("create (n:safety);"));
			//neo4jQueryRemote(neoServer, neoPath, nq);
			//neo4jQueryLocal(nq);

		} catch (Exception e) {
			e.printStackTrace();
			se.setStatus("error");
			se.setReason("Configuration File:" + e.getMessage());
			return;
		}		

		try {
			
			se.setStatus("success");
			
		} catch (Exception e) {
			
			se.setStatus("error");
			se.setReason(e.getMessage());
			
		}
			
		return;
	}
	
	private void neo4jQueryRemote(String host, String path, NeoQuery nq) {
		
		CloseableHttpClient httpclient = null;
        ContentType ct = null;
        HttpResponse response = null;
        int responseCode = 0;
        String responseReason = null;
        
		// Remove trailing slashes from the host
		if(host.endsWith("/")) {
			host = host.substring(0, host.length() -1);
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
		
		final String urlString = host + path + "/transaction/commit";
		
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		String payload = gson.toJson(nq);
		System.out.println(payload);

		//String payload = "{\"statements\" : [ {\"statement\" : \"" +query + "\"} ]}";

	    try {
			HttpHost target = new HttpHost(host_name, port, protocol);
		    
			user = "sub";	// TODO
			password = "sub";
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
		    credsProvider.setCredentials(
		                new AuthScope(target.getHostName(), target.getPort()),
		                new UsernamePasswordCredentials(user, password));
		    httpclient = HttpClients.custom()
		                .setDefaultCredentialsProvider(credsProvider)
		                .build();

            HttpClientContext localContext = HttpClientContext.create();
            HttpPost req = new HttpPost(URI.create(urlString));
            
            // Add the payload
            req.setEntity(new StringEntity(payload));
            req.setHeader("Accept", "application/json");
            req.setHeader("Content-type", "application/json; charset=UTF-8");

            System.out.println("	Info: submitting to: " + req.getURI().toString());
            response = httpclient.execute(target, req, localContext);
            ResponseHandler<String> handler = new BasicResponseHandler();
            responseCode = response.getStatusLine().getStatusCode();
            responseReason = response.getStatusLine().getReasonPhrase(); 
            
            String body = handler.handleResponse(response);
            System.out.println("Response: " + responseReason);
            System.out.println("Response Body: " + body);
           	
	       			
		} catch (UnsupportedEncodingException e) {
			
			String msg = "UnsupportedCodingException:" + e.getMessage();
			System.out.println("        " + msg);
			
		} catch(ClientProtocolException e) {

			String msg = "ClientProtocolException:" + e.getMessage();
			System.out.println("        " + msg);
			e.printStackTrace();
		} catch(IOException e) {

			String msg = "IOException:" + e.getMessage();
			System.out.println("        " + msg);
			e.printStackTrace();
		} catch(IllegalArgumentException e) {		
			String msg = "IllegalArgumentException:" + e.getMessage();
			System.out.println("        " + msg);
		} finally {
        	try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        	
        	
        }
		
	}
	/*
    private static enum RelTypes implements RelationshipType
    {
        KNOWS
    }
    
	@SuppressWarnings("deprecation")
	private void neo4jQueryLocal(NeoQuery nq) {
		
		GraphDatabaseService graphDb;
		Node firstNode;
		Node secondNode;
		Relationship relationship;
		  
		final String DB_PATH = gBasePath + "/neo4j";
		System.out.println("Neo4j Database: " + DB_PATH);
		
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
		WrappingNeoServerBootstrapper srv = new WrappingNeoServerBootstrapper((GraphDatabaseAPI)graphDb);
		int status = srv.start();

		if(status == srv.WEB_SERVER_STARTUP_ERROR_CODE) {
			System.out.println("Web Server failed to start: " + status);
		}
		
		System.out.println("sleeping now");
		try {
			Thread.sleep(100 * 1000);
		} catch (Exception e) {
			// ignore
		}
		//registerShutdownHook( graphDb );
		
        try ( Transaction tx = graphDb.beginTx() )
        {
            // Database operations go here
            // END SNIPPET: transaction
            // START SNIPPET: addData
            firstNode = graphDb.createNode();
            firstNode.setProperty( "message", "Hello, " );
            secondNode = graphDb.createNode();
            secondNode.setProperty( "message", "World!" );

            relationship = firstNode.createRelationshipTo( secondNode, RelTypes.KNOWS );
            relationship.setProperty( "message", "brave Neo4j " );
            // END SNIPPET: addData

            // START SNIPPET: readData
            System.out.print( firstNode.getProperty( "message" ) );
            System.out.print( relationship.getProperty( "message" ) );
            System.out.print( secondNode.getProperty( "message" ) );
 
            tx.success();
        }
        //graphDb.shutdown();
	}
	
    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }
*/
}
