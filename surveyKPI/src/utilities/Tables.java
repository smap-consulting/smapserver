package utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

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

public class Tables {
	
	private class TableInfo {
		private int fId;
		private String tName;
		private int pId;
		private String parentName;
		
		public TableInfo(String tName, int fId, int pId) {
			this.fId = fId;
			this.tName = tName;
			this.pId = pId;
		}
		
		public int getFId() {
			return fId;
		}
		
		public String getTName() {
			return tName;
		}
		
		public int getPId() {
			return pId;
		}
		
		public String getParentName() {
			return parentName;
		}
	}
	
	HashMap<String, TableInfo> iTables = new HashMap<String, TableInfo> ();
	int sId;
	
	public Tables(int sId) {
		this.sId = sId;
	}
	
	// add a table
	public void add(String name, int fId, int parent) {
		System.out.println("Add table to iTables: " + name);
		iTables.put(name, new TableInfo(name, fId, parent));
	}

	/*
	 * Intermediate tables need to be added. For example
	 *   Table "hobby" has parent in table "member"
	 *   Table "member" has parent in table "household"
	 * The query requires a value in table "hobby" to be grouped by a value in table "household"
	 *  then the table member needs to be added so that the join can be created.
	 *  
	 */
	public void addIntermediateTables(Connection connection) throws SQLException {
		
		// 1) get all the tables
		String sql = "select table_name, f_id, parentform from form where s_id = ?";
		PreparedStatement pstmt = connection.prepareStatement(sql);
		pstmt.setInt(1, sId);
		ResultSet resultSet = pstmt.executeQuery();
		ArrayList <TableInfo> allTables = new ArrayList<TableInfo> ();
		while(resultSet.next()) {
			System.out.println("Adding table: " + resultSet.getString(1));
			allTables.add(new TableInfo(
					resultSet.getString(1),
					resultSet.getInt(2),
					resultSet.getInt(3)
					));
		}
		
		// Debug
		for(int i = 0; i < allTables.size(); i++) {
			System.out.println("  All tables: " + allTables.get(i).getTName());
		}
		pstmt.close();
		
		// 2) For each table in the query add any tables between it and another table in the query
		Collection<TableInfo> t = null;
		Iterator<TableInfo> itr = null;

		t = iTables.values(); 
		itr = t.iterator();
		TableInfo pTable = null;
		ArrayList <TableInfo> potentialNewTables = new ArrayList<TableInfo> ();
		HashMap <String, TableInfo> acceptedNewTables = new HashMap<String, TableInfo> ();
		while (itr.hasNext()) {
			TableInfo ti = itr.next();	// starting point
			System.out.println("Starting point:" + ti.getTName());
			boolean hasAncestor = false;
			while((pTable = getParent(ti, allTables)) != null) {
				if(iTables.get(pTable.getTName()) != null) {
					hasAncestor = true;
					break;		// Found intermediate tables from the starting point survey
				} else {
					potentialNewTables.add(pTable);
				}
				
				ti = pTable;
			}
			if(hasAncestor) {
				// Add the potential new tables
				for(int i = 0; i < potentialNewTables.size(); i++) {
					TableInfo nt = potentialNewTables.get(i);
					acceptedNewTables.put(nt.getTName(), nt);
				}
			}			
		}
		
		// Add the new tables to the hashmap of tables
		if(acceptedNewTables.size() > 0) {
			t = acceptedNewTables.values(); 
			itr = t.iterator();
			while (itr.hasNext()) {
				TableInfo nt = itr.next();
				System.out.println("Adding accepted new table: " + nt.getTName());
				getParent(nt, allTables);	// Set the parent name
				iTables.put(nt.getTName(), nt);
			}
		}
		
		// Debug
		t = iTables.values(); 
		itr = t.iterator();
		while (itr.hasNext()) {
			TableInfo nt = itr.next();
			System.out.println("Final set of tables: " + nt.getTName());
		}
	}
	
	/*
	 * Gets the parent table from the list of all tables
	 */
	private TableInfo getParent(TableInfo ti, ArrayList<TableInfo> at) {
		
		int parentId = ti.getPId();
		TableInfo parentTable = null;
		
		if(parentId != -1) {
			for(int i = 0; i < at.size(); i++) {
				TableInfo tab = at.get(i);
				if(tab.getFId() == parentId) {
					parentTable = tab;
					ti.parentName = tab.getTName();
					break;
				}
			}
		} 
		
		return parentTable;
	}
	
	/*
	 * Returns the SQL fragment that makes up the list of tables in the query
	 */
	public String getTablesSQL() {
		String sqlFrag = "";
		
		Collection<TableInfo> t = null;
		Iterator<TableInfo> itr = null;

		t = iTables.values(); 
		itr = t.iterator();
		int i = 0;
		while (itr.hasNext()) {
			if(i > 0) {
				sqlFrag += ",";
			}
			TableInfo ti = itr.next();
			sqlFrag += ti.getTName();
			i++;
		}
		
		return sqlFrag;
	}
	
	public String getTableJoinSQL() {
		String sqlFrag = "";
		
		Collection<TableInfo> t = null;
		Iterator<TableInfo> itr = null;

		t = iTables.values(); 
		itr = t.iterator();
		int i = 0;
		while (itr.hasNext()) {
			TableInfo ti = itr.next();
			if(ti.getFId() != -1) {		// Ignore external tables, the join sql is generated elsewhere for these
				if(ti.getPId() != -1) {
					if(iTables.get(ti.getParentName()) != null) {	// Make sure the parent is a part of the query
						if(i > 0) {
							sqlFrag += " and ";
						}
						sqlFrag += ti.getTName() + ".parkey = " + ti.getParentName() + ".prikey";
						i++;
					}
				}
			}
		}
		
		return sqlFrag;
	}
	
	/*
	 * Returns the SQL fragment that restricts results to records that are not marked bad
	 */
	public String getNoBadClause() {
		String sqlFrag = "";
		
		Collection<TableInfo> t = null;
		Iterator<TableInfo> itr = null;

		t = iTables.values(); 
		itr = t.iterator();
		int i = 0;
		while (itr.hasNext()) {	
			TableInfo ti = itr.next();
			if(ti.getFId() != -1) {
				if(i > 0) {
					sqlFrag += " and "; 
				}
				sqlFrag += ti.getTName() + "._bad = 'f'";
				i++;
			}
		}
		
		return sqlFrag;
	}
	
}
