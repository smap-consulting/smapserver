package surveyKPI;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringEscapeUtils;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.model.TableColumn;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * Exports a survey as an OSM file
 *    
 */
@Path("/exportSurveyOSM/{sId}/{filename}")
public class ExportSurveyOSM extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	LogManager lm = new LogManager();		// Application log
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyOSM.class.getName());
	

	private class Tag {
		public String key;
		public String value;
		
		Tag(String k, String v) {
			key = k;
			value = v;
		}
	}
	
	private class LonLat {
		public String lon;
		public String lat;
		public int id;
		
		LonLat(String lon, String lat, int id) {
			this.lon = lon;
			this.lat = lat;
			this.id = id;
		}
	}
	
	private class Way {
		ArrayList<String> nodes = null;
		ArrayList<Tag> tags = null;
		boolean isPolygon;
		
		private Way() {
			nodes = new ArrayList<String> ();
		}
	}
	
	private class FormDesc {
		int f_id;
		int parent;
		String table_name;
		String columns = null;
		String parkey = null;
		String form_name;
		ArrayList<Tag> tags = null;
		boolean isWay = false;
		boolean isPolygon = false;
		ArrayList<FormDesc> children = null;
		ArrayList<TableColumn> cols = null;
		
		public void print() {
			System.out.println("========= ");
			System.out.println("    f_id: " + f_id);
			System.out.println("    parent: " + parent);
			System.out.println("    table_name: " + table_name);
			System.out.println("    form_name: " + form_name);
			System.out.println("    columns: " + columns);
			System.out.println("    parkey: " + parkey);
			System.out.println("    isWay: " + isWay);
			System.out.println("    isPolygon: " + isPolygon);
		}
	}

	ArrayList<StringBuffer> parentRows = null;
	private int idcounter;
	
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response exportOSM (@Context HttpServletRequest request, 
			@PathParam("sId") int sId,
			@PathParam("filename") String filename,
			@QueryParam("ways") String waylist,
			@QueryParam("exp_ro") boolean exp_ro,
			@QueryParam("language") String language) {
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		
		Response response = null;
		ResponseBuilder builder = Response.ok();
		String wayArray [] = null;
		idcounter = -1;

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    try {
		    	String msg = "Error: Can't find PostgreSQL JDBC Driver";
				log.log(Level.SEVERE, msg, e);
		    	response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
			    return response;
		    } catch (Exception ex) {
		    	log.log(Level.SEVERE, "Exception", ex);
		    }
		}
		
		if(waylist != null) {
			wayArray = waylist.split(",");
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurveyOSM");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "Export as OSM");
		
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

		builder.header("Content-Disposition", "attachment; filename=\"" + escapedFileName + ".osm\"");
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}

		if(sId != 0) {
			
			Connection connectionResults = null;
			PreparedStatement pstmt = null;
			PreparedStatement pstmt2 = null;

			try {
 
				HashMap<Integer, FormDesc> forms = new HashMap<Integer, FormDesc> ();			// A description of each form in the survey
				ArrayList <FormDesc> formList = new ArrayList<FormDesc> ();					// A list of all the forms
				FormDesc topForm = null;
				HashMap<String, Way> ways = new HashMap<String, Way> ();
							
				connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");
				
				/*
				 * Get the tables in this survey
				 */
				String sql = "SELECT f_id, table_name, parentform, name FROM form" +
						" WHERE s_id = ? " +
						" ORDER BY f_id;";	

				pstmt = connectionSD.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				ResultSet resultSet = pstmt.executeQuery();
				
				while (resultSet.next()) {

					FormDesc fd = new FormDesc();
					fd.f_id = resultSet.getInt("f_id");
					fd.parent = resultSet.getInt("parentform");
					fd.table_name = resultSet.getString("table_name");
					fd.form_name = resultSet.getString("name");
					if(wayArray != null) {
						for(int i = 0; i < wayArray.length; i++) {
							if(Integer.parseInt(wayArray[i].trim()) == fd.f_id) {
								fd.isWay = true;
								if(fd.form_name != null && fd.form_name.startsWith("geopolygon")) {
									fd.isPolygon = true;
								}
								break;
							}
						}
					}
					forms.put(fd.f_id, fd);
					if(fd.parent == 0) {
						topForm = fd;
					} 
				}
				
				/*
				 * Put the forms into a list in top down order
				 */
				formList.add(topForm);		// The top level form
				addChildren(topForm, forms, formList);
								

				for(FormDesc f : formList) {
					
					f.cols = GeneralUtilityMethods.getColumnsInForm(
							connectionSD,
							connectionResults,
							sId,
							request.getRemoteUser(),
							f.parent,
							f.f_id,
							f.table_name,
							exp_ro,
							false,		// Don't include parent key
							false,		// Don't include "bad" columns
							false,		// Don't include instance id
							true		// Include other meta data
							);
					
					for(TableColumn col : f.cols) {
						String name = col.name;
						String qType = col.type;
						
						// Ignore the following columns
						if(name.equals("parkey") ||	name.startsWith("_")) {
							continue;
						}
							
						// Set the sql selection text for this column 			
						String selName = null;
						if(qType.equals("geopoint") || qType.equals("geoshape") 
								|| qType.equals("geotrace")
								|| qType.equals("geolinestring")
								|| qType.equals("geopolygon")) {
							selName = "ST_AsTEXT(" + name + ") as the_geom ";
						} else if(qType.equals("dateTime")) {	// Return all timestamps at UTC with no time zone
							selName = "timezone('UTC', " + name + ") as " + name;	
						} else {
							boolean isAttachment = false;
								if(qType.equals("image") || qType.equals("audio") || qType.equals("video")) {
									isAttachment = true;
								}
							if(isAttachment) {
								selName = "'" + urlprefix + "' || " + name + " as " + name;
							} else {
								selName = name;
							}
						}
						
						if(f.columns == null) {
							f.columns = selName;
						} else {
							f.columns += "," + selName;
						}

					}
				}
				
				/*
				 * Generate the XML
				 */
		    	try {  		
		    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		    		DocumentBuilder b = dbf.newDocumentBuilder();    		
		    		Document outputXML = b.newDocument();     		
		           	Writer outWriter = new StringWriter();
		           	Result outStream = new StreamResult(outWriter);
		           	
		        	Element rootElement = populateRoot(outputXML);									// Create a new XML Document	        	
		        	getNodes(outputXML, rootElement, connectionResults, formList, topForm, ways);	// Add the nodes
		        	getWays(outputXML, rootElement, ways);											// Add the ways
		           	
		        	Transformer transformer = TransformerFactory.newInstance().newTransformer();
		        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		        	DOMSource source = new DOMSource(outputXML);
		        	transformer.transform(source, outStream);
		        	
		        	response = builder.entity(outWriter.toString()).build();
		        	
			
		    	} catch (Exception e) {
		    		response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		    		log.log(Level.SEVERE, "Error", e);
		    	}
			
			} catch (SQLException e) {
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				log.log(Level.SEVERE, "SQL Error", e);
			} catch (Exception e) {
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
				log.log(Level.SEVERE, "Exception", e);
			} finally {
				try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
				try {if (pstmt2 != null) {pstmt2.close();	}} catch (SQLException e) {	}

				SDDataSource.closeConnection("surveyKPI-ExportSurveyOSM", connectionSD);
				ResultsDataSource.closeConnection("surveyKPI-ExportSurvey", connectionResults);
			}
		}
		
		return response;
 
	}
	
	/*
	 * Populate the root osm element
	 */
    public Element populateRoot(Document outputXML) {

    	Element rootElement = outputXML.createElement("osm");
    	rootElement.setAttribute("version", "0.6");
    	rootElement.setAttribute("generator", "Smap");
    	outputXML.appendChild(rootElement);
    	
    	return rootElement;
    }

	
	/*
	 * Get the latitude, longitude from a WKT POINT
	 */
	private ArrayList<LonLat> getLonLat(String value) {
		String [] coords = null;
		String [] pointArray = null;
		ArrayList<LonLat> points = new ArrayList<LonLat> ();
		
		int idx1 = value.lastIndexOf("(");	// Polygons with two brackets supported (Multi Polygons TODO)
		int idx2 = value.indexOf(")");
		if(idx2 > idx1) {
			String coordString = value.substring(idx1 + 1, idx2);
			if(value.startsWith("POINT")) {
				coords = coordString.split(" ");
				points.add(new LonLat(coords[0], coords[1], idcounter--));
			} else if(value.startsWith("POLYGON") || value.startsWith("LINESTRING")) {			
				pointArray = coordString.split(",");
				for(int i = 0; i < pointArray.length; i++) {
					coords = pointArray[i].split(" ");
					points.add(new LonLat(coords[0], coords[1], idcounter--));
				}
			}
		}
		return points;
	}

	/*
	 * For each way add the osm xml
	 */
	private void getWays(Document outputXML, Element rootElement, HashMap<String, Way> ways) {
		
		for (String wayId : ways.keySet()) {
			Way way = ways.get(wayId);
			if(way.nodes.size() > 0) {
				Element wayElement = outputXML.createElement("way");
		    	String id = Integer.toString(idcounter--);
		    	wayElement.setAttribute("id", id);
		    	wayElement.setAttribute("visible", "true");
		    	if(way.tags != null) {
		    		for(int i = 0; i < way.tags.size(); i++) {
		    	   		Element tagElement = outputXML.createElement("tag");
		        		Tag tag = way.tags.get(i);
		        		tagElement.setAttribute("k", translate(tag.key));
		        		tagElement.setAttribute("v", tag.value);
		        		wayElement.appendChild(tagElement);
		    		}
		    	}
		    	for(int i = 0; i < way.nodes.size(); i++) {
		    		Element ndElement = outputXML.createElement("nd");
		    		ndElement.setAttribute("ref", way.nodes.get(i));
		    		wayElement.appendChild(ndElement);
		    	}
		    	if(way.isPolygon) {	// Complete the circle
		    		Element ndElement = outputXML.createElement("nd");
		    		ndElement.setAttribute("ref", way.nodes.get(0));
		    		wayElement.appendChild(ndElement);
		    	}
		    	rootElement.appendChild(wayElement);
			}
		}
	}
	
	
	/*
	 * For each record in the top level table all records in other tables that
	 * can link back to the top level record are retrieved.  
	 * 
	 * The function is called recursively until the last table
	 */
	private void getNodes(Document outputXML, Element rootElement, Connection connectionResults,  
			ArrayList<FormDesc> formList, FormDesc f, HashMap<String, Way> ways) {
		
		String sql = null;
		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		
		//f.print();
		/*
		 * Retrieve the data for this table
		 */
		sql = "SELECT " + f.columns + " FROM " + f.table_name +
				" WHERE _bad IS FALSE";		
		
		if(f.parkey != null) {
			sql += " AND parkey=? ";
		}
		sql += " ORDER BY prikey ASC;";	
		
		try {
			pstmt = connectionResults.prepareStatement(sql);	 					
			if(f.parkey != null) {
				pstmt.setInt(1, Integer.parseInt(f.parkey));
			}
			log.info("Getting node data: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			
			while (resultSet.next()) {
				ArrayList<LonLat> points = null;
				String prikey = resultSet.getString(1);				
				
				// Add the tags to the output node
				ArrayList<Tag> tags = new ArrayList<Tag>();
				
				for(TableColumn col : f.cols) {
					
					String key = col.name;
					if(key.equals("prikey") ||
							key.equals("instancename") ||	
							key.equals("parkey") ||	
							key.startsWith("_")) {
						continue;
					}
					String value = resultSet.getString(key);
					
					if(value != null) { 
						if(key.equals("the_geom")) {  // Get the location
							points = getLonLat(value);
						} else if(!key.startsWith("_")) {	
							tags.add(new Tag(key, value));
						}
					}
					
					
				}
				if(points != null && points.size() > 0) {	// Ignore records without location
					if(points.size() == 1) {
						populateNode(outputXML, rootElement, tags, points);
					} else {
						populateNode(outputXML, rootElement, null, points);		// No node tags with polygons and lines
					}
					if(f.isWay || points.size() > 1) {			// Add nodes to way
	
						String wayId;							// Get the way identifier
						Way way = null;
						if(points.size() == 1) {
							if(f.parkey == null) {	
								wayId = String.valueOf(f.f_id);
							} else {
								wayId = f.f_id + "_" + f.parkey;
							}
							way = ways.get(wayId);				// Get the way
							if(way == null) {
								way = new Way();
								ways.put(wayId, way);
							}
							way.nodes.add(String.valueOf(points.get(0).id));	
						} else {
							wayId = String.valueOf(idcounter);
							way = new Way();
							ways.put(wayId, way);
							for(int i = 0; i < points.size(); i++) {
								way.nodes.add(String.valueOf(points.get(i).id));
							}
						}
						
						
						if(points.size() == 1) {
							way.tags = f.tags;
						} else {
							way.tags = tags;
						}
						if(f.isPolygon) {		// Only required for polygons constructed from a subform of points (real polygons are already closed)
							way.isPolygon = true;
						}
					}
				}
								
				// Process child tables
				if(f.children != null) {
					for(int j = 0; j < f.children.size(); j++) {
						FormDesc nextForm = f.children.get(j);
						nextForm.parkey = prikey;
						nextForm.tags = tags;
						getNodes(outputXML, rootElement, connectionResults, formList, nextForm, ways);
					}
				}
			
			}
			
	
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			try{
				if(resultSet != null) {resultSet.close();};
				if(pstmt != null) {pstmt.close();};
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Unable to close resultSet or prepared statement");
			}
		}
		
	}
	
	/*
	 * Populate the XML for a Node
	 */
	private void populateNode(Document outputXML, Element rootElement,  
			ArrayList<Tag> tags, ArrayList<LonLat> points) {
		
		for(LonLat ll : points) {
	    	Element nodeElement = outputXML.createElement("node");
	    	String id = Integer.toString(ll.id);
	    	nodeElement.setAttribute("id", id);
	    	nodeElement.setAttribute("visible", "true");
	    	nodeElement.setAttribute("lat", ll.lat);
	    	nodeElement.setAttribute("lon", ll.lon);
	    	if(tags != null) {
		    	for(int i = 0; i < tags.size(); i++) {
		    		Element tagElement = outputXML.createElement("tag");
		    		Tag tag = tags.get(i);
		    		tagElement.setAttribute("k", translate(tag.key));
		    		tagElement.setAttribute("v", tag.value);
		    		nodeElement.appendChild(tagElement);
		    	}
	    	}
	    	rootElement.appendChild(nodeElement);
		}
	}
	
	/*
	 * Translate keys into osm standard values
	 *  This is required where odk/xlsform cannnot represent the key in the syntax reqd by osm
	 */
	private String translate(String in) {
		if(in.startsWith("addr_")) {
			int idx = in.indexOf('_');
			return "addr:" + in.substring(idx+1);
		} else  {
			return in;
		}
	}
	/*
	 * Add the list of children to parent forms
	 */
	private void addChildren(FormDesc parentForm, HashMap<Integer, FormDesc> forms, ArrayList<FormDesc> formList) {
		
		for(FormDesc fd : forms.values()) {
			if(fd.parent != 0 && fd.parent == parentForm.f_id) {
				if(parentForm.children == null) {
					parentForm.children = new ArrayList<FormDesc> ();
				}
				parentForm.children.add(fd);
				formList.add(fd);
				addChildren(fd,  forms, formList);
			}
		}
		
	}


}
