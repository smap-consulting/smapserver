package surveyKPI;

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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.managers.FiretailManager;

import java.util.ArrayList;
import java.util.logging.Logger;

/*
 * The FireTail module manages the interface between a Smap logged in user and the FireTail 
 *  messaging module
 */
@Path("/firetail")
public class Firetail extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Firetail.class.getName());
	
	public Firetail() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		
		a = new Authorise(authorisations, null);	
	}

	/*
	 * Get a list of messages
	 */
	@GET
	@Produces("application/json")
	public Response getMessages(@Context HttpServletRequest request) {

		Response response = null;
		
		FiretailManager ftm = new FiretailManager();
		ftm.getEvents();
		
		return response;
	}


}

