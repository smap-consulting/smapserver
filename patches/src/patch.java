import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class patch {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//for(int i = 0; i < args.length; i++) {
		//	System.out.println("Arg:" + args[i]);
		//}
		
		if(args.length < 3) {
			System.out.println("Usage: java -jar patch.jar test_run sd_db results_db");
			return;
		}
		
		String basePath;
		
		
		Connection connectionSD = null;
		Connection connectionResults = null;
		String response;
		boolean testRun = false;
		String dbClass = "org.postgresql.Driver";
		if(!args[0].equals("apply")) {
			testRun = true;
		}
		System.out.println("Test run only:" + testRun);
		String sd_db = "jdbc:postgresql://127.0.0.1:5432/" + args[1];
		String results_db = "jdbc:postgresql://127.0.0.1:5432/" + args[2];
		
		
		try {
		    Class.forName(dbClass);	 
			connectionSD = DriverManager.getConnection(sd_db, "ws", "ws1234");
			connectionResults = DriverManager.getConnection(results_db, "ws", "ws1234");
				
			//connectionSD.setAutoCommit(false);
			//connectionResults.setAutoCommit(false);
			
			// Apply double underscore patch
			//DoubleUnderscore du = new DoubleUnderscore();
			//du.apply(testRun, connectionSD, connectionResults);
			
			// Apply geom insertion patch
			//GeomInsertion gi = new GeomInsertion();
			//gi.apply(testRun, connectionSD, connectionResults);
			
			// Apply add instance id patch
			//AddInstanceId id = new AddInstanceId();
			//id.apply(testRun, connectionSD, connectionResults);
			
			// Apply bad reason patch
			//AddBadReason br = new AddBadReason();
			//br.apply(testRun, connectionSD, connectionResults);
			
			// Create test data for task management
			//Mdu mdu = new Mdu();
			//mdu.apply(testRun, connectionSD, connectionResults, results_table, survey_id);
			
			// Rename the server
			//RenameServer rs = new RenameServer();
			//rs.apply(testRun, connectionSD, connectionResults, old_name, new_name);
			
			// Check for duplicates
			//Duplicates dups = new Duplicates();
			//dups.apply(testRun, connectionSD, connectionResults);

			// Apply urlBase patch
			//System.out.println("========= Patch URL bases to remove hostname and scheme");
			//RemoveUrlBase ub = new RemoveUrlBase();
			//ub.apply(testRun, connectionSD, connectionResults);
			
			// Apply upload move patch
			//System.out.println("\n\n\n");
			//System.out.println("========== Relocate upload files to per survey folders");
			//RelocateUploadedFiles rf = new RelocateUploadedFiles();
			//rf.apply(testRun, connectionSD, null, basePath);
			
			// Create addresses and port table from multiports things@
			//System.out.println("\n\n\n");
			//System.out.println("========== Normalise ports and addresses");
			//NormalisePorts np = new NormalisePorts();
			//np.apply(testRun, connectionResults);

			// Add _modified, _complete to all surveys
			AddModifiedComplete mc = new AddModifiedComplete();
			mc.apply(testRun, connectionSD, connectionResults);
			
			// Populate column_name with clean names
			//System.out.println("Patching column names");
			//ColumnNames cn = new ColumnNames();
			//cn.apply(testRun, connectionSD);
			
			
		} catch (Exception e) {
			response = "Error: Rolling back: " + e.getMessage();
			e.printStackTrace();
			System.out.println("        " + response);
			
			if(connectionSD != null) {
				try {		
					connectionSD.rollback();
				} catch (SQLException ex) {
				
				}
			} 
			
			if(connectionResults != null) {
				try {		
					connectionResults.rollback();
				} catch (SQLException ex) {
				
				}

			} 
			
		} finally {
			try {
				if (connectionSD != null) {
					connectionSD.close();
				}
			} catch (SQLException e) {
				
			}
			try {
				if (connectionResults != null) {
					connectionResults.close();
				}
			} catch (SQLException e) {
				
			}
		}		

	}
	

}
