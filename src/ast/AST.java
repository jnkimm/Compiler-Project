package ast;

import java.util.ArrayList;

import compiler.*;

public class AST {

    public ASTnode main;
	public SymbolTable syms;
	
	public AST () {
		main = null;
		syms = new SymbolTable();
	}

	public VarSymbol searchVars(String name) {
		return syms.findVar(name);
	}
	
	static public ArrayList<VarSymbol> paramsToSymbols(ASTnode params) {
		ArrayList<VarSymbol> symbols = new ArrayList<VarSymbol>();
		for (ASTnode n : params.children) {
			if (n.tokenInfo == "") {
				System.exit(-1);
			}
			symbols.add(new VarSymbol(n.tokenInfo, n.type));
		}
		return symbols;
	}
	
	public String printPreOrder() {
		StringBuilder output = new StringBuilder();
		prettyPrint(main, 0, output);
		return output.toString();
	}
	
	public void prettyPrint(ASTnode n, int level, StringBuilder out) {
		for (int i = 0; i < level; i++) {
			out.append("  ");
		}
		if (Kind.Group.EXTRA_INFO.firstSet().contains(n.nodetype)) {
			switch (n.nodetype) {
				case CALL:
					out.append(n.nodetype.toString());
	    			ArrayList<FunSymbol> functSig = syms.lookupFun(n.tokenInfo);
					if (functSig.size() == 0) {
						out.append("[" + n.tokenInfo + ":No functions found]");
					}
					out.append("[");
					boolean first = true;
					for (FunSymbol fun : functSig) {
						if (!first) {
							out.append(", ");
						}
						out.append(n.tokenInfo + ":(");
						for (int i = 0; i < fun.inputs.size(); i++) {
							if (i == fun.inputs.size()-1) {
								out.append(fun.inputs.get(i).type.toString());
							} else {
								out.append(fun.inputs.get(i).type.toString() + ",");
							}
						}
						out.append(")->"+fun.output.name);
						first = false;
					}
					out.append("]");
					break;
				case COMPUTATION:
					out.append(n.nodetype.toString());
					out.append("[main:()->void]");
					break;
				case VARIABLE_DECLARATION:
					out.append(n.nodetype.toString());
					out.append("["+n.tokenInfo+":"+n.type.toString()+"]\n");
					return;
				case FUNC:
					out.append(n.nodetype.toString());
					ArrayList<VarSymbol> params = paramsToSymbols(n.children.get(0));
					ArrayList<Type> signature = new ArrayList<Type>();
					for (VarSymbol p : params) {
						signature.add(p.type);
					}
					FunSymbol fun = syms.findFun(n.tokenInfo, signature);
					out.append("[" + fun.toString(true) + "]\n");
					syms.startFunction(fun.scope);
					prettyPrint(n.children.get(2), level+1, out);
					syms.endFunction();
					return;
				case IDENT:
					out.append(n.tokenInfo + ":");
					out.append(searchVars(n.tokenInfo).type.toString());
				break;
				case INT_VAL:
					out.append(n.nodetype.toString());
					out.append("[" + n.tokenInfo + "]");
				break;
				case FLOAT_VAL:
					out.append(n.nodetype.toString());
					out.append("[" + n.tokenInfo + "]");
				break;
				case TRUE:
					out.append("BoolLiteral[true]");
				break;
				case FALSE:
					out.append("BoolLiteral[false]");
				break;
				case ARRAY_INDEX:
					out.append(n.nodetype.toString() + "\n");
					prettyPrint(n.children.get(1), level+1, out);
					prettyPrint(n.children.get(0), level+1, out);
					return;
				default:
			}
		} else {
			out.append(n.nodetype.toString());
		}
		if (n.type != null) {
			out.append(" -> " + n.type.toString());
		}
		out.append("\n");
		if (n.children == null) {
			return;
		}
		for (ASTnode child : n.children) {
			prettyPrint(child, level+1, out);
		}
	}
	
	// stub for interface reasons
	public String asDotGraph() {
		return "";
	}
}

