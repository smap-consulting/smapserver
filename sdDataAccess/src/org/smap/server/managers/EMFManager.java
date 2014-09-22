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

package org.smap.server.managers;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EMFManager {
	
	private static final EMFManager manager = new EMFManager();
	protected EntityManagerFactory emf;
	
	private EMFManager() {
		
	}
	
	public static EMFManager getManager() {
		return manager;
	}
	
	
	public EntityManagerFactory getEntityManagerFactory(String pu) {
		if(emf == null) {
			emf = Persistence.createEntityManagerFactory(pu);
		}
		return emf;
	}
	
	public void closeEntityManagerFactory() {
		if(emf != null) {
			emf.close();
			emf = null;
		}
	}

}
