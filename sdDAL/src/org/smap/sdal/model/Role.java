package org.smap.sdal.model;

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

public class Role {
	public int id;
	public int oId;
	public String name;
	public String desc;
	public boolean enabled;
	public int linkid;
	public ArrayList<RoleColumnFilter> column_filter;	// Columns that are allowed to be seen by people with this role
	public String row_filter;							// String row filter sent to client
	public String changed_by;
	public String changed_ts;
}
