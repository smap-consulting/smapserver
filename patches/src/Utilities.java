import java.io.File;

public class Utilities {

	public static void deleteDirectory(File d) throws Exception {
		String[]entries = d.list();
		for(String e: entries){
		    File f = new File(d.getPath(), e);
		    if(f.isDirectory()) {
		    		deleteDirectory(f);
		    		deleteFile(f);
		    } else {
		    		deleteFile(f);
		    }
		}
	}
	
	public static void deleteFile(File f) throws Exception {
		if(f.getAbsolutePath().startsWith("/smap/uploadedSurveys")) {
			System.out.println("=========== deleting: " + f.getAbsolutePath());
			boolean success = f.delete();
			if(!success) {
				System.out.println("########### Error failed to delete: " + f.getAbsolutePath());
			}
		} else {
			throw new Exception("Attempting to delete directory outside valid range");
		}
	}
}
