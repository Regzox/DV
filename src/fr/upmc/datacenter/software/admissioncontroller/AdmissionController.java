package fr.upmc.datacenter.software.admissioncontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import fr.upmc.components.AbstractComponent;
import fr.upmc.components.connectors.DataConnector;
import fr.upmc.components.cvm.AbstractCVM;
import fr.upmc.components.interfaces.DataRequiredI.PullI;
import fr.upmc.datacenter.connectors.ControlledDataConnector;
import fr.upmc.datacenter.hardware.computer.stock.Stock;
import fr.upmc.datacenter.hardware.computers.Computer;
import fr.upmc.datacenter.hardware.computers.Computer.AllocatedCore;
import fr.upmc.datacenter.hardware.computers.ComputerDynamicState;
import fr.upmc.datacenter.hardware.computers.connectors.ComputerServicesConnector;
import fr.upmc.datacenter.hardware.computers.interfaces.ComputerDynamicStateI;
import fr.upmc.datacenter.hardware.computers.interfaces.ComputerServicesI;
import fr.upmc.datacenter.hardware.computers.interfaces.ComputerStateDataConsumerI;
import fr.upmc.datacenter.hardware.computers.interfaces.ComputerStaticStateI;
import fr.upmc.datacenter.hardware.computers.ports.ComputerDynamicStateDataOutboundPort;
import fr.upmc.datacenter.hardware.computers.ports.ComputerServicesOutboundPort;
import fr.upmc.datacenter.hardware.computers.ports.ComputerStaticStateDataOutboundPort;
import fr.upmc.datacenter.hardware.processor.model.Model;
import fr.upmc.datacenter.hardware.processors.Processor;
import fr.upmc.datacenter.hardware.processors.Processor.ProcessorPortTypes;
import fr.upmc.datacenter.hardware.processors.ProcessorDynamicState;
import fr.upmc.datacenter.hardware.processors.ProcessorStaticState;
import fr.upmc.datacenter.hardware.processors.connectors.ProcessorIntrospectionConnector;
import fr.upmc.datacenter.hardware.processors.connectors.ProcessorManagementConnector;
import fr.upmc.datacenter.hardware.processors.interfaces.ProcessorIntrospectionI;
import fr.upmc.datacenter.hardware.processors.interfaces.ProcessorManagementI;
import fr.upmc.datacenter.hardware.processors.ports.ProcessorIntrospectionOutboundPort;
import fr.upmc.datacenter.hardware.processors.ports.ProcessorManagementOutboundPort;
import fr.upmc.datacenter.interfaces.ControlledDataRequiredI.ControlledPullI;
import fr.upmc.datacenter.software.admissioncontroller.interfaces.AdmissionControllerI;
import fr.upmc.datacenter.software.admissioncontroller.interfaces.AdmissionControllerManagementI;
import fr.upmc.datacenter.software.admissioncontroller.ports.AdmissionControllerManagementInboundPort;
import fr.upmc.datacenter.software.applicationvm.extended.ApplicationVM;
import fr.upmc.datacenter.software.connectors.RequestNotificationConnector;
import fr.upmc.datacenter.software.connectors.RequestSubmissionConnector;
import fr.upmc.datacenter.software.dispatcher.Dispatcher;
import fr.upmc.datacenter.software.dispatcher.connectors.DispatcherManagementConnector;
import fr.upmc.datacenter.software.dispatcher.interfaces.DispatcherManagementI;
import fr.upmc.datacenter.software.dispatcher.ports.DispatcherManagementOutboundPort;
import fr.upmc.datacenter.software.enumerations.Tag;
import fr.upmc.datacenter.software.interfaces.RequestNotificationI;
import fr.upmc.datacenter.software.interfaces.RequestSubmissionI;
import fr.upmc.datacenter.software.ports.RequestNotificationOutboundPort;
import fr.upmc.datacenter.software.ports.RequestSubmissionOutboundPort;
import fr.upmc.datacenterclient.requestgenerator.RequestGenerator;
import fr.upmc.datacenterclient.requestgenerator.connectors.RequestGeneratorManagementConnector;
import fr.upmc.datacenterclient.requestgenerator.interfaces.RequestGeneratorManagementI;
import fr.upmc.datacenterclient.requestgenerator.ports.RequestGeneratorManagementOutboundPort;
import fr.upmc.external.software.applications.AbstractApplication;
import fr.upmc.javassist.DynamicConnectorFactory;

/**
 * Le {@link AdmissionController} a pour r�le de r�cup�rer les demandes clientes
 * d'h�bergement d'applications. Pour cela il dispose d'un parc informatique {@link Stock}
 * mettant � sa disposition un certain nombre d'ordinateurs {@link Computer} compos�s d'un certain
 * nombre de processeurs {@link Processor} disposant d'une puissance de calcul bas�e sur des mod�les
 * existant ou fictifs de processeurs {@link Model}.
 * 
 * @author Daniel RADEAU
 *
 */

