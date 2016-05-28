package org.smap.sdal.Utilities;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.sql.DataSource;

public class ResultsDataSource {
	
	private static Logger log =
			 Logger.getLogger(ResultsDataSource.class.getName());
	
	
	private ResultsDataSource() {
	}
	
	private static int count;
	
	public static Connection getConnection(String requester) {

		try {
			InitialContext cxt = new InitialContext();
			DataSource ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/results" );

			Connection c = ds.getConnection();
			c.setAutoCommit(true);		// Can't rely on auto commit being set to true when connection comes from pool
			count++;
			log.info(" ++++ " + count + " Create Results connection: " + requester);
			return c;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error getting results datasource", e);
			return null;
		}
	}
	
	public static void closeConnection(String requester, Connection c) {

		if (c != null) try { 
			c.setAutoCommit(true);
			c.close(); 
			count--;
			log.info(" ---- " + count + " Close Results connection: " + requester);
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Failed to close results connection", e);
		}
	}
}
