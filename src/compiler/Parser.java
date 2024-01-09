package compiler;

import java.io.FileReader;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.ArrayList;

import ast.*;

public class Parser {
	public String filename;
	public AST comp;
	public Scanner scanner;
	public Token currentToken;
	
	private StringBuilder errors;
	
    public String errorReport () {
        return errors.toString();
    }

    public boolean hasError () {
        return errors.length() != 0;
    }
	
	public class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ParseException (String errorMessage) {
            super(errorMessage);
        }
    }
	
	public ParseException reportError(String errMsg) {
		errors.append(errMsg + "\n");
		return new ParseException(errMsg);
	}
	
	public Parser (String filename) {
		this.filename = filename;
		try {
			scanner = new Scanner(new FileReader(filename));
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error accessing the code file: \"" + filename + "\"");
            System.exit(-3);
        }
		errors = new StringBuilder();
		comp = new AST();
		comp.syms.funs = hardcodedFunctions();
	}
	
	public Parser (Scanner scan) {
		filename = "";
		scanner = scan;
		errors = new StringBuilder();
		comp = new AST();
		comp.syms.funs = hardcodedFunctions();
	}
	
	public void go () {
		if (scanner == null) {
			throw reportError("We were not given a valid scanner or filename!\n");
		}
		currentToken = this.scanner.next();
		try {
			computation();
		} catch (RuntimeException e) {
			System.out.println("Error parsing file.");
			if (e.getClass() != ParseException.class && 
				e.getClass() != SymbolTable.RedeclarationException.class && 
				e.getClass() != SymbolTable.SymbolNotFoundException.class) {
					throw e;
			}
			if (e.getClass() == ParseException.class) {
				String[] error = e.toString().split(":");
				System.out.println(error[1].trim());
			}
			if (e.getClass() == SymbolTable.RedeclarationException.class) {
				System.out.println("DeclareSymbolError");
				String[] error = e.toString().split(":");
				System.out.println(error[1].trim());
			}
			if (e.getClass() == SymbolTable.SymbolNotFoundException.class) {
				System.out.print("ResolveSymbolError");
				System.out.print(errorPrepend());
				String[] error = e.toString().split(":");
				System.out.print("[" + error[1].trim() + "]");
			}
			System.exit(-1);
		}
	}
	
// ===== MISC HELPER FUNCTIONS =====

	private ArrayList<FunSymbol> hardcodedFunctions() {
		ArrayList<FunSymbol> HardcodedFuns = new ArrayList<FunSymbol>();
		Token hardcode = new Token();
		ArrayList<VarSymbol> intvar = new ArrayList<VarSymbol>();
		ArrayList<VarSymbol> floatvar = new ArrayList<VarSymbol>();
		ArrayList<VarSymbol> boolvar = new ArrayList<VarSymbol>();
		intvar.add(new VarSymbol(hardcode, new Type(Kind.INT)));
		floatvar.add(new VarSymbol(hardcode, new Type(Kind.FLOAT)));
		boolvar.add(new VarSymbol(hardcode, new Type(Kind.BOOL)));
		HardcodedFuns.add(new FunSymbol(hardcode, "readInt"   , new ArrayList<VarSymbol>(), null, Kind.INT));
		HardcodedFuns.add(new FunSymbol(hardcode, "readFloat" , new ArrayList<VarSymbol>(), null, Kind.FLOAT));
		HardcodedFuns.add(new FunSymbol(hardcode, "readBool"  , new ArrayList<VarSymbol>(), null, Kind.BOOL));
		HardcodedFuns.add(new FunSymbol(hardcode, "println"   , new ArrayList<VarSymbol>(), null, Kind.VOID));
		HardcodedFuns.add(new FunSymbol(hardcode, "printInt"  , intvar                    , null, Kind.VOID));
		HardcodedFuns.add(new FunSymbol(hardcode, "printFloat", floatvar                  , null, Kind.VOID));
		HardcodedFuns.add(new FunSymbol(hardcode, "printBool" , boolvar                   , null, Kind.VOID));
		return HardcodedFuns;
	}
	
