package src;
import javolution.io.Union;

public class Attribute extends Union {
    //CONSTRUCTORS
    <T> Attribute(T attribute){
        if(Integer.class.isInstance(attribute))
            this.i = (int)attribute;
        else if(Double.class.isInstance(attribute))
            this.d = (double)attribute;
        else if(Character.class.isInstance(attribute))
            this.c = (char)attribute;
        else if(String.class.isInstance(attribute))
            this.s = String.valueOf(attribute);
        else
            this.o = attribute;
    }

    //ACCESSORS
    public String get() {
        if (this.i != null)
            return String.valueOf(this.i);
        else if(this.d != null)
            return String.valueOf(this.d);
        else if(this.c != null)
            return String.valueOf(this.c);
        else if(this.s != null)
            return this.s;
        else if(this.o != null)
            return String.valueOf(this.o);
        else
            return null;
    }

    //MEMBER VARIABLES
    private Integer i = null;
    private Double d = null;
    private Character c = null;
    private String s = null;
    private Object o = null;
}

