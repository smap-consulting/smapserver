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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import model.GenRegion;
import model.Settings;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/regions")
public class Regions extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Regions.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Regions.class);
		return s;
	}


	public Regions() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
	}
	
	// JSON
	@GET
	@Produces("application/json")
	public String getEvents(@Context HttpServletRequest request) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver: ", e);
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Regions");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		JSONArray ja = new JSONArray();	

		ResultSet resultSet = null;
		PreparedStatement pstmt = null;
		try {
		
			String sql = "SELECT r.region_name, r.table_name FROM regions r, users u " +
					" WHERE u.ident = ? " +
					" AND u.o_id = r.o_id " +
					"ORDER BY region_name;";
		
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, request.getRemoteUser());
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {								

				JSONObject jp = new JSONObject();
				jp.put("name", resultSet.getString(1));
				jp.put("table", resultSet.getString(2));

				ja.put(jp);
			} 

			
		} catch (SQLException e) {
		    log.log(Level.SEVERE,"Error: ", e);
		} catch (JSONException e) {
			log.log(Level.SEVERE,"Error: ", e);
		} catch(Exception e) {
			log.log(Level.SEVERE,"Error: ", e);
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Error: Failed to close connection", e);
			}
		}

		return ja.toString();
	}
	
	/*
	 * Create a region
	 */
	@POST
	@Consumes("application/json")
	public Response createRegion(@Context HttpServletRequest request,
			@FormParam("settings") String settings) { 
		
		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Regions");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		// Escape any quotes
		if(settings != null) {
			settings = settings.replace("'", "''"); 
		} 
		
		Type type = new TypeToken<ArrayList<Settings>>(){}.getType();		
		GenRegion region = new Gson().fromJson(settings, GenRegion.class);
		PreparedStatement pstmt = null;

		Connection connection = null; 
		try {
			
			// Get the organisation id
			String sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
						
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			log.info("SQL: " + sql + ":" + request.getRemoteUser());
			ResultSet resultSet = pstmt.executeQuery();
			
			int o_id = 0;
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
			}
			pstmt.close();

			// Clean the name so that it is suitable as a table name
			String tableName = "r_" + o_id + "_" + region.name;
			tableName = tableName.toLowerCase().replace(" ", "");
			
			connection = ResultsDataSource.getConnection("surveyKPI-Regions");
			connection.setAutoCommit(false);
			connectionSD.setAutoCommit(false);
			
			// Create the table
			sql = "CREATE TABLE " + tableName + " (gid serial not null primary key, name text);";
			log.info(sql);
			try {
				pstmt = connection.prepareStatement(sql);
				pstmt.executeUpdate();
			} catch (SQLException e) {
				try { connection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
				try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
				System.out.println(e.getMessage());
				String resp = e.getMessage();		
				response = Response.ok(resp).build();		// Most likely message is that table already exists.  Return this to the user
				return response;
			}
			
			
			// Add the geometry column
			sql = "SELECT addgeometrycolumn(?,'the_geom', 4326, 'POLYGON', 2);";
			log.info(sql);
			pstmt = connection.prepareStatement(sql);
			pstmt.setString(1, tableName);
			pstmt.executeQuery();
			
			System.out.println("Centre x:" + region.centre_x);
			System.out.println("Left x:" + region.lower_left_x);
			System.out.println("Right x:" + region.upper_right_x);
			System.out.println("Width:" + region.width);
			
			int hr = (int) Math.ceil(region.width / 4);	// Half the radius
			int r = hr *2;
			int w = hr * 4;	// Adjusted width to keep everyting proportional
			int cx = region.centre_x;
			int cy = region.centre_y;
			int llx = region.lower_left_x;
			int lly = region.lower_left_y;
			int urx = region.upper_right_x;
			int ury = region.upper_right_y;
			
			// Starting x position is an even number of cells from the centre that is just outside the bounds
			int startX = cx - w * ((int) Math.ceil((cx - llx) / w));
			int startY = cy - w * ((int) Math.ceil((cy - lly) / w));
			int stopX = cx + w * ((int) Math.ceil((urx - cx) / w));
			int stopY = cy + w * ((int) Math.ceil((ury - cy) / w));
			
			System.out.println("bounds:" + startX + ":" + startY + ":" + stopX + ":" + stopY);
			
			// Generate the grid using the function from http://trac.osgeo.org/postgis/wiki/UsersWikiGenerateHexagonalGrid
			String polygon = "0 0," + hr + " " +  hr + "," + hr + " " + r + ", 0 " + (hr*3) + "," + (-hr) + " " + r + "," + (-hr) + " " + hr + ",0 0";
			sql = "INSERT INTO " + tableName + " (the_geom) " +
					" SELECT st_transform(st_translate(the_geom, x_series, y_series), 4326)" +
					" from generate_series(" + (startX - r) + "," + (stopX + r) + "," + r + ") as x_series," +
					" generate_series(" + (startY - r) + "," + (stopY + r) + "," + w + ") as y_series, " + 
						" (" +
						" SELECT ST_GeomFromEWKT('srid=900913;POLYGON((" + polygon + "))') as the_geom" +
						" UNION" +
						" SELECT st_translate(ST_GeomFromEWKT('srid=900913;POLYGON((" + polygon + "))'), " + hr + ", " + r + ")  as the_geom" +
						" ) as two_hex;";
			
			log.info(sql);
			pstmt = connection.prepareStatement(sql);
			pstmt.executeUpdate();	
			
			sql = "update " + tableName + " set name = gid;";
			log.info(sql);
			pstmt = connection.prepareStatement(sql);
			pstmt.executeUpdate();
			pstmt.close();
			
			/*
			 * Add the new table to the tables available for aggregation
			 */
			sql = "insert into regions (o_id, table_name, region_name) values (?,?,?);";
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, o_id);
			pstmt.setString(2, tableName);
			pstmt.setString(3, region.name);
			pstmt.executeUpdate();
			
			connection.commit();
			connectionSD.commit();
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			try { connection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (connection != null) {
					connection.setAutoCommit(true);
					connection.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}
	
	/*
	 * Delete a region
	 */
	@DELETE
	@Path("/{region}")
	public Response deleteRegion(@Context HttpServletRequest request,
			@PathParam("region") String region) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Regions");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		// Escape any quotes
		if(region != null) {
			region = region.replace("'", "''"); 
		} 

		Connection connection = null; 
		PreparedStatement pstmt = null;
		try {
			
			connection = ResultsDataSource.getConnection("surveyKPI-Regions");
			connection.setAutoCommit(false);
			connectionSD.setAutoCommit(false);
				
			// Get the organisation id
			String sql = "SELECT u.o_id " +
					" FROM users u " +  
					" WHERE u.ident = ?;";				
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, request.getRemoteUser());
			ResultSet resultSet = pstmt.executeQuery();
			
			int o_id = 0;
			if(resultSet.next()) {
				o_id = resultSet.getInt(1);
			}
			pstmt.close();
			
			// Get the table name
			sql = "SELECT r.table_name " +
					" FROM regions r " +  
					" WHERE r.region_name = ?;";				
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setString(1, region);
			resultSet = pstmt.executeQuery();
			
			String tableName = null;
			if(resultSet.next()) {
				tableName = resultSet.getString(1);
			}
			pstmt.close();
			
			// delete the tables entry in the geometry table
			sql = "delete from regions r " +
					" WHERE table_name = ? " +
					" AND o_id = ?;";
			
			log.info(sql);
			pstmt = connectionSD.prepareStatement(sql);
			System.out.println("Table:" + tableName);
			pstmt.setString(1, tableName);
			pstmt.setInt(2, o_id);
			int count = pstmt.executeUpdate();
			System.out.println("Count:" + count);
				
			// delete the table
			sql = "drop table if exists " + tableName + ";";		// Ignore errors if table does nto exist
			log.info(sql);
			pstmt = connection.prepareStatement(sql);
			pstmt.executeUpdate();
						
			connection.commit();
			connectionSD.commit();
			response = Response.ok().build();
				
		} catch (Exception e) {
			response = Response.serverError().build();
			log.log(Level.SEVERE,"Error", e);
			try { connection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			try { connectionSD.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.setAutoCommit(true);
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (connection != null) {
					connection.setAutoCommit(true);
					connection.close();
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}

}

