package compiler;

import java.util.ArrayList;
import java.util.Stack;
import java.util.BitSet;
import java.util.Dictionary;
import java.util.Hashtable;

public class RegAlloc{
	public IR regAllocIR;
	private ArrayList<regInterferenceNode> riGraph;
	public int numRegs;
	private Stack<regInterferenceNode> nodeStack;
	public Dictionary<Integer,String> regVals;
	private Dictionary<Integer,String> spillRegs;
	
	public int getSpillReg(String var) {
		for (int i = numRegs - 1; i <= numRegs; i++) {
			if (spillRegs.get(i).equals("")) {
				spillRegs.put(i,var);
				return i;
			}
		}
		return -1;
	}
	
	public RegAlloc (IR ir, int regs) {
		regAllocIR = ir;
		numRegs = regs;
		ir.registerAllocPreProcess();
		generateOffsets();
		regInterference();
		colorGraph();
		regVals = new Hashtable<>();
		spillRegs = new Hashtable<>();
		for (int i = 1; i <= regs - 2; i++) {
			regVals.put(i,"");
		}
		for (int i = regs - 1; i <= regs; i++) {
			spillRegs.put(i,"");
		}
		genRegAlloc();
//		threeRegAlloc();
	}
	
	private void genRegAlloc() {
		
		for (regInterferenceGraph graph:regAllocIR.riGraphs) {
			Boolean funcPreamble = false;
			for (int k = 1; k <= numRegs-2; k++) {
				regVals.put(k, "");
			}
			if (graph.function != null) {
				funcPreamble = true;
			}
			for (int i = graph.start; i <= graph.end; i++) {
				if (i == 0) {
					ArrayList<Token> pushSPMainOprnds = new ArrayList<Token>();
					pushSPMainOprnds.add(new Token(Kind.REGISTER,"29",0,0));
					pushSPMainOprnds.add(new Token(Kind.REGISTER,"0",0,0));
					pushSPMainOprnds.add(new Token(Kind.INT_VAL,Integer.toString(9999+regAllocIR.GDBoffset),0,0));
					Operation pushSPMainOp = new Operation(OpCode.ADDI,pushSPMainOprnds);
					regAllocIR.blocks.get(i).ops.add(0,pushSPMainOp);
					
					ArrayList<Token> pushFPMainOprnds = new ArrayList<Token>();
					pushFPMainOprnds.add(new Token(Kind.REGISTER,"28",0,0));
					pushFPMainOprnds.add(new Token(Kind.REGISTER,"0",0,0));
					pushFPMainOprnds.add(new Token(Kind.INT_VAL,Integer.toString(9999+regAllocIR.GDBoffset),0,0));
					Operation pushFPMainOp = new Operation(OpCode.ADDI,pushFPMainOprnds);
					regAllocIR.blocks.get(i).ops.add(1,pushFPMainOp);
				}
				for (int j = (i == 0) ? 2:0; j < regAllocIR.blocks.get(i).ops.size(); j++) {
					int currOpNum = j;
					if (funcPreamble) {
						ArrayList<Token> pushLocalsOprnds = new ArrayList<Token>();
						pushLocalsOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						pushLocalsOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						pushLocalsOprnds.add(new Token(Kind.INT_VAL,Integer.toString(graph.function.fpOffset),0,0));
						Operation pushLocalsOp = new Operation(OpCode.ADDI,pushLocalsOprnds);
						regAllocIR.blocks.get(i).ops.add(0,pushLocalsOp);
						currOpNum++;
						j++;
						funcPreamble = false;
					}
					if (regAllocIR.blocks.get(i).ops.get(currOpNum).code.name.equals("CALL")) {
						//Function call preamble
						for (int k = 1; k <= numRegs-2; k++) {
							ArrayList<Token> ops = new ArrayList<Token>();
							ops.add(new Token(Kind.REGISTER,Integer.toString(k),0,0));
							ops.add(new Token(Kind.REGISTER,"29",0,0));
							ops.add(new Token(Kind.INT_VAL,"-4",0,0));
							Operation newOp = new Operation(OpCode.PSH,ops);
							regAllocIR.blocks.get(i).ops.add(currOpNum,newOp);
							currOpNum++;
							j++;
						}
						
						if (regAllocIR.blocks.get(i).ops.get(currOpNum).fun.inputs.size() == 0) {
							ArrayList<Token> ops = new ArrayList<Token>();
							ops.add(new Token(Kind.REGISTER,"29",0,0));
							ops.add(new Token(Kind.REGISTER,"29",0,0));
							ops.add(new Token(Kind.INT_VAL,"-4",0,0));
							Operation newOp = new Operation(OpCode.ADDI,ops);
							regAllocIR.blocks.get(i).ops.add(currOpNum,newOp);
							currOpNum++;
							j++;
						} else {
							for (int k = (regAllocIR.blocks.get(i).ops.get(currOpNum).fun.output == Kind.VOID) ? 0 : 1;k < regAllocIR.blocks.get(i).ops.get(currOpNum).operands.size();k++) {
								int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
								if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.IDENT || regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.TEMP) {
									if (reg == -1) {
										reg = numRegs-1;
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										j++;
										currOpNum++;
									} else if (regVals.get(reg) == "") {
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										j++;
										currOpNum++;
									} else if (!regVals.get(reg).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme)) {
										Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,storeOp);
										currOpNum++;
										j++;
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										j++;
										currOpNum++;
									}
								} else if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.INT_VAL) {
									reg = numRegs-1;
									ArrayList<Token> constantOprnds = new ArrayList<Token>();
									constantOprnds.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
									constantOprnds.add(new Token(Kind.REGISTER,"0",0,0));
									constantOprnds.add(new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0));
									//new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0)
									Operation constantOp = new Operation(OpCode.ADDI,constantOprnds);
									regAllocIR.blocks.get(i).ops.add(currOpNum,constantOp);
									currOpNum++;
									j++;
								}
								ArrayList<Token> ops = new ArrayList<Token>();
								ops.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
								ops.add(new Token(Kind.REGISTER,"29",0,0));
								ops.add(new Token(Kind.INT_VAL,"-4",0,0));
								Operation newOp = new Operation(OpCode.PSH,ops);
								regAllocIR.blocks.get(i).ops.add(currOpNum,newOp);
								currOpNum++;
								j++;
								spillRegs.put(reg, "");
							}
						}
						
