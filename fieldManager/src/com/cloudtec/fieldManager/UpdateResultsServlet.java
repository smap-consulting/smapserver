package com.cloudtec.fieldManager;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class UpdateResults
 */
public class UpdateResultsServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * Default constructor. 
     */
    public UpdateResultsServlet() {
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		Map<String, String[]> params = request.getParameterMap();
		Set <String> keys = params.keySet();
		
		String target = null;
		String survey = null;
		String redirect = null;
		String subscriber = null;
		
		for(String key : keys) {
			System.out.println(key + ":" + params.get(key)[0]);
			if(key.equals("target")) {
				target = params.get(key)[0];
				if(target.equals("map")) {
					redirect = "/monitorMap.jsp";
				} else if(target.equals("table")) {
					redirect = "/monitorTable.jsp";
				}
		    } else if(key.equals("survey")) {
		    	survey = params.get(key)[0];
		    } else if(key.equals("subscriber")) {
		    	subscriber = params.get(key)[0];
		    }
		}
		if(redirect == null) {
			redirect = "/monitorTable.jsp";
		}
		if(survey == null) {
			survey = "_all";
		}

		String contextPath = request.getContextPath();
		response.sendRedirect(response.encodeRedirectURL(contextPath + redirect + "?survey=" + survey + "&subscriber=" + subscriber));
	}


}
