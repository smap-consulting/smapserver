package com.cloudtec.fieldManager;

import java.sql.Connection;
import javax.naming.InitialContext;
import javax.sql.DataSource;

public class TMDataSource {
	private static DataSource ds = null;
	
	private TMDataSource() {
		try {

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static Connection getConnection() {

		if(ds == null) {
			try {
				InitialContext cxt = new InitialContext();
				ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/task_management" );
			} catch (Exception e){
				e.printStackTrace();
				return null;
			}
		}
		try {
			return ds.getConnection();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	

}
