package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.model.Query;
import org.smap.sdal.model.QueryForm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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

/*
 * Manage the table that stores queries
 */
public class QueryManager {
	
	private static Logger log =
			 Logger.getLogger(QueryManager.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	/*
	 * Get all queries for a user ident
	 */
	public ArrayList<Query> getQueries(Connection sd, String userIdent) throws SQLException {
		
		ArrayList<Query> queries = new ArrayList<Query>();	// Results of request
		
		String sql = "select query.id, query.name, query.query "
				+ "from custom_query query, users u "
				+ "where u.id = query.u_id "
				+ "and u.ident = ? "
				+ "order by query.name asc";
		PreparedStatement pstmt = null;
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Type type = new TypeToken<ArrayList<QueryForm>>(){}.getType();

		try {
	
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, userIdent);

			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				Query q = new Query();
				q.id = rs.getInt(1);
				q.name = rs.getString(2);
				String formsString = rs.getString(3);
				q.forms = gson.fromJson(formsString, type);
				
				queries.add(q);
			}
		} finally {
			try { if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return queries;
		
	}
	
	
}


