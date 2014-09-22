package model;

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
public class ReportItem {
	public String caption;
	public String imageURL;	// Link to an image of the item
	public String thumbURL;	// Optional link to a thumbnail for the above image
	public String dataGen;	// Base 64 encoded image created by client
	public String type;		// Item type: image, table, data_url 
	public String action;	// delete || new || existing || abandon
	public int itemId;		// Primary key of the item if it is existing
}