						ArrayList<Token> raPshOprnds = new ArrayList<Token>();
						raPshOprnds.add(new Token(Kind.REGISTER,"31",0,0));
						raPshOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						raPshOprnds.add(new Token(Kind.INT_VAL,"-4",0,0));
						Operation raPshOp = new Operation(OpCode.PSH,raPshOprnds);
						regAllocIR.blocks.get(i).ops.add(currOpNum,raPshOp);
						currOpNum++;
						j++;
						
						ArrayList<Token> fpPshOprnds = new ArrayList<Token>();
						fpPshOprnds.add(new Token(Kind.REGISTER,"28",0,0));
						fpPshOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						fpPshOprnds.add(new Token(Kind.INT_VAL,"-4",0,0));
						Operation fpPshOp = new Operation(OpCode.PSH,fpPshOprnds);
						regAllocIR.blocks.get(i).ops.add(currOpNum,fpPshOp);
						currOpNum++;
						j++;
						
						ArrayList<Token> setSpOprnds = new ArrayList<Token>();
						setSpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						setSpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						setSpOprnds.add(new Token(Kind.INT_VAL,"-4",0,0));
						Operation setSpOp = new Operation(OpCode.ADDI,setSpOprnds);
						regAllocIR.blocks.get(i).ops.add(currOpNum,setSpOp);
						currOpNum++;
						j++;
						
						ArrayList<Token> fpIsSpOprnds = new ArrayList<Token>();
						fpIsSpOprnds.add(new Token(Kind.REGISTER,"28",0,0));
						fpIsSpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						fpIsSpOprnds.add(new Token(Kind.REGISTER,"0",0,0));
						Operation fpIsSpOp = new Operation(OpCode.ADD,fpIsSpOprnds);
						regAllocIR.blocks.get(i).ops.add(currOpNum,fpIsSpOp);
						currOpNum++;
						j++;
						
						//Function call postamble
						ArrayList<Token> spIsFpOprnds = new ArrayList<Token>();
						spIsFpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						spIsFpOprnds.add(new Token(Kind.REGISTER,"28",0,0));
						spIsFpOprnds.add(new Token(Kind.REGISTER,"0",0,0));
						Operation spIsFpOp = new Operation(OpCode.ADD,spIsFpOprnds);
						regAllocIR.blocks.get(i).ops.add(j+1,spIsFpOp);
						j++;
						
						ArrayList<Token> resetSpOprnds = new ArrayList<Token>();
						resetSpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						resetSpOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						resetSpOprnds.add(new Token(Kind.INT_VAL,"4",0,0));
						Operation resetSpOp = new Operation(OpCode.ADDI,resetSpOprnds);
						regAllocIR.blocks.get(i).ops.add(j+1,resetSpOp);
						j++;
						
						ArrayList<Token> fpPopOprnds = new ArrayList<Token>();
						fpPopOprnds.add(new Token(Kind.REGISTER,"28",0,0));
						fpPopOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						fpPopOprnds.add(new Token(Kind.INT_VAL,"4",0,0));
						Operation fpPopOp = new Operation(OpCode.POP,fpPopOprnds);
						regAllocIR.blocks.get(i).ops.add(j+1,fpPopOp);
						j++;
						
						ArrayList<Token> raPopOprnds = new ArrayList<Token>();
						raPopOprnds.add(new Token(Kind.REGISTER,"31",0,0));
						raPopOprnds.add(new Token(Kind.REGISTER,"29",0,0));
						raPopOprnds.add(new Token(Kind.INT_VAL,"4",0,0));
						Operation raPopOp = new Operation(OpCode.POP,raPopOprnds);
						regAllocIR.blocks.get(i).ops.add(j+1,raPopOp);
						j++;
						
