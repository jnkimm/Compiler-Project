package ast;

import java.util.ArrayList;

import compiler.Kind;
import compiler.Token;
import compiler.Type;

public class ASTnode {
	public Kind nodetype;
	public int lineNum;
	public int charPos;
	public String tokenInfo;
	public Type type;
	public ArrayList<ASTnode> children;
	
	public ASTnode () {
		children = new ArrayList<ASTnode>();
	}
	
	public ASTnode(ASTnode n) {
		nodetype = n.nodetype;
		lineNum = n.lineNum;
		charPos = n.charPos;
		tokenInfo = new String(n.tokenInfo);
		if (n.type == null) {
			type = null;
		} else {
			type = new Type(n.type);
		}
		if (n.children == null) {
			children = new ArrayList<ASTnode>();
		} else {
			children = new ArrayList<ASTnode>(n.children);
		}
	}
	
	public ASTnode(Token tok) {
		setToken(tok);
		setIfTerminal();
	}
	
	// Used for Kind.COMPUTATION
	public ASTnode (Kind nodetype) {
		this.nodetype = nodetype;
		children = new ArrayList<ASTnode>();
	}
	
	public ASTnode (Kind nodetype, Token tok) {
		setToken(tok);
		this.nodetype = nodetype;
		children = new ArrayList<ASTnode>();
	}
	
	public ASTnode (Kind nodetype, Token tok, boolean terminal) {
		setToken(tok);
		this.nodetype = nodetype;
		children = new ArrayList<ASTnode>();
	}
	
	public void setIfTerminal () {
		children = new ArrayList<ASTnode>();
	}
	
	public void setToken (Token tok) {
		lineNum = tok.lineNum;
		charPos = tok.charPos;
		nodetype = tok.kind;
		tokenInfo = "";
		if (Kind.Group.EXTRA_INFO.firstSet().contains(tok.kind)) {
			tokenInfo = tok.lexeme;
		}
	}
	
    @Override
    public String toString(){
        StringBuilder str = new StringBuilder("ast.ASTnode {nodetype=");
		
		if (nodetype == null) {
			str.append("null");
		} else {
			str.append(nodetype.toString());
		}
		str.append(", lineNum=" + lineNum + ", charPos=" + charPos + ", tokenInfo=" + tokenInfo + ", type=");
		if (type == null) {
			str.append("null");
		} else {
			str.append(type.toString());
		}
		str.append("}");
		return str.toString();
    }
}