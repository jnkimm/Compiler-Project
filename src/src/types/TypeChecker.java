package types;

import java.util.ArrayList;

import compiler.Kind;
import compiler.Type;

public class TypeChecker {

    private boolean checkingFunction = false;
	private ArrayList<compiler.VarSymbol> funSymTable = null;
    private StringBuilder errorBuffer = new StringBuilder();
    private ast.AST checkAST;
    private int dimCheck;
    private compiler.VarSymbol array;
    private boolean funcDec = false;
    private ArrayList<compiler.VarSymbol> functionVars;
    private compiler.Kind returnType;
    //private Symbol currentFunction;

    /* 
     * Useful error strings:
     *
     * "Call with args " + argTypes + " matches no function signature."
     * "Call with args " + argTypes + " matches multiple function signatures."
     * 
     * "IfStat requires relation condition not " + cond.getClass() + "."
     * "WhileStat requires relation condition not " + cond.getClass() + "."
     * "RepeatStat requires relation condition not " + cond.getClass() + "."
     * 
     * "Function " + currentFunction.name() + " returns " + statRetType + " instead of " + funcRetType + "."
     * 
     * "Variable " + var.name() + " has invalid type " + var.type() + "."
     * "Array " + var.name() + " has invalid base type " + baseType + "."
     * 
     * 
     * "Function " + currentFunction.name() + " has a void arg at pos " + i + "."
     * "Function " + currentFunction.name() + " has an error in arg at pos " + i + ": " + ((ErrorType) t).message())
     * "Not all paths in function " + currentFunction.name() + " return."
     */

    public TypeChecker() {
//    	checkAST = ast;
    	array = null;
//    	TypeCheckingHelp(ast.main);
    }
    
