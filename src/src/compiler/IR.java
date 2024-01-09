package compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.lang.Integer;
import ast.*;

public class IR {
	public ArrayList<Block> blocks;
	public SymbolTable syms;
	private int tempNum = 0;
	private BitSet temps;
	private Token indexAssignment = null;
	private ASTnode indexNode = null;
	private boolean PRINT_DEBUG_FUNCTION_SYMBOLS = false;
	private boolean PRINT_DEBUG_PREDECESSOR_MAP = true;
	public ArrayList<varOffset> globalOffsets;
	public int GDBoffset = 0;
	public FunSymbol currFun = null;
	public int numOps;
	public Boolean inFuncBlocks = false;
	public ArrayList<liveVars> liveVariables;
	public ArrayList<regInterferenceGraph> riGraphs;
	public Boolean mainRIGraphCreated = false;
	
	public IR (AST ast) {
		syms = ast.syms;
		globalOffsets = new ArrayList<varOffset>();
		blocks = new ArrayList<Block>();
		blocks.add(new Block());
		generateIR(ast.main);
	}
	
	private class IRError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final String name;

		public IRError (String message) {
			super(message);
			this.name = message;
		}
	}
	
	public void my_assert(boolean check, String message) {
		if (!check) {
			IRError e = new IRError(message);
			e.printStackTrace();
			throw e;
		}
	}
	
