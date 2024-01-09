package compiler;

import java.util.ArrayList;

public class SymbolTable {
    public ArrayList<VarSymbol> vars;
    public ArrayList<FunSymbol> funs;
	public ArrayList<VarSymbol> functionVars;
	public int currOffsetGDB;
	
	public SymbolTable() {
		vars = new ArrayList<VarSymbol>();
		funs = new ArrayList<FunSymbol>();
		functionVars = null;
		currOffsetGDB = 0;
	}
	
	public void startFunction(ArrayList<VarSymbol> functionLocals) {
		functionVars = functionLocals;
	}
	
	public void endFunction() {
		functionVars = null;
	}
	
	public boolean parsingFunction() {
		return functionVars != null;
	}
	
	public int lookupVarIndex(ArrayList<VarSymbol> vars, String name) {
		for (int i = 0; i < vars.size(); i++) {
			VarSymbol var = vars.get(i);
			if (var.name.equals(name)) {
				return i;
			}
		}
		return -1;
	}
	
	private VarSymbol lookupVar(ArrayList<VarSymbol> vars, String name) {
		for (VarSymbol var : vars) {
			if (var.name.equals(name)) {
				return var;
			}
		}
		return null;
	}
	
	public VarSymbol lookupVar(String name) {
		if (parsingFunction()) {
			VarSymbol var = lookupVar(functionVars, name);
			if (var != null) {
				return var;
			}
		}
		return lookupVar(vars, name);
	}
	
	public FunSymbol lookupFun(String name, ArrayList<Type> signature) {
		for (FunSymbol fun : funs) {
			if (fun.name.equals(name) && signature.size() == fun.inputs.size()) {
				boolean match = true;
				for (int i = 0; i < signature.size(); i++) {
					match &= fun.inputs.get(i).type.equ(signature.get(i));
				}
				if (match) {
					return fun;
				}
			}
		}
		return null;
	}
	
	public ArrayList<FunSymbol> lookupFun(String name) {
		ArrayList<FunSymbol> ret = new ArrayList<FunSymbol>();
		for (FunSymbol fun : funs) {
			if (fun.name.equals(name)) {
				ret.add(fun);
			}
		}
		return ret;
	}
	
	public void addVar(Token tok, Type type) {
		if (parsingFunction()) {
			VarSymbol var = lookupVar(functionVars, tok.lexeme);
			if (var != null) {
				throw new RedeclarationException(tok, var.tok);
			}
			functionVars.add(new VarSymbol(tok, type));
		} else {
			VarSymbol var = lookupVar(vars, tok.lexeme);
			if (var != null) {
				throw new RedeclarationException(tok, var.tok);
			}
			vars.add(new VarSymbol(tok, type, currOffsetGDB,false));
			currOffsetGDB -= 4;
		}
	}
	
	public void addFun(Token tok, ArrayList<VarSymbol> inputs, ArrayList<VarSymbol> scope, Kind output) {
		ArrayList<Type> inputTypes = new ArrayList<Type>();
		for (VarSymbol in : inputs) {
			inputTypes.add(in.type);
		}
		FunSymbol f = lookupFun(tok.lexeme, inputTypes);
		if (f != null) {
			throw new RedeclarationException(tok, f.tok);
		}
		funs.add(new FunSymbol(tok, inputs, scope, output));
	}
	
	public VarSymbol findVar(String name) {
		if (parsingFunction()) {
			VarSymbol var = lookupVar(functionVars, name);
			if (var == null) {
				var = lookupVar(vars,name);
				if (var == null) {
					throw new SymbolNotFoundException(name);
				}
			}
			return var;
		} else {
			VarSymbol var = lookupVar(vars, name);
			if (var == null) {
				throw new SymbolNotFoundException(name);
			}
			return var;
		}
	}
	
	public FunSymbol findFun(String name, ArrayList<Type> signature) {
		FunSymbol f = lookupFun(name, signature);
		if (f == null) {
			throw new SymbolNotFoundException(name);
		}
		return f;
	}
	
	public class SymbolNotFoundException extends RuntimeException {

		private static final long serialVersionUID = 1L;
		private final String name;

		public SymbolNotFoundException (String name) {
			super("Could not find " + name + ".");
			this.name = name;
		}

		public String name () {
			return name;
		}
	}

	public class RedeclarationException extends RuntimeException {

		private static final long serialVersionUID = 1L;
		private final String name;

		public RedeclarationException (Token badtok, Token oldtok) {
			super("Symbol " + badtok.lexeme + " being redeclared at " + Parser.errorPrepend(badtok) + "\n"
				+ "Initial declaration of symbol " + oldtok.lexeme + " at " + Parser.errorPrepend(oldtok));
			this.name = badtok.lexeme;
		}

		public String name () {
			return name;
		}
	}
}