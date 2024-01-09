package compiler;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.IOException;

public class Scanner implements Iterator<Token> {

    private BufferedReader input;   // buffered reader to read file
    private boolean closed; // flag for whether reader is closed or not

    private int lineNum;    // current line number
    private int charPos;    // character offset on current line

    private int nextChar;   // contains the next char (-1 == EOF)

    // reader will be a FileReader over the source file
    public Scanner (Reader reader) {
        this.input = new BufferedReader(reader);
		this.lineNum = 1;
		this.charPos = 0;
		this.closed = false;
		this.nextChar = readChar();
    }

    // signal an error message
    public void Error (String msg, Exception e) {
        System.err.println("Scanner: Line - " + lineNum + ", Char - " + charPos);
        if (e != null) {
            e.printStackTrace();
        }
        System.err.println(msg);
    }

    /*
     * helper function for reading a single char from input
     * can be used to catch and handle any IOExceptions,
     * advance the charPos or lineNum, etc.
     */
    private int readChar () {
		charPos++;
        try {
			return input.read();
		} catch (IOException e) {
			return -1;
		}
    }

    /*
     * function to query whether or not more characters can be read
     * depends on closed and nextChar
     */
    @Override
    public boolean hasNext () {
        return !closed;
    }

    /*
     *	returns next Token from input
     *
     *  invariants:
     *  1. call assumes that nextChar is already holding an unread character
     *  2. return leaves nextChar containing an untokenized character
     *  3. closes reader when emitting EOF
     */
    @Override
    public Token next () {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
		EatWhitespace();
		if (HitEOF()) {
			return new Token(Kind.EOF, "",  lineNum, charPos);
		}
		if (nextChar >= '0' && nextChar <= '9') {
			return LexNumber();
		}
		if ( (nextChar >= 'a' && nextChar <= 'z') || (nextChar >= 'A' && nextChar <= 'Z' ) ) {
			return LexLetter();
		}
		return LexSpecial();
    }

    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design
    //           (useful for handling special case Tokens)
	private void EatWhitespace () {
		// all chars between 0 and 32 (space) (inclusive) are
		// whitespace and control characters
		while(nextChar <= ' ' && nextChar >= 0) {
			if (nextChar == '\n') {
				lineNum++;
				charPos = 0;
			}
			nextChar = readChar();
		}
	}
	
	private boolean HitEOF () {
		if (nextChar == -1) {
			try {
				this.input.close();
			} catch (IOException e) {
				
			}
			this.closed = true;
			return true;
		}
		return false;
	}
	
	private Token ErrorState(StringBuilder lexeme, int ln, int cp) {
		while (nextChar > ' ') {
			lexeme.append((char)nextChar);
			nextChar = readChar();
		}
		return new Token(Kind.ERROR, lexeme.toString(), ln, cp);
	}
	
	private Token LexNumber () {
		StringBuilder lexeme = new StringBuilder();
		boolean isFloat = false;
		int ln = lineNum;
		int cp = charPos;
		//TODO: Fix float
		while ( (nextChar >= '0' && nextChar <= '9') || nextChar == '.') {
			if (!isFloat && nextChar == '.') {
				isFloat = true;
				lexeme.append((char)nextChar);
				nextChar = readChar();
				if (nextChar < '0' || nextChar > '9') {
					return ErrorState(lexeme, ln, cp);
				}
			} else if (nextChar == '.') {
				break;
			}
			lexeme.append((char)nextChar);
			nextChar = readChar();
		}
		Token t;
		if (isFloat) {
			t = new Token(Kind.FLOAT_VAL, lexeme.toString(), ln, cp);
		} else {
			t = new Token(Kind.INT_VAL, lexeme.toString(), ln, cp);
		}
		return t;
	}
	
	private Token LexLetter () {
		StringBuilder lexeme = new StringBuilder();
		Token t = new Token();
		t.lineNum = lineNum;
		t.charPos = charPos;
		while ( (nextChar >= 'a' && nextChar <= 'z') || 
				(nextChar >= 'A' && nextChar <= 'Z') || 
				(nextChar >= '0' && nextChar <= '9') ||
				nextChar == '_' ) {
			lexeme.append((char)nextChar);
			nextChar = readChar();
			//extra error checking >:(
			if (nextChar == '"'  || nextChar == '\'' ||
				nextChar == '\\' || nextChar == '?'  ||
				nextChar == '&'  || nextChar == '|'  ||
				nextChar == '~'  || nextChar == '`'  ||
				nextChar == '@'  || nextChar == '#'  ||
				nextChar == '#'  || nextChar == '$') {
				
				return ErrorState(lexeme, t.lineNum, t.charPos);
			}
		}
		t.lexeme = lexeme.toString();
		switch (t.lexeme) {
			case "void":
				t.kind = Kind.VOID;
			break;
			case "bool":
				t.kind = Kind.BOOL;
			break;
			case "int":
				t.kind = Kind.INT;
			break;
			case "float":
				t.kind = Kind.FLOAT;
			break;
			case "true":
				t.kind = Kind.TRUE;
			break;
			case "false":
				t.kind = Kind.FALSE;
			break;
			case "and":
				t.kind = Kind.AND;
			break;
			case "or":
				t.kind = Kind.OR;
			break;
			case "not":
				t.kind = Kind.NOT;
			break;
			case "if":
				t.kind = Kind.IF;
			break;
			case "then":
				t.kind = Kind.THEN;
			break;
			case "else":
				t.kind = Kind.ELSE;
			break;
			case "fi":
				t.kind = Kind.FI;
			break;
			case "while":
				t.kind = Kind.WHILE;
			break;
			case "do":
				t.kind = Kind.DO;
			break;
			case "od":
				t.kind = Kind.OD;
			break;
			case "repeat":
				t.kind = Kind.REPEAT;
			break;
			case "until":
				t.kind = Kind.UNTIL;
			break;
			case "call":
				t.kind = Kind.CALL;
			break;
			case "return":
				t.kind = Kind.RETURN;
			break;
			case "main":
				t.kind = Kind.MAIN;
			break;
			case "function":
				t.kind = Kind.FUNC;
			break;
			default:
				t.kind = Kind.IDENT;
			break;
		}
		return t;
	}
	
