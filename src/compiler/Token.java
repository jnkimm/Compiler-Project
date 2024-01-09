package compiler;

import java.util.Arrays;

public class Token {

    public int lineNum;
    public int charPos;
    public Kind kind;
    public String lexeme = "";


    // TODO: implement remaining factory functions for handling special cases (EOF below)
	
	public Token (Token tok) {
		this.kind = tok.kind;
		this.lexeme = tok.lexeme;
		this.lineNum = tok.lineNum;
		this.charPos = tok.charPos;
	}
	
	public Token (Kind k, String lexeme, int lineNum, int charPos) {
		this.kind = k;
		this.lexeme = lexeme;
		this.lineNum = lineNum;
		this.charPos = charPos;
	}
	
	public Token () {
		this.kind = Kind.ERROR;
		this.lexeme = "";
		this.lineNum = 0;
		this.charPos = 0;
	}
	
	public String lexeme() {
		return this.lexeme;
	}

    // TODO: function to query a token about its kind - boolean is (Token.Kind kind)

    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design
	
	public static boolean match (Token t1, Token t2) {
		return (t1.kind == t2.kind) && t1.lexeme.equals(t2.lexeme);
	}

    @Override
    public String toString () {
        return "Line: " + lineNum + ", Char: " + charPos + ", Lexeme: " + lexeme + ", Kind: " + kind.toString();
    }
}
