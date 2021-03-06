package fr.upmc.datacenter.providers.resources.logical.interfaces;

import fr.upmc.components.interfaces.OfferedI;
import fr.upmc.components.interfaces.RequiredI;
import fr.upmc.datacenter.providers.resources.logical.AllocatedApplicationVM;
import fr.upmc.datacenter.software.controllers.performance.AllocatedDispatcher;

/**
 * Services du fournisseur de resources logiques.
 * 
 * @author Daniel RADEAU
 *
 */

public interface LogicalResourcesProviderServicesI 
extends 	RequiredI,
			OfferedI
{
	
	/**
	 * Augmente la fr�quence des coeurs allou� par l'{@link AllocatedApplicationVM} pass� en param�tre. 
	 * 
	 * @param avm
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	Integer[] increaseApplicationVMFrequency(AllocatedApplicationVM avm) throws Exception;
	
	/**
	 * Diminue la fr�quence des coeurs allou� par l'{@link AllocatedApplicationVM} pass� en param�tre.
	 * 
	 * @param avm
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	Integer[] decreaseApplicationVMFrequency(AllocatedApplicationVM avm) throws Exception;
	
	/**
	 * Augmente le nombre de coeurs allou�s � une m�me {@link AllocatedApplicationVM}.
	 * 
	 * @param avm
	 * @param coreCount
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	Integer increaseApplicationVMCores(AllocatedApplicationVM avm, Integer coreCount) throws Exception;
	
	/**
	 * Diminue le nombre de coeurs allou�s � une m�me {@link AllocatedApplicationVM}.
	 * 
	 * @param avm
	 * @param coreCount
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	Integer decreaseApplicationVMCores(AllocatedApplicationVM avm, Integer coreCount) throws Exception;
	
	/**
	 * Permet l'allocation d'un certain nombre de {@link AllocatedApplicationVM}.
	 * 
	 * @param avmCount
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	AllocatedApplicationVM[] allocateApplicationVMs(Integer avmCount) throws Exception;
	
	/**
	 * Permet la d�sallocation d'un certain nombre de {@link AllocatedApplicationVM}.
	 * @param avms
	 * @return TODO
	 * @throws Exception TODO
	 */
	
	AllocatedApplicationVM[] releaseApplicationVMs(AllocatedApplicationVM[] avms) throws Exception;
	
	/**
	 * Permet de r�alis� la connection du port de sortie de l'avm concern�e au port d'entr�e du r�partiteur concern�.
	 * 
	 * @param aavm
	 * @param adsp
	 * @throws Exception
	 */
	
	void connectApplicationVM(AllocatedApplicationVM aavm, AllocatedDispatcher adsp) throws Exception;
	
	/**
	 * Permet de r�alis� la d�connection de l'avm au r�partiteur de requ�tes.
	 * 
	 * @param aavm
	 * @param adsp
	 * @throws Exception
	 */
	
	void disconnectApplicationVM(AllocatedApplicationVM aavm, AllocatedDispatcher adsp) throws Exception;
	
}