////////// IR generation //////////
	
	private Block pushBlock () {
		Block newBlock = new Block();
		if (inFuncBlocks) newBlock.func = currFun;
		blocks.add(newBlock);
		return curBlock();
	}
	
	private Block curBlock () {
		return blocks.get(blocks.size()-1);
	}
	
	public void generateIR(ASTnode n) {
		if (n == null) {
			my_assert(false, "Tried to call generateIR on a null value.");
		}
		switch (n.nodetype) {
		case COMPUTATION:
			// we emit main first because we want to have block 0
			// contain the entry point, and we have to treat
			// main as though it returns void
			generateIR(n.children.get(n.children.size()-1));
			voidFunFixup();
			for (int i = 0; i < n.children.size()-1; i++) {
				generateIR(n.children.get(i));
			}
			orphanFuncElim();
			convertTrueFalseToInts();
			removeEmptyBlocks();
			autoInitializeVariables();
		break;
		case FUNC:
			inFuncBlocks = true;
			if (!curBlock().isEmpty()) { pushBlock(); }
			ArrayList<VarSymbol> params = AST.paramsToSymbols(n.children.get(0));
			ArrayList<Type> sig = new ArrayList<Type>();
			for (VarSymbol p : params) {
				sig.add(p.type);
			}
			FunSymbol fun = syms.findFun(n.tokenInfo, sig);
			currFun = fun;
			Block lastBlock = curBlock();
			lastBlock.func = currFun;
			syms.startFunction(fun.scope);
			fun.blockEntryPoint = blocks.size()-1;
			generateIR(n.children.get(2));
			syms.endFunction();
			if (fun.output == Kind.VOID) {
				voidFunFixup();
			} else {
				if (curBlock().isEmpty()) {
					blocks.remove(blocks.size()-1);
				}
			}
		break;
		case FUNCTION_BODY:
		case DECLARATION_LIST:
		case STATEMENT_SEQUENCE:
			for (ASTnode c : n.children) {
				generateIR(c);
			}
		break;
		case ARGUMENT_LIST:
		case VARIABLE_DECLARATION:
			if (n.type.dims.size() > 0) {
				int arroffset = 4;
				for (int dims:n.type.dims) {
					arroffset *= dims;
				}
				if (currFun != null) {
					currFun.localOffsets.add(new varOffset(new Token(n.type.kind,n.tokenInfo,0,0),currFun.fpOffset));
					currFun.fpOffset -= arroffset;
				} else {
					globalOffsets.add(new varOffset(new Token(n.type.kind,n.tokenInfo,0,0),GDBoffset));
					GDBoffset -= arroffset;
				}
			}
			// just return because these are no-ops in the IR
			return;
		case IF: {
			Block relBlock = curBlock();
			Operation o = generateIRBranch(n.children.get(0)); // relation
			Block thenBlock = pushBlock();
			generateIR(n.children.get(1)); // then stat seq
			relBlock.branchFixup(blocks.size(), o);
			if (n.children.size() == 3) {
				Block elseBlock = pushBlock();
				generateIR(n.children.get(2)); // else stat seq
				if (thenBlock.strat != Block.BlockStrat.RETURN) {
					thenBlock.jumpFixup(blocks.size());
				}
			}
			pushBlock();
		break;}
		case WHILE: {
			Block rel = curBlock().isEmpty() ? curBlock() : pushBlock();
			Operation o = generateIRBranch(n.children.get(0)); // relation
			int relInd = blocks.size()-1;
			pushBlock();
			generateIR(n.children.get(1)); // stat seq
			Block loop = curBlock();
			loop.jumpFixup(relInd);
			rel.branchFixup(blocks.size(), o);
			pushBlock();
		break; }
		case REPEAT:
			Block b = curBlock().isEmpty() ? curBlock() : pushBlock();
			int ind = blocks.size()-1;
			generateIR(n.children.get(0)); // stat seq
			Operation o = generateIRBranch(n.children.get(1)); // relation
			b.branchFixup(ind, o);
			pushBlock();
		break;
		case CALL:
			ArrayList<Type> signature = new ArrayList<Type>();
			ArrayList<Token> operands = new ArrayList<Token>();
			ASTnode argList = n.children.get(0);
			for(int i = 0; i < argList.children.size(); i++) {
				ASTnode child = argList.children.get(i);
				operands.add(generateIRToken(child));
				my_assert(child.type != null, "Found a missed typechecking step in IR: " + n.tokenInfo + " param " + i + " does not have a type!");
				signature.add(child.type);
			}
			FunSymbol funThis = syms.findFun(n.tokenInfo, signature);
			if (funThis.output != Kind.VOID) {
				operands.add(0, getTempToken(n));
			}
			emit(new Operation(OpCode.CALL, operands, funThis));
			for (Token op : operands) {
				loseTempToken(op);
			}
		break;
		case RETURN:
			my_assert(n.children.size() <= 1 , "Bad children size");
			if (n.children.size() == 1) {
				Token t = generateIRToken(n.children.get(0));
				curBlock().returnFixup(t);
				loseTempToken(t);
			} else {
				curBlock().returnFixup();
			}
			pushBlock();
		break;
		case ASSIGN:
			Token right = generateIRToken(n.children.get(1));
			Token left = generateIRAssignment(n.children.get(0));
			Operation backup = new Operation(OpCode.MOV, buildTokenArray(left, right));
			if (right.kind != Kind.TEMP || curBlock().isEmpty()) {
				emit(backup);
				loseTempToken(right);
				loseTempToken(left);
				return;
			}
			Operation op = curBlock().ops.get(curBlock().ops.size()-1);
			if (op.operands.size() < 2) {
				emit(backup);
				loseTempToken(right);
				loseTempToken(left);
				return;
			}
			Token t = op.operands.get(0);
			if (t != right) {
				emit(backup);
				loseTempToken(right);
				loseTempToken(left);
				return;
			}
			op.operands.set(0, left);
			loseTempToken(right);
			loseTempToken(left);
		break;
		case ADD_ASSIGN:
		case SUB_ASSIGN:
		case MUL_ASSIGN:
		case DIV_ASSIGN:
		case MOD_ASSIGN:
		case POW_ASSIGN: {
			my_assert(n.children.size() == 2, "Bad children size");
			Token right1 = generateIRToken(n.children.get(1));
			Token left1 = generateIRToken(n.children.get(0));
			Token left2 = generateIRAssignment(n.children.get(0));
			ArrayList<Token> ops = buildTokenArray(left2, left1, right1);
			switch (n.nodetype) {
			case ADD_ASSIGN:
				emit(new Operation(OpCode.ADD, ops));
			break;
			case SUB_ASSIGN:
				emit(new Operation(OpCode.SUB, ops));
			break;
			case MUL_ASSIGN:
				emit(new Operation(OpCode.MUL, ops));
			break;
			case DIV_ASSIGN:
				emit(new Operation(OpCode.DIV, ops));
			break;
			case MOD_ASSIGN:
				emit(new Operation(OpCode.MOD, ops));
			break;
			case POW_ASSIGN:
				emit(new Operation(OpCode.POW, ops));
			break;
			}
			loseTempToken(right1);
			loseTempToken(left1);
			loseTempToken(left2);
		break; }
		case UNI_INC:
		case UNI_DEC: {
			my_assert(n.children.size() == 1, "Bad children size");
			Token get = generateIRToken(n.children.get(0));
			Token set = generateIRAssignment(n.children.get(0));
			ArrayList<Token> ops = buildTokenArray(set, get, getConstToken(n, 1));
			if (n.nodetype == Kind.UNI_INC) {
				emit(new Operation(OpCode.ADD, ops));
			} else {
				emit(new Operation(OpCode.SUB, ops));
			}
			loseTempToken(get);
			loseTempToken(set);
		break; }
		default:
			my_assert(false, "generateIR doesn't handle the case of " + n.nodetype.toString() + "!");
		}
	}
	
	public void voidFunFixup() {
		my_assert(curBlock().strat != Block.BlockStrat.JUMP, "The last block in a function should never be a jump block");
		
		if (curBlock().strat == Block.BlockStrat.RETURN) {
			return;
		}
		if (curBlock().strat == Block.BlockStrat.BRANCH) {
			pushBlock();
		}
		curBlock().returnFixup();
	}
	
	public Operation generateIRBranch(ASTnode n) {
		switch (n.children.size()) {
		case 2:
			Token left  = generateIRToken(n.children.get(0));
			Token right = generateIRToken(n.children.get(1));
			// Token retVal = (left.kind == Kind.TEMP) ? left : (right.kind == Kind.TEMP) ? right : getTempToken(n);
			Token retVal = getTempToken(n);
			if(n.nodetype.is(Kind.Group.REL_OP)) {
				emit(new Operation(OpCode.SUB, buildTokenArray(retVal, left, right)));
				switch(n.nodetype) {
				// we emit the negative branch of what the condition asks for
				// because in every case we want to branch if false
				case LESS_THAN:
					return new Operation(OpCode.BGE, buildTokenArray(retVal));
				case GREATER_THAN:
					return new Operation(OpCode.BLE, buildTokenArray(retVal));
				case EQUAL_TO:
					return new Operation(OpCode.BNE, buildTokenArray(retVal));
				case NOT_EQUAL:
					return new Operation(OpCode.BEQ, buildTokenArray(retVal));
				case LESS_EQUAL:
					return new Operation(OpCode.BGT, buildTokenArray(retVal));
				case GREATER_EQUAL:
					return new Operation(OpCode.BLT, buildTokenArray(retVal));
				}
			} else if (n.nodetype == Kind.AND) {
				emit(new Operation(OpCode.AND, buildTokenArray(retVal, left, right)));
				return new Operation(OpCode.BEQ, buildTokenArray(retVal));
			} else if (n.nodetype == Kind.OR) {
				emit(new Operation(OpCode.OR, buildTokenArray(retVal, left, right)));
				return new Operation(OpCode.BEQ, buildTokenArray(retVal));
			}
			my_assert(false, "generateIRbranch does not handle this case!");
			return null;
		case 1:
			if (n.nodetype != Kind.NOT && n.nodetype != Kind.FUNC) {
				my_assert(false, "generateIRbranch does not handle this case!");
			}
			Token t;
			Operation o1;
			if (n.nodetype == Kind.NOT) {
				t = generateIRToken(n.children.get(0));
				o1 = new Operation(OpCode.BNE, buildTokenArray(t));
			} else {
				t = generateIRToken(n);
				o1 = new Operation(OpCode.BEQ, buildTokenArray(t));
			}
			loseTempToken(t);
			return o1;
		case 0:
			my_assert(n.nodetype.is(Kind.Group.TERMINAL) && n.nodetype != Kind.INT_VAL, "Bad ASTnode!");
			Token val = new Token(n.nodetype, n.tokenInfo, n.lineNum, n.charPos);
			return new Operation(OpCode.BEQ, buildTokenArray(val));
		}
		return null;
	}
	
	public Token generateIRToken(ASTnode n) {
		if (n.children.size() == 2) {
			if (n.nodetype == Kind.ARRAY_INDEX) {
				Token array_pointer = generateIRArrayIndex(n);
				Token retVal = getTempToken(n);
				emit(new Operation(OpCode.LDW, buildTokenArray(retVal, array_pointer)));
				loseTempToken(array_pointer);
				return retVal;
			}
			Token right = generateIRToken(n.children.get(1));
			Token left  = generateIRToken(n.children.get(0));
			// Token retVal = (right.kind == Kind.TEMP) ? right : (left.kind == Kind.TEMP) ? left : getTempToken(n);
			Token retVal = getTempToken(n);
			ArrayList<Token> ops = buildTokenArray(retVal, left, right);
			switch (n.nodetype) {
			case ADD:
				emit(new Operation(OpCode.ADD, ops));
			break;
			case SUB:
				emit(new Operation(OpCode.SUB, ops));
			break;
			case MUL:
				emit(new Operation(OpCode.MUL, ops));
			break;
			case DIV:
				emit(new Operation(OpCode.DIV, ops));
			break;
			case MOD:
				emit(new Operation(OpCode.MOD, ops));
			break;
			case POW:
				emit(new Operation(OpCode.POW, ops));
			break;
			case AND:
				emit(new Operation(OpCode.AND, ops));
			break;
			case OR:
				emit(new Operation(OpCode.OR, ops));
			break;
			case LESS_THAN:
				emit(new Operation(OpCode.SUB, ops));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
			break;
			case GREATER_THAN:
				emit(new Operation(OpCode.SUB, buildTokenArray(retVal, right, left)));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
			break;
			case EQUAL_TO: {
				Token tempA = getTempToken(n);
				emit(new Operation(OpCode.SUB, buildTokenArray(tempA, left, right)));
				emit(new Operation(OpCode.LSH, buildTokenArray(tempA, tempA, getConstToken(n, -31))));
				emit(new Operation(OpCode.SUB, buildTokenArray(retVal, right, left)));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
				emit(new Operation(OpCode.XOR, buildTokenArray(retVal, tempA, retVal)));
				loseTempToken(tempA);
				emit(new Operation(OpCode.NOT, buildTokenArray(retVal, retVal)));
			break; }
			case NOT_EQUAL: {
				Token tempB = getTempToken(n);
				emit(new Operation(OpCode.SUB, buildTokenArray(tempB, left, right)));
				emit(new Operation(OpCode.LSH, buildTokenArray(tempB, tempB, getConstToken(n, -31))));
				emit(new Operation(OpCode.SUB, buildTokenArray(retVal, right, left)));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
				emit(new Operation(OpCode.XOR, buildTokenArray(retVal, tempB, retVal)));
				loseTempToken(tempB);
			break; }
			case LESS_EQUAL:
				emit(new Operation(OpCode.SUB, buildTokenArray(retVal, right, left)));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
				emit(new Operation(OpCode.NOT, buildTokenArray(retVal, retVal)));
			break;
			case GREATER_EQUAL:
				emit(new Operation(OpCode.SUB, ops));
				emit(new Operation(OpCode.LSH, buildTokenArray(retVal, retVal, getConstToken(n, -31))));
				emit(new Operation(OpCode.NOT, buildTokenArray(retVal, retVal)));
			break;
			default:
				my_assert(false, "We missed an operation in generateIRToken!");
			}
			if (retVal != left) {
				loseTempToken(left);
			}
			if (retVal != right) {
				loseTempToken(right);
			}
			return retVal;
		} else if (n.children.size() == 1) {
			Token retVal;
			switch (n.nodetype) {
			case NOT:
				Token subExpr = generateIRToken(n.children.get(0));
				// Token retVal = (subExpr.kind == Kind.TEMP) ? subExpr : getTempToken(n);
				retVal = getTempToken(n);
				emit(new Operation(OpCode.NOT, buildTokenArray(retVal, subExpr)));
				return retVal;
			case CALL:
				ArrayList<Type> signature = new ArrayList<Type>();
				ArrayList<Token> operands = new ArrayList<Token>();
				ASTnode argList = n.children.get(0);
				for(int i = 0; i < argList.children.size(); i++) {
					operands.add(generateIRToken(argList.children.get(i)));
					signature.add(argList.children.get(i).type);
				}
				retVal = getTempToken(n);
				operands.add(0, retVal);
				emit(new Operation(OpCode.CALL, new ArrayList<Token>(operands), syms.findFun(n.tokenInfo, signature)));
				for (int i = 1; i < operands.size(); i++) {
					loseTempToken(operands.get(i));
				}
				return retVal;
			default:
				my_assert(false, "We missed an operation in generateIRToken!");
				return null;
			}
		} else if (n.children.size() == 0) {
			my_assert(n.nodetype.is(Kind.Group.TERMINAL), "Bad ASTnode!");
			return new Token(n.nodetype, n.tokenInfo, n.lineNum, n.charPos);
		} else {
			my_assert(false, "Unhandled number of children in generateIRToken");
			return null;
		}
	}
	
	public Token generateIRArrayIndex(ASTnode n) {
		if (n.nodetype != Kind.ARRAY_INDEX) {
			my_assert(false, "Unhandled nodetype in generateIRArrayIndex!");
		}
		ASTnode ident = n;
		ArrayList<ASTnode> index_exprs = new ArrayList<ASTnode>();
		while(ident.nodetype == Kind.ARRAY_INDEX) {
			index_exprs.add(ident.children.get(1));
			ident = ident.children.get(0);
		}
		my_assert(ident.nodetype == Kind.IDENT, "Found terminal node of array index that is not an ident!");
		VarSymbol var = syms.findVar(ident.tokenInfo);
		my_assert(var.type.dims.size() <= index_exprs.size(), 
			"Found array index that tries to index the array more than it is able to be indexed!");
		
		// int byte_size = 4;
		int byte_size = var.type.kind == Kind.INT ? 4 : 1;
		Token ident_tok = generateIRToken(ident);
		Token p_acc = getTempToken(ident);
		Token mul_temp = getTempToken(ident);
		for(int i = 0; i < index_exprs.size(); i++) {
			emit(new Operation(OpCode.MUL, buildTokenArray(mul_temp, getConstToken(ident, byte_size), generateIRToken(index_exprs.get(i)))));
			if (i == 0) {
				emit(new Operation(OpCode.ADD, buildTokenArray(p_acc, ident_tok, mul_temp)));
			} else {
				emit(new Operation(OpCode.ADD, buildTokenArray(p_acc, p_acc, mul_temp)));
			}
			byte_size *= var.type.dims.get(var.type.dims.size()-1-i);
		}
		
		//TODO: emit CHK if array values are not constant
		
		loseTempToken(mul_temp);
		return p_acc;
	}
	public Boolean isGlobNewVar(String name) {
//		if (globalOffsets != null) return true;
		for (varOffset var:globalOffsets) {
			if (var.variable.lexeme.equals(name)) {
				return false;
			}
		}
		return true;
	}
	
	public Boolean isLocalNewVar(String name,FunSymbol currFun) {
		for (varOffset var:currFun.localOffsets) {
			if (var.variable.lexeme.equals(name)) {
				return false;
			}
		}
		return true;
	}
	public Token generateIRAssignment(ASTnode n) {
		if (n.nodetype == Kind.IDENT) {
			Token assignTok = new Token(n.nodetype, n.tokenInfo, n.lineNum, n.charPos);
//			if (isGlobNewVar(n.tokenInfo)) {
//				globalOffsets.add(new varOffset(assignTok,GDBoffset));
//				GDBoffset -= 4;
//			}
			return assignTok;
		}
		if (n.nodetype != Kind.ARRAY_INDEX) {
			my_assert(false, "Unhandled nodetype in generateIRAssignment!");
		}
		Token val = getTempToken(n);
		indexAssignment = val;
		indexNode = n;
		return val;
	}
	
	private void loseTempToken(Token t) {
		if (t.kind == Kind.TEMP) {
//			Boolean removedTemp = false;
//			for (int i = 0; i < globalOffsets.size(); i++) {
//				if (removedTemp) {
//					globalOffsets.get(i).Offset += 4;
//				} else if (globalOffsets.get(i).variable.lexeme.equals(t.lexeme)) {
//					globalOffsets.remove(i);
//					GDBoffset += 4;
//					removedTemp = true;
//				}
//			}
			if (t == indexAssignment) {
				Token address = generateIRArrayIndex(indexNode);
				emit(new Operation(OpCode.STW, buildTokenArray(t, address)));
				indexAssignment = null;
				indexNode = null;
			}
			// tempNum--;
		}
	}
	
	private Token getTempToken (ASTnode n) {
		Token tempTok = new Token(Kind.TEMP, Integer.valueOf(tempNum++).toString(), n.lineNum, n.charPos);
//		if (isGlobNewVar(tempTok.lexeme)) {
//			globalOffsets.add(new varOffset(tempTok,GDBoffset));
//			GDBoffset -= 4;
//		}
		return tempTok;
	}
	
	private Token getConstToken(ASTnode n, int a) {
		return new Token(Kind.INT_VAL, Integer.valueOf(a).toString(), n.lineNum, n.charPos);
	}
	
	public void emit(Operation op) {
		blocks.get(blocks.size()-1).ops.add(op);
	}
	
	static public ArrayList<Token> buildTokenArray(Token... tokens) {
		return new ArrayList<Token>(Arrays.asList(tokens));
	}
	
