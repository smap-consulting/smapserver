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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;

import utilities.CSVReader;
import utilities.Geo;

import model.Report;
import model.Settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Manage creation and supply of reports
 */
@Path("/reports")
public class ReportListSvc extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ReportListSvc.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ReportListSvc.class);
		return s;
	}
	
	private class ImageDim {
		int width = 0;
		int height = 0;
	}
	
	private static int largest_width = 800;
	private static int largest_height = 600;
	
	String sqlReport = "select r_id, " +
			"r.item_type, " +
			"r.title, " +
			"r.description, " +
			"r.country, " +
			"r.region, " +
			"r.district, " +
			"r.community," +
			"r.category," +
			"r.pub_date, " +
			"extract(epoch from r.pub_date) as epoch, " +
			"r.author, " +
			"r.p_id, " + 
			"r.ident, " +
			"Box2D(the_geom) as locn, " +
			"r.url, " +
			"r.thumb_url, " +
			"r.data_url, " +
			"r.width, " +
			"r.height, " +
			"r.t_width, " +
			"r.t_height " +
			"from reports r ";

	String sqlReportList =
			" where r.p_id = ? " +
			"order by r.pub_date desc;";
	
	String sqlReportListBounded =
			" where r.p_id = ? " +
			" and extract(epoch from r.pub_date) > ? and extract(epoch from r.pub_date) <= ? " +
			" and ST_Intersects(the_geom, ST_GeomFromText(?, 4326)) " +
			" order by r.pub_date desc;";
	
	String sqlReportSpecific =
			" where r.ident = ? ";


	/*
	 * Get a report in JSON via an oembed request
	 */
	@GET
	@Path("/oembed.json")
	@Produces("application/json")
	public Response getOembedReport(@Context HttpServletRequest request,
			@QueryParam("url") String url,
			@QueryParam("maxheight") int maxheight,
			@QueryParam("maxwidth") int maxwidth
			) {
		
		Response response;
		
		if(url == null) {
			response = Response.status(Status.NOT_FOUND).entity("No URL specified").build();
		} else {
			
			int idx = url.indexOf("view/");
			
			if(idx > 1) {
				String reportIdent = url.substring(idx + 5);
				reportIdent = reportIdent.replaceAll("/", "");
				if(maxwidth > largest_width) {maxwidth = largest_width;}
				if(maxheight > largest_height) {maxheight = largest_height;}
				Report report =  getReportDetails(request, reportIdent, maxwidth, maxheight);
				if(report == null) {
					response = Response.status(Status.NOT_FOUND).entity("Report not found").build();
				} else {
					String scrolling = "no";
					if(report.smap.data_type.equals("table")) {
						scrolling = "yes";
					}
					report.html = "<iframe frameborder = \"0\" scrolling=\"" + scrolling + "\" " + 
							"style=\"width: 500px; height: 360px; border-width: 1px; border-style: solid; border-color: rgb(180, 180, 180); max-width: 99%; min-width: 200px; padding: 0px; \" " +
							"src=\"" + url + "?format=embed\">" +
							"</iframe>";
					response = getResponse(report, "json", reportIdent);
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity("Invalid url: " + url).build();
			}
		}
		
		return response;
	}
	
	/*
	 * Get a report
	 * Format can be json, html, word, xls or embed (a variant on html)
	 * A format parameter is used rather than accept in the header
	 */
	@GET
	@Path("/view/{reportIdent}")
	@Produces("application/json")
	public Response getReport(@Context HttpServletRequest request,
			@PathParam("reportIdent") String reportIdent,
			@QueryParam("format") String format) {
		
		log.info("getReport: " + reportIdent + " : " + format);
		
		if(reportIdent != null) {
			reportIdent = reportIdent.replace("'", "''"); 
		}
		if(format == null) {
			format = "html";
		} else {
			format = format.replace("'", "''");
		}
		
		Report report =  getReportDetails(request, reportIdent, largest_width, largest_height);
		
		if(report == null) {
			return Response.status(Status.NOT_FOUND).entity("Report " + reportIdent + " not found").build();
		} else {
			return getResponse(report, format, reportIdent);
		}
	}
	
	/*
	 * Get report list
	 */
	@GET
	@Path("/list/{projectId}")
	@Produces("application/json")
	public Response getReports(@Context HttpServletRequest request,
			@QueryParam("fromDate") long fromDate,
			@QueryParam("toDate") long toDate,
			@QueryParam("bbox") String bboxString,
			@PathParam("projectId") int projectId) {
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ReportListSvc");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation

		ArrayList<Report> reportList = new ArrayList<Report> ();
		Connection connection = null; 
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		
		try {
			
			// Get the bounding box as an array
			String [] bbox = null;
			String geomValue = null;
			if(bboxString != null) {
				bbox = bboxString.split(",");
				if(bbox.length != 4) {
				    throw new Exception("Survey: Error: Invalid bbox specification: \"bbox=" + bboxString +
				    		"\" should be: bbox=left,bottom,right,top");
				}
				// Convert the bounds into a polygon
				geomValue = "POLYGON((" + 
							  bbox[0] + " " + bbox[1] + 
						"," + bbox[2] + " " + bbox[1] + 
						"," + bbox[2] + " " + bbox[3] + 
						"," + bbox[0] + " " + bbox[3] + 
						"," + bbox[0] + " " + bbox[1] +
						"))";
				} 
			
			connection = ResultsDataSource.getConnection("surveyKPI-ReportListSvc");
			String sql;
			if(toDate > 0 || geomValue != null) {
				if(geomValue == null) {		// Default to the whole world
					geomValue = "POLYGON((-180 -90, 180 -90, 180 90, -180 90, -180 -90))";
				}
				if(toDate == 0) {
					toDate = 32503680000L;	// Default to Year 3000
				}
				sql = sqlReport + sqlReportListBounded;
				pstmt = connection.prepareStatement(sql);	
				pstmt.setInt(1, projectId);
				pstmt.setLong(2, fromDate);
				pstmt.setLong(3, toDate);
				pstmt.setString(4, geomValue);
				log.info(sql + " : " + projectId + " : " + fromDate + " : " + toDate);
			} else {
				sql = sqlReport + sqlReportList;
				pstmt = connection.prepareStatement(sql);	
				pstmt.setInt(1, projectId);
				log.info(sql + " : " + projectId);
			}
			
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {		
				Report aReport = new Report();
				populateReport(request, aReport, resultSet, largest_width, largest_height);
				reportList.add(aReport);
			}	
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();
			String resp = gson.toJson(reportList);
			response = Response.ok(resp).build();
			
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
		
	}
	
	/*
	 * Add a new report
	 */
	@POST
	@Path("/report/{projectId}")
	@Consumes("application/json")
	public Response addReport(@Context HttpServletRequest request, 
			@PathParam("projectId") int projectId,
			@FormParam("report") String reportString) { 
		
		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		String user = request.getRemoteUser();
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ReportListSvc");
		a.isAuthorised(connectionSD, user);
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		String serverName = request.getServerName();
		Type type = new TypeToken<Report>(){}.getType();
		Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();
		Report aReport = gson.fromJson(reportString, type);
		
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");

		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + serverName.toLowerCase();
		}

		String ident = null;

		Connection connection = null; 
		PreparedStatement pstmt = null;
		String reportType = aReport.type;
		String itemType = aReport.smap.data_gen_type;
		String inImageURL = aReport.url;
		String inThumbURL = aReport.thumbnail_url;
		String outImageURL = null;
		String outThumbURL = null;
		String outDataURL = null;
		ImageDim dimURL = new ImageDim();	// Dimensions of image
		ImageDim dimThumb = new ImageDim();	// Dimensions of image
		
		// Get the ident
		if(aReport.smap.ident == null) {
			ident = String.valueOf(UUID.randomUUID());	// Create the report
			log.info("Creating new report: " + aReport.smap.ident);
		} else {
			ident = aReport.smap.ident;
			log.info("Updating report: " + aReport.smap.ident);
		}
		
		/*
		 * Save attachments to the file system if this is a new report
		 * Editing an existing report will not result in attachments being saved
		 */
		if(aReport.smap.ident == null) {
			log.info("saving image to file system: " + itemType + " : " + ident);
			try {
				if(reportType.equals("photo") || reportType.equals("video") || reportType.equals("audio")) {
					// Existing image, hence copy to the report folder
					// Get the file path from the image url

					int idx = inImageURL.lastIndexOf('/');
					String imageName = inImageURL.substring(idx+1);
					idx = inThumbURL.lastIndexOf('/');
					String thumbName = inThumbURL.substring(idx+1);
					
					outImageURL = "attachments/report/" + ident + "_" + imageName;
					if(reportType.equals("photo") || reportType.equals("video")) {
						outThumbURL = "attachments/report/thumbs/" + ident + "_" + thumbName;
					} else {
						outThumbURL = "fieldAnalysis/img/audio-icon.png";
					}
					
					copyImageFile(inImageURL, ident + "_" + imageName, serverName, basePath);
					if(reportType.equals("photo") || reportType.equals("video")) {
						copyImageFile(inThumbURL, "thumbs/" + ident + "_" + thumbName, serverName, basePath);
					}
					
					dimThumb = getImageDimension(outThumbURL);
					if(reportType.equals("photo")) {
						dimURL = getImageDimension(outImageURL);
					} else {
						dimURL = dimThumb;
					}
					
				}  else if(reportType.equals("data_url")) {	// Only set for new generated data
					
					// Save the JSON data file
					outDataURL = "attachments/report/" + ident + ".json";
					String dataFileName = basePath + "/attachments/report/" + ident + ".json";
					File of = new File(dataFileName);
					String data = aReport.smap.data_gen;
					
					// Copy all attachments referenced by this JSON file to the reports area and update the JSON 
					//data = copyAllAttachments(data, ident, basePath, serverName);
					
					if(data != null) { 
						FileWriter ofw = new FileWriter(of);  
						ofw.write(data);  
						ofw.flush(); 
						ofw.close();
					}
					

					if(aReport.smap.data_gen_type.equals("graph")) {  // New graph
						
						// Generated image write to disk
						outImageURL = "attachments/report/" + ident + ".png";
						outThumbURL = "attachments/report/thumbs/" + ident + ".png";
						
						String imageFileName = basePath + "/attachments/report/" + ident + ".png";
						String thumbFileName = basePath + "/attachments/report/thumbs/" + ident + ".png";
						
						File ofImage = new File(imageFileName);	
						String image = aReport.smap.data_gen_capture;
						writeImageFile(image, ofImage);
						
						Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/usr/bin/convert -thumbnail 200 "  + 
		        				imageFileName + " " + thumbFileName + 
		        				" >> /var/log/tomcat7/attachments.log 2>&1"});
						
						dimURL = getImageDimension(outImageURL);
						dimThumb = getImageDimension(outThumbURL);
					
					} else {

						if(aReport.smap.data_gen_type.equals("map")) {	// New map
							
							outThumbURL = "fieldAnalysis/img/map_ico2.png";
							
						} else if(aReport.smap.data_gen_type.equals("table")) {    // New Table
							
							outThumbURL = "fieldAnalysis/img/table_ico.png";
							
							outImageURL = "attachments/report/" + ident + ".csv";
							File ofCsv = new File(basePath + "/attachments/report/" + ident + ".csv");
							String csv = aReport.smap.data_gen_capture;
							
							// Copy all attachments referenced by this CSV file to the reports area and update the JSON 
							//csv = copyAllAttachments(csv, ident, basePath, serverName);
							
							if(csv != null) { // dataGen can be null if an existing report is being edited
								FileWriter ofw = new FileWriter(ofCsv);  
								ofw.write(csv);  
								ofw.flush(); 
								ofw.close();
							}
						} else {	// Unknown type
							outThumbURL = "fieldAnalysis/img/table_ico.png";
						}
						
						dimURL.width = aReport.width;
						dimURL.height = aReport.height;
						dimThumb = getImageDimension(outThumbURL);
						
					}
				}
			} catch(Exception e) {
				log.log(Level.SEVERE,"Failed to save image", e);
			}
		}

		try {
			// Get user name 
			String sql = "select name from users where ident = ?;";
			String author = null;
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, user);
			ResultSet resultSet = pstmt.executeQuery();
			if (resultSet.next()) {		
				author = resultSet.getString(1);	
			}
			
			connection = ResultsDataSource.getConnection("surveyKPI-ReportListSvc");
			connection.setAutoCommit(false);					// Commit or rollback everything
			
			// Convert the bounds into a polygon
			String geomValue;
			if(aReport.smap.bbox != null && aReport.smap.bbox.length == 4) {
				
				geomValue = "POLYGON((" + 
							  aReport.smap.bbox[0] + " " + aReport.smap.bbox[1] + 
						"," + aReport.smap.bbox[2] + " " + aReport.smap.bbox[1] + 
						"," + aReport.smap.bbox[2] + " " + aReport.smap.bbox[3] + 
						"," + aReport.smap.bbox[0] + " " + aReport.smap.bbox[3] + 
						"," + aReport.smap.bbox[0] + " " + aReport.smap.bbox[1] +
						"))";
			} else {
				geomValue = null;
			}
			
			if(aReport.smap.ident == null) {
				/*
				 * Create the report
				 */
				sql = "insert into reports (title, description, country, " +
							"region, " +
							"district, " +
							"community, " +
							"pub_date, " +
							"author," +
							"the_geom, " +
							"ident, " +
							"p_id, " +
							"url, " + 
							"thumb_url, " +
							"data_url, " +
							"item_type," +
							"width," +
							"height," +
							"t_width," +
							"t_height" +
							") " +
						"values (" +
							"?, ?, ?, ?, ?, ?, " +
							"now(), " +
							"?, " +
							"ST_GeomFromText(?, 4326), " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?, " +
							"?" +
						");";
						
				log.info(sql + " : " + aReport.smap.description);
				pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				pstmt.setString(1, aReport.title);
				pstmt.setString(2, aReport.smap.description);
				pstmt.setString(3, aReport.smap.country);
				pstmt.setString(4, aReport.smap.region);
				pstmt.setString(5, aReport.smap.district);
				pstmt.setString(6, aReport.smap.community);
				pstmt.setString(7, author);
				pstmt.setString(8, geomValue);
				pstmt.setString(9, ident);
				pstmt.setInt(10, projectId);
				pstmt.setString(11, outImageURL);
				pstmt.setString(12, outThumbURL);
				pstmt.setString(13, outDataURL);
				pstmt.setString(14, itemType);
				pstmt.setInt(15, dimURL.width);
				pstmt.setInt(16, dimURL.height);
				pstmt.setInt(17, dimThumb.width);
				pstmt.setInt(18, dimThumb.height);
				pstmt.executeUpdate();
				
			} else {
				/*
				 * Update the report
				 */
				
				sql = "update reports set " +
						"title = ?, " +
						"description = ?, " +
						"country = ?, " +
						"region = ?, " +
						"district = ?, " +
						"community = ?, " +
						"pub_date = now(), " +
						"author = ?, " +
						"the_geom = ST_GeomFromText(?, 4326) " +
						"where ident = ? " +
						"and p_id = ?;";
						
				log.info(sql);
				pstmt = connection.prepareStatement(sql);
				pstmt.setString(1, aReport.title);
				pstmt.setString(2, aReport.smap.description);
				pstmt.setString(3, aReport.smap.country);
				pstmt.setString(4, aReport.smap.region);
				pstmt.setString(5, aReport.smap.district);
				pstmt.setString(6, aReport.smap.community);
				pstmt.setString(7, author);
				pstmt.setString(8, geomValue);
				pstmt.setString(9, aReport.smap.ident);
				pstmt.setInt(10, projectId);
				pstmt.executeUpdate();
				
				int count = pstmt.executeUpdate();
				
				if(count != 1) {
					throw new Exception("Failed to update report");
				}
			}
				
			connection.commit();
			connection.setAutoCommit(true);
			response = Response.ok().build();
				
		} catch (Exception e) {
			try { connection.rollback();connection.setAutoCommit(true);} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Remove HTML markup from media, needed if saving to CSV
	 */
	private String removeImageFromLinks(String data) {
		int idx1 = -1;
		int idx2 = -1;
		
		StringBuffer output = new StringBuffer();
		
		try {
		       
			String searchString = "href=";
			// Locate next media file
			if((idx1 = data.indexOf(searchString, idx1 + 1)) >= 0 ) {	
				
				// Found a media file
				idx1 = data.indexOf('"', idx1);		// Jump to href file
				if(idx1 != -1) {
					idx2 = data.indexOf('"', idx1 + 1);		// End of href file
					if(idx2 > 0) {
						output = output.append(new StringBuffer("<a href=\"" + data.substring(idx1 + 1, idx2)) + "\">Picture</a>");
					}

				}
			} else {
				output = output.append(new StringBuffer(data));	// No media files
			}
			
			
		} catch (Exception e) {
			log.log(Level.SEVERE,"Removing media markup", e);
		}
		
		return output.toString();

	}
	
	@DELETE
	@Path("/report/{projectId}/{reportIdent}")
	public Response deleteReport(@Context HttpServletRequest request,
			@PathParam("projectId") int projectId,
			@PathParam("reportIdent") String ident) { 
		
		log.info("Deleting report:" + ident);
		
		Response response;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ReportListSvc");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		String basePath = request.getServletContext().getInitParameter("au.com.smap.files");

		if(basePath == null) {
			basePath = "/smap";
		} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
			basePath = "/ebs1/servers/" + request.getServerName().toLowerCase();
		}
		
		if(ident != null) {

			String sql = null;				
			Connection connectionRel = null; 
			PreparedStatement pstmt = null;
			try {
				connectionRel = ResultsDataSource.getConnection("surveyKPI-ReportListSvc");		
				
				sql = "DELETE FROM reports WHERE ident = ? and p_id = ?;";	
				pstmt = connectionRel.prepareStatement(sql);
				pstmt.setString(1, ident);
				pstmt.setInt(2, projectId);
				pstmt.executeUpdate();	
				
				String attachments = basePath + "/attachments/report/" + ident + ".*";
				Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "rm -vf  "  + attachments +  
        				" >> /var/log/tomcat7/attachments.log 2>&1"});
				
				response = Response.ok("Report " + ident + " deleted.").build();

			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
				response = Response.serverError().entity(e.getMessage()).build();
			} catch (Exception e)  {
				log.log(Level.SEVERE, "Error", e);
				response = Response.serverError().entity(e.getMessage()).build();
			}
			finally {
				try {
					if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
				try {
					if (connectionSD != null) {
						connectionSD.close();
						connectionSD = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE, "Failed to close connection", e);
				}
				
				try {
					if (connectionRel != null) {
						connectionRel.close();
						connectionRel = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE, "Failed to close connection", e);
				}
			}
		} else {
			response = Response.status(Status.NOT_FOUND).entity("No ident specified").build();
		}

		return response; 
	}
	
	

	/*
	 * --------------- Support functions ----------------
	 * Convert a report object into a Response object in the required format
	 */
	private Response getResponse(Report report, String format, String ident) {
		String filename = "";
		ResponseBuilder builder = Response.ok();
		
		log.info("getResponse: " + format + " : " + ident);
		
		if(format.equals("json")) {
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();
			String resp = gson.toJson(report);
			builder = builder.entity(resp);
			
		} else if (format.equals("embed") || format.equals("word") || format.equals("html") || format.equals("xls")) {
			
			if(format.equals("word")) {
				filename = StringEscapeUtils.escapeHtml3(report.title) + ".doc";
				filename = filename.replace(' ', '_');     // Remove spaces 
			} else if (format.equals("xls")) {
				filename = StringEscapeUtils.escapeHtml3(report.title) + ".xls";
				filename = filename.replace(' ', '_');     // Remove spaces 
			}	

			StringBuffer respBuf = getOutput(format, report, ident);
			
			if(format.equals("word")) {
				builder.header("Content-type","application/vnd.ms-word; charset=UTF-8");
				builder.header("Content-Disposition", "attachment;Filename=" + filename);
			} else 	if(format.equals("html") || format.equals("embed")) {
				builder.header("Content-type","text/html");
			} else if (format.equals("xls")) {
				builder.header("Content-type",  "application/vnd.ms-excel; charset=UTF-8");
				builder.header("Content-Disposition",  "attachment;Filename=" + filename);
			}
			builder = builder.entity(respBuf.toString());
			
		}
		
		return builder.build();
	}
	
	/*
	 * Get the output text for a response
	 */
	private StringBuffer getOutput(String format, Report report, String ident) {
		
		StringBuffer respBuf = new StringBuffer();
		
		if(format.equals("html")) {
			respBuf.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
		} else if(format.equals("embed")) {
			respBuf.append("<!doctype html>");
		}
		
		//respBuf.append("<html style=\"width: 95%; height: 95%;\">");
		respBuf.append("<html style=\"width: 100%; height: 100%;\">");
		respBuf.append("<head>");
		if(format.equals("html") || format.equals("embed")) {
			respBuf.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
			respBuf.append("<link type=\"text/css\" media=\"all\" rel=\"stylesheet\" href=\"/fieldAnalysis/css/reports.css\">");
			respBuf.append("<link type=\"text/css\" media=\"all\" rel=\"stylesheet\" href=\"/css/Aristo/Aristo.css\">");
			respBuf.append("<link type=\"text/css\" media=\"all\" rel=\"stylesheet\" href=\"/css/responsivemobilemenu.css\">");
		} else if(format.equals("word")) {
			respBuf.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
		} else if(format.equals("xls")) {
			respBuf.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
			respBuf.append("<meta name=ProgId content=Excel.Sheet>");					// Gridlines
			respBuf.append("<meta name=Generator content=\"Microsoft Excel 11\">");		// Gridlines
			
			respBuf.append("<style <!--table @page{} -->>");
			respBuf.append("h1 {text-align:center; font-size: 2em; } ");
			respBuf.append("h2 {font-size: 1.6em; } ");
			respBuf.append("h3 {font-size: 1.2em; } ");
			respBuf.append("table,th,td {border:0.5 } ");
			respBuf.append("table {border-collapse:collapse;} ");
			respBuf.append("td {padding: 5px; } ");
			respBuf.append(".xl1 {mso-number-format:0;} ");										// _device style
			respBuf.append(".xl2 {mso-number-format:\"yyyy\\\\-mm\\\\-dd\\\\ h\\:mm\\:ss\";}");	// Date style

			respBuf.append("</style>");
			respBuf.append("<xml>");							// Gridlines
			respBuf.append("<x:ExcelWorkbook>");
			respBuf.append("<x:ExcelWorksheets>");
			respBuf.append("<x:ExcelWorksheet>");
			respBuf.append("<x:Name>Results Export</x:Name>");
			respBuf.append("<x:WorksheetOptions>");
			respBuf.append("<x:Panes>");
			respBuf.append("</x:Panes>");
			respBuf.append("</x:WorksheetOptions>");
			respBuf.append("</x:ExcelWorksheet>");
			respBuf.append("</x:ExcelWorksheets>");
			respBuf.append("</x:ExcelWorkbook>");
			respBuf.append("</xml>");
		}
		
		respBuf.append("<title>");
		respBuf.append(report.title);
		respBuf.append("</title>");
		
		if(!format.equals("word") && !format.equals("xls")) {
			respBuf.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"/fieldAnalysis/css/reports.css\" />");
		}
		if((format.equals("html") || format.equals("embed")) && report.smap.data_type.equals("graph")) {
			respBuf.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"/fieldAnalysis/js/libs/jqplot/jquery.jqplot.css\" />");
			respBuf.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"/fieldAnalysis/js/libs/jqplot/examples.min.css\" />");
			
		}
		respBuf.append("</head>");
		
		respBuf.append("<body style=\"width: 100%; height: 100%;\">");	
		if(format.equals("html")) {
			// Add menu options to export in other formats
			respBuf.append("<div class=\"rmm noPrint\" data-menu-style = \"minimal\">");
			respBuf.append("<ul>");
			respBuf.append("<li><a href=\"/surveyKPI/reports/view/");
			respBuf.append(ident);
			respBuf.append("?format=word");
			respBuf.append("\" title=\"Export to Word\" class=\"menu_enabled menu_no_hover\">Word</a></li>"); 
			respBuf.append("<li><a href=\"/surveyKPI/reports/view/");
			respBuf.append(ident);
			respBuf.append("?format=xls");
			respBuf.append("\" title=\"Export to Excel\" class=\"menu_enabled menu_no_hover\">Excel</a></li>"); 
			respBuf.append("</ul></div>"); 
		}
		
		respBuf.append("<div id=\"p0\" style=\"width: 100%; height: 100%;\">");		// Add a div that is equivalent to the panel div used in the dashboard
		if(format.equals("html") || format.equals("word") || format.equals("embed")) {
			respBuf.append(getMetaHtml(format, report));
		}
		
		/*
		 * If the report item type is "table" then add it to the output
		 */
		if(report.smap.data_type.equals("table")) {
			
			if(format.equals("xls") || format.equals("word")) {
				
				CSVReader in = null;
				try {		
					
					URL urlFile = new URL(report.url);	
					String [] line;
					log.info("Opening CSVReader on stream at: "  + report.url);
					in = new CSVReader(new InputStreamReader(urlFile.openStream()));
					line = in.readNext();
					if(line != null && line.length > 0) {
						// Assume first line is the header
						respBuf.append("<table>");
						respBuf.append("<thead>");
						respBuf.append("<tr>");
						for(int i = 0; i < line.length; i++) {
							respBuf.append("<td><b>");
							respBuf.append(line[i]);
							respBuf.append("</b></td>");
						}
						respBuf.append("</tr>");
						respBuf.append("</thead>");
						
						// Get the body
						respBuf.append("<tbody>");
						while ((line = in.readNext()) != null) {
							respBuf.append("<tr>");
							for(int i = 0; i < line.length; i++) {
								respBuf.append("<td>");
								if(format.equals("xls")) {
									line[i]= removeImageFromLinks(line[i]);
								}
								respBuf.append(line[i]);
								respBuf.append("</td>");
							}
							respBuf.append("</tr>");
					    }
						respBuf.append("</tbody>");
						respBuf.append("</table>");

					}
				    
				} catch (Exception e) {
					log.log(Level.SEVERE,"Error", e);
				} finally {
					try { if(in != null) {in.close();};} catch(Exception e) {};
				}
				
			} else {
				
				respBuf.append("<div class=\"rep_content\" id=\"table\"></div>");
				respBuf.append("<img id=\"hour_glass\" src=\"/fieldAnalysis/img/ajax-loader.gif\" alt=\"hourglass\" style=\"display:none;\" height=\"60\" width=\"60\">");
				
				respBuf.append("<span id=\"data_source\" style=\"display:none;\">");
					respBuf.append(report.smap.data_url);
				respBuf.append("</span>");
				respBuf.append("<script data-main=\"/fieldAnalysis/js/table_reports_main\" src=\"/js/libs/require.js\"></script>");
			}
			
		} 

		/*
		 * If the report item type is "photo", "video", "audio" and the format is "html", "word" or "embed" then add it to the output
		 */
		if((report.smap.data_type.equals("photo") || report.smap.data_type.equals("video") || report.smap.data_type.equals("audio")) && 
				(format.equals("html") || format.equals("word") || format.equals("embed"))) {
			
			if(format.equals("html")) {
				respBuf.append("<div class=\"rep_content\" style=\"border-width: 1px; border-style: solid; border-color: rgb(180, 180, 180);\">");
			}
			respBuf.append("<a href=\"").append(report.url).append("\"><img ");
			if(report.smap.data_type.equals("photo")) {
				if(report.width > 0) {
					respBuf.append("width=\"").append(report.width).append("\" height=\"").append(report.height).append("\" src=\"");
				} else {
					respBuf.append("src=\"");
				}
			} else {
				if(report.thumbnail_width > 0) {
					respBuf.append("width=\"").append(report.thumbnail_width).append("\" height=\"").append(report.thumbnail_height).append("\" src=\"");
				} else {
					respBuf.append("src=\"");
				}
			}
			if(report.smap.data_type.equals("photo")) {
				respBuf.append(report.url);
			} else {
				respBuf.append(report.thumbnail_url);
			}
			respBuf.append("\" alt=\"");
			respBuf.append(report.title);
			respBuf.append("\"/></a>");
			if(format.equals("html")) {
				respBuf.append("</div>");
			}
			
		}
		
		/*
		 * If the report item type is "map" or "graph" and the format is "html" or "embed" then add it to the output
		 */
		if(format.equals("html") || format.equals("embed")) {
			if(report.smap.data_type.equals("map")) {
				
				if(format.equals("html")) {
					respBuf.append("<div class=\"rep_content\" style=\"border-width: 1px; border-style: solid; border-color: rgb(180, 180, 180);\">");
				}
				respBuf.append("<div id=\"features\" class=\"feature-panel-right\"></div>");
				respBuf.append("<div id=\"map\" class=\"rep_content\">");
				respBuf.append("<div class=\"timecontrols\" style=\"display:none;\"><div class=\"timecontrols_inner\">");
				respBuf.append("Interval: <select name=\"span\"></select>");
				respBuf.append("<button class=\"starttimer\">Start</button>");
				respBuf.append("<input type=\"text\" class=\"slide_date1\" readonly>");
				respBuf.append("<input type=\"text\" class=\"slide_date2\" readonly>");
				respBuf.append("<div style=\"clear: both;\"></div>");
				respBuf.append("<div class=\"slider-range\"></div>");
				respBuf.append("</div></div>");
				respBuf.append("</div>");
				if(format.equals("html")) {
					respBuf.append("</div>");
				} 
				respBuf.append("<img id=\"hour_glass\" src=\"/fieldAnalysis/img/ajax-loader.gif\" alt=\"hourglass\" style=\"display:none;\" height=\"60\" width=\"60\">");
				respBuf.append("<span id=\"data_source\" style=\"display:none;\">");
					respBuf.append(report.smap.data_url);
				respBuf.append("</span>");		
				respBuf.append("<script data-main=\"/fieldAnalysis/js/map_reports_main\" src=\"/js/libs/require.js\"></script>");
				respBuf.append("<script src=\"//maps.google.com/maps/api/js?v=3.6&amp\"></script>");
				respBuf.append("<script src=\"/js/libs/OpenLayers/OpenLayers.js\"></script>");
				
			} else if(report.smap.data_type.equals("graph")) {
				
				if(format.equals("html")) {
					respBuf.append("<div class=\"rep_content\" style=\"border-width: 1px; border-style: solid; border-color: rgb(180, 180, 180);\">");
					respBuf.append("<a class=\"graphLabel\">L</a>");
				}

				respBuf.append("<div id=\"graph\" class=\"rep_content\"></div>");

				respBuf.append("</div>");			// End of panel0 div
				if(format.equals("html")) {
					respBuf.append("</div>");		// End of div that sets size for HTML exports
				}
				respBuf.append("<img id=\"hour_glass\" src=\"/fieldAnalysis/img/ajax-loader.gif\" alt=\"hourglass\" style=\"display:none;\" height=\"60\" width=\"60\">");

				respBuf.append("<span id=\"data_source\" style=\"display:none;\">");
					respBuf.append(report.smap.data_url);
				respBuf.append("</span>");	
				respBuf.append("<script src=\"/js/libs/modernizr.js\"></script>");
				respBuf.append("<script data-main=\"/fieldAnalysis/js/graph_reports_main\" src=\"/js/libs/require.js\"></script>");
				
			}
		}
		
		/*
		 * If the report item type is "graph" and the format is "word" then add the picture of the graph to the output
		 */
		if(report.smap.data_type.equals("graph") && format.equals("word")) {
			
			respBuf.append("<a href=\"").append(report.url).append("\"><img ");
			respBuf.append("width=\"").append(report.width).append("\" height=\"").append(report.height).append("\" src=\"");				
			respBuf.append(report.url);
			respBuf.append("\" alt=\"");
			respBuf.append(report.title);
			respBuf.append("\"></a>");
			
		}
		
		respBuf.append("</body>");
		respBuf.append("</html>");
		
		return respBuf;
	}
	
	/*
	 * Get the html for the meta tags
	 */
	private String getMetaHtml(String format, Report report) {
		StringBuffer resp = new StringBuffer();
		boolean hasCountry = false;
		boolean hasRegion = false;
		boolean hasDistrict = false; 
		boolean hasCommunity = false;
		
		if(report.smap.country != null && report.smap.country.trim().length() > 0) {
			hasCountry = true;
		}
		if(report.smap.region != null && report.smap.region.trim().length() > 0) {
			hasRegion = true;
		}
		if(report.smap.district != null && report.smap.district.trim().length() > 0) {
			hasDistrict = true;
		}
		if(report.smap.community != null && report.smap.community.trim().length() > 0) {
			hasCommunity = true;
		}
		
		
		resp.append("<div id=\"rep_meta\" class=\"r_overview\">");
		resp.append("<div id=\"rm_left\">");

		resp.append("</div>");	// End of left
	    
		resp.append("<div id=\"rm_right\">");
		
		resp.append("<p>").append(report.title).append(" author: ").append(report.author_name);
		if(hasCountry || hasRegion || hasDistrict || hasCommunity) {
			resp.append(" from: ");
			if(hasCountry) {
				resp.append(report.smap.country).append(", ");
			}
			if(hasRegion) {
				resp.append(report.smap.region).append(", ");
			}
			if(hasDistrict) {
				resp.append(report.smap.district).append(", ");
			}
			if(hasCommunity) {
				resp.append(report.smap.community);	
			}
			resp.append("</p>");
		}
		if(report.smap.description != null && report.smap.description.trim().length() > 0) {
			resp.append("<p>").append(report.smap.description).append("</p>");
		}
		resp.append("<div class=\"r_description\">");	// Location of automatically generated description
		resp.append("</div>");
	      
		resp.append("</div>");	// End of right
		resp.append("</div>");	// End of report meta
		resp.append("<div stye=\"clear: both;\"></div>");
		
		System.out.println("Meta response:" + resp.toString());
		return resp.toString();
	}
	
	/*
	 * Get report details from the database and return as a Report object
	 */
	private Report getReportDetails(HttpServletRequest request, String ident, int maxwidth, int maxheight) {

		Report aReport = new Report ();
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
		    return null;
		}

		Connection connection = null; 
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			connection = ResultsDataSource.getConnection("surveyKPI-ReportListSvc");
			String sql = sqlReport + sqlReportSpecific;
			
			log.info(sql + " : " + ident);
			pstmt = connection.prepareStatement(sql);
			pstmt.setString(1, ident);
			resultSet = pstmt.executeQuery();
			if (resultSet.next()) {		
				populateReport(request, aReport, resultSet, maxwidth, maxheight);	
			} else {
				log.info("Report : " + ident + " : not found");
				aReport = null;
			}
			
			
		} catch (Exception e) {
			aReport = null;
			log.log(Level.SEVERE,"Error", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return aReport;
		
	}
	

	/*
	 * Write an image to the disk
	 */
	private void writeImageFile(String image, File of) throws IOException  {
		
		String filteredData=image.substring(image.indexOf(',') + 1);			
		byte [] imageData = Base64.decodeBase64(filteredData);				

		FileOutputStream osf = new FileOutputStream(of);  
		osf.write(imageData);  
		osf.flush(); 
		osf.close();
	}
	
	private void copyImageFile(String inURL, String name, String serverName, String basePath) throws IOException {

		// Get the input URL from the server excluding the protocol
		String inServerPath = null;
		if(inURL.startsWith("https")) {
			inServerPath = inURL.substring(8);
		} else if(inURL.startsWith("http")) {
			inServerPath = inURL.substring(7);
		} else if(inURL.startsWith("//")) {
			inServerPath = inURL.substring(2);
		} else {
			inServerPath = inURL;
			log.info("Invalid input URL for image file:" + inURL);
		}
		int idx = inServerPath.indexOf('/');	// Remove the server name
		inServerPath = inServerPath.substring(idx);
		
		String inPath = basePath + "/" + inServerPath;		
		String outPath = basePath + "/attachments/report/" + name;
		
		if(!inPath.equals(outPath)) {		
			File inputFile = new File(inPath);
			File outputFile = new File(outPath);
			outputFile.delete();
			FileInputStream is = new FileInputStream(inputFile); 
		    FileOutputStream os = new FileOutputStream(outputFile);
		    IOUtils.copy(is, os);
		    os.flush();
		    os.close();
		    is.close();
		} else {
			// The paths are the same probably because an existing report has been edited
			log.info("Images not copied - identical paths: " + inPath);
		}
	}
	
	

	/*
	 * Add the stored data on the report to a report object
	 */
	private void populateReport(HttpServletRequest request, Report aReport, ResultSet resultSet, 
			int maxwidth, int maxheight) throws SQLException {
		
		aReport.provider_url = request.getScheme() + "://" + request.getServerName();
		String urlprefix = aReport.provider_url + "/";
		
		aReport.smap.data_type = resultSet.getString("item_type");
		aReport.title = resultSet.getString("title");
		aReport.smap.pub_date = resultSet.getTimestamp("pub_date");
		aReport.smap.epoch = resultSet.getInt("epoch");
		aReport.author_name = resultSet.getString("author");
		aReport.url = urlprefix + resultSet.getString("url");
		aReport.thumbnail_url = urlprefix + resultSet.getString("thumb_url");
		aReport.smap.data_url = urlprefix + resultSet.getString("data_url");
		aReport.smap.country = resultSet.getString("country");
		aReport.smap.region = resultSet.getString("region");
		aReport.smap.district = resultSet.getString("district");
		aReport.smap.community = resultSet.getString("community");
		aReport.smap.category = resultSet.getString("category");
		aReport.smap.description = resultSet.getString("description");
		aReport.smap.project_id = resultSet.getInt("p_id");
		aReport.smap.ident = resultSet.getString("ident");
		aReport.width = resultSet.getInt("width");
		aReport.height = resultSet.getInt("height");
		aReport.thumbnail_height = resultSet.getInt("t_height");
		aReport.thumbnail_width = resultSet.getInt("t_width");
		
		String locn = resultSet.getString("locn");
		aReport.smap.bbox = Geo.bboxToArray(locn);

		/*
		 * Limit the dimensions of items to the maximum values requested by client
		 */
		if(maxheight > 0 && maxwidth > 0 && aReport.width > 0 && aReport.height > 0) {
			if(aReport.width > maxwidth) {	
				aReport.height = aReport.height * maxwidth / aReport.width;
				aReport.width = maxwidth;
			}
			if(aReport.height > maxheight) {	
				aReport.width = aReport.width * maxheight / aReport.height;
				aReport.height = maxheight;
			}
		}
		
		/*
		 * Set the report type
		 */
		String item_type = aReport.smap.data_type;
		if(item_type != null) {
			if(item_type.equals("map")) {
				aReport.type = "rich";
			} else if(item_type.equals("table")) {
				aReport.type = "rich";
			} else if(item_type.equals("photo")) {
				aReport.type = "photo";
			} else if(item_type.equals("graph")) {
				aReport.type = "rich";
			} else if(item_type.equals("video")) {
				aReport.type = "video";
			}
		}
		
		aReport.smap.report_url = urlprefix + "surveyKPI/reports/view/" + aReport.smap.ident;
			
		
	}
	
	/*
	 * Get the dimensions of an image from its URL
	 */
	private ImageDim getImageDimension(String urlString) {
		
		ImageDim dim = new ImageDim();
		try {
			
			URL urlFile = new URL(urlString);
			BufferedImage bimg = ImageIO.read(urlFile);
			dim.width = bimg.getWidth();
			dim.height = bimg.getHeight();

		} catch (Exception e) {
			log.info("Warning: Failed to get dimensions of: " + urlString);
			log.info(e.getMessage());
		}
		
		return dim;
	}
	

}

