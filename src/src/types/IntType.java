package types;

public class IntType extends TypeC {
	public String message;
	
	public IntType(String m) {
		super(m);
	}
	
    public TypeC mul (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot multiply " + this.message  + " with " + that.message  + ".");
        }
    }

    public TypeC div (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot divide " + this.message  + " by " + that.message  + ".");
        }
    }

    public TypeC add (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot add " + this.message  + " to " + that.message  + ".");
        }
    }

    public TypeC sub (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot subtract " + that.message  + " from " + this.message  + ".");
        }
    }

    public TypeC pow (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot take " + this.message  + " to the power of " + that.message  + ".");
        }
    }

    public TypeC compare (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new BoolType("bool");
        } else {
            return new ErrorType("Cannot compare " + this.message  + " with " + that.message  + ".");
        }
    }
    
    public TypeC mod (TypeC that) {
        if (that.getClass().equals(IntType.class)) {
            return new IntType("int");
        } else {
            return new ErrorType("Cannot mod " + this.message  + " by " + that.message  + ".");
        }
    }
    
    public TypeC assign (TypeC that) {
    	if (that.getClass().equals(IntType.class)) {
    		return new IntType("int");
    	} else {
    		return new ErrorType("Cannot assign " + this.message  + " to " + that.message  + ".");
    	}
    }
}
