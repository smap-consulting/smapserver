package model;

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

// Deprecated - remove speed
public class ExchangeHeader {
	public ArrayList<ExchangeColumn> columns = new ArrayList<ExchangeColumn> ();
	public int lonIndex = -1;			// Column containing longitude
	public int latIndex = -1;			// Column containing latitude
	public int instanceIdColumn = -1;
	public boolean hasGeopoint = false;
	
	public PreparedStatement pstmtInsert = null;
}
