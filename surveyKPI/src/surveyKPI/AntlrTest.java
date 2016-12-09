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

import org.smap.sdal.Utilities.Authorise;
import lqas.LqasAppEntry;

import java.util.ArrayList;
import java.util.logging.Logger;

@Path("/antlr")
public class AntlrTest extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(AntlrTest.class.getName());
	
	public AntlrTest() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
	}

	
	// Respond with JSON 
	@GET
	@Produces("application/json")
	@Path("/a")
	public void getEvents(@Context HttpServletRequest request) {

		LqasAppEntry a = new LqasAppEntry();
		a.getQuery("a = 5");

	}


}

