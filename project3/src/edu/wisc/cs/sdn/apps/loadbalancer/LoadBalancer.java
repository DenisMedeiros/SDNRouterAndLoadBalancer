package edu.wisc.cs.sdn.apps.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.l3routing.IL3Routing;
import edu.wisc.cs.sdn.apps.util.ArpServer;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener,
IOFMessageListener
{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();

	private static final byte TCP_FLAG_SYN = 0x02;

	private static final byte TCP_FLAG_RESET = 0x04;

	private static final short IDLE_TIMEOUT = 20;

	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;

	// Interface to device manager service
	private IDeviceService deviceProv;

	// Interface to L3Routing application
	private IL3Routing l3RoutingApp;

	// Switch table in which rules should be installed
	private byte table;

	// Set of virtual IPs and the load balancer instances they correspond with
	private Map<Integer,LoadBalancerInstance> instances;

	/**
	 * Loads dependencies and initializes data structures.
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
			{
		log.info(String.format("Initializing %s...", MODULE_NAME));

		// Obtain table number from config
		Map<String,String> config = context.getConfigParams(this);
		this.table = Byte.parseByte(config.get("table"));

		// Create instances from config
		this.instances = new HashMap<Integer,LoadBalancerInstance>();
		String[] instanceConfigs = config.get("instances").split(";");
		for (String instanceConfig : instanceConfigs)
		{
			String[] configItems = instanceConfig.split(" ");
			if (configItems.length != 3)
			{ 
				log.error("Ignoring bad instance config: " + instanceConfig);
				continue;
			}
			LoadBalancerInstance instance = new LoadBalancerInstance(
					configItems[0], configItems[1], configItems[2].split(","));
			this.instances.put(instance.getVirtualIP(), instance);
			log.info("Added load balancer instance: " + instance);
		}

		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);
		this.l3RoutingApp = context.getServiceImpl(IL3Routing.class);

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
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);

		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */

		/*********************************************************************/
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

		/*********************************************************************/
		/* TODO: Install rules to send:                                      */
		/*       (1) packets from new connections to each virtual load       */
		/*       balancer IP to the controller                               */
		/*       (2) ARP packets to the controller, and                      */
		/*       (3) all other packets to the next rule table in the switch  */
		/*********************************************************************/


		// Installing the rules in the switch

		OFActionOutput action;
		List<OFAction> actionList;
		OFInstructionApplyActions instructions;
		OFMatch matchCriteria;
		List<OFInstruction> instructionsList;

		// If the packet is address to the virtual IP, send it to the controller.

		Iterator<LoadBalancerInstance> iteratorLoadBalancer = this.instances.values().iterator();

		while(iteratorLoadBalancer.hasNext()) { // Iterate over all instances (each has one virtual IP).

			LoadBalancerInstance loadBalancer = (LoadBalancerInstance)iteratorLoadBalancer.next();

			action = new OFActionOutput();
			action.setPort(OFPort.OFPP_CONTROLLER);

			actionList = new ArrayList<OFAction>();
			actionList.add(action);

			instructions = new OFInstructionApplyActions();
			instructions.setActions(actionList);


			matchCriteria = new OFMatch();

			matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, loadBalancer.getVirtualIP()); 
			matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);	

			instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList));


			SwitchCommands.installRule(sw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 1),
					matchCriteria, instructionsList);


			// If the packet is an ARP request, send it to the controller.


			action = new OFActionOutput();
			action.setPort(OFPort.OFPP_CONTROLLER);

			actionList = new ArrayList<OFAction>();
			actionList.add(action);

			instructions = new OFInstructionApplyActions();
			instructions.setActions(actionList);


			matchCriteria = new OFMatch();

			matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_ARP);
			matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_ARP, loadBalancer.getVirtualIP()); 


			instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList));

			SwitchCommands.installRule(sw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 1),
					matchCriteria, instructionsList);


		}


		// Finally, other kind of packets should go to the level-3 routing table.

		matchCriteria = new OFMatch(); // A blank match should work as a wildcard.

		instructionsList = Arrays.asList((OFInstruction)new OFInstructionGotoTable().setTableId(l3RoutingApp.getTable()));

		SwitchCommands.installRule(sw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 0),
				matchCriteria, instructionsList);


	}

	/**
	 * Handle incoming packets sent from switches.
	 * @param sw switch on which the packet was received
	 * @param msg message from the switch
	 * @param cntx the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) 
	{
		// We're only interested in packet-in messages
		if (msg.getType() != OFType.PACKET_IN)
		{ return Command.CONTINUE; }
		OFPacketIn pktIn = (OFPacketIn)msg;
		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0,
				pktIn.getPacketData().length);

		// Case 1: Arp Request
		if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {

			ARP arpPacket = (ARP)ethPkt.getPayload();


			// Check if arpPacket is a request

			if (arpPacket.getOpCode() == ARP.OP_REQUEST) {

				// send ARP reply 

				int virtualIP = IPv4.toIPv4Address(arpPacket.getTargetProtocolAddress());
				byte[] virtualMAC = instances.get(virtualIP).getVirtualMAC();


				//this.getHostMACAddress(virtualIP);

				if (virtualMAC == null) {
					// TODO: error, mac not found
				}

				// reconstruct ARP packet

				arpPacket.setOpCode(ARP.OP_REPLY);
				arpPacket.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
				arpPacket.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());
				arpPacket.setSenderHardwareAddress(virtualMAC);
				arpPacket.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(virtualIP));


				// reset ethernet fields for source and destination MAC addresses

				ethPkt.setPayload(arpPacket);

				ethPkt.setDestinationMACAddress(ethPkt.getSourceMACAddress());
				ethPkt.setSourceMACAddress(virtualMAC);


				// send packet

				SwitchCommands.sendPacket(sw, (short) pktIn.getInPort(), ethPkt);
			}

			// Case 2: TCP, not a syn request

		} else if  (ethPkt.getEtherType() == Ethernet.TYPE_IPv4) {

			IPv4 ipPacket = null;
			ipPacket = (IPv4)ethPkt.getPayload();

			// Check whether Packet is of type TCP

			if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP) {


				TCP tcpPacket = (TCP) ipPacket.getPayload();

				if (tcpPacket.getFlags() != TCP_FLAG_SYN) {

					System.out.println("@@@@@@@@@@@@ Received a NON-SIN! = " + tcpPacket.getFlags());

					// set reset Flags
					tcpPacket.setFlags((short) TCP_FLAG_RESET);
					tcpPacket.setDestinationPort(tcpPacket.getSourcePort());
					tcpPacket.setSourcePort((short) pktIn.getInPort());


					// reset ethernet fields for source and destination MAC addresses

					ethPkt.setSourceMACAddress(ethPkt.getDestinationMACAddress());
					ethPkt.setDestinationMACAddress(ethPkt.getSourceMACAddress());


					// send packet
					SwitchCommands.sendPacket(sw, (short) pktIn.getInPort(), ethPkt);

				} else {

					// TCP packet is of type SYN, connection is initializing.

					// Get the virtual IP and MAC addresses of the current load balancer.



					int virtualIP = ipPacket.getDestinationAddress();

					LoadBalancerInstance loadBalancer = this.instances.get(virtualIP);
					byte[] virtualMAC = loadBalancer.getVirtualMAC();

					System.out.println("@@@@@@@@@@@@ Received a SIN!");


					// Get the real IP and MAC from one host of the load balancer.

					int hostIP = loadBalancer.getNextHostIP();
					byte[] hostMAC = this.getHostMACAddress(hostIP);

					// Modify the packet coming from the client to the virtual IP.


					OFActionSetField dstMAC = new OFActionSetField(OFOXMFieldType.ETH_DST, hostMAC);
					OFActionSetField dstIP = new OFActionSetField(OFOXMFieldType.IPV4_DST, hostIP);

					OFActionSetField srcMAC = new OFActionSetField(OFOXMFieldType.ETH_SRC, virtualMAC);
					OFActionSetField srcIP = new OFActionSetField(OFOXMFieldType.IPV4_SRC, virtualIP);

					// Set up match criteria for source address of client IP.

					OFMatch matchCriteria = new OFMatch();

					matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
					matchCriteria.setNetworkSource(OFMatch.ETH_TYPE_IPV4, ipPacket.getSourceAddress());
					matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, virtualIP);
					matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);	
					matchCriteria.setTransportSource(OFMatch.IP_PROTO_TCP, tcpPacket.getSourcePort());
					matchCriteria.setTransportDestination(OFMatch.IP_PROTO_TCP, tcpPacket.getDestinationPort());
					

					// Set up action list of fields.

					List<OFAction> actionList = new ArrayList<OFAction>();
					actionList.add(dstMAC);
					actionList.add(dstIP);
					actionList.add(srcMAC);
					actionList.add(srcIP);


					List<OFInstruction> instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList),
							new OFInstructionGotoTable().setTableId(l3RoutingApp.getTable())	
							);

					SwitchCommands.installRule(sw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 2),
							matchCriteria, instructionsList, (short) 0, IDLE_TIMEOUT);


					Iterator<IOFSwitch> iteratorSwitch = this.floodlightProv.getAllSwitchMap().values().iterator();

					while(iteratorSwitch.hasNext()) {

						IOFSwitch switchElement = (IOFSwitch)iteratorSwitch.next();

						// Modify the packet coming from the real host (hostIP) to the virtualIP

						dstIP = new OFActionSetField(OFOXMFieldType.IPV4_DST, ipPacket.getSourceAddress());
						dstMAC = new OFActionSetField(OFOXMFieldType.ETH_DST, ethPkt.getSourceMAC().toBytes());

						srcMAC = new OFActionSetField(OFOXMFieldType.ETH_SRC, loadBalancer.getVirtualMAC());
						srcIP = new OFActionSetField(OFOXMFieldType.IPV4_SRC, loadBalancer.getVirtualIP());

						matchCriteria = new OFMatch();

						matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
						matchCriteria.setNetworkSource(OFMatch.ETH_TYPE_IPV4, hostIP);
						matchCriteria.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, virtualIP);
						matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
						matchCriteria.setTransportSource(OFMatch.IP_PROTO_TCP, tcpPacket.getDestinationPort());
						matchCriteria.setTransportDestination(OFMatch.IP_PROTO_TCP, tcpPacket.getSourcePort());
						


						// set up action list of fields
						actionList = new ArrayList<OFAction>();
						actionList.add(dstMAC);
						actionList.add(dstIP);
						actionList.add(srcMAC);
						actionList.add(srcIP);

						instructionsList = Arrays.asList((OFInstruction)new OFInstructionApplyActions().setActions(actionList),
								new OFInstructionGotoTable().setTableId(l3RoutingApp.getTable())	
								);



						SwitchCommands.installRule(switchElement, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 2),
								matchCriteria, instructionsList, (short) 0, IDLE_TIMEOUT);

					}

				}


			}
		}




		return Command.CONTINUE;
	}

	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * @param hostIPAddress the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
	{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(
				null, null, hostIPAddress, null, null);
		if (!iterator.hasNext())
		{ return null; }
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{ /* Nothing we need to do, since the switch is no longer active */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

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
	{ /* Nothing we need to do, since load balancer rules are port-agnostic */}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }

	/**
	 * Tell the module system which services we provide.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
	 * Tell the module system which services we implement.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
	getServiceImpls() 
	{ return null; }

	/**
	 * Tell the module system which modules we depend on.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
	getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
				new ArrayList<Class<? extends IFloodlightService>>();
		floodlightService.add(IFloodlightProviderService.class);
		floodlightService.add(IDeviceService.class);
		return floodlightService;
	}

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) 
	{
		return (OFType.PACKET_IN == type 
				&& (name.equals(ArpServer.MODULE_NAME) 
						|| name.equals(DeviceManagerImpl.MODULE_NAME))); 
	}

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) 
	{ return false; }
}
