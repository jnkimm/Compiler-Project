package compiler;

import java.util.ArrayList;
import java.util.BitSet;

public class regInterferenceGraph{
	public FunSymbol function;
	public ArrayList<liveVars> liveVariables;
	public ArrayList<regInterferenceNode> nodes = new ArrayList<regInterferenceNode>();
	public int start;
	public int end;
	
	public regInterferenceGraph(FunSymbol func, int start, int end) {
		this.start = start;
		this.end = end;
		function = func;
		liveVariables = new ArrayList<liveVars>();
	}
	
	public regInterferenceGraph(int start, int end) {
		this.start = start;
		this.end = end;
		function = null;
		liveVariables = null;
	}
	
	public void add(String name, int index, int length) {
		for (regInterferenceNode node: nodes) {
			if (node.var.equals(name)) {
				return;
			}
		}
		nodes.add(new regInterferenceNode(index,name,length));
		return;
	}
	
	public void interfere(liveVars lives, Boolean func) {
		for (int i = 0; i < lives.vars.size(); i++) {
			if (lives.vars.get(i)) {
				regInterferenceNode currNode = getNode(i);
				if (func && currNode == null) {
					continue;
				}
				currNode.connectedVars.or(lives.vars);
			}
		}
	}
	
	public regInterferenceNode getNode(int index) {
		for (regInterferenceNode node:nodes) {
			if (node.bitSetIndex == index) {
				return node;
			}
		}
		return null;
	}
	
	public void printRIGraph() {
		for (regInterferenceNode node:nodes) {
//			StringBuilder s = new StringBuilder();
//            for( int i = 0; i < node.connectedVars.length();  i++ )
//            {
//                s.append( node.connectedVars.get( i ) == true ? 1: 0 );
//            }
			if (node.inGraph) {
				System.out.print(node.var+"("+node.bitSetIndex+"): ");
				System.out.println(node.coloringVars);
			}
		}
		System.out.println();
		System.out.println();
	}
	
	public void startGraphColoring() {
		for (regInterferenceNode node:nodes) {
			node.coloringVars = (BitSet)node.connectedVars.clone();
			node.coloringVars.flip(node.bitSetIndex);
		}
	}
	
	public void removeVar(int index) {
		for (regInterferenceNode node:nodes) {
			if (node.coloringVars.get(index)) node.coloringVars.flip(index);
		}
	}
	
	public BitSet addVar(regInterferenceNode nodeToAdd,int numRegs) {
		BitSet unavailableRegs = new BitSet(numRegs);
		for (regInterferenceNode node:nodes) { 
			if (node.inGraph && node.connectedVars.get(nodeToAdd.bitSetIndex) && !node.spilled) {
				node.coloringVars.set(nodeToAdd.bitSetIndex);
				nodeToAdd.coloringVars.set(node.bitSetIndex);
				unavailableRegs.set(node.reg-1);
			}
		}
		nodeToAdd.inGraph = true;
		return unavailableRegs;
	}
	
	public int getReg(String name) {
		for (regInterferenceNode node:nodes) {
			if (node.var.equals(name) && !node.spilled) {
				return node.reg;
			}
		}
		return -1;
	}
}