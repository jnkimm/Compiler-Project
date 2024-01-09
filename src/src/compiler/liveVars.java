package compiler;

import java.util.ArrayList;
import java.util.BitSet;

public class liveVars{
	public int blockNum;
	public int line;
	public BitSet vars;
	
	public liveVars(int block, int lineNum, BitSet liveVars) {
		line = lineNum;
		blockNum = block;
		vars = (BitSet)liveVars.clone();
	}
}