////////// Printing and checking //////////

	public void orphanFuncElim() {
		int numFuncs = syms.funs.size();
		BitSet liveFuncs = new BitSet(numFuncs);
		BitSet compedFuncs = new BitSet(numFuncs);
		
		// sets hardcoded functions as already computed
		for (int i = 0; i < numFuncs; i++) {
			compedFuncs.set(i, syms.funs.get(i).blockEntryPoint < 0);
		}
		
		// does main
		orphanFuncElimIterBlocks(0, getFunctionEnd(0), liveFuncs);
		
		for (int i = 0; i < numFuncs; i++) {
			if (liveFuncs.get(i) && !compedFuncs.get(i)) {
				int entry = syms.funs.get(i).blockEntryPoint;
				orphanFuncElimIterBlocks(entry, getFunctionEnd(entry), liveFuncs);
				compedFuncs.set(i);
				i = 0;
			}
		}
		
		for (int i = numFuncs-1; i >= 0; i--) {
			if (!liveFuncs.get(i)) {
				FunSymbol curFun = syms.funs.get(i);
				syms.funs.remove(i);
				if (curFun.blockEntryPoint > 0) {
					int start = curFun.blockEntryPoint;
					int end = getFunctionEnd(start);
					for (int j = end; j >= start; j--) {
						removeBlock(j);
					}
				}
			}
		}
	}
	
	public void orphanFuncElimIterBlocks(int start, int end, BitSet funcs) {
		for (int i = start; i <= end; i++) {
			Block b = blocks.get(i);
			for (Operation op : b.ops) {
				if (op.code == OpCode.CALL) {
					int index = syms.funs.indexOf(op.fun);
					funcs.set(index);
				}
			}
		}
	}
	
	public void autoInitializeVariables() {
		
		int start = 0;
		ArrayList<BitSet> blockLiveVars = new ArrayList<BitSet>();
		while (start < blocks.size()) {
			
			FunSymbol currentFun = null;
			
			if (start > 0) {
				int ctr = 0;
				for (FunSymbol fun : syms.funs) {
					if (fun.blockEntryPoint == start) {
						ctr++;
						currentFun = fun;
						syms.startFunction(fun.scope);
					}
				}
				my_assert(ctr == 1, "We expected to find exactly one function starting at block " + start + " but did not!");
			}
			int end = getFunctionEnd(start);
			int bitsetSize = syms.vars.size() + ((start > 0) ? syms.functionVars.size() : 0);
			for (int i = 0; i < blocks.size(); i++) {
				blockLiveVars.add(new BitSet(bitsetSize));
			}
			
			for (int curBlock = end; curBlock >= start; curBlock--) {
				Block b = blocks.get(curBlock);
				switch (b.strat) {
				case FALL:
					blockLiveVars.set(curBlock, autoInitVarsBlock(b, blockLiveVars.get(curBlock+1), bitsetSize));
				break;
				case RETURN:
					blockLiveVars.set(curBlock, autoInitVarsBlock(b, new BitSet(bitsetSize), bitsetSize));
				break;
				case JUMP:
					if (b.blockPointer <= curBlock) {
						BitSet check = autoInitVarsBlock(b, blockLiveVars.get(b.blockPointer), bitsetSize);
						if (!check.equals(blockLiveVars.get(curBlock))) {
							blockLiveVars.set(curBlock, check);
							curBlock = b.blockPointer+1;
							continue;
						}
					} else {
						blockLiveVars.set(curBlock, autoInitVarsBlock(b, blockLiveVars.get(b.blockPointer), bitsetSize));
					}
				break;
				case BRANCH:
					BitSet set1 = (BitSet) blockLiveVars.get(curBlock+1).clone();
					BitSet set2 = blockLiveVars.get(b.blockPointer);
					set1.and(set2);
					BitSet mergeSet = autoInitVarsBlock(b, set1, bitsetSize);
					if (b.blockPointer <= curBlock) {
						if (!mergeSet.equals(blockLiveVars.get(curBlock))) {
							blockLiveVars.set(curBlock, mergeSet);
							curBlock = b.blockPointer+1;
							continue;
						}
					} else {
						blockLiveVars.set(curBlock, mergeSet);
					}
				break;
				}
			}
			// check to make sure we don't put init statements in a loop
			HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
			if (predecessorMap.get(start).size() != 0) {
				insertBlockAfter(start - 1);
			}
			Block autoInitLoc = blocks.get(start);
			for (int i = 0; i < bitsetSize; i++) {
				if (blockLiveVars.get(start).get(i)) {
					continue;
				}
				VarSymbol var;
				if (syms.parsingFunction()) {
					int index = i - syms.vars.size();
					if (index < currentFun.inputs.size()) { continue; }
					var = syms.functionVars.get(index);
				} else {
					var = syms.vars.get(i);
				}
				Token varTok = new Token(Kind.IDENT, var.name, 0, 0);
				autoInitLoc.ops.add(0, new Operation(OpCode.MOV, buildTokenArray(varTok, new Token(Kind.INT_VAL, "0", 0, 0))));
			}
			syms.endFunction();
			start = end+1;
		}
	}
	
	private BitSet autoInitVarsBlock(Block b, BitSet init, int bitsetSize) {
		BitSet update = (BitSet) init.clone();
		for (int op_ind = b.ops.size()-1; op_ind >= 0; op_ind--) {
			Operation op = b.ops.get(op_ind);
			if (op.code != OpCode.STW && !(op.code == OpCode.CALL && op.fun.output == Kind.VOID)) {
				Token isSet = op.operands.get(0);
				if (isSet.kind == Kind.TEMP) {
					continue;
				}
				update.set(getTokenBitSetIndex(isSet, bitsetSize, false));
			}
		}
		return update;
	}
	
	public void removeEmptyBlocks() {
		for (int i = 0; i < blocks.size(); i++) {
			if (blocks.get(i).isEmpty()) {
				removeBlock(i);
			}
		}
	}
	
	public void removeBlock(int i) {
		if (blocks.get(i).strat == Block.BlockStrat.JUMP) {
			HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
			ArrayList<Integer> predecessors = predecessorMap.get(i);
			for (int predecessor : predecessors) {
				Block pBlock = blocks.get(predecessor);
				my_assert(pBlock.strat != Block.BlockStrat.RETURN, "Found a return block as a predecessor to another block!");
				if (predecessor == i-1) {
					//fixup the fallthrough
					if (pBlock.strat == Block.BlockStrat.BRANCH) {
						// maybe log here?
						return;
					}
					pBlock.strat = Block.BlockStrat.JUMP;
				}
				pBlock.blockPointer = blocks.get(i).blockPointer;
			}
		}
		blocks.remove(i);
		for (Block b : blocks) {
			if (b.blockPointer > i) {
				b.blockPointer--;
			}
		}
		for (FunSymbol fun : syms.funs) {
			if (fun.blockEntryPoint > i) {
				fun.blockEntryPoint--;
			}
		}
	}
	
	public void insertBlockAfter(int i) {
		blocks.add(i+1, new Block());
		for (Block b : blocks) {
			if (b.blockPointer > i) {
				b.blockPointer++;
			}
		}
		for (FunSymbol fun : syms.funs) {
			if (fun.blockEntryPoint > i) {
				fun.blockEntryPoint++;
			}
		}
	}
	
	public void convertTrueFalseToInts() {
		for (Block b : blocks) {
			for (Operation op : b.ops) {
				convertTrueFalseToIntsOp(op);
			}
			if (b.strat == Block.BlockStrat.RETURN || b.strat == Block.BlockStrat.BRANCH) {
				convertTrueFalseToIntsOp(b.lastOp);
			}
		}
	}
	
	private void convertTrueFalseToIntsOp(Operation op) {
		for (Token t : op.operands) {
			if (t.kind == Kind.TRUE) {
				t.kind = Kind.INT_VAL;
				t.lexeme = "1";
			}
			if (t.kind == Kind.FALSE) {
				t.kind = Kind.INT_VAL;
				t.lexeme = "0";
			}
		}
	}
	
	public String asDotGraph () {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph G {\n");
		int instCounter = 1;
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			int blockNum = i+1;
			sb.append("bb" + blockNum + " [shape=record, label= \"<b>BB" + blockNum + "| {");
			String delim = "";
			for (Operation op : b.ops) {
				sb.append(delim + instCounter + ": " + op.toString());
				delim = "|";
				instCounter++;
			}
			if (b.strat == Block.BlockStrat.RETURN || b.strat == Block.BlockStrat.BRANCH) {
				sb.append(delim + instCounter + ": " + b.lastOp.toString());
				if (b.strat == Block.BlockStrat.BRANCH) {
					sb.append(" (" + (b.blockPointer+1) + ")");
				}
				instCounter++;
			}
			if (b.strat == Block.BlockStrat.JUMP) {
				sb.append(delim + instCounter + ": JMP (" + (b.blockPointer+1) + ")");
				instCounter++;
			}
			sb.append("}\"];\n");
		}
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			int blockNum = i+1;
			switch (b.strat) {
			case RETURN:
			break;
			case JUMP:
				sb.append("bb" + blockNum + ":s -> bb" + (b.blockPointer+1) + ":n [label=\"jump\"];\n");
			break;
			case BRANCH:
				sb.append("bb" + blockNum + ":s -> bb" + (b.blockPointer+1) + ":n [label=\"branch\"];\n");
			case FALL:
				sb.append("bb" + blockNum + ":s -> bb" + (blockNum+1) + ":n [label=\"fall-through\"];\n");
			break;
			}
		}
		sb.append("}");
		///////////////////////////////////////
		//   Change the return statement     // 
		//   To switch between dot-graph     // 
		//   and assembly views              // 
		///////////////////////////////////////
		// return sb.toString();
		return print();
		///////////////////////////////////////
		//   To see the printouts before     // 
		//   and after, uncomment            // 
		//   Compiler.optimization return    // 
		//   value. (in coco.Compiler)       // 
		///////////////////////////////////////
	}
	
	private String print() {
		StringBuilder sb = new StringBuilder();
		if (PRINT_DEBUG_FUNCTION_SYMBOLS) {
			for(FunSymbol fun : syms.funs) {
				sb.append(fun.blockEntryPoint + ": " + fun.toString(false) + "\n");
			}
		}
		if (PRINT_DEBUG_PREDECESSOR_MAP) {
			HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
			HashMap<Integer, FunSymbol> funEntries = new HashMap<Integer, FunSymbol>();
			for (FunSymbol fun : syms.funs) {
				if (fun.blockEntryPoint < 0) {
					continue;
				}
				funEntries.put(fun.blockEntryPoint, fun);
			}
			for (int b_i = 0; b_i < blocks.size(); b_i++) {
				if (b_i == 0) {
					sb.append("0: main\n");
				} else if (funEntries.containsKey(b_i)) {
					sb.append(b_i + ": " + funEntries.get(b_i).name + "\n");
				} else {
					sb.append(b_i + ":");
					ArrayList<Integer> predecessors = predecessorMap.get(b_i);
					for (Integer pd : predecessors) {
						sb.append(" " + pd);
					}
					sb.append("\n");
				}
			}
		}
		sb.append("\n");
		for(int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			sb.append("Block Start:" + i + "\n");
			for (Operation op : b.ops) {
				sb.append("   " + op.toString() + "\n");
			}
			if (b.strat == Block.BlockStrat.RETURN || b.strat == Block.BlockStrat.BRANCH) {
				sb.append("   " + b.lastOp.toString());
				if (b.strat == Block.BlockStrat.BRANCH) {
					sb.append("block:" + b.blockPointer);
				}
				sb.append("\n");
			}
			if (b.strat == Block.BlockStrat.JUMP) {
				sb.append("   JMP block:" + b.blockPointer + "\n");
			}
		}
		return sb.toString();
	}
	
