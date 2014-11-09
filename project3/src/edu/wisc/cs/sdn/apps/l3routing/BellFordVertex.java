package edu.wisc.cs.sdn.apps.l3routing;

import net.floodlightcontroller.core.IOFSwitch;

public class BellFordVertex {
	
	private IOFSwitch sw;
	
	private int cost;
	
	private int outPort;
	
	private BellFordVertex nextHop;
	
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex() {
		this.sw =null;
		this.cost = -1;
		this.nextHop = this;
	}
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex(IOFSwitch sw, int cost) {
		this.sw = sw;
		this.cost = cost;
		this.nextHop = this;
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
	
	public int getOutPort() {
		return this.outPort;
	}
	
	public void setOutPort(int outPort) {
		this.outPort = outPort;
	}

	public BellFordVertex getNextHop() {
		return nextHop;
	}

	public void setNextHop(BellFordVertex nextHop) {
		this.nextHop = nextHop;
	}	
	
	

}
