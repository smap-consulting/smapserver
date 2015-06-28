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
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Get the values of the passed in text question and the history of changes
 * Only return non null values or null values which have a history of changes
 */

@Path("/review/{sId}")
public class Review extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Review.class);
		return s;
	}
	
	private class InvalidUndoException extends Exception {
		public InvalidUndoException(String msg) {
			super(msg);
		}
	}
	
	/*
	 * One ReviewUpdate object per question for select_one and text questions
	 * One ReviewUpdate object per question / option pair for select_multiple questions
	 */
	private class ReviewItem {
		int	q_id;
		String newValue;	// The new value to be written
		boolean set;		// Set or unset a select question
		
		// The following attributes are populated by this service for local use
		String table;
		String qname;
		String qtype;
	}

	// A review update applies to a single table, multiple questions, optionally multiple records
	private class ReviewUpdate {
		int qFilter;		// Filter question id
		String valueFilter;	// If r_id is 0 then use this to identify values that records that need to be changed
		int r_id;			// If greater than 0 then restrict change to this record
		
		String reason;
		String description;
		ArrayList<ReviewItem> reviewItems;
	}
	
	private class UpdateItem {
		int	sId;
		int	qId;
		String qname;
		String qtype;
		int rId;
		String table;
		String newValue;
		String oldValue;
		boolean set;
	}

	private class Result {
		String text;
		String count;
		String targetQuestion;
	}
	private ArrayList<Result> results = new ArrayList<Result> ();

	private class AuditItem {
		int id;
		int sId;
		String change_name;
		String change_reason;
		String description;
		boolean reversed;
		String reverse_name;
	}
	
	/*
	 * Get the distinct text results for a question
	 */
	@GET
	@Path("/results/distinct/{qId}")
	@Produces("application/json")
	public Response getDistinctTextResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Survey Id
			@PathParam("qId") int qId,				// Question Id 
			@QueryParam("targetQuestion") int targetQId				// Target Question Id 
			) { 
	
		Response response = null;
		String table = null;
		String name = null;
		String qtype = null;
		String targetName = null;
		String targetType = null;
		boolean hasTarget = false;
		PreparedStatement  pstmt = null;
		PreparedStatement  pstmtTarget = null;

		Connection dConnection = null;
			
		log.info("Other question id: " + targetQId);
		
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Review");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
					
		try {
			dConnection = ResultsDataSource.getConnection("surveyKPI-Review");

			/*
			 * Get the table name and column name containing the text data
			 */
			String sql = "select f.table_name, q.qname, qtype from form f, question q " +
					" where f.f_id = q.f_id" +
					" and q.q_id = ?";
	
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setInt(1, qId);
			log.info("Get question: " + pstmt.toString());
			ResultSet resultSet = pstmt.executeQuery();

			if(resultSet.next()) {
				table = resultSet.getString(1);
				name = UtilityMethodsEmail.cleanName(resultSet.getString(2));
				qtype = resultSet.getString(3);
				
				// If data for antarget question is also required then ensure it is in the same table and get the question name
				if(targetQId > 0) {
					String sqlTarget = "select q.qname, qtype from form f, question q " +
							" where f.f_id = q.f_id" +
							" and f.table_name = ?" +
							" and q.q_id = ?";
					
					pstmtTarget = connectionSD.prepareStatement(sqlTarget);	
					pstmtTarget.setString(1, table);
					pstmtTarget.setInt(2, targetQId);
					log.info("Get question: " + pstmtTarget.toString());
					ResultSet rsTarget = pstmtTarget.executeQuery();
					if(rsTarget.next()) {
						targetName = rsTarget.getString(1);
						targetType = rsTarget.getString(2);
						hasTarget = true;
					}
					log.info("Target name: " + targetName);
					
				}
				
				
				if(!qtype.equals("string") && !qtype.equals("select1")) {
					throw new ApplicationException("Unsupported question type: " + qtype);
				}
				
				if (pstmt != null) {
					pstmt.close();
				}
				
				/*
				 * Get the data
				 */
				String targetN = "";
				if(hasTarget) {
					targetN = "," + targetName;
				}
				sql = "select distinct " + name + targetN + ",count(*) from " + table +		
						" where _bad = 'false' " +
						" and " + name + " is not null " +
						" group by " + name + targetN +
						" order by " + name + targetN +";";
				pstmt = dConnection.prepareStatement(sql);
				log.info("Getting data for review: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
				
				while(resultSet.next()) {
					
					Result r = new Result();
					r.text = resultSet.getString(1);
					if(hasTarget) {
						r.targetQuestion = resultSet.getString(2);
						r.count = resultSet.getString(3);
					} else {
						r.count = resultSet.getString(2);
					}
					
					results.add(r);
				}
			} else {
				throw new ApplicationException("Question not found");
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(results);
			response = Response.ok(resp).build();

		} catch (ApplicationException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    String msg = e.getMessage();
		    log.info(msg);
			response = Response.status(Status.BAD_REQUEST).entity(msg).build();

		} catch (SQLException e) {
			
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				log.info("No data: " + msg);
			} else {
				log.log(Level.SEVERE,"Exception", e);
			}	
			response = Response.status(Status.NOT_FOUND).entity(msg).build();

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtTarget != null) {pstmtTarget.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (dConnection != null) {
					dConnection.close();
					dConnection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}

		return response;
	}

	//=======================================================================================
	private class PathAndValue {
		String path;
		String value;
	}
	
	private class RelevanceOption {
		int oId;
		String name;
		String label;
	}
	
	private class RelevanceQuestion {
		int qId;
		String type;
		String label;
		String name;
		String oname;
		ArrayList<RelevanceOption> options = new ArrayList<RelevanceOption> ();
	}
	
	ArrayList <RelevanceQuestion> questions = new ArrayList<RelevanceQuestion> ();
	
	/*
	 * Check if the passed in question is has a relevance clause
	 * Assume (for the moment) that the question this relevance clause refers to is a select question (and there is only one)
	 * Return the list of options from this select question
	 */
	@GET
	@Path("/relevance/{language}/{qId}")
	@Produces("application/json")
	public Response getReferencedQuestion(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("language") String language,
			@PathParam("qId") int qId) { 

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
		    log.log(Level.SEVERE, "SQL Exception", e);
		    return Response.serverError().entity(e.getMessage()).build();

		}
		
		log.info("===========getReferencedQuestion: " + sId + " : " + language + " : " + qId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-QuestionList");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		Response response = null;

		ArrayList<RelevanceQuestion> relResults = new ArrayList<RelevanceQuestion> ();

		PreparedStatement pstmtGetRelevance = null;
		PreparedStatement pstmtGetQuestion = null;
		PreparedStatement pstmtGetOption = null;
	 
		try {
			ResultSet resultSet = null;
			
			/*
			 * Prepared Statements
			 */
			String sqlGetRelevance = "select relevant " +
					" from question " +  
					" where q_id = ? " +
					" and relevant is not null;";
			pstmtGetRelevance = connectionSD.prepareStatement(sqlGetRelevance);	 
			
			String sqlGetQuestion = "SELECT q.q_id, q.qtype, t.value, q.qname " +
					" FROM form f, question q, translation t " +  
					" WHERE f.f_id = q.f_id " +
					" AND f.s_id = t.s_id " +
					" AND q.qtext_id = t.text_id " +
					" AND t.language = ? " +
					" AND t.type = 'none' " +		// TODO support multiple types as questions
					" AND f.s_id = ? " +
					" AND q.path = ? " +
			        " ORDER BY f.table_name, q.seq;";
			pstmtGetQuestion = connectionSD.prepareStatement(sqlGetQuestion);
			
			String sqlGetOption = "SELECT o.o_id, o.ovalue, t.value " +
					" FROM form f, question q, option o, translation t " +  
					" WHERE o.label_id = t.text_id " +
					" AND t.language = ? " +
					" AND f.s_id = t.s_id " +
					" AND q.f_id = f.f_id " +
					" AND q.q_id = o.q_id " +
					" AND t.type = 'none' " +		
					" AND o.q_id = ?" +	
					" AND f.s_id = ? " +
			        " ORDER BY o.seq;";
			pstmtGetOption = connectionSD.prepareStatement(sqlGetOption);
				
			/*
			 * Get the relevance statement for the passed in question
			 */
			log.info("getReferenceQuestion" + sqlGetRelevance + " : " + qId);
			pstmtGetRelevance.setInt(1,  qId);
			resultSet = pstmtGetRelevance.executeQuery();
			
			if(resultSet.next()) {
				String relevance = resultSet.getString(1);
				
				/*
				 * Get the paths of any select questions referenced in this relevance statement
				 */
				ArrayList<PathAndValue> paths = new ArrayList<PathAndValue> ();
				addRelevantQuestionPaths(paths, relevance);
				
				for(int i = 0; i < paths.size(); i++) {
					
					PathAndValue pAndV = paths.get(i);
					String path = pAndV.path;
					
					/*
					 * Get the question details
					 */
					pstmtGetQuestion.setString(1,  language);
					pstmtGetQuestion.setInt(2,  sId);
					pstmtGetQuestion.setString(3,  path);
					
					resultSet = pstmtGetQuestion.executeQuery();
					if (resultSet.next()) {
						RelevanceQuestion q = new RelevanceQuestion();
						q.qId = resultSet.getInt(1);
						q.type = resultSet.getString(2);
						q.label = resultSet.getString(3);
						q.name = resultSet.getString(4);
						q.oname = pAndV.value;
						
						/*
						 * Get the options
						 */
						if(q.type.startsWith("select")) {
							pstmtGetOption.setString(1, language);
							pstmtGetOption.setInt(2, q.qId);
							pstmtGetOption.setInt(3, sId);

							ResultSet resultSetOptions = pstmtGetOption.executeQuery();
							while(resultSetOptions.next()) {
								RelevanceOption o = new RelevanceOption();
								o.oId = resultSetOptions.getInt(1);
								o.name = resultSetOptions.getString(2);
								o.label = resultSetOptions.getString(3);
								q.options.add(o);
							}		
						}
						
						relResults.add(q);
					}	
				}
				
			}
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(relResults);
			response = Response.ok(resp).build();
				
		} catch (SQLException e) {
		    log.log(Level.SEVERE, "SQL Exception", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmtGetRelevance != null) {pstmtGetRelevance.close();}} catch (SQLException e) {}
			try {if (pstmtGetQuestion != null) {pstmtGetQuestion.close();}} catch (SQLException e) {}
			try {if (pstmtGetOption != null) {pstmtGetOption.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Exception", e);
			}
		}

		return response;
	}

	//================================================================================================
	
	/*
	 * Get all the changes that have been applied to a survey
	 */
	@GET
	@Path("/audit")
	@Produces("application/json")
	public Response getChangesets(@Context HttpServletRequest request,
			@PathParam("sId") int sId				// Survey Id
			) { 
	
		Response response = null;
		PreparedStatement  pstmt = null;

				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Review");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
				
		Connection dConnection = ResultsDataSource.getConnection("surveyKPI-Audit");
		
		try {

			/*
			 * Get changeset history
			 */
			String sql = "select id, s_id, user_name, change_reason, description, reversed, reversed_by_user, changed_ts " +
					" from changeset " +
					" where s_id = ? " +
					" order by id desc";
	

			pstmt = dConnection.prepareStatement(sql);
			pstmt.setInt(1, sId);
			ResultSet resultSet = pstmt.executeQuery();
			
			ArrayList<AuditItem> auditItems = new ArrayList<AuditItem> ();

			while(resultSet.next()) {
				AuditItem ai = new AuditItem();
				ai.id = resultSet.getInt(1);
				ai.sId = resultSet.getInt(2);
				ai.change_name = resultSet.getString(3);
				ai.change_reason = resultSet.getString(4);
				ai.description = resultSet.getString(5);
				ai.reversed = resultSet.getBoolean(6);
				ai.reverse_name = resultSet.getString(7);
				
				auditItems.add(ai);
				
			} 
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(auditItems);
			response = Response.ok(resp).build();

		} catch (SQLException e) {
			log.log(Level.SEVERE,"Exception", e);
		    String msg = e.getMessage();		
			response = Response.status(Status.NOT_FOUND).entity(msg).build();

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (dConnection != null) {
					dConnection.close();
					dConnection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}


		return response;

	}
	
	//===============================================================================
	
	/*
	 * Get the update items for an indiidual changeset
	 */
	@GET
	@Path("/audit/details/{csId}")
	@Produces("application/json")
	public Response getChangesetDetails(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Survey Id
			@PathParam("csId") int csId				// Changeset Id
			) { 
	
		Response response = null;
		PreparedStatement  pstmt = null;

				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Review");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
				
		Connection dConnection = ResultsDataSource.getConnection("surveyKPI-Audit");
		
		try {

			/*
			 * Get changeset history
			 */
			String sql = "select ch.q_id, ch.r_id, ch.old_value, ch.new_value, ch.qname, ch.qtype, ch.oname " +
			" from change_history ch, changeset cs " +
			" where ch.c_id = ? " +
			" and cs.s_id = ? " + 		// Authorisation check
			" and cs.id = ch.c_id " +
			" order by ch.id asc;";
	

			pstmt = dConnection.prepareStatement(sql);	
			pstmt.setInt(1,csId);
			pstmt.setInt(2,sId);
			ResultSet resultSet = pstmt.executeQuery();
			
			ArrayList<UpdateItem> updateItems = new ArrayList<UpdateItem> ();

			while(resultSet.next()) {
				UpdateItem ui = new UpdateItem();
				String oldValue;
				String newValue;
				String oname;
				
				ui.qId = resultSet.getInt(1);
				ui.rId = resultSet.getInt(2);
				oldValue = resultSet.getString(3);
				newValue = resultSet.getString(4);
				ui.qname = resultSet.getString(5);
				ui.qtype = resultSet.getString(6);
				oname = resultSet.getString(7);
				
				if(ui.qtype.equals("select")) {
					ui.set = false;
					if(newValue.equals("1")) {
						ui.set = true;
					}
					ui.oldValue = oname;
					ui.newValue = oname;
				} else {
					ui.oldValue = oldValue;
					ui.newValue = newValue;
				}
				updateItems.add(ui);
				
			} 
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(updateItems);
			response = Response.ok(resp).build();

		} catch (SQLException e) {
			log.log(Level.SEVERE,"Exception", e);
		    String msg = e.getMessage();		
			response = Response.status(Status.NOT_FOUND).entity(msg).build();

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (dConnection != null) {
					dConnection.close();
					dConnection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}


		return response;

	}
	
	//=====================================================================================
	
	@POST
	@Produces("application/json")
	public Response updateResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Project Id
			@FormParam("updates") String updates
			) { 
	
		Response response = null;
		String userName;
		int csId;
		
		PreparedStatement pstmtGetUserName = null;
		PreparedStatement pstmtInsertChangeset = null;
		PreparedStatement pstmtGetTable = null;
		PreparedStatement pstmtGetCurrentValue = null;;
		PreparedStatement pstmtData = null;
		PreparedStatement pstmtInsertChangeHistory = null;
		PreparedStatement pstmtGetRecords = null;

		/*
		 * Get the updates
		 */
		Type updateType = new TypeToken<ReviewUpdate>() {}.getType();
		ReviewUpdate u = new Gson().fromJson(updates, updateType);

		Connection dConnection = null;
				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Review");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		try {
			ArrayList<UpdateItem> uiList = new ArrayList<UpdateItem> ();
			
			/*
			 * Prepare statements
			 */
			dConnection = ResultsDataSource.getConnection("surveyKPI-Review");
			
			String sqlGetUserName = "select name from users where ident = ?;";
			pstmtGetUserName = connectionSD.prepareStatement(sqlGetUserName);
			
			String sqlInsertChangeset = "insert into changeset(user_name, s_id, change_reason, description) " +
					" values(?, ?, ?, ?);";
			pstmtInsertChangeset = dConnection.prepareStatement(sqlInsertChangeset, Statement.RETURN_GENERATED_KEYS);
			
			String sqlGetTable = "select f.table_name, q.qname, q.qtype from form f, question q " +
					" where f.f_id = q.f_id" +
					" and f.s_id = ? " +		// Verify that the question is in the specified survey (authorisation)
					" and q.q_id = ?";
			pstmtGetTable = connectionSD.prepareStatement(sqlGetTable);	
			
			String sqlInsertChangeHistory = "insert into change_history(c_id, q_id, r_id, old_value, " +
					"new_value, qname, qtype, tablename, oname) " +
					" values(?, ?, ?, ?, ?, ?, ?, ?, ?);";
			pstmtInsertChangeHistory = dConnection.prepareStatement(sqlInsertChangeHistory);
			
			// Get the user name
			pstmtGetUserName.setString(1, request.getRemoteUser());
			ResultSet resultSet = pstmtGetUserName.executeQuery();
			if(resultSet.next()) {
				userName = resultSet.getString(1);
			} else {
				throw new ApplicationException("Failed to get user name");
			}
						
			// Get table name, question name and question type for all update items
			for(int i = 0; i < u.reviewItems.size(); i++) {
				ReviewItem ri = u.reviewItems.get(i);
				pstmtGetTable.setInt(1, sId);
				pstmtGetTable.setInt(2, ri.q_id);
				resultSet = pstmtGetTable.executeQuery();

				if(resultSet.next()) {
					ri.table = resultSet.getString(1);
					ri.qname = UtilityMethodsEmail.cleanName(resultSet.getString(2));
					ri.qtype = resultSet.getString(3);
				} else {
					throw new ApplicationException("Table not found for question: " + ri.q_id);
				}	

			}
			
			// Create an array of update items for each update item in each record
			if(u.r_id > 0) {
				
				addUpdateItem(
						dConnection,
						pstmtGetCurrentValue,
						uiList, 
						sId,
						u.r_id, 
						u.reviewItems);
			
			} else if (u.qFilter > 0 ) {
				
				// Get the record ids using the question filter
				String filter_name = null;
				String table = null;
				
				// Get the filter question name and type
				pstmtGetTable.setInt(1, sId);
				pstmtGetTable.setInt(2, u.qFilter);
				resultSet = pstmtGetTable.executeQuery();

				if(resultSet.next()) {
					table = resultSet.getString(1);
					filter_name = UtilityMethodsEmail.cleanName(resultSet.getString(2));
				} else {
					throw new ApplicationException("Table not found for question: " + u.qFilter);
				}
				
				// Get the record ids that match the filter
				String sqlGetRecords = "select prikey from " +  table + " where " + filter_name + " = ?;";
				try {if (pstmtGetRecords != null) {pstmtGetRecords.close();}} catch (SQLException e) {}
				pstmtGetRecords = dConnection.prepareStatement(sqlGetRecords);
				pstmtGetRecords.setString(1, u.valueFilter);
				
				ResultSet resultSet3 = pstmtGetRecords.executeQuery();
				while(resultSet3.next()) {
					
					int prikey = resultSet3.getInt(1);
					addUpdateItem(
							dConnection,
							pstmtGetCurrentValue,
							uiList, 
							sId,
							prikey, 
							u.reviewItems);
				}
			} else {
				throw new ApplicationException("Can't get records to update");
			}
			
			// Create an entry for this changeset and get the changeset id
			
			pstmtInsertChangeset.setString(1, userName);
			pstmtInsertChangeset.setInt(2, sId);
			pstmtInsertChangeset.setString(3, u.reason);
			pstmtInsertChangeset.setString(4, u.description);
			
			log.info("Get changeset id: " + pstmtInsertChangeset.toString());
			pstmtInsertChangeset.executeUpdate();
			resultSet = pstmtInsertChangeset.getGeneratedKeys();
			if(resultSet.next()) {
				csId = resultSet.getInt(1);
			} else {
				throw new ApplicationException("Failed to create changeset entry");
			}
			
			applyUpdateItems (
					csId,
					uiList,
					dConnection,
					pstmtData,
					pstmtInsertChangeHistory
					);

			response = Response.ok().build();

		} catch (ApplicationException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    String msg = e.getMessage();
		    log.info(msg);
			response = Response.status(Status.BAD_REQUEST).entity(msg).build();

		} catch (SQLException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    log.log(Level.SEVERE,"Exception", e);
		    String msg = e.getMessage();
			
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
			

		} catch (Exception e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		
		} finally {
			
			try {connectionSD.setAutoCommit(true);} catch (Exception e) {}	
			try {dConnection.setAutoCommit(true);} catch (Exception e) {}
			try {if (pstmtGetTable != null) {pstmtGetTable.close();}} catch (SQLException e) {}
			try {if (pstmtData != null) {pstmtData.close();}} catch (SQLException e) {}
			try {if (pstmtGetCurrentValue != null) {pstmtGetCurrentValue.close();}} catch (SQLException e) {}
			try {if (pstmtInsertChangeHistory != null) {pstmtInsertChangeHistory.close();}} catch (SQLException e) {}
			try {if (pstmtGetUserName != null) {pstmtGetUserName.close();}} catch (SQLException e) {}
			try {if (pstmtInsertChangeset != null) {pstmtInsertChangeset.close();}} catch (SQLException e) {}
			try {if (pstmtGetRecords != null) {pstmtGetRecords.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (dConnection != null) {
					dConnection.close();
					dConnection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}

		return response;

	}
	
	private void addUpdateItem(
			Connection dConnection,
			PreparedStatement pstmtGetCurrentValue,
			ArrayList<UpdateItem> uiList, 
			int sId,
			int rId, 
			ArrayList<ReviewItem> riList) throws ApplicationException, SQLException, Exception {
		
		for(int i = 0; i < riList.size(); i++) {
			ReviewItem ri = riList.get(i);
			UpdateItem ui = new UpdateItem();
			ui.qId = ri.q_id;
			ui.sId = sId;
			ui.qname = ri.qname;
			ui.qtype = ri.qtype;
			ui.table = ri.table;
			ui.rId = rId;
			ui.newValue = ri.newValue;
			ui.set = ri.set;
			
			// Get the old value for this question
			String sqlGetCurrentValue = null;
			if(ri.qtype.equals("select")) {
				String cleanOname = UtilityMethodsEmail.cleanName(ri.newValue);
				sqlGetCurrentValue = "select " + ri.qname + "__" + cleanOname + " from " + ri.table;
			} else {
				sqlGetCurrentValue = "select " + ri.qname + " from " + ri.table;
			}
			sqlGetCurrentValue += " where prikey = ?" ;
			
			log.info("Get Current Value: " + sqlGetCurrentValue + " : " + rId);
			try {if (pstmtGetCurrentValue != null) {pstmtGetCurrentValue.close();}} catch (SQLException e) {}
			pstmtGetCurrentValue = dConnection.prepareStatement(sqlGetCurrentValue);
			pstmtGetCurrentValue.setInt(1, rId);
			
			// Get the current value 
			ResultSet resultSet = pstmtGetCurrentValue.executeQuery();
			if(resultSet.next()) {
				ui.oldValue = resultSet.getString(1);
			} else {
				throw new ApplicationException("Current value not found");
			}
			uiList.add(ui);
		}
	}
	

	
	private void applyUpdateItems (
			int csId,
			ArrayList<UpdateItem> updateItems,
			Connection dConnection,
			PreparedStatement pstmtData,
			PreparedStatement pstmtInsertChangeHistory
			) throws ApplicationException, SQLException, Exception {
		
		// Update to table and change history must be in a single transaction
		dConnection.setAutoCommit(false);
		
		/*
		 * Get the current value and primary key of each record that is to change
		 */
		for(int i = 0; i < updateItems.size(); i++) {
			String sqlData;
			
			UpdateItem ui = updateItems.get(i);
			
			/*
			 * Update data
			 */
			if(ui.qtype.equals("select")) {
				String cleanOname = UtilityMethodsEmail.cleanName(ui.newValue);
				sqlData = "update " + ui.table + " set " + ui.qname + "__" + cleanOname + " = ? where prikey = ?;";
			} else {
				sqlData = "update " + ui.table + " set " + ui.qname + " = ? where prikey = ?;";	
			}

			try {if (pstmtData != null) {pstmtData.close();}} catch (SQLException e) {}
			pstmtData = dConnection.prepareStatement(sqlData);
			int ovalue = 0;
			if(ui.qtype.equals("select")) {
				if(ui.set) {
					ovalue = 1;
				}
				pstmtData.setInt(1, ovalue);
			} else {
				pstmtData.setString(1, ui.newValue);
			}
			pstmtData.setInt(2, ui.rId);
			
			log.info("Modifying data record: " + sqlData + " : " + ui.newValue + " : " + ui.rId);
			pstmtData.executeUpdate();
				
			/*
			 * insert a record in the change history
			 * Only required if this is a new change, reversals are recorded only in the changeset
			 */
			if(pstmtInsertChangeHistory != null) {
				pstmtInsertChangeHistory.setInt(1, csId);
				pstmtInsertChangeHistory.setInt(2, ui.qId);
				pstmtInsertChangeHistory.setInt(3, ui.rId);
				pstmtInsertChangeHistory.setString(4, ui.oldValue);
				if(ui.qtype.equals("select")) {				
					pstmtInsertChangeHistory.setString(5, String.valueOf(ovalue));
				} else {
					pstmtInsertChangeHistory.setString(5, ui.newValue);
				}
				pstmtInsertChangeHistory.setString(6, ui.qname);
				pstmtInsertChangeHistory.setString(7, ui.qtype);
				pstmtInsertChangeHistory.setString(8, ui.table);
				if(ui.qtype.equals("select")) {				
					pstmtInsertChangeHistory.setString(9, ui.newValue);
				} else {
					pstmtInsertChangeHistory.setString(9, "");
				}
				
				log.info("Insert change history: " + pstmtInsertChangeHistory.toString());
				pstmtInsertChangeHistory.executeUpdate();
				
				dConnection.commit();
			} 
		}
		try {dConnection.setAutoCommit(true);} catch (Exception e) {}
	}
	

	
	@POST
	@Path("/undo/{csId}")
	@Produces("application/json")
	public Response undoChange(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Project Id
			@PathParam("csId") int csId				// Changeset Id
			) {

		Response response = null;
		String userName;
		ArrayList<ReviewItem> riList = new ArrayList<ReviewItem> ();
		
		PreparedStatement pstmtGetUserName = null;
		PreparedStatement pstmtUpdateChangeset = null;
		PreparedStatement pstmtGetTable = null;
		PreparedStatement pstmtGetCurrentValue = null;;
		PreparedStatement pstmtData = null;
		PreparedStatement pstmtGetChangeHistory = null;
		PreparedStatement pstmtGetLaterChangeset = null;

		Connection dConnection = null;
				
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Results");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		

		
		try {
			dConnection = ResultsDataSource.getConnection("surveyKPI-ReviewResultsText");
			ArrayList<UpdateItem> uiList = new ArrayList<UpdateItem> ();
			
			/*
			 * Prepare statements
			 */
			dConnection = ResultsDataSource.getConnection("surveyKPI-Review");
			
			String sqlGetUserName = "select name from users where ident = ?;";
			pstmtGetUserName = connectionSD.prepareStatement(sqlGetUserName);
			
			String sqlUpdateChangeset = "update changeset set reversed_by_user = ?, reversed = 'true' " +
					"where id = ? and s_id = ? and reversed = 'false'";
			pstmtUpdateChangeset = dConnection.prepareStatement(sqlUpdateChangeset);
			
			String sqlGetTable = "select f.table_name, q.qname, q.qtype from form f, question q " +
					" where f.f_id = q.f_id" +
					" and q.q_id = ?";
			pstmtGetTable = connectionSD.prepareStatement(sqlGetTable);	
			
			String sqlGetChangeHistory = "select q_id, r_id, old_value, new_value, qname, qtype, tablename, oname " +
					" from change_history " +
					" where c_id = ?;";
			pstmtGetChangeHistory = dConnection.prepareStatement(sqlGetChangeHistory);
			
			String sqlGetLaterChangeset = "select cs.id " +
					" from changeset cs, change_history ch " +
					" where cs.id = ch.c_id " +
					" and cs.id > ? " +
					" and cs.reversed = 'false'" +
					" and ch.q_id = ? " +
					" and ch.r_id = ?;";
			pstmtGetLaterChangeset = dConnection.prepareStatement(sqlGetLaterChangeset);
			
			// Get the user id
			pstmtGetUserName.setString(1, request.getRemoteUser());
			ResultSet resultSet = pstmtGetUserName.executeQuery();
			if(resultSet.next()) {
				userName = resultSet.getString(1);
			} else {
				throw new ApplicationException("Failed to get user name");
			}
			
			// Update the changeset
			pstmtUpdateChangeset.setString(1, userName);
			pstmtUpdateChangeset.setInt(2, csId);
			pstmtUpdateChangeset.setInt(3, sId);
			int count = pstmtUpdateChangeset.executeUpdate();
			if(count != 1) {
				throw new ApplicationException("Failed to update existing changset status. Count:" + count);
			}
			
			/*
			 * Get list of update items from the change history
			 */
			pstmtGetChangeHistory.setInt(1, csId);
			resultSet = pstmtGetChangeHistory.executeQuery();
			while (resultSet.next()) {
				UpdateItem ui = new UpdateItem();
								
				String newValue;
				String oname;
				
				ui.qId = resultSet.getInt(1);
				ui.rId = resultSet.getInt(2);
				newValue = resultSet.getString(3);	// The new value is the old value in the change history
				// Skip the new value in change history
				ui.qname = resultSet.getString(5);
				ui.qtype = resultSet.getString(6);
				ui.table = resultSet.getString(7);
				oname = resultSet.getString(8);
				
				if(ui.qtype.equals("select")) {
					ui.newValue = oname;
					if(newValue.equals("1")) {
						ui.set = true;
					} else {
						ui.set = false;
					}
				} else {
					ui.newValue = newValue;
				}
				
				/*
				 * Check that there are no later changes that have modified this update item
				 */
				pstmtGetLaterChangeset.setInt(1, csId);
				pstmtGetLaterChangeset.setInt(2, ui.qId);
				pstmtGetLaterChangeset.setInt(3, ui.rId);
				ResultSet rsValid = pstmtGetLaterChangeset.executeQuery();
				log.info("Reverse valid check: " + sqlGetLaterChangeset + " : " + csId + " : " + ui.qId + " : " + ui.rId);
				if(rsValid.next()) {
					int laterChangeset = rsValid.getInt(1);
					throw new InvalidUndoException("A later changeset modifies question " + ui.qId + ", record " + 
							ui.rId + ":" + laterChangeset);
				}
				
				uiList.add(ui);
			
			}
			
			applyUpdateItems (
					csId,
					uiList,
					dConnection,
					pstmtData,
					null
					);

			response = Response.ok().build();

		} catch (InvalidUndoException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    String msg = e.getMessage();
		    log.info(msg);
			response = Response.status(Status.BAD_REQUEST).entity(msg).build();

		} catch (ApplicationException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    String msg = e.getMessage();
		    log.info(msg);
			response = Response.status(Status.BAD_REQUEST).entity(msg).build();

		} catch (SQLException e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
		    log.log(Level.SEVERE,"Exception", e);
		    String msg = e.getMessage();
			
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
			

		} catch (Exception e) {
			
			try { dConnection.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}
			
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		
		} finally {
			
			try {connectionSD.setAutoCommit(true);} catch (Exception e) {}	
			try {dConnection.setAutoCommit(true);} catch (Exception e) {}
			try {if (pstmtGetTable != null) {pstmtGetTable.close();}} catch (SQLException e) {}
			try {if (pstmtData != null) {pstmtData.close();}} catch (SQLException e) {}
			try {if (pstmtGetCurrentValue != null) {pstmtGetCurrentValue.close();}} catch (SQLException e) {}
			try {if (pstmtGetChangeHistory != null) {pstmtGetChangeHistory.close();}} catch (SQLException e) {}
			try {if (pstmtGetUserName != null) {pstmtGetUserName.close();}} catch (SQLException e) {}
			try {if (pstmtGetLaterChangeset != null) {pstmtGetLaterChangeset.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (dConnection != null) {
					dConnection.close();
					dConnection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}

		return response;
	}
	
	/*
	 * Get the paths of any select questions that are referred to in the passed in relevance clause
	 */
	private void addRelevantQuestionPaths(ArrayList<PathAndValue> paths, String relevance) {
		int idx1 = -1;
		int idx2 = -1;
		
		PathAndValue pv = new PathAndValue();
		
		while((idx1 = relevance.indexOf("selected", idx1 + 1)) >= 0) {
			idx1 = relevance.indexOf("(", idx1 + 1);
			if(idx1 > 0) {
				idx2 = relevance.indexOf(",", idx1);
				if(idx2 > 0) {
					pv.path = relevance.substring(idx1+1, idx2).trim();
					idx1 = relevance.indexOf("'", idx2);
					if(idx1 > 0) {
						idx2 = relevance.indexOf("'", idx1 + 1);
						if(idx2 > 0) {
							pv.value = relevance.substring(idx1+1, idx2).trim();
						}
					}
				}

			}
			
		
			paths.add(pv);
		}
	}
}