////////// Optimizations //////////
	
	public void maxOptimizations() {
		boolean overall;
		
		do { do {
				overall = false;
				overall |= constCopyPropOneStep(false, true);
				overall |= constFoldOneStep();
			} while (overall);
		} while (commonSubexpression());
		deadcodeElimination(false);
	}
	
	public boolean constantPropagation() {
		boolean overall = constCopyPropOneStep(false, false);
		if (overall) while (constCopyPropOneStep(false, false));
		return overall;
	}
	
	public boolean constantFolding() {
		boolean overall = constFoldOneStep();
		if (overall) while (constFoldOneStep());
		return overall;
	}
	
	public boolean copyPropagation() {
		boolean overall = constCopyPropOneStep(true, false);
		if (overall) while (constCopyPropOneStep(true, false));
		return overall;
	}
	
	public HashMap<Integer, ArrayList<Integer>> genPredecessorMap() {
		HashMap<Integer, ArrayList<Integer>> retVal = new HashMap<Integer, ArrayList<Integer>>();
		for (int i = 0; i < blocks.size(); i++) {
			retVal.put(i, new ArrayList<Integer>());
		}
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			switch (b.strat) {
			case FALL:
				retVal.get(i+1).add(i);
			break;
			case RETURN:
			break;
			case JUMP:
				retVal.get(b.blockPointer).add(i);
			break;
			case BRANCH:
				retVal.get(b.blockPointer).add(i);
				retVal.get(i+1).add(i);
			break;
			}
		}
		return retVal;
	}
	
	public boolean commonSubexpression() {
		HashMap<Operation, Operation> replaceOps = new HashMap<Operation, Operation>();
		HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
		ArrayList<HashSet<Operation>> validExprsAtBlockEnd = new ArrayList<HashSet<Operation>>();
		
		for (int i = 0; i < blocks.size(); i++) {
			validExprsAtBlockEnd.add(new HashSet<Operation>());
		}
		
		boolean firstIter = true;
		boolean equal;
		do {
			ArrayList<HashSet<Operation>> oldExprList = new ArrayList<HashSet<Operation>>();
			for(int i = 0; i < blocks.size(); i++) {
				oldExprList.add(new HashSet<Operation>(validExprsAtBlockEnd.get(i)));
			}
			for (int curBlock = 0; curBlock < blocks.size(); curBlock++) {
				ArrayList<Integer> predecessors = predecessorMap.get(curBlock);
				HashSet<Operation> validExprs;
				if (predecessors.size() == 0) {
					validExprs = new HashSet<Operation>();
				} else {
					validExprs = new HashSet<Operation>(validExprsAtBlockEnd.get(predecessors.get(0)));
					for (int i = 1; i < predecessors.size(); i++) {
						if (firstIter && predecessors.get(i) >= i) {
							continue;
						}
						validExprs = commonSubexprCombineValidExprs(validExprs, validExprsAtBlockEnd.get(predecessors.get(i)));
					}
				}
				commonSubexprBlock(blocks.get(curBlock), replaceOps, validExprs);
				validExprsAtBlockEnd.set(curBlock, validExprs);
			}
			equal = !firstIter;
			//do checking
			for (int i = 0; i < blocks.size(); i++) {
				HashSet<Operation> oldExprs = oldExprList.get(i);
				for (Operation op : oldExprs) {
					equal &= validExprsAtBlockEnd.get(i).contains(op);
				}
			}
			firstIter = false;
		} while (!equal);
		
		for (Block b : blocks) {
			for (Operation op : b.ops) {
				if (replaceOps.containsKey(op)) {
					Operation expr = replaceOps.get(op);
					op.code = OpCode.MOV;
					op.operands = buildTokenArray(op.operands.get(0), expr.operands.get(0));
				}
			}
		}
		
		return !replaceOps.isEmpty();
	}
	
	private HashSet<Operation> commonSubexprCombineValidExprs(HashSet<Operation> a, HashSet<Operation> b) {
		HashSet<Operation> retVal = new HashSet<Operation>();
		for (Operation op : a) {
			if (b.contains(op)) {
				retVal.add(op);
			}
		}
		return retVal;
	}
	
	private void commonSubexprBlock(Block b, HashMap<Operation, Operation> replaceOps, HashSet<Operation> validExprs) {
		for (Operation op : b.ops) {
			if (op.code == OpCode.STW || op.code == OpCode.CALL || op.code == OpCode.LDW || op.code == OpCode.MOV) {
				continue;
			}
			boolean wasReplaced = false;
			Token set = op.operands.get(0);
			// iterate backwards to make removal safe
			
			for (Iterator<Operation> iter = validExprs.iterator(); iter.hasNext();) {
				Operation expr = iter.next();
				if (op.code == expr.code) {
					my_assert(op.code.registers >= 2 && op.code.registers <= 3, "Found a weird OpCode in commonSubexpression!");
					if (op.code.registers == 2) {
						if (Token.match(op.operands.get(1), expr.operands.get(1))) {
							replaceOps.put(op, expr);
							wasReplaced = true;
						}
					} else {
						Token op1 = op.operands.get(1);
						Token op2 = op.operands.get(2);
						Token expr1 = expr.operands.get(1);
						Token expr2 = expr.operands.get(2);
						if (Token.match(op1, expr1) && Token.match(op2, expr2)) {
							if (!replaceOps.containsKey(op)) {
								replaceOps.put(op, expr);
							}
							wasReplaced = true;
						}
					}
				}
				if (expr.code.registers == 2) {
					if (Token.match(set, expr.operands.get(0)) || 
						Token.match(set, expr.operands.get(1))) {
						
						iter.remove();
					}
				} else {
					if (Token.match(set, expr.operands.get(0)) || 
						Token.match(set, expr.operands.get(1)) || 
						Token.match(set, expr.operands.get(2))) {
						
						iter.remove();
					}
				}
			}
			if (!wasReplaced) {
				if (op.code.registers == 2) {
					if (!Token.match(set, op.operands.get(1))) {
						validExprs.add(op);
					}
				} else {
					if (!(Token.match(set, op.operands.get(1)) || Token.match(set, op.operands.get(2)))) {
						validExprs.add(op);
					}
				}
				replaceOps.remove(op);
			}
		}
	}
	
	public boolean eliminateUnreachableBlocks() {
		boolean retVal = false;
		boolean step;
		do {
			step = eliminateUnreachableBlocksOneStep();
			retVal |= step;
		} while (step);
		return retVal;
	}
	
	public boolean eliminateUnreachableBlocksOneStep() {
		HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
		HashMap<Integer, FunSymbol> funEntries = new HashMap<Integer, FunSymbol>();
		for (FunSymbol fun : syms.funs) {
			if (fun.blockEntryPoint < 0) {
				continue;
			}
			funEntries.put(fun.blockEntryPoint, fun);
		}
		ArrayList<Integer> unreachableBlocks = new ArrayList<Integer>();
		for (Integer block_ind = 1; block_ind < blocks.size(); block_ind++) {
			if (predecessorMap.get(block_ind).size() == 0 && funEntries.containsKey(block_ind) == false) {
				unreachableBlocks.add(block_ind);
			}
			Block b = blocks.get(block_ind);
			if (b.strat == Block.BlockStrat.BRANCH && b.lastOp.operands.get(0).kind == Kind.INT_VAL) {
				System.out.println("Found a constant branch in block " + block_ind);
			}
		}
		
		// go backwards to avoid eliminating incorrect blocks
		for (int i = unreachableBlocks.size()-1; i >= 0; i--) {
			removeBlock(unreachableBlocks.get(i));
		}			
		
		return !unreachableBlocks.isEmpty();
	}
	
	public boolean deadcodeElimination(Boolean justLives) {
		
		//extract function extents to find main
		int start = 0;
		ArrayList<Operation> deadOps = new ArrayList<Operation>();
		if (justLives) {
			liveVariables = new ArrayList<liveVars>();
			riGraphs = new ArrayList<regInterferenceGraph>();
		}
		currFun = null;
		
		int end;
		while (start < blocks.size()) {
			end = getFunctionEnd(start);
			if (start > 0) {
				int ctr = 0;
				for (FunSymbol fun : syms.funs) {
					if (fun.blockEntryPoint == start) {
						ctr++;
						syms.startFunction(fun.scope);
						if (!fun.riGraphCreated && justLives) {
							riGraphs.add(new regInterferenceGraph(fun,start,end));
							fun.riGraphCreated = true;
							currFun = fun;
						}
					}
				}
				my_assert(ctr == 1, "We expected to find exactly one function starting at block " + start + " but did not!");
			} else {
				if (!mainRIGraphCreated && justLives) {
					riGraphs.add(new regInterferenceGraph(start,end));
					mainRIGraphCreated = true;
				}
			}
			
			int tempIndexStart = syms.vars.size() + ((start > 0) ? syms.functionVars.size() : 0);
			BitSet defaultBitSet = new BitSet(tempIndexStart);
			if (start > 0) {
				//set globals to be live by default in functions
				defaultBitSet.set(0, syms.vars.size());
			}
			ArrayList<BitSet> liveVars = new ArrayList<BitSet>(blocks.size());
			for (int i = 0; i < blocks.size(); i++) {
				liveVars.add(new BitSet(tempIndexStart));
			}
			
			//start at the end of main
			for (int i = end; i >= start; i--) {
				Block b = blocks.get(i);
				my_assert(i != end || b.strat == Block.BlockStrat.RETURN, 
					"Found the end block of main does not return in deadcodeElimination!");
				switch (b.strat) {
				case RETURN:
					BitSet exit = new BitSet(tempIndexStart);
					exit.xor(defaultBitSet);
					liveVars.set(i, deadElimBlock(b, exit, tempIndexStart, deadOps, i, justLives));
				break;
				case FALL:
					liveVars.set(i, deadElimBlock(b, liveVars.get(i+1), tempIndexStart, deadOps, i, justLives));
				break;
				case JUMP:
					if (b.blockPointer > i) {
						liveVars.set(i, deadElimBlock(b, liveVars.get(b.blockPointer), tempIndexStart, deadOps, i, justLives));
					} else {
						deadElimLoopConverge(b.blockPointer, i, liveVars, deadOps, tempIndexStart, defaultBitSet, justLives);
						i = b.blockPointer;
					}
				break;
				case BRANCH:
					BitSet mergeBits = (BitSet)liveVars.get(i+1).clone();
					mergeBits.or(liveVars.get(b.blockPointer));
					if (b.blockPointer > i) {
						liveVars.set(i, deadElimBlock(b, mergeBits, tempIndexStart, deadOps, i, justLives));
					} else {
						deadElimLoopConverge(b.blockPointer, i, liveVars, deadOps, tempIndexStart, defaultBitSet, justLives);
						i = b.blockPointer;
					}
				}
			}
			syms.endFunction();
			start = end+1;
		}
		for(Block b : blocks) {
			if (!justLives) b.ops.removeAll(deadOps);
		}
		
		if (!justLives) {
			boolean didElimBlocks = eliminateUnreachableBlocks();
			return (deadOps.size() > 0) || didElimBlocks;
		} else {
			return false;
		}
	}
	
	private void deadElimLoopConverge(int startBlock, int endBlock, ArrayList<BitSet> liveVars, ArrayList<Operation> deadOps, int tempIndexStart, BitSet defaultBitSet, Boolean justLives) {
		ArrayList<BitSet> oldVars = new ArrayList<BitSet>();
		for (BitSet bs : liveVars) {
			oldVars.add(new BitSet());
		}
		boolean equal;
		do {
			//do a deep copy over to old
			for (int i = 0; i < liveVars.size(); i++) {
				oldVars.set(i, liveVars.get(i).get(0, liveVars.get(i).length()));
			}
			for (int curBlock = endBlock; curBlock >= startBlock; curBlock--) {
				Block b = blocks.get(curBlock);
				switch (b.strat) {
				case RETURN:
					BitSet exit = new BitSet(tempIndexStart);
					exit.xor(defaultBitSet);
					liveVars.set(curBlock, deadElimBlock(b, exit, tempIndexStart, deadOps, curBlock, justLives));
				break;
				case FALL:
					liveVars.set(curBlock, deadElimBlock(b, liveVars.get(curBlock+1), tempIndexStart, deadOps, curBlock, justLives));
				break;
				case JUMP:
					if (b.blockPointer > curBlock || curBlock == endBlock) {
						liveVars.set(curBlock, deadElimBlock(b, liveVars.get(b.blockPointer), tempIndexStart, deadOps, curBlock, justLives));
					} else {
						deadElimLoopConverge(b.blockPointer, curBlock, liveVars, deadOps, tempIndexStart, defaultBitSet, justLives);
						curBlock = b.blockPointer;
					}
				break;
				case BRANCH:
					BitSet mergeBits = (BitSet)liveVars.get(curBlock+1).clone();
					mergeBits.or(liveVars.get(b.blockPointer));
					if (b.blockPointer > curBlock || curBlock == endBlock) {
						liveVars.set(curBlock, deadElimBlock(b, mergeBits, tempIndexStart, deadOps, curBlock, justLives));
					} else {
						deadElimLoopConverge(b.blockPointer, curBlock, liveVars, deadOps, tempIndexStart, defaultBitSet, justLives);
						curBlock = b.blockPointer;
					}
				break;
				}
			}
			//check if we have converged
			equal = true;
			for (int i = 0; i < liveVars.size(); i++) {
				BitSet check = liveVars.get(i).get(0, liveVars.get(i).length());
				check.xor(oldVars.get(i));
				equal &= check.isEmpty();
			}
		} while (!equal);
	}
	
	private BitSet deadElimBlock(Block b, BitSet initial, int tempIndexStart, ArrayList<Operation> deadOps, int blockNum, Boolean justLives) {
		BitSet varBits = (BitSet)initial.clone();
		if (justLives) adjustLives(varBits, blockNum, b.ops.size()) ;
		
		if (b.strat == Block.BlockStrat.RETURN || b.strat == Block.BlockStrat.BRANCH) {
			Operation op = b.lastOp;
			for (int k = 0; k < op.operands.size(); k++) {
				int index = getTokenBitSetIndex(op.operands.get(k), tempIndexStart, justLives);
				if (index >= 0) {
					varBits.set(index);
					if (justLives) adjustLives(varBits, blockNum, k) ;
				}
			}
		}
		for (int j = b.ops.size()-1; j >= 0; j--) {
			Operation op = b.ops.get(j);
			// for now we assume that user-defined functions use
			// all global variables, because we don't want to do a
			// static analysis of which globals each function uses
//			adjustLives(varBits, blockNum, j);
			if (op.operands.size() == 0) {
				if (op.code == OpCode.CALL && op.fun.blockEntryPoint > 0) {
					// set globals
					varBits.set(0, syms.vars.size());
				}
				continue;
			}
			if (op.code == OpCode.CALL) {
				int index = getTokenBitSetIndex(op.operands.get(0), tempIndexStart, justLives);
				if (op.fun.output != Kind.VOID) {
					// clear outputs
					my_assert(index >= 0, "We are setting something that is not an ident or a temp! Offending token: " + op.operands.get(0));
					varBits.clear(index);
				}
				if (op.code == OpCode.CALL && op.fun.blockEntryPoint > 0) {
					// set globals
					varBits.set(0, syms.vars.size());
				}
				// set inputs
				if (op.fun.output == Kind.VOID) {
					if (index >= 0) {
						varBits.set(index);
					}
				}
				for (int k = 1; k < op.operands.size(); k++) {
					index = getTokenBitSetIndex(op.operands.get(k), tempIndexStart, justLives);
					if (index >= 0) {
						varBits.set(index);
					}
				}
				if (justLives) adjustLives(varBits, blockNum, j);
				continue;
			}
			if (op.code == OpCode.STW || op.code == OpCode.CHK || op.code == OpCode.WRI || op.code == OpCode.WRB  || op.code == OpCode.WRL) {
				// In STW (and some other memory instructions)
				// all registers are inputs
				for (Token t : op.operands) {
					int index = getTokenBitSetIndex(t, tempIndexStart, justLives);
					if (index >= 0) {
						varBits.set(index);
					}
				}
			} else {
				int index = getTokenBitSetIndex(op.operands.get(0), tempIndexStart, justLives);
				my_assert(index >= 0, "We are setting something that is not an ident or a temp! Offending token: " + op.operands.get(0));
				if (varBits.get(index)) {
					varBits.clear(index);
				} else if (!justLives) {
					if (!deadOps.contains(op)) {
						deadOps.add(op);
					}
					continue;
				}
				for (int k = 1; k < op.operands.size(); k++) {
					index = getTokenBitSetIndex(op.operands.get(k), tempIndexStart, justLives);
					if (index >= 0) {
						varBits.set(index);
					}
				}
			}
			if (justLives) {
				adjustLives(varBits, blockNum, j);
			} else {
				deadOps.remove(op);
			}
		}
		return varBits;
	}
	
	private int getTokenBitSetIndex(Token t, int tempOffset, Boolean justLives) {
		int index = -1;
		switch (t.kind) {
		case TEMP:
			index = Integer.parseInt(t.lexeme) + tempOffset;
		break;
		case IDENT:
			if (syms.parsingFunction()) {
				index = syms.lookupVarIndex(syms.functionVars, t.lexeme);
				if (index == -1) {
					index = syms.lookupVarIndex(syms.vars, t.lexeme);
				} else {
					index += syms.vars.size();
				}
			} else {
				index = syms.lookupVarIndex(syms.vars, t.lexeme);
			}
		break;
		case TRUE:
		case FALSE:
		case INT_VAL:
		break;
		default:
			my_assert(false, "Found unhandled token kind " + t.kind.toString() + " in deadcodeElimiation.");
		}
		
		if (justLives && (t.kind == Kind.IDENT || t.kind == Kind.TEMP)) {
			if (syms.parsingFunction()) {
				riGraphs.get(riGraphs.size()-1).add(t.lexeme, index, globalOffsets.size()+currFun.localOffsets.size());
			} else {
				riGraphs.get(riGraphs.size()-1).add(t.lexeme, index, globalOffsets.size());
			}
		}
		return index;
	}
	
	private void adjustLives(BitSet newBits, int block, int opNum) {
		if (riGraphs.get(riGraphs.size()-1).liveVariables != null) {
			for (int i = 0; i < riGraphs.get(riGraphs.size()-1).liveVariables.size(); i++) {
				if (riGraphs.get(riGraphs.size()-1).liveVariables.get(i).blockNum == block && riGraphs.get(riGraphs.size()-1).liveVariables.get(i).line == opNum) {
					riGraphs.get(riGraphs.size()-1).liveVariables.get(i).vars = (BitSet)newBits.clone();
					return;
				}
			}
			riGraphs.get(riGraphs.size()-1).liveVariables.add(new liveVars(block,opNum,newBits));
		} else {
			for (int i = 0; i < liveVariables.size(); i++) {
				if (liveVariables.get(i).blockNum == block && liveVariables.get(i).line == opNum) {
					liveVariables.get(i).vars = (BitSet)newBits.clone();
					return;
				}
			}
			liveVariables.add(new liveVars(block,opNum,newBits));
		}
		return;
	}
	
	private ArrayList<Operation> mergeOpList(ArrayList<Operation> opList1, ArrayList<Operation> opList2) {
		if (opList1 == null) {
			return new ArrayList<Operation>(opList2);
		}
		if (opList2 == null) {
			return new ArrayList<Operation>(opList1);
		}
		ArrayList<Operation> outList = new ArrayList<Operation>();
		if (opList1.size() == 0) {
			return outList;
		}
		if (opList2.size() == 0) {
			return outList;
		}
		for (Operation op1 : opList1) {
			for (Operation op2 : opList2) {
				if (Operation.compareEquals(op1, op2)) {
					outList.add(new Operation(op1.code, new ArrayList<Token>(op1.operands)));
				}
			}
		}
		return outList;
	}
	
	public boolean constCopyPropOneStep(boolean doCopyProp, boolean doBoth) {
		HashMap<Integer, ArrayList<Operation>> availableOpsMap = new HashMap<Integer, ArrayList<Operation>>();
		for (int i = 0; i < blocks.size(); i++) {
			availableOpsMap.put(i, new ArrayList<Operation>());
		}
		HashMap<Integer, ArrayList<Integer>> predecessorMap = genPredecessorMap();
		BitSet blockHasBeenTouched = new BitSet(blocks.size());
		
		// setup the avaliableOpsMap to contain correct values
		for(int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			
			ArrayList<Operation> availableOps = null;
			for (Integer predecessor : predecessorMap.get(i)) {
				if (blockHasBeenTouched.get(predecessor)) {
					availableOps = mergeOpList(availableOps, availableOpsMap.get(predecessor));
				}
			}
			if (availableOps == null) {
				availableOps = new ArrayList<Operation>();
			}
			
			for (Operation op : b.ops) {
				if (op.operands.size() == 0) {
					continue;
				}
				if (op.code == OpCode.CALL && op.fun.blockEntryPoint > 0) {
					availableOps.clear();
					continue;
				}
				for (int k = 0; k < availableOps.size(); k++) {
					if (op.hasOutput() && (op.operands.get(0).lexeme().equals(availableOps.get(k).operands.get(0).lexeme()))) {
						availableOps.remove(k);
					}
				}
				if (doBoth) {
					if (op.code == OpCode.MOV && ( op.operands.get(1).kind == Kind.INT_VAL ||
					                               op.operands.get(1).kind == Kind.IDENT ||
					                               op.operands.get(1).kind == Kind.TEMP)) {
						availableOps.add(op);
					}
				} else if (doCopyProp) {
					if (op.code == OpCode.MOV && ( op.operands.get(1).kind == Kind.IDENT ||
					                               op.operands.get(1).kind == Kind.TEMP)) {
						availableOps.add(op);
					}
				} else {
					if (op.code == OpCode.MOV && (op.operands.get(1).kind == Kind.INT_VAL)) {
						availableOps.add(op);
					}
				}
			}
			ArrayList<Operation> oldAvailableOps = availableOpsMap.get(i);
			availableOpsMap.put(i, availableOps);
			boolean firstTouch = !blockHasBeenTouched.get(i);
			blockHasBeenTouched.set(i);
			if ( (b.strat == Block.BlockStrat.BRANCH || b.strat == Block.BlockStrat.JUMP) && b.blockPointer <= i) {
				
				if (firstTouch || oldAvailableOps.size() != availableOps.size()) {
					i = b.blockPointer-1;
					continue;
				}
				boolean atLeastOneNotFound = false;
				for (Operation newOp : availableOps) {
					boolean found = false;
					for (Operation oldOp : oldAvailableOps) {
						found |= Operation.compareEquals(newOp, oldOp);
					}
					if (!found) {
						atLeastOneNotFound = true;
						break;
					}
				}
				if (atLeastOneNotFound) {
					i = b.blockPointer-1;
					continue;
				}
			}
		}
		// use the computed op avaliability list to make changes
		boolean changedIR = false;
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			
			ArrayList<Operation> availableOps = null;
			for (Integer predecessor : predecessorMap.get(i)) {
				availableOps = mergeOpList(availableOps, availableOpsMap.get(predecessor));
			}
			if (availableOps == null) {
				availableOps = new ArrayList<Operation>();
			}
			
			for (Operation op : b.ops) {
				if (op.operands.size() == 0) {
					continue;
				}
				int j = op.hasOutput() ? 1 : 0;
				for (; j < op.operands.size(); j++) {
					Token checkToken = op.operands.get(j);
					if (checkToken.kind == Kind.IDENT || checkToken.kind == Kind.TEMP) {
						for (int k = 0; k < availableOps.size(); k++) {
							Operation oldOp = availableOps.get(k);
							if (checkToken.lexeme().equals(oldOp.operands.get(0).lexeme())) {
								Token newConst = new Token(oldOp.operands.get(1));
								op.operands.set(j, newConst);
								changedIR = true;
							}
						}
					}
				}
				if (op.code == OpCode.CALL && op.fun.blockEntryPoint > 0) {
					availableOps.clear();
					continue;
				}
				for (int k = 0; k < availableOps.size(); k++) {
					if (op.hasOutput() && (op.operands.get(0).lexeme().equals(availableOps.get(k).operands.get(0).lexeme()))) {
						availableOps.remove(k);
					}
				}
				if (doBoth) {
					if (op.code == OpCode.MOV && ( op.operands.get(1).kind == Kind.INT_VAL ||
					                               op.operands.get(1).kind == Kind.IDENT ||
					                               op.operands.get(1).kind == Kind.TEMP)) {
						availableOps.add(op);
					}
				} else if (doCopyProp) {
					if (op.code == OpCode.MOV && ( op.operands.get(1).kind == Kind.IDENT ||
					                               op.operands.get(1).kind == Kind.TEMP)) {
						availableOps.add(op);
					}
				} else {
					if (op.code == OpCode.MOV && (op.operands.get(1).kind == Kind.INT_VAL)) {
						availableOps.add(op);
					}
				}
			}
			if (b.strat == Block.BlockStrat.BRANCH || (b.strat == Block.BlockStrat.RETURN && b.lastOp.operands.size() > 0)) {
				Token checkToken = b.lastOp.operands.get(0);
				if (checkToken.kind == Kind.IDENT || checkToken.kind == Kind.TEMP) {
					for (int k = 0; k < availableOps.size(); k++) {
						Operation oldOp = availableOps.get(k);
						if (checkToken.lexeme().equals(oldOp.operands.get(0).lexeme())) {
							Token newConst = new Token(oldOp.operands.get(1));
							b.lastOp.operands.set(0, newConst);
							changedIR = true;
						}
					}
				}
			}
		}
		return changedIR;
	}
	
	public boolean constFoldOneStep() {
		Boolean changedIR = false;
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			for (int j = 0; j < b.ops.size(); j++) {
				Operation op = b.ops.get(j);
				if (op.code == OpCode.MOV || op.code == OpCode.LDW || op.code == OpCode.STW || op.code == OpCode.CALL) {
					continue;
				}
				if (op.operands.size() == 3) {
					Token t0 = op.operands.get(0);
					Token t1 = op.operands.get(1);
					Token t2 = op.operands.get(2);
					if (t1.kind == Kind.INT_VAL && t2.kind == Kind.INT_VAL) {
						int x = Integer.parseInt(t1.lexeme());
						int y = Integer.parseInt(t2.lexeme());
						int z;
						switch (op.code) {
						case ADD:
							z = x + y;
						break;
						case SUB:
							z = x - y;
						break;
						case MUL:
							z = x * y;
						break;
						case DIV:
							if (y == 0) {
								System.out.println("ERROR: Hit divide by zero in optimization constant folding! On line " + t0.lineNum + 
									"\nWhile calculating " + (t0.kind == Kind.IDENT ? t0.lexeme : "temp_" + t0.lexeme) + 
									"(" + t0.lineNum + ", " + t0.charPos + ") = " +
									t1.lexeme + "(" + t1.lineNum + ", " + t1.charPos +
									") / " + t2.lexeme + "(" + t2.lineNum + ", " + t2.charPos + ")");
								System.exit(-1);
							}
							z = x / y;
						break;
						case MOD:
							if (y == 0) {
								System.out.println("ERROR: Hit modulo by zero in optimization constant folding! On line " + t0.lineNum + 
									"\nWhile calculating " + (t0.kind == Kind.IDENT ? t0.lexeme : "temp_" + t0.lexeme) + 
									"(" + t0.lineNum + ", " + t0.charPos + ") = " +
									t1.lexeme + "(" + t1.lineNum + ", " + t1.charPos +
									") % " + t2.lexeme + "(" + t2.lineNum + ", " + t2.charPos + ")");
								System.exit(-1);
							}
							z = x % y;
						break;
						case POW:
							z = (int) Math.pow(x, y);
						break;
						case  OR:
							z = (x == 1 || y == 1) ? 1 : 0;
						break;
						case AND:
							z = (x == 1 && y == 1) ? 1 : 0;
						break;
						case XOR:
							z = (x == y) ? 0 : 1;
						break;
						case LSH:
							if (y > 31 || y < -31) {
								z = 0;
							} else if (y > 0) {
								z = x << y;
							} else if (y < 0) {
								z = x >> -y;
							} else {
								z = x;
							}
						break;
						case ASH:
							my_assert(false, "We have not implemented constant folding on arithmatic shift yet!");
							z = 0;
						break;
						case CMP:
						case BIC:
						case CHK:
						default:
							my_assert(false, "Why did we try to constant fold operation " + op.code.toString() + "?");
							z = 0;
						}
						ArrayList<Token> newOperands = buildTokenArray(t0, 
							new Token(Kind.INT_VAL, Integer.valueOf( z ).toString(), t1.lineNum, t1.charPos));
						Operation newOp = new Operation(OpCode.MOV, newOperands);
						b.ops.set(j, newOp);
						changedIR = true;
					//do arithmatic folding for some reason???
					} else if (t1.kind == Kind.INT_VAL && t1.lexeme.equals("0")) {
						Token t = null;
						switch (op.code) {
						case ADD:
						case  OR:
							t = t2;
						break;
						case MUL:
						case DIV:
						case MOD:
						case POW:
						case AND:
						case LSH:
						case ASH:
							t = t1;
						break;
						}
						if (t != null) {
							b.ops.set(j, new Operation(OpCode.MOV, buildTokenArray(t0, t)));
							changedIR = true;
						}
					} else if (t1.kind == Kind.INT_VAL && t1.lexeme.equals("1")) {
						Token t = null;
						switch (op.code) {
						case MUL:
						case AND:
							t = t2;
						break;
						case POW:
						case  OR:
							t = t1;
						}
						if (t != null) {
							b.ops.set(j, new Operation(OpCode.MOV, buildTokenArray(t0, t)));
							changedIR = true;
						}
					} else if (t2.kind == Kind.INT_VAL && t2.lexeme.equals("0")) {
						Token t = null;
						switch (op.code) {
						case ADD:
						case SUB:
						case  OR:
						case LSH:
						case ASH:
							t = t1;
						break;
						case MUL:
						case AND:
							t = t2;
						break;
						case DIV:
							System.out.println("ERROR: Hit divide by zero in optimization constant folding! On line " + t0.lineNum + 
								"\nWhile calculating " + (t0.kind == Kind.IDENT ? t0.lexeme : "temp_" + t0.lexeme) + 
								"(" + t0.lineNum + ", " + t0.charPos + ") = " +
								(t1.kind == Kind.IDENT ? t1.lexeme : "temp_" + t1.lexeme) + "(" + t1.lineNum + ", " + t1.charPos +
								") / " + t2.lexeme + "(" + t2.lineNum + ", " + t2.charPos + ")");
							System.exit(-1);
						break;
						case MOD:
							System.out.println("ERROR: Hit modulo by zero in optimization constant folding! On line " + t0.lineNum + 
								"\nWhile calculating " + (t0.kind == Kind.IDENT ? t0.lexeme : "temp_" + t0.lexeme) + 
								"(" + t0.lineNum + ", " + t0.charPos + ") = " +
								(t1.kind == Kind.IDENT ? t1.lexeme : "temp_" + t1.lexeme) + "(" + t1.lineNum + ", " + t1.charPos +
								") / " + t2.lexeme + "(" + t2.lineNum + ", " + t2.charPos + ")");
							System.exit(-1);
						break;
						case POW:
							t = new Token(Kind.INT_VAL, "1", t2.lineNum, t2.charPos);
						break;
						}
						if (t != null) {
							b.ops.set(j, new Operation(OpCode.MOV, buildTokenArray(t0, t)));
							changedIR = true;
						}
					} else if (t2.kind == Kind.INT_VAL && t2.lexeme.equals("1")) {
						Token t = null;
						switch (op.code) {
						case MUL:
						case AND:
						case POW:
						case DIV:
							t = t1;
						break;
						case  OR:
							t = t2;
						break;
						case MOD:
							t = new Token(Kind.INT_VAL, "0", t2.lineNum, t2.charPos);
						break;
						}
						if (t != null) {
							b.ops.set(j, new Operation(OpCode.MOV, buildTokenArray(t0, t)));
							changedIR = true;
						}
					}
				} else if (op.operands.size() == 2) {
					my_assert(op.code == OpCode.NOT, "Found a 2-operand instruction that is not a NOT in constant folding!");
					Token t1 = op.operands.get(1);
					if (t1.kind == Kind.INT_VAL) {
						my_assert(t1.lexeme.equals("1") || t1.lexeme.equals("0"), 
							"Found a NOT on a bad constant value in constant folding!");
						t1.lexeme = (t1.lexeme.equals("0")) ? "1" : "0";
						op.code = OpCode.MOV;
						changedIR = true;
					}
				} else {
					my_assert(false, "Tried to constant fold an operation with an unexpected number of operands!");
				}
			}
		}
		return changedIR;
	}
	
	public int getFunctionEnd(int blockIndEntry) {
		int end = blocks.size();
		for (FunSymbol fun : syms.funs) {
			int check = fun.blockEntryPoint;
			if (check <= blockIndEntry) {
				continue;
			}
			end = check < end ? check : end;
		}
		// end is 1 past the last block of the function (either because it's the 
		// start of the next function or because we didn't subtract 1 from
		// blocks.size()) so we do that subtraction here.
		return end - 1;
	}
	
