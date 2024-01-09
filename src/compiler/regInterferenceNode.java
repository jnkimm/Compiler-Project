package compiler;

import java.util.ArrayList;
import java.util.BitSet;

public class regInterferenceNode {
	public int bitSetIndex;
	public String var;
//	public ArrayList<regInterferenceNode> connectedVars;
	public BitSet connectedVars;
	public BitSet coloringVars;
	public Boolean spilled = false;
	public Boolean inGraph = true;
	public int reg;
	
	public regInterferenceNode(int index, String name,int length) {
		connectedVars = new BitSet(length);
		bitSetIndex = index;
		var = name;
	}
	
	public int numEdges() {
		int edges = 0;
		for (int i = 0; i < coloringVars.size(); i++) {
			if (coloringVars.get(i)) {
				edges++;
			}
		}
		return edges;
	}
}