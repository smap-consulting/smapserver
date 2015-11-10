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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;
import org.smap.server.entities.Translation;

public class TranslationManager {

	private EntityManager em = null;

	public TranslationManager(PersistenceContext pc) {
		em = pc.getEntityManager();
	}

	/**
	 * Get Media
	 * 
	 */
	@SuppressWarnings("unchecked")
	public List<Translation> getManifestBySurvey(Survey survey, String language)
	{
		Query query = em.createQuery("SELECT t FROM TRANSLATION t " +
				"WHERE t.s_id = ?1 " +				
				"AND (t.type = 'image' OR t.type = 'video' OR t.type = 'audio' OR t.type = 'csv') " +
				"AND t.language = ?2 ");
		query.setParameter(1, survey.getId());
		query.setParameter(2, language);
		
		List<Translation> translationList = query.getResultList();
		return translationList;
	}
	
	
	@SuppressWarnings("unchecked")
	public List<Translation> getBySurvey(Survey survey) {
		Query query = em.createQuery("SELECT t FROM TRANSLATION t " +
				"WHERE t.s_id = ?1 " +
				"ORDER BY t.language");
		query.setParameter(1, survey.getId());
		List<Translation> translationList = query.getResultList();
		return translationList;
	}
	
	@SuppressWarnings("unchecked")
	public List<Translation> getBySurveyAndLanguage(Survey survey, String language) {
		Query query = em.createQuery("SELECT t FROM TRANSLATION t " +
				"WHERE t.s_id = ?1 " +
				"AND t.language = ?2 ");
		query.setParameter(1, survey.getId());
		query.setParameter(2, language);
		List<Translation> translationList = query.getResultList();
		return translationList;
	}
	
	@SuppressWarnings("unchecked")
	public List<Translation> getBySurveyAndLanguageAndType(Survey survey, String language, String form) {
		Query query = em.createQuery("SELECT t FROM TRANSLATION t " +
				"WHERE t.s_id = ?1 " +
				"AND t.language = ?2 " +
				"AND (t.type = ?3 OR t.type = 'none')");
		query.setParameter(1, survey.getId());
		query.setParameter(2, language);
		query.setParameter(3, form);
		List<Translation> translationList = query.getResultList();
		return translationList;
	}

	public void remove(Translation t) {
		try {
			em.getTransaction().begin();
			em.remove(t);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
		}
	}

	public void persist(Translation t) {
		try {
			em.getTransaction().begin();
			em.persist(t);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			e.printStackTrace();
			em.getTransaction().rollback();
			// TODO handle exceptions, specifically constraint violations
		}
		
	}
	
	public void persistBatch(Collection<HashMap<String, Translation>> l, Survey survey) {
		try {
			em.getTransaction().begin();
			
			Iterator<HashMap<String,Translation>> itrL = l.iterator();
			while(itrL.hasNext()) {								
				HashMap<String,Translation> types = (HashMap<String, Translation>) itrL.next();
				
				Collection<Translation> t = types.values();
				Iterator<Translation> itrT = t.iterator();

				while(itrT.hasNext()) {
					Translation trans = (Translation) itrT.next();
					trans.setSurveyId(survey.getId());
					em.persist(trans);
				}
			}
			em.getTransaction().commit();			

		} catch (PersistenceException e) {
			e.printStackTrace();
			em.getTransaction().rollback();
			// TODO handle exceptions, specifically constraint violations
		}
	}
}
