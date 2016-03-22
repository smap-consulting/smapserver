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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

public class PersistenceContext {

	private String persistenceUnit = null;
	private EntityManager em;
	
	public PersistenceContext(String pu) {
		persistenceUnit = pu;
		EntityManagerFactory emf = EMFManager.getManager().getEntityManagerFactory(persistenceUnit);
		em = emf.createEntityManager();
	}
	
	/*
	 * Gets the entity manager for the current persistence unit
	 * If the entity manager factory does not exist then a new one is created for 
	 *  the passed in persistence unit.  Otherwise if the factory already
	 *  exists then the persistence unit is ignored and will not change. Use
	 *  resetPersistenceUnit() to force the persistence unit to change.
	 */
	// 
	public EntityManager getEntityManager() {
		return em;
	}
	
	/*
	// Force a reset of the persistence unit / entity manager factory
	public void resetPersistenceUnit(String pu) {
		persistenceUnit = pu;
		EMFManager.getManager().closeEntityManagerFactory();
		EntityManagerFactory emf = EMFManager.getManager().getEntityManagerFactory(persistenceUnit);
		em = emf.createEntityManager();
	}
	*/
	

}
