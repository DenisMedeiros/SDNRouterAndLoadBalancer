package edu.wisc.cs.sdn.apps.l3routing;

import net.floodlightcontroller.core.IOFSwitch;

public class BellFordVertex {
	
	private IOFSwitch sw;
	
	private int cost;
	
	private IOFSwitch nextHop;
	
	private int outPort;
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex() {
		this.sw =null;
		this.cost = -1;
		this.nextHop = null;
	}
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex(IOFSwitch sw, int cost, IOFSwitch nextHop) {
		this.sw = sw;
		this.cost = cost;
		this.nextHop = nextHop;
	}
	
	public IOFSwitch getSwitch() {
		return this.sw;
	}
	
	public void setSwitch(IOFSwitch sw) {
		this.sw = sw;
	}
	
	public int getCost() {
		return this.cost;
	}
	
	public void setCost(int cost) {
		this.cost = cost;
	}
	
	public IOFSwitch getNextHop() {
		return this.nextHop;
	}
	
	public void setNextHop(IOFSwitch nextHop) {
		this.nextHop = nextHop;
	}
	
	public int getOutPort() {
		return this.outPort;
	}
	
	public void setOutPort(int outPort) {
		this.cost = outPort;
	}	
	

}
