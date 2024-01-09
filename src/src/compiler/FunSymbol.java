package compiler;

import java.util.ArrayList;

public class FunSymbol {
	public Token tok;
	public String name;
	public ArrayList<VarSymbol> inputs;
	public Kind output;
	public ArrayList<VarSymbol> scope;
	public int blockEntryPoint = -1;
	public ArrayList<varOffset> localOffsets;
	public int fpOffset = 0;
	public Boolean riGraphCreated = false;
	
	public FunSymbol (Token tok, String name, ArrayList<VarSymbol> inputs, ArrayList<VarSymbol> scope, Kind output) {
		localOffsets = new ArrayList<varOffset>();
		this.tok = tok;
		this.name = name;
		this.inputs = inputs;
		this.output = output;
		this.scope = scope;
	}
	
	public FunSymbol (Token tok, ArrayList<VarSymbol> inputs, ArrayList<VarSymbol> scope, Kind output) {
		localOffsets = new ArrayList<varOffset>();
		this.tok = tok;
		name = tok.lexeme;
		this.inputs = inputs;
		this.output = output;
		this.scope = scope;
	}
	
	public FunSymbol () {
		localOffsets = new ArrayList<varOffset>();
		tok = null;
		name = "";
		inputs = null;
		scope = null;
		output = Kind.VOID;
	}
	
	public String toString(boolean printParamNames) {
		StringBuilder sb = new StringBuilder();
		sb.append(name + ":(");
		String prevSep = "";
		for (VarSymbol in : inputs) {
			sb.append(prevSep);
			if (in.name != "" && printParamNames) {
				sb.append(in.name + ":");
			}
			sb.append(in.type.toString());
			prevSep = ",";
		}
		sb.append(")->" + output.toString());
		return sb.toString();
	}
}