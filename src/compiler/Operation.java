package compiler;

import java.util.ArrayList;

public class Operation {
	public OpCode code;
	public ArrayList<Token> operands;
	public FunSymbol fun = null;
	
	public Operation(OpCode code, ArrayList<Token> ops) {
		this.code = code;
		operands = ops;
	}
	
	public Operation(OpCode code, ArrayList<Token> ops, FunSymbol function) {
		this.code = code;
		operands = ops;
		fun = function;
	}
	
	public static boolean compareEquals(Operation op1, Operation op2) {
		if (op1 == op2) {
			return true;
		}
		if (op1.code != op2.code) {
			return false;
		}
		if (op1.operands.size() != op2.operands.size()) {
			System.out.print("Operand size mismatch!");
			System.exit(-1);
		}
		for (int i = 0; i < op1.operands.size(); i++) {
			Token tok1 = op1.operands.get(i);
			Token tok2 = op2.operands.get(i);
			if (tok1.kind != tok2.kind || !tok1.lexeme.equals(tok2.lexeme)) {
				return false;
			}
		}
		return true;
	}
	
	public boolean hasOutput() {
		return !(code == OpCode.STW || code == OpCode.CHK || (code == OpCode.CALL && fun.output == Kind.VOID));
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(code.toString() + " ");
		if (code == OpCode.CALL) {
			sb.append(fun.name + " ");
		}
		for (Token tok : operands) {
			switch (tok.kind) {
			case TEMP:
				sb.append("temp_" + tok.lexeme + " ");
			break;
			case INT_VAL:
				sb.append(tok.lexeme + " ");
			break;
			case TRUE:
				sb.append("true ");
			break;
			case FALSE:
				sb.append("false ");
			break;
			case IDENT:
				sb.append(tok.lexeme + " ");
			break;
			case REGISTER:
				sb.append("R"+tok.lexeme+" ");
			break;
			default:
				System.out.print("Unhandled token type in Operation.toString(): " + tok.kind);
				assert(false);
			}
		}
		return sb.toString();
	}
}