package compiler;

import ast.AST;
import java.util.List;
import org.apache.commons.cli.CommandLine;

public class Compiler {
	public Parser p;
	public IR ir;
	public RegAlloc reg;
	public int numReg;
	
	public Compiler (Scanner s, int numRegisters) {
		p = new Parser(s);
		numReg = numRegisters;
	}
	
	public AST genAST() {
		p.go();
		return p.comp;
	}
	
	public IR genSSA(AST ast) {
		ir = new IR(ast);
		return ir;
	}
	
	public String optimization(List<String> optArgs, CommandLine cmd) {
		if (optArgs.size() == 0 && cmd.hasOption("maxOpt")) {
			ir.maxOptimizations();
			return ir.asDotGraph();
		}
		boolean didOpt;
		do {
			didOpt = false;
			for (String arg : optArgs) {
				switch (arg) {
				case "cp":
					didOpt |= ir.constantPropagation();
				break;
				case "cf":
					didOpt |= ir.constantFolding();
				break;
				case "cpp":
					didOpt |= ir.copyPropagation();
				break;
				case "cse":
					didOpt |= ir.commonSubexpression();
				break;
				case "dce":
					didOpt |= ir.deadcodeElimination(false);
				break;
				default:
					System.out.println("Unhandled optimization argument '" + arg + "'!\nExiting.");
					System.exit(1);
				}
			}
		} while (cmd.hasOption("maxOpt") && didOpt);
			
		return ir.asDotGraph();
	}
	
	public String regAlloc(int numReg) {
		// do register allocation
		reg = new RegAlloc(ir,numReg);
		return ir.asDotGraph();
	}
	
	public boolean hasError() { return false; }
	public String errorReport() { return ""; }
	
	public int[] genCode() {
		return ir.emitBytecode();
	}
}