	private Token LexSpecial () {
		Token t = new Token();
		t.lineNum = lineNum;
		t.charPos = charPos;
		int c = nextChar;
		nextChar = readChar();
		switch (c) {
		//delimiters first
		case ',':
			t.kind = Kind.COMMA;
			t.lexeme = ",";
		break;
		case ':':
			t.kind = Kind.COLON;
			t.lexeme = ":";
		break;
		case ';':
			t.kind = Kind.SEMICOLON;
			t.lexeme = ";";
		break;
		case '.':
			t.kind = Kind.PERIOD;
			t.lexeme = ".";
		break;
		case '(':
			t.kind = Kind.OPEN_PAREN;
			t.lexeme = "(";
		break;
		case ')':
			t.kind = Kind.CLOSE_PAREN;
			t.lexeme = ")";
		break;
		case '{':
			t.kind = Kind.OPEN_BRACE;
			t.lexeme = "{";
		break;
		case '}':
			t.kind = Kind.CLOSE_BRACE;
			t.lexeme = "}";
		break;
		case '[':
			t.kind = Kind.OPEN_BRACKET;
			t.lexeme = "[";
		break;
		case ']':
			t.kind = Kind.CLOSE_BRACKET;
			t.lexeme = "]";
		break;
		// maybe compound stuff
		case '+':
			if (nextChar == '=') {
				t.kind = Kind.ADD_ASSIGN;
				t.lexeme = "+=";
				nextChar = readChar();
			} else if (nextChar == '+') {
				t.kind = Kind.UNI_INC;
				t.lexeme = "++";
				nextChar = readChar();
			} else {
				t.kind = Kind.ADD;
				t.lexeme = "+";
			}
		break;
		case '-':
			if (nextChar == '=') {
				t.kind = Kind.SUB_ASSIGN;
				t.lexeme = "-=";
				nextChar = readChar();
			} else if (nextChar == '-') {
				t.kind = Kind.UNI_DEC;
				t.lexeme = "--";
				nextChar = readChar();
			} else if (nextChar >= '0' && nextChar <= '9') {
				Token t2 = LexNumber();
				StringBuilder str = new StringBuilder(t2.lexeme);
				str = str.reverse().append('-').reverse();
				t.kind = t2.kind;
				t.lexeme = str.toString();
			} else {
				t.kind = Kind.SUB;
				t.lexeme = "-";
			}
		break;
		case '=':
			if (nextChar == '=') {
				t.kind = Kind.EQUAL_TO;
				t.lexeme = "==";
				nextChar = readChar();
			} else {
				t.kind = Kind.ASSIGN;
				t.lexeme = "=";
			}
		break;
		case '>':
			if (nextChar == '=') {
				t.kind = Kind.GREATER_EQUAL;
				t.lexeme = ">=";
				nextChar = readChar();
			} else {
				t.kind = Kind.GREATER_THAN;
				t.lexeme = ">";
			}
		break;
		case '<':
			if (nextChar == '=') {
				t.kind = Kind.LESS_EQUAL;
				t.lexeme = "<=";
				nextChar = readChar();
			} else {
				t.kind = Kind.LESS_THAN;
				t.lexeme = "<";
			}
		break;
		case '^':
			if (nextChar == '=') {
				t.kind = Kind.POW_ASSIGN;
				t.lexeme = "^=";
				nextChar = readChar();
			} else {
				t.kind = Kind.POW;
				t.lexeme = "^";
			}
		break;
		case '*':
			if (nextChar == '=') {
				t.kind = Kind.MUL_ASSIGN;
				t.lexeme = "*=";
				nextChar = readChar();
			} else {
				t.kind = Kind.MUL;
				t.lexeme = "*";
			}
		break;
		case '/':
			if (nextChar == '=') {
				t.kind = Kind.DIV_ASSIGN;
				t.lexeme = "/=";
				nextChar = readChar();
			} else if (nextChar == '/') {
				//Single-line Comment (//)
				while (nextChar != '\n' && nextChar != -1) {
					nextChar = readChar();
				}
				return this.next();
			} else if (nextChar == '*') {
				//Block comment (/*)
				nextChar = readChar();
				do {
					while (nextChar != '*' && nextChar != -1) {
						nextChar = readChar();
					}
					nextChar = readChar();
					if (nextChar == -1) {
						t.kind = Kind.ERROR;
						t.lexeme = "/*";
						return t;
					}
				} while (nextChar != '/');
				nextChar = readChar();
				return this.next();
			} else {
				t.kind = Kind.DIV;
				t.lexeme = "/";
			}
		break;
		case '%':
			if (nextChar == '=') {
				t.kind = Kind.MOD_ASSIGN;
				t.lexeme = "%=";
				nextChar = readChar();
			} else {
				t.kind = Kind.MOD;
				t.lexeme = "%";
			}
		break;
		case '!':
			if (nextChar == '=') {
				t.kind = Kind.NOT_EQUAL;
				t.lexeme = "!=";
				nextChar = readChar();
			} else {
				t = ErrorState(new StringBuilder("!"), t.lineNum, t.charPos);
			}
		break;
		default:
			t = ErrorState(new StringBuilder((char)c), t.lineNum, t.charPos);
		}
		return t;
	}
}
