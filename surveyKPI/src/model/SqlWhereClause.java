package model;

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
 * Start Simple!
 * Initially filter is limited to selecting rows where the passed in option was selected
 */
public class SqlWhereClause {
	public String qType;
	public String lang;
	public int qId;
	public int oId;
	public String oValue;
	public String qText;
	public int qInteger;
}