						int returnReg = -1;
						if (regAllocIR.blocks.get(i).ops.get(currOpNum).fun.output != Kind.VOID) {
							if (regAllocIR.blocks.get(i).ops.get(currOpNum).fun.inputs.size() > 1) {
								ArrayList<Token> getRetValOffsetOprnds = new ArrayList<Token>();
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.INT_VAL,Integer.toString((regAllocIR.blocks.get(i).ops.get(currOpNum).fun.inputs.size()-1)*4),0,0));
								Operation getRetValOffsetOp = new Operation(OpCode.ADDI,getRetValOffsetOprnds);
								regAllocIR.blocks.get(i).ops.add(j+1,getRetValOffsetOp);
								j++;
							}
							
							Operation storeOp = null;
							int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							if (reg == -1) {
								reg = numRegs-1;
								storeOp = store(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,regAllocIR.blocks.get(i));
							}
							
							ArrayList<Token> retValPopOprnds = new ArrayList<Token>();
							retValPopOprnds.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							retValPopOprnds.add(new Token(Kind.REGISTER,"29",0,0));
							retValPopOprnds.add(new Token(Kind.INT_VAL,"4",0,0));
							Operation retValPopOp = new Operation(OpCode.POP,retValPopOprnds);
							regAllocIR.blocks.get(i).ops.add(j+1,retValPopOp);
							j++;
							regVals.put(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							returnReg = reg;
							if (storeOp != null) {
								regAllocIR.blocks.get(i).ops.add(j+1,storeOp); 
								j++;
							}
						} else {
							if (regAllocIR.blocks.get(i).ops.get(currOpNum).fun.inputs.size() <= 1) {
								ArrayList<Token> getRetValOffsetOprnds = new ArrayList<Token>();
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.INT_VAL,"4",0,0));
								Operation getRetValOffsetOp = new Operation(OpCode.ADDI,getRetValOffsetOprnds);
								regAllocIR.blocks.get(i).ops.add(j+1,getRetValOffsetOp);
								j++;
							} else {
								ArrayList<Token> getRetValOffsetOprnds = new ArrayList<Token>();
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.REGISTER,"29",0,0));
								getRetValOffsetOprnds.add(new Token(Kind.INT_VAL,Integer.toString((regAllocIR.blocks.get(i).ops.get(currOpNum).fun.inputs.size())*4),0,0));
								Operation getRetValOffsetOp = new Operation(OpCode.ADDI,getRetValOffsetOprnds);
								regAllocIR.blocks.get(i).ops.add(j+1,getRetValOffsetOp);
								j++;
							}
						}
						
						for (int k = numRegs-2; k >= 1; k--) {
							if (k != returnReg) {
								ArrayList<Token> ops = new ArrayList<Token>();
								ops.add(new Token(Kind.REGISTER,Integer.toString(k),0,0));
								ops.add(new Token(Kind.REGISTER,"29",0,0));
								ops.add(new Token(Kind.INT_VAL,"4",0,0));
								Operation newOp = new Operation(OpCode.POP,ops);
								regAllocIR.blocks.get(i).ops.add(j+1,newOp);
								j++;
							} else {
								ArrayList<Token> ops = new ArrayList<Token>();
								ops.add(new Token(Kind.REGISTER,"29",0,0));
								ops.add(new Token(Kind.REGISTER,"29",0,0));
								ops.add(new Token(Kind.INT_VAL,"4",0,0));
								Operation newOp = new Operation(OpCode.ADDI,ops);
								regAllocIR.blocks.get(i).ops.add(j+1,newOp);
								j++;
							}
						}
					} else if (regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode >= 56 && regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode <= 58) {
						int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
						if (reg == -1) {
							reg = numRegs-1;
							Operation storeOp = store(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,regAllocIR.blocks.get(i));
							regAllocIR.blocks.get(i).ops.add(currOpNum+1,storeOp);
							j++;
						} else if (regVals.get(reg) == "") {
							regVals.put(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
						} else if (!regVals.get(reg).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme)){
							Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
							regAllocIR.blocks.get(i).ops.add(currOpNum,storeOp);
							currOpNum++;
							j++;
							regVals.put(reg, regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
						}
						regAllocIR.blocks.get(i).ops.get(currOpNum).operands.set(0, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
					} else if (regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode >= 59 && regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode <= 61) {
						if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).kind == Kind.TEMP || regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).kind == Kind.IDENT) {
							int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							if (reg == -1) {
								reg = numRegs-1;
								Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,regAllocIR.blocks.get(i));
								regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
								currOpNum++;
								j++;
							} else if (regVals.get(reg) == "") {
								Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,regAllocIR.blocks.get(i));
								regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
								currOpNum++;
								j++;
								regVals.put(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							} else if (!regVals.get(reg).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme)){
								Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
								regAllocIR.blocks.get(i).ops.add(currOpNum,storeOp);
								currOpNum++;
								j++;
								Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,regAllocIR.blocks.get(i));
								regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
								currOpNum++;
								j++;
								regVals.put(reg, regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							}
							regAllocIR.blocks.get(i).ops.get(currOpNum).operands.set(0, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
						} else if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).kind == Kind.INT_VAL) {
							ArrayList<Token> constantOprnds = new ArrayList<Token>();
							constantOprnds.add(new Token(Kind.REGISTER,Integer.toString(numRegs-1),0,0));
							constantOprnds.add(new Token(Kind.REGISTER,"0",0,0));
							constantOprnds.add(new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme,0,0));
							//new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0))
							Operation constantOp = new Operation(OpCode.ADDI,constantOprnds);
							regAllocIR.blocks.get(i).ops.add(currOpNum,constantOp);
							currOpNum++;
							j++;
							regAllocIR.blocks.get(i).ops.get(currOpNum).operands.set(0, new Token(Kind.REGISTER,Integer.toString(numRegs-1),0,0));
						}
					} else {
						for (int k = regAllocIR.blocks.get(i).ops.get(currOpNum).operands.size()-1;k>=0;k--) {
							if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.TEMP || regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.IDENT) {
								int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
								if (reg == -1) {
									reg = getSpillReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
									if (k != 0) {
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
//										Operation storeOp = store(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										j++;
										currOpNum++;
//										regAllocIR.blocks.get(i).ops.add(currOpNum+1,storeOp);
//										j++;
									} else {
										Operation storeOp = store(numRegs-1,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum+1,storeOp);
										j++;
									}
								} else if (regVals.get(reg) == "") {
									if (k != 0) {
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										j++;
										currOpNum++;
										regVals.put(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
									} else {
										regVals.put(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
									}
								} else if (!regVals.get(reg).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme)){
									// If not working store what is in result register
									if (k != 0) {
										Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
										Operation loadOp = load(reg,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,storeOp);
										currOpNum++;
										regAllocIR.blocks.get(i).ops.add(currOpNum,loadOp);
										currOpNum++;
										regVals.put(reg, regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
										j+=2;
									} else {
										Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
										regAllocIR.blocks.get(i).ops.add(currOpNum,storeOp);
										currOpNum++;
										j++;
										regVals.put(reg, regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
									}
								}
							} else if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.INT_VAL && !regAllocIR.blocks.get(i).ops.get(currOpNum).code.name.equals("MOV")) {
								int reg = getSpillReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
								ArrayList<Token> constantOprnds = new ArrayList<Token>();
								constantOprnds.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
								constantOprnds.add(new Token(Kind.REGISTER,"0",0,0));
								constantOprnds.add(new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0));
								//new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0))
								Operation constantOp = new Operation(OpCode.ADDI,constantOprnds);
								regAllocIR.blocks.get(i).ops.add(currOpNum,constantOp);
								currOpNum++;
								j++;
								regAllocIR.blocks.get(i).ops.get(currOpNum).operands.set(k, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							}
						}
						if (regAllocIR.blocks.get(i).ops.get(currOpNum).code.name.equals("MOV")) {
							ArrayList<Token> ops = new ArrayList<Token>();
							Operation newOp;
							int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme);
							if (reg == -1) {
//								for (int l = numRegs-2; l <= numRegs; l++) {
//									if (spillRegs.get(l).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(0).lexeme)) {
//										reg = l;
//										break;
//									}
//								}
								reg = numRegs-1;
							}
							ops.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							ops.add(new Token(Kind.REGISTER,"0",0,0));
							if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(1).kind == Kind.TEMP || regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(1).kind == Kind.IDENT) {
								reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(1).lexeme);
								if (reg == -1) {
//									for (int l = numRegs-2; l <= numRegs; l++) {
//										if (spillRegs.get(l).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(1).lexeme)) {
//											reg = l;
//											break;
//										}
//									}
									reg = numRegs-1;
								}
								ops.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
								newOp = new Operation(OpCode.ADD,ops);
							} else {
								ops.add(new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(1).lexeme,0,0));
								newOp = new Operation(OpCode.ADDI,ops);
							}
							regAllocIR.blocks.get(i).ops.set(currOpNum,newOp);
						} else {
							for (int k = regAllocIR.blocks.get(i).ops.get(currOpNum).operands.size()-1;k>=0;k--) {
								if (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.TEMP || regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).kind == Kind.IDENT) {
									int reg = graph.getReg(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme);
									if (reg == -1) {
										for (int l = numRegs-1; l <= numRegs; l++) {
											if (spillRegs.get(l).equals(regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme)) {
												reg = l;
												break;
											}
										}
									}
									regAllocIR.blocks.get(i).ops.get(currOpNum).operands.set(k, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
								}
							}
						}
					}
					if ((regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode == 40 || regAllocIR.blocks.get(i).ops.get(currOpNum).code.opcode == 43) && (regAllocIR.blocks.get(i).ops.get(currOpNum).operands.size() < 3)) {
						regAllocIR.blocks.get(i).ops.get(currOpNum).operands.add(new Token(Kind.INT_VAL,"0",0,0));
					}
					for (int l = numRegs - 1; l <= numRegs; l++) {
						spillRegs.put(l,"");
					}
				}
				if (regAllocIR.blocks.get(i).lastOp != null) {
					if (regAllocIR.blocks.get(i).lastOp.operands.size() > 0) {
						int reg = 0;
						for (int k = regAllocIR.blocks.get(i).lastOp.operands.size()-1;k>=0;k--) {
							if (regAllocIR.blocks.get(i).lastOp.operands.get(k).kind == Kind.TEMP || regAllocIR.blocks.get(i).lastOp.operands.get(k).kind == Kind.IDENT) {
								reg = graph.getReg(regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme);
								if (reg == -1) {
									reg = getSpillReg(regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme);
									Operation loadOp = load(reg,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme,regAllocIR.blocks.get(i));
	//									Operation storeOp = store(reg,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme,regAllocIR.blocks.get(i));
									regAllocIR.blocks.get(i).ops.add(loadOp);
								} else if (regVals.get(reg) == "") {
									Operation loadOp = load(reg,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme,regAllocIR.blocks.get(i));
									regAllocIR.blocks.get(i).ops.add(loadOp);
									regVals.put(reg,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme);
								} else if (!regVals.get(reg).equals(regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme)){
									// If not working store what is in result register
									Operation storeOp = store(reg,regVals.get(reg),regAllocIR.blocks.get(i));
									Operation loadOp = load(reg,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme,regAllocIR.blocks.get(i));
									regAllocIR.blocks.get(i).ops.add(storeOp);
									regAllocIR.blocks.get(i).ops.add(loadOp);
									regVals.put(reg, regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme);
								}
								regAllocIR.blocks.get(i).lastOp.operands.set(k, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							} else if (regAllocIR.blocks.get(i).lastOp.operands.get(k).kind == Kind.INT_VAL) {
								reg = getSpillReg(regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme);
								ArrayList<Token> constantOprnds = new ArrayList<Token>();
								constantOprnds.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
								constantOprnds.add(new Token(Kind.REGISTER,"0",0,0));
								constantOprnds.add(new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).lastOp.operands.get(k).lexeme,0,0));
								//new Token(Kind.INT_VAL,regAllocIR.blocks.get(i).ops.get(currOpNum).operands.get(k).lexeme,0,0)
								Operation constantOp = new Operation(OpCode.ADDI,constantOprnds);
								regAllocIR.blocks.get(i).ops.add(constantOp);
								regAllocIR.blocks.get(i).lastOp.operands.set(k, new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							} 
						}
						if (regAllocIR.blocks.get(i).func != null && regAllocIR.blocks.get(i).lastOp.code == OpCode.RET) {
							ArrayList<Token> retValOprnds = new ArrayList<Token>();
							retValOprnds.add(new Token(Kind.REGISTER,Integer.toString(reg),0,0));
							retValOprnds.add(new Token(Kind.REGISTER,"28",0,0));
							if (regAllocIR.blocks.get(i).func.inputs.size() < 1) {
								retValOprnds.add(new Token(Kind.INT_VAL,"12",0,0));
							}
							retValOprnds.add(new Token(Kind.INT_VAL,Integer.toString((regAllocIR.blocks.get(i).func.inputs.size()*4)+8),0,0));
							Operation retValOp = new Operation(OpCode.STW,retValOprnds);
							regAllocIR.blocks.get(i).ops.add(retValOp);
							regAllocIR.blocks.get(i).lastOp.operands.set(0, new Token(Kind.REGISTER,"31",0,0));
						}
					} else if (regAllocIR.blocks.get(i).lastOp.code == OpCode.RET) {
						regAllocIR.blocks.get(i).lastOp.operands.add(new Token(Kind.INT_VAL,"0",0,0));
					}
				}
			}
		}
	}
	
	private void storeVar(int block, int op, String name, Boolean func) {
		
		regAllocIR.registerAllocPreProcess();
		threeRegAlloc();
		regAllocIR.addPreamblePostambles();
	}
	
	private void threeRegAlloc() {
		for (Block blocks : regAllocIR.blocks) {
			if (blocks.ops.size() == 0 && blocks.lastOp != null) {
				switch (blocks.lastOp.operands.get(0).kind) {
					case IDENT:
						ArrayList<Token> ops = new ArrayList<Token>();
						if (blocks.func != null) {
							int offset = getLocalOffset(blocks.lastOp.operands.get(0).lexeme,blocks);
							ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
							if (offset == -1) {
								offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
								ops.add(new Token(Kind.REGISTER,"30",0,0));
							} else {
								ops.add(new Token(Kind.REGISTER,"28",0,0));
							}
							ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
						} else {
							int offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
							ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
							ops.add(new Token(Kind.REGISTER,"30",0,0));
							ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
						}
						Operation newOp = new Operation(OpCode.LDW,ops);
						blocks.ops.add(0,newOp);
						break;
					case TEMP:
						ops = new ArrayList<Token>();
						if (blocks.func != null) {
							int offset = getLocalOffset(blocks.lastOp.operands.get(0).lexeme,blocks);
							ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
							if (offset == -1) {
								offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
								ops.add(new Token(Kind.REGISTER,"30",0,0));
							} else {
								ops.add(new Token(Kind.REGISTER,"28",0,0));
							}
							ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
						} else {
							int offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
							ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
							ops.add(new Token(Kind.REGISTER,"30",0,0));
							ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
						}
						newOp = new Operation(OpCode.LDW,ops);
						blocks.ops.add(0,newOp);
						break;
					case TRUE:
						ops = new ArrayList<Token>();
						ops.add(new Token(Kind.REGISTER,"1",0,0));
						ops.add(new Token(Kind.REGISTER,"0",0,0));
						ops.add(new Token(Kind.INT_VAL,"1",0,0));
						newOp = new Operation(OpCode.ORI,ops);
						blocks.ops.add(0,newOp);
						break;
					case FALSE:
						ops = new ArrayList<Token>();
						ops.add(new Token(Kind.REGISTER,"1",0,0));
						ops.add(new Token(Kind.REGISTER,"0",0,0));
						ops.add(new Token(Kind.INT_VAL,"0",0,0));
						newOp = new Operation(OpCode.ORI,ops);
						blocks.ops.add(0,newOp);
						break;
					case INT_VAL:
						ops = new ArrayList<Token>();
						ops.add(new Token(Kind.REGISTER,"1",0,0));
						ops.add(new Token(Kind.REGISTER,"0",0,0));
						ops.add(new Token(Kind.INT_VAL,blocks.lastOp.operands.get(0).lexeme,0,0));
						newOp = new Operation(OpCode.ORI,ops);
						blocks.ops.add(0,newOp);
						break;
				}
			
				blocks.lastOp.operands.set(0, new Token(Kind.REGISTER,"1",0,0));
				continue;
			}
			for (int i = 0; i < blocks.ops.size(); i++) {
				if (blocks.ops.get(i).code != OpCode.RET) {
					for (int j = blocks.ops.get(i).operands.size()-1; j >= 0; j--) {
						if (j == 0 && blocks.ops.get(i).code != OpCode.CALL) {
							if (blocks.ops.get(i).operands.get(j).kind == Kind.IDENT || blocks.ops.get(i).operands.get(j).kind == Kind.TEMP) {
								ArrayList<Token> ops = new ArrayList<Token>();
								if (blocks.func != null) {
									int offset = getLocalOffset(blocks.ops.get(i).operands.get(j).lexeme,blocks);
									ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
									if (offset == -1) {
										offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
										ops.add(new Token(Kind.REGISTER,"30",0,0));
									} else {
										ops.add(new Token(Kind.REGISTER,"28",0,0));
									}
									ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
								} else {
									int offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
									ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
									ops.add(new Token(Kind.REGISTER,"30",0,0));
									ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
								}
								Operation newOp = new Operation(OpCode.STW,ops);
								blocks.ops.add(i+1,newOp);
							}
						} else {
							switch (blocks.ops.get(i).operands.get(j).kind) {
								case IDENT:
									ArrayList<Token> ops = new ArrayList<Token>();
									if (blocks.func != null) {
										int offset = getLocalOffset(blocks.ops.get(i).operands.get(j).lexeme,blocks);
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
										if (offset == -1) {
											offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
											ops.add(new Token(Kind.REGISTER,"30",0,0));
										} else {
											ops.add(new Token(Kind.REGISTER,"28",0,0));
										}
										ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
									} else {
										int offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
										ops.add(new Token(Kind.REGISTER,"30",0,0));
										ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
									}
									Operation newOp = new Operation(OpCode.LDW,ops);
									blocks.ops.add(i,newOp);
									i++;
									break;
								case TEMP:
									ops = new ArrayList<Token>();
									if (blocks.func != null) {
										int offset = getLocalOffset(blocks.ops.get(i).operands.get(j).lexeme,blocks);
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
										if (offset == -1) {
											offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
											ops.add(new Token(Kind.REGISTER,"30",0,0));
										} else {
											ops.add(new Token(Kind.REGISTER,"28",0,0));
										}
										ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
									} else {
										int offset = getGlobalOffset(blocks.ops.get(i).operands.get(j).lexeme);
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
										ops.add(new Token(Kind.REGISTER,"30",0,0));
										ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
									}
									newOp = new Operation(OpCode.LDW,ops);
									blocks.ops.add(i,newOp);
									i++;
									break;
								case TRUE:
									ops = new ArrayList<Token>();
									if (blocks.ops.get(i).code == OpCode.MOV) {
										ops.add(new Token(Kind.REGISTER,"1",0,0));
									} else {
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
									}
									ops.add(new Token(Kind.REGISTER,"0",0,0));
									ops.add(blocks.ops.get(i).operands.get(j));
									newOp = new Operation(OpCode.ORI,ops);
									blocks.ops.add(i,newOp);
									i++;
									break;
								case FALSE:
									ops = new ArrayList<Token>();
									if (blocks.ops.get(i).code == OpCode.MOV) {
										ops.add(new Token(Kind.REGISTER,"1",0,0));
									} else {
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
									}
									ops.add(new Token(Kind.REGISTER,"0",0,0));
									ops.add(blocks.ops.get(i).operands.get(j));
									newOp = new Operation(OpCode.ORI,ops);
									blocks.ops.add(i,newOp);
									i++;
									break;
								case INT_VAL:
									ops = new ArrayList<Token>();
									if (blocks.ops.get(i).code == OpCode.MOV) {
										ops.add(new Token(Kind.REGISTER,"1",0,0));
									} else {
										ops.add(new Token(Kind.REGISTER,Integer.toString(j+1),0,0));
									}
									ops.add(new Token(Kind.REGISTER,"0",0,0));
									ops.add(blocks.ops.get(i).operands.get(j));
									newOp = new Operation(OpCode.ADDI,ops);
									blocks.ops.add(i,newOp);
									i++;
									break;
							}
						}
					}
					if (blocks.ops.get(i).code == OpCode.CALL) {
						for (int k = blocks.ops.get(i).operands.size()-1; k >= 0; k--) {
							blocks.ops.get(i).operands.set(k,new Token(Kind.REGISTER,Integer.toString(k+1),0,0));
						}
					} else if (blocks.ops.get(i).code != OpCode.MOV) {
						ArrayList<Token> ops = new ArrayList<Token>();
						ops.add(new Token(Kind.REGISTER,"1",0,0));
						ops.add(new Token(Kind.REGISTER,"2",0,0));
						ops.add(new Token(Kind.REGISTER,"3",0,0));
						Operation newOp = new Operation(blocks.ops.get(i).code,ops);
						blocks.ops.set(i, newOp);
						i++;
					} else {
						blocks.ops.remove(i);
					}
//					i++;
				}
				if ((i == blocks.ops.size() - 1) && (blocks.func != null) && (blocks.lastOp != null)) {
					switch (blocks.lastOp.operands.get(0).kind) {
						case IDENT:
							ArrayList<Token> ops = new ArrayList<Token>();
							if (blocks.func != null) {
								int offset = getLocalOffset(blocks.lastOp.operands.get(0).lexeme,blocks);
								ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
								if (offset == -1) {
									offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
									ops.add(new Token(Kind.REGISTER,"30",0,0));
								} else {
									ops.add(new Token(Kind.REGISTER,"28",0,0));
								}
								ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
							} else {
								int offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
								ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
								ops.add(new Token(Kind.REGISTER,"30",0,0));
								ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
							}
							Operation newOp = new Operation(OpCode.LDW,ops);
							blocks.ops.add(i+1,newOp);
							break;
						case TEMP:
							ops = new ArrayList<Token>();
							if (blocks.func != null) {
								int offset = getLocalOffset(blocks.lastOp.operands.get(0).lexeme,blocks);
								ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
								if (offset == -1) {
									offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
									ops.add(new Token(Kind.REGISTER,"30",0,0));
								} else {
									ops.add(new Token(Kind.REGISTER,"28",0,0));
								}
								ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
							} else {
								int offset = getGlobalOffset(blocks.lastOp.operands.get(0).lexeme);
								ops.add(new Token(Kind.REGISTER,Integer.toString(1),0,0));
								ops.add(new Token(Kind.REGISTER,"30",0,0));
								ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
							}
							newOp = new Operation(OpCode.LDW,ops);
							blocks.ops.add(i+1,newOp);
							break;
						case TRUE:
							ops = new ArrayList<Token>();
							ops.add(new Token(Kind.REGISTER,"1",0,0));
							ops.add(new Token(Kind.REGISTER,"0",0,0));
							ops.add(new Token(Kind.INT_VAL,"1",0,0));
							newOp = new Operation(OpCode.ORI,ops);
							blocks.ops.add(i+1,newOp);
							break;
						case FALSE:
							ops = new ArrayList<Token>();
							ops.add(new Token(Kind.REGISTER,"1",0,0));
							ops.add(new Token(Kind.REGISTER,"0",0,0));
							ops.add(new Token(Kind.INT_VAL,"0",0,0));
							newOp = new Operation(OpCode.ORI,ops);
							blocks.ops.add(i+1,newOp);
							break;
						case INT_VAL:
							ops = new ArrayList<Token>();
							ops.add(new Token(Kind.REGISTER,"1",0,0));
							ops.add(new Token(Kind.REGISTER,"0",0,0));
							ops.add(new Token(Kind.INT_VAL,blocks.lastOp.operands.get(0).lexeme,0,0));
							newOp = new Operation(OpCode.ORI,ops);
							blocks.ops.add(i+1,newOp);
							break;
					}
					
					blocks.lastOp.operands.set(0, new Token(Kind.REGISTER,"1",0,0));
					i++;
				}
			}
		}
	}
	
	private void generateOffsets() {
		FunSymbol currFun = null;
		for (Block block:regAllocIR.blocks) {
			if (currFun != block.func && block.func != null) {
				currFun = block.func;
				int paramOffset = 8 + (block.func.inputs.size()*4);
				for (int i = 0; i < block.func.inputs.size(); i++) {
					currFun.localOffsets.add(new varOffset(new Token(Kind.IDENT,block.func.inputs.get(i).name,0,0),paramOffset));
					paramOffset -= 4;
				}
				for (int i = block.func.inputs.size(); i < block.func.scope.size(); i++) {
					currFun.localOffsets.add(new varOffset(new Token(Kind.IDENT,block.func.scope.get(i).name,0,0),currFun.fpOffset));
					currFun.fpOffset -= 4;
				}
			}
			for (Operation op:block.ops) {
				if (op.operands.size() == 0) continue;
				if (regAllocIR.isGlobNewVar(op.operands.get(0).lexeme) && (op.operands.get(0).kind == Kind.TEMP || op.operands.get(0).kind == Kind.IDENT)) {
					if (op.fun != null) {
						if (op.fun.output == Kind.VOID) continue;
					}
					if (currFun != null) {
						if (regAllocIR.isLocalNewVar(op.operands.get(0).lexeme,currFun)) {
							currFun.localOffsets.add(new varOffset(op.operands.get(0),currFun.fpOffset));
							currFun.fpOffset -= 4;
						} 
					} else {
						regAllocIR.globalOffsets.add(new varOffset(op.operands.get(0),regAllocIR.GDBoffset));
						regAllocIR.GDBoffset -= 4;
					}
				}
			}
		}
	}
	
	public Operation store(int regNum, String var, Block blocks) {
		ArrayList<Token> ops = new ArrayList<Token>();
		if (blocks.func != null) {
			int offset = getLocalOffset(var,blocks);
			ops.add(new Token(Kind.REGISTER,Integer.toString(regNum),0,0));
			if (offset == -1) {
				offset = getGlobalOffset(var);
				ops.add(new Token(Kind.REGISTER,"30",0,0));
			} else {
				ops.add(new Token(Kind.REGISTER,"28",0,0));
			}
			ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
		} else {
			int offset = getGlobalOffset(var);
			ops.add(new Token(Kind.REGISTER,Integer.toString(regNum),0,0));
			ops.add(new Token(Kind.REGISTER,"30",0,0));
			ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
		}
		return new Operation(OpCode.STW,ops);
	}
	
	public Operation load(int regNum, String var, Block blocks) {
		ArrayList<Token> ops = new ArrayList<Token>();
		if (blocks.func != null) {
			int offset = getLocalOffset(var,blocks);
			ops.add(new Token(Kind.REGISTER,Integer.toString(regNum),0,0));
			if (offset == -1) {
				offset = getGlobalOffset(var);
				ops.add(new Token(Kind.REGISTER,"30",0,0));
			} else {
				ops.add(new Token(Kind.REGISTER,"28",0,0));
			}
			ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
		} else {
			int offset = getGlobalOffset(var);
			ops.add(new Token(Kind.REGISTER,Integer.toString(regNum),0,0));
			ops.add(new Token(Kind.REGISTER,"30",0,0));
			ops.add(new Token(Kind.INT_VAL,Integer.toString(offset),0,0));
		}
		return new Operation(OpCode.LDW,ops);
	}
	
	public void regInterference() {
		regAllocIR.deadcodeElimination(true);
		for (int i = 0; i < regAllocIR.riGraphs.size(); i++) {
			if (regAllocIR.riGraphs.get(i).function != null) {
				for (int j = 0; j < regAllocIR.riGraphs.get(i).liveVariables.size(); j++) {
					if (regAllocIR.riGraphs.get(i).liveVariables.get(j).blockNum <= regAllocIR.riGraphs.get(i).end && regAllocIR.riGraphs.get(i).liveVariables.get(j).blockNum >= regAllocIR.riGraphs.get(i).start) {
						regAllocIR.riGraphs.get(i).interfere(regAllocIR.riGraphs.get(i).liveVariables.get(j),true);
					}
				}
			} else {
				for (int j = 0; j < regAllocIR.liveVariables.size(); j++) {
					if (regAllocIR.liveVariables.get(j).blockNum <= regAllocIR.riGraphs.get(i).end && regAllocIR.liveVariables.get(j).blockNum >= regAllocIR.riGraphs.get(i).start) {
						regAllocIR.riGraphs.get(i).interfere(regAllocIR.liveVariables.get(j),false);
					}
				}
			}
		}
//			regAllocIR.riGraphs.get(i).printRIGraph();
		
//		for (int i = 0; i < regAllocIR.globalOffsets.size(); i++) {
//			if (regAllocIR.globalOffsets.get(i).lvIndex != null) {
//				
//			}
//		}
		
	}
	
	public void colorGraph() {
		int k = numRegs - 2;
		nodeStack = new Stack<regInterferenceNode>();
		for (regInterferenceGraph graph:regAllocIR.riGraphs) {
			graph.startGraphColoring();
			while (nodeStack.size() < graph.nodes.size()) {
//				graph.printRIGraph();
				int i = 0;
				int maxEdges = 0;
				int maxEdgesIndex = 0;
				for(;i<graph.nodes.size();i++) {
					if (!graph.nodes.get(i).inGraph) continue;
					int edges = graph.nodes.get(i).numEdges();
					if (edges < k) {
						nodeStack.push(graph.nodes.get(i));
						graph.removeVar(graph.nodes.get(i).bitSetIndex);
						graph.nodes.get(i).inGraph = false;
						break;
					}
					if (edges > maxEdges) {
						maxEdges = edges;
						maxEdgesIndex = i;
					}
				}
				if (i == graph.nodes.size()) {
					nodeStack.push(graph.nodes.get(maxEdgesIndex));
					graph.removeVar(graph.nodes.get(maxEdgesIndex).bitSetIndex);
					graph.nodes.get(maxEdgesIndex).spilled = true;
					graph.nodes.get(maxEdgesIndex).inGraph = false;
				}
			}
			while (nodeStack.size() > 0) {
				regInterferenceNode currNode = nodeStack.pop();
				BitSet unavailableRegs = (BitSet)(graph.addVar(currNode,k)).clone();
				for (int i = 0; i < k; i++) {
					if (!unavailableRegs.get(i)) {
						currNode.reg = i+1;
						currNode.spilled = false;
						break;
					}
				}
			}
		}
	}
	
	public int getLocalOffset(String name, Block currBlock) {
		for (int i = 0; i < currBlock.func.localOffsets.size(); i++) {
			if (currBlock.func.localOffsets.get(i).variable.lexeme.equals(name)) {
				return currBlock.func.localOffsets.get(i).Offset;
			}
		}
		return -1;
	}
	
	public int getGlobalOffset(String name) {
		for (int i = 0; i < regAllocIR.globalOffsets.size(); i++) {
			if (regAllocIR.globalOffsets.get(i).variable.lexeme.equals(name)) {
				return regAllocIR.globalOffsets.get(i).Offset;
			}
		}
		return -1;
	}
	

}