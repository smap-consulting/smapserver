

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

import javax.servlet.http.HttpServletRequest;

import model.DataEndPoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/*
 * Returns data for the passed in table name
 */
@Path("/v1/data")
public class Data extends Application {
	
	//Authorise a = new Authorise(null, Authorise.ANALYST);
	
	//private static Logger log =
	//		 Logger.getLogger(Data.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data.class);
		return s;
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 */
	@GET
	@Produces("application/json")
	public Response getData(@Context HttpServletRequest request) { 
		
		Response response = null;
		ArrayList<DataEndPoint> data = new ArrayList<DataEndPoint> ();
		
		DataEndPoint x = new DataEndPoint();
		x.description =" Yay";
		
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		String resp = gson.toJson(data);
		response = Response.ok(resp).build();
		
		return response;
	}
	



}

