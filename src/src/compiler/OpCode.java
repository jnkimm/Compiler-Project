package compiler;

public enum OpCode {
	
	ADD("ADD", 3, 0),
	SUB("SUB", 3, 1),
	MUL("MUL", 3, 2),
	DIV("DIV", 3, 3),
	MOD("MOD", 3, 4),
	POW("POW", 3, 5),
	CMP("CMP", 3, 6),
	
	// Floating point versions of above
	// skipped
	
	 OR("OR" , 3, 13),
	AND("AND", 3, 14),
	BIC("BIC", 3, 15),
	XOR("XOR", 3, 16),
	
	LSH("LSH", 3, 17),
	ASH("ASH", 3, 18),
	
	CHK("CHK", 3, 19),
	
	ADDI("ADDI", 3, 20),
	SUBI("SUBI", 3, 21),
	MULI("MULI", 3, 22),
	DIVI("DIVI", 3, 23),
	MODI("MODI", 3, 24),
	POWI("POWI", 3, 25),
	CMPI("CMPI", 3, 26),
	
	// Floating point versions of above
	// skipped
	
	 ORI("ORI" , 3, 33),
	ANDI("ANDI", 3, 34),
	BICI("BICI", 3, 35),
	XORI("XORI", 3, 36),
	
	LSHI("LSHI", 3, 37),
	ASHI("ASHI", 3, 38),
	
	CHKI("CHKI", 3, 39),
	
	LDW("LDW", 3, 40),
	LDX("LDX", 3, 41),
	POP("POP", 3, 42),
	STW("STW", 3, 43),
	STX("STX", 3, 44),
	PSH("PSH", 3, 45),
	
	ARRCPY("ARRCPY", 3, 46),
	
	BEQ("BEQ", 2, 47),
	BNE("BNE", 2, 48),
	BLT("BLT", 2, 49),
	BGE("BGE", 2, 50),
	BLE("BLE", 2, 51),
	BGT("BGT", 2, 52),
	
	BSR("BSR", 1, 53),
	JSR("JSR", 1, 54),
	RET("RET", 1, 55),
	//If you want to JMP, use BEQ r0
	
	RDI("RDI", 1, 56),
	RDF("RDF", 1, 57),
	RDB("RDB", 1, 58),
	WRI("WRI", 1, 59),
	WRF("WRF", 1, 60),
	WRB("WRB", 1, 61),
	WRL("WRL", 0, 62),
	
	//not real opcodes
	NONE("NONE", 0, -1),
	 NOT("NOT" , 2, -1), // we plan to generate this as a XORI
	 MOV("MOV" , 2, -1),
	CALL("CALL", 0, -1);
	
	public String name;
	public int registers;
	public int opcode;
	
	private OpCode(String str, int num1, int num2) {
		name = str;
		registers = num1;
		opcode = num2;
	}
	
	@Override
	public String toString() {
		return name;
	}
}