public class AdmissionController
	extends 
		AbstractComponent
	implements 
		AdmissionControllerI,
		AdmissionControllerManagementI,
		ComputerStateDataConsumerI
{		
	public static boolean LOGGING_ALL = false;
	public static boolean LOGGING_REQUEST_GENERATOR = false;
	public static boolean LOGGING_APPLICATION_VM = false;
	public static boolean LOGGING_DISPATCHER = false;
	
	protected String uri;

	/**
	 * Maps des URIs des port appartenant aux diff�rents ordinateurs connect�s au contr�leur d'admission
	 */
	
	protected Map<String, String> computerServicesOutboundPortMap;
	protected Map<String, String> computerStaticStateDataOutboundPortMap;
	protected Map<String, String> computerDynamicStateDataOutboundPortMap;
	
	/**
	 * Maps des �tats statiques et dynamiques des ordinateurs
	 */
	
	protected Map<String, ComputerStaticStateI> computersStaticStates;
	protected Map<String, ComputerDynamicStateI> computersDynamicStates;
	
	/**
	 * Maps des ports de chaques processeurs appartenant aux ordinateurs
	 */
	
	protected Map<String, Map<String, String>> computerProcessorsManagementOutboundPortsMap;
	protected Map<String, Map<String, String>> computerProcessorsIntrospectionOutboundPortsMap;

	/**
	 * Construction d'un {@link AdmissionController} ayant pour nom <em>uri</em> et
	 * disposant d'un port de contr�le offert {@link AdmissionControllerManagementInboundPort}
	 * 
	 * @param uri
	 * @param AdmissionControllerManagementInboundPortURI
	 * @throws Exception
	 */
	
	public AdmissionController(String uri, String AdmissionControllerManagementInboundPortURI) throws Exception {
		super(1, 1);

		this.uri = uri;

		/**
		 * Cr�ation du port d'entr�e de management du contr�leur d'admission
		 */

		if (!offeredInterfaces.contains(AdmissionControllerManagementI.class))
			addOfferedInterface(AdmissionControllerManagementI.class);
		
		AdmissionControllerManagementInboundPort acmip = new AdmissionControllerManagementInboundPort(AdmissionControllerManagementInboundPortURI, AdmissionControllerManagementI.class, this);
		addPort(acmip);
		acmip.publishPort();
		
		computerServicesOutboundPortMap = new HashMap<>();
		computerStaticStateDataOutboundPortMap = new HashMap<>();
		computerDynamicStateDataOutboundPortMap = new HashMap<>();
		
		computersStaticStates = new HashMap<>();
		computersDynamicStates = new HashMap<>();
		
		computerProcessorsManagementOutboundPortsMap = new HashMap<>();
		computerProcessorsIntrospectionOutboundPortsMap = new HashMap<>();
	}

	/**
	 * Traitements en interne
	 */
	
	protected String findComputerAviable() {
		return null;
	}

	@Override
	public void connectToComputer(String computerURI, String csipURI, String cssdipURI, String cdsdipURI) throws Exception {

		/**
		 * Cr�ation d'un port de sortie ComputerServicesOutboundPort
		 * Connexion au port d'entr�e ComputerServicesInboundPort
		 */
		
		if (!requiredInterfaces.contains(ComputerServicesI.class))
			addRequiredInterface(ComputerServicesI.class);
		
		String csopURI = generateURI(Tag.COMPUTER_SERVICES_OUTBOUND_PORT);
		ComputerServicesOutboundPort csop = new ComputerServicesOutboundPort(csopURI, this);
		addPort(csop);
		csop.publishPort();
		csop.doConnection(csipURI, ComputerServicesConnector.class.getCanonicalName());

		logMessage(csopURI + " created and connected to " + csipURI);

		if (!requiredInterfaces.contains(PullI.class))
			addRequiredInterface(PullI.class);
		
		String cssdopURL = generateURI(Tag.COMPUTER_STATIC_STATE_DATA_OUTBOUND_PORT);
		ComputerStaticStateDataOutboundPort cssdop = new ComputerStaticStateDataOutboundPort(cssdopURL, this, computerURI);
		System.out.println(isInterface(cssdop.getImplementedInterface()));
		addPort(cssdop);
		cssdop.publishPort();
		cssdop.doConnection(cssdipURI, DataConnector.class.getCanonicalName());
		
		logMessage(cssdopURL + " created and connected to " + cssdipURI);
		
		if (!requiredInterfaces.contains(ControlledPullI.class))
			addRequiredInterface(ControlledPullI.class);
		
		String cdsdopURI = generateURI(Tag.COMPUTER_DYNAMIC_STATE_DATA_OUTBOUND_PORT);
		ComputerDynamicStateDataOutboundPort cdsdop = new ComputerDynamicStateDataOutboundPort(cdsdopURI, this, computerURI);
		addPort(cdsdop);
		cdsdop.publishPort();
		cdsdop.doConnection(cdsdipURI, ControlledDataConnector.class.getCanonicalName());

		logMessage(cdsdopURI + " created and connected to " + cdsdipURI);
		logMessage("");

		computerServicesOutboundPortMap.put(computerURI, csopURI);
		computerStaticStateDataOutboundPortMap.put(computerURI, cssdopURL);
		computerDynamicStateDataOutboundPortMap.put(computerURI, cdsdopURI);
		
		computersStaticStates.put(computerURI, (ComputerStaticStateI) cssdop.request());
		
		/**
		 * Cr�ation d'un port de management et d'introspection sur chaque processeur
		 */
		
		for (String processorURI : computersStaticStates.get(computerURI).getProcessorURIs().values()) {
			String processorManagementInboundPortURI = computersStaticStates.get(computerURI).getProcessorPortMap().get(processorURI).get(ProcessorPortTypes.MANAGEMENT);
			String processorIntrospectionInboundPortURI = computersStaticStates.get(computerURI).getProcessorPortMap().get(processorURI).get(ProcessorPortTypes.INTROSPECTION);
			
			if (!requiredInterfaces.contains(ProcessorManagementI.class))
				addRequiredInterface(ProcessorManagementI.class);
			
			ProcessorManagementOutboundPort pmop = new ProcessorManagementOutboundPort("pmop-" + processorURI, this);
			this.addPort(pmop);
			pmop.publishPort();
			pmop.doConnection(processorManagementInboundPortURI, ProcessorManagementConnector.class.getCanonicalName());
			
			
			if (!requiredInterfaces.contains(ProcessorIntrospectionI.class))
				addRequiredInterface(ProcessorIntrospectionI.class);
			
			ProcessorIntrospectionOutboundPort piop = new ProcessorIntrospectionOutboundPort("piop-" + processorURI, this);
			this.addPort(piop);
			piop.publishPort();
			piop.doConnection(processorIntrospectionInboundPortURI, ProcessorIntrospectionConnector.class.getCanonicalName());
		}
		
	}


	@Override
	public String generateURI(Object tag) {
		return uri + '_' + tag + '_' + Math.abs(new Random().nextInt());
	}


	@Override
	public String submitApplication() throws Exception {

		/**
		 * Recherche parmis les ordinateurs disponibles du premier candidat poss�dant les ressources suffisantes
		 * � l'allocation d'une AVM de taille arbitraire.
		 * Dans un premier temps nous allons salement allouer un processeur entier par AVM.
		 * 
		 */

		/**
		 * Parcours de tous les ordinateurs mis � disposition � la recherche d'un processeur � allouer
		 */

		String computerURI = null;
		int numberOfCores = 0;
		
//		for (String cdsdopURI : computerDynamicStateDataOutboundPortMap.values()) {
//			ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(cdsdopURI);
//			ComputerDynamicState data = (ComputerDynamicState) cdsdop.request();
//			boolean[][] processorMap = data.getCurrentCoreReservations();
//
//			/**
//			 * Parcours de tous les processeurs de l'ordinateur en cours
//			 */
//			
//			for (int proccessorIndex = 0; proccessorIndex < processorMap.length; proccessorIndex++) {
//				
//				/**
//				 * Parcours de tous les coeurs du processeur en cours
//				 */
//				
//				for (boolean coreAllocated : processorMap[proccessorIndex]) {
//					
//					/**
//					 * A la d�tection du premier coeur non allou�, nous tenons notre machine pour l'allocation
//					 */
//					
//					if (!coreAllocated) {
//						computerURI = data.getComputerURI();
//						numberOfCores =  processorMap[proccessorIndex].length;
//					}
//				}
//			}
//		}
		
		computerURI = findAvailableComputerForApplicationVMAllocation();
		
		/**
		 * Dans la cas o� nous n'avons pas trouv� de d'ordinateurs de libres, alors computerURI est toujours nulle
		 * et nous ne pouvons pas donner suite � la demande d'hebergement d'application
		 */
		
		if (computerURI == null && numberOfCores == 0) {
			logMessage("Ressources leak, impossible to welcome the application");
			throw new Exception("Ressources leak, impossible to welcome the application");
		}
		
		logMessage("Ressources found, " + numberOfCores +  " cores on computer : " + computerURI);
		
		/**
		 * D�claration d'un nouveau g�n�rateur de requetes
		 */
		
		final String requestGeneratorURI = generateURI(Tag.REQUEST_GENERATOR);
		double meanInterArrivalTime = 500;
		long meanNumberOfInstructions = 10000000000L;
		final String requestGeneratorManagementInboundPortURI = generateURI(Tag.REQUEST_GENERATOR_MANAGEMENT_INBOUND_PORT);
		final String requestGeneratorRequestSubmissionOutboundPortURI = generateURI(Tag.REQUEST_SUBMISSION_OUTBOUND_PORT);
		final String requestGeneratorRequestNotificationInboundPortURI = generateURI(Tag.REQUEST_NOTIFICATION_INBOUND_PORT);
		
		RequestGenerator requestGenerator = new RequestGenerator(
				requestGeneratorURI, 
				meanInterArrivalTime, 
				meanNumberOfInstructions, 
				requestGeneratorManagementInboundPortURI, 
				requestGeneratorRequestSubmissionOutboundPortURI, 
				requestGeneratorRequestNotificationInboundPortURI
				);
		AbstractCVM.theCVM.addDeployedComponent(requestGenerator);
		
		if (LOGGING_ALL | LOGGING_REQUEST_GENERATOR) {
			requestGenerator.toggleLogging();
			requestGenerator.toggleTracing();
		}
		
		requestGenerator.start();
		
		/**
		 * D�claration d'une nouvelle applicationVM
		 */
		
		final String applicationVMURI = generateURI(Tag.APPLICATION_VM);
		final String applicationVMManagementInboundPortURI = generateURI(Tag.APPLICATION_VM_MANAGEMENT_INBOUND_PORT);
		final String applicationVMRequestSubmissionInboundPortURI = generateURI(Tag.REQUEST_SUBMISSION_INBOUND_PORT);
		final String applicationVMRequestNotificationOutboundPortURI = generateURI(Tag.REQUEST_NOTIFICATION_OUTBOUND_PORT);
		
		ApplicationVM applicationVM = new ApplicationVM(
				applicationVMURI,
				applicationVMManagementInboundPortURI,
				applicationVMRequestSubmissionInboundPortURI,
				applicationVMRequestNotificationOutboundPortURI
				);
		AbstractCVM.theCVM.addDeployedComponent(applicationVM);
		
		if (LOGGING_ALL | LOGGING_APPLICATION_VM) {
			applicationVM.toggleLogging();
			applicationVM.toggleTracing();
		}
		
		/**
		 * D�claration d'un nouveau dispatcher
		 */
		
		final String dispatcherURI = generateURI(Tag.DISPATCHER);
		final String dispatcherManagementInboundPortURI = generateURI(Tag.DISPATCHER_MANAGEMENT_INBOUND_PORT);
		
		Dispatcher dispatcher = new Dispatcher(
				dispatcherURI,
				dispatcherManagementInboundPortURI
				);
		AbstractCVM.theCVM.addDeployedComponent(dispatcher);
		
		if (LOGGING_ALL | LOGGING_DISPATCHER) {
			dispatcher.toggleLogging();
			dispatcher.toggleTracing();
		}
		
		dispatcher.start();
		
//		/**
//		 * Tentative d'allocation du nombre de coeurs voulu pour l'applicationVM
//		 */
//		
//		ComputerServicesOutboundPort csop = (ComputerServicesOutboundPort) findPortFromURI(computerServicesOutboundPortMap.get(computerURI));
//		AllocatedCore[] cores = csop.allocateCores(numberOfCores);
//		
//		if (cores.length == numberOfCores)
//			logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores successfully found on " + computerURI);
//		else
//			logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores unfortunately not found on " + computerURI);
		
		AllocatedCore[] cores = tryToAllocateCoreOn(computerURI);
		
		/**
		 * Si � ce stade aucun coeurs n'est disponible alors nous nous trouvons dans un �tat incoh�rent 
		 * et la soumission ne peut donc donner suite
		 */
		
		if (cores.length == 0) {
			logMessage("GRAVE : no core busyless found on the selected computer " + computerURI);
			AbstractCVM.theCVM.removeDeployedComponent(requestGenerator);
			AbstractCVM.theCVM.removeDeployedComponent(applicationVM);
			AbstractCVM.theCVM.removeDeployedComponent(dispatcher);
			logMessage("Submission aborted due to incoherent an computer status on " + computerURI);
			throw new Exception("Submission aborted due to incoherent an computer status on " + computerURI);
		}
		
		applicationVM.allocateCores(cores);
		
		logMessage(cores.length + " cores are successfully allocated for the applicationVM " + applicationVMURI);
		
		/**
		 * Cr�ation d'un port de contr�le pour la gestion du dispatcher
		 */
		
		if (!requiredInterfaces.contains(DispatcherManagementI.class))
			addRequiredInterface(DispatcherManagementI.class);
		
		DispatcherManagementOutboundPort dmop = new DispatcherManagementOutboundPort(DispatcherManagementI.class, this);
		addPort(dmop);
		dmop.publishPort();
		dmop.doConnection(dispatcherManagementInboundPortURI, DispatcherManagementConnector.class.getCanonicalName());
		
		/**
		 * Connexion du RequestGenerator au dispatcher
		 */
				
		String rsipURI = dmop.connectToRequestGenerator(requestGeneratorRequestNotificationInboundPortURI);
		RequestSubmissionOutboundPort rsop = (RequestSubmissionOutboundPort) requestGenerator.findPortFromURI(requestGeneratorRequestSubmissionOutboundPortURI);
		rsop.doConnection(rsipURI, RequestSubmissionConnector.class.getCanonicalName());
		
		logMessage(requestGeneratorURI + " connected to " + dispatcherURI);
		
		/**
		 * Connexion de l'ApplicationVM au dispatcher
		 */
		
		String rnipURI = dmop.connectToApplicationVM(applicationVMRequestSubmissionInboundPortURI);
		RequestNotificationOutboundPort rnop = (RequestNotificationOutboundPort) applicationVM.findPortFromURI(applicationVMRequestNotificationOutboundPortURI);
		rnop.doConnection(rnipURI, RequestNotificationConnector.class.getCanonicalName());
		
		logMessage(applicationVMURI + " connected to " + dispatcherURI);
		
		/**
		 * Lancement de l'applicationVM
		 */
		
		applicationVM.start();
		
		logMessage(applicationVMURI + " launched and ready to receive requests");
		
		/**
		 * Cr�ation du port de management pour la gestion du RequestGenerator
		 */
		
		if (!requiredInterfaces.contains(RequestGeneratorManagementI.class))
			addRequiredInterface(RequestGeneratorManagementI.class);
		
		final String rgmopURI = generateURI(Tag.REQUEST_GENERATOR_MANAGEMENT_OUTBOUND_PORT);
		RequestGeneratorManagementOutboundPort rgmop = new RequestGeneratorManagementOutboundPort(rgmopURI, this);
		addPort(rgmop);
		rgmop.publishPort();
		
		/**
		 * Connexion du port de management au RequestGenerator
		 */
		
		rgmop.doConnection(requestGeneratorManagementInboundPortURI, RequestGeneratorManagementConnector.class.getCanonicalName());
		
		/**
		 * Retour de l'URI du port de management du RequestGenerator
		 */
		
		return rgmopURI;
	}

	@Override
	public void submitApplication(
			AbstractApplication application,
			Class<?> submissionInterface) throws Exception 
	{
		/**
		 * On suppose que le client voulant soumettre son application poss�de d�j� un composant de type g�n�rateur de requ�tes
		 * lui permettant de soumettre via son interface requise (impl�ment�e par son ...submissionOutboundPort) 
		 */
		
		/**
		 * Recherche parmis les ordinateurs disponibles du premier candidat poss�dant les ressources suffisantes
		 * � l'allocation d'une AVM de taille arbitraire.
		 * Dans un premier temps nous allons salement allouer un processeur entier par AVM.
		 * 
		 */

		/**
		 * Parcours de tous les ordinateurs mis � disposition � la recherche d'un processeur � allouer
		 */

		String computerURI = null;
		int numberOfCores = 0;
		
		for (String cdsdopURI : computerDynamicStateDataOutboundPortMap.values()) {
			ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(cdsdopURI);
			ComputerDynamicState data = (ComputerDynamicState) cdsdop.request();
			boolean[][] processorMap = data.getCurrentCoreReservations();

			/**
			 * Parcours de tous les processeurs de l'ordinateur en cours
			 */
			
			for (int proccessorIndex = 0; proccessorIndex < processorMap.length; proccessorIndex++) {
				
				/**
				 * Parcours de tous les coeurs du processeur en cours
				 */
				
				for (boolean coreAllocated : processorMap[proccessorIndex]) {
					
					/**
					 * A la d�tection du premier coeur non allou�, nous tenons notre machine pour l'allocation
					 */
					
					if (!coreAllocated) {
						computerURI = data.getComputerURI();
						numberOfCores =  processorMap[proccessorIndex].length;
					}
				}
			}
		}
		
		/**
		 * Dans la cas o� nous n'avons pas trouv� de d'ordinateurs de libres, alors computerURI est toujours nulle
		 * et nous ne pouvons pas donner suite � la demande d'hebergement d'application
		 */
		
		if (computerURI == null && numberOfCores == 0) {
			logMessage("Ressources leak, impossible to welcome the application");
			throw new Exception("Ressources leak, impossible to welcome the application");
		}
		
		logMessage("Ressources found, " + numberOfCores +  " cores on computer : " + computerURI);
		
		/**
		 * D�claration d'une nouvelle applicationVM
		 */
		
		final String applicationVMURI = generateURI(Tag.APPLICATION_VM);
		final String applicationVMManagementInboundPortURI = generateURI(Tag.APPLICATION_VM_MANAGEMENT_INBOUND_PORT);
		final String applicationVMRequestSubmissionInboundPortURI = generateURI(Tag.REQUEST_SUBMISSION_INBOUND_PORT);
		final String applicationVMRequestNotificationOutboundPortURI = generateURI(Tag.REQUEST_NOTIFICATION_OUTBOUND_PORT);
		
		ApplicationVM applicationVM = new ApplicationVM(
				applicationVMURI,
				applicationVMManagementInboundPortURI,
				applicationVMRequestSubmissionInboundPortURI,
				applicationVMRequestNotificationOutboundPortURI
				);
		AbstractCVM.theCVM.addDeployedComponent(applicationVM);
		
		if (LOGGING_ALL | LOGGING_APPLICATION_VM) {
			applicationVM.toggleLogging();
			applicationVM.toggleTracing();
		}
		
		/**
		 * D�claration d'un nouveau dispatcher
		 */
		
		final String dispatcherURI = generateURI(Tag.DISPATCHER);
		final String dispatcherManagementInboundPortURI = generateURI(Tag.DISPATCHER_MANAGEMENT_INBOUND_PORT);
		
		Dispatcher dispatcher = new Dispatcher(
				dispatcherURI,
				dispatcherManagementInboundPortURI
				);
		AbstractCVM.theCVM.addDeployedComponent(dispatcher);
		
		if (LOGGING_ALL | LOGGING_DISPATCHER) {
			dispatcher.toggleLogging();
			dispatcher.toggleTracing();
		}
		
		dispatcher.start();
		
		/**
		 * Tentative d'allocation du nombre de coeurs voulu pour l'applicationVM
		 */
				
		ComputerServicesOutboundPort csop = (ComputerServicesOutboundPort) findPortFromURI(computerServicesOutboundPortMap.get(computerURI));
		AllocatedCore[] cores = csop.allocateCores(numberOfCores);
		
		if (cores.length == numberOfCores)
			logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores successfully found on " + computerURI);
		else
			logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores unfortunately not found on " + computerURI);
		
		/**
		 * Si � ce stade aucun coeurs n'est disponible alors nous nous trouvons dans un �tat incoh�rent 
		 * et la soumission ne peut donc donner suite
		 */
		
		if (cores.length == 0) {
			logMessage("GRAVE : no core busyless found on the selected computer " + computerURI);
//			AbstractCVM.theCVM.removeDeployedComponent(requestGenerator);
			AbstractCVM.theCVM.removeDeployedComponent(applicationVM);
			AbstractCVM.theCVM.removeDeployedComponent(dispatcher);
			logMessage("Submission aborted due to incoherent an computer status on " + computerURI);
			throw new Exception("Submission aborted due to incoherent an computer status on " + computerURI);
		}
		
		applicationVM.allocateCores(cores);
		
		logMessage(cores.length + " cores are successfully allocated for the applicationVM " + applicationVMURI);
		
		/**
		 * Cr�ation d'un port de contr�le pour la gestion du dispatcher
		 */
		
		if (!requiredInterfaces.contains(DispatcherManagementI.class))
			addRequiredInterface(DispatcherManagementI.class);
		
		DispatcherManagementOutboundPort dmop = new DispatcherManagementOutboundPort(DispatcherManagementI.class, this);
		addPort(dmop);
		dmop.publishPort();
		dmop.doConnection(dispatcherManagementInboundPortURI, DispatcherManagementConnector.class.getCanonicalName());
		
		/**
		 * Connexion de l'application au dispatcher
		 */

		System.out.println(application.findPortURIsFromInterface(RequestSubmissionI.class)[0]);
		System.out.println(application.findPortURIsFromInterface(RequestNotificationI.class)[0]);
		
		String rsipURI = dmop.connectToRequestGenerator(application.findPortURIsFromInterface(RequestNotificationI.class)[0]);
		RequestSubmissionOutboundPort rsop = (RequestSubmissionOutboundPort) application.findPortFromURI(application.findPortURIsFromInterface(RequestSubmissionI.class)[0]);
		rsop.doConnection(rsipURI, DynamicConnectorFactory.createConnector(RequestSubmissionI.class, RequestSubmissionI.class).getCanonicalName());
		 
		logMessage(rsop.getOwner().toString() + " connected to " + dispatcherURI);
		
		/**
		 * Connexion de l'ApplicationVM au dispatcher
		 */
		
		String rnipURI = dmop.connectToApplicationVM(applicationVMRequestSubmissionInboundPortURI);
		RequestNotificationOutboundPort rnop = (RequestNotificationOutboundPort) applicationVM.findPortFromURI(applicationVMRequestNotificationOutboundPortURI);
		rnop.doConnection(rnipURI, RequestNotificationConnector.class.getCanonicalName());
		
		logMessage(applicationVMURI + " connected to " + dispatcherURI);
		
		/**
		 * Lancement de l'applicationVM
		 */
		
		applicationVM.start();
		
		logMessage(applicationVMURI + " launched and ready to receive requests");
				
	}
	
	@Override
	public void forceApplicationVMIncrementation() throws Exception {
		String[] dmopsURI = this.findOutboundPortURIsFromInterface(DispatcherManagementI.class);

		for (String dmopURI : dmopsURI) {
			
			String computerURI = null;
			int numberOfCores = 0; 
			
			for (String cdsdopURI : computerDynamicStateDataOutboundPortMap.values()) {
				ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(cdsdopURI);
				ComputerDynamicState data = (ComputerDynamicState) cdsdop.request();
				boolean[][] processorMap = data.getCurrentCoreReservations();

				/**
				 * Parcours de tous les processeurs de l'ordinateur en cours
				 */
				
				for (int proccessorIndex = 0; proccessorIndex < processorMap.length; proccessorIndex++) {
					
					/**
					 * Parcours de tous les coeurs du processeur en cours
					 */
					
					for (boolean coreAllocated : processorMap[proccessorIndex]) {
						
						/**
						 * A la d�tection du premier coeur non allou�, nous tenons notre machine pour l'allocation
						 */
						
						if (!coreAllocated) {
							computerURI = data.getComputerURI();
							numberOfCores =  processorMap[proccessorIndex].length;
						}
					}
				}
			}
			
			/**
			 * Dans la cas o� nous n'avons pas trouv� de d'ordinateurs de libres, alors computerURI est toujours nulle
			 * et nous ne pouvons pas donner suite � la demande d'hebergement d'application
			 */
			
			if (computerURI == null && numberOfCores == 0) {
				logMessage("Ressources leak, impossible to welcome the application");
				throw new Exception("Ressources leak, impossible to welcome the application");
			}
			
			logMessage("Ressources found, " + numberOfCores +  " cores on computer : " + computerURI);
			
			/**
			 * D�claration d'une nouvelle applicationVM
			 */
			
			final String applicationVMURI = generateURI(Tag.APPLICATION_VM);
			final String applicationVMManagementInboundPortURI = generateURI(Tag.APPLICATION_VM_MANAGEMENT_INBOUND_PORT);
			final String applicationVMRequestSubmissionInboundPortURI = generateURI(Tag.REQUEST_SUBMISSION_INBOUND_PORT);
			final String applicationVMRequestNotificationOutboundPortURI = generateURI(Tag.REQUEST_NOTIFICATION_OUTBOUND_PORT);
			
			ApplicationVM applicationVM = new ApplicationVM(
					applicationVMURI,
					applicationVMManagementInboundPortURI,
					applicationVMRequestSubmissionInboundPortURI,
					applicationVMRequestNotificationOutboundPortURI
					);
			AbstractCVM.theCVM.addDeployedComponent(applicationVM);
			
			if (LOGGING_ALL | LOGGING_APPLICATION_VM) {
				applicationVM.toggleLogging();
				applicationVM.toggleTracing();
			}
			
			/**
			 * Tentative d'allocation du nombre de coeurs voulu pour l'applicationVM
			 */
			
			ComputerServicesOutboundPort csop = (ComputerServicesOutboundPort) findPortFromURI(computerServicesOutboundPortMap.get(computerURI));
			AllocatedCore[] cores = csop.allocateCores(numberOfCores);
			
			if (cores.length == numberOfCores)
				logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores successfully found on " + computerURI);
			else
				logMessage("(" + cores.length + "/" + numberOfCores + ") Amount of wanted cores unfortunately not found on " + computerURI);
			
			/**
			 * Si � ce stade aucun coeurs n'est disponible alors nous nous trouvons dans un �tat incoh�rent 
			 * et la soumission ne peut donc donner suite
			 */
			
			if (cores.length == 0) {
				logMessage("GRAVE : no core busyless found on the selected computer " + computerURI);
				AbstractCVM.theCVM.removeDeployedComponent(applicationVM);
				logMessage("Submission aborted due to incoherent an computer status on " + computerURI);
				throw new Exception("Submission aborted due to incoherent an computer status on " + computerURI);
			}
			
			applicationVM.allocateCores(cores);
			
			logMessage(cores.length + " cores are successfully allocated for the applicationVM " + applicationVMURI);
			
			/**
			 * Connexion de l'ApplicationVM au dispatcher
			 */
			
			DispatcherManagementOutboundPort dmop = (DispatcherManagementOutboundPort) this.findPortFromURI(dmopURI);
			
			String rnipURI = dmop.connectToApplicationVM(applicationVMRequestSubmissionInboundPortURI);
			RequestNotificationOutboundPort rnop = (RequestNotificationOutboundPort) applicationVM.findPortFromURI(applicationVMRequestNotificationOutboundPortURI);
			rnop.doConnection(rnipURI, RequestNotificationConnector.class.getCanonicalName());
			
			logMessage(applicationVMURI + " connected to " + dmop.getClientPortURI());
			
			/**
			 * Lancement de l'applicationVM
			 */
			
			applicationVM.start();
			
			logMessage(applicationVMURI + " launched and ready to receive requests");
		}
		
	}
	
	@Override
	public void startDynamicStateDataPushing(int milliseconds) throws Exception {
		
		stopDynamicStateDataPushing();
		
		for (String computerDynamicDataStateOutboundPortURI : computerDynamicStateDataOutboundPortMap.values()) {
			ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(computerDynamicDataStateOutboundPortURI);
			
			cdsdop.startUnlimitedPushing(milliseconds);
		}
		
	}
	
	@Override
	public void stopDynamicStateDataPushing() throws Exception {
		
		for (String computerDynamicDataStateOutboundPortURI : computerDynamicStateDataOutboundPortMap.values()) {
			ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(computerDynamicDataStateOutboundPortURI);
			
			cdsdop.stopPushing();
		}
		
	}
	
	@Override
	public void acceptComputerStaticData(String computerURI, ComputerStaticStateI staticState) throws Exception {
		logMessage(computerURI + "submit some static data");
		computersStaticStates.put(computerURI, staticState);
	}


	@Override
	public void acceptComputerDynamicData(String computerURI, ComputerDynamicStateI currentDynamicState)
			throws Exception {
		logMessage(computerURI + "submit some dynamic data");
		computersDynamicStates.put(computerURI, currentDynamicState);
	}
	
	@Override
	public void increaseCoreFrequency(String computerURI, String processorURI, Integer coreNo) throws Exception {
		assert computerURI != null;
		assert processorURI != null;
		assert coreNo >= 0;
		
		//void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		ProcessorManagementOutboundPort pmop = (ProcessorManagementOutboundPort) this.findPortFromURI("pmop-" + processorURI);
		ProcessorIntrospectionOutboundPort piop = (ProcessorIntrospectionOutboundPort) this.findPortFromURI("piop-" + processorURI);
		ProcessorStaticState pss = (ProcessorStaticState) piop.getStaticState();
		ProcessorDynamicState pds = (ProcessorDynamicState) piop.getDynamicState();
		List<Integer> admissibleFrequencies = null;
		Integer frequency = null;
		Integer index = null;
		
		/**
		 * V�rification que le processeur demand� appartient bien � l'ordinateur
		 */
		
		if (!computerStaticState.getProcessorURIs().values().contains(processorURI))
			throw new Exception("Le processeur n'appartient pas � cet ordinateur");
		

		/**
		 * V�rifiaction que le processeur poss�de bien le num�ro de coeur souhait�
		 */
		
		if (!(coreNo < computerStaticState.getNumberOfCoresPerProcessor()))
			throw new Exception("Num�ro de coeur trop haut, le processeur n'en poss�de pas autant");
		
		/**
		 * Collecte de la fr�quence courante du coeur
		 */
		
		frequency = pds.getCurrentCoreFrequency(coreNo);

		/**
		 * Constitution de la liste tri�e des fr�quences admissible pour une augmentation 
		 * en palier de fr�quences admissibles plut�t de que tatonner � la recherche d'une fr�quence admissible
		 */
		
		admissibleFrequencies = new ArrayList<>(pss.getAdmissibleFrequencies());
		Collections.sort(admissibleFrequencies);
		
		/**
		 * Collecte de l'index de fr�quence possible de la fr�quence actuelle
		 * et tentative d'incr�mentation de cet index pour augmenter la fr�quence d'un palier
		 */
		
		index = admissibleFrequencies.indexOf(frequency);
		
		if (index == -1) 
			throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
		
		index += 1;
		
		if (index >= admissibleFrequencies.size())
			return; //result;
		
		/**
		 * Si il n'est pas possible de monter la fr�quence du coeur souhait�, alors on augmente la fr�quence de tous les coeurs
		 */
		
		if (!piop.isCurrentlyPossibleFrequencyForCore(coreNo, frequency)) {
					
			for (int i = 0; i < computerStaticState.getNumberOfCoresPerProcessor(); i++) {
				frequency = pds.getCurrentCoreFrequency(i);
				index = admissibleFrequencies.indexOf(frequency);
				
				if (index == -1) 
					throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
				
				index += 1;
				
				/**
				 * V�rification que le plafond maximum n'a pas �t� atteint
				 */
				
				if (index >= admissibleFrequencies.size())
					break;
				
				pmop.setCoreFrequency(i, admissibleFrequencies.get(index));
				
//				/**
//				 * Le coeur que nous voulons augmenter n'est pas � son maximum, donc le resultat de l'appel n'est pas une stagnation mais une augmentation
//				 */
//				
//				if (i == coreNo)
//					result = void.INCREASED;
			}
			
		} else {
			
			/**
			 * Le coeur est possible � augmenter sans craindre une diff�rence de fr�quences trop haute
			 */
			
			pmop.setCoreFrequency(coreNo, admissibleFrequencies.get(index));
//			result = void.INCREASED;
			
		}
		
//		return result;
	}
	
	@Override
	public void decreaseCoreFrequency(String computerURI, String processorURI, Integer coreNo) throws Exception {
		assert computerURI != null;
		assert processorURI != null;
		assert coreNo >= 0;
		
//		void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		ProcessorManagementOutboundPort pmop = (ProcessorManagementOutboundPort) this.findPortFromURI("pmop-" + processorURI);
		ProcessorIntrospectionOutboundPort piop = (ProcessorIntrospectionOutboundPort) this.findPortFromURI("piop-" + processorURI);
		ProcessorStaticState pss = (ProcessorStaticState) piop.getStaticState();
		ProcessorDynamicState pds = (ProcessorDynamicState) piop.getDynamicState();
		List<Integer> admissibleFrequencies = null;
		Integer frequency = null;
		Integer index = null;
		
		/**
		 * V�rification que le processeur demand� appartient bien � l'ordinateur
		 */
		
		if (!computerStaticState.getProcessorURIs().values().contains(processorURI))
			throw new Exception("Le processeur n'appartient pas � cet ordinateur");
		

		/**
		 * V�rifiaction que le processeur poss�de bien le num�ro de coeur souhait�
		 */
		
		if (!(coreNo < computerStaticState.getNumberOfCoresPerProcessor()))
			throw new Exception("Num�ro de coeur trop haut, le processeur n'en poss�de pas autant");
		
		/**
		 * Collecte de la fr�quence courante du coeur
		 */
		
		frequency = pds.getCurrentCoreFrequency(coreNo);

		/**
		 * Constitution de la liste tri�e des fr�quences admissible pour une augmentation 
		 * en palier de fr�quences admissibles plut�t de que tatonner � la recherche d'une fr�quence admissible
		 */
		
		admissibleFrequencies = new ArrayList<>(pss.getAdmissibleFrequencies());
		Collections.sort(admissibleFrequencies);
		
		/**
		 * Collecte de l'index de fr�quence possible de la fr�quence actuelle
		 * et tentative d'incr�mentation de cet index pour diminuer la fr�quence d'un palier
		 */
		
		index = admissibleFrequencies.indexOf(frequency);
		
		if (index == -1) 
			throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
		
		index -= 1;
		
		if (index < 0)
			return; //result;
		
		/**
		 * Si il n'est pas possible de diminuer la fr�quence du coeur souhait�, alors on diminue la fr�quence de tous les coeurs
		 */
		
		if (!piop.isCurrentlyPossibleFrequencyForCore(coreNo, frequency)) {
					
			for (int i = 0; i < computerStaticState.getNumberOfCoresPerProcessor(); i++) {
				frequency = pds.getCurrentCoreFrequency(i);
				index = admissibleFrequencies.indexOf(frequency);
				
				if (index == -1) 
					throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
				
				index -= 1;
				
				/**
				 * V�rification que le seuil minimum n'a pas �t� atteint
				 */
				
				if (index < 0)
					break;
				
				pmop.setCoreFrequency(i, admissibleFrequencies.get(index));
				
//				/**
//				 * Le coeur que nous voulons diminuer n'est pas � son minimum, donc le resultat de l'appel n'est pas une stagnation mais une diminution
//				 */
//				
//				if (i == coreNo)
//					result = void.DECREASED;
			}
			
		} else {
			
			/**
			 * Le coeur est possible � diminuer sans craindre une diff�rence de fr�quences trop haute
			 */
			
			pmop.setCoreFrequency(coreNo, admissibleFrequencies.get(index));
//			result = void.DECREASED;
			
		}
		
//		return result;
	}

	@Override
	public void increaseProcessorFrenquency(String computerURI, String processorURI) throws Exception {
		assert computerURI != null;
		assert processorURI != null;
		
//		void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		ProcessorManagementOutboundPort pmop = (ProcessorManagementOutboundPort) this.findPortFromURI("pmop-" + processorURI);
		ProcessorIntrospectionOutboundPort piop = (ProcessorIntrospectionOutboundPort) this.findPortFromURI("piop-" + processorURI);
		ProcessorStaticState pss = (ProcessorStaticState) piop.getStaticState();
		ProcessorDynamicState pds = (ProcessorDynamicState) piop.getDynamicState();
		List<Integer> admissibleFrequencies = null;
		Integer frequency = null;
		Integer index = null;
		
		/**
		 * Constitution de la liste tri�e des fr�quences admissible pour une augmentation 
		 * en palier de fr�quences admissibles plut�t de que tatonner � la recherche d'une fr�quence admissible
		 */
		
		admissibleFrequencies = new ArrayList<>(pss.getAdmissibleFrequencies());
		Collections.sort(admissibleFrequencies);
		
		for (int i = 0; i < computerStaticState.getNumberOfCoresPerProcessor(); i++) {
			frequency = pds.getCurrentCoreFrequency(i);
			index = admissibleFrequencies.indexOf(frequency);
			
			if (index == -1) 
				throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
			
			index += 1;
			
			/**
			 * V�rification que le seuil maximum n'a pas �t� atteint
			 */
			
			if (index >= admissibleFrequencies.size())
				break;
			
			pmop.setCoreFrequency(i, admissibleFrequencies.get(index));
//			result = void.INCREASED;
		}
		
//		return result;
	}

	@Override
	public void decreaseProcessorFrenquency(String computerURI, String processorURI) throws Exception {
		assert computerURI != null;
		assert processorURI != null;
		
//		void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		ProcessorManagementOutboundPort pmop = (ProcessorManagementOutboundPort) this.findPortFromURI("pmop-" + processorURI);
		ProcessorIntrospectionOutboundPort piop = (ProcessorIntrospectionOutboundPort) this.findPortFromURI("piop-" + processorURI);
		ProcessorStaticState pss = (ProcessorStaticState) piop.getStaticState();
		ProcessorDynamicState pds = (ProcessorDynamicState) piop.getDynamicState();
		List<Integer> admissibleFrequencies = null;
		Integer frequency = null;
		Integer index = null;
		
		/**
		 * Constitution de la liste tri�e des fr�quences admissible pour une augmentation 
		 * en palier de fr�quences admissibles plut�t de que tatonner � la recherche d'une fr�quence admissible
		 */
		
		admissibleFrequencies = new ArrayList<>(pss.getAdmissibleFrequencies());
		Collections.sort(admissibleFrequencies);
		
		for (int i = 0; i < computerStaticState.getNumberOfCoresPerProcessor(); i++) {
			frequency = pds.getCurrentCoreFrequency(i);
			index = admissibleFrequencies.indexOf(frequency);
			
			if (index == -1) 
				throw new Exception("La fr�quence n'a pas �t� trouv�e parmis les fr�quences admissibles");
			
			index -= 1;
			
			/**
			 * V�rification que le seuil minimum n'a pas �t� atteint
			 */
			
			if (index < 0)
				break;
			
			pmop.setCoreFrequency(i, admissibleFrequencies.get(index));
//			result = void.DECREASED;
		}
		
//		return result;
	}

	@Override
	public void increaseProcessorsFrenquencies(String computerURI) throws Exception {		
		assert computerURI != null;

//		void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		
		for (String processorURI : computerStaticState.getProcessorURIs().values()) {
//			void variation = 
					increaseProcessorFrenquency(computerURI, processorURI);
//			if (variation != void.STAGNATED)
//				result = variation;
		}
		
//		return result;
	}

	@Override
	public void decreaseProcessorsFrenquencies(String computerURI) throws Exception {
		assert computerURI != null;

//		void result = void.STAGNATED;
		ComputerStaticStateI computerStaticState = computersStaticStates.get(computerURI);
		
		for (String processorURI : computerStaticState.getProcessorURIs().values()) {
//			void variation = 
					decreaseProcessorFrenquency(computerURI, processorURI);
//			if (variation != void.STAGNATED)
//				result = variation;
		}
		
//		return result;
	}

	@Override
	public void allocateCores(String computerURI, String avmURI, int cores) throws Exception {
		assert computerURI != null;
		assert avmURI != null;
		assert cores > 0;
		
//		void result = void.STAGNATED;
		
		if (hasAvailableCoresFromComputer(computerURI, cores)) {
			
		}
		
//		return result;
	}

	@Override
	public void releaseCores(String compterURI, String avmURI, int cores) throws Exception {
		
	}

//	@Override
//	public void increaseAVMs(String dispatcherURI) {
//		
//	}
//
//	@Override
//	public void decreaseAVMs(String dispatcherURI) {
//
//	}

	/**************************************************
	 * 				REFACTORING METHODS
	 **************************************************/
	
	/**
	 * Retourne le nombre de coeurs libre pour l'allocation au sein d'un ordinateur
	 * 
	 * @param computerURI
	 * @return
	 * @throws Exception
	 */
	
	private int computerAvailableCores(String computerURI) throws Exception {
		ComputerDynamicStateDataOutboundPort cdsdop = (ComputerDynamicStateDataOutboundPort) findPortFromURI(computerDynamicStateDataOutboundPortMap.get(computerURI));
		ComputerDynamicState data = (ComputerDynamicState) cdsdop.request();
		boolean[][] cr = data.getCurrentCoreReservations();
		
		int computerAvailableCores = 0;
		
		for (int pi = 0; pi < cr.length; pi++)			
			computerAvailableCores += processorAvailableCores(cr[pi]);
		
		return computerAvailableCores;
	}
	
	/**
	 * Retourne le nombre de coeurs libre au sein d'un processeur
	 * 
	 * @param coreReservations
	 * @return
	 * @throws Exception
	 */
	
	private int processorAvailableCores(boolean[] coreReservations) throws Exception {
		int processorAvailableCores = 0;
		
		for (int ci = 0; ci < coreReservations.length; ci++)
			if (coreReservations[ci])
				processorAvailableCores++;
		
		return processorAvailableCores;
	}
	
	/**
	 * Retourne vrai si l'ordinateur cible poss�de le montant de coeurs libre voulu
	 * 
	 * @param computerURI
	 * @param wanted
	 * @return
	 * @throws Exception
	 */
	
	private boolean hasAvailableCoresFromComputer(String computerURI, int wanted) throws Exception {
		return !(wanted < computerAvailableCores(computerURI));
	}

	/**
	 * Retourne l'URI du premier ordinateur trouv� disponible pour une allocation d'AVM avec le nombre de coeurs souhait�s,
	 * null si aucun ordinateur ne poss�de de coeurs de libres
	 * 
	 * @param cores
	 * @return
	 * @throws Exception
	 */
	
	private String findAvailableComputerForApplicationVMAllocation(int cores) throws Exception {
		assert computerServicesOutboundPortMap.size() > 0;
		
		String computerURI = null;
		List<String> computerURIs = new ArrayList<>(computerServicesOutboundPortMap.keySet());
		
		for (String cURI : computerURIs) {
			if (hasAvailableCoresFromComputer(cURI, cores)) {
				computerURI = cURI;
				break;
			}
		}
		
		return computerURI;
	}
	
	/**
	 * Retourne l'URI du premier ordinateur trouv� disponible (avec un coeur de disponible) pour une allocation d'AVM,
	 * null si aucun ordinateur ne poss�de de coeurs de libres pour une AVM
	 * 
	 * @return
	 */
	
	private String findAvailableComputerForApplicationVMAllocation() throws Exception {
		return findAvailableComputerForApplicationVMAllocation(1);
	}
	
	/**
	 * Tente d'allouer un maximum de coeurs.
	 * Un tableau vide est retourn� si aucun coeur n'est disponible
	 * 
	 * @param computerURI
	 * @param wantedCores
	 * @return
	 * @throws Exception
	 */
	
	private AllocatedCore[] tryToAllocateCoresOn(String computerURI, int wantedCores) throws Exception {
		assert computerURI != null;
		assert wantedCores > 0;
				
		ComputerServicesOutboundPort csop = (ComputerServicesOutboundPort) findPortFromURI(computerServicesOutboundPortMap.get(computerURI));
		AllocatedCore[] cores = csop.allocateCores(wantedCores);
		
		if (cores.length == wantedCores)
			logMessage("(" + cores.length + "/" + wantedCores + ") Amount of wanted cores successfully found on " + computerURI);
		else
			logMessage("(" + cores.length + "/" + wantedCores + ") Amount of wanted cores unfortunately not found on " + computerURI);
		
		return cores;
	}
	
	/**
	 * Tente d'allouer un coeur sur l'ordinateur cible
	 * 
	 * @param computerURI
	 * @return
	 * @throws Exception
	 */
	
	private AllocatedCore[] tryToAllocateCoreOn(String computerURI) throws Exception {
		return tryToAllocateCoresOn(computerURI, 1);
	}
}