// ===== PARSING HELPER FUNCTIONS =====

	private String reportSyntaxError (Kind.Group nt) {
        String message = "SyntaxError(" + currentToken.lineNum + "," + currentToken.charPos + ")[Expected a token from " + nt.name() + " but got " + currentToken.kind + ".]";
        return message;
    }

    private String reportSyntaxError (Kind kind) {
        String message = "SyntaxError(" + currentToken.lineNum + "," + currentToken.charPos + ")[Expected " + kind + " but got " + currentToken.kind + ".]";
        return message;
    }
	
	static public String errorPrepend(Token tok) {
		return "(" + tok.lineNum + "," + tok.charPos + ")";
	}
	
	private String errorPrepend() {
		return errorPrepend(currentToken);
	}

	private boolean have (Kind kind) {
        return currentToken.kind == kind;
    }

    private boolean have (Kind.Group nt) {
        return nt.firstSet().contains(currentToken.kind);
    }

    private boolean accept (Kind kind) {
        if (have(kind)) {
            try {
                currentToken = scanner.next();
            }
            catch (NoSuchElementException e) {
                if (!kind.equals(Kind.EOF)) {
                    reportError(reportSyntaxError(kind));
                }
            }
            return true;
        }
        return false;
    }

    private boolean accept (Kind.Group nt) {
        if (have(nt)) {
            currentToken = scanner.next();
            return true;
        }
        return false;
    }

    private void expect (Kind kind) {
        if (accept(kind)) {
            return;
        }
        throw reportError(reportSyntaxError(kind));
    }

    private void expect (Kind.Group nt) {
        if (accept(nt)) {
            return;
        }
        throw reportError(reportSyntaxError(nt));
    }

    private Token expectRetrieve (Kind kind) {
        Token tok = currentToken;
        if (accept(kind)) {
            return tok;
        }
        throw reportError(reportSyntaxError(kind));
    }

    private Token expectRetrieve (Kind.Group nt) {
        Token tok = currentToken;
        if (accept(nt)) {
            return tok;
        }
        throw reportError(reportSyntaxError(nt));
    }
	
