package compiler;

public class VarSymbol {
	public Token tok;
	public String name;
	public Type type;
	public int GDBoffset;
	public int FPoffset;
	
	public VarSymbol () {
		tok = null;
		name = "";
		type = new Type(Kind.VOID);
	}
	
	public VarSymbol (Token tok, Type type) {
		this.tok = tok;
		name = tok.lexeme;
		this.type = type;
	}
	
	public VarSymbol (String name, Type type) {
		tok = null;
		this.name = name;
		this.type = type;
	}
	
	public VarSymbol (Token tok, Type type, int offset, Boolean inFunc) {
		if (!inFunc) {
			this.tok = tok;
			name = tok.lexeme;
			this.type = type;
			this.GDBoffset = offset;
			this.FPoffset = Integer.MAX_VALUE;
		} else {
			this.tok = tok;
			name = tok.lexeme;
			this.type = type;
			this.FPoffset = offset;
			this.GDBoffset = Integer.MAX_VALUE;
		}
	}
	
	@Override
	public String toString() {
		return name + "<" + type.toString() + ">";
	}
}