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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.ResultsDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Table related functions
 */

@Path("/table/{table}")
public class Table extends Application {

	private static Logger log =
			 Logger.getLogger(UserList.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Table.class);
		return s;
	}
	
	private class Column {
		String name;
	}
	
	private class TableDesc {
		ArrayList<Column> columns = new ArrayList<Column> ();
		String column_name;
	}

	
	/*
	 * Get all the columns for a table
	 */
	@GET
	public Response getColumns(@PathParam("table") String tableName) { 	
		
		Response response = null;
		PreparedStatement pstmt = null;
		TableDesc t = new TableDesc(); 
		
		if(tableName != null) {
			tableName = tableName.toLowerCase();	// Support for older surveys without clean table name
			
			try {
			    Class.forName("org.postgresql.Driver");	 
			} catch (ClassNotFoundException e) {
				log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			    response = Response.serverError().build();
			    return response;
			}
	
			Connection connectionRel = null; 
			try {
				
				String sql = "select column_name from information_schema.columns where table_name = ? " +
						"order by ordinal_position asc;";
				connectionRel = ResultsDataSource.getConnection("surveyKPI-Table");
				pstmt = connectionRel.prepareStatement(sql);
				pstmt.setString(1,  tableName);
				
				ResultSet resultSet = pstmt.executeQuery();
				while(resultSet.next()) {
					Column c = new Column();
					c.name = resultSet.getString("column_name");
					t.columns.add(c);
				}
				
				
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				String resp = gson.toJson(t);
				response = Response.ok(resp).build();
					
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Error: No organisation", e);
			    response = Response.serverError().build();
			    

			} finally {
				if(pstmt != null) try {	pstmt.close(); } catch(SQLException e) {};
				if (connectionRel != null) try { connectionRel.close();	} catch(SQLException e) {};
			}

		}
		return response; 
	}
}