// ===== PARSE RULES =====
	
	private ASTnode designator () {
        Token ident = expectRetrieve(Kind.IDENT);
		VarSymbol var = comp.searchVars(ident.lexeme);
		ASTnode n = new ASTnode(ident);
		Token tok = currentToken;
		while (accept(Kind.OPEN_BRACKET)) {
			ASTnode expr = relExpr();
			ASTnode top = new ASTnode(Kind.ARRAY_INDEX, tok);
			top.children.add(n);
			top.children.add(expr);
			n = top;
			expect(Kind.CLOSE_BRACKET);
		}
        return n;
    }
	
	private ASTnode groupExpr() {
		Token tok = currentToken;
		ASTnode result;
		if (have(Kind.CALL)) {
			result = funcCall();
		} else if (accept(Kind.NOT)) {
			ASTnode expr = relExpr();
			result = new ASTnode(tok);
			result.children.add(expr);
		} else if (have(Kind.OPEN_PAREN)) {
			result = relation();
		} else if (accept(Kind.Group.LITERAL)) {
			result = new ASTnode(tok);
		} else {
			result = designator();
		}
		return result;
	}
	
	private ASTnode powExpr() {
		ASTnode left = groupExpr();
		Token op = currentToken;
		while (accept(Kind.POW)) {
			ASTnode right = groupExpr();
			ASTnode expr = new ASTnode(op);
			expr.children.add(left);
			expr.children.add(right);
			left = expr;
		}
		return left;
	}
	
	private ASTnode mulExpr() {
		ASTnode left = powExpr();
		Token op = currentToken;
		while (accept(Kind.Group.MUL_OP)) {
			ASTnode right = powExpr();
			ASTnode expr = new ASTnode(op);
			expr.children.add(left);
			expr.children.add(right);
			left = expr;
			op = currentToken;
		}
		return left;
	}
	
	private ASTnode addExpr() {
		ASTnode left = mulExpr();
		Token op = currentToken;
		while (accept(Kind.Group.ADD_OP)) {
			ASTnode right = mulExpr();
			ASTnode expr = new ASTnode(op);
			expr.children.add(left);
			expr.children.add(right);
			left = expr;
			op = currentToken;
		}
		return left;
	}
	
	private ASTnode relExpr() {
		ASTnode left = addExpr();
		Token op = currentToken;
		while (accept(Kind.Group.REL_OP)) {
			ASTnode right = addExpr();
			ASTnode expr = new ASTnode(op);
			expr.children.add(left);
			expr.children.add(right);
			left = expr;
			op = currentToken;
		}
		return left;
	}
	
	private ASTnode relation() {
		expect(Kind.OPEN_PAREN);
		ASTnode rel = relExpr();
		expect(Kind.CLOSE_PAREN);
		return rel;
	}
	
	private ASTnode funcCall() {
		expect(Kind.CALL);
		ASTnode call = new ASTnode(Kind.CALL, expectRetrieve(Kind.IDENT));
		call.children = new ArrayList<ASTnode>();
		ASTnode list = new ASTnode(Kind.ARGUMENT_LIST, expectRetrieve(Kind.OPEN_PAREN));
		call.children.add(list);
		while (!accept(Kind.CLOSE_PAREN)) {
			list.children.add(relExpr());
			accept(Kind.COMMA);
		}
		return call;
	}
	
	private ASTnode ifStat() {
		ASTnode ifNode = new ASTnode(expectRetrieve(Kind.IF));
		ifNode.children.add(relation());
		ifNode.children.add(statSeq(expectRetrieve(Kind.THEN)));
		Token elseToken = currentToken;
		if (accept(Kind.ELSE)) {
			ifNode.children.add(statSeq(elseToken));
		}
		expect(Kind.FI);
		return ifNode;
	}
	
	private ASTnode whileStat() {
		ASTnode whileNode = new ASTnode(expectRetrieve(Kind.WHILE));
		whileNode.children.add(relation());
		whileNode.children.add(statSeq(expectRetrieve(Kind.DO)));
		expect(Kind.OD);
		return whileNode;
	}
	
	private ASTnode repeatStat() {
		Token tok = expectRetrieve(Kind.REPEAT);
		ASTnode repeatNode = new ASTnode(tok);
		repeatNode.children.add(statSeq(tok));
		expect(Kind.UNTIL);
		repeatNode.children.add(relation());
		return repeatNode;
	}
	
	private ASTnode returnStat() {
		Token tok = expectRetrieve(Kind.RETURN);
		ASTnode ret = new ASTnode(tok);
		if (currentToken.kind != Kind.SEMICOLON) {
			ret.children.add(relExpr());
		}
		return ret;
	}
	
	private ASTnode assignStat() {
		ASTnode var = designator();
		ASTnode assign = new ASTnode(currentToken);
		assign.children.add(var);
		if (accept(Kind.Group.ASSIGN_OP)) {
			ASTnode right = relExpr();
			assign.children.add(right);
		} else {
			expect(Kind.Group.UNARY_OP);
		}
		return assign;
	}
	
	private ASTnode statSeq(Token start) {
		ASTnode seq = new ASTnode(Kind.STATEMENT_SEQUENCE, start);
		while (currentToken.kind != Kind.CLOSE_BRACE &&
			   currentToken.kind != Kind.ELSE &&
			   currentToken.kind != Kind.FI &&
			   currentToken.kind != Kind.OD &&
			   currentToken.kind != Kind.UNTIL) {
			ASTnode n;
			switch (currentToken.kind) {
			case CALL:
				n = funcCall();
			break;
			case IF:
				n = ifStat();
			break;
			case WHILE:
				n = whileStat();
			break;
			case REPEAT:
				n = repeatStat();
			break;
			case RETURN:
				n = returnStat();
			break;
			default:
				n = assignStat();
			}
			expect(Kind.SEMICOLON);
			seq.children.add(n);
		}
		return seq;
	}
	
	private ASTnode typeDecl() {
		Token tok = expectRetrieve(Kind.Group.TYPE);
		ASTnode type = new ASTnode(tok);
		if (tok.kind == Kind.VOID) {
			reportError(errorPrepend() + "Found a declaration of a variable with type 'void'.");
		}
		type.type = new Type(tok.kind);
		while (accept(Kind.OPEN_BRACKET)) {
			Token num = expectRetrieve(Kind.INT_VAL);
			type.type.dims.add(Integer.parseInt(num.lexeme));
			expect(Kind.CLOSE_BRACKET);
		}
		return type;
	}

	private void varDecl(ASTnode top) {
		
		ASTnode type = typeDecl();
		do {
			ASTnode decl = new ASTnode(type);
			Token ident = expectRetrieve(Kind.IDENT);
			decl.setToken(ident);
			decl.nodetype = Kind.VARIABLE_DECLARATION;
			comp.syms.addVar(ident, type.type);
			decl.children.add(type);
			top.children.add(decl);
		} while (accept(Kind.COMMA));
		
		expect(Kind.SEMICOLON);
	}
	
	private ASTnode paramDecl() {
		Token tok = expectRetrieve(Kind.Group.TYPE);
		int dim = 0;
		while(accept(Kind.OPEN_BRACKET)) {
			dim++;
			expect(Kind.CLOSE_BRACKET);
		}
		Type type = new Type(tok.kind);
		for (int i = 0; i < dim; i++) {
			type.dims.add(0);
		}
		ASTnode param = new ASTnode(expectRetrieve(Kind.IDENT));
		param.type = type;
		return param;
	}
	
	private ASTnode formalParam() {
		ASTnode params = new ASTnode(Kind.ARGUMENT_LIST, expectRetrieve(Kind.OPEN_PAREN));
		if (accept(Kind.CLOSE_PAREN)) {
			return params;
		}
		do {
			params.children.add(paramDecl());
		} while (accept(Kind.COMMA));
		expect(Kind.CLOSE_PAREN);
		return params;
	}
	
	private ASTnode funDecl() {
		ASTnode func = new ASTnode(expectRetrieve(Kind.FUNC));
		Token ident = expectRetrieve(Kind.IDENT);

		func.tokenInfo = ident.lexeme;
		ASTnode params = formalParam();
		comp.syms.startFunction(comp.paramsToSymbols(params));
		func.children.add(params);
		expect(Kind.COLON);
		Token returnType = expectRetrieve(Kind.Group.TYPE);
		func.children.add(new ASTnode(returnType));
		Token brace = expectRetrieve(Kind.OPEN_BRACE);
		ASTnode funcBody = new ASTnode(Kind.FUNCTION_BODY, brace);
		Token tok = currentToken;
		if (have(Kind.Group.TYPE)) {
			ASTnode decls = new ASTnode(Kind.DECLARATION_LIST, tok);
			while (have(Kind.Group.TYPE)) {
				varDecl(decls);
			}
			funcBody.children.add(decls);
		}
		
		funcBody.children.add(statSeq(brace));
		expect(Kind.CLOSE_BRACE);
		expect(Kind.SEMICOLON);
		func.children.add(funcBody);
		comp.syms.addFun(ident, comp.paramsToSymbols(params), comp.syms.functionVars, returnType.kind);
		comp.syms.endFunction();
		return func;
	}

    // computation	= "main" {varDecl} {funcDecl} "{" statSeq "}" "."
    private void computation () {
		
		comp.main = new ASTnode(Kind.COMPUTATION);
		
        expect(Kind.MAIN);

        // deal with varDecl - many
		Token tok = currentToken;
		if (have(Kind.Group.TYPE)) {
			ASTnode decl = new ASTnode(Kind.DECLARATION_LIST, tok);
			while (have(Kind.Group.TYPE)) {
				varDecl(decl);
			}
			comp.main.children.add(decl);
		}
		
		tok = currentToken;
		if (have(Kind.FUNC)) {
			ASTnode decl = new ASTnode(Kind.DECLARATION_LIST, tok);
			while (have(Kind.FUNC)) {
				decl.children.add(funDecl());
			}
			comp.main.children.add(decl);
		}
		
        Token brace = expectRetrieve(Kind.OPEN_BRACE);
        comp.main.children.add(statSeq(brace));
        expect(Kind.CLOSE_BRACE);
        expect(Kind.PERIOD);       
    }
}