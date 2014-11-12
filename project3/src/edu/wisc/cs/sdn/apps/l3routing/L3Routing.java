package edu.wisc.cs.sdn.apps.l3routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;


import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener, 
ILinkDiscoveryListener, IDeviceListener, IL3Routing
{
	public static final String MODULE_NAME = L3Routing.class.getSimpleName();

	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;

	// Interface to link discovery service
	private ILinkDiscoveryService linkDiscProv;

	// Interface to device manager service
	private IDeviceService deviceProv;

	// Switch table in which rules should be installed
	private byte table;

	// Map of hosts to devices
	private Map<IDevice,Host> knownHosts;

	private int INFINITY = 9999999;
	/**
	 * Loads dependencies and initializes data structures.
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
			{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
		this.table = Byte.parseByte(config.get("table"));

		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
		this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);

		this.knownHosts = new ConcurrentHashMap<IDevice,Host>();

		/*********************************************************************/
		/* TODO: Initialize other class variables, if necessary              */

		/*********************************************************************/
			}

	/**
	 * Subscribes to events and performs other startup tasks.
	 */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
			{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);

		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */

		/*********************************************************************/
			}

	/**
	 * Get the table in which this application installs rules.
	 */
	public byte getTable()
	{ return this.table; }

	/**
	 * Get a list of all known hosts in the network.
	 */
	private Collection<Host> getHosts()
	{ return this.knownHosts.values(); }

	/**
	 * Get a map of all active switches in the network. Switch DPID is used as
	 * the key.
	 */
	private Map<Long, IOFSwitch> getSwitches()
	{ return floodlightProv.getAllSwitchMap(); }

	/**
	 * Get a list of all active links in the network.
	 */
	private Collection<Link> getLinks()
	{ return linkDiscProv.getLinks().keySet(); }

	/**
	 * Event handler called when a host joins the network.
	 * @param device information about the host
	 */
	@Override
	public void deviceAdded(IDevice device) 
	{

		Host host = new Host(device, this.floodlightProv);

		//this.getHosts().add(host); We cannot do that!

		if(host.getIPv4Address() != null) {
			this.knownHosts.put(device, host); // Add only hosts with valid IP address.
		}

		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);

			// do calculations for 1 new host
			bellmanFord(host);
		}
	}

	/**
	 * Event handler called when a host is no longer attached to a switch.
	 * @param device information about the host
	 */
	@Override
	public void deviceRemoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);

		// remove device
		this.getHosts().remove(host);

		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}

		log.info(String.format("Host %s is no longer attached to a switch", 
				host.getName()));

		removeHost(host);


		// TODO
		// Should the paths be recalculated here?

	}

	/**
	 * Event handler called when a host moves within the network.
	 * @param device information about the host
	 */
	@Override
	public void deviceMoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}

		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));

		// remove host from all tables
		removeHost(host);

		// redo for this host
		bellmanFord(host);
	}

	/**
	 * Event handler called when a switch joins the network.
	 * @param DPID for the switch
	 */
	@Override		
	public void switchAdded(long switchId) 
	{

		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

		// remove switch from list 
		this.getSwitches().put(sw.getId(), sw);

		// remove information of all hosts from tables
		for (Host host : this.getHosts()) {
			removeHost(host);
		}

		// recalculate for all hosts
		bellmanFord();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);

		log.info(String.format("Switch s%d removed", switchId));

		// remove switch from list 
		this.getSwitches().remove(sw);

		// remove information of all hosts from tables
		for (Host host : this.getHosts()) {
			removeHost(host);
		}

		// recalculate for all hosts
		bellmanFord();
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) 
	{
		


		for (LDUpdate update : updateList)
		{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if (0 == update.getDst() || 0 == update.getSrc())
			{
				log.info(String.format("Link s%s:%d -> host updated", 
						update.getSrc(), update.getSrcPort()));

			}

			// Otherwise, the link is between two switches. Remove the link
			else
			{
				log.info(String.format("Link s%s:%d -> %s:%d updated", 
						update.getSrc(), update.getSrcPort(),
						update.getDst(), update.getDstPort()));

				for (Link link: this.getLinks()) {
					if (link.getDstPort() == update.getSrc() || link.getSrcPort() == update.getDst()) {
						// remove link from link list
						this.getLinks().remove(link);
						break;

					}
				}

			}
		}


		// recalculate for all hosts
		bellmanFord();


	}

	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update) 
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }

	/**
	 * Event handler called when the IP address of a host changes.
	 * @param device information about the host
	 */
	@Override
	public void deviceIPV4AddrChanged(IDevice device) 
	{ this.deviceAdded(device); }

	/**
	 * Event handler called when the VLAN of a host changes.
	 * @param device information about the host
	 */
	@Override
	public void deviceVlanChanged(IDevice device) 
	{ /* Nothing we need to do, since we're not using VLANs */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId) 
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) 
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) 
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) 
	{ return false; }

	/**
	 * Tell the module system which services we provide.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{
		Collection<Class<? extends IFloodlightService>> services =
				new ArrayList<Class<? extends IFloodlightService>>();
		services.add(IL3Routing.class);
		return services; 
	}

	/**
	 * Tell the module system which services we implement.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
	getServiceImpls() 
	{ 
		Map<Class<? extends IFloodlightService>, IFloodlightService> services =
				new HashMap<Class<? extends IFloodlightService>, 
				IFloodlightService>();
		// We are the class that implements the service
		services.put(IL3Routing.class, this);
		return services;
	}

	/**
	 * Tell the module system which modules we depend on.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
	getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> modules =
				new ArrayList<Class<? extends IFloodlightService>>();
		modules.add(IFloodlightProviderService.class);
		modules.add(ILinkDiscoveryService.class);
		modules.add(IDeviceService.class);
		return modules;
	}

	private void bellmanFord() {

		for(Host host : this.getHosts()) {

			bellmanFord(host);

		}

	}

	private void bellmanFord(Host dstHost) {

		// Initially, add all switches to a switch list.
		Map<Long, BellFordVertex> switches = new HashMap<Long, BellFordVertex>();

		// This is the switch that the host is plugged.
		IOFSwitch dstSw = dstHost.getSwitch();
		
		

		// Set all costs to infinity, except for the source which is set to 0.

		for (IOFSwitch sw : this.getSwitches().values()) {
			BellFordVertex tempVertex = new BellFordVertex();
			if (sw.equals(dstSw)) {
				tempVertex = new BellFordVertex(sw, 0);
			}else{
				tempVertex = new BellFordVertex(sw, INFINITY);		
			}
			switches.put(sw.getId(), tempVertex);
		}

		// Relax the weights. 

		for (int i = 1; i < switches.size(); i++) { // We need more one iteration instead of size - 1.
			

			ArrayList<Link> allLinks = new ArrayList<Link>(this.getLinks());

			Iterator<Link> iteratorLink = allLinks.iterator();

			while(iteratorLink.hasNext()) {

				Link link = (Link)iteratorLink.next();

				BellFordVertex vertexU = switches.get(link.getSrc());
				BellFordVertex vertexV = switches.get(link.getDst());

				if (vertexU.getCost() + 1 < vertexV.getCost()) {

					vertexV.setOutPort(link.getDstPort());
					vertexV.setCost(vertexU.getCost() + 1);
					
					
				}

			}

		}


		// Installing the rules based on the previous result.	

		for (BellFordVertex srcSw: switches.values()) {
			
			OFActionOutput action;
			List<OFAction> actionList;
			OFInstructionApplyActions instructions;
			OFMatch matchCriteria;
			List<OFInstruction> instructionsList;
			
			if (srcSw.getCost() == INFINITY) {
				
				
				// host not reachable. send ICMP message


				matchCriteria = new OFMatch();
				
				matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				matchCriteria.setNetworkDestination(dstHost.getIPv4Address());
				
				SwitchCommands.removeRules(srcSw.getSwitch(), this.table, matchCriteria);
				
				log.info(String.format("Removing rule for unreachable host", dstSw.getId(), dstHost.getIPv4Address()));
				
			} else {
	
				// If the switch is the final in the path, then redirect the packets to
				// the port where the host is connected.
	
				if (srcSw.getSwitch().equals(dstSw)) {
	
					action = new OFActionOutput();
					action.setPort(dstHost.getPort());
	
					actionList = new ArrayList<OFAction>();
					actionList.add(action);
	
					instructions = new OFInstructionApplyActions();
					instructions.setActions(actionList);
	
					matchCriteria = new OFMatch();
	
					matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
					matchCriteria.setNetworkDestination(dstHost.getIPv4Address());
	
					instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList));
	
	
					SwitchCommands.installRule(dstSw, this.table, SwitchCommands.DEFAULT_PRIORITY,
							matchCriteria, instructionsList);
	
					// If the switch is not the final in the path, then forward the packets to the next hop.	
					
					log.info(String.format("Adding rule for switch ID %d for IP host %d. This switch is connected to the host", dstSw.getId(), dstHost.getIPv4Address()));
	
				} else {
	
					// If the switch is not the final in the path, then forward the packets to the next hop.	
	
					// create a new action with appropriate outgoing port
					action = new OFActionOutput();
					action.setPort(srcSw.getOutPort());
	
					// add action to the list of instructions
					actionList = new ArrayList<OFAction>();
					actionList.add(action);
	
					instructions = new OFInstructionApplyActions();
					instructions.setActions(actionList);
	
					// Construct IP packet
					matchCriteria = new OFMatch();
	
					matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
					matchCriteria.setNetworkDestination(dstHost.getIPv4Address());
	
					instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList));
	
					SwitchCommands.installRule(srcSw.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY,
							matchCriteria, instructionsList);
	
	
					log.info(String.format("Adding rule for switch ID %d for IP host %d. This switch ISNOT connected to the host", dstSw.getId(), dstHost.getIPv4Address()));
	
				}
			}
		}

	}

	private void removeHost(Host host) {

		// set up match information to be used for removal
		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(host.getIPv4Address());

		// remove host from all switches
		for (IOFSwitch swtch: this.getSwitches().values()) {

			SwitchCommands.removeRules(swtch, this.table, matchCriteria);

		}
	}


}
