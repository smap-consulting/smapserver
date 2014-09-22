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

package com.cloudtec.fieldManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;

/**
 * This servlet is called by a mobile user to update their status and location.
 * 
 * @author Wenjing Yan
 */
public class UpdateStatusServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  The database configuration.
	 */
	private final String dbClass = "org.postgresql.Driver";
	
	/**
	 * Handles HTTP GET requests.
	 */
	public void doGet( HttpServletRequest request, HttpServletResponse response ) 
		throws ServletException, IOException {
		response.setContentType( "text/javascript" );
		PrintWriter out = response.getWriter();
		
		//String userid = request.getRemoteUser();
		//String fakeuserid = request.getParameter( "userid" );
		String fakeuserid = "wenjing";
		JSONObject jsonObject = getUserStatus( fakeuserid );
		System.out.println( "DEBUG: " + jsonObject );
		
		out.print( jsonObject.toString() );
		out.flush();
		
		//updateStatus( "testuser", 123.789, -123.789, "avaliable" );
	}

	/**
	 * Handles HTTP POST requests.
	 */
	public void doPost( HttpServletRequest request, HttpServletResponse response ) 
		throws ServletException, IOException {
		//response.setContentType("application/json");
		
		
		/**********
		StringBuffer buffer = new StringBuffer();
		String line = null;
		try {
			BufferedReader reader = request.getReader();
			while ( ( line = reader.readLine() ) != null ) {
				buffer.append( line );
			}
			
			jsonObj = new JSONObject( buffer.toString() );
			
			System.out.println( "GET >> " + buffer.toString() );
			
		} catch ( Exception e ) {
			e.printStackTrace();
		}
		***********/
		
		System.out.println( " THIS IS DO POST!!!! " );
		
	}

	///
	/// Utility methods for updating user status
	///
	
	/**
	 * Updates user status and location.
	 */
	private void updateStatus( String userid, double lat, double lon, String status ) {
		Connection dbConnection = null;
		String sql = null;
		try {
		    Class.forName( dbClass );
			dbConnection = TMDataSource.getConnection();
			Statement statement = dbConnection.createStatement();

			sql = "INSERT INTO user_location( userid, location, status ) VALUES (" +
				" '" + userid + "', ST_GeomFromText( 'POINT( " + lat + " " + lon + 
				" )', 4326 ), '" + status + "' );";
			System.out.println( "Executed: " + sql );
			statement.execute( sql );
		} catch ( Exception e ) {
			if ( dbConnection != null ) {
				try {
					dbConnection.rollback();
				} catch ( SQLException ex ) {
					System.err.println( "Error: Rolling back: " + ex.getMessage() );
				}
			}
		} finally {
			if ( dbConnection != null ) {
				try {
					dbConnection.close();
				} catch ( SQLException e ) {
					System.err.println( "Error: fail to close datatbase connection" );
				}
			}
		}
	}
	
	///
	/// Utility methods for getting user status
	///
	
	/**
	 * Returns user information from database.
	 */
	private JSONObject getUserStatus( String userid ) {
		JSONObject jsonResult = new JSONObject();
		Connection dbConnection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
		    Class.forName( dbClass );
			dbConnection = TMDataSource.getConnection();
			statement = dbConnection.createStatement();
			
			String sql = "SELECT * FROM user_location WHERE userid = '" + userid + "';";
			rs = statement.executeQuery( sql );
			System.out.println( "Executed: " + sql );
			
			rs.next(); // Points to the first row
			if ( rs.getRow() == 1 ) { // Makes sure unique record for current userid
				Vector<String> columnNames = getColumnNames( rs );
				for ( String columnName : columnNames ) {
					// TODO change to return GeoJson object
					if ( columnName.equalsIgnoreCase( "location" ) ) {
						sql = "SELECT AsText( location ) FROM user_location " +
							"WHERE userid = '" + userid + "';";
						ResultSet rs_1 = statement.executeQuery( sql );
						System.out.println( "Executed: " + sql );
						rs_1.next();
						jsonResult.put("location", rs_1.getObject( 1 ) );
					}
					
					jsonResult.put( columnName, rs.getObject( columnName ) );
				}
			}
		} catch ( Exception e ) {
			if ( dbConnection != null ) {
				try {
					dbConnection.rollback();
				} catch ( SQLException ex ) {
					System.err.println( "Error: Rolling back: " + ex.getMessage() );
				}
			}
		} finally {
            if ( dbConnection != null ) {
	            try {
					dbConnection.close();
					statement.close();
		            rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
            }
		}
		
		return jsonResult;
	}
	
	/**
	 * Gets column names from ResultSet.
	 */
	private Vector<String> getColumnNames(ResultSet rs ) {
		Vector<String> names = new Vector<String>();
		
	    if (rs == null) {
	    	return names;
	    }
	    
	    try {
		    ResultSetMetaData metadata = rs.getMetaData();
		    int numOfColumns = metadata.getColumnCount();
		    for ( int i = 1; i < numOfColumns + 1; i++ ) {
		    	names.add( metadata.getColumnName( i ) );
		    	
		    }
	    } catch ( SQLException e ) {
	    	e.printStackTrace();
	    }
		
	    return names;
	}
}