    private TypeC TypeCheckingHelp(ast.ASTnode node) {
    	switch (node.nodetype) {
			case DECLARATION_LIST:
				if (node.children.get(0).nodetype == compiler.Kind.FUNC) {
					funcDec = true;
    				for (int i = 0; i < node.children.size(); i++) {
    					dimCheck = 0;
    					array = null;
    					ArrayList<compiler.VarSymbol> params = paramsToSymbols(node.children.get(i).children.get(0));
    					ArrayList<compiler.Type> signature = new ArrayList<compiler.Type>();
    					for (compiler.VarSymbol p : params) {
    						signature.add(p.type);
    					}
    					functionVars = checkAST.syms.findFun(node.children.get(i).tokenInfo, signature).scope;
    					returnType = node.children.get(i).children.get(1).nodetype;
    					TypeC typeToCheck = TypeCheckingHelp(node.children.get(i).children.get(2));
    					if (typeToCheck.getClass().equals(ErrorType.class)) {
    						reportError(node.children.get(i).children.get(0).lineNum,node.children.get(i).children.get(0).charPos,typeToCheck.message);
    						if (node.nodetype == compiler.Kind.REPEAT && i == 1) {
    							reportError(node.lineNum,node.charPos,"RepeatStat requires bool condition not "+typeToCheck.message+".");
    						}
    					}
    				}
    				funcDec = false;
				} else {
					for (int i = 0; i < node.children.size(); i++) {
						for (int j = 0; j < node.children.get(i).type.dims.size(); j++) {
							if (node.children.get(i).type.dims.get(j) < 1) {
								reportError(node.children.get(i).lineNum,node.children.get(i).charPos+4,"Array "+node.children.get(i).tokenInfo+" has invalid size "+node.children.get(i).type.dims.get(j)+".");
							}
						}
					}
				}
				break;
    		case CALL:
    			ArrayList<compiler.Type> args = new ArrayList<compiler.Type>();
    			for (int i = 0; i < node.children.get(0).children.size(); i++) {
    				TypeC argType = TypeCheckingHelp(node.children.get(0).children.get(i));
    				if (argType.getClass().equals(BoolType.class)) {
    					args.add(new compiler.Type(compiler.Kind.BOOL));
    				} else if (argType.getClass().equals(IntType.class)) {
    					args.add(new compiler.Type(compiler.Kind.INT));
    				} else if (argType.getClass().equals(ErrorType.class) && (node.children.get(0).children.get(0).nodetype != compiler.Kind.OR) && ((node.children.get(0).children.get(0).nodetype != compiler.Kind.ARRAY_INDEX))) {
    					reportError(node.children.get(0).children.get(i).lineNum,node.children.get(0).children.get(i).charPos,argType.message);
    					args.add(new compiler.Type(compiler.Kind.ERROR));
    				}
    			}
    			compiler.FunSymbol params = checkAST.syms.lookupFun(node.tokenInfo,args);
    			if (params == null) {
    				String errorStr = "Call with args TypeList(";
    				for (int i = 0; i < args.size(); i++) {
    					errorStr += args.get(i).toString();
    				}
    				errorStr += ") matches no function signature.";
    				reportError(node.lineNum,node.charPos-5,errorStr);
    				return new ErrorType(errorStr);
    			} else {
	    			switch (params.output) {
		    			case INT:
							node.type = new Type(Kind.INT);
		    				return new IntType("int");
		    			case BOOL:
							node.type = new Type(Kind.BOOL);
		    				return new BoolType("bool");
		    			case VOID:
		    				return new VoidType("void");
	    			}
    			}
    		case ARRAY_INDEX:
    			dimCheck++;
    			int currDim = dimCheck;
    			compiler.VarSymbol array;
    			ast.ASTnode temp = node.children.get(0);
    			while (temp.children.size()>0) {
    				temp = temp.children.get(0);
    			}
    			array = checkAST.syms.lookupVar(temp.tokenInfo);
    			TypeC arrayType = TypeCheckingHelp(node.children.get(0));
    			if (node.children.get(1).nodetype == compiler.Kind.ARRAY_INDEX) {
    				dimCheck = 0;
    			}
    			TypeC indexArg = TypeCheckingHelp(node.children.get(1));
    			if (indexArg.getClass().equals(IntType.class)) {
    				if (node.children.get(1).nodetype != compiler.Kind.INT_VAL) {
    					return arrayType;
    				}
        			if ((array.type.dims.get(array.type.dims.size()- currDim) <= Integer.parseInt(node.children.get(1).tokenInfo)) || (Integer.parseInt(node.children.get(1).tokenInfo) < 0)) {
        				reportError(node.children.get(1).lineNum,node.children.get(1).charPos,"Out-of-bounds array argument "+array.type.dims.get(array.type.dims.size()- currDim)+".");
        				return new ErrorType("Out-of-bounds array argument "+Integer.parseInt(node.children.get(1).tokenInfo)+".");
        			} else {
        				return arrayType;
        			}
    			} else if (indexArg.getClass().equals(ErrorType.class)){
    				if (Kind.Group.INDEXING_OPS.firstSet().contains(node.children.get(1).nodetype)) {
    					reportError(node.children.get(1).lineNum,node.children.get(1).charPos,indexArg.message);
    				}
    				reportError(node.children.get(1).lineNum,node.children.get(1).charPos,"Invalid array argument ("+indexArg.message+").");
    				return indexArg;
    			} else {
    				reportError(node.children.get(1).lineNum,node.children.get(1).charPos,"Invalid array argument ("+array.type.dims.get(array.type.dims.size()- currDim)+").");
    				return new ErrorType("Invalid array argument ("+array.type.dims.get(array.type.dims.size()- currDim)+").");
    			}
    		case IDENT:
//    			if (checkAST.lookupVar(node.tokenInfo).type.dims.size() > 0) {
//    				array = checkAST.lookupVar(node.tokenInfo);
//    			}
    			if (funcDec) {
        			for (int i = 0; i<functionVars.size();i++) {
        				if (functionVars.get(i).name.equals(node.tokenInfo)) {
        					if (functionVars.get(i).type.kind == compiler.Kind.INT) {
								node.type = new Type(Kind.INT);
        						return new IntType("int");
        					} else if (functionVars.get(i).type.kind == compiler.Kind.BOOL) {
								node.type = new Type(Kind.BOOL);
        						return new BoolType("bool");
        					}
        				}
        			}
    			} 
    			if (checkAST.syms.lookupVar(node.tokenInfo).type.kind == compiler.Kind.INT) {
					node.type = new Type(Kind.INT);
    				return new IntType("int");
    			} else if (checkAST.syms.lookupVar(node.tokenInfo).type.kind == compiler.Kind.BOOL) {
					node.type = new Type(Kind.BOOL);
    				return new BoolType("bool");
    			} 
    			break;
    		case UNI_INC:
    			TypeC uni = TypeCheckingHelp(node.children.get(0));
    			if (uni.getClass().equals(BoolType.class)) {
    				reportError(node.children.get(0).lineNum,node.children.get(0).charPos+node.children.get(0).tokenInfo.length(),"Cannot add int to bool");
    				reportError(node.children.get(0).lineNum,node.children.get(0).charPos,"Cannot assign errortype to bool");
    			} else {
    				node.type = new compiler.Type(Kind.INT);
    			}
    			break;
    		case UNI_DEC:
    			TypeC unidec = TypeCheckingHelp(node.children.get(0));
    			if (unidec.getClass().equals(BoolType.class)) {
    				reportError(node.children.get(0).lineNum,node.children.get(0).charPos+node.children.get(0).tokenInfo.length(),"Cannot add int to bool");
    				reportError(node.children.get(0).lineNum,node.children.get(0).charPos,"Cannot assign errortype to bool");
    			} else {
    				node.type = new compiler.Type(Kind.INT);
    			}
    			break;
    		case INT_VAL:
				node.type = new Type(Kind.INT);
    			return new IntType("int");
    		case TRUE:
    		case FALSE:
				node.type = new Type(Kind.BOOL);
    			return new BoolType("bool");
    		case ADD:
				node.type = new Type(Kind.INT);
    			return TypeCheckingHelp(node.children.get(0)).add(TypeCheckingHelp(node.children.get(1)));
	    	case SUB:
				node.type = new Type(Kind.INT);
	    		return TypeCheckingHelp(node.children.get(0)).sub(TypeCheckingHelp(node.children.get(1)));
    		case MUL:
				node.type = new Type(Kind.INT);
	    		return TypeCheckingHelp(node.children.get(0)).mul(TypeCheckingHelp(node.children.get(1)));
	    	case DIV:
				node.type = new Type(Kind.INT);
	    		return TypeCheckingHelp(node.children.get(0)).div(TypeCheckingHelp(node.children.get(1)));
	    	case MOD:
				node.type = new Type(Kind.INT);
	    		return TypeCheckingHelp(node.children.get(0)).mod(TypeCheckingHelp(node.children.get(1)));
	    	case POW:
				node.type = new Type(Kind.INT);
	    		return TypeCheckingHelp(node.children.get(0)).pow(TypeCheckingHelp(node.children.get(1)));
	    	case AND:
				node.type = new Type(Kind.BOOL);
	    		return TypeCheckingHelp(node.children.get(0)).and(TypeCheckingHelp(node.children.get(1)));
	    	case OR:
				node.type = new Type(Kind.BOOL);
	    		return TypeCheckingHelp(node.children.get(0)).or(TypeCheckingHelp(node.children.get(1)));
	    	case NOT:
				node.type = new Type(Kind.BOOL);
	    		return TypeCheckingHelp(node.children.get(0)).not();
	    	case IF:
	    		TypeC ifCond = TypeCheckingHelp(node.children.get(0));
	    		if (!ifCond.getClass().equals(BoolType.class)) {
	    			reportError(node.children.get(0).lineNum,node.children.get(0).charPos,"Not a valid comparison");
	    			reportError(node.lineNum,node.charPos,"IfStat requires bool not"+ifCond.message+".");
	    		}
	    		for (int i = 1; i < node.children.size(); i++) {
	    			TypeCheckingHelp(node.children.get(i));
	    		}
	    		return new VoidType("void");
	    	case RETURN:
	    		if (funcDec) {
	    			TypeC returnT = TypeCheckingHelp(node.children.get(0));
	    			if (returnT.getClass().equals(ErrorType.class)) {
	    				reportError(node.children.get(0).lineNum,node.children.get(0).charPos,returnT.message);
	    			}
	    			if (returnType == compiler.Kind.INT) {
	    				if (!returnT.getClass().equals(IntType.class)) {
	    					//reportError(node.children.get(0).lineNum,node.children.get(0).charPos,returnT.message);
	    					reportError(node.lineNum,node.charPos,"Cannot return Type("+returnT.message+").");
	    				}
	    			} else if (returnType == compiler.Kind.BOOL) {
	    				if (!returnT.getClass().equals(BoolType.class)) {
//	    					reportError(node.children.get(0).lineNum,node.children.get(0).charPos,returnT.message);
	    					reportError(node.lineNum,node.charPos,"Cannot return Type("+returnT.message+").");
	    				}
	    			} else {
	    				if (!returnT.getClass().equals(VoidType.class)) {
//	    					reportError(node.children.get(0).lineNum,node.children.get(0).charPos,returnT.message);
	    					reportError(node.lineNum,node.charPos,"Cannot return Type("+returnT.message+").");
	    				}
	    			}
	    		}
	    		break;
	    	case WHILE:
	    		TypeC whilecondition = TypeCheckingHelp(node.children.get(0));
	    		if (whilecondition.getClass().equals(ErrorType.class)) {
	    			reportError(node.lineNum,node.charPos,"WhileStat requires bool condition not "+whilecondition.message+".");
	    		} else if (!whilecondition.getClass().equals(BoolType.class)) {
	    			reportError(node.lineNum,node.charPos,"WhileStat requires bool condition not "+whilecondition.message+".");
	    		}
//	    	case REPEAT:
//	    		TypeC repeatcondition = TypeCheckingHelp(node.children.get(1));
//	    		if (repeatcondition.getClass().equals(ErrorType.class)) {
//	    			reportError(node.children.get(1).lineNum,node.children.get(1).charPos,"RepeatStat requires bool condition not "+repeatcondition.message+".");
//	    		} else if (!repeatcondition.getClass().equals(BoolType.class)) {
//	    			reportError(node.children.get(1).lineNum,node.children.get(1).charPos,"RepeatStat requires bool condition not "+repeatcondition.message+".");
//	    		}
    		default:
    			if (compiler.Kind.Group.ASSIGN_OP.firstSet().contains(node.nodetype)) {
//    				TypeC rhsCheck = TypeCheckingHelp(node.children.get(1));
//    				TypeC assignType = TypeCheckingHelp(node.children.get(0)).assign(rhsCheck);
//    				if (assignType.getClass().equals(ErrorType.class)) {
//    					reportError(node.children.get(0).lineNum,node.children.get(0).charPos,assignType.message);
//    					
//    				}
    				return TypeCheckingHelp(node.children.get(0)).assign(TypeCheckingHelp(node.children.get(1)));
    			} else if (compiler.Kind.Group.REL_OP.firstSet().contains(node.nodetype)) {
//    				TypeC rhsCheck = TypeCheckingHelp(node.children.get(1));
//    				if (rhsCheck.getClass().equals(ErrorType.class)) {
//    					reportError(node.children.get(1).lineNum,node.children.get(1).charPos,rhsCheck.message);
//    				}
					node.type = new Type(Kind.BOOL);
    				return TypeCheckingHelp(node.children.get(0)).compare(TypeCheckingHelp(node.children.get(1)));
    			}  else {
    				for (int i = 0; i < node.children.size(); i++) {
    					dimCheck = 0;
    					array = null;
    					TypeC typeToCheck = TypeCheckingHelp(node.children.get(i));
    					if (typeToCheck.getClass().equals(ErrorType.class) && node.children.get(i).nodetype != compiler.Kind.CALL) {
//    						reportError(node.children.get(i).children.get(0).lineNum,node.children.get(i).children.get(0).charPos,typeToCheck.message);
    						if (node.nodetype == compiler.Kind.REPEAT && i == 1) {
    							reportError(node.children.get(i).lineNum,node.children.get(i).charPos,typeToCheck.message);
    							reportError(node.lineNum,node.charPos,"RepeatStat requires bool condition not "+typeToCheck.message+".");
    						} else {
    							reportError(node.children.get(i).children.get(0).lineNum,node.children.get(i).children.get(0).charPos,typeToCheck.message);
    						}
    					}
    				}
    			}
    	}
    	return new VoidType("Void");
    }
    
	public ArrayList<compiler.VarSymbol> paramsToSymbols(ast.ASTnode params) {
		ArrayList<compiler.VarSymbol> symbols = new ArrayList<compiler.VarSymbol>();
		for (ast.ASTnode n : params.children) {
			symbols.add(new compiler.VarSymbol(n.tokenInfo, n.type));
		}
		return symbols;
	}

    private void reportError (int lineNum, int charPos, String message) {
        errorBuffer.append("TypeError(" + lineNum + "," + charPos + ")");
        errorBuffer.append("[" + message + "]" + "\n");
    }

    public boolean hasError () {
        return errorBuffer.length() != 0;
    }


    public String errorReport () {
        return errorBuffer.toString();
    }
	
	public boolean check(ast.AST comp) {
		checkAST = comp;
		TypeCheckingHelp(comp.main);
		return true;
	}
}
