import java.io.IOException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.Date;

import org.smap.server.managers.PersistenceContext;
import org.smap.subscribers.Subscriber;

/*****************************************************************************

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

 ******************************************************************************/

/*
 * Usage java -jar subscribers.jar {path to subscriber configurations} {file base path} 
 */

public class Manager {

	public static void main(String[] args) {
		
		String fileLocn = "/smap";			// Default for legacy servers that do not set file path
		String subscriberType = "upload";	// Default subscriberType
		
		String smapId = args[0];
		if(args.length > 1) {
			if(args[1] != null && !args[1].equals("null")) {
				fileLocn = args[1];	
			}
		}
		if(args.length > 2) {
			subscriberType = args[2];	
		}
		
		System.out.println("Starting subscriber: " + smapId + " : " + fileLocn + " : " + subscriberType);
		int delaySecs = 10;
		PersistenceContext pc = new PersistenceContext(smapId);
		
		while(true) {

			SubscriberBatch batchJob = new SubscriberBatch();
			batchJob.go(pc, smapId, fileLocn, subscriberType);	// Run the batch job for the specified server

			try {
				Thread.sleep(delaySecs * 1000);
			} catch (Exception e) {
				// ignore
			}

		}
				
	}
	
	


}