////////// Register Allocation //////////
	
	public void convertHardcodedFunctions() {
		for (Block b : blocks) {
			for (Operation op : b.ops) {
				if (op.code == OpCode.CALL && op.fun.blockEntryPoint == -1) {
					switch (op.fun.name) {
					case "readInt"   :
						op.code = OpCode.RDI;
					break;
					case "readFloat" :
						op.code = OpCode.RDF;
					break;
					case "readBool"  :
						op.code = OpCode.RDB;
					break;
					case "println"   :
						op.code = OpCode.WRL;
					break;
					case "printInt"  :
						op.code = OpCode.WRI;
					break;
					case "printFloat":
						op.code = OpCode.WRF;
					break;
					case "printBool" :
						op.code = OpCode.WRB;
					break;
					default:
						my_assert(false, "Hit an unknown hardcoded function!");
					}
					op.fun = null;
				}
			}
		}
	}
	
	public void coaleseReturns() {
		int start = 0;
		while (start < blocks.size()) {
			int end = getFunctionEnd(start);
			ArrayList<Integer> returns = new ArrayList<Integer>();
			for (int block_ind = start; block_ind <= end; block_ind++) {
				if (blocks.get(block_ind).strat == Block.BlockStrat.RETURN) {
					returns.add(block_ind);
				}
			}
			my_assert(returns.size() > 0, "Found a function that does not return!");
			boolean returningValue = blocks.get(returns.get(0)).lastOp.operands.size() > 0;
			if (returns.size() > 1) {
				insertBlockAfter(end);
				end++;
				for (int block_ind : returns) {
					Block fixing = blocks.get(block_ind);
					if (returningValue) {
						Token movIn = fixing.lastOp.operands.get(0);
						Token tempToken = new Token(Kind.TEMP, "0", movIn.lineNum, movIn.charPos);
						fixing.ops.add(new Operation(OpCode.MOV, buildTokenArray(tempToken, movIn)));
					}
					if (block_ind+1 == end) {
						fixing.strat = Block.BlockStrat.FALL;
					} else {
						fixing.jumpFixup(end);
					}
					fixing.lastOp = null;
				}
				if (returningValue) {
					Token temp = new Token(Kind.TEMP, "0", 0, 0);
					blocks.get(end).returnFixup(temp);
				} else {
					blocks.get(end).returnFixup();
				}
			}
			start = end + 1;
		}
	}
	
	public void registerAllocPreProcess() {
		convertHardcodedFunctions();
		removeEmptyBlocks();
		coaleseReturns();
	}
	
	public void addPreamblePostambles() {
		// do a special preamble for main
		
		int start = 0;
		
		// add check to see if any other blocks go to block 0
		// before pushing onto the top
		boolean noLook = true;
		for (Block block : blocks) {
			noLook &= block.blockPointer != start;
		}
		if (!noLook) {
			insertBlockAfter(-1);
		}
		int end = getFunctionEnd(start);
		
		int numOpsPushed = 0;
		Block preamble  = blocks.get(start);
		Block postamble = blocks.get(end);
		Token RP = new Token(Kind.REGISTER, "31", 0, 0); // return
		Token GP = new Token(Kind.REGISTER, "30", 0, 0); // globals
		Token SP = new Token(Kind.REGISTER, "29", 0, 0); // stack
		Token FP = new Token(Kind.REGISTER, "28", 0, 0); // frame
		Token R0 = new Token(Kind.REGISTER,  "0", 0, 0);
		// size of globals
		Token val100 = new Token(Kind.INT_VAL, "100", 0, 0); 
		// size of locals
		Token val20 = new Token(Kind.INT_VAL, "20", 0, 0); 
		// set r29 and r28 to be r30 - the size of all globals
		preamble.ops.add(numOpsPushed++, new Operation(OpCode.SUBI, buildTokenArray(FP, GP, val100)));
		preamble.ops.add(numOpsPushed++, new Operation(OpCode.SUBI, buildTokenArray(SP, GP, val100)));
		// push locals
		preamble.ops.add(numOpsPushed++, new Operation(OpCode.SUBI, buildTokenArray(SP, SP, val20)));
		 
		
		// do a special postamble for main
		// set r31 to be 0 before return
		postamble.ops.add(new Operation(OpCode.ADD, buildTokenArray(RP, R0, R0)));
		
		// for all user-def functions
		{
		// do preamble
		// push locals
		
		// do postamble
		// pop locals
		}
	}
	
