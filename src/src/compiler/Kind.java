package compiler;

import java.util.HashSet;
import java.util.Set;

public enum Kind {

	// arithmetic operators
	POW("Power"),

	MUL("Multiplication"),
	DIV("Division"),
	MOD("Modulo"),

	ADD("Addition"),
	SUB("Subtraction"),
	
	//assignment
	ASSIGN("Assignment"),

	// region delimiters
	OPEN_PAREN("OPEN_PAREN"),
	CLOSE_PAREN("CLOSE_PAREN"),
	OPEN_BRACE("OPEN_BRACE"),
	CLOSE_BRACE("CLOSE_BRACE"),
	OPEN_BRACKET("OPEN_BRACKET"),
	CLOSE_BRACKET("CLOSE_BRACKET"),

	// field/record delimiters
	COMMA(","),
	COLON(":"),
	SEMICOLON(";"),
	PERIOD("."),

	// relational operators
	LESS_THAN("Relation[<]"),
	GREATER_THAN("Relation[>]"),
	EQUAL_TO("Relation[==]"),
	NOT_EQUAL("Relation[!=]"),
	LESS_EQUAL("Relation[<=]"),
	GREATER_EQUAL("Relation[>=]"),

	// assignment operators
	ADD_ASSIGN("Assignment, Add"),
	SUB_ASSIGN("Assignment, Sub"),
	MUL_ASSIGN("Assignment, Mul"),
	DIV_ASSIGN("Assignment, Div"),
	MOD_ASSIGN("Assignment, Mod"),
	POW_ASSIGN("Assignment, Pow"),

	// unary increment/decrement
	UNI_INC("Increment"),
	UNI_DEC("Decrement"),
	
	// primitive types
	VOID("void"),
	BOOL("bool"),
	INT("int"),
	FLOAT("float"),

	// boolean literals
	TRUE("True"),
	FALSE("False"),
	
	// boolean operators
	AND("LogicalAnd"),
	OR("LogicalOr"),
	NOT("LogicalNot"),

	// control flow statements
	IF("IfStatement"),
	THEN("then"),
	ELSE("else"),
	FI("FI"),

	WHILE("WhileStatement"),
	DO("do"),
	OD("od"),

	REPEAT("RepeatStatement"),
	UNTIL("until"),

	CALL("FunctionCall"),
	RETURN("ReturnStatement"),

	// keywords
	MAIN("main"),
	FUNC("FunctionDeclaration"),
	
	COMPUTATION("Computation"), // Computation[main:()->void]
    STATEMENT_SEQUENCE("StatementSequence"),

    DECLARATION_LIST("DeclarationList"),
    VARIABLE_DECLARATION("VariableDeclaration"), // VariableDeclaration[input:int]

    //FUNCTION_DECLARATION("Function Declaration"), // FunctionDeclaration[power:(int,int)->int]
    FUNCTION_BODY("FunctionBody"),
    //FUNCTION_CALL("Function Call"), // FunctionCall[power:(int,int)->int]
    ARGUMENT_LIST("ArgumentList"),

    ARRAY_INDEX("ArrayIndex"),

	// special cases
	INT_VAL("IntegerLiteral"),
	FLOAT_VAL("FloatLiteral"),
	IDENT("Identifier"),
	
	REGISTER("R"),
	
	TEMP("temp"),

	EOF("End Of File"),

	ERROR("Error");

	public String name;

	Kind () {
		name = "";
	}

	Kind (String str) {
		name = str;
	}
	
	public boolean is (Group g) {
		return g.firstSet().contains(this);
	}
	
    @Override
    public String toString(){
        return name;
    }
	
	public enum Group {
		// operators
		REL_OP(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(EQUAL_TO);
				add(NOT_EQUAL);
				add(LESS_THAN);
				add(LESS_EQUAL);
				add(GREATER_EQUAL);
				add(GREATER_THAN);
			}
		}),
		ASSIGN_OP(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(ASSIGN);
				add(ADD_ASSIGN);
				add(SUB_ASSIGN);
				add(MUL_ASSIGN);
				add(DIV_ASSIGN);
				add(MOD_ASSIGN);
				add(POW_ASSIGN);
			}
		}),
		ADD_OP(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(ADD);
				add(SUB);
				add(OR);
			}
		}),
		MUL_OP(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(MUL);
				add(DIV);
				add(MOD);
				add(AND);
			}
		}),
		UNARY_OP(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(UNI_INC);
				add(UNI_DEC);
			}
		}),
		
		// literals (integer and float handled by Scanner)
		BOOL_LIT(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(TRUE);
				add(FALSE);
			}
		}),
		LITERAL(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(INT_VAL);
				add(FLOAT_VAL);
				addAll(BOOL_LIT.firstSet());
			}
		}),
		TYPE(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(VOID);
				add(BOOL);
				add(INT);
				add(FLOAT);
			}
		}),
		TERMINAL(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(IDENT);
				addAll(LITERAL.firstSet());
				addAll(TYPE.firstSet());
			}
		}),
		EXTRA_INFO(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(IDENT);
				add(FLOAT_VAL);
				add(INT_VAL);
				add(CALL);
				add(VARIABLE_DECLARATION);
				add(FUNC);
				add(COMPUTATION);
				add(TRUE);
				add(FALSE);
				add(ARRAY_INDEX);
			}
		}),
		INDEXING_OPS(new HashSet<Kind>() {
			private static final long serialVersionUID = 1L;
			{
				add(MUL);
				add(DIV);
				add(MOD);
				add(ADD);
				add(SUB);
			}
		}),
		;

		private final Set<Kind> firstSet = new HashSet<>();

		private Group (Set<Kind> set) {
			firstSet.addAll(set);
		}

		public final Set<Kind> firstSet () {
			return firstSet;
		}
	}
}