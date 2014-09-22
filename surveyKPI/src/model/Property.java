package model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;

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

/*
 * Data object for Neo4J / thingsat export
 */

public class Property {
	public String key;					// The key that identifies the property
	public String value_type;			// record || constant
	public String value;				// Value if the value_type is constant
	public String colName;				// Name of the column containing the data if the value type is record
	public boolean unique;	
	
	public void debug() {
		System.out.println("  Property");
		System.out.println("     key: " + key);
		System.out.println("     value type: " + value_type);
		System.out.println("     value: " + value);
		System.out.println("     colName: " + colName);
		System.out.println("     unique: " + unique);
	}
}