////////// Emitting Bytecode //////////

	public int[] emitBytecode() {
		ArrayList<Operation> bytecodeBuilder = new ArrayList<Operation>();
		
		HashMap<Integer, ArrayList<Integer>> backlinks = new HashMap<Integer, ArrayList<Integer>>(); //block, index
		ArrayList<Integer> blockstart = new ArrayList<Integer>();
		
		Token R0 = new Token(Kind.REGISTER, "0", 0, 0);
		
		int current_op = 0;
		for(int block_ind = 0; block_ind < blocks.size(); block_ind++) {
			
			blockstart.add(current_op);
			if (backlinks.containsKey(block_ind)) {
				for (Integer builder_ind : backlinks.get(block_ind)) {
					Operation link = bytecodeBuilder.get(builder_ind);
					int link_val = current_op - builder_ind;
					for (int operand_ind = 0; operand_ind < link.operands.size(); operand_ind++) {
						if (link.operands.get(operand_ind).kind == Kind.TEMP) {
							link.operands.set(operand_ind, new Token(Kind.INT_VAL, Integer.valueOf(link_val).toString(), 0, 0));
						}
					}
				}
				backlinks.remove(block_ind);
			}
			
			Block block = blocks.get(block_ind);
			for(Operation op : block.ops) {
				if (op.code == OpCode.MOV) {
					op.code = OpCode.ADD;
					op.operands.add(R0);
				}
				if (op.code == OpCode.NOT) {
					op.code = OpCode.XORI;
					op.operands.add(new Token(Kind.INT_VAL, "1", 0, 0));
				}
				if (op.code == OpCode.CALL) {
					op.code = OpCode.BSR;
					op.operands = new ArrayList<Token>();
					Token branch_offset;
					if (op.fun.blockEntryPoint > block_ind) {
						if (!backlinks.containsKey(op.fun.blockEntryPoint)) {
							backlinks.put(op.fun.blockEntryPoint, new ArrayList<Integer>());
						}
						backlinks.get(op.fun.blockEntryPoint).add(current_op);
						branch_offset = new Token(Kind.TEMP, "0", 0, 0);
					} else {
						int branch_ind_val = blockstart.get(op.fun.blockEntryPoint) - current_op;
						branch_offset = new Token(Kind.INT_VAL, Integer.valueOf(branch_ind_val).toString(), 0, 0);
					}
					op.operands.add(branch_offset);
				}
				my_assert(op.operands.size() <= 3, "Operand " + op.code + " at " + current_op + " has too many operands!");
				bytecodeBuilder.add(op);
				current_op++;
			}
			if (block.strat == Block.BlockStrat.JUMP || block.strat == Block.BlockStrat.BRANCH) {
				Token branch_offset;
				if (block.blockPointer > block_ind) {
					if (!backlinks.containsKey(block.blockPointer)) {
						backlinks.put(block.blockPointer, new ArrayList<Integer>());
					}
					backlinks.get(block.blockPointer).add(current_op);
					branch_offset = new Token(Kind.TEMP, "0", 0, 0);
				} else {
					int branch_ind_val = blockstart.get(block.blockPointer) - current_op;
					branch_offset = new Token(Kind.INT_VAL, Integer.valueOf(branch_ind_val).toString(), 0, 0);
				}
				Operation op;
				if (block.strat == Block.BlockStrat.JUMP) {
					op = new Operation(OpCode.BEQ, buildTokenArray(R0, branch_offset));
				} else { //BRANCH
					op = block.lastOp;
					op.operands.add(branch_offset);
				}
				bytecodeBuilder.add(op);
				current_op++;
			} else if (block.strat == Block.BlockStrat.RETURN) {
				bytecodeBuilder.add(block.lastOp);
				current_op++;
			}
		}
		
		// copy the dynamically allocated bytecode to static and return
		int[] bytecode = new int[bytecodeBuilder.size()];
		for (int i = 0; i < bytecodeBuilder.size(); i++) {
//			System.out.println(bytecode[i]);
			Operation op = bytecodeBuilder.get(i);
			int code = op.code.opcode;
			int assembled_op;
			switch (op.operands.size()) {
			case 0:
				assembled_op = DLX.assemble(code);
			break;
			case 1:
				assembled_op = DLX.assemble(code,
					Integer.parseInt(op.operands.get(0).lexeme));
			break;
			case 2:
				assembled_op = DLX.assemble(code,
					Integer.parseInt(op.operands.get(0).lexeme),
					Integer.parseInt(op.operands.get(1).lexeme));
			break;
			case 3:
				assembled_op = DLX.assemble(code,
					Integer.parseInt(op.operands.get(0).lexeme),
					Integer.parseInt(op.operands.get(1).lexeme),
					Integer.parseInt(op.operands.get(2).lexeme));
			break;
			default:
				my_assert(false, "Tried to emit opcode with more than 3 operands!");
				assembled_op = 0xBABABABA;
			}
			bytecode[i] = assembled_op;
		}
		return bytecode;
	}

}