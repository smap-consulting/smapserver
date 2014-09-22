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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

/*
 * Get a report
 * No authorisation is required however a valid ident must be supplied
 */
@Path("/login")
public class Login extends Application {
	
	private static Logger log =
			 Logger.getLogger(Login.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Login.class);
		return s;
	}


	@GET
	@Produces("text/html")
	public Response getLoginForm() {
		StringBuffer respBuf = new StringBuffer();
		ResponseBuilder builder = Response.ok();

		respBuf.append("<html><head>");
		respBuf.append("<title>Login</title>");
		/*
</head>
<body>
<div id="box">
<form method="POST" action="j_security_check">
<table>
    <tr>
      <td align="right"><p id="text">Username:</p></td>
      <td align="left">
		<input type="text" name="j_username">
	</td>
    </tr>
    <tr>
      <td align="right"><p id="text">Password:</p></td>
      <td align="left"><input type="password" name="j_password"></td>
    </tr>
    <tr>
      <td align="right"><input type="submit" value="Log In"></td>
      <td align="left"><input type="reset" value="Reset"></></td>
    </tr>
</table>
</form>
</div> 
</body>
</html>");
*/
		respBuf.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
		respBuf.append("<html>");

		
		respBuf.append("<head>");
		respBuf.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
		respBuf.append("<link type=\"text/css\" media=\"all\" rel=\"stylesheet\" href=\"/fieldAnalysis/css/smap.css\">");
		
		respBuf.append("<title>");
		respBuf.append("Logon");
		respBuf.append("</title>");
		
		respBuf.append("<style type=\"text/css\">");
		respBuf.append("h1 {text-align:center; font-size: 2em; } ");
		respBuf.append("h2 {font-size: 1.6em; } ");
		respBuf.append("h3 {font-size: 1.2em; } ");
		respBuf.append("table,th,td {border:1px solid black;} ");
		respBuf.append("table {border-collapse:collapse;} ");
		respBuf.append("td {padding: 5px;} ");
		respBuf.append("</style>");
		respBuf.append("</head>");
		
		respBuf.append("<body>");	
		

		respBuf.append("<h2>Location</h2>");
		

		
		respBuf.append("</body>");
		respBuf.append("</html>");
		
		builder.header("Content-type","text/html");	
		builder = builder.entity(respBuf.toString());	
	
		return builder.build();

	}
	

	

	/*
	 * Accept logon credentials
	 */
	@POST
	@Consumes("application/json")
	public Response login(@Context HttpServletRequest request, 
			@FormParam("report") String reportString) { 
		
		Response response = null;
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		
		String serverName = request.getServerName();
		System.out.println("Server: " + serverName);
		System.out.println("report string:" + reportString);


		
		return response;
	}
	

	



}

