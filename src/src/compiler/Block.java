package compiler;

import java.util.ArrayList;
import java.util.Arrays;

public class Block {
	public int blockPointer;
	public ArrayList<Token> in;
	public ArrayList<Token> out;
	public ArrayList<Integer> liveVarAnalysisBlocks;
	public BlockStrat strat;
	public ArrayList<Operation> ops;
	public Operation lastOp;
	public ArrayList<varOffset> localVars;
//	public Boolean function = false;
	public FunSymbol func = null;
	
	public enum BlockStrat {
		FALL,
		JUMP,
		BRANCH,
		RETURN,
	};
	
	public Block() {
		blockPointer = -1;
		strat = BlockStrat.FALL;
		ops = new ArrayList<Operation>();
		lastOp = new Operation(OpCode.NONE, new ArrayList<Token>());
	}
	
	public void branchFixup(int blockNum, Operation o) {
		blockPointer = blockNum;
		strat = BlockStrat.BRANCH;
		lastOp = o;
	}
	
	public void jumpFixup(int blockNum) {
		blockPointer = blockNum;
		strat = BlockStrat.JUMP;
	}
	
	public void returnFixup() {
		blockPointer = -1;
		strat = BlockStrat.RETURN;
		lastOp = new Operation(OpCode.RET, new ArrayList<Token>());
	}
	
	public void returnFixup(Token t) {
		blockPointer = -1;
		strat = BlockStrat.RETURN;
		lastOp = new Operation(OpCode.RET, IR.buildTokenArray(t));
	}
	
	public boolean isEmpty() {
		return ops.size() == 0 && lastOp.code == OpCode.NONE;
	}
}