package edu.wisc.cs.sdn.apps.l3routing;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.IOFSwitch;

public class BellFordVertex {
	
	private IOFSwitch sw;
	
	private int cost;
	
	private int outPort;
	
	// keeps the outgoing port to each neighbor
	private  Map<Integer, BellFordVertex> neighbors;
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex() {
		this.sw =null;
		this.cost = -1;
		this.neighbors = new HashMap<Integer, BellFordVertex>();
	}
	
	// Constructors for Bellman Ford Vertex
	public BellFordVertex(IOFSwitch sw, int cost) {
		this.sw = sw;
		this.cost = cost;
		this.neighbors = new HashMap<Integer, BellFordVertex>();
	}
	
	
	public Map<Integer, BellFordVertex> getNeighbors(){
		return this.neighbors;
	}
	
	public void addNeighbor(int port, BellFordVertex sw) {
		neighbors.put(port, sw);
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
		this.cost = outPort;
	}	
	
	

}
