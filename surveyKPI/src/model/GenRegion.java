package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

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

public class GenRegion {
	public int lower_left_x;	// metres
	public int lower_left_y;
	public int upper_right_x;
	public int upper_right_y;
	public int centre_x;
	public int centre_y;
	public int width;
	public String name;
}
