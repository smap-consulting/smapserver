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
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the valid forms for this function
 */

@Path("/ssc/{sId}/{fn}")
public class ServerSideCalculates extends Application {
	
	Authorise a = new Authorise(Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ServerSideCalculates.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(ServerSideCalculates.class);
		return s;
	}

	private class Form {
		int fId;
		String name;
	}
	private ArrayList<Form> formList = new ArrayList<Form> ();
	
	@Path("/forms")
	@GET
	public Response getForms(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("fn") String fn) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {
			String sql = null;
			if(fn.equals("area")) {
				sql = "SELECT DISTINCT f.f_id, f.name from form f, question q " +
						" where f.f_id = q.f_id " +
						" and f.s_id = ? " + 
						" and (q.qtype='geopolygon' OR q.qtype='geoshape') " +
						" order by f.name;";
			} else if(fn.equals("length")) {
				sql = "SELECT DISTINCT f.f_id, f.name from form f, question q " +
						" where f.f_id = q.f_id " +
						" and f.s_id = ? " + 
						" and (q.qtype='geopolygon' OR q.qtype='geoshape' OR q.qtype='geolinestring' OR q.qtype='geotrace') " +
						" order by f.name;";
			} else {	// All forms are valid
				sql = "SELECT DISTINCT f.f_id, f.name from form f " +
						" where f.s_id = ? " + 
						" order by f.name;";
			}
			pstmt = connectionSD.prepareStatement(sql);	
			
			pstmt.setInt(1, sId);
			System.out.println("sql: " + sql + " : " + sId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {	
				Form f = new Form();
				f.fId = rs.getInt(1);
				f.name = rs.getString(2);
				formList.add(f);
			}			
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(formList);
			response = Response.ok(resp).build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
	
	@Path("/add")
	@POST
	public Response addServerSideCalculate(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("fn") String fn,
			@FormParam("name") String name,
			@FormParam("form") int fId,
			@FormParam("units") String units) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		System.out.println("Name: " + name + " : " + fId);
		PreparedStatement pstmtDupName = null;
		PreparedStatement pstmtDupSSC = null;
		PreparedStatement pstmtAddSSC = null;
		
		try {
			/*
			 * Check that the name is not already in use in this survey
			 */
			String sql = "SELECT count(*) from question q, form f " +
					" where f.f_id = q.f_id " +
					" and f.s_id = ? " + 
					" and q.qname = ?;";
			
			pstmtDupName = connectionSD.prepareStatement(sql);	
			
			pstmtDupName.setInt(1, sId);
			pstmtDupName.setString(2, name);
			System.out.println("sql: " + sql + " : " + sId + " : " + name);
			ResultSet rs = pstmtDupName.executeQuery();
			int count = 0;
			if (rs.next()) {	
				count = rs.getInt(1);
			}
			if(count > 0) {
				throw new ApplicationException("Name already in use");
			}
			
			/*
			 * Check that the name is not already in use as a server side calculate function
			 */
			sql = "SELECT count(*) from ssc " +
					" where s_id = ? " +
					" and name = ?;";
			
			pstmtDupSSC = connectionSD.prepareStatement(sql);	
			
			pstmtDupSSC.setInt(1, sId);
			pstmtDupSSC.setString(2, name);
			System.out.println("sql: " + sql + " : " + sId + " : " + name);
			rs = pstmtDupSSC.executeQuery();
			count = 0;
			if (rs.next()) {	
				count = rs.getInt(1);
			}
			if(count > 0) {
				throw new ApplicationException("Name already in use as as server side calculate value");
			}
			
			/*
			 * Add the server side calculate function
			 */
			sql = "insert into ssc (s_id, f_id, name, function, units) values " +
					" (?, ?, ?, ?, ?); ";
			pstmtAddSSC = connectionSD.prepareStatement(sql);	
			pstmtAddSSC.setInt(1, sId);
			pstmtAddSSC.setInt(2, fId);
			pstmtAddSSC.setString(3, name);
			pstmtAddSSC.setString(4, fn);
			pstmtAddSSC.setString(5, units);
			pstmtAddSSC.executeUpdate();
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} catch (ApplicationException e) {
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmtDupName != null) {pstmtDupName.close();}} catch (SQLException e) {}
			try {if (pstmtDupSSC != null) {pstmtDupSSC.close();}} catch (SQLException e) {}
			try {if (pstmtAddSSC != null) {pstmtAddSSC.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}
	
	@Path("/delete/{id}")
	@DELETE
	public Response deleteServerSideCalculate(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("fn") String fn,
			@PathParam("id") int id) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		
		try {
		
			String sql = "delete from ssc  " +
					" where s_id = ? " +
					" and id = ?; ";
			
			pstmt = connectionSD.prepareStatement(sql);	
			
			pstmt.setInt(1, sId);
			pstmt.setInt(2, id);
			System.out.println("sql: " + sql + " : " + sId + " : " + id);
			pstmt.executeUpdate();
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		}  catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;

	}

}

