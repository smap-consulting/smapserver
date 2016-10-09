	package surveyKPI;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import model.Neo4J;
import model.Property;
import model.Thingsat;
import model.ThingsatDO;

import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.QueryGenerator;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.SqlDesc;

import com.google.gson.Gson;

/*
 * Various types of export related to a survey
 *    
 */
@Path("/deprected/exportSurveyThingsat/{sId}/{filename}")
public class ExportSurveyThingsat extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	LogManager lm = new LogManager();		// Application log
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyThingsat.class.getName());
	
	/*
	 * Export as:
	 *    a) csv files
	 *    b) neo4j command query language files
	 */
	@GET
	@Produces("application/x-download")
	public Response exportShape (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("form") int fId,
			@QueryParam("language") String language,
			@QueryParam("from") Date startDate,
			@QueryParam("to") Date endDate,
			@QueryParam("dateId") int dateId) throws IOException {

		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		log.info("userevent: " + request.getRemoteUser() + " Export " + sId + " as a thingsat file to " + filename + " starting from form " + fId);
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    try {
		    	response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    } catch (Exception ex) {
		    	log.log(Level.SEVERE, "Exception", ex);
		    }
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurvey");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "Export as Neo4J");
		
		String escapedFileName = null;
		try {
			escapedFileName = URLDecoder.decode(filename, "UTF-8");
			escapedFileName = URLEncoder.encode(escapedFileName, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		escapedFileName = escapedFileName.replace("+", " "); // Spaces ok for file name within quotes
		escapedFileName = escapedFileName.replace("%2C", ","); // Commas ok for file name within quotes

		/*
		 * Get a path to a temporary folder
		 */
		String basePath = GeneralUtilityMethods.getBasePath(request);
		String filepath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique		
		FileUtils.forceMkdir(new File(filepath));
		
		/*
		 * Set the language
		 */
		if(language == null) {
			language = "none";
		}
			
		Connection connectionResults = null;
		PreparedStatement  pstmt = null;

		try {
			
			connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");
			
			
			/*
			 * Get the thingsat model
			 */
			Thingsat things = new Thingsat(getThingsAtModel(connectionSD, sId));
			addChoices(connectionSD, things);		// Add the options to any properties that are from select questions
			//things.debug();
			things.createDataFiles(filepath, "import");
			/*
			 * Get the sql
			 */
			SqlDesc sqlDesc = QueryGenerator.gen(connectionSD, 
					connectionResults,
					sId,
					fId,
					language, 
					"thingsat", 
					urlprefix,
					true,
					true,
					false,
					null,
					false,
					true,
					request.getServerName().toLowerCase(),
					null,
					null,
					request.getRemoteUser(),
					startDate,
					endDate,
					dateId);
			
			pstmt = connectionResults.prepareStatement(sqlDesc.sql + ";");
			ResultSet rs = pstmt.executeQuery();
			
			/*
			 * Write the data
			 */
			things.writeDataHeaders();		// Write the headers
			while(rs.next()) {
				things.writeData(rs);
			}
			things.writeLoaderFile();
			things.closeFiles();
			things.zip();
			things.localLoad();
			
			File file = new File(filepath + ".zip");
        	builder = Response.ok(file);
        	builder.header("Content-type","application/zip");
        	builder.header("Content-Disposition", "attachment;Filename=\"" + escapedFileName + ".zip\"");
        	response = builder.build();
        	
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE, "Exception", e);
		} finally {

			SDDataSource.closeConnection("surveyKPI-ExportSurvey", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", connectionResults);
		}	
		
		return response;
		
	}
	



	
	/*
	 * Get the thingsat model for this survey from the database
	 */
	ThingsatDO getThingsAtModel(Connection connectionSD, int sId) {
		
		ThingsatDO things = new ThingsatDO();
		PreparedStatement pstmt = null;
		String sql = "select model from survey where s_id = ?;";
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String modelString = rs.getString(1);
				System.out.println("Model: " + modelString);
				things = new Gson().fromJson(modelString, ThingsatDO.class);
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try { pstmt.close(); } catch(Exception e) {}
		}
		
		return things;
		
		/*
		Property p;
		Neo4J n;
		Neo4J r;
		
		// Create model for developement
		Thingsat things = new Thingsat();
		
		// Add nodes
		things.nodes = new ArrayList<Neo4J> ();
		
		n = new Neo4J();
		n.name = "village";
		n.label.value = "village";
		n.label.value_type = "constant";
		n.isRelation = false;
			p = new Property();
			p.key = "name";
			p.value_type = "record";
			p.colName = "village";
			p.unique = true;
			n.properties.add(p);
		things.nodes.add(n);
		
		n = new Neo4J();
		n.name = "household";
		n.label.value = "household";
		n.label.value_type = "constant";
		n.isRelation = false;
			p = new Property();
			p.key = "hhid";
			p.value_type = "record";
			p.colName = "hhid";
			p.unique = true;
			n.properties.add(p);
		things.nodes.add(n);
		
		n = new Neo4J();
		n.name = "person";
		n.label.value = "person";
		n.label.value_type = "constant";
		n.isRelation = false;
			p = new Property();
			p.key = "name";
			p.value_type = "record";
			p.colName = "name";
			p.unique = false;
			n.properties.add(p);
			
			p = new Property();
			p.key = "age";
			p.value_type = "record";
			p.colName = "age";
			p.unique = false;
			n.properties.add(p);
			
			p = new Property();		// A unique id must be added
			p.key = "uuid";
			p.value_type = "record_uuid";	
			p.unique = true;
			p.colName = "uuid";
			n.properties.add(p);
			
		things.nodes.add(n);

		
		// Add relations
		things.links = new ArrayList<Neo4J> ();
		r = new Neo4J();
		r.name = "is_in";
		r.label.value = "is_in";
		r.label.value_type = "constant";
		r.source = 1;
		r.target = 0;
		r.isRelation = true;
		things.links.add(r);
		
		r = new Neo4J();
		r.name = "member_of";
		r.label.value = "member_of";
		r.label.value_type = "constant";
		r.source = 2;
		r.target = 1;
		r.isRelation = true;
		things.links.add(r);
		return things;
		*/
	}
	
	private void addChoices(Connection connectionSD, Thingsat things) {
		PreparedStatement pstmt = null;
		String sql = "select o.ovalue from option o, question q "
				+ "where q.q_id = ? "
				+ "and q.l_id = o.l_id;";
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
		
			for(Neo4J n : things.nodes) {
				System.out.println("--------" + n.name);
				addChoicesToProperties(pstmt, n.properties);
			}
			for(Neo4J l : things.links) {
				System.out.println("++++++++" + l.name);
				addChoicesToProperties(pstmt, l.properties);
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try { pstmt.close(); } catch(Exception e) {}
		}
	}

	private void addChoicesToProperties(PreparedStatement pstmt, ArrayList<Property> properties) throws SQLException {
		for(Property p: properties) {
			System.out.println("     property" + p.colName + " : " + p.q_type);
			if(p.q_type != null && p.q_type.equals("select")) {
				p.optionValues = new ArrayList<String> ();
				pstmt.setInt(1, p.q_id);
				
				ResultSet rs = pstmt.executeQuery();
				while(rs.next()) {
					p.optionValues.add(rs.getString(1));
				}
			}
		}
	}

}
