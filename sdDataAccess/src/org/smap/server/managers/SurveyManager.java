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

import java.util.List;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.MissingTemplateException;
import org.smap.server.entities.Survey;


/**
 * 
 * @author Neil Penman, Gerard Gigliotti
 * 
 *         This class is used to manipulate Surveys from the database.
 */
public class SurveyManager {

	//private EntityManager em = null;
	private PersistenceContext pc = null;
	
	private static Logger log =
			 Logger.getLogger(SurveyManager.class.getName());

	public SurveyManager(PersistenceContext pc) {
		// em = pc.getEntityManager();
		this.pc = pc;
	}

	@SuppressWarnings("unchecked")
	public List<Survey> getAll() {
		EntityManager em = pc.getEntityManager();
		Query query = em.createQuery("SELECT survey FROM SURVEY survey");
		List<Survey> surveyList = query.getResultList();
		return surveyList;
	}

	/**
	 * Retrieves the survey by the survey's ID.
	 * 
	 * @param id
	 *            The surveyID used to retrieve the survey
	 * @return The survey found within the database. Returns null if no value
	 *         found.
	 */
	public Survey getById(int surveyId) {
		EntityManager em = pc.getEntityManager();
		Survey s = em.find(Survey.class, surveyId);
		return s;
	}
	
	/**
	 * Retrieves the survey by the survey's ident
	 * 
	 * @param id
	 *            The surveyIDent used to retrieve the survey
	 * @return The survey found within the database. Returns null if no value
	 *         found.
	 * @throws MissingTemplateException 
	 */
	public Survey getByIdent(String ident) throws MissingTemplateException {
		EntityManager em = pc.getEntityManager();
		Survey survey = null;
		Query searchQuery = em
				.createQuery("SELECT s FROM SURVEY s WHERE s.ident =:ident");
		Query searchQuery2 = em
				.createQuery("SELECT s FROM SURVEY s WHERE s.id =:id");		// Hack to address temporary issue where idents of old surveys may have been set wrongly during upgrade
		
		
		try {
			searchQuery.setParameter("ident", ident);
			survey = (Survey) searchQuery.getSingleResult();
		} catch (NoResultException e) {
			try {
				int sId = Integer.parseInt(ident);
				searchQuery2.setParameter("id", sId);
				survey = (Survey) searchQuery2.getSingleResult();
			} catch (NoResultException e2) {
				System.out.println("Error: Survey Template not found with ident(" + ident + ") survey results ignored");
				throw new MissingTemplateException(ident);
			}
		} finally {
		}
		return survey;
	}

	/**
	 * This method is used to return a survey based on its name.
	 * 
	 * @param name
	 *            The name of the survey to find.
	 * @return The survey found. Can return null.
	 */
	public Survey getByName(String name) throws MissingTemplateException {
		EntityManager em = pc.getEntityManager();
		Survey survey = null;
		Query searchQuery = em
				.createQuery("SELECT s FROM SURVEY s WHERE s.name =:name");
		searchQuery.setParameter("name", name);
		try {
			survey = (Survey) searchQuery.getSingleResult();
		} catch (NoResultException e) {
			System.out.println("Error: Survey Template not found(" + name + ") survey results ignored");
			throw new MissingTemplateException(name);
		} finally {
		}
		return survey;
	}
	
	/**
	 * This method is used to delete a survey based on its name.
	 * 
	 * @param name
	 *            The name of the survey to delete.
	 */
	public void delete(String name) {

		EntityManager em = pc.getEntityManager();
		em.getTransaction().begin();
		Query deleteQuery = em.createQuery("DELETE FROM SURVEY s WHERE s.name = :name");
		deleteQuery.setParameter("name", name);
		deleteQuery.executeUpdate();
		em.getTransaction().commit();
	}

	/**
	 * Checks to confirm that a survey exists.
	 * 
	 * @param name
	 *            Name of the survey to search for
	 * @return returns True if survey found, false if it isn't.
	 */
	public boolean surveyExists(String name, int projectId) {
		EntityManager em = pc.getEntityManager();
		Query query = em.createQuery(
				"SELECT COUNT(*) FROM SURVEY survey WHERE survey.display_name = ?1 " +
				" and survey.p_id = ?2")
				.setParameter(1, name)
				.setParameter(2, projectId);
		Long count = (Long) query.getSingleResult();
		return count == 1;
	}

	public void persist(Survey s) {
		EntityManager em = pc.getEntityManager();
		try {
			// TODO shift this function into the survey object
			//String surveyName = generateSurveyName(s.getDisplayName());
			
			em.getTransaction().begin();
			em.persist(s);
			String ident = s.getIdent();
			
			log.info("Persisting to ident: " + ident);
			
			// Set the survey ident to a temporary value based on survey id if an ident was not specified
			if(ident == null || ident.trim().length() == 0) {
				String surveyName = "s" + s.getProjectId() + "_" + s.getId();
				s.setIdent(surveyName);
				em.persist(s);
			}

			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
			e.printStackTrace();
		} finally {
		}
	}
	
	/*
	 * Returns a survey name without spaces and truncated to 30 characters.
	 * 
	 * @return survey name
	 */
	public String generateSurveyName(String displayName) {
		String surveyName = null;
		int maxLength = 30;
		
		surveyName = displayName.trim();
		surveyName = surveyName.replace(" ", "");	// Remove spaces
		surveyName = surveyName.replaceAll("[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]", "");	// Remove special characters ;
		//surveyName = surveyName.replaceAll("[\\.]", "");	// Remove special characters ;
		if(surveyName.length() > maxLength) {
			surveyName = surveyName.substring(0, maxLength);
		}
		
		return surveyName;
	}
}
