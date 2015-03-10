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
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * Exports a survey as an OSM file
 *    
 */
@Path("/exportSurveyOSM/{sId}/{filename}")
public class ExportSurveyOSM extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyOSM.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}
	
	private class Tag {
		public String key;
		public String value;
		
		Tag(String k, String v) {
			key = k;
			value = v;
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
		String f_id;
		String parent;
		String table_name;
		String columns = null;
		String parkey = null;
		String form_name;
		ArrayList<Tag> tags = null;
		boolean isWay = false;
		boolean isPolygon = false;
		ArrayList<FormDesc> children = null;
		
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
		System.out.println("urlprefix: " + urlprefix);
		System.out.println("Including read only: " + exp_ro);
		
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
		
		System.out.println("New OSM export, ways:" + waylist );
		if(waylist != null) {
			wayArray = waylist.split(",");
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-ExportSurveyOSM");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation

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

		System.out.println("Escaped file name:" + escapedFileName);
		builder.header("Content-Disposition", "attachment; filename=\"" + escapedFileName + ".osm\"");
		
		if(language != null) {
			language = language.replace("'", "''");	// Escape apostrophes
		} else {
			language = "none";
		}
		System.out.println("Language:" + language);

		if(sId != 0) {
			
			Connection connectionResults = null;
			PreparedStatement pstmt = null;
			PreparedStatement pstmt2 = null;
			PreparedStatement pstmtQType = null;

			try {
 
				// Prepare the statement to get the question type and readonly attribute
				String sqlQType = "select q.qtype, q.readonly from question q, form f " +
						" where q.f_id = f.f_id " +
						" and f.table_name = ? " +
						" and q.qname = ?;";
				pstmtQType = connectionSD.prepareStatement(sqlQType);
				
				HashMap<String, FormDesc> forms = new HashMap<String, FormDesc> ();			// A description of each form in the survey
				ArrayList <FormDesc> formList = new ArrayList<FormDesc> ();					// A list of all the forms
				FormDesc topForm = null;
				HashMap<String, Way> ways = new HashMap<String, Way> ();
							
				connectionResults = ResultsDataSource.getConnection("surveyKPI-ExportSurvey");
				
				/*
				 * Get the array of tables 
				 */
				String sql = null;
				ResultSet resultSet2 = null;
			
				sql = "SELECT f_id, table_name, parentform, name FROM form" +
						" WHERE s_id = ? " +
						" ORDER BY f_id;";	

				pstmt = connectionSD.prepareStatement(sql);	
				pstmt.setInt(1, sId);
				ResultSet resultSet = pstmt.executeQuery();
				
				while (resultSet.next()) {

					FormDesc fd = new FormDesc();
					fd.f_id = resultSet.getString("f_id");
					fd.parent = resultSet.getString("parentform");
					fd.table_name = resultSet.getString("table_name");
					fd.form_name = resultSet.getString("name");
					if(wayArray != null) {
						for(int i = 0; i < wayArray.length; i++) {
							if(wayArray[i].trim().equals(fd.f_id.trim())) {
								fd.isWay = true;
								if(fd.form_name != null && fd.form_name.startsWith("geopolygon")) {
									fd.isPolygon = true;
								}
								break;
							}
						}
					}
					forms.put(fd.f_id, fd);
					if(fd.parent == null) {
						topForm = fd;
					} 
				}
				
				/*
				 * Put the forms into a list in top down order
				 */
				formList.add(topForm);		// The top level form
				addChildren(topForm, forms, formList);
								

				for(FormDesc f : formList) {

					// Get the column names
					sql = "SELECT * FROM " + f.table_name + " LIMIT 1;";
					
					if(pstmt2 != null) {pstmt2.close();};
					pstmt2 = connectionResults.prepareStatement(sql);
					if(resultSet2 != null) {resultSet2.close();};
					resultSet2 = pstmt2.executeQuery();
					ResultSetMetaData rsMetaData2 = resultSet2.getMetaData();
					
					for(int j = 1; j <= rsMetaData2.getColumnCount(); j++) {
						String name = rsMetaData2.getColumnName(j);
						String type = rsMetaData2.getColumnTypeName(j);
						
						// Ignore the following columns
						if(name.equals("parkey") ||	name.startsWith("_")) {
							continue;
						}
							
						// Set the sql selection text for this column 			
						String selName = null;
						if(type.equals("geometry")) {
							selName = "ST_AsTEXT(" + name + ") ";
						} else if(type.equals("timestamptz")) {	// Return all timestamps at UTC with no time zone
							selName = "timezone('UTC', " + name + ") as " + name;	
						} else {
							// Get the question type
							pstmtQType.setString(1, f.table_name);
							pstmtQType.setString(2, name);
							ResultSet rsType = pstmtQType.executeQuery();
							boolean isAttachment = false;
							if(rsType.next()) {
								String qType = rsType.getString(1);
								boolean ro = rsType.getBoolean(2);
								if(!exp_ro && ro) {
									System.out.println("Dropping readonly: " + name);
									continue;			// Drop read only columns if they are not selected to be exported				
								}
								if(qType.equals("image") || qType.equals("audio") || qType.equals("video")) {
									isAttachment = true;
								}
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
				try {if (pstmtQType != null) {pstmtQType.close();	}} catch (SQLException e) {	}
				try {
					if (connectionSD != null) {
						connectionSD.close();
						connectionSD = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE, "Failed to close sd connection", e);
				}
				
				try {
					if (connectionResults != null) {
						connectionResults.close();
						connectionResults = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE, "Failed to close connection", e);
				}
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
	private String [] getLonLat(String point) {
		String [] coords = null;
		int idx1 = point.indexOf("(");
		int idx2 = point.indexOf(")");
		if(idx2 > idx1) {
			String lonLat = point.substring(idx1 + 1, idx2);
			coords = lonLat.split(" ");
		}
		return coords;
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
		ResultSetMetaData rsMetaData = null;
		
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
			resultSet = pstmt.executeQuery();
			rsMetaData = resultSet.getMetaData();
			
			System.out.println("getNodes: " + sql);
			while (resultSet.next()) {
				String prikey = resultSet.getString(1);				
				
				// Add the tags to the output node
				ArrayList<Tag> tags = new ArrayList<Tag>();
				String lon = null;
				String lat = null;
				for(int i = 2; i <= rsMetaData.getColumnCount(); i++) {
											
					String key = rsMetaData.getColumnName(i);
					String value = resultSet.getString(i);
					
					if(value != null && value.startsWith("POINT")) {	// Get the location
						String coords [] = getLonLat(value);
						if(coords != null && coords.length > 1) {
							lon = coords[0];
							lat = coords[1];		
						}
					} else if(key.startsWith("_")) {
						// Ignore default pre-loaded questions
					} else if(key.equals("the_geom")) {		
						// Ignore any other geometry types for the moment
					} else if (value != null && !key.startsWith("_")) {	
						tags.add(new Tag(key, value));
					}
					
					
				}
				if(lon != null && lat != null) {	// Ignore records without location
					populateNode(outputXML, rootElement, tags, lon, lat);
					if(f.isWay) {								// Add node to way
	
						String wayId;							// Get the way identifier
						if(f.parkey == null) {	
							wayId = f.f_id;
						} else {
							wayId = f.f_id + "_" + f.parkey;
						}
						
						Way way = ways.get(wayId);				// Get the way
						if(way == null) {
							way = new Way();
							ways.put(wayId, way);
						}
						
						way.nodes.add(String.valueOf(idcounter));		
						way.tags = f.tags;
						if(f.isPolygon) {
							way.isPolygon = true;
						}
					}
					idcounter--;
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
			ArrayList<Tag> tags, String lon, String lat) {
		
    	Element nodeElement = outputXML.createElement("node");
    	String id = Integer.toString(idcounter);
    	nodeElement.setAttribute("id", id);
    	nodeElement.setAttribute("visible", "true");
    	nodeElement.setAttribute("lat", lat);
    	nodeElement.setAttribute("lon", lon);
    	for(int i = 0; i < tags.size(); i++) {
    		Element tagElement = outputXML.createElement("tag");
    		Tag tag = tags.get(i);
    		tagElement.setAttribute("k", translate(tag.key));
    		tagElement.setAttribute("v", tag.value);
    		nodeElement.appendChild(tagElement);
    	}
    	rootElement.appendChild(nodeElement);
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
	private void addChildren(FormDesc parentForm, HashMap<String, FormDesc> forms, ArrayList<FormDesc> formList) {
		
		for(FormDesc fd : forms.values()) {
			if(fd.parent != null && fd.parent.equals(parentForm.f_id)) {